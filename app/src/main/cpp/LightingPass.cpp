#include "LightingPass.h"

#include <android/log.h>

#include <vector>

#define LOG_TAG "EmberfallVK"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace emberfall {

namespace {

// Must match the LightBlock declared in lighting.frag.
struct LightBlock {
    float lights[LightingPass::kMaxLights][4];
    float params[4];  // x = darkness alpha, y = light count
};
static_assert(sizeof(LightBlock) == (32 * 4 + 4) * sizeof(float), "LightBlock layout mismatch");

uint32_t findMemoryType(VkPhysicalDevice physical, uint32_t typeBits, VkMemoryPropertyFlags props) {
    VkPhysicalDeviceMemoryProperties mem{};
    vkGetPhysicalDeviceMemoryProperties(physical, &mem);
    for (uint32_t i = 0; i < mem.memoryTypeCount; ++i) {
        if ((typeBits & (1u << i)) && (mem.memoryTypes[i].propertyFlags & props) == props) return i;
    }
    return UINT32_MAX;
}

}  // namespace

bool LightingPass::init(VkPhysicalDevice physical, VkDevice device, VkRenderPass renderPass,
                        VkShaderModule fullscreenVert, VkShaderModule lightingFrag, uint32_t framesInFlight) {
    if (!physical || !device || !fullscreenVert || !lightingFrag || framesInFlight == 0 || framesInFlight > 2) {
        return false;
    }
    physical_ = physical;
    device_ = device;
    framesInFlight_ = framesInFlight;

    VkDescriptorSetLayoutBinding binding{};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    binding.descriptorCount = 1;
    binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

    VkDescriptorSetLayoutCreateInfo layoutInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    layoutInfo.bindingCount = 1;
    layoutInfo.pBindings = &binding;
    if (vkCreateDescriptorSetLayout(device_, &layoutInfo, nullptr, &setLayout_) != VK_SUCCESS) return false;

    VkDescriptorPoolSize poolSize{VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, framesInFlight_};
    VkDescriptorPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    poolInfo.maxSets = framesInFlight_;
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    if (vkCreateDescriptorPool(device_, &poolInfo, nullptr, &pool_) != VK_SUCCESS) return false;

    // One uniform buffer per frame in flight, so writing lights never races the GPU.
    for (uint32_t f = 0; f < framesInFlight_; ++f) {
        VkBufferCreateInfo bi{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bi.size = sizeof(LightBlock);
        bi.usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
        bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(device_, &bi, nullptr, &buffers_[f]) != VK_SUCCESS) return false;

        VkMemoryRequirements req{};
        vkGetBufferMemoryRequirements(device_, buffers_[f], &req);
        const uint32_t type = findMemoryType(physical_, req.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (type == UINT32_MAX) return false;

        VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        alloc.allocationSize = req.size;
        alloc.memoryTypeIndex = type;
        if (vkAllocateMemory(device_, &alloc, nullptr, &memories_[f]) != VK_SUCCESS) return false;
        if (vkBindBufferMemory(device_, buffers_[f], memories_[f], 0) != VK_SUCCESS) return false;
        if (vkMapMemory(device_, memories_[f], 0, sizeof(LightBlock), 0, &mapped_[f]) != VK_SUCCESS) return false;
    }

    std::vector<VkDescriptorSetLayout> layouts(framesInFlight_, setLayout_);
    VkDescriptorSetAllocateInfo setAlloc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    setAlloc.descriptorPool = pool_;
    setAlloc.descriptorSetCount = framesInFlight_;
    setAlloc.pSetLayouts = layouts.data();
    if (vkAllocateDescriptorSets(device_, &setAlloc, sets_) != VK_SUCCESS) return false;

    for (uint32_t f = 0; f < framesInFlight_; ++f) {
        VkDescriptorBufferInfo info{buffers_[f], 0, sizeof(LightBlock)};
        VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        write.dstSet = sets_[f];
        write.dstBinding = 0;
        write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        write.pBufferInfo = &info;
        vkUpdateDescriptorSets(device_, 1, &write, 0, nullptr);
    }

    VkPipelineLayoutCreateInfo pl{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    pl.setLayoutCount = 1;
    pl.pSetLayouts = &setLayout_;
    if (vkCreatePipelineLayout(device_, &pl, nullptr, &pipelineLayout_) != VK_SUCCESS) return false;

    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = fullscreenVert;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = lightingFrag;
    stages[1].pName = "main";

    VkPipelineVertexInputStateCreateInfo vertexInput{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
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

    // Premultiplied alpha: result = src + dst * (1 - src.a).
    VkPipelineColorBlendAttachmentState blendAttachment{};
    blendAttachment.blendEnable = VK_TRUE;
    blendAttachment.srcColorBlendFactor = VK_BLEND_FACTOR_ONE;
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
    ci.renderPass = renderPass;
    ci.subpass = 0;
    if (vkCreateGraphicsPipelines(device_, VK_NULL_HANDLE, 1, &ci, nullptr, &pipeline_) != VK_SUCCESS) {
        LOGE("Lighting pipeline creation failed");
        return false;
    }
    return true;
}

void LightingPass::destroy() {
    if (device_ == VK_NULL_HANDLE) return;
    if (pipeline_) vkDestroyPipeline(device_, pipeline_, nullptr);
    if (pipelineLayout_) vkDestroyPipelineLayout(device_, pipelineLayout_, nullptr);
    if (pool_) vkDestroyDescriptorPool(device_, pool_, nullptr);  // frees the sets
    if (setLayout_) vkDestroyDescriptorSetLayout(device_, setLayout_, nullptr);
    for (uint32_t f = 0; f < framesInFlight_; ++f) {
        if (mapped_[f]) vkUnmapMemory(device_, memories_[f]);
        if (buffers_[f]) vkDestroyBuffer(device_, buffers_[f], nullptr);
        if (memories_[f]) vkFreeMemory(device_, memories_[f], nullptr);
    }
    *this = LightingPass();
}

void LightingPass::update(uint32_t frame, const float* lights, uint32_t count, float ambient) {
    active_ = ambient > 0.0f && frame < framesInFlight_;
    if (!active_) return;
    if (count > kMaxLights) count = kMaxLights;

    auto* block = static_cast<LightBlock*>(mapped_[frame]);
    for (uint32_t i = 0; i < count; ++i) {
        block->lights[i][0] = lights[i * 4 + 0];
        block->lights[i][1] = lights[i * 4 + 1];
        block->lights[i][2] = lights[i * 4 + 2];
        block->lights[i][3] = 0.0f;
    }
    block->params[0] = ambient;
    block->params[1] = static_cast<float>(count);
    block->params[2] = 0.0f;
    block->params[3] = 0.0f;
}

void LightingPass::record(VkCommandBuffer cmd, uint32_t frame, VkExtent2D extent) {
    if (!active_) return;

    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline_);
    VkViewport vp{0.0f, 0.0f, static_cast<float>(extent.width), static_cast<float>(extent.height), 0.0f, 1.0f};
    vkCmdSetViewport(cmd, 0, 1, &vp);
    VkRect2D scissor{{0, 0}, extent};
    vkCmdSetScissor(cmd, 0, 1, &scissor);
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout_,
                            0, 1, &sets_[frame], 0, nullptr);
    vkCmdDraw(cmd, 3, 1, 0, 0);
}

}  // namespace emberfall
