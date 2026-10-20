#include "VulkanRenderer.h"

#include <android/log.h>
#include <android/native_window.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <string>

#define LOG_TAG "EmberfallVK"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define VK_CHECK(call, what)                                                  \
    do {                                                                      \
        VkResult vkResult_ = (call);                                          \
        if (vkResult_ != VK_SUCCESS) {                                        \
            LOGE("%s failed with VkResult %d", what, static_cast<int>(vkResult_)); \
            return false;                                                     \
        }                                                                     \
    } while (0)

namespace emberfall {

namespace {

// Must match SpriteBatch.kt: 13 floats per sprite.
struct SpriteRecord {
    float x, y, w, h;
    float u0, v0, u1, v1;
    float r, g, b, a;
    float rotation;   // radians, about the rectangle centre
};
static_assert(sizeof(SpriteRecord) == 13 * sizeof(float), "sprite record must be 13 floats");

}  // namespace

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

bool VulkanRenderer::init(ANativeWindow* window, AAssetManager* assets) {
    window_ = window;
    assets_ = assets;

    if (!createInstance()) return false;
    if (!createSurface()) return false;
    if (!pickPhysicalDevice()) return false;
    if (!createDevice()) return false;
    if (!createCommandResources()) return false;
    if (!createFrameSync()) return false;
    if (!createStaticBuffers()) return false;
    if (!createDescriptorResources()) return false;
    if (!createRenderPass()) return false;
    if (!createPipeline()) return false;

    VkShaderModule lightVert = loadShader("lighting.vert");
    VkShaderModule lightFrag = loadShader("lighting.frag");
    lightingReady_ = lighting_.init(physical_, device_, renderPass_, lightVert, lightFrag, kFramesInFlight);
    if (lightVert) vkDestroyShaderModule(device_, lightVert, nullptr);
    if (lightFrag) vkDestroyShaderModule(device_, lightFrag, nullptr);
    if (!lightingReady_) LOGE("Lighting pass unavailable; night will be skipped");

    // A 1x1 white atlas lets solid-colour sprites share the textured pipeline.
    const uint32_t white = 0xFFFFFFFFu;
    if (!uploadAtlas(&white, 1, 1)) return false;

    if (!createSwapchain()) {
        // Zero-sized surface (minimised at start-up): retry on the first frame.
        needsRecreate_ = true;
    }

    ready_ = true;
    LOGI("Vulkan renderer ready");
    return true;
}

void VulkanRenderer::shutdown() {
    if (device_ != VK_NULL_HANDLE) vkDeviceWaitIdle(device_);

    destroySwapchain();
    lighting_.destroy();

    if (device_ != VK_NULL_HANDLE) {
        if (pipeline_ != VK_NULL_HANDLE) vkDestroyPipeline(device_, pipeline_, nullptr);
        if (pipelineLayout_ != VK_NULL_HANDLE) vkDestroyPipelineLayout(device_, pipelineLayout_, nullptr);
        if (renderPass_ != VK_NULL_HANDLE) vkDestroyRenderPass(device_, renderPass_, nullptr);
        if (descriptorPool_ != VK_NULL_HANDLE) vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
        if (setLayout_ != VK_NULL_HANDLE) vkDestroyDescriptorSetLayout(device_, setLayout_, nullptr);
        if (sampler_ != VK_NULL_HANDLE) vkDestroySampler(device_, sampler_, nullptr);
    }
    destroyImage(atlas_);
    destroyBuffer(indexBuffer_);
    for (auto& buffer : vertexBuffers_) destroyBuffer(buffer);

    if (device_ != VK_NULL_HANDLE) {
        for (uint32_t i = 0; i < kFramesInFlight; ++i) {
            if (inFlight_[i] != VK_NULL_HANDLE) vkDestroyFence(device_, inFlight_[i], nullptr);
            if (imageAvailable_[i] != VK_NULL_HANDLE) vkDestroySemaphore(device_, imageAvailable_[i], nullptr);
        }
        if (commandPool_ != VK_NULL_HANDLE) vkDestroyCommandPool(device_, commandPool_, nullptr);
        vkDestroyDevice(device_, nullptr);
    }
    if (surface_ != VK_NULL_HANDLE) vkDestroySurfaceKHR(instance_, surface_, nullptr);
    if (instance_ != VK_NULL_HANDLE) vkDestroyInstance(instance_, nullptr);
    if (window_) ANativeWindow_release(window_);

    *this = VulkanRenderer();
}

// ---------------------------------------------------------------------------
// Instance, surface, device
// ---------------------------------------------------------------------------

bool VulkanRenderer::createInstance() {
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "Emberfall";
    app.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    app.pEngineName = "EmberfallEngine";
    app.apiVersion = VK_API_VERSION_1_1;

    const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};

    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ci.pApplicationInfo = &app;
    ci.enabledExtensionCount = 2;
    ci.ppEnabledExtensionNames = extensions;
    VK_CHECK(vkCreateInstance(&ci, nullptr, &instance_), "vkCreateInstance");
    return true;
}

bool VulkanRenderer::createSurface() {
    VkAndroidSurfaceCreateInfoKHR ci{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
    ci.window = window_;
    VK_CHECK(vkCreateAndroidSurfaceKHR(instance_, &ci, nullptr, &surface_), "vkCreateAndroidSurfaceKHR");
    return true;
}

bool VulkanRenderer::pickPhysicalDevice() {
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance_, &count, nullptr);
    std::vector<VkPhysicalDevice> devices(count);
    if (count > 0) vkEnumeratePhysicalDevices(instance_, &count, devices.data());

    for (VkPhysicalDevice device : devices) {
        VkPhysicalDeviceProperties props{};
        vkGetPhysicalDeviceProperties(device, &props);
        if (props.apiVersion < VK_API_VERSION_1_1) continue;

        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(device, &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(device, &familyCount, families.data());

        for (uint32_t i = 0; i < familyCount; ++i) {
            VkBool32 presentSupported = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface_, &presentSupported);
            if ((families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && presentSupported) {
                physical_ = device;
                queueFamily_ = i;

                // Prefer a non-sRGB format: colours then reach the screen exactly
                // as the Canvas renderer writes them, so the two paths can be
                // compared pixel for pixel. Gamma handling comes with post-processing.
                uint32_t formatCount = 0;
                vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface_, &formatCount, nullptr);
                std::vector<VkSurfaceFormatKHR> formats(formatCount);
                vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface_, &formatCount, formats.data());
                format_ = formats.empty() ? VkSurfaceFormatKHR{VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR}
                                          : formats[0];
                for (const auto& f : formats) {
                    if (f.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR &&
                        (f.format == VK_FORMAT_R8G8B8A8_UNORM || f.format == VK_FORMAT_B8G8R8A8_UNORM)) {
                        format_ = f;
                        break;
                    }
                }
                LOGI("Selected device '%s', Vulkan %u.%u",
                     props.deviceName,
                     VK_API_VERSION_MAJOR(props.apiVersion),
                     VK_API_VERSION_MINOR(props.apiVersion));
                return true;
            }
        }
    }
    LOGE("No Vulkan 1.1 device with graphics and present support");
    return false;
}

bool VulkanRenderer::createDevice() {
    const float priority = 1.0f;
    VkDeviceQueueCreateInfo queue{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queue.queueFamilyIndex = queueFamily_;
    queue.queueCount = 1;
    queue.pQueuePriorities = &priority;

    const char* extensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};

    VkDeviceCreateInfo ci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    ci.queueCreateInfoCount = 1;
    ci.pQueueCreateInfos = &queue;
    ci.enabledExtensionCount = 1;
    ci.ppEnabledExtensionNames = extensions;
    VK_CHECK(vkCreateDevice(physical_, &ci, nullptr, &device_), "vkCreateDevice");
    vkGetDeviceQueue(device_, queueFamily_, 0, &queue_);
    return true;
}

// ---------------------------------------------------------------------------
// Command resources and frame sync
// ---------------------------------------------------------------------------

bool VulkanRenderer::createCommandResources() {
    VkCommandPoolCreateInfo pool{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    pool.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pool.queueFamilyIndex = queueFamily_;
    VK_CHECK(vkCreateCommandPool(device_, &pool, nullptr, &commandPool_), "vkCreateCommandPool");

    VkCommandBufferAllocateInfo alloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    alloc.commandPool = commandPool_;
    alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    alloc.commandBufferCount = kFramesInFlight;
    VK_CHECK(vkAllocateCommandBuffers(device_, &alloc, commandBuffers_), "vkAllocateCommandBuffers");
    return true;
}

bool VulkanRenderer::createFrameSync() {
    VkSemaphoreCreateInfo semaphore{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VkFenceCreateInfo fence{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    fence.flags = VK_FENCE_CREATE_SIGNALED_BIT;  // first wait returns immediately
    for (uint32_t i = 0; i < kFramesInFlight; ++i) {
        VK_CHECK(vkCreateSemaphore(device_, &semaphore, nullptr, &imageAvailable_[i]), "vkCreateSemaphore");
        VK_CHECK(vkCreateFence(device_, &fence, nullptr, &inFlight_[i]), "vkCreateFence");
    }
    return true;
}

bool VulkanRenderer::createStaticBuffers() {
    const VkDeviceSize vertexBytes = VkDeviceSize(kMaxSprites) * 4 * sizeof(Vertex);
    for (auto& buffer : vertexBuffers_) {
        if (!createBuffer(vertexBytes, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                          VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, buffer)) {
            return false;
        }
    }

    // Indices never change, so they are written once: two triangles per sprite.
    const VkDeviceSize indexBytes = VkDeviceSize(kMaxSprites) * 6 * sizeof(uint32_t);
    if (!createBuffer(indexBytes, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                      VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, indexBuffer_)) {
        return false;
    }
    auto* indices = static_cast<uint32_t*>(indexBuffer_.mapped);
    for (uint32_t i = 0; i < kMaxSprites; ++i) {
        const uint32_t base = i * 4;
        uint32_t* out = indices + i * 6;
        out[0] = base; out[1] = base + 1; out[2] = base + 2;
        out[3] = base + 2; out[4] = base + 3; out[5] = base;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Descriptors, render pass, pipeline
// ---------------------------------------------------------------------------

bool VulkanRenderer::createDescriptorResources() {
    VkDescriptorSetLayoutBinding binding{};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    binding.descriptorCount = 1;
    binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

    VkDescriptorSetLayoutCreateInfo layout{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    layout.bindingCount = 1;
    layout.pBindings = &binding;
    VK_CHECK(vkCreateDescriptorSetLayout(device_, &layout, nullptr, &setLayout_), "vkCreateDescriptorSetLayout");

    VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1};
    VkDescriptorPoolCreateInfo pool{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    pool.maxSets = 1;
    pool.poolSizeCount = 1;
    pool.pPoolSizes = &size;
    VK_CHECK(vkCreateDescriptorPool(device_, &pool, nullptr, &descriptorPool_), "vkCreateDescriptorPool");

    VkDescriptorSetAllocateInfo alloc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    alloc.descriptorPool = descriptorPool_;
    alloc.descriptorSetCount = 1;
    alloc.pSetLayouts = &setLayout_;
    VK_CHECK(vkAllocateDescriptorSets(device_, &alloc, &descriptorSet_), "vkAllocateDescriptorSets");

    VkSamplerCreateInfo samp{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    samp.magFilter = VK_FILTER_LINEAR;
    samp.minFilter = VK_FILTER_LINEAR;
    samp.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
    samp.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samp.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samp.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samp.maxLod = 0.0f;
    VK_CHECK(vkCreateSampler(device_, &samp, nullptr, &sampler_), "vkCreateSampler");
    return true;
}

bool VulkanRenderer::createRenderPass() {
    VkAttachmentDescription color{};
    color.format = format_.format;
    color.samples = VK_SAMPLE_COUNT_1_BIT;
    color.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    color.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    color.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    color.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    color.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    color.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference ref{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &ref;

    VkSubpassDependency dep{};
    dep.srcSubpass = VK_SUBPASS_EXTERNAL;
    dep.dstSubpass = 0;
    dep.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo ci{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    ci.attachmentCount = 1;
    ci.pAttachments = &color;
    ci.subpassCount = 1;
    ci.pSubpasses = &subpass;
    ci.dependencyCount = 1;
    ci.pDependencies = &dep;
    VK_CHECK(vkCreateRenderPass(device_, &ci, nullptr, &renderPass_), "vkCreateRenderPass");
    return true;
}

VkShaderModule VulkanRenderer::loadShader(const char* name) {
    const std::string path = std::string("shaders/") + name + ".spv";
    AAsset* asset = AAssetManager_open(assets_, path.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Missing shader asset %s", path.c_str());
        return VK_NULL_HANDLE;
    }
    const size_t size = AAsset_getLength(asset);
    std::vector<uint32_t> code(size / sizeof(uint32_t));
    AAsset_read(asset, code.data(), code.size() * sizeof(uint32_t));
    AAsset_close(asset);

    VkShaderModuleCreateInfo ci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    ci.codeSize = code.size() * sizeof(uint32_t);
    ci.pCode = code.data();
    VkShaderModule module = VK_NULL_HANDLE;
    if (vkCreateShaderModule(device_, &ci, nullptr, &module) != VK_SUCCESS) return VK_NULL_HANDLE;
    return module;
}

bool VulkanRenderer::createPipeline() {
    VkShaderModule vert = loadShader("sprite.vert");
    VkShaderModule frag = loadShader("sprite.frag");
    if (!vert || !frag) {
        if (vert) vkDestroyShaderModule(device_, vert, nullptr);
        if (frag) vkDestroyShaderModule(device_, frag, nullptr);
        return false;
    }

    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vert;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = frag;
    stages[1].pName = "main";

    VkVertexInputBindingDescription binding{0, sizeof(Vertex), VK_VERTEX_INPUT_RATE_VERTEX};
    VkVertexInputAttributeDescription attrs[3]{
        {0, 0, VK_FORMAT_R32G32_SFLOAT, offsetof(Vertex, x)},
        {1, 0, VK_FORMAT_R32G32_SFLOAT, offsetof(Vertex, u)},
        {2, 0, VK_FORMAT_R32G32B32A32_SFLOAT, offsetof(Vertex, r)},
    };
    VkPipelineVertexInputStateCreateInfo vertexInput{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
    vertexInput.vertexBindingDescriptionCount = 1;
    vertexInput.pVertexBindingDescriptions = &binding;
    vertexInput.vertexAttributeDescriptionCount = 3;
    vertexInput.pVertexAttributeDescriptions = attrs;

    VkPipelineInputAssemblyStateCreateInfo assembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
    assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkPipelineViewportStateCreateInfo viewport{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
    viewport.viewportCount = 1;
    viewport.scissorCount = 1;

    VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
    raster.polygonMode = VK_POLYGON_MODE_FILL;
    raster.cullMode = VK_CULL_MODE_NONE;
    raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    raster.lineWidth = 1.0f;

    VkPipelineMultisampleStateCreateInfo multisample{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
    multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineColorBlendAttachmentState blendAttachment{};
    blendAttachment.blendEnable = VK_TRUE;
    blendAttachment.srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA;
    blendAttachment.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    blendAttachment.colorBlendOp = VK_BLEND_OP_ADD;
    blendAttachment.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
    blendAttachment.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    blendAttachment.alphaBlendOp = VK_BLEND_OP_ADD;
    blendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                     VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    VkPipelineColorBlendStateCreateInfo blend{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
    blend.attachmentCount = 1;
    blend.pAttachments = &blendAttachment;

    VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamic{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
    dynamic.dynamicStateCount = 2;
    dynamic.pDynamicStates = dynamicStates;

    VkPushConstantRange push{VK_SHADER_STAGE_VERTEX_BIT, 0, 2 * sizeof(float)};
    VkPipelineLayoutCreateInfo layoutInfo{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    layoutInfo.setLayoutCount = 1;
    layoutInfo.pSetLayouts = &setLayout_;
    layoutInfo.pushConstantRangeCount = 1;
    layoutInfo.pPushConstantRanges = &push;
    VkResult layoutResult = vkCreatePipelineLayout(device_, &layoutInfo, nullptr, &pipelineLayout_);

    VkResult pipeResult = VK_ERROR_UNKNOWN;
    if (layoutResult == VK_SUCCESS) {
        VkGraphicsPipelineCreateInfo ci{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        ci.stageCount = 2;
        ci.pStages = stages;
        ci.pVertexInputState = &vertexInput;
        ci.pInputAssemblyState = &assembly;
        ci.pViewportState = &viewport;
        ci.pRasterizationState = &raster;
        ci.pMultisampleState = &multisample;
        ci.pColorBlendState = &blend;
        ci.pDynamicState = &dynamic;
        ci.layout = pipelineLayout_;
        ci.renderPass = renderPass_;
        ci.subpass = 0;
        pipeResult = vkCreateGraphicsPipelines(device_, VK_NULL_HANDLE, 1, &ci, nullptr, &pipeline_);
    }

    vkDestroyShaderModule(device_, vert, nullptr);
    vkDestroyShaderModule(device_, frag, nullptr);
    if (layoutResult != VK_SUCCESS || pipeResult != VK_SUCCESS) {
        LOGE("Pipeline creation failed (layout %d, pipeline %d)", (int)layoutResult, (int)pipeResult);
        return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Swapchain
// ---------------------------------------------------------------------------

bool VulkanRenderer::createSwapchain() {
    VkSurfaceCapabilitiesKHR caps{};
    VK_CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical_, surface_, &caps), "surface capabilities");

    uint32_t winW = window_ ? static_cast<uint32_t>(ANativeWindow_getWidth(window_)) : 0;
    uint32_t winH = window_ ? static_cast<uint32_t>(ANativeWindow_getHeight(window_)) : 0;

    VkExtent2D extent = caps.currentExtent;
    if (winW > 0 && winH > 0) {
        extent.width = std::max(winW, winH);
        extent.height = std::min(winW, winH);
    }
    if (extent.width == 0 || extent.height == 0) return false;  // minimised

    uint32_t imageCount = std::max(caps.minImageCount + 1, 2u);
    if (caps.maxImageCount > 0 && imageCount > caps.maxImageCount) imageCount = caps.maxImageCount;

    VkSwapchainCreateInfoKHR ci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
    ci.surface = surface_;
    ci.minImageCount = imageCount;
    ci.imageFormat = format_.format;
    ci.imageColorSpace = format_.colorSpace;
    ci.imageExtent = extent;
    ci.imageArrayLayers = 1;
    ci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    ci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ci.preTransform = (caps.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
        ? VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
        : caps.currentTransform;
    ci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    ci.presentMode = VK_PRESENT_MODE_FIFO_KHR;   // vsync; always available
    ci.clipped = VK_TRUE;
    VK_CHECK(vkCreateSwapchainKHR(device_, &ci, nullptr, &swapchain_), "vkCreateSwapchainKHR");

    uint32_t actual = 0;
    vkGetSwapchainImagesKHR(device_, swapchain_, &actual, nullptr);
    std::vector<VkImage> images(actual);
    vkGetSwapchainImagesKHR(device_, swapchain_, &actual, images.data());

    extent_ = extent;
    swapViews_.resize(actual);
    framebuffers_.resize(actual);
    renderFinished_.resize(actual);

    VkSemaphoreCreateInfo semaphore{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    for (uint32_t i = 0; i < actual; ++i) {
        VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        view.image = images[i];
        view.viewType = VK_IMAGE_VIEW_TYPE_2D;
        view.format = format_.format;
        view.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        VK_CHECK(vkCreateImageView(device_, &view, nullptr, &swapViews_[i]), "vkCreateImageView");

        VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
        fb.renderPass = renderPass_;
        fb.attachmentCount = 1;
        fb.pAttachments = &swapViews_[i];
        fb.width = extent.width;
        fb.height = extent.height;
        fb.layers = 1;
        VK_CHECK(vkCreateFramebuffer(device_, &fb, nullptr, &framebuffers_[i]), "vkCreateFramebuffer");

        VK_CHECK(vkCreateSemaphore(device_, &semaphore, nullptr, &renderFinished_[i]), "vkCreateSemaphore");
    }
    return true;
}

void VulkanRenderer::destroySwapchain() {
    if (device_ == VK_NULL_HANDLE) return;
    for (VkFramebuffer fb : framebuffers_) vkDestroyFramebuffer(device_, fb, nullptr);
    for (VkImageView view : swapViews_) vkDestroyImageView(device_, view, nullptr);
    for (VkSemaphore sem : renderFinished_) vkDestroySemaphore(device_, sem, nullptr);
    if (swapchain_ != VK_NULL_HANDLE) vkDestroySwapchainKHR(device_, swapchain_, nullptr);
    framebuffers_.clear();
    swapViews_.clear();
    renderFinished_.clear();
    swapchain_ = VK_NULL_HANDLE;
}

bool VulkanRenderer::rebuildSwapchain() {
    vkDeviceWaitIdle(device_);
    destroySwapchain();
    needsRecreate_ = false;
    return createSwapchain();
}

// ---------------------------------------------------------------------------
// Frame loop
// ---------------------------------------------------------------------------

bool VulkanRenderer::drawFrame(const float* sprites, uint32_t count, const float clearColor[3],
                               const float* lights, uint32_t lightCount, float ambient) {
    if (!ready_) return false;
    if (count > kMaxSprites) count = kMaxSprites;

    if (needsRecreate_ || swapchain_ == VK_NULL_HANDLE) {
        if (!rebuildSwapchain()) return false;
    }

    const uint32_t frame = frame_;
    VkFence fence = inFlight_[frame];
    vkWaitForFences(device_, 1, &fence, VK_TRUE, UINT64_MAX);

    uint32_t imageIndex = 0;
    VkResult acquire = vkAcquireNextImageKHR(device_, swapchain_, UINT64_MAX,
                                             imageAvailable_[frame], VK_NULL_HANDLE, &imageIndex);
    if (acquire == VK_ERROR_OUT_OF_DATE_KHR) {
        needsRecreate_ = true;
        return false;
    }
    if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR) return false;

    vkResetFences(device_, 1, &fence);
    writeSprites(frame, sprites, count);
    if (lightingReady_) lighting_.update(frame, lights, lightCount, ambient);
    recordCommands(frame, imageIndex, count, clearColor);

    const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submit.waitSemaphoreCount = 1;
    submit.pWaitSemaphores = &imageAvailable_[frame];
    submit.pWaitDstStageMask = &waitStage;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &commandBuffers_[frame];
    submit.signalSemaphoreCount = 1;
    submit.pSignalSemaphores = &renderFinished_[imageIndex];
    if (vkQueueSubmit(queue_, 1, &submit, fence) != VK_SUCCESS) return false;

    VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
    present.waitSemaphoreCount = 1;
    present.pWaitSemaphores = &renderFinished_[imageIndex];
    present.swapchainCount = 1;
    present.pSwapchains = &swapchain_;
    present.pImageIndices = &imageIndex;
    VkResult result = vkQueuePresentKHR(queue_, &present);
    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) needsRecreate_ = true;

    frame_ = (frame_ + 1) % kFramesInFlight;
    return true;
}

void VulkanRenderer::writeSprites(uint32_t frame, const float* sprites, uint32_t count) {
    auto* verts = static_cast<Vertex*>(vertexBuffers_[frame].mapped);
    const auto* in = reinterpret_cast<const SpriteRecord*>(sprites);
    for (uint32_t i = 0; i < count; ++i) {
        const SpriteRecord& s = in[i];
        Vertex* v = verts + i * 4;
        const float cx = s.x + s.w * 0.5f;
        const float cy = s.y + s.h * 0.5f;
        const float hw = s.w * 0.5f;
        const float hh = s.h * 0.5f;
        const float c = std::cos(s.rotation);
        const float sn = std::sin(s.rotation);
        auto corner = [&](float lx, float ly, float u, float vv, Vertex& out) {
            out = {cx + lx * c - ly * sn, cy + lx * sn + ly * c, u, vv, s.r, s.g, s.b, s.a};
        };
        corner(-hw, -hh, s.u0, s.v0, v[0]);
        corner( hw, -hh, s.u1, s.v0, v[1]);
        corner( hw,  hh, s.u1, s.v1, v[2]);
        corner(-hw,  hh, s.u0, s.v1, v[3]);
    }
}

void VulkanRenderer::recordCommands(uint32_t frame, uint32_t imageIndex, uint32_t count, const float clearColor[3]) {
    VkCommandBuffer cmd = commandBuffers_[frame];
    vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    vkBeginCommandBuffer(cmd, &begin);

    VkClearValue clear{};
    clear.color = {{clearColor[0], clearColor[1], clearColor[2], 1.0f}};

    VkRenderPassBeginInfo pass{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    pass.renderPass = renderPass_;
    pass.framebuffer = framebuffers_[imageIndex];
    pass.renderArea = {{0, 0}, extent_};
    pass.clearValueCount = 1;
    pass.pClearValues = &clear;
    vkCmdBeginRenderPass(cmd, &pass, VK_SUBPASS_CONTENTS_INLINE);

    if (count > 0) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline_);

        VkViewport vp{0.0f, 0.0f, static_cast<float>(extent_.width), static_cast<float>(extent_.height), 0.0f, 1.0f};
        vkCmdSetViewport(cmd, 0, 1, &vp);
        VkRect2D scissor{{0, 0}, extent_};
        vkCmdSetScissor(cmd, 0, 1, &scissor);

        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout_,
                                0, 1, &descriptorSet_, 0, nullptr);

        VkDeviceSize offset = 0;
        VkBuffer vb = vertexBuffers_[frame].buffer;
        vkCmdBindVertexBuffers(cmd, 0, 1, &vb, &offset);
        vkCmdBindIndexBuffer(cmd, indexBuffer_.buffer, 0, VK_INDEX_TYPE_UINT32);

        const float push[2] = {static_cast<float>(extent_.width), static_cast<float>(extent_.height)};
        vkCmdPushConstants(cmd, pipelineLayout_, VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(push), push);

        vkCmdDrawIndexed(cmd, count * 6, 1, 0, 0, 0);
    }

    if (lightingReady_) lighting_.record(cmd, frame, extent_);

    vkCmdEndRenderPass(cmd);
    vkEndCommandBuffer(cmd);
}

// ---------------------------------------------------------------------------
// Buffers, images, atlas
// ---------------------------------------------------------------------------

uint32_t VulkanRenderer::findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags props) const {
    VkPhysicalDeviceMemoryProperties mem{};
    vkGetPhysicalDeviceMemoryProperties(physical_, &mem);
    for (uint32_t i = 0; i < mem.memoryTypeCount; ++i) {
        if ((typeBits & (1u << i)) && (mem.memoryTypes[i].propertyFlags & props) == props) return i;
    }
    return UINT32_MAX;
}

bool VulkanRenderer::createBuffer(VkDeviceSize size, VkBufferUsageFlags usage,
                                  VkMemoryPropertyFlags props, Buffer& out) {
    VkBufferCreateInfo bi{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bi.size = size;
    bi.usage = usage;
    bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    VK_CHECK(vkCreateBuffer(device_, &bi, nullptr, &out.buffer), "vkCreateBuffer");

    VkMemoryRequirements req{};
    vkGetBufferMemoryRequirements(device_, out.buffer, &req);
    const uint32_t type = findMemoryType(req.memoryTypeBits, props);
    if (type == UINT32_MAX) {
        LOGE("No memory type for buffer");
        return false;
    }
    VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = type;
    VK_CHECK(vkAllocateMemory(device_, &alloc, nullptr, &out.memory), "vkAllocateMemory");
    VK_CHECK(vkBindBufferMemory(device_, out.buffer, out.memory, 0), "vkBindBufferMemory");

    if (props & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) {
        VK_CHECK(vkMapMemory(device_, out.memory, 0, size, 0, &out.mapped), "vkMapMemory");
    }
    return true;
}

void VulkanRenderer::destroyBuffer(Buffer& buffer) {
    if (device_ == VK_NULL_HANDLE) return;
    if (buffer.mapped) vkUnmapMemory(device_, buffer.memory);
    if (buffer.buffer) vkDestroyBuffer(device_, buffer.buffer, nullptr);
    if (buffer.memory) vkFreeMemory(device_, buffer.memory, nullptr);
    buffer = Buffer{};
}

bool VulkanRenderer::createAtlasImage(int width, int height, Image& out) {
    VkImageCreateInfo ii{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    ii.imageType = VK_IMAGE_TYPE_2D;
    ii.format = VK_FORMAT_R8G8B8A8_UNORM;
    ii.extent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1};
    ii.mipLevels = 1;
    ii.arrayLayers = 1;
    ii.samples = VK_SAMPLE_COUNT_1_BIT;
    ii.tiling = VK_IMAGE_TILING_OPTIMAL;
    ii.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    ii.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ii.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VK_CHECK(vkCreateImage(device_, &ii, nullptr, &out.image), "vkCreateImage");

    VkMemoryRequirements req{};
    vkGetImageMemoryRequirements(device_, out.image, &req);
    const uint32_t type = findMemoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (type == UINT32_MAX) return false;
    VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = type;
    VK_CHECK(vkAllocateMemory(device_, &alloc, nullptr, &out.memory), "vkAllocateMemory (image)");
    VK_CHECK(vkBindImageMemory(device_, out.image, out.memory, 0), "vkBindImageMemory");

    VkImageViewCreateInfo vi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    vi.image = out.image;
    vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vi.format = VK_FORMAT_R8G8B8A8_UNORM;
    vi.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    VK_CHECK(vkCreateImageView(device_, &vi, nullptr, &out.view), "vkCreateImageView (atlas)");
    return true;
}

void VulkanRenderer::destroyImage(Image& image) {
    if (device_ == VK_NULL_HANDLE) return;
    if (image.view) vkDestroyImageView(device_, image.view, nullptr);
    if (image.image) vkDestroyImage(device_, image.image, nullptr);
    if (image.memory) vkFreeMemory(device_, image.memory, nullptr);
    image = Image{};
}

VkCommandBuffer VulkanRenderer::beginOneTime() {
    VkCommandBufferAllocateInfo alloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    alloc.commandPool = commandPool_;
    alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    alloc.commandBufferCount = 1;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    if (vkAllocateCommandBuffers(device_, &alloc, &cmd) != VK_SUCCESS) return VK_NULL_HANDLE;

    VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &begin);
    return cmd;
}

bool VulkanRenderer::endOneTime(VkCommandBuffer cmd) {
    vkEndCommandBuffer(cmd);
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &cmd;
    const VkResult r = vkQueueSubmit(queue_, 1, &submit, VK_NULL_HANDLE);
    vkQueueWaitIdle(queue_);
    vkFreeCommandBuffers(device_, commandPool_, 1, &cmd);
    return r == VK_SUCCESS;
}

void VulkanRenderer::recordBarrier(VkCommandBuffer cmd, VkImage image,
                                   VkImageLayout from, VkImageLayout to,
                                   VkAccessFlags srcAccess, VkAccessFlags dstAccess,
                                   VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage) {
    VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.oldLayout = from;
    barrier.newLayout = to;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    barrier.srcAccessMask = srcAccess;
    barrier.dstAccessMask = dstAccess;
    vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, 0, nullptr, 0, nullptr, 1, &barrier);
}

bool VulkanRenderer::uploadAtlas(const uint32_t* argb, int width, int height) {
    if (device_ == VK_NULL_HANDLE || !argb || width <= 0 || height <= 0) return false;

    const size_t pixelCount = static_cast<size_t>(width) * static_cast<size_t>(height);
    Buffer staging;
    if (!createBuffer(pixelCount * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                      VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, staging)) {
        return false;
    }

    // Android ARGB ints -> RGBA8 bytes.
    auto* dst = static_cast<uint8_t*>(staging.mapped);
    for (size_t i = 0; i < pixelCount; ++i) {
        const uint32_t p = argb[i];
        dst[i * 4 + 0] = static_cast<uint8_t>((p >> 16) & 0xFF);
        dst[i * 4 + 1] = static_cast<uint8_t>((p >> 8) & 0xFF);
        dst[i * 4 + 2] = static_cast<uint8_t>(p & 0xFF);
        dst[i * 4 + 3] = static_cast<uint8_t>((p >> 24) & 0xFF);
    }

    // Safe to replace the old atlas only once the GPU has finished with it.
    vkDeviceWaitIdle(device_);
    destroyImage(atlas_);
    if (!createAtlasImage(width, height, atlas_)) {
        destroyBuffer(staging);
        return false;
    }

    VkCommandBuffer cmd = beginOneTime();
    if (!cmd) {
        destroyBuffer(staging);
        return false;
    }
    recordBarrier(cmd, atlas_.image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                  0, VK_ACCESS_TRANSFER_WRITE_BIT,
                  VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    VkBufferImageCopy region{};
    region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.imageExtent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1};
    vkCmdCopyBufferToImage(cmd, staging.buffer, atlas_.image,
                           VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    recordBarrier(cmd, atlas_.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                  VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                  VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                  VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);

    const bool submitted = endOneTime(cmd);
    destroyBuffer(staging);
    if (!submitted) return false;

    // Point the descriptor at the new view.
    VkDescriptorImageInfo info{};
    info.sampler = sampler_;
    info.imageView = atlas_.view;
    info.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    write.dstSet = descriptorSet_;
    write.dstBinding = 0;
    write.descriptorCount = 1;
    write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    write.pImageInfo = &info;
    vkUpdateDescriptorSets(device_, 1, &write, 0, nullptr);
    return true;
}

}  // namespace emberfall
