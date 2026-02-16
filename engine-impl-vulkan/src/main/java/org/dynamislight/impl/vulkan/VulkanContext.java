package org.dynamislight.impl.vulkan;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.model.VulkanTexturePixelData;
import org.dynamislight.impl.vulkan.command.VulkanCommandSubmitter;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorRingPolicy;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
import org.dynamislight.impl.vulkan.shader.VulkanShaderCompiler;
import org.dynamislight.impl.vulkan.shader.VulkanShaderSources;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowMatrixBuilder;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainSelector;

import static org.dynamislight.impl.vulkan.math.VulkanMath.clamp01;
import static org.dynamislight.impl.vulkan.math.VulkanMath.floatEquals;
import static org.dynamislight.impl.vulkan.math.VulkanMath.identityMatrix;
import static org.dynamislight.impl.vulkan.math.VulkanMath.normalize3;
import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memAllocPointer;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_info;
import static org.lwjgl.stb.STBImage.stbi_is_hdr;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_loadf;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_UNIFORM_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_ERROR_INITIALIZATION_FAILED;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TRUE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.common.texture.KtxDecodeUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkViewport;

final class VulkanContext {
    private static final int VERTEX_STRIDE_FLOATS = 11;
    private static final int VERTEX_STRIDE_BYTES = VERTEX_STRIDE_FLOATS * Float.BYTES;
    private static final int DEFAULT_FRAMES_IN_FLIGHT = 3;
    private static final int DEFAULT_MAX_DYNAMIC_SCENE_OBJECTS = 2048;
    private static final int DEFAULT_MAX_PENDING_UPLOAD_RANGES = 64;
    private static final int DEFAULT_DYNAMIC_UPLOAD_MERGE_GAP_OBJECTS = 1;
    private static final int DEFAULT_DYNAMIC_OBJECT_SOFT_LIMIT = 1536;
    private static final int MAX_PENDING_UPLOAD_RANGES_HARD_CAP = 4096;
    private static final int MAX_SHADOW_CASCADES = 4;
    private static final int POINT_SHADOW_FACES = 6;
    private static final int MAX_SHADOW_MATRICES = 6;
    private static final int GLOBAL_SCENE_UNIFORM_BYTES = 784;
    private static final int OBJECT_UNIFORM_BYTES = 96;
    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private int graphicsQueueFamilyIndex = -1;
    private long window = VK_NULL_HANDLE;
    private long surface = VK_NULL_HANDLE;
    private long swapchain = VK_NULL_HANDLE;
    private int swapchainImageFormat = VK_FORMAT_B8G8R8A8_SRGB;
    private int swapchainWidth = 1;
    private int swapchainHeight = 1;
    private long[] swapchainImages = new long[0];
    private long[] swapchainImageViews = new long[0];
    private int depthFormat = VK_FORMAT_D32_SFLOAT;
    private long[] depthImages = new long[0];
    private long[] depthMemories = new long[0];
    private long[] depthImageViews = new long[0];
    private long shadowDepthImage = VK_NULL_HANDLE;
    private long shadowDepthMemory = VK_NULL_HANDLE;
    private long shadowDepthImageView = VK_NULL_HANDLE;
    private long[] shadowDepthLayerImageViews = new long[0];
    private long shadowSampler = VK_NULL_HANDLE;
    private long shadowRenderPass = VK_NULL_HANDLE;
    private long shadowPipelineLayout = VK_NULL_HANDLE;
    private long shadowPipeline = VK_NULL_HANDLE;
    private long[] shadowFramebuffers = new long[0];
    private long renderPass = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long graphicsPipeline = VK_NULL_HANDLE;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long textureDescriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    private long[] frameDescriptorSets = new long[0];
    private long textureDescriptorPool = VK_NULL_HANDLE;
    private long sceneGlobalUniformBuffer = VK_NULL_HANDLE;
    private long sceneGlobalUniformMemory = VK_NULL_HANDLE;
    private long sceneGlobalUniformStagingBuffer = VK_NULL_HANDLE;
    private long sceneGlobalUniformStagingMemory = VK_NULL_HANDLE;
    private long sceneGlobalUniformStagingMappedAddress;
    private long objectUniformBuffer = VK_NULL_HANDLE;
    private long objectUniformMemory = VK_NULL_HANDLE;
    private long objectUniformStagingBuffer = VK_NULL_HANDLE;
    private long objectUniformStagingMemory = VK_NULL_HANDLE;
    private long objectUniformStagingMappedAddress;
    private int uniformStrideBytes = OBJECT_UNIFORM_BYTES;
    private int uniformFrameSpanBytes = OBJECT_UNIFORM_BYTES;
    private int globalUniformFrameSpanBytes = GLOBAL_SCENE_UNIFORM_BYTES;
    private int framesInFlight = DEFAULT_FRAMES_IN_FLIGHT;
    private int maxDynamicSceneObjects = DEFAULT_MAX_DYNAMIC_SCENE_OBJECTS;
    private int maxPendingUploadRanges = DEFAULT_MAX_PENDING_UPLOAD_RANGES;
    private int dynamicUploadMergeGapObjects = DEFAULT_DYNAMIC_UPLOAD_MERGE_GAP_OBJECTS;
    private int dynamicObjectSoftLimit = DEFAULT_DYNAMIC_OBJECT_SOFT_LIMIT;
    private int maxObservedDynamicObjects;
    private long[] framebuffers = new long[0];
    private long commandPool = VK_NULL_HANDLE;
    private VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];
    private long[] imageAvailableSemaphores = new long[0];
    private long[] renderFinishedSemaphores = new long[0];
    private long[] renderFences = new long[0];
    private int currentFrame;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private long sceneReuseHitCount;
    private long sceneReorderReuseCount;
    private long sceneTextureRebindCount;
    private long sceneFullRebuildCount;
    private long meshBufferRebuildCount;
    private long descriptorPoolBuildCount;
    private long descriptorPoolRebuildCount;
    private long descriptorRingReuseHitCount;
    private long descriptorRingGrowthRebuildCount;
    private long descriptorRingSteadyRebuildCount;
    private long descriptorRingPoolReuseCount;
    private long descriptorRingPoolResetFailureCount;
    private long descriptorRingCapBypassCount;
    private int descriptorRingSetCapacity;
    private int descriptorRingPeakSetCapacity;
    private int descriptorRingActiveSetCount;
    private int descriptorRingWasteSetCount;
    private int descriptorRingPeakWasteSetCount;
    private int descriptorRingMaxSetCapacity = 4096;
    private long estimatedGpuMemoryBytes;
    private int lastFrameUniformUploadBytes;
    private int maxFrameUniformUploadBytes;
    private int lastFrameGlobalUploadBytes;
    private int maxFrameGlobalUploadBytes;
    private int lastFrameUniformObjectCount;
    private int maxFrameUniformObjectCount;
    private int lastFrameUniformUploadRanges;
    private int maxFrameUniformUploadRanges;
    private int lastFrameUniformUploadStartObject;
    private long globalStateRevision = 1;
    private long sceneStateRevision = 1;
    private long[] frameGlobalRevisionApplied = new long[DEFAULT_FRAMES_IN_FLIGHT];
    private long[] frameSceneRevisionApplied = new long[DEFAULT_FRAMES_IN_FLIGHT];
    private int[] pendingSceneDirtyStarts = new int[DEFAULT_MAX_PENDING_UPLOAD_RANGES];
    private int[] pendingSceneDirtyEnds = new int[DEFAULT_MAX_PENDING_UPLOAD_RANGES];
    private int pendingSceneDirtyRangeCount;
    private long pendingUploadSrcOffset = -1L;
    private long pendingUploadDstOffset = -1L;
    private int pendingUploadByteCount;
    private int pendingUploadObjectCount;
    private int pendingUploadStartObject;
    private long[] pendingUploadSrcOffsets = new long[DEFAULT_MAX_PENDING_UPLOAD_RANGES];
    private long[] pendingUploadDstOffsets = new long[DEFAULT_MAX_PENDING_UPLOAD_RANGES];
    private int[] pendingUploadByteCounts = new int[DEFAULT_MAX_PENDING_UPLOAD_RANGES];
    private int pendingUploadRangeCount;
    private long pendingUploadRangeOverflowCount;
    private long pendingGlobalUploadSrcOffset = -1L;
    private long pendingGlobalUploadDstOffset = -1L;
    private int pendingGlobalUploadByteCount;
    private final List<VulkanGpuMesh> gpuMeshes = new ArrayList<>();
    private List<VulkanSceneMeshData> pendingSceneMeshes = List.of(VulkanSceneMeshData.defaultTriangle());
    private float[] viewMatrix = identityMatrix();
    private float[] projMatrix = identityMatrix();
    private float dirLightDirX = 0.35f;
    private float dirLightDirY = -1.0f;
    private float dirLightDirZ = 0.25f;
    private float dirLightColorR = 1.0f;
    private float dirLightColorG = 0.98f;
    private float dirLightColorB = 0.95f;
    private float dirLightIntensity = 1.0f;
    private float pointLightPosX = 0.0f;
    private float pointLightPosY = 1.2f;
    private float pointLightPosZ = 1.8f;
    private float pointLightColorR = 0.95f;
    private float pointLightColorG = 0.62f;
    private float pointLightColorB = 0.22f;
    private float pointLightIntensity = 1.0f;
    private float pointLightDirX = 0.0f;
    private float pointLightDirY = -1.0f;
    private float pointLightDirZ = 0.0f;
    private float pointLightInnerCos = 1.0f;
    private float pointLightOuterCos = 1.0f;
    private float pointLightIsSpot;
    private boolean pointShadowEnabled;
    private float pointShadowFarPlane = 15f;
    private boolean shadowEnabled;
    private float shadowStrength = 0.45f;
    private float shadowBias = 0.0015f;
    private int shadowPcfRadius = 1;
    private int shadowCascadeCount = 1;
    private int shadowMapResolution = 1024;
    private final float[] shadowCascadeSplitNdc = new float[]{1f, 1f, 1f};
    private final float[][] shadowLightViewProjMatrices = new float[][]{
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix()
    };
    private boolean fogEnabled;
    private float fogR = 0.5f;
    private float fogG = 0.5f;
    private float fogB = 0.5f;
    private float fogDensity;
    private int fogSteps;
    private boolean smokeEnabled;
    private float smokeR = 0.6f;
    private float smokeG = 0.6f;
    private float smokeB = 0.6f;
    private float smokeIntensity;
    private boolean iblEnabled;
    private float iblDiffuseStrength;
    private float iblSpecularStrength;
    private float iblPrefilterStrength;
    private Path iblIrradiancePath;
    private Path iblRadiancePath;
    private Path iblBrdfLutPath;
    private VulkanGpuTexture iblIrradianceTexture;
    private VulkanGpuTexture iblRadianceTexture;
    private VulkanGpuTexture iblBrdfLutTexture;
    private boolean tonemapEnabled;
    private float tonemapExposure = 1.0f;
    private float tonemapGamma = 2.2f;
    private boolean bloomEnabled;
    private float bloomThreshold = 1.0f;
    private float bloomStrength = 0.8f;
    private boolean postOffscreenRequested;
    private boolean postOffscreenActive;
    private long offscreenColorImage = VK_NULL_HANDLE;
    private long offscreenColorMemory = VK_NULL_HANDLE;
    private long offscreenColorImageView = VK_NULL_HANDLE;
    private long offscreenColorSampler = VK_NULL_HANDLE;
    private long postRenderPass = VK_NULL_HANDLE;
    private long postPipelineLayout = VK_NULL_HANDLE;
    private long postGraphicsPipeline = VK_NULL_HANDLE;
    private long postDescriptorSetLayout = VK_NULL_HANDLE;
    private long postDescriptorPool = VK_NULL_HANDLE;
    private long postDescriptorSet = VK_NULL_HANDLE;
    private long[] postFramebuffers = new long[0];
    private boolean postIntermediateInitialized;

    VulkanContext() {
        reallocateFrameTracking();
        reallocateUploadRangeTracking();
    }

    void configureFrameResources(int framesInFlight, int maxDynamicSceneObjects, int maxPendingUploadRanges) {
        if (device != null) {
            return;
        }
        this.framesInFlight = clamp(framesInFlight, 2, 6);
        this.maxDynamicSceneObjects = clamp(maxDynamicSceneObjects, 256, 8192);
        this.maxPendingUploadRanges = clamp(maxPendingUploadRanges, 8, 2048);
        reallocateFrameTracking();
        reallocateUploadRangeTracking();
    }

    void configureDynamicUploadMergeGap(int mergeGapObjects) {
        if (device != null) {
            return;
        }
        dynamicUploadMergeGapObjects = clamp(mergeGapObjects, 0, 32);
    }

    void configureDynamicObjectSoftLimit(int softLimit) {
        if (device != null) {
            return;
        }
        dynamicObjectSoftLimit = clamp(softLimit, 128, 8192);
    }

    void configureDescriptorRing(int maxSetCapacity) {
        if (device != null) {
            return;
        }
        descriptorRingMaxSetCapacity = clamp(maxSetCapacity, 256, 32768);
    }

    int configuredFramesInFlight() {
        return framesInFlight;
    }

    int configuredMaxDynamicSceneObjects() {
        return maxDynamicSceneObjects;
    }

    int configuredMaxPendingUploadRanges() {
        return maxPendingUploadRanges;
    }

    int configuredDescriptorRingMaxSetCapacity() {
        return descriptorRingMaxSetCapacity;
    }

    void initialize(String appName, int width, int height, boolean windowVisible) throws EngineException {
        initWindow(appName, width, height, windowVisible);
        try (MemoryStack stack = stackPush()) {
            createInstance(stack, appName);
            createSurface(stack);
            selectPhysicalDevice(stack);
            createLogicalDevice(stack);
            createDescriptorResources(stack);
            createSwapchainResources(stack, width, height);
            createCommandResources(stack);
            createShadowResources(stack);
            createSyncObjects(stack);
            uploadSceneMeshes(stack, pendingSceneMeshes);
        }
    }

    VulkanFrameMetrics renderFrame() throws EngineException {
        long start = System.nanoTime();
        if (device != null && graphicsQueue != null && commandBuffers.length > 0 && swapchain != VK_NULL_HANDLE) {
            try (MemoryStack stack = stackPush()) {
                int frameIdx = currentFrame % commandBuffers.length;
                int acquireResult = acquireNextImage(stack, frameIdx);
                if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR || acquireResult == VK_SUBOPTIMAL_KHR) {
                    recreateSwapchainFromWindow();
                }
                currentFrame = (currentFrame + 1) % Math.max(1, commandBuffers.length);
            }
        }
        double cpuMs = (System.nanoTime() - start) / 1_000_000.0;
        return new VulkanFrameMetrics(cpuMs, cpuMs * 0.7, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, estimatedGpuMemoryBytes);
    }

    void resize(int width, int height) throws EngineException {
        if (device == null || swapchain == VK_NULL_HANDLE) {
            return;
        }
        recreateSwapchain(Math.max(1, width), Math.max(1, height));
    }

    void setPlannedWorkload(long drawCalls, long triangles, long visibleObjects) {
        plannedDrawCalls = Math.max(1, drawCalls);
        plannedTriangles = Math.max(1, triangles);
        plannedVisibleObjects = Math.max(1, visibleObjects);
    }

    SceneReuseStats sceneReuseStats() {
        return new SceneReuseStats(
                sceneReuseHitCount,
                sceneReorderReuseCount,
                sceneTextureRebindCount,
                sceneFullRebuildCount,
                meshBufferRebuildCount,
                descriptorPoolBuildCount,
                descriptorPoolRebuildCount
        );
    }

    FrameResourceProfile frameResourceProfile() {
        return new FrameResourceProfile(
                framesInFlight,
                frameDescriptorSets.length,
                uniformStrideBytes,
                uniformFrameSpanBytes,
                globalUniformFrameSpanBytes,
                maxDynamicSceneObjects,
                pendingSceneDirtyStarts.length,
                lastFrameGlobalUploadBytes,
                maxFrameGlobalUploadBytes,
                lastFrameUniformUploadBytes,
                maxFrameUniformUploadBytes,
                lastFrameUniformObjectCount,
                maxFrameUniformObjectCount,
                lastFrameUniformUploadRanges,
                maxFrameUniformUploadRanges,
                lastFrameUniformUploadStartObject,
                pendingUploadRangeOverflowCount,
                descriptorRingSetCapacity,
                descriptorRingPeakSetCapacity,
                descriptorRingActiveSetCount,
                descriptorRingWasteSetCount,
                descriptorRingPeakWasteSetCount,
                descriptorRingMaxSetCapacity,
                descriptorRingReuseHitCount,
                descriptorRingGrowthRebuildCount,
                descriptorRingSteadyRebuildCount,
                descriptorRingPoolReuseCount,
                descriptorRingPoolResetFailureCount,
                descriptorRingCapBypassCount,
                dynamicUploadMergeGapObjects,
                dynamicObjectSoftLimit,
                maxObservedDynamicObjects,
                objectUniformStagingMappedAddress != 0L && sceneGlobalUniformStagingMappedAddress != 0L
        );
    }

    ShadowCascadeProfile shadowCascadeProfile() {
        return new ShadowCascadeProfile(
                shadowEnabled,
                shadowCascadeCount,
                shadowMapResolution,
                shadowPcfRadius,
                shadowBias,
                shadowCascadeSplitNdc[0],
                shadowCascadeSplitNdc[1],
                shadowCascadeSplitNdc[2]
        );
    }

    PostProcessPipelineProfile postProcessPipelineProfile() {
        String mode = postOffscreenActive ? "offscreen" : "shader-fallback";
        return new PostProcessPipelineProfile(postOffscreenRequested, postOffscreenActive, mode);
    }

    void setSceneMeshes(List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        List<VulkanSceneMeshData> safe = (sceneMeshes == null || sceneMeshes.isEmpty())
                ? List.of(VulkanSceneMeshData.defaultTriangle())
                : List.copyOf(sceneMeshes);
        pendingSceneMeshes = safe;
        if (device == null) {
            return;
        }
        if (canReuseGpuMeshes(safe)) {
            sceneReuseHitCount++;
            descriptorRingReuseHitCount++;
            updateDynamicSceneState(safe);
            return;
        }
        if (canReuseGeometryBuffers(safe)) {
            sceneReuseHitCount++;
            sceneTextureRebindCount++;
            descriptorRingReuseHitCount++;
            rebindSceneTexturesAndDynamicState(safe);
            return;
        }
        sceneFullRebuildCount++;
        try (MemoryStack stack = stackPush()) {
            uploadSceneMeshes(stack, safe);
        }
        markSceneStateDirty(0, Math.max(0, safe.size() - 1));
    }

    void setCameraMatrices(float[] view, float[] proj) {
        boolean changed = false;
        if (view != null && view.length == 16) {
            if (!Arrays.equals(viewMatrix, view)) {
                viewMatrix = view.clone();
                changed = true;
            }
        }
        if (proj != null && proj.length == 16) {
            if (!Arrays.equals(projMatrix, proj)) {
                projMatrix = proj.clone();
                changed = true;
            }
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setLightingParameters(
            float[] dirDir,
            float[] dirColor,
            float dirIntensity,
            float[] pointPos,
            float[] pointColor,
            float pointIntensity,
            float[] pointDirection,
            float pointInnerCos,
            float pointOuterCos,
            boolean pointIsSpot,
            float pointRange,
            boolean pointCastsShadows
    ) {
        boolean changed = false;
        if (dirDir != null && dirDir.length == 3) {
            if (!floatEquals(dirLightDirX, dirDir[0]) || !floatEquals(dirLightDirY, dirDir[1]) || !floatEquals(dirLightDirZ, dirDir[2])) {
                dirLightDirX = dirDir[0];
                dirLightDirY = dirDir[1];
                dirLightDirZ = dirDir[2];
                changed = true;
            }
        }
        if (dirColor != null && dirColor.length == 3) {
            if (!floatEquals(dirLightColorR, dirColor[0]) || !floatEquals(dirLightColorG, dirColor[1]) || !floatEquals(dirLightColorB, dirColor[2])) {
                dirLightColorR = dirColor[0];
                dirLightColorG = dirColor[1];
                dirLightColorB = dirColor[2];
                changed = true;
            }
        }
        float clampedDirIntensity = Math.max(0f, dirIntensity);
        if (!floatEquals(dirLightIntensity, clampedDirIntensity)) {
            dirLightIntensity = clampedDirIntensity;
            changed = true;
        }
        if (pointPos != null && pointPos.length == 3) {
            if (!floatEquals(pointLightPosX, pointPos[0]) || !floatEquals(pointLightPosY, pointPos[1]) || !floatEquals(pointLightPosZ, pointPos[2])) {
                pointLightPosX = pointPos[0];
                pointLightPosY = pointPos[1];
                pointLightPosZ = pointPos[2];
                changed = true;
            }
        }
        if (pointColor != null && pointColor.length == 3) {
            if (!floatEquals(pointLightColorR, pointColor[0]) || !floatEquals(pointLightColorG, pointColor[1]) || !floatEquals(pointLightColorB, pointColor[2])) {
                pointLightColorR = pointColor[0];
                pointLightColorG = pointColor[1];
                pointLightColorB = pointColor[2];
                changed = true;
            }
        }
        float clampedPointIntensity = Math.max(0f, pointIntensity);
        if (!floatEquals(pointLightIntensity, clampedPointIntensity)) {
            pointLightIntensity = clampedPointIntensity;
            changed = true;
        }
        if (pointDirection != null && pointDirection.length == 3) {
            float[] normalized = normalize3(pointDirection[0], pointDirection[1], pointDirection[2]);
            if (!floatEquals(pointLightDirX, normalized[0]) || !floatEquals(pointLightDirY, normalized[1]) || !floatEquals(pointLightDirZ, normalized[2])) {
                pointLightDirX = normalized[0];
                pointLightDirY = normalized[1];
                pointLightDirZ = normalized[2];
                changed = true;
            }
        }
        float clampedInner = clamp01(pointInnerCos);
        float clampedOuter = clamp01(pointOuterCos);
        if (clampedOuter > clampedInner) {
            clampedOuter = clampedInner;
        }
        if (!floatEquals(pointLightInnerCos, clampedInner) || !floatEquals(pointLightOuterCos, clampedOuter)) {
            pointLightInnerCos = clampedInner;
            pointLightOuterCos = clampedOuter;
            changed = true;
        }
        float spotValue = pointIsSpot ? 1f : 0f;
        if (!floatEquals(pointLightIsSpot, spotValue)) {
            pointLightIsSpot = spotValue;
            changed = true;
        }
        float clampedRange = Math.max(1.0f, pointRange);
        if (!floatEquals(pointShadowFarPlane, clampedRange)) {
            pointShadowFarPlane = clampedRange;
            changed = true;
        }
        if (pointShadowEnabled != pointCastsShadows) {
            pointShadowEnabled = pointCastsShadows;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setShadowParameters(boolean enabled, float strength, float bias, int pcfRadius, int cascadeCount, int mapResolution)
            throws EngineException {
        boolean changed = false;
        if (shadowEnabled != enabled) {
            shadowEnabled = enabled;
            changed = true;
        }
        float clampedStrength = Math.max(0f, Math.min(1f, strength));
        if (!floatEquals(shadowStrength, clampedStrength)) {
            shadowStrength = clampedStrength;
            changed = true;
        }
        float clampedBias = Math.max(0.00002f, bias);
        if (!floatEquals(shadowBias, clampedBias)) {
            shadowBias = clampedBias;
            changed = true;
        }
        int clampedPcf = Math.max(0, pcfRadius);
        if (shadowPcfRadius != clampedPcf) {
            shadowPcfRadius = clampedPcf;
            changed = true;
        }
        int clampedCascadeCount = Math.max(1, Math.min(MAX_SHADOW_MATRICES, cascadeCount));
        if (shadowCascadeCount != clampedCascadeCount) {
            shadowCascadeCount = clampedCascadeCount;
            changed = true;
        }
        int clampedResolution = Math.max(256, Math.min(4096, mapResolution));
        if (shadowMapResolution != clampedResolution) {
            shadowMapResolution = clampedResolution;
            changed = true;
            if (device != null) {
                vkDeviceWaitIdle(device);
                try (MemoryStack stack = stackPush()) {
                    destroyShadowResources();
                    createShadowResources(stack);
                    if (!gpuMeshes.isEmpty()) {
                        createTextureDescriptorSets(stack);
                    }
                }
            }
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setFogParameters(boolean enabled, float r, float g, float b, float density, int steps) {
        boolean changed = false;
        if (fogEnabled != enabled) {
            fogEnabled = enabled;
            changed = true;
        }
        if (!floatEquals(fogR, r) || !floatEquals(fogG, g) || !floatEquals(fogB, b)) {
            fogR = r;
            fogG = g;
            fogB = b;
            changed = true;
        }
        float clampedDensity = Math.max(0f, density);
        if (!floatEquals(fogDensity, clampedDensity)) {
            fogDensity = clampedDensity;
            changed = true;
        }
        int clampedSteps = Math.max(0, steps);
        if (fogSteps != clampedSteps) {
            fogSteps = clampedSteps;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setSmokeParameters(boolean enabled, float r, float g, float b, float intensity) {
        boolean changed = false;
        if (smokeEnabled != enabled) {
            smokeEnabled = enabled;
            changed = true;
        }
        if (!floatEquals(smokeR, r) || !floatEquals(smokeG, g) || !floatEquals(smokeB, b)) {
            smokeR = r;
            smokeG = g;
            smokeB = b;
            changed = true;
        }
        float clampedIntensity = Math.max(0f, Math.min(1f, intensity));
        if (!floatEquals(smokeIntensity, clampedIntensity)) {
            smokeIntensity = clampedIntensity;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setIblParameters(boolean enabled, float diffuseStrength, float specularStrength, float prefilterStrength) {
        boolean changed = false;
        if (iblEnabled != enabled) {
            iblEnabled = enabled;
            changed = true;
        }
        float clampedDiffuse = Math.max(0f, Math.min(2.0f, diffuseStrength));
        float clampedSpecular = Math.max(0f, Math.min(2.0f, specularStrength));
        float clampedPrefilter = Math.max(0f, Math.min(1.0f, prefilterStrength));
        if (!floatEquals(iblDiffuseStrength, clampedDiffuse)
                || !floatEquals(iblSpecularStrength, clampedSpecular)
                || !floatEquals(iblPrefilterStrength, clampedPrefilter)) {
            iblDiffuseStrength = clampedDiffuse;
            iblSpecularStrength = clampedSpecular;
            iblPrefilterStrength = clampedPrefilter;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setIblTexturePaths(Path irradiancePath, Path radiancePath, Path brdfLutPath) {
        iblIrradiancePath = irradiancePath;
        iblRadiancePath = radiancePath;
        iblBrdfLutPath = brdfLutPath;
    }

    void setPostProcessParameters(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
        boolean changed = false;
        if (this.tonemapEnabled != tonemapEnabled) {
            this.tonemapEnabled = tonemapEnabled;
            changed = true;
        }
        float clampedExposure = Math.max(0.05f, Math.min(8.0f, exposure));
        float clampedGamma = Math.max(0.8f, Math.min(3.2f, gamma));
        if (!floatEquals(tonemapExposure, clampedExposure) || !floatEquals(tonemapGamma, clampedGamma)) {
            tonemapExposure = clampedExposure;
            tonemapGamma = clampedGamma;
            changed = true;
        }
        if (this.bloomEnabled != bloomEnabled) {
            this.bloomEnabled = bloomEnabled;
            changed = true;
        }
        float clampedThreshold = Math.max(0f, Math.min(4.0f, bloomThreshold));
        float clampedStrength = Math.max(0f, Math.min(2.0f, bloomStrength));
        if (!floatEquals(this.bloomThreshold, clampedThreshold) || !floatEquals(this.bloomStrength, clampedStrength)) {
            this.bloomThreshold = clampedThreshold;
            this.bloomStrength = clampedStrength;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void configurePostProcessMode(boolean requestOffscreen) {
        boolean changed = postOffscreenRequested != requestOffscreen || postOffscreenActive;
        postOffscreenRequested = requestOffscreen;
        // Keep existing shader-driven post as safe fallback until Vulkan offscreen chain is fully wired.
        postOffscreenActive = false;
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void shutdown() {
        if (device != null) {
            vkDeviceWaitIdle(device);
        }

        if (device != null) {
            for (long fence : renderFences) {
                if (fence != VK_NULL_HANDLE) {
                    vkDestroyFence(device, fence, null);
                }
            }
            for (long sem : renderFinishedSemaphores) {
                if (sem != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, sem, null);
                }
            }
            for (long sem : imageAvailableSemaphores) {
                if (sem != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, sem, null);
                }
            }
        }
        renderFences = new long[0];
        renderFinishedSemaphores = new long[0];
        imageAvailableSemaphores = new long[0];

        commandBuffers = new VkCommandBuffer[0];
        destroySceneMeshes();
        destroyShadowResources();
        if (commandPool != VK_NULL_HANDLE && device != null) {
            vkDestroyCommandPool(device, commandPool, null);
            commandPool = VK_NULL_HANDLE;
        }

        destroySwapchainResources();
        destroyDescriptorResources();

        if (device != null) {
            vkDestroyDevice(device, null);
            device = null;
        }
        if (surface != VK_NULL_HANDLE && instance != null) {
            vkDestroySurfaceKHR(instance, surface, null);
            surface = VK_NULL_HANDLE;
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
            instance = null;
        }
        physicalDevice = null;
        graphicsQueue = null;
        graphicsQueueFamilyIndex = -1;

        if (window != VK_NULL_HANDLE) {
            glfwDestroyWindow(window);
            window = VK_NULL_HANDLE;
        }
        glfwTerminate();
        GLFWErrorCallback callback = org.lwjgl.glfw.GLFW.glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private void initWindow(String appName, int width, int height, boolean windowVisible) throws EngineException {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "GLFW initialization failed", false);
        }
        if (!glfwVulkanSupported()) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "GLFW Vulkan not supported", false);
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_VISIBLE, windowVisible ? GLFW_TRUE : GLFW_FALSE);
        window = glfwCreateWindow(Math.max(1, width), Math.max(1, height), appName, VK_NULL_HANDLE, VK_NULL_HANDLE);
        if (window == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Failed to create Vulkan window", false);
        }
    }

    private void createInstance(MemoryStack stack, String appName) throws EngineException {
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(appName))
                .applicationVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                .pEngineName(stack.UTF8("DynamicLightEngine"))
                .engineVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                .apiVersion(VK10.VK_MAKE_API_VERSION(0, 1, 1, 0));

        PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No required Vulkan instance extensions from GLFW", false);
        }

        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(requiredExtensions);

        PointerBuffer pInstance = stack.mallocPointer(1);
        int result = vkCreateInstance(createInfo, null, pInstance);
        if (result != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateInstance failed: " + result,
                    result == VK_ERROR_INITIALIZATION_FAILED
            );
        }
        instance = new VkInstance(pInstance.get(0), createInfo);
    }

    private void createSurface(MemoryStack stack) throws EngineException {
        var pSurface = stack.longs(VK_NULL_HANDLE);
        int result = glfwCreateWindowSurface(instance, window, null, pSurface);
        if (result != VK_SUCCESS || pSurface.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "glfwCreateWindowSurface failed: " + result, false);
        }
        surface = pSurface.get(0);
    }

    private void selectPhysicalDevice(MemoryStack stack) throws EngineException {
        var pCount = stack.ints(0);
        int countResult = vkEnumeratePhysicalDevices(instance, pCount, null);
        if (countResult != VK_SUCCESS || pCount.get(0) == 0) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "No Vulkan physical devices available: " + countResult,
                    false
            );
        }

        PointerBuffer devices = stack.mallocPointer(pCount.get(0));
        int enumerateResult = vkEnumeratePhysicalDevices(instance, pCount, devices);
        if (enumerateResult != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Failed to enumerate Vulkan physical devices: " + enumerateResult,
                    false
            );
        }

        for (int i = 0; i < devices.capacity(); i++) {
            VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
            int queueIndex = findGraphicsPresentQueueFamily(candidate, stack);
            if (queueIndex >= 0 && supportsSwapchainExtension(candidate, stack)) {
                physicalDevice = candidate;
                graphicsQueueFamilyIndex = queueIndex;
                return;
            }
        }

        throw new EngineException(
                EngineErrorCode.BACKEND_INIT_FAILED,
                "No Vulkan device with graphics+present+swapchain support found",
                false
        );
    }

    private int findGraphicsPresentQueueFamily(VkPhysicalDevice candidate, MemoryStack stack) {
        var pQueueCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, pQueueCount, null);
        int queueCount = pQueueCount.get(0);
        if (queueCount <= 0) {
            return -1;
        }
        VkQueueFamilyProperties.Buffer queueProps = VkQueueFamilyProperties.calloc(queueCount, stack);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, pQueueCount, queueProps);

        var pSupported = stack.ints(0);
        for (int i = 0; i < queueCount; i++) {
            vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, pSupported);
            boolean presentSupported = pSupported.get(0) == VK_TRUE;
            boolean graphicsSupported = (queueProps.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
            if (graphicsSupported && presentSupported) {
                return i;
            }
        }
        return -1;
    }

    private boolean supportsSwapchainExtension(VkPhysicalDevice candidate, MemoryStack stack) {
        var pCount = stack.ints(0);
        int result = VK10.vkEnumerateDeviceExtensionProperties(candidate, (String) null, pCount, null);
        if (result != VK_SUCCESS || pCount.get(0) <= 0) {
            return false;
        }
        var props = org.lwjgl.vulkan.VkExtensionProperties.calloc(pCount.get(0), stack);
        result = VK10.vkEnumerateDeviceExtensionProperties(candidate, (String) null, pCount, props);
        if (result != VK_SUCCESS) {
            return false;
        }
        for (int i = 0; i < props.capacity(); i++) {
            if (VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(props.get(i).extensionNameString())) {
                return true;
            }
        }
        return false;
    }

    private void createLogicalDevice(MemoryStack stack) throws EngineException {
        if (physicalDevice == null || graphicsQueueFamilyIndex < 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Vulkan physical device not selected", false);
        }

        var queuePriority = stack.floats(1.0f);
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamilyIndex)
                .pQueuePriorities(queuePriority);

        PointerBuffer extensions = stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
        VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfo)
                .ppEnabledExtensionNames(extensions);

        PointerBuffer pDevice = stack.mallocPointer(1);
        int createResult = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice);
        if (createResult != VK_SUCCESS || pDevice.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDevice failed: " + createResult,
                    createResult == VK_ERROR_INITIALIZATION_FAILED
            );
        }

        device = new VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo);
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0, pQueue);
        graphicsQueue = new VkQueue(pQueue.get(0), device);
    }

    private void createDescriptorResources(MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
        bindings.get(0)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(1)
                .binding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int layoutResult = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
        if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorSetLayout failed: " + layoutResult, false);
        }
        descriptorSetLayout = pLayout.get(0);

        VkDescriptorSetLayoutBinding.Buffer textureBindings = VkDescriptorSetLayoutBinding.calloc(8, stack);
        textureBindings.get(0)
                .binding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(1)
                .binding(1)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(2)
                .binding(2)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(3)
                .binding(3)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(4)
                .binding(4)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(5)
                .binding(5)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(6)
                .binding(6)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        textureBindings.get(7)
                .binding(7)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

        VkDescriptorSetLayoutCreateInfo textureLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(textureBindings);
        var pTextureLayout = stack.longs(VK_NULL_HANDLE);
        int textureLayoutResult = vkCreateDescriptorSetLayout(device, textureLayoutInfo, null, pTextureLayout);
        if (textureLayoutResult != VK_SUCCESS || pTextureLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorSetLayout(texture) failed: " + textureLayoutResult,
                    false
            );
        }
        textureDescriptorSetLayout = pTextureLayout.get(0);

        VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceProperties(physicalDevice, props);
        long minAlign = Math.max(1L, props.limits().minUniformBufferOffsetAlignment());
        uniformStrideBytes = alignUp(OBJECT_UNIFORM_BYTES, (int) Math.min(Integer.MAX_VALUE, minAlign));
        uniformFrameSpanBytes = uniformStrideBytes * maxDynamicSceneObjects;
        globalUniformFrameSpanBytes = alignUp(GLOBAL_SCENE_UNIFORM_BYTES, (int) Math.min(Integer.MAX_VALUE, minAlign));
        int totalObjectUniformBytes = uniformFrameSpanBytes * framesInFlight;
        int totalGlobalUniformBytes = globalUniformFrameSpanBytes * framesInFlight;

        VulkanBufferAlloc objectUniformDeviceAlloc = createBuffer(
                stack,
                totalObjectUniformBytes,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        objectUniformBuffer = objectUniformDeviceAlloc.buffer();
        objectUniformMemory = objectUniformDeviceAlloc.memory();

        VulkanBufferAlloc objectUniformStagingAlloc = createBuffer(
                stack,
                totalObjectUniformBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        objectUniformStagingBuffer = objectUniformStagingAlloc.buffer();
        objectUniformStagingMemory = objectUniformStagingAlloc.memory();
        PointerBuffer pObjectMapped = stack.mallocPointer(1);
        int mapObjectStagingResult = vkMapMemory(device, objectUniformStagingMemory, 0, totalObjectUniformBytes, 0, pObjectMapped);
        if (mapObjectStagingResult != VK_SUCCESS || pObjectMapped.get(0) == 0L) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkMapMemory(objectStagingPersistent) failed: " + mapObjectStagingResult,
                    false
            );
        }
        objectUniformStagingMappedAddress = pObjectMapped.get(0);

        VulkanBufferAlloc globalUniformDeviceAlloc = createBuffer(
                stack,
                totalGlobalUniformBytes,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        sceneGlobalUniformBuffer = globalUniformDeviceAlloc.buffer();
        sceneGlobalUniformMemory = globalUniformDeviceAlloc.memory();

        VulkanBufferAlloc globalUniformStagingAlloc = createBuffer(
                stack,
                totalGlobalUniformBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        sceneGlobalUniformStagingBuffer = globalUniformStagingAlloc.buffer();
        sceneGlobalUniformStagingMemory = globalUniformStagingAlloc.memory();
        PointerBuffer pGlobalMapped = stack.mallocPointer(1);
        int mapGlobalStagingResult = vkMapMemory(device, sceneGlobalUniformStagingMemory, 0, totalGlobalUniformBytes, 0, pGlobalMapped);
        if (mapGlobalStagingResult != VK_SUCCESS || pGlobalMapped.get(0) == 0L) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkMapMemory(globalStagingPersistent) failed: " + mapGlobalStagingResult,
                    false
            );
        }
        sceneGlobalUniformStagingMappedAddress = pGlobalMapped.get(0);
        estimatedGpuMemoryBytes = ((long) totalObjectUniformBytes * 2L) + ((long) totalGlobalUniformBytes * 2L);

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(framesInFlight);
        poolSizes.get(1)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                .descriptorCount(framesInFlight);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(framesInFlight)
                .pPoolSizes(poolSizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorPool failed: " + poolResult, false);
        }
        descriptorPool = pPool.get(0);

        java.nio.LongBuffer layouts = stack.mallocLong(framesInFlight);
        for (int i = 0; i < framesInFlight; i++) {
            layouts.put(i, descriptorSetLayout);
        }
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts);
        java.nio.LongBuffer pSet = stack.mallocLong(framesInFlight);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, pSet);
        if (setResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateDescriptorSets failed: " + setResult, false);
        }
        frameDescriptorSets = new long[framesInFlight];
        for (int i = 0; i < framesInFlight; i++) {
            frameDescriptorSets[i] = pSet.get(i);
        }
        descriptorSet = frameDescriptorSets[0];

        VkDescriptorBufferInfo.Buffer globalBufferInfos = VkDescriptorBufferInfo.calloc(framesInFlight, stack);
        VkDescriptorBufferInfo.Buffer objectBufferInfos = VkDescriptorBufferInfo.calloc(framesInFlight, stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(framesInFlight * 2, stack);
        for (int i = 0; i < framesInFlight; i++) {
            long globalFrameBase = (long) i * globalUniformFrameSpanBytes;
            long objectFrameBase = (long) i * uniformFrameSpanBytes;
            globalBufferInfos.get(i)
                    .buffer(sceneGlobalUniformBuffer)
                    .offset(globalFrameBase)
                    .range(GLOBAL_SCENE_UNIFORM_BYTES);
            objectBufferInfos.get(i)
                    .buffer(objectUniformBuffer)
                    .offset(objectFrameBase)
                    .range(OBJECT_UNIFORM_BYTES);
            writes.get(i * 2)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(frameDescriptorSets[i])
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, globalBufferInfos.get(i)));
            writes.get((i * 2) + 1)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(frameDescriptorSets[i])
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, objectBufferInfos.get(i)));
        }
        vkUpdateDescriptorSets(device, writes, null);
    }

    private void destroyDescriptorResources() {
        if (device == null) {
            return;
        }
        if (objectUniformBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, objectUniformBuffer, null);
            objectUniformBuffer = VK_NULL_HANDLE;
        }
        if (objectUniformMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, objectUniformMemory, null);
            objectUniformMemory = VK_NULL_HANDLE;
        }
        if (objectUniformStagingBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, objectUniformStagingBuffer, null);
            objectUniformStagingBuffer = VK_NULL_HANDLE;
        }
        if (objectUniformStagingMappedAddress != 0L && objectUniformStagingMemory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, objectUniformStagingMemory);
            objectUniformStagingMappedAddress = 0L;
        }
        if (objectUniformStagingMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, objectUniformStagingMemory, null);
            objectUniformStagingMemory = VK_NULL_HANDLE;
        }
        if (sceneGlobalUniformBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, sceneGlobalUniformBuffer, null);
            sceneGlobalUniformBuffer = VK_NULL_HANDLE;
        }
        if (sceneGlobalUniformMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, sceneGlobalUniformMemory, null);
            sceneGlobalUniformMemory = VK_NULL_HANDLE;
        }
        if (sceneGlobalUniformStagingBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, sceneGlobalUniformStagingBuffer, null);
            sceneGlobalUniformStagingBuffer = VK_NULL_HANDLE;
        }
        if (sceneGlobalUniformStagingMappedAddress != 0L && sceneGlobalUniformStagingMemory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, sceneGlobalUniformStagingMemory);
            sceneGlobalUniformStagingMappedAddress = 0L;
        }
        if (sceneGlobalUniformStagingMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, sceneGlobalUniformStagingMemory, null);
            sceneGlobalUniformStagingMemory = VK_NULL_HANDLE;
        }
        uniformStrideBytes = OBJECT_UNIFORM_BYTES;
        uniformFrameSpanBytes = OBJECT_UNIFORM_BYTES;
        globalUniformFrameSpanBytes = GLOBAL_SCENE_UNIFORM_BYTES;
        estimatedGpuMemoryBytes = 0;
        lastFrameUniformUploadBytes = 0;
        maxFrameUniformUploadBytes = 0;
        lastFrameGlobalUploadBytes = 0;
        maxFrameGlobalUploadBytes = 0;
        lastFrameUniformObjectCount = 0;
        maxFrameUniformObjectCount = 0;
        lastFrameUniformUploadRanges = 0;
        maxFrameUniformUploadRanges = 0;
        lastFrameUniformUploadStartObject = 0;
        maxObservedDynamicObjects = 0;
        pendingUploadSrcOffset = -1L;
        pendingUploadDstOffset = -1L;
        pendingUploadByteCount = 0;
        pendingUploadObjectCount = 0;
        pendingUploadStartObject = 0;
        pendingGlobalUploadSrcOffset = -1L;
        pendingGlobalUploadDstOffset = -1L;
        pendingGlobalUploadByteCount = 0;
        pendingSceneDirtyRangeCount = 0;
        globalStateRevision = 1;
        sceneStateRevision = 1;
        Arrays.fill(frameGlobalRevisionApplied, 0L);
        Arrays.fill(frameSceneRevisionApplied, 0L);
        frameDescriptorSets = new long[0];
        descriptorSet = VK_NULL_HANDLE;
        descriptorRingSetCapacity = 0;
        descriptorRingPeakSetCapacity = 0;
        descriptorRingActiveSetCount = 0;
        descriptorRingWasteSetCount = 0;
        descriptorRingPeakWasteSetCount = 0;
        descriptorRingCapBypassCount = 0;
        descriptorRingPoolReuseCount = 0;
        descriptorRingPoolResetFailureCount = 0;
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = VK_NULL_HANDLE;
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = VK_NULL_HANDLE;
        }
        if (textureDescriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, textureDescriptorSetLayout, null);
            textureDescriptorSetLayout = VK_NULL_HANDLE;
        }
    }

    private void createSwapchainResources(MemoryStack stack, int requestedWidth, int requestedHeight) throws EngineException {
        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
        int capsResult = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);
        if (capsResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR failed: " + capsResult, false);
        }

        var formatCount = stack.ints(0);
        int formatResult = vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
        if (formatResult != VK_SUCCESS || formatCount.get(0) == 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No Vulkan surface formats", false);
        }
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);
        VkSurfaceFormatKHR chosenFormat = VulkanSwapchainSelector.chooseSurfaceFormat(formats);

        var presentModeCount = stack.ints(0);
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null);
        var presentModes = stack.mallocInt(Math.max(1, presentModeCount.get(0)));
        if (presentModeCount.get(0) > 0) {
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes);
        }
        int presentMode = VulkanSwapchainSelector.choosePresentMode(presentModes, presentModeCount.get(0));

        VkExtent2D extent = VulkanSwapchainSelector.chooseExtent(capabilities, requestedWidth, requestedHeight, stack);
        int imageCount = capabilities.minImageCount() + 1;
        if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
            imageCount = capabilities.maxImageCount();
        }

        VkSwapchainCreateInfoKHR swapchainInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(chosenFormat.format())
                .imageColorSpace(chosenFormat.colorSpace())
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform((capabilities.supportedTransforms() & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0
                        ? VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
                        : capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);

        var pSwapchain = stack.longs(VK_NULL_HANDLE);
        int swapchainResult = vkCreateSwapchainKHR(device, swapchainInfo, null, pSwapchain);
        if (swapchainResult != VK_SUCCESS || pSwapchain.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSwapchainKHR failed: " + swapchainResult, false);
        }
        swapchain = pSwapchain.get(0);
        swapchainImageFormat = chosenFormat.format();
        swapchainWidth = extent.width();
        swapchainHeight = extent.height();

        var imageCountBuf = stack.ints(0);
        vkGetSwapchainImagesKHR(device, swapchain, imageCountBuf, null);
        LongBufferWrapper imageHandles = LongBufferWrapper.allocate(stack, imageCountBuf.get(0));
        vkGetSwapchainImagesKHR(device, swapchain, imageCountBuf, imageHandles.buffer());
        swapchainImages = imageHandles.toArray();

        createImageViews(stack);
        createDepthResources(stack);
        createRenderPass(stack);
        createGraphicsPipeline(stack);
        createFramebuffers(stack);
        postOffscreenActive = false;
        if (postOffscreenRequested) {
            try {
                createPostProcessResources(stack);
                postOffscreenActive = true;
            } catch (EngineException ex) {
                destroyPostProcessResources();
                postOffscreenActive = false;
            }
        }
    }

    private void createImageViews(MemoryStack stack) throws EngineException {
        swapchainImageViews = new long[swapchainImages.length];
        for (int i = 0; i < swapchainImages.length; i++) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(swapchainImages[i])
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(swapchainImageFormat);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            var pView = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateImageView(device, viewInfo, null, pView);
            if (result != VK_SUCCESS || pView.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImageView failed: " + result, false);
            }
            swapchainImageViews[i] = pView.get(0);
        }
    }

    private void createDepthResources(MemoryStack stack) throws EngineException {
        depthImages = new long[swapchainImages.length];
        depthMemories = new long[swapchainImages.length];
        depthImageViews = new long[swapchainImages.length];
        for (int i = 0; i < swapchainImages.length; i++) {
            VulkanImageAlloc depth = createImage(
                    stack,
                    swapchainWidth,
                    swapchainHeight,
                    depthFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            );
            depthImages[i] = depth.image();
            depthMemories[i] = depth.memory();
            depthImageViews[i] = createImageView(stack, depth.image(), depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT);
        }
    }

    private void createShadowResources(MemoryStack stack) throws EngineException {
        VulkanImageAlloc shadowDepth = createImage(
                stack,
                shadowMapResolution,
                shadowMapResolution,
                depthFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                MAX_SHADOW_MATRICES
        );
        shadowDepthImage = shadowDepth.image();
        shadowDepthMemory = shadowDepth.memory();
        shadowDepthImageView = createImageView(
                stack,
                shadowDepthImage,
                depthFormat,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY,
                0,
                MAX_SHADOW_MATRICES
        );
        shadowDepthLayerImageViews = new long[MAX_SHADOW_MATRICES];
        for (int i = 0; i < MAX_SHADOW_MATRICES; i++) {
            shadowDepthLayerImageViews[i] = createImageView(
                    stack,
                    shadowDepthImage,
                    depthFormat,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_IMAGE_VIEW_TYPE_2D,
                    i,
                    1
            );
        }
        shadowSampler = createShadowSampler(stack);
        createShadowRenderPass(stack);
        createShadowPipeline(stack);
        createShadowFramebuffers(stack);
    }

    private long createShadowSampler(MemoryStack stack) throws EngineException {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK10.VK_FILTER_LINEAR)
                .minFilter(VK10.VK_FILTER_LINEAR)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .borderColor(VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                .unnormalizedCoordinates(false)
                .compareEnable(true)
                .compareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .mipLodBias(0.0f)
                .minLod(0.0f)
                .maxLod(0.0f);
        var pSampler = stack.longs(VK_NULL_HANDLE);
        int result = VK10.vkCreateSampler(device, samplerInfo, null, pSampler);
        if (result != VK_SUCCESS || pSampler.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSampler(shadow) failed: " + result, false);
        }
        return pSampler.get(0);
    }

    private void createShadowRenderPass(MemoryStack stack) throws EngineException {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack)
                .format(depthFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);

        VkAttachmentReference.Buffer depthRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .pDepthStencilAttachment(depthRef.get(0));

        VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
        dependencies.get(0)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .dstAccessMask(VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
        dependencies.get(1)
                .srcSubpass(0)
                .dstSubpass(VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .srcAccessMask(VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

        VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependencies);

        var pRenderPass = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
        if (result != VK_SUCCESS || pRenderPass.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateRenderPass(shadow) failed: " + result, false);
        }
        shadowRenderPass = pRenderPass.get(0);
    }

    private void createShadowPipeline(MemoryStack stack) throws EngineException {
        String shadowVertSource = VulkanShaderSources.shadowVertex();
        String shadowFragSource = VulkanShaderSources.shadowFragment();

        ByteBuffer vertSpv = VulkanShaderCompiler.compileGlslToSpv(shadowVertSource, shaderc_glsl_vertex_shader, "shadow.vert");
        ByteBuffer fragSpv = VulkanShaderCompiler.compileGlslToSpv(shadowFragSource, shaderc_fragment_shader, "shadow.frag");
        long vertModule = VK_NULL_HANDLE;
        long fragModule = VK_NULL_HANDLE;
        try {
            vertModule = VulkanShaderCompiler.createShaderModule(device, stack, vertSpv);
            fragModule = VulkanShaderCompiler.createShaderModule(device, stack, fragSpv);
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertModule)
                    .pName(stack.UTF8("main"));
            shaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            var bindingDesc = org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
            bindingDesc.get(0)
                    .binding(0)
                    .stride(VERTEX_STRIDE_BYTES)
                    .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            var attrDesc = org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(1, stack);
            attrDesc.get(0)
                    .location(0)
                    .binding(0)
                    .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDesc)
                    .pVertexAttributeDescriptions(attrDesc);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0f)
                    .y(0f)
                    .width((float) shadowMapResolution)
                    .height((float) shadowMapResolution)
                    .minDepth(0f)
                    .maxDepth(1f);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset(it -> it.set(0, 0));
            scissor.get(0).extent(VkExtent2D.calloc(stack).set(shadowMapResolution, shadowMapResolution));
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .pViewports(viewport)
                    .scissorCount(1)
                    .pScissors(scissor);
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK10.VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK10.VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);
            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .attachmentCount(0);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                            .offset(0)
                            .size(Integer.BYTES));
            var pLayout = stack.longs(VK_NULL_HANDLE);
            int layoutResult = vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pLayout);
            if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreatePipelineLayout(shadow) failed: " + layoutResult, false);
            }
            shadowPipelineLayout = pLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(shadowPipelineLayout)
                    .renderPass(shadowRenderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pPipeline = stack.longs(VK_NULL_HANDLE);
            int pipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (pipelineResult != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateGraphicsPipelines(shadow) failed: " + pipelineResult, false);
            }
            shadowPipeline = pPipeline.get(0);
        } finally {
            if (vertModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertModule, null);
            }
            if (fragModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragModule, null);
            }
        }
    }

    private void createShadowFramebuffers(MemoryStack stack) throws EngineException {
        shadowFramebuffers = new long[MAX_SHADOW_MATRICES];
        for (int i = 0; i < MAX_SHADOW_MATRICES; i++) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(shadowRenderPass)
                    .pAttachments(stack.longs(shadowDepthLayerImageViews[i]))
                    .width(shadowMapResolution)
                    .height(shadowMapResolution)
                    .layers(1);
            var pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS || pFramebuffer.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFramebuffer(shadow) failed: " + result, false);
            }
            shadowFramebuffers[i] = pFramebuffer.get(0);
        }
    }

    private void createRenderPass(MemoryStack stack) throws EngineException {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
        attachments.get(0)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        attachments.get(1)
                .format(depthFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkAttachmentReference.Buffer depthRef = VkAttachmentReference.calloc(1, stack)
                .attachment(1)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorRef)
                .pDepthStencilAttachment(depthRef.get(0));

        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

        VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependency);

        var pRenderPass = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
        if (result != VK_SUCCESS || pRenderPass.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateRenderPass failed: " + result, false);
        }
        renderPass = pRenderPass.get(0);
    }

    private void createGraphicsPipeline(MemoryStack stack) throws EngineException {
        String vertexShaderSource = VulkanShaderSources.mainVertex();
        String fragmentShaderSource = VulkanShaderSources.mainFragment();

        ByteBuffer vertSpv = VulkanShaderCompiler.compileGlslToSpv(vertexShaderSource, shaderc_glsl_vertex_shader, "triangle.vert");
        ByteBuffer fragSpv = VulkanShaderCompiler.compileGlslToSpv(fragmentShaderSource, shaderc_fragment_shader, "triangle.frag");

        long vertModule = VK_NULL_HANDLE;
        long fragModule = VK_NULL_HANDLE;
        try {
            vertModule = VulkanShaderCompiler.createShaderModule(device, stack, vertSpv);
            fragModule = VulkanShaderCompiler.createShaderModule(device, stack, fragSpv);

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertModule)
                    .pName(stack.UTF8("main"));
            shaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            var bindingDesc = org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
            bindingDesc.get(0)
                    .binding(0)
                    .stride(VERTEX_STRIDE_BYTES)
                    .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            var attrDesc = org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(4, stack);
            attrDesc.get(0)
                    .location(0)
                    .binding(0)
                    .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            attrDesc.get(1)
                    .location(1)
                    .binding(0)
                    .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(3 * Float.BYTES);
            attrDesc.get(2)
                    .location(2)
                    .binding(0)
                    .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                    .offset(6 * Float.BYTES);
            attrDesc.get(3)
                    .location(3)
                    .binding(0)
                    .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(8 * Float.BYTES);

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDesc)
                    .pVertexAttributeDescriptions(attrDesc);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0f)
                    .y(0f)
                    .width((float) swapchainWidth)
                    .height((float) swapchainHeight)
                    .minDepth(0f)
                    .maxDepth(1f);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset(it -> it.set(0, 0));
            scissor.get(0).extent(VkExtent2D.calloc(stack).set(swapchainWidth, swapchainHeight));

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .pViewports(viewport)
                    .scissorCount(1)
                    .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK10.VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout, textureDescriptorSetLayout));
            var pPipelineLayout = stack.longs(VK_NULL_HANDLE);
            int layoutResult = vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            if (layoutResult != VK_SUCCESS || pPipelineLayout.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreatePipelineLayout failed: " + layoutResult, false);
            }
            pipelineLayout = pPipelineLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);

            var pPipeline = stack.longs(VK_NULL_HANDLE);
            int pipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (pipelineResult != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateGraphicsPipelines failed: " + pipelineResult, false);
            }
            graphicsPipeline = pPipeline.get(0);
        } finally {
            if (vertModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertModule, null);
            }
            if (fragModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragModule, null);
            }
        }
    }

    private void createFramebuffers(MemoryStack stack) throws EngineException {
        framebuffers = new long[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .pAttachments(stack.longs(swapchainImageViews[i], depthImageViews[i]))
                    .width(swapchainWidth)
                    .height(swapchainHeight)
                    .layers(1);
            var pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS || pFramebuffer.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFramebuffer failed: " + result, false);
            }
            framebuffers[i] = pFramebuffer.get(0);
        }
    }

    private void createPostProcessResources(MemoryStack stack) throws EngineException {
        postIntermediateInitialized = false;

        VulkanImageAlloc intermediate = createImage(
                stack,
                swapchainWidth,
                swapchainHeight,
                swapchainImageFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        offscreenColorImage = intermediate.image();
        offscreenColorMemory = intermediate.memory();
        offscreenColorImageView = createImageView(stack, offscreenColorImage, swapchainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT);
        offscreenColorSampler = createSampler(stack);

        createPostDescriptorResources(stack);
        createPostRenderPass(stack);
        createPostPipeline(stack);
        createPostFramebuffers(stack);
    }

    private void createPostDescriptorResources(MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
        bindings.get(0)
                .binding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int layoutResult = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
        if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorSetLayout(post) failed: " + layoutResult, false);
        }
        postDescriptorSetLayout = pLayout.get(0);

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1)
                .pPoolSizes(poolSizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorPool(post) failed: " + poolResult, false);
        }
        postDescriptorPool = pPool.get(0);

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(postDescriptorPool)
                .pSetLayouts(stack.longs(postDescriptorSetLayout));
        var pSet = stack.longs(VK_NULL_HANDLE);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, pSet);
        if (setResult != VK_SUCCESS || pSet.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateDescriptorSets(post) failed: " + setResult, false);
        }
        postDescriptorSet = pSet.get(0);

        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
        imageInfo.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(offscreenColorImageView)
                .sampler(offscreenColorSampler);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
        writes.get(0)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(postDescriptorSet)
                .dstBinding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo);
        vkUpdateDescriptorSets(device, writes, null);
    }

    private void createPostRenderPass(MemoryStack stack) throws EngineException {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
        attachments.get(0)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorRef);
        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependency);
        var pRenderPass = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
        if (result != VK_SUCCESS || pRenderPass.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateRenderPass(post) failed: " + result, false);
        }
        postRenderPass = pRenderPass.get(0);
    }

    private void createPostPipeline(MemoryStack stack) throws EngineException {
        String vertexShaderSource = VulkanShaderSources.postVertex();
        String fragmentShaderSource = VulkanShaderSources.postFragment();
        ByteBuffer vertSpv = VulkanShaderCompiler.compileGlslToSpv(vertexShaderSource, shaderc_glsl_vertex_shader, "post.vert");
        ByteBuffer fragSpv = VulkanShaderCompiler.compileGlslToSpv(fragmentShaderSource, shaderc_fragment_shader, "post.frag");
        long vertModule = VK_NULL_HANDLE;
        long fragModule = VK_NULL_HANDLE;
        try {
            vertModule = VulkanShaderCompiler.createShaderModule(device, stack, vertSpv);
            fragModule = VulkanShaderCompiler.createShaderModule(device, stack, fragSpv);
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertModule).pName(stack.UTF8("main"));
            shaderStages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragModule).pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0f).y(0f).width((float) swapchainWidth).height((float) swapchainHeight).minDepth(0f).maxDepth(1f);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset(it -> it.set(0, 0));
            scissor.get(0).extent(VkExtent2D.calloc(stack).set(swapchainWidth, swapchainHeight));
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1).pViewports(viewport).scissorCount(1).pScissors(scissor);
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false).rasterizerDiscardEnable(false).polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f).cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).depthBiasEnable(false);
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false).rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(false).depthWriteEnable(false).depthBoundsTestEnable(false).stencilTestEnable(false);
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);
            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            VkPushConstantRange.Buffer pushRanges = VkPushConstantRange.calloc(1, stack);
            pushRanges.get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(8 * Float.BYTES);
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(postDescriptorSetLayout))
                    .pPushConstantRanges(pushRanges);
            var pLayout = stack.longs(VK_NULL_HANDLE);
            int layoutResult = vkCreatePipelineLayout(device, layoutInfo, null, pLayout);
            if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreatePipelineLayout(post) failed: " + layoutResult, false);
            }
            postPipelineLayout = pLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(postPipelineLayout)
                    .renderPass(postRenderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pPipeline = stack.longs(VK_NULL_HANDLE);
            int pipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (pipelineResult != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateGraphicsPipelines(post) failed: " + pipelineResult, false);
            }
            postGraphicsPipeline = pPipeline.get(0);
        } finally {
            if (vertModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertModule, null);
            }
            if (fragModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragModule, null);
            }
        }
    }

    private void createPostFramebuffers(MemoryStack stack) throws EngineException {
        postFramebuffers = new long[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(postRenderPass)
                    .pAttachments(stack.longs(swapchainImageViews[i]))
                    .width(swapchainWidth)
                    .height(swapchainHeight)
                    .layers(1);
            var pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS || pFramebuffer.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFramebuffer(post) failed: " + result, false);
            }
            postFramebuffers[i] = pFramebuffer.get(0);
        }
    }

    private void destroyShadowResources() {
        if (device == null) {
            return;
        }
        for (long shadowFramebuffer : shadowFramebuffers) {
            if (shadowFramebuffer != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, shadowFramebuffer, null);
            }
        }
        shadowFramebuffers = new long[0];
        if (shadowPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, shadowPipeline, null);
            shadowPipeline = VK_NULL_HANDLE;
        }
        if (shadowPipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, shadowPipelineLayout, null);
            shadowPipelineLayout = VK_NULL_HANDLE;
        }
        if (shadowRenderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, shadowRenderPass, null);
            shadowRenderPass = VK_NULL_HANDLE;
        }
        if (shadowSampler != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, shadowSampler, null);
            shadowSampler = VK_NULL_HANDLE;
        }
        if (shadowDepthImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, shadowDepthImageView, null);
            shadowDepthImageView = VK_NULL_HANDLE;
        }
        for (long shadowDepthLayerImageView : shadowDepthLayerImageViews) {
            if (shadowDepthLayerImageView != VK_NULL_HANDLE) {
                vkDestroyImageView(device, shadowDepthLayerImageView, null);
            }
        }
        shadowDepthLayerImageViews = new long[0];
        if (shadowDepthImage != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, shadowDepthImage, null);
            shadowDepthImage = VK_NULL_HANDLE;
        }
        if (shadowDepthMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, shadowDepthMemory, null);
            shadowDepthMemory = VK_NULL_HANDLE;
        }
    }

    private void destroySwapchainResources() {
        if (device == null) {
            return;
        }
        destroyPostProcessResources();
        for (long fb : framebuffers) {
            if (fb != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, fb, null);
            }
        }
        framebuffers = new long[0];
        if (graphicsPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, graphicsPipeline, null);
            graphicsPipeline = VK_NULL_HANDLE;
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = VK_NULL_HANDLE;
        }
        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
            renderPass = VK_NULL_HANDLE;
        }
        for (long view : swapchainImageViews) {
            if (view != VK_NULL_HANDLE) {
                vkDestroyImageView(device, view, null);
            }
        }
        for (long view : depthImageViews) {
            if (view != VK_NULL_HANDLE) {
                vkDestroyImageView(device, view, null);
            }
        }
        for (long image : depthImages) {
            if (image != VK_NULL_HANDLE) {
                VK10.vkDestroyImage(device, image, null);
            }
        }
        for (long memory : depthMemories) {
            if (memory != VK_NULL_HANDLE) {
                vkFreeMemory(device, memory, null);
            }
        }
        depthImageViews = new long[0];
        depthImages = new long[0];
        depthMemories = new long[0];
        swapchainImageViews = new long[0];
        swapchainImages = new long[0];
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK_NULL_HANDLE;
        }
    }

    private void destroyPostProcessResources() {
        if (device == null) {
            return;
        }
        for (long fb : postFramebuffers) {
            if (fb != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, fb, null);
            }
        }
        postFramebuffers = new long[0];
        if (postGraphicsPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, postGraphicsPipeline, null);
            postGraphicsPipeline = VK_NULL_HANDLE;
        }
        if (postPipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, postPipelineLayout, null);
            postPipelineLayout = VK_NULL_HANDLE;
        }
        if (postRenderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, postRenderPass, null);
            postRenderPass = VK_NULL_HANDLE;
        }
        if (postDescriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, postDescriptorPool, null);
            postDescriptorPool = VK_NULL_HANDLE;
        }
        if (postDescriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, postDescriptorSetLayout, null);
            postDescriptorSetLayout = VK_NULL_HANDLE;
        }
        postDescriptorSet = VK_NULL_HANDLE;
        if (offscreenColorSampler != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, offscreenColorSampler, null);
            offscreenColorSampler = VK_NULL_HANDLE;
        }
        if (offscreenColorImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, offscreenColorImageView, null);
            offscreenColorImageView = VK_NULL_HANDLE;
        }
        if (offscreenColorImage != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, offscreenColorImage, null);
            offscreenColorImage = VK_NULL_HANDLE;
        }
        if (offscreenColorMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, offscreenColorMemory, null);
            offscreenColorMemory = VK_NULL_HANDLE;
        }
        postIntermediateInitialized = false;
        postOffscreenActive = false;
    }

    private void createCommandResources(MemoryStack stack) throws EngineException {
        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(graphicsQueueFamilyIndex);

        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateCommandPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateCommandPool failed: " + poolResult, false);
        }
        commandPool = pPool.get(0);

        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(framesInFlight);

        PointerBuffer pCommandBuffer = stack.mallocPointer(framesInFlight);
        int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        if (allocResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers failed: " + allocResult, false);
        }
        commandBuffers = new VkCommandBuffer[framesInFlight];
        for (int i = 0; i < framesInFlight; i++) {
            commandBuffers[i] = new VkCommandBuffer(pCommandBuffer.get(i), device);
        }
        currentFrame = 0;
    }

    private void createSyncObjects(MemoryStack stack) throws EngineException {
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

        imageAvailableSemaphores = new long[framesInFlight];
        renderFinishedSemaphores = new long[framesInFlight];
        renderFences = new long[framesInFlight];

        for (int i = 0; i < framesInFlight; i++) {
            var pSemaphore = stack.longs(VK_NULL_HANDLE);
            int semaphoreResult = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (semaphoreResult != VK_SUCCESS || pSemaphore.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSemaphore(imageAvailable) failed: " + semaphoreResult, false);
            }
            imageAvailableSemaphores[i] = pSemaphore.get(0);

            pSemaphore.put(0, VK_NULL_HANDLE);
            semaphoreResult = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (semaphoreResult != VK_SUCCESS || pSemaphore.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSemaphore(renderFinished) failed: " + semaphoreResult, false);
            }
            renderFinishedSemaphores[i] = pSemaphore.get(0);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            var pFence = stack.longs(VK_NULL_HANDLE);
            int fenceResult = vkCreateFence(device, fenceInfo, null, pFence);
            if (fenceResult != VK_SUCCESS || pFence.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFence failed: " + fenceResult, false);
            }
            renderFences[i] = pFence.get(0);
        }
    }

    private int acquireNextImage(MemoryStack stack, int frameIdx) throws EngineException {
        VkCommandBuffer commandBuffer = commandBuffers[frameIdx];
        long imageAvailableSemaphore = imageAvailableSemaphores[frameIdx];
        long renderFinishedSemaphore = renderFinishedSemaphores[frameIdx];
        long renderFence = renderFences[frameIdx];
        var pImageIndex = stack.ints(0);
        int acquireResult = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE, imageAvailableSemaphore, VK_NULL_HANDLE, pImageIndex);
        if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
            int imageIndex = pImageIndex.get(0);

            int waitResult = vkWaitForFences(device, stack.longs(renderFence), true, Long.MAX_VALUE);
            if (waitResult != VK_SUCCESS) {
                throw vkFailure("vkWaitForFences", waitResult);
            }
            int resetFenceResult = vkResetFences(device, stack.longs(renderFence));
            if (resetFenceResult != VK_SUCCESS) {
                throw vkFailure("vkResetFences", resetFenceResult);
            }
            int resetCmdResult = vkResetCommandBuffer(commandBuffer, 0);
            if (resetCmdResult != VK_SUCCESS) {
                throw vkFailure("vkResetCommandBuffer", resetCmdResult);
            }

            recordCommandBuffer(stack, commandBuffer, imageIndex, frameIdx);
            return VulkanCommandSubmitter.submitAndPresent(
                    stack,
                    graphicsQueue,
                    swapchain,
                    commandBuffer,
                    imageIndex,
                    imageAvailableSemaphore,
                    renderFinishedSemaphore,
                    renderFence
            );
        }
        if (acquireResult != VK_ERROR_OUT_OF_DATE_KHR) {
            throw vkFailure("vkAcquireNextImageKHR", acquireResult);
        }
        return acquireResult;
    }

    private void recordCommandBuffer(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIndex, int frameIdx) throws EngineException {
        int beginResult = VulkanRenderCommandRecorder.beginOneShot(commandBuffer, stack);
        if (beginResult != VK_SUCCESS) {
            throw vkFailure("vkBeginCommandBuffer", beginResult);
        }

        updateShadowLightViewProjMatrices();
        prepareFrameUniforms(frameIdx);
        uploadFrameUniforms(commandBuffer, frameIdx);
        long frameDescriptorSet = descriptorSetForFrame(frameIdx);
        int drawCount = gpuMeshes.isEmpty() ? 1 : Math.min(maxDynamicSceneObjects, gpuMeshes.size());
        List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes = new ArrayList<>(Math.min(drawCount, gpuMeshes.size()));
        for (int i = 0; i < drawCount && i < gpuMeshes.size(); i++) {
            VulkanGpuMesh mesh = gpuMeshes.get(i);
            meshes.add(new VulkanRenderCommandRecorder.MeshDrawCmd(
                    mesh.vertexBuffer,
                    mesh.indexBuffer,
                    mesh.indexCount,
                    mesh.textureDescriptorSet
            ));
        }

        VulkanRenderCommandRecorder.recordShadowAndMainPasses(
                stack,
                commandBuffer,
                new VulkanRenderCommandRecorder.RenderPassInputs(
                        drawCount,
                        swapchainWidth,
                        swapchainHeight,
                        shadowMapResolution,
                        shadowEnabled,
                        pointShadowEnabled,
                        shadowCascadeCount,
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES,
                        frameDescriptorSet,
                        renderPass,
                        framebuffers[imageIndex],
                        graphicsPipeline,
                        pipelineLayout,
                        shadowRenderPass,
                        shadowPipeline,
                        shadowPipelineLayout,
                        shadowFramebuffers
                ),
                meshes,
                meshIndex -> dynamicUniformOffset(frameIdx, meshIndex)
        );

        if (postOffscreenActive) {
            postIntermediateInitialized = VulkanRenderCommandRecorder.executePostCompositePass(
                    stack,
                    commandBuffer,
                    new VulkanRenderCommandRecorder.PostCompositeInputs(
                            imageIndex,
                            swapchainWidth,
                            swapchainHeight,
                            postIntermediateInitialized,
                            tonemapEnabled,
                            tonemapExposure,
                            tonemapGamma,
                            bloomEnabled,
                            bloomThreshold,
                            bloomStrength,
                            postRenderPass,
                            postGraphicsPipeline,
                            postPipelineLayout,
                            postDescriptorSet,
                            offscreenColorImage,
                            swapchainImages[imageIndex],
                            postFramebuffers
                    )
            );
        }

        int endResult = VulkanRenderCommandRecorder.end(commandBuffer);
        if (endResult != VK_SUCCESS) {
            throw vkFailure("vkEndCommandBuffer", endResult);
        }
    }

    private void recreateSwapchainFromWindow() throws EngineException {
        if (window == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            var pW = stack.ints(1);
            var pH = stack.ints(1);
            glfwGetFramebufferSize(window, pW, pH);
            int width = Math.max(1, pW.get(0));
            int height = Math.max(1, pH.get(0));
            recreateSwapchain(width, height);
        }
    }

    private void recreateSwapchain(int width, int height) throws EngineException {
        if (device == null || physicalDevice == null || surface == VK_NULL_HANDLE) {
            return;
        }
        vkDeviceWaitIdle(device);
        destroySwapchainResources();
        try (MemoryStack stack = stackPush()) {
            createSwapchainResources(stack, width, height);
        }
    }

    private EngineException vkFailure(String operation, int result) {
        EngineErrorCode code = result == VK_ERROR_DEVICE_LOST ? EngineErrorCode.DEVICE_LOST : EngineErrorCode.BACKEND_INIT_FAILED;
        return new EngineException(code, operation + " failed: " + result, false);
    }

    private static int alignUp(int value, int alignment) {
        int safeAlignment = Math.max(1, alignment);
        int remainder = value % safeAlignment;
        if (remainder == 0) {
            return value;
        }
        return value + (safeAlignment - remainder);
    }

    private void updateShadowLightViewProjMatrices() {
        VulkanShadowMatrixBuilder.updateMatrices(
                new VulkanShadowMatrixBuilder.ShadowInputs(
                        pointLightIsSpot,
                        pointLightDirX,
                        pointLightDirY,
                        pointLightDirZ,
                        pointLightPosX,
                        pointLightPosY,
                        pointLightPosZ,
                        pointLightOuterCos,
                        pointShadowEnabled,
                        pointShadowFarPlane,
                        shadowCascadeCount,
                        viewMatrix,
                        projMatrix,
                        dirLightDirX,
                        dirLightDirY,
                        dirLightDirZ,
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES
                ),
                shadowLightViewProjMatrices,
                shadowCascadeSplitNdc
        );
    }

    private boolean canReuseGpuMeshes(List<VulkanSceneMeshData> sceneMeshes) {
        if (gpuMeshes.isEmpty() || sceneMeshes.size() != gpuMeshes.size()) {
            return false;
        }
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh gpuMesh : gpuMeshes) {
            if (byId.put(gpuMesh.meshId, gpuMesh) != null) {
                return false;
            }
        }
        for (VulkanSceneMeshData sceneMesh : sceneMeshes) {
            VulkanGpuMesh gpuMesh = byId.get(sceneMesh.meshId());
            if (gpuMesh == null) {
                return false;
            }
            long vertexBytes = (long) sceneMesh.vertices().length * Float.BYTES;
            long indexBytes = (long) sceneMesh.indices().length * Integer.BYTES;
            if (gpuMesh.vertexBytes != vertexBytes || gpuMesh.indexBytes != indexBytes || gpuMesh.indexCount != sceneMesh.indices().length) {
                return false;
            }
            if (gpuMesh.vertexHash != Arrays.hashCode(sceneMesh.vertices()) || gpuMesh.indexHash != Arrays.hashCode(sceneMesh.indices())) {
                return false;
            }
            String albedoKey = textureCacheKey(sceneMesh.albedoTexturePath(), false);
            String normalKey = textureCacheKey(sceneMesh.normalTexturePath(), true);
            String metallicRoughnessKey = textureCacheKey(sceneMesh.metallicRoughnessTexturePath(), false);
            String occlusionKey = textureCacheKey(sceneMesh.occlusionTexturePath(), false);
            if (!gpuMesh.albedoKey.equals(albedoKey)
                    || !gpuMesh.normalKey.equals(normalKey)
                    || !gpuMesh.metallicRoughnessKey.equals(metallicRoughnessKey)
                    || !gpuMesh.occlusionKey.equals(occlusionKey)) {
                return false;
            }
        }
        return true;
    }

    private boolean canReuseGeometryBuffers(List<VulkanSceneMeshData> sceneMeshes) {
        if (gpuMeshes.isEmpty() || sceneMeshes.size() != gpuMeshes.size()) {
            return false;
        }
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh gpuMesh : gpuMeshes) {
            if (byId.put(gpuMesh.meshId, gpuMesh) != null) {
                return false;
            }
        }
        for (VulkanSceneMeshData sceneMesh : sceneMeshes) {
            VulkanGpuMesh gpuMesh = byId.get(sceneMesh.meshId());
            if (gpuMesh == null) {
                return false;
            }
            long vertexBytes = (long) sceneMesh.vertices().length * Float.BYTES;
            long indexBytes = (long) sceneMesh.indices().length * Integer.BYTES;
            if (gpuMesh.vertexBytes != vertexBytes || gpuMesh.indexBytes != indexBytes || gpuMesh.indexCount != sceneMesh.indices().length) {
                return false;
            }
            if (gpuMesh.vertexHash != Arrays.hashCode(sceneMesh.vertices()) || gpuMesh.indexHash != Arrays.hashCode(sceneMesh.indices())) {
                return false;
            }
        }
        return true;
    }

    private void updateDynamicSceneState(List<VulkanSceneMeshData> sceneMeshes) {
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            byId.put(mesh.meshId, mesh);
        }
        List<VulkanGpuMesh> ordered = new ArrayList<>(sceneMeshes.size());
        boolean reordered = false;
        int dirtyStart = Integer.MAX_VALUE;
        int dirtyEnd = -1;
        for (int i = 0; i < sceneMeshes.size(); i++) {
            VulkanSceneMeshData sceneMesh = sceneMeshes.get(i);
            VulkanGpuMesh mesh = byId.get(sceneMesh.meshId());
            if (mesh == null) {
                continue;
            }
            if (i < gpuMeshes.size() && gpuMeshes.get(i) != mesh) {
                reordered = true;
            }
            boolean changed = mesh.updateDynamicState(
                    sceneMesh.modelMatrix().clone(),
                    sceneMesh.color()[0],
                    sceneMesh.color()[1],
                    sceneMesh.color()[2],
                    sceneMesh.metallic(),
                    sceneMesh.roughness()
            );
            if (changed) {
                dirtyStart = Math.min(dirtyStart, i);
                dirtyEnd = Math.max(dirtyEnd, i);
            }
            ordered.add(mesh);
        }
        if (ordered.size() == gpuMeshes.size()) {
            gpuMeshes.clear();
            gpuMeshes.addAll(ordered);
        }
        if (reordered) {
            sceneReorderReuseCount++;
            dirtyStart = 0;
            dirtyEnd = Math.max(0, gpuMeshes.size() - 1);
        }
        if (dirtyEnd >= dirtyStart) {
            markSceneStateDirty(dirtyStart, dirtyEnd);
        }
    }

    private void uploadSceneMeshes(MemoryStack stack, List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        meshBufferRebuildCount++;
        destroySceneMeshes();
        Map<String, VulkanGpuTexture> textureCache = new HashMap<>();
        VulkanGpuTexture defaultAlbedo = createTextureFromPath(null, false);
        VulkanGpuTexture defaultNormal = createTextureFromPath(null, true);
        VulkanGpuTexture defaultMetallicRoughness = createTextureFromPath(null, false);
        VulkanGpuTexture defaultOcclusion = createTextureFromPath(null, false);
        iblIrradianceTexture = resolveOrCreateTexture(iblIrradiancePath, textureCache, defaultAlbedo, false);
        iblRadianceTexture = resolveOrCreateTexture(iblRadiancePath, textureCache, defaultAlbedo, false);
        iblBrdfLutTexture = resolveOrCreateTexture(iblBrdfLutPath, textureCache, defaultAlbedo, false);
        for (VulkanSceneMeshData mesh : sceneMeshes) {
            float[] vertices = mesh.vertices();
            int[] indices = mesh.indices();
            ByteBuffer vertexData = ByteBuffer.allocateDirect(vertices.length * Float.BYTES).order(ByteOrder.nativeOrder());
            FloatBuffer vb = vertexData.asFloatBuffer();
            vb.put(vertices);
            vertexData.limit(vertices.length * Float.BYTES);

            ByteBuffer indexData = ByteBuffer.allocateDirect(indices.length * Integer.BYTES).order(ByteOrder.nativeOrder());
            IntBuffer ib = indexData.asIntBuffer();
            ib.put(indices);
            indexData.limit(indices.length * Integer.BYTES);

            VulkanBufferAlloc vertexAlloc = createDeviceLocalBufferWithStaging(
                    stack,
                    vertexData,
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
            );
            VulkanBufferAlloc indexAlloc = createDeviceLocalBufferWithStaging(
                    stack,
                    indexData,
                    VK_BUFFER_USAGE_INDEX_BUFFER_BIT
            );
            int vertexHash = Arrays.hashCode(vertices);
            int indexHash = Arrays.hashCode(indices);
            String albedoKey = textureCacheKey(mesh.albedoTexturePath(), false);
            String normalKey = textureCacheKey(mesh.normalTexturePath(), true);
            String metallicRoughnessKey = textureCacheKey(mesh.metallicRoughnessTexturePath(), false);
            String occlusionKey = textureCacheKey(mesh.occlusionTexturePath(), false);
            VulkanGpuTexture albedoTexture = resolveOrCreateTexture(mesh.albedoTexturePath(), textureCache, defaultAlbedo, false);
            VulkanGpuTexture normalTexture = resolveOrCreateTexture(mesh.normalTexturePath(), textureCache, defaultNormal, true);
            VulkanGpuTexture metallicRoughnessTexture = resolveOrCreateTexture(
                    mesh.metallicRoughnessTexturePath(),
                    textureCache,
                    defaultMetallicRoughness,
                    false
            );
            VulkanGpuTexture occlusionTexture = resolveOrCreateTexture(mesh.occlusionTexturePath(), textureCache, defaultOcclusion, false);

            gpuMeshes.add(new VulkanGpuMesh(
                    vertexAlloc.buffer(),
                    vertexAlloc.memory(),
                    indexAlloc.buffer(),
                    indexAlloc.memory(),
                    indices.length,
                    vertexData.remaining(),
                    indexData.remaining(),
                    mesh.modelMatrix().clone(),
                    mesh.color()[0],
                    mesh.color()[1],
                    mesh.color()[2],
                    mesh.metallic(),
                    mesh.roughness(),
                    albedoTexture,
                    normalTexture,
                    metallicRoughnessTexture,
                    occlusionTexture,
                    mesh.meshId(),
                    vertexHash,
                    indexHash,
                    albedoKey,
                    normalKey,
                    metallicRoughnessKey,
                    occlusionKey
            ));
        }

        createTextureDescriptorSets(stack);

        long meshBytes = 0;
        long textureBytes = 0;
        Set<VulkanGpuTexture> uniqueTextures = new HashSet<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            meshBytes += mesh.vertexBytes + mesh.indexBytes;
            if (uniqueTextures.add(mesh.albedoTexture)) {
                textureBytes += mesh.albedoTexture.bytes();
            }
            if (uniqueTextures.add(mesh.normalTexture)) {
                textureBytes += mesh.normalTexture.bytes();
            }
            if (uniqueTextures.add(mesh.metallicRoughnessTexture)) {
                textureBytes += mesh.metallicRoughnessTexture.bytes();
            }
            if (uniqueTextures.add(mesh.occlusionTexture)) {
                textureBytes += mesh.occlusionTexture.bytes();
            }
        }
        if (uniqueTextures.add(iblIrradianceTexture)) {
            textureBytes += iblIrradianceTexture.bytes();
        }
        if (uniqueTextures.add(iblRadianceTexture)) {
            textureBytes += iblRadianceTexture.bytes();
        }
        if (uniqueTextures.add(iblBrdfLutTexture)) {
            textureBytes += iblBrdfLutTexture.bytes();
        }
        long uniformBytes = (long) uniformFrameSpanBytes * framesInFlight * 2L;
        estimatedGpuMemoryBytes = uniformBytes + meshBytes + textureBytes;
    }

    private void rebindSceneTexturesAndDynamicState(List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            byId.put(mesh.meshId, mesh);
        }
        Set<VulkanGpuTexture> oldTextures = collectLiveTextures(gpuMeshes, iblIrradianceTexture, iblRadianceTexture, iblBrdfLutTexture);
        List<VulkanGpuMesh> rebound = new ArrayList<>(sceneMeshes.size());
        Map<String, VulkanGpuTexture> textureCache = new HashMap<>();
        VulkanGpuTexture defaultAlbedo = createTextureFromPath(null, false);
        VulkanGpuTexture defaultNormal = createTextureFromPath(null, true);
        VulkanGpuTexture defaultMetallicRoughness = createTextureFromPath(null, false);
        VulkanGpuTexture defaultOcclusion = createTextureFromPath(null, false);
        VulkanGpuTexture newIblIrradiance = resolveOrCreateTexture(iblIrradiancePath, textureCache, defaultAlbedo, false);
        VulkanGpuTexture newIblRadiance = resolveOrCreateTexture(iblRadiancePath, textureCache, defaultAlbedo, false);
        VulkanGpuTexture newIblBrdfLut = resolveOrCreateTexture(iblBrdfLutPath, textureCache, defaultAlbedo, false);
        for (VulkanSceneMeshData sceneMesh : sceneMeshes) {
            VulkanGpuMesh mesh = byId.get(sceneMesh.meshId());
            if (mesh == null) {
                throw new EngineException(
                        EngineErrorCode.RESOURCE_CREATION_FAILED,
                        "Unable to resolve reusable mesh '" + sceneMesh.meshId() + "' for texture rebind path",
                        false
                );
            }
            String albedoKey = textureCacheKey(sceneMesh.albedoTexturePath(), false);
            String normalKey = textureCacheKey(sceneMesh.normalTexturePath(), true);
            String metallicRoughnessKey = textureCacheKey(sceneMesh.metallicRoughnessTexturePath(), false);
            String occlusionKey = textureCacheKey(sceneMesh.occlusionTexturePath(), false);
            VulkanGpuTexture albedoTexture = resolveOrCreateTexture(sceneMesh.albedoTexturePath(), textureCache, defaultAlbedo, false);
            VulkanGpuTexture normalTexture = resolveOrCreateTexture(sceneMesh.normalTexturePath(), textureCache, defaultNormal, true);
            VulkanGpuTexture metallicRoughnessTexture = resolveOrCreateTexture(
                    sceneMesh.metallicRoughnessTexturePath(),
                    textureCache,
                    defaultMetallicRoughness,
                    false
            );
            VulkanGpuTexture occlusionTexture = resolveOrCreateTexture(sceneMesh.occlusionTexturePath(), textureCache, defaultOcclusion, false);
            rebound.add(new VulkanGpuMesh(
                    mesh.vertexBuffer,
                    mesh.vertexMemory,
                    mesh.indexBuffer,
                    mesh.indexMemory,
                    mesh.indexCount,
                    mesh.vertexBytes,
                    mesh.indexBytes,
                    sceneMesh.modelMatrix().clone(),
                    sceneMesh.color()[0],
                    sceneMesh.color()[1],
                    sceneMesh.color()[2],
                    sceneMesh.metallic(),
                    sceneMesh.roughness(),
                    albedoTexture,
                    normalTexture,
                    metallicRoughnessTexture,
                    occlusionTexture,
                    mesh.meshId,
                    mesh.vertexHash,
                    mesh.indexHash,
                    albedoKey,
                    normalKey,
                    metallicRoughnessKey,
                    occlusionKey
            ));
        }
        gpuMeshes.clear();
        gpuMeshes.addAll(rebound);
        iblIrradianceTexture = newIblIrradiance;
        iblRadianceTexture = newIblRadiance;
        iblBrdfLutTexture = newIblBrdfLut;
        try (MemoryStack stack = stackPush()) {
            createTextureDescriptorSets(stack);
        }
        Set<VulkanGpuTexture> newTextures = collectLiveTextures(gpuMeshes, iblIrradianceTexture, iblRadianceTexture, iblBrdfLutTexture);
        oldTextures.removeAll(newTextures);
        destroyTextures(oldTextures);
        markSceneStateDirty(0, Math.max(0, sceneMeshes.size() - 1));
    }

    private void destroySceneMeshes() {
        if (device == null) {
            gpuMeshes.clear();
            return;
        }
        Set<VulkanGpuTexture> uniqueTextures = new HashSet<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            if (mesh.vertexBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, mesh.vertexBuffer, null);
            }
            if (mesh.vertexMemory != VK_NULL_HANDLE) {
                vkFreeMemory(device, mesh.vertexMemory, null);
            }
            if (mesh.indexBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, mesh.indexBuffer, null);
            }
            if (mesh.indexMemory != VK_NULL_HANDLE) {
                vkFreeMemory(device, mesh.indexMemory, null);
            }
            uniqueTextures.add(mesh.albedoTexture);
            uniqueTextures.add(mesh.normalTexture);
            uniqueTextures.add(mesh.metallicRoughnessTexture);
            uniqueTextures.add(mesh.occlusionTexture);
        }
        uniqueTextures.add(iblIrradianceTexture);
        uniqueTextures.add(iblRadianceTexture);
        uniqueTextures.add(iblBrdfLutTexture);
        destroyTextures(uniqueTextures);
        if (textureDescriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, textureDescriptorPool, null);
            textureDescriptorPool = VK_NULL_HANDLE;
        }
        iblIrradianceTexture = null;
        iblRadianceTexture = null;
        iblBrdfLutTexture = null;
        gpuMeshes.clear();
    }

    private Set<VulkanGpuTexture> collectLiveTextures(List<VulkanGpuMesh> meshes, VulkanGpuTexture iblIrr, VulkanGpuTexture iblRad, VulkanGpuTexture iblBrdf) {
        Set<VulkanGpuTexture> textures = new HashSet<>();
        for (VulkanGpuMesh mesh : meshes) {
            textures.add(mesh.albedoTexture);
            textures.add(mesh.normalTexture);
            textures.add(mesh.metallicRoughnessTexture);
            textures.add(mesh.occlusionTexture);
        }
        textures.add(iblIrr);
        textures.add(iblRad);
        textures.add(iblBrdf);
        return textures;
    }

    private void destroyTextures(Set<VulkanGpuTexture> textures) {
        if (device == null || textures == null || textures.isEmpty()) {
            return;
        }
        for (VulkanGpuTexture texture : textures) {
            if (texture == null) {
                continue;
            }
            if (texture.sampler() != VK_NULL_HANDLE) {
                VK10.vkDestroySampler(device, texture.sampler(), null);
            }
            if (texture.view() != VK_NULL_HANDLE) {
                vkDestroyImageView(device, texture.view(), null);
            }
            if (texture.image() != VK_NULL_HANDLE) {
                VK10.vkDestroyImage(device, texture.image(), null);
            }
            if (texture.memory() != VK_NULL_HANDLE) {
                vkFreeMemory(device, texture.memory(), null);
            }
        }
    }

    private VulkanGpuTexture resolveOrCreateTexture(
            Path texturePath,
            Map<String, VulkanGpuTexture> cache,
            VulkanGpuTexture defaultTexture,
            boolean normalMap
    )
            throws EngineException {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return defaultTexture;
        }
        String cacheKey = textureCacheKey(texturePath, normalMap);
        VulkanGpuTexture cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        VulkanGpuTexture created = createTextureFromPath(texturePath, normalMap);
        cache.put(cacheKey, created);
        return created;
    }

    private String textureCacheKey(Path texturePath, boolean normalMap) {
        if (texturePath == null) {
            return normalMap ? "normal:__default__" : "albedo:__default__";
        }
        return (normalMap ? "normal:" : "albedo:") + texturePath.toAbsolutePath().normalize();
    }

    private void createTextureDescriptorSets(MemoryStack stack) throws EngineException {
        if (gpuMeshes.isEmpty() || textureDescriptorSetLayout == VK_NULL_HANDLE) {
            return;
        }
        int requiredSetCount = gpuMeshes.size();
        int targetSetCapacity = targetDescriptorRingCapacity(requiredSetCount);
        boolean needsRebuild = textureDescriptorPool == VK_NULL_HANDLE || requiredSetCount > descriptorRingSetCapacity;
        if (!needsRebuild) {
            int resetResult = vkResetDescriptorPool(device, textureDescriptorPool, 0);
            if (resetResult == VK_SUCCESS) {
                descriptorRingPoolReuseCount++;
            } else {
                descriptorRingPoolResetFailureCount++;
                needsRebuild = true;
            }
        }

        if (needsRebuild) {
            if (textureDescriptorPool != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(device, textureDescriptorPool, null);
                textureDescriptorPool = VK_NULL_HANDLE;
                descriptorPoolRebuildCount++;
                if (requiredSetCount > descriptorRingSetCapacity) {
                    descriptorRingGrowthRebuildCount++;
                } else {
                    descriptorRingSteadyRebuildCount++;
                }
            }
            descriptorPoolBuildCount++;
            descriptorRingSetCapacity = targetSetCapacity;
            descriptorRingPeakSetCapacity = Math.max(descriptorRingPeakSetCapacity, descriptorRingSetCapacity);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(descriptorRingSetCapacity * 8);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(descriptorRingSetCapacity)
                    .pPoolSizes(poolSizes);
            var pPool = stack.longs(VK_NULL_HANDLE);
            int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
            if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateDescriptorPool(texture) failed: " + poolResult,
                        false
                );
            }
            textureDescriptorPool = pPool.get(0);
        }
        descriptorRingActiveSetCount = requiredSetCount;
        descriptorRingWasteSetCount = Math.max(0, descriptorRingSetCapacity - requiredSetCount);
        descriptorRingPeakWasteSetCount = Math.max(descriptorRingPeakWasteSetCount, descriptorRingWasteSetCount);

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(textureDescriptorPool);
        LongBufferWrapper setLayouts = LongBufferWrapper.allocate(stack, requiredSetCount);
        for (int i = 0; i < requiredSetCount; i++) {
            setLayouts.buffer().put(i, textureDescriptorSetLayout);
        }
        allocInfo.pSetLayouts(setLayouts.buffer());

        LongBufferWrapper allocatedSets = LongBufferWrapper.allocate(stack, requiredSetCount);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, allocatedSets.buffer());
        if (setResult != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkAllocateDescriptorSets(texture) failed: " + setResult,
                    false
            );
        }

        for (int i = 0; i < requiredSetCount; i++) {
            VulkanGpuMesh mesh = gpuMeshes.get(i);
            mesh.textureDescriptorSet = allocatedSets.buffer().get(i);

            VkDescriptorImageInfo.Buffer albedoInfo = VkDescriptorImageInfo.calloc(1, stack);
            albedoInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(mesh.albedoTexture.view())
                    .sampler(mesh.albedoTexture.sampler());
            VkDescriptorImageInfo.Buffer normalInfo = VkDescriptorImageInfo.calloc(1, stack);
            normalInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(mesh.normalTexture.view())
                    .sampler(mesh.normalTexture.sampler());
            VkDescriptorImageInfo.Buffer metallicRoughnessInfo = VkDescriptorImageInfo.calloc(1, stack);
            metallicRoughnessInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(mesh.metallicRoughnessTexture.view())
                    .sampler(mesh.metallicRoughnessTexture.sampler());
            VkDescriptorImageInfo.Buffer occlusionInfo = VkDescriptorImageInfo.calloc(1, stack);
            occlusionInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(mesh.occlusionTexture.view())
                    .sampler(mesh.occlusionTexture.sampler());
            VkDescriptorImageInfo.Buffer shadowInfo = VkDescriptorImageInfo.calloc(1, stack);
            shadowInfo.get(0)
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                    .imageView(shadowDepthImageView)
                    .sampler(shadowSampler);
            VkDescriptorImageInfo.Buffer iblIrradianceInfo = VkDescriptorImageInfo.calloc(1, stack);
            iblIrradianceInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(iblIrradianceTexture.view())
                    .sampler(iblIrradianceTexture.sampler());
            VkDescriptorImageInfo.Buffer iblRadianceInfo = VkDescriptorImageInfo.calloc(1, stack);
            iblRadianceInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(iblRadianceTexture.view())
                    .sampler(iblRadianceTexture.sampler());
            VkDescriptorImageInfo.Buffer iblBrdfLutInfo = VkDescriptorImageInfo.calloc(1, stack);
            iblBrdfLutInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(iblBrdfLutTexture.view())
                    .sampler(iblBrdfLutTexture.sampler());

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(8, stack);
            writes.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(albedoInfo);
            writes.get(1)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(normalInfo);
            writes.get(2)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(2)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(metallicRoughnessInfo);
            writes.get(3)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(3)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(occlusionInfo);
            writes.get(4)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(4)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(shadowInfo);
            writes.get(5)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(5)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(iblIrradianceInfo);
            writes.get(6)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(6)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(iblRadianceInfo);
            writes.get(7)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(7)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(iblBrdfLutInfo);
            vkUpdateDescriptorSets(device, writes, null);
        }
    }

    private VulkanGpuTexture createTextureFromPath(Path texturePath, boolean normalMap) throws EngineException {
        VulkanTexturePixelData pixels = loadTexturePixels(texturePath);
        if (pixels == null) {
            ByteBuffer data = memAlloc(4);
            if (normalMap) {
                data.put((byte) 0x80).put((byte) 0x80).put((byte) 0xFF).put((byte) 0xFF).flip();
            } else {
                data.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
            }
            pixels = new VulkanTexturePixelData(data, 1, 1);
        }
        try {
            return createTextureFromPixels(pixels);
        } finally {
            memFree(pixels.data());
        }
    }

    private VulkanTexturePixelData loadTexturePixels(Path texturePath) {
        Path sourcePath = texturePath;
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return null;
        }
        if (isKtxContainerPath(sourcePath)) {
            VulkanTexturePixelData decoded = loadTexturePixelsFromKtx(sourcePath);
            if (decoded != null) {
                return decoded;
            }
            sourcePath = resolveContainerSourcePath(sourcePath);
            if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
                return null;
            }
        }
        try {
            BufferedImage image = ImageIO.read(sourcePath.toFile());
            if (image != null) {
                return bufferedImageToPixels(image);
            }
        } catch (IOException ignored) {
            // Fall through to stb path.
        }
        return loadTexturePixelsViaStb(sourcePath);
    }

    private VulkanTexturePixelData loadTexturePixelsFromKtx(Path containerPath) {
        KtxDecodeUtil.DecodedRgba decoded = KtxDecodeUtil.decodeToRgbaIfSupported(containerPath);
        if (decoded == null) {
            return null;
        }
        int width = decoded.width();
        int height = decoded.height();
        byte[] src = decoded.rgbaBytes();
        ByteBuffer buffer = memAlloc(src.length);
        int rowBytes = width * 4;
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            buffer.put(src, srcY * rowBytes, rowBytes);
        }
        buffer.flip();
        return new VulkanTexturePixelData(buffer, width, height);
    }

    private VulkanTexturePixelData bufferedImageToPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer buffer = memAlloc(width * height * 4);
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, srcY);
                buffer.put((byte) ((argb >> 16) & 0xFF));
                buffer.put((byte) ((argb >> 8) & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buffer.flip();
        return new VulkanTexturePixelData(buffer, width, height);
    }

    private VulkanTexturePixelData loadTexturePixelsViaStb(Path texturePath) {
        String path = texturePath.toAbsolutePath().toString();
        try (MemoryStack stack = stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!stbi_info(path, x, y, channels)) {
                return null;
            }
            int width = x.get(0);
            int height = y.get(0);
            if (width <= 0 || height <= 0) {
                return null;
            }
            if (stbi_is_hdr(path)) {
                FloatBuffer hdr = stbi_loadf(path, x, y, channels, 4);
                if (hdr == null) {
                    return null;
                }
                try {
                    ByteBuffer buffer = memAlloc(width * height * 4);
                    for (int i = 0; i < width * height; i++) {
                        float r = hdr.get(i * 4);
                        float g = hdr.get(i * 4 + 1);
                        float b = hdr.get(i * 4 + 2);
                        float a = hdr.get(i * 4 + 3);
                        buffer.put((byte) toLdrByte(r));
                        buffer.put((byte) toLdrByte(g));
                        buffer.put((byte) toLdrByte(b));
                        buffer.put((byte) Math.max(0, Math.min(255, Math.round(Math.max(0f, Math.min(1f, a)) * 255f))));
                    }
                    buffer.flip();
                    return new VulkanTexturePixelData(buffer, width, height);
                } finally {
                    stbi_image_free(hdr);
                }
            }
            ByteBuffer ldr = stbi_load(path, x, y, channels, 4);
            if (ldr == null) {
                return null;
            }
            try {
                ByteBuffer copy = memAlloc(width * height * 4);
                copy.put(ldr);
                copy.flip();
                return new VulkanTexturePixelData(copy, width, height);
            } finally {
                stbi_image_free(ldr);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int toLdrByte(float hdrValue) {
        float toneMapped = hdrValue / (1.0f + Math.max(0f, hdrValue));
        float gammaCorrected = (float) Math.pow(Math.max(0f, toneMapped), 1.0 / 2.2);
        return Math.max(0, Math.min(255, Math.round(gammaCorrected * 255f)));
    }

    private static Path resolveContainerSourcePath(Path requestedPath) {
        if (requestedPath == null || !Files.isRegularFile(requestedPath) || !isKtxContainerPath(requestedPath)) {
            return requestedPath;
        }
        String fileName = requestedPath.getFileName() == null ? null : requestedPath.getFileName().toString();
        if (fileName == null) {
            return requestedPath;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return requestedPath;
        }
        String baseName = fileName.substring(0, dot);
        for (String ext : new String[]{".png", ".hdr", ".jpg", ".jpeg"}) {
            Path candidate = requestedPath.resolveSibling(baseName + ext);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return requestedPath;
    }

    private static boolean isKtxContainerPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".ktx") || name.endsWith(".ktx2");
    }

    private VulkanGpuTexture createTextureFromPixels(VulkanTexturePixelData pixels) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VulkanBufferAlloc staging = createBuffer(
                    stack,
                    pixels.data().remaining(),
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            try {
                uploadToMemory(staging.memory(), pixels.data());
                VulkanImageAlloc imageAlloc = createImage(
                        stack,
                        pixels.width(),
                        pixels.height(),
                        VK10.VK_FORMAT_R8G8B8A8_SRGB,
                        VK10.VK_IMAGE_TILING_OPTIMAL,
                        VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );
                transitionImageLayout(imageAlloc.image(), VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                copyBufferToImage(staging.buffer(), imageAlloc.image(), pixels.width(), pixels.height());
                transitionImageLayout(imageAlloc.image(), VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

                long imageView = createImageView(stack, imageAlloc.image(), VK10.VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT);
                long sampler = createSampler(stack);
                return new VulkanGpuTexture(imageAlloc.image(), imageAlloc.memory(), imageView, sampler, (long) pixels.width() * pixels.height() * 4L);
            } finally {
                if (staging.buffer() != VK_NULL_HANDLE) {
                    vkDestroyBuffer(device, staging.buffer(), null);
                }
                if (staging.memory() != VK_NULL_HANDLE) {
                    vkFreeMemory(device, staging.memory(), null);
                }
            }
        }
    }

    private VulkanImageAlloc createImage(
            MemoryStack stack,
            int width,
            int height,
            int format,
            int tiling,
            int usage,
            int properties
    ) throws EngineException {
        return createImage(stack, width, height, format, tiling, usage, properties, 1);
    }

    private VulkanImageAlloc createImage(
            MemoryStack stack,
            int width,
            int height,
            int format,
            int tiling,
            int usage,
            int properties,
            int arrayLayers
    ) throws EngineException {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK10.VK_IMAGE_TYPE_2D)
                .extent(e -> e.width(width).height(height).depth(1))
                .mipLevels(1)
                .arrayLayers(Math.max(1, arrayLayers))
                .format(format)
                .tiling(tiling)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(usage)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        var pImage = stack.longs(VK_NULL_HANDLE);
        int createImageResult = VK10.vkCreateImage(device, imageInfo, null, pImage);
        if (createImageResult != VK_SUCCESS || pImage.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImage failed: " + createImageResult, false);
        }
        long image = pImage.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        VK10.vkGetImageMemoryRequirements(device, image, memReq);

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(findMemoryType(memReq.memoryTypeBits(), properties));

        var pMemory = stack.longs(VK_NULL_HANDLE);
        int allocResult = vkAllocateMemory(device, allocInfo, null, pMemory);
        if (allocResult != VK_SUCCESS || pMemory.get(0) == VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, image, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateMemory(image) failed: " + allocResult, false);
        }
        long memory = pMemory.get(0);
        int bindResult = VK10.vkBindImageMemory(device, image, memory, 0);
        if (bindResult != VK_SUCCESS) {
            vkFreeMemory(device, memory, null);
            VK10.vkDestroyImage(device, image, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkBindImageMemory failed: " + bindResult, false);
        }
        return new VulkanImageAlloc(image, memory);
    }

    private void transitionImageLayout(long image, int oldLayout, int newLayout) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer cmd = beginSingleTimeCommands(stack);
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            int sourceStage;
            int destinationStage;
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.get(0).srcAccessMask(0);
                barrier.get(0).dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                    && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.get(0).srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.get(0).dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Unsupported image layout transition", false);
            }

            VK10.vkCmdPipelineBarrier(
                    cmd,
                    sourceStage,
                    destinationStage,
                    0,
                    null,
                    null,
                    barrier
            );
            endSingleTimeCommands(stack, cmd);
        }
    }

    private void copyBufferToImage(long buffer, long image, int width, int height) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer cmd = beginSingleTimeCommands(stack);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0);
            region.get(0).imageSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(width, height, 1);
            VK10.vkCmdCopyBufferToImage(cmd, buffer, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            endSingleTimeCommands(stack, cmd);
        }
    }

    private VkCommandBuffer beginSingleTimeCommands(MemoryStack stack) throws EngineException {
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
        PointerBuffer pCommandBuffer = stack.mallocPointer(1);
        int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        if (allocResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers(one-shot) failed: " + allocResult, false);
        }
        VkCommandBuffer cmd = new VkCommandBuffer(pCommandBuffer.get(0), device);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        int beginResult = vkBeginCommandBuffer(cmd, beginInfo);
        if (beginResult != VK_SUCCESS) {
            throw vkFailure("vkBeginCommandBuffer(one-shot)", beginResult);
        }
        return cmd;
    }

    private void endSingleTimeCommands(MemoryStack stack, VkCommandBuffer cmd) throws EngineException {
        int endResult = vkEndCommandBuffer(cmd);
        if (endResult != VK_SUCCESS) {
            throw vkFailure("vkEndCommandBuffer(one-shot)", endResult);
        }
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(cmd.address()));
        int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
        if (submitResult != VK_SUCCESS) {
            throw vkFailure("vkQueueSubmit(one-shot)", submitResult);
        }
        int waitResult = vkQueueWaitIdle(graphicsQueue);
        if (waitResult != VK_SUCCESS) {
            throw vkFailure("vkQueueWaitIdle(one-shot)", waitResult);
        }
        vkFreeCommandBuffers(device, commandPool, stack.pointers(cmd.address()));
    }

    private long createImageView(MemoryStack stack, long image, int format, int aspectMask) throws EngineException {
        return createImageView(stack, image, format, aspectMask, VK_IMAGE_VIEW_TYPE_2D, 0, 1);
    }

    private long createImageView(
            MemoryStack stack,
            long image,
            int format,
            int aspectMask,
            int viewType,
            int baseArrayLayer,
            int layerCount
    ) throws EngineException {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(viewType)
                .format(format);
        viewInfo.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(baseArrayLayer)
                .layerCount(layerCount);
        var pView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pView);
        if (result != VK_SUCCESS || pView.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImageView(texture) failed: " + result, false);
        }
        return pView.get(0);
    }

    private long createSampler(MemoryStack stack) throws EngineException {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK10.VK_FILTER_LINEAR)
                .minFilter(VK10.VK_FILTER_LINEAR)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
                .mipLodBias(0.0f)
                .minLod(0.0f)
                .maxLod(0.0f);
        var pSampler = stack.longs(VK_NULL_HANDLE);
        int result = VK10.vkCreateSampler(device, samplerInfo, null, pSampler);
        if (result != VK_SUCCESS || pSampler.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSampler failed: " + result, false);
        }
        return pSampler.get(0);
    }

    private void markGlobalStateDirty() {
        globalStateRevision++;
    }

    private void markSceneStateDirty(int dirtyStart, int dirtyEnd) {
        if (dirtyEnd < dirtyStart) {
            return;
        }
        sceneStateRevision++;
        addPendingSceneDirtyRange(Math.max(0, dirtyStart), Math.max(0, dirtyEnd));
    }

    private void addPendingSceneDirtyRange(int start, int end) {
        if (end < start) {
            return;
        }
        if (pendingSceneDirtyRangeCount >= pendingSceneDirtyStarts.length && !tryGrowUploadRangeTracking()) {
            pendingUploadRangeOverflowCount++;
            pendingSceneDirtyRangeCount = 1;
            pendingSceneDirtyStarts[0] = 0;
            pendingSceneDirtyEnds[0] = Math.max(start, end);
            return;
        }
        pendingSceneDirtyStarts[pendingSceneDirtyRangeCount] = start;
        pendingSceneDirtyEnds[pendingSceneDirtyRangeCount] = end;
        pendingSceneDirtyRangeCount++;
        normalizePendingSceneDirtyRanges();
    }

    private void normalizePendingSceneDirtyRanges() {
        if (pendingSceneDirtyRangeCount <= 1) {
            return;
        }
        for (int i = 1; i < pendingSceneDirtyRangeCount; i++) {
            int start = pendingSceneDirtyStarts[i];
            int end = pendingSceneDirtyEnds[i];
            int j = i - 1;
            while (j >= 0 && pendingSceneDirtyStarts[j] > start) {
                pendingSceneDirtyStarts[j + 1] = pendingSceneDirtyStarts[j];
                pendingSceneDirtyEnds[j + 1] = pendingSceneDirtyEnds[j];
                j--;
            }
            pendingSceneDirtyStarts[j + 1] = start;
            pendingSceneDirtyEnds[j + 1] = end;
        }
        int write = 0;
        for (int read = 1; read < pendingSceneDirtyRangeCount; read++) {
            int currStart = pendingSceneDirtyStarts[read];
            int currEnd = pendingSceneDirtyEnds[read];
            int prevEnd = pendingSceneDirtyEnds[write];
            if (currStart <= (prevEnd + 1 + dynamicUploadMergeGapObjects)) {
                pendingSceneDirtyEnds[write] = Math.max(prevEnd, currEnd);
            } else {
                write++;
                pendingSceneDirtyStarts[write] = currStart;
                pendingSceneDirtyEnds[write] = currEnd;
            }
        }
        pendingSceneDirtyRangeCount = write + 1;
    }

    private boolean allFramesApplied(long[] frameRevisions, long revision) {
        for (long frameRevision : frameRevisions) {
            if (frameRevision != revision) {
                return false;
            }
        }
        return true;
    }

    private void prepareFrameUniforms(int frameIdx) throws EngineException {
        int meshCount = Math.max(1, gpuMeshes.size());
        maxObservedDynamicObjects = Math.max(maxObservedDynamicObjects, meshCount);
        if (meshCount > maxDynamicSceneObjects) {
            throw new EngineException(
                    EngineErrorCode.RESOURCE_CREATION_FAILED,
                    "Scene mesh count " + meshCount + " exceeds dynamic uniform capacity " + maxDynamicSceneObjects,
                    false
            );
        }
        int normalizedFrame = Math.floorMod(frameIdx, framesInFlight);
        int frameBase = normalizedFrame * uniformFrameSpanBytes;
        boolean globalStale = frameGlobalRevisionApplied[normalizedFrame] != globalStateRevision;
        boolean sceneStale = frameSceneRevisionApplied[normalizedFrame] != sceneStateRevision;
        if (!globalStale && !sceneStale) {
            clearPendingUploads();
            return;
        }
        pendingGlobalUploadSrcOffset = -1L;
        pendingGlobalUploadDstOffset = -1L;
        pendingGlobalUploadByteCount = 0;

        long globalFrameBase = (long) normalizedFrame * globalUniformFrameSpanBytes;
        if (globalStale) {
            ByteBuffer globalMapped;
            if (sceneGlobalUniformStagingMappedAddress != 0L) {
                globalMapped = memByteBuffer(sceneGlobalUniformStagingMappedAddress + globalFrameBase, GLOBAL_SCENE_UNIFORM_BYTES);
            } else {
                globalMapped = memAlloc(GLOBAL_SCENE_UNIFORM_BYTES);
            }
            globalMapped.order(ByteOrder.nativeOrder());
            try {
                writeGlobalSceneUniform(globalMapped);
                if (sceneGlobalUniformStagingMappedAddress == 0L) {
                    try (MemoryStack stack = stackPush()) {
                        PointerBuffer pData = stack.mallocPointer(1);
                        int mapResult = vkMapMemory(device, sceneGlobalUniformStagingMemory, globalFrameBase, GLOBAL_SCENE_UNIFORM_BYTES, 0, pData);
                        if (mapResult != VK_SUCCESS) {
                            throw vkFailure("vkMapMemory(globalStaging)", mapResult);
                        }
                        memCopy(memAddress(globalMapped), pData.get(0), GLOBAL_SCENE_UNIFORM_BYTES);
                        vkUnmapMemory(device, sceneGlobalUniformStagingMemory);
                    }
                }
            } finally {
                if (sceneGlobalUniformStagingMappedAddress == 0L) {
                    memFree(globalMapped);
                }
            }
            pendingGlobalUploadSrcOffset = globalFrameBase;
            pendingGlobalUploadDstOffset = globalFrameBase;
            pendingGlobalUploadByteCount = GLOBAL_SCENE_UNIFORM_BYTES;
        }

        int uploadRangeCount;
        int uploadCapacity = pendingUploadSrcOffsets.length;
        int[] uploadStarts = new int[uploadCapacity];
        int[] uploadEnds = new int[uploadCapacity];
        if (sceneStale && pendingSceneDirtyRangeCount > 0) {
            uploadRangeCount = 0;
            for (int i = 0; i < pendingSceneDirtyRangeCount && uploadRangeCount < uploadCapacity; i++) {
                int start = Math.max(0, Math.min(meshCount - 1, pendingSceneDirtyStarts[i]));
                int end = Math.max(start, Math.min(meshCount - 1, pendingSceneDirtyEnds[i]));
                uploadStarts[uploadRangeCount] = start;
                uploadEnds[uploadRangeCount] = end;
                uploadRangeCount++;
            }
            if (uploadRangeCount == 0) {
                uploadRangeCount = 1;
                uploadStarts[0] = 0;
                uploadEnds[0] = meshCount - 1;
            }
        } else {
            uploadRangeCount = 1;
            uploadStarts[0] = 0;
            uploadEnds[0] = meshCount - 1;
        }

        ByteBuffer mapped;
        if (objectUniformStagingMappedAddress != 0L) {
            mapped = memByteBuffer(objectUniformStagingMappedAddress + frameBase, uniformFrameSpanBytes);
        } else {
            mapped = memAlloc(uniformFrameSpanBytes);
        }
        mapped.order(ByteOrder.nativeOrder());
        try {
            for (int range = 0; range < uploadRangeCount; range++) {
                int rangeStart = uploadStarts[range];
                int rangeEnd = uploadEnds[range];
                for (int meshIndex = rangeStart; meshIndex <= rangeEnd; meshIndex++) {
                    VulkanGpuMesh mesh = gpuMeshes.isEmpty() ? null : gpuMeshes.get(meshIndex);
                    writeObjectUniform(mapped, meshIndex * uniformStrideBytes, mesh);
                }
            }
            if (objectUniformStagingMappedAddress == 0L) {
                try (MemoryStack stack = stackPush()) {
                    PointerBuffer pData = stack.mallocPointer(1);
                    int mapResult = vkMapMemory(device, objectUniformStagingMemory, frameBase, uniformFrameSpanBytes, 0, pData);
                    if (mapResult != VK_SUCCESS) {
                        throw vkFailure("vkMapMemory(objectStaging)", mapResult);
                    }
                    for (int range = 0; range < uploadRangeCount; range++) {
                        int rangeStartByte = uploadStarts[range] * uniformStrideBytes;
                        int rangeByteCount = ((uploadEnds[range] - uploadStarts[range]) + 1) * uniformStrideBytes;
                        memCopy(memAddress(mapped) + rangeStartByte, pData.get(0) + rangeStartByte, rangeByteCount);
                    }
                    vkUnmapMemory(device, objectUniformStagingMemory);
                }
            }
        } finally {
            if (objectUniformStagingMappedAddress == 0L) {
                memFree(mapped);
            }
        }
        pendingUploadRangeCount = uploadRangeCount;
        pendingUploadObjectCount = 0;
        pendingUploadStartObject = Integer.MAX_VALUE;
        pendingUploadByteCount = 0;
        for (int range = 0; range < uploadRangeCount; range++) {
            int rangeStartByte = uploadStarts[range] * uniformStrideBytes;
            int rangeByteCount = ((uploadEnds[range] - uploadStarts[range]) + 1) * uniformStrideBytes;
            long srcOffset = (long) frameBase + rangeStartByte;
            pendingUploadSrcOffsets[range] = srcOffset;
            pendingUploadDstOffsets[range] = srcOffset;
            pendingUploadByteCounts[range] = rangeByteCount;
            pendingUploadObjectCount += (uploadEnds[range] - uploadStarts[range]) + 1;
            pendingUploadStartObject = Math.min(pendingUploadStartObject, uploadStarts[range]);
            pendingUploadByteCount += rangeByteCount;
        }
        if (pendingUploadRangeCount == 1) {
            pendingUploadSrcOffset = pendingUploadSrcOffsets[0];
            pendingUploadDstOffset = pendingUploadDstOffsets[0];
            pendingUploadByteCount = pendingUploadByteCounts[0];
        } else {
            pendingUploadSrcOffset = -1L;
            pendingUploadDstOffset = -1L;
        }
        if (pendingUploadStartObject == Integer.MAX_VALUE) {
            pendingUploadStartObject = 0;
        }
        frameGlobalRevisionApplied[normalizedFrame] = globalStateRevision;
        frameSceneRevisionApplied[normalizedFrame] = sceneStateRevision;
        if (pendingSceneDirtyRangeCount > 0 && allFramesApplied(frameSceneRevisionApplied, sceneStateRevision)) {
            pendingSceneDirtyRangeCount = 0;
        }
    }

    private void writeGlobalSceneUniform(ByteBuffer target) {
        target.position(0);
        target.limit(GLOBAL_SCENE_UNIFORM_BYTES);
        FloatBuffer fb = target.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(viewMatrix);
        fb.put(projMatrix);
        fb.put(new float[]{dirLightDirX, dirLightDirY, dirLightDirZ, 0f});
        fb.put(new float[]{dirLightColorR, dirLightColorG, dirLightColorB, 0f});
        fb.put(new float[]{pointLightPosX, pointLightPosY, pointLightPosZ, pointShadowFarPlane});
        fb.put(new float[]{pointLightColorR, pointLightColorG, pointLightColorB, 0f});
        fb.put(new float[]{pointLightDirX, pointLightDirY, pointLightDirZ, 0f});
        fb.put(new float[]{pointLightInnerCos, pointLightOuterCos, pointLightIsSpot, pointShadowEnabled ? 1f : 0f});
        fb.put(new float[]{dirLightIntensity, pointLightIntensity, 0f, 0f});
        fb.put(new float[]{shadowEnabled ? 1f : 0f, shadowStrength, shadowBias, (float) shadowPcfRadius});
        float split1 = shadowCascadeSplitNdc[0];
        float split2 = shadowCascadeSplitNdc[1];
        float split3 = shadowCascadeSplitNdc[2];
        fb.put(new float[]{(float) shadowCascadeCount, (float) shadowMapResolution, split1, split2});
        fb.put(new float[]{0f, split3, 0f, 0f});
        fb.put(new float[]{fogEnabled ? 1f : 0f, fogDensity, 0f, 0f});
        fb.put(new float[]{fogR, fogG, fogB, (float) fogSteps});
        float viewportW = (float) Math.max(1, swapchainWidth);
        float viewportH = (float) Math.max(1, swapchainHeight);
        fb.put(new float[]{smokeEnabled ? 1f : 0f, smokeIntensity, viewportW, viewportH});
        fb.put(new float[]{smokeR, smokeG, smokeB, 0f});
        fb.put(new float[]{iblEnabled ? 1f : 0f, iblDiffuseStrength, iblSpecularStrength, iblPrefilterStrength});
        boolean scenePostEnabled = !postOffscreenActive;
        fb.put(new float[]{scenePostEnabled && tonemapEnabled ? 1f : 0f, tonemapExposure, tonemapGamma, 0f});
        fb.put(new float[]{scenePostEnabled && bloomEnabled ? 1f : 0f, bloomThreshold, bloomStrength, 0f});
        for (int i = 0; i < MAX_SHADOW_MATRICES; i++) {
            fb.put(shadowLightViewProjMatrices[i]);
        }
    }

    private void writeObjectUniform(ByteBuffer target, int offset, VulkanGpuMesh mesh) {
        ByteBuffer slice = target.duplicate();
        slice.position(offset);
        slice.limit(offset + OBJECT_UNIFORM_BYTES);
        FloatBuffer fb = slice.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        if (mesh == null) {
            fb.put(identityMatrix());
            fb.put(new float[]{1f, 1f, 1f, 1f});
            fb.put(new float[]{0f, 0.8f, 0f, 0f});
        } else {
            fb.put(mesh.modelMatrix);
            fb.put(new float[]{mesh.colorR, mesh.colorG, mesh.colorB, 1f});
            fb.put(new float[]{mesh.metallic, mesh.roughness, 0f, 0f});
        }
    }

    private void uploadFrameUniforms(VkCommandBuffer commandBuffer, int frameIdx) {
        if (pendingGlobalUploadByteCount > 0) {
            VkBufferCopy.Buffer globalCopy = VkBufferCopy.calloc(1)
                    .srcOffset(pendingGlobalUploadSrcOffset)
                    .dstOffset(pendingGlobalUploadDstOffset)
                    .size(pendingGlobalUploadByteCount);
            vkCmdCopyBuffer(commandBuffer, sceneGlobalUniformStagingBuffer, sceneGlobalUniformBuffer, globalCopy);
            globalCopy.free();

            VkBufferMemoryBarrier.Buffer globalBarrier = VkBufferMemoryBarrier.calloc(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_UNIFORM_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(sceneGlobalUniformBuffer)
                    .offset(pendingGlobalUploadDstOffset)
                    .size(pendingGlobalUploadByteCount);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    globalBarrier,
                    null
            );
            globalBarrier.free();
        }
        lastFrameGlobalUploadBytes = pendingGlobalUploadByteCount;
        maxFrameGlobalUploadBytes = Math.max(maxFrameGlobalUploadBytes, pendingGlobalUploadByteCount);

        if (pendingUploadRangeCount <= 0) {
            lastFrameUniformUploadBytes = 0;
            lastFrameUniformObjectCount = 0;
            lastFrameUniformUploadRanges = 0;
            lastFrameUniformUploadStartObject = 0;
            return;
        }

        int totalByteCount = 0;
        for (int i = 0; i < pendingUploadRangeCount; i++) {
            totalByteCount += pendingUploadByteCounts[i];
        }
        lastFrameUniformUploadBytes = totalByteCount;
        maxFrameUniformUploadBytes = Math.max(maxFrameUniformUploadBytes, totalByteCount);
        lastFrameUniformObjectCount = pendingUploadObjectCount;
        maxFrameUniformObjectCount = Math.max(maxFrameUniformObjectCount, pendingUploadObjectCount);
        lastFrameUniformUploadRanges = pendingUploadRangeCount;
        maxFrameUniformUploadRanges = Math.max(maxFrameUniformUploadRanges, pendingUploadRangeCount);
        lastFrameUniformUploadStartObject = pendingUploadStartObject;

        for (int range = 0; range < pendingUploadRangeCount; range++) {
            VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1)
                    .srcOffset(pendingUploadSrcOffsets[range])
                    .dstOffset(pendingUploadDstOffsets[range])
                    .size(pendingUploadByteCounts[range]);
            vkCmdCopyBuffer(commandBuffer, objectUniformStagingBuffer, objectUniformBuffer, copy);
            copy.free();

            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_UNIFORM_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(objectUniformBuffer)
                    .offset(pendingUploadDstOffsets[range])
                    .size(pendingUploadByteCounts[range]);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    barrier,
                    null
            );
            barrier.free();
        }
        clearPendingUploads();
    }

    private void clearPendingUploads() {
        pendingUploadSrcOffset = -1L;
        pendingUploadDstOffset = -1L;
        pendingUploadByteCount = 0;
        pendingUploadObjectCount = 0;
        pendingUploadStartObject = 0;
        pendingUploadRangeCount = 0;
        pendingGlobalUploadSrcOffset = -1L;
        pendingGlobalUploadDstOffset = -1L;
        pendingGlobalUploadByteCount = 0;
    }

    private int dynamicUniformOffset(int frameIdx, int meshIndex) {
        return meshIndex * uniformStrideBytes;
    }

    private long descriptorSetForFrame(int frameIdx) {
        if (frameDescriptorSets.length == 0) {
            return descriptorSet;
        }
        int normalizedFrame = Math.floorMod(frameIdx, frameDescriptorSets.length);
        return frameDescriptorSets[normalizedFrame];
    }

    private VulkanBufferAlloc createBuffer(MemoryStack stack, int sizeBytes, int usage, int memoryProperties) throws EngineException {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(sizeBytes)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        var pBuffer = stack.longs(VK_NULL_HANDLE);
        int createBufferResult = vkCreateBuffer(device, bufferInfo, null, pBuffer);
        if (createBufferResult != VK_SUCCESS || pBuffer.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateBuffer failed: " + createBufferResult, false);
        }
        long buffer = pBuffer.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        vkGetBufferMemoryRequirements(device, buffer, memReq);

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(findMemoryType(memReq.memoryTypeBits(), memoryProperties));

        var pMemory = stack.longs(VK_NULL_HANDLE);
        int allocResult = vkAllocateMemory(device, allocInfo, null, pMemory);
        if (allocResult != VK_SUCCESS || pMemory.get(0) == VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateMemory failed: " + allocResult, false);
        }
        long memory = pMemory.get(0);
        int bindResult = vkBindBufferMemory(device, buffer, memory, 0);
        if (bindResult != VK_SUCCESS) {
            vkFreeMemory(device, memory, null);
            vkDestroyBuffer(device, buffer, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkBindBufferMemory failed: " + bindResult, false);
        }
        return new VulkanBufferAlloc(buffer, memory);
    }

    private VulkanBufferAlloc createDeviceLocalBufferWithStaging(MemoryStack stack, ByteBuffer source, int usage) throws EngineException {
        int sizeBytes = source.remaining();
        VulkanBufferAlloc staging = createBuffer(
                stack,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        VulkanBufferAlloc deviceLocal = createBuffer(
                stack,
                sizeBytes,
                usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        try {
            uploadToMemory(staging.memory(), source);
            copyBuffer(staging.buffer(), deviceLocal.buffer(), sizeBytes);
            return deviceLocal;
        } finally {
            if (staging.buffer() != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, staging.buffer(), null);
            }
            if (staging.memory() != VK_NULL_HANDLE) {
                vkFreeMemory(device, staging.memory(), null);
            }
        }
    }

    private void copyBuffer(long srcBuffer, long dstBuffer, int sizeBytes) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
            if (allocResult != VK_SUCCESS) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers(copy) failed: " + allocResult, false);
            }
            VkCommandBuffer cmd = new VkCommandBuffer(pCommandBuffer.get(0), device);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            int beginResult = vkBeginCommandBuffer(cmd, beginInfo);
            if (beginResult != VK_SUCCESS) {
                throw vkFailure("vkBeginCommandBuffer(copy)", beginResult);
            }

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.get(0).srcOffset(0).dstOffset(0).size(sizeBytes);
            vkCmdCopyBuffer(cmd, srcBuffer, dstBuffer, copyRegion);

            int endResult = vkEndCommandBuffer(cmd);
            if (endResult != VK_SUCCESS) {
                throw vkFailure("vkEndCommandBuffer(copy)", endResult);
            }

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmd.address()));
            int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            if (submitResult != VK_SUCCESS) {
                throw vkFailure("vkQueueSubmit(copy)", submitResult);
            }
            int waitResult = vkQueueWaitIdle(graphicsQueue);
            if (waitResult != VK_SUCCESS) {
                throw vkFailure("vkQueueWaitIdle(copy)", waitResult);
            }
            vkFreeCommandBuffers(device, commandPool, stack.pointers(cmd.address()));
        }
    }

    private void uploadToMemory(long memory, ByteBuffer source) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, memory, 0, source.remaining(), 0, pData);
            if (mapResult != VK_SUCCESS) {
                throw vkFailure("vkMapMemory", mapResult);
            }
            memCopy(memAddress(source), pData.get(0), source.remaining());
            vkUnmapMemory(device, memory);
        }
    }

    private int findMemoryType(int typeFilter, int properties) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                boolean typeMatch = (typeFilter & (1 << i)) != 0;
                boolean propsMatch = (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties;
                if (typeMatch && propsMatch) {
                    return i;
                }
            }
        }
        throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No suitable Vulkan memory type found", false);
    }

    private int targetDescriptorRingCapacity(int requiredSetCount) {
        VulkanDescriptorRingPolicy.Decision decision = VulkanDescriptorRingPolicy.decide(
                descriptorRingSetCapacity,
                requiredSetCount,
                descriptorRingMaxSetCapacity
        );
        if (decision.capBypass()) {
            descriptorRingCapBypassCount++;
        }
        return decision.targetCapacity();
    }

    private void reallocateFrameTracking() {
        frameGlobalRevisionApplied = new long[framesInFlight];
        frameSceneRevisionApplied = new long[framesInFlight];
    }

    private void reallocateUploadRangeTracking() {
        int capacity = Math.max(8, maxPendingUploadRanges);
        pendingSceneDirtyStarts = new int[capacity];
        pendingSceneDirtyEnds = new int[capacity];
        pendingUploadSrcOffsets = new long[capacity];
        pendingUploadDstOffsets = new long[capacity];
        pendingUploadByteCounts = new int[capacity];
    }

    private boolean tryGrowUploadRangeTracking() {
        int current = pendingSceneDirtyStarts.length;
        if (current >= MAX_PENDING_UPLOAD_RANGES_HARD_CAP) {
            return false;
        }
        int target = Math.min(MAX_PENDING_UPLOAD_RANGES_HARD_CAP, Math.max(current + 1, current * 2));
        pendingSceneDirtyStarts = Arrays.copyOf(pendingSceneDirtyStarts, target);
        pendingSceneDirtyEnds = Arrays.copyOf(pendingSceneDirtyEnds, target);
        pendingUploadSrcOffsets = Arrays.copyOf(pendingUploadSrcOffsets, target);
        pendingUploadDstOffsets = Arrays.copyOf(pendingUploadDstOffsets, target);
        pendingUploadByteCounts = Arrays.copyOf(pendingUploadByteCounts, target);
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class LongBufferWrapper {
        private final java.nio.LongBuffer buffer;

        private LongBufferWrapper(java.nio.LongBuffer buffer) {
            this.buffer = buffer;
        }

        static LongBufferWrapper allocate(MemoryStack stack, int count) {
            return new LongBufferWrapper(stack.mallocLong(count));
        }

        java.nio.LongBuffer buffer() {
            return buffer;
        }

        long[] toArray() {
            long[] out = new long[buffer.capacity()];
            for (int i = 0; i < out.length; i++) {
                out[i] = buffer.get(i);
            }
            return out;
        }
    }
}
