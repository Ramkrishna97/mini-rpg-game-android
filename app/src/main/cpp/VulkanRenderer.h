#pragma once

#include <android/asset_manager.h>
#include <android/native_window.h>
#include <vulkan/vulkan.h>

#include <cstdint>
#include <vector>

#include "LightingPass.h"

namespace emberfall {

/**
 * Minimal Vulkan 1.1 sprite renderer for Android.
 *
 * One swapchain, two frames in flight, one pipeline, one atlas texture, and
 * one indexed draw call per frame. Sprites are submitted as 12 floats each:
 *   x y w h   (screen pixels, top-left origin)
 *   u0 v0 u1 v1 (atlas UV rectangle)
 *   r g b a   (tint, 0..1)
 *
 * All methods must be called from the same thread (the game loop thread).
 */
class VulkanRenderer {
public:
    static constexpr uint32_t kMaxSprites = 16384;
    static constexpr uint32_t kFramesInFlight = 2;

    bool init(ANativeWindow* window, AAssetManager* assets);
    void shutdown();

    bool drawFrame(const float* sprites, uint32_t count, const float clearColor[3],
                   const float* lights, uint32_t lightCount, float ambient);
    bool uploadAtlas(const uint32_t* argb, int width, int height);

    /** Marks the swapchain for rebuild on the next frame (call on resize). */
    void requestRecreate() { needsRecreate_ = true; }

    bool ready() const { return ready_; }

private:
    struct Vertex {
        float x, y, u, v;
        float r, g, b, a;
    };

    struct Buffer {
        VkBuffer buffer = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        void* mapped = nullptr;
    };

    struct Image {
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
    };

    // Setup, in call order.
    bool createInstance();
    bool createSurface();
    bool pickPhysicalDevice();
    bool createDevice();
    bool createCommandResources();
    bool createFrameSync();
    bool createStaticBuffers();
    bool createDescriptorResources();
    bool createRenderPass();
    bool createPipeline();

    // Swapchain lifecycle.
    bool createSwapchain();
    void destroySwapchain();
    bool rebuildSwapchain();

    // Helpers.
    bool createBuffer(VkDeviceSize size, VkBufferUsageFlags usage, VkMemoryPropertyFlags props, Buffer& out);
    void destroyBuffer(Buffer& buffer);
    bool createAtlasImage(int width, int height, Image& out);
    void destroyImage(Image& image);
    uint32_t findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags props) const;
    VkShaderModule loadShader(const char* name);
    VkCommandBuffer beginOneTime();
    bool endOneTime(VkCommandBuffer cmd);
    void recordBarrier(VkCommandBuffer cmd, VkImage image,
                       VkImageLayout from, VkImageLayout to,
                       VkAccessFlags srcAccess, VkAccessFlags dstAccess,
                       VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage);
    void writeSprites(uint32_t frame, const float* sprites, uint32_t count);
    void recordCommands(uint32_t frame, uint32_t imageIndex, uint32_t count, const float clearColor[3]);

    ANativeWindow* window_ = nullptr;
    AAssetManager* assets_ = nullptr;
    bool ready_ = false;
    bool needsRecreate_ = false;
    uint32_t frame_ = 0;

    VkInstance instance_ = VK_NULL_HANDLE;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    VkPhysicalDevice physical_ = VK_NULL_HANDLE;
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue queue_ = VK_NULL_HANDLE;
    uint32_t queueFamily_ = 0;
    VkSurfaceFormatKHR format_{};

    VkCommandPool commandPool_ = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffers_[kFramesInFlight]{};
    VkSemaphore imageAvailable_[kFramesInFlight]{};
    VkFence inFlight_[kFramesInFlight]{};

    VkSwapchainKHR swapchain_ = VK_NULL_HANDLE;
    VkExtent2D extent_{};
    std::vector<VkImageView> swapViews_;
    std::vector<VkFramebuffer> framebuffers_;
    std::vector<VkSemaphore> renderFinished_;

    VkRenderPass renderPass_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout setLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool_ = VK_NULL_HANDLE;
    VkDescriptorSet descriptorSet_ = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline pipeline_ = VK_NULL_HANDLE;
    VkSampler sampler_ = VK_NULL_HANDLE;

    Buffer vertexBuffers_[kFramesInFlight];
    Buffer indexBuffer_;
    Image atlas_;
    LightingPass lighting_;
    bool lightingReady_ = false;
};

}  // namespace emberfall
