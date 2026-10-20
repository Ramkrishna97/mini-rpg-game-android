#pragma once

#include <vulkan/vulkan.h>

#include <cstdint>

namespace emberfall {

/**
 * Full-screen night pass. Draws darkness over the world with soft holes
 * around light sources. Blended as premultiplied alpha.
 *
 * Lights go in a per-frame uniform buffer, so this pass works after the
 * sprite pass without touching the sprite pipeline.
 */
class LightingPass {
public:
    static constexpr uint32_t kMaxLights = 32;

    bool init(VkPhysicalDevice physical, VkDevice device, VkRenderPass renderPass,
              VkShaderModule fullscreenVert, VkShaderModule lightingFrag, uint32_t framesInFlight);
    void destroy();

    /** Call after the frame's fence has been waited on. [lights]: 4 floats per light. */
    void update(uint32_t frame, const float* lights, uint32_t count, float ambient);

    /** Records the pass into an active render pass. Does nothing if ambient was 0. */
    void record(VkCommandBuffer cmd, uint32_t frame, VkExtent2D extent);

private:
    VkPhysicalDevice physical_ = VK_NULL_HANDLE;
    VkDevice device_ = VK_NULL_HANDLE;
    uint32_t framesInFlight_ = 0;
    bool active_ = false;

    VkDescriptorSetLayout setLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool pool_ = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline pipeline_ = VK_NULL_HANDLE;

    VkDescriptorSet sets_[2]{};
    VkBuffer buffers_[2]{};
    VkDeviceMemory memories_[2]{};
    void* mapped_[2]{};
};

}  // namespace emberfall
