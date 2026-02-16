package org.dynamislight.impl.vulkan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.command.VulkanCommandSubmitter;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandOrchestrator;
import org.dynamislight.impl.vulkan.command.VulkanFrameSyncResources;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorRingPolicy;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamislight.impl.vulkan.descriptor.VulkanTextureDescriptorPoolManager;
import org.dynamislight.impl.vulkan.descriptor.VulkanTextureDescriptorWriter;
import org.dynamislight.impl.vulkan.pipeline.VulkanMainPipelineBuilder;
import org.dynamislight.impl.vulkan.pipeline.VulkanPostProcessResources;
import org.dynamislight.impl.vulkan.pipeline.VulkanPostPipelineBuilder;
import org.dynamislight.impl.vulkan.pipeline.VulkanShadowPipelineBuilder;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
import org.dynamislight.impl.vulkan.scene.VulkanDynamicSceneUpdater;
import org.dynamislight.impl.vulkan.scene.VulkanSceneMeshLifecycle;
import org.dynamislight.impl.vulkan.scene.VulkanSceneReusePolicy;
import org.dynamislight.impl.vulkan.shader.VulkanShaderCompiler;
import org.dynamislight.impl.vulkan.shader.VulkanShaderSources;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowMatrixBuilder;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowResources;
import org.dynamislight.impl.vulkan.swapchain.VulkanFramebufferResources;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainAllocation;
import org.dynamislight.impl.vulkan.swapchain.VulkanDeviceSelector;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainImageViews;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainSelector;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.dynamislight.impl.vulkan.uniform.VulkanFrameUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformUploadRecorder;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformWriters;

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
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
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
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_ERROR_INITIALIZATION_FAILED;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
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
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
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
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TRUE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
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
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
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
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
import static org.lwjgl.vulkan.VK10.vkResetDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
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
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
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
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
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
            createFrameSyncResources(stack);
            createShadowResources(stack);
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
        if (VulkanSceneReusePolicy.canReuseGpuMeshes(gpuMeshes, safe, this::textureCacheKey)) {
            sceneReuseHitCount++;
            descriptorRingReuseHitCount++;
            updateDynamicSceneState(safe);
            return;
        }
        if (VulkanSceneReusePolicy.canReuseGeometryBuffers(gpuMeshes, safe)) {
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
            VulkanFrameSyncResources.destroy(
                    device,
                    new VulkanFrameSyncResources.Allocation(
                            commandPool,
                            commandBuffers,
                            imageAvailableSemaphores,
                            renderFinishedSemaphores,
                            renderFences
                    )
            );
        }
        renderFences = new long[0];
        renderFinishedSemaphores = new long[0];
        imageAvailableSemaphores = new long[0];

        commandBuffers = new VkCommandBuffer[0];
        destroySceneMeshes();
        destroyShadowResources();
        commandPool = VK_NULL_HANDLE;

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
        VulkanDeviceSelector.Selection selection = VulkanDeviceSelector.select(instance, surface, stack);
        physicalDevice = selection.physicalDevice();
        graphicsQueueFamilyIndex = selection.graphicsQueueFamilyIndex();
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
        VulkanDescriptorResources.Allocation descriptorResources = VulkanDescriptorResources.create(
                device,
                physicalDevice,
                stack,
                framesInFlight,
                maxDynamicSceneObjects,
                OBJECT_UNIFORM_BYTES,
                GLOBAL_SCENE_UNIFORM_BYTES
        );
        descriptorSetLayout = descriptorResources.descriptorSetLayout();
        textureDescriptorSetLayout = descriptorResources.textureDescriptorSetLayout();
        descriptorPool = descriptorResources.descriptorPool();
        frameDescriptorSets = descriptorResources.frameDescriptorSets();
        descriptorSet = frameDescriptorSets[0];
        objectUniformBuffer = descriptorResources.objectUniformBuffer();
        objectUniformMemory = descriptorResources.objectUniformMemory();
        objectUniformStagingBuffer = descriptorResources.objectUniformStagingBuffer();
        objectUniformStagingMemory = descriptorResources.objectUniformStagingMemory();
        objectUniformStagingMappedAddress = descriptorResources.objectUniformStagingMappedAddress();
        sceneGlobalUniformBuffer = descriptorResources.sceneGlobalUniformBuffer();
        sceneGlobalUniformMemory = descriptorResources.sceneGlobalUniformMemory();
        sceneGlobalUniformStagingBuffer = descriptorResources.sceneGlobalUniformStagingBuffer();
        sceneGlobalUniformStagingMemory = descriptorResources.sceneGlobalUniformStagingMemory();
        sceneGlobalUniformStagingMappedAddress = descriptorResources.sceneGlobalUniformStagingMappedAddress();
        uniformStrideBytes = descriptorResources.uniformStrideBytes();
        uniformFrameSpanBytes = descriptorResources.uniformFrameSpanBytes();
        globalUniformFrameSpanBytes = descriptorResources.globalUniformFrameSpanBytes();
        estimatedGpuMemoryBytes = descriptorResources.estimatedGpuMemoryBytes();
    }

    private void destroyDescriptorResources() {
        if (device == null) {
            return;
        }
        VulkanDescriptorResources.destroy(
                device,
                new VulkanDescriptorResources.Allocation(
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        descriptorPool,
                        frameDescriptorSets,
                        objectUniformBuffer,
                        objectUniformMemory,
                        objectUniformStagingBuffer,
                        objectUniformStagingMemory,
                        objectUniformStagingMappedAddress,
                        sceneGlobalUniformBuffer,
                        sceneGlobalUniformMemory,
                        sceneGlobalUniformStagingBuffer,
                        sceneGlobalUniformStagingMemory,
                        sceneGlobalUniformStagingMappedAddress,
                        uniformStrideBytes,
                        uniformFrameSpanBytes,
                        globalUniformFrameSpanBytes,
                        estimatedGpuMemoryBytes
                )
        );
        objectUniformBuffer = VK_NULL_HANDLE;
        objectUniformMemory = VK_NULL_HANDLE;
        objectUniformStagingBuffer = VK_NULL_HANDLE;
        objectUniformStagingMemory = VK_NULL_HANDLE;
        objectUniformStagingMappedAddress = 0L;
        sceneGlobalUniformBuffer = VK_NULL_HANDLE;
        sceneGlobalUniformMemory = VK_NULL_HANDLE;
        sceneGlobalUniformStagingBuffer = VK_NULL_HANDLE;
        sceneGlobalUniformStagingMemory = VK_NULL_HANDLE;
        sceneGlobalUniformStagingMappedAddress = 0L;
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
        descriptorPool = VK_NULL_HANDLE;
        descriptorSetLayout = VK_NULL_HANDLE;
        textureDescriptorSetLayout = VK_NULL_HANDLE;
    }

    private void createSwapchainResources(MemoryStack stack, int requestedWidth, int requestedHeight) throws EngineException {
        VulkanSwapchainAllocation.Allocation swapchainAllocation = VulkanSwapchainAllocation.create(
                physicalDevice,
                device,
                stack,
                surface,
                requestedWidth,
                requestedHeight
        );
        swapchain = swapchainAllocation.swapchain();
        swapchainImageFormat = swapchainAllocation.swapchainImageFormat();
        swapchainWidth = swapchainAllocation.swapchainWidth();
        swapchainHeight = swapchainAllocation.swapchainHeight();
        swapchainImages = swapchainAllocation.swapchainImages();

        createImageViews(stack);
        createDepthResources(stack);
        VulkanMainPipelineBuilder.Result mainPipeline = VulkanMainPipelineBuilder.create(
                device,
                stack,
                swapchainImageFormat,
                depthFormat,
                swapchainWidth,
                swapchainHeight,
                VERTEX_STRIDE_BYTES,
                descriptorSetLayout,
                textureDescriptorSetLayout
        );
        renderPass = mainPipeline.renderPass();
        pipelineLayout = mainPipeline.pipelineLayout();
        graphicsPipeline = mainPipeline.graphicsPipeline();
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
        swapchainImageViews = VulkanSwapchainImageViews.create(device, stack, swapchainImages, swapchainImageFormat);
    }

    private void createDepthResources(MemoryStack stack) throws EngineException {
        VulkanFramebufferResources.DepthResources depthResources = VulkanFramebufferResources.createDepthResources(
                device,
                physicalDevice,
                stack,
                swapchainImages.length,
                swapchainWidth,
                swapchainHeight,
                depthFormat
        );
        depthImages = depthResources.depthImages();
        depthMemories = depthResources.depthMemories();
        depthImageViews = depthResources.depthImageViews();
    }

    private void createShadowResources(MemoryStack stack) throws EngineException {
        VulkanShadowResources.Allocation shadowResources = VulkanShadowResources.create(
                device,
                physicalDevice,
                stack,
                depthFormat,
                shadowMapResolution,
                MAX_SHADOW_MATRICES,
                VERTEX_STRIDE_BYTES,
                descriptorSetLayout
        );
        shadowDepthImage = shadowResources.shadowDepthImage();
        shadowDepthMemory = shadowResources.shadowDepthMemory();
        shadowDepthImageView = shadowResources.shadowDepthImageView();
        shadowDepthLayerImageViews = shadowResources.shadowDepthLayerImageViews();
        shadowSampler = shadowResources.shadowSampler();
        shadowRenderPass = shadowResources.shadowRenderPass();
        shadowPipelineLayout = shadowResources.shadowPipelineLayout();
        shadowPipeline = shadowResources.shadowPipeline();
        shadowFramebuffers = shadowResources.shadowFramebuffers();
    }

    private void createFramebuffers(MemoryStack stack) throws EngineException {
        framebuffers = VulkanFramebufferResources.createMainFramebuffers(
                device,
                stack,
                renderPass,
                swapchainImageViews,
                depthImageViews,
                swapchainWidth,
                swapchainHeight
        );
    }

    private void createPostProcessResources(MemoryStack stack) throws EngineException {
        postIntermediateInitialized = false;
        VulkanPostProcessResources.Allocation postResources = VulkanPostProcessResources.create(
                device,
                physicalDevice,
                stack,
                swapchainImageFormat,
                swapchainWidth,
                swapchainHeight,
                swapchainImageViews
        );
        offscreenColorImage = postResources.offscreenColorImage();
        offscreenColorMemory = postResources.offscreenColorMemory();
        offscreenColorImageView = postResources.offscreenColorImageView();
        offscreenColorSampler = postResources.offscreenColorSampler();
        postDescriptorSetLayout = postResources.postDescriptorSetLayout();
        postDescriptorPool = postResources.postDescriptorPool();
        postDescriptorSet = postResources.postDescriptorSet();
        postRenderPass = postResources.postRenderPass();
        postPipelineLayout = postResources.postPipelineLayout();
        postGraphicsPipeline = postResources.postGraphicsPipeline();
        postFramebuffers = postResources.postFramebuffers();
    }

    private void destroyShadowResources() {
        VulkanShadowResources.destroy(
                device,
                new VulkanShadowResources.Allocation(
                        shadowDepthImage,
                        shadowDepthMemory,
                        shadowDepthImageView,
                        shadowDepthLayerImageViews,
                        shadowSampler,
                        shadowRenderPass,
                        shadowPipelineLayout,
                        shadowPipeline,
                        shadowFramebuffers
                )
        );
        shadowFramebuffers = new long[0];
        shadowPipeline = VK_NULL_HANDLE;
        shadowPipelineLayout = VK_NULL_HANDLE;
        shadowRenderPass = VK_NULL_HANDLE;
        shadowSampler = VK_NULL_HANDLE;
        shadowDepthImageView = VK_NULL_HANDLE;
        shadowDepthLayerImageViews = new long[0];
        shadowDepthImage = VK_NULL_HANDLE;
        shadowDepthMemory = VK_NULL_HANDLE;
    }

    private void destroySwapchainResources() {
        if (device == null) {
            return;
        }
        destroyPostProcessResources();
        VulkanFramebufferResources.destroyFramebuffers(device, framebuffers);
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
        VulkanSwapchainImageViews.destroy(device, swapchainImageViews);
        VulkanFramebufferResources.destroyDepthResources(
                device,
                new VulkanFramebufferResources.DepthResources(depthImages, depthMemories, depthImageViews)
        );
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
        VulkanPostProcessResources.destroy(
                device,
                new VulkanPostProcessResources.Allocation(
                        offscreenColorImage,
                        offscreenColorMemory,
                        offscreenColorImageView,
                        offscreenColorSampler,
                        postDescriptorSetLayout,
                        postDescriptorPool,
                        postDescriptorSet,
                        postRenderPass,
                        postPipelineLayout,
                        postGraphicsPipeline,
                        postFramebuffers
                )
        );
        postFramebuffers = new long[0];
        postGraphicsPipeline = VK_NULL_HANDLE;
        postPipelineLayout = VK_NULL_HANDLE;
        postRenderPass = VK_NULL_HANDLE;
        postDescriptorPool = VK_NULL_HANDLE;
        postDescriptorSetLayout = VK_NULL_HANDLE;
        postDescriptorSet = VK_NULL_HANDLE;
        offscreenColorSampler = VK_NULL_HANDLE;
        offscreenColorImageView = VK_NULL_HANDLE;
        offscreenColorImage = VK_NULL_HANDLE;
        offscreenColorMemory = VK_NULL_HANDLE;
        postIntermediateInitialized = false;
        postOffscreenActive = false;
    }

    private void createFrameSyncResources(MemoryStack stack) throws EngineException {
        VulkanFrameSyncResources.Allocation frameSyncResources = VulkanFrameSyncResources.create(
                device,
                stack,
                graphicsQueueFamilyIndex,
                framesInFlight
        );
        commandPool = frameSyncResources.commandPool();
        commandBuffers = frameSyncResources.commandBuffers();
        imageAvailableSemaphores = frameSyncResources.imageAvailableSemaphores();
        renderFinishedSemaphores = frameSyncResources.renderFinishedSemaphores();
        renderFences = frameSyncResources.renderFences();
        currentFrame = 0;
    }

    private int acquireNextImage(MemoryStack stack, int frameIdx) throws EngineException {
        VkCommandBuffer commandBuffer = commandBuffers[frameIdx];
        long imageAvailableSemaphore = imageAvailableSemaphores[frameIdx];
        long renderFinishedSemaphore = renderFinishedSemaphores[frameIdx];
        long renderFence = renderFences[frameIdx];
        return VulkanCommandSubmitter.acquireRecordSubmitPresent(
                stack,
                device,
                graphicsQueue,
                swapchain,
                commandBuffer,
                imageAvailableSemaphore,
                renderFinishedSemaphore,
                renderFence,
                imageIndex -> recordCommandBuffer(stack, commandBuffer, imageIndex, frameIdx)
        );
    }

    private void recordCommandBuffer(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIndex, int frameIdx) throws EngineException {
        VulkanFrameCommandOrchestrator.record(
                stack,
                commandBuffer,
                imageIndex,
                frameIdx,
                new VulkanFrameCommandOrchestrator.FrameHooks(
                        this::updateShadowLightViewProjMatrices,
                        () -> prepareFrameUniforms(frameIdx),
                        () -> uploadFrameUniforms(commandBuffer, frameIdx),
                        value -> postIntermediateInitialized = value
                ),
                new VulkanFrameCommandOrchestrator.Inputs(
                        gpuMeshes,
                        maxDynamicSceneObjects,
                        swapchainWidth,
                        swapchainHeight,
                        shadowMapResolution,
                        shadowEnabled,
                        pointShadowEnabled,
                        shadowCascadeCount,
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES,
                        renderPass,
                        framebuffers,
                        graphicsPipeline,
                        pipelineLayout,
                        shadowRenderPass,
                        shadowPipeline,
                        shadowPipelineLayout,
                        shadowFramebuffers,
                        postOffscreenActive,
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
                        swapchainImages,
                        postFramebuffers,
                        this::descriptorSetForFrame,
                        meshIndex -> dynamicUniformOffset(frameIdx, meshIndex),
                        this::vkFailure
                )
        );
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

    private void updateDynamicSceneState(List<VulkanSceneMeshData> sceneMeshes) {
        VulkanDynamicSceneUpdater.Result result = VulkanDynamicSceneUpdater.update(gpuMeshes, sceneMeshes);
        if (result.reordered()) {
            sceneReorderReuseCount++;
        }
        if (result.dirtyEnd() >= result.dirtyStart()) {
            markSceneStateDirty(result.dirtyStart(), result.dirtyEnd());
        }
    }

    private void uploadSceneMeshes(MemoryStack stack, List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        meshBufferRebuildCount++;
        destroySceneMeshes();
        VulkanSceneMeshLifecycle.UploadResult result = VulkanSceneMeshLifecycle.uploadMeshes(
                device,
                physicalDevice,
                commandPool,
                graphicsQueue,
                stack,
                sceneMeshes,
                gpuMeshes,
                iblIrradiancePath,
                iblRadiancePath,
                iblBrdfLutPath,
                uniformFrameSpanBytes,
                framesInFlight,
                this::createTextureFromPath,
                this::resolveOrCreateTexture,
                this::textureCacheKey,
                this::createTextureDescriptorSets,
                this::vkFailure
        );
        iblIrradianceTexture = result.iblIrradianceTexture();
        iblRadianceTexture = result.iblRadianceTexture();
        iblBrdfLutTexture = result.iblBrdfLutTexture();
        estimatedGpuMemoryBytes = result.estimatedGpuMemoryBytes();
    }

    private void rebindSceneTexturesAndDynamicState(List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        VulkanSceneMeshLifecycle.RebindResult result = VulkanSceneMeshLifecycle.rebindSceneTextures(
                sceneMeshes,
                gpuMeshes,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture,
                iblIrradiancePath,
                iblRadiancePath,
                iblBrdfLutPath,
                this::createTextureFromPath,
                this::resolveOrCreateTexture,
                this::textureCacheKey
        );
        iblIrradianceTexture = result.iblIrradianceTexture();
        iblRadianceTexture = result.iblRadianceTexture();
        iblBrdfLutTexture = result.iblBrdfLutTexture();
        try (MemoryStack stack = stackPush()) {
            createTextureDescriptorSets(stack);
        }
        destroyTextures(result.staleTextures());
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
        return VulkanTextureResourceOps.collectLiveTextures(meshes, iblIrr, iblRad, iblBrdf);
    }

    private void destroyTextures(Set<VulkanGpuTexture> textures) {
        VulkanTextureResourceOps.destroyTextures(device, textures);
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
        VulkanTextureDescriptorPoolManager.State state = VulkanTextureDescriptorPoolManager.createOrReuseAndWrite(
                device,
                stack,
                gpuMeshes,
                textureDescriptorSetLayout,
                textureDescriptorPool,
                descriptorRingSetCapacity,
                descriptorRingPeakSetCapacity,
                descriptorRingPeakWasteSetCount,
                descriptorPoolBuildCount,
                descriptorPoolRebuildCount,
                descriptorRingGrowthRebuildCount,
                descriptorRingSteadyRebuildCount,
                descriptorRingPoolReuseCount,
                descriptorRingPoolResetFailureCount,
                targetSetCapacity,
                shadowDepthImageView,
                shadowSampler,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture
        );
        textureDescriptorPool = state.textureDescriptorPool();
        descriptorPoolBuildCount = state.descriptorPoolBuildCount();
        descriptorPoolRebuildCount = state.descriptorPoolRebuildCount();
        descriptorRingGrowthRebuildCount = state.descriptorRingGrowthRebuildCount();
        descriptorRingSteadyRebuildCount = state.descriptorRingSteadyRebuildCount();
        descriptorRingPoolReuseCount = state.descriptorRingPoolReuseCount();
        descriptorRingPoolResetFailureCount = state.descriptorRingPoolResetFailureCount();
        descriptorRingSetCapacity = state.descriptorRingSetCapacity();
        descriptorRingPeakSetCapacity = state.descriptorRingPeakSetCapacity();
        descriptorRingActiveSetCount = state.descriptorRingActiveSetCount();
        descriptorRingWasteSetCount = state.descriptorRingWasteSetCount();
        descriptorRingPeakWasteSetCount = state.descriptorRingPeakWasteSetCount();
    }

    private VulkanGpuTexture createTextureFromPath(Path texturePath, boolean normalMap) throws EngineException {
        return VulkanTextureResourceOps.createTextureFromPath(
                texturePath,
                normalMap,
                new VulkanTextureResourceOps.Context(device, physicalDevice, commandPool, graphicsQueue, this::vkFailure)
        );
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

    private void prepareFrameUniforms(int frameIdx) throws EngineException {
        VulkanFrameUniformCoordinator.Result result = VulkanFrameUniformCoordinator.prepare(
                new VulkanFrameUniformCoordinator.Inputs(
                        frameIdx,
                        gpuMeshes.size(),
                        maxObservedDynamicObjects,
                        maxDynamicSceneObjects,
                        framesInFlight,
                        uniformFrameSpanBytes,
                        globalUniformFrameSpanBytes,
                        uniformStrideBytes,
                        OBJECT_UNIFORM_BYTES,
                        GLOBAL_SCENE_UNIFORM_BYTES,
                        device,
                        objectUniformStagingMemory,
                        sceneGlobalUniformStagingMemory,
                        objectUniformStagingMappedAddress,
                        sceneGlobalUniformStagingMappedAddress,
                        globalStateRevision,
                        sceneStateRevision,
                        frameGlobalRevisionApplied,
                        frameSceneRevisionApplied,
                        pendingSceneDirtyRangeCount,
                        pendingSceneDirtyStarts,
                        pendingSceneDirtyEnds,
                        pendingUploadSrcOffsets,
                        pendingUploadDstOffsets,
                        pendingUploadByteCounts,
                        idx -> gpuMeshes.isEmpty() ? null : gpuMeshes.get(idx),
                        globalSceneUniformInput(),
                        this::vkFailure
                )
        );
        maxObservedDynamicObjects = result.maxObservedDynamicObjects();
        if (result.clearPendingOnly()) {
            clearPendingUploads();
            return;
        }
        pendingGlobalUploadSrcOffset = result.pendingGlobalUploadSrcOffset();
        pendingGlobalUploadDstOffset = result.pendingGlobalUploadDstOffset();
        pendingGlobalUploadByteCount = result.pendingGlobalUploadByteCount();
        pendingUploadRangeCount = result.pendingUploadRangeCount();
        pendingUploadObjectCount = result.pendingUploadObjectCount();
        pendingUploadStartObject = result.pendingUploadStartObject();
        pendingUploadByteCount = result.pendingUploadByteCount();
        pendingUploadSrcOffset = result.pendingUploadSrcOffset();
        pendingUploadDstOffset = result.pendingUploadDstOffset();
        pendingSceneDirtyRangeCount = result.pendingSceneDirtyRangeCount();
    }

    private void uploadFrameUniforms(VkCommandBuffer commandBuffer, int frameIdx) {
        VulkanUniformUploadRecorder.UploadStats stats = VulkanUniformUploadRecorder.recordUploads(
                commandBuffer,
                new VulkanUniformUploadRecorder.UploadInputs(
                        sceneGlobalUniformStagingBuffer,
                        sceneGlobalUniformBuffer,
                        objectUniformStagingBuffer,
                        objectUniformBuffer,
                        pendingGlobalUploadSrcOffset,
                        pendingGlobalUploadDstOffset,
                        pendingGlobalUploadByteCount,
                        pendingUploadObjectCount,
                        pendingUploadStartObject,
                        pendingUploadSrcOffsets,
                        pendingUploadDstOffsets,
                        pendingUploadByteCounts,
                        pendingUploadRangeCount
                )
        );
        lastFrameGlobalUploadBytes = stats.globalUploadBytes();
        maxFrameGlobalUploadBytes = Math.max(maxFrameGlobalUploadBytes, stats.globalUploadBytes());
        lastFrameUniformUploadBytes = stats.uniformUploadBytes();
        maxFrameUniformUploadBytes = Math.max(maxFrameUniformUploadBytes, stats.uniformUploadBytes());
        lastFrameUniformObjectCount = stats.uniformObjectCount();
        maxFrameUniformObjectCount = Math.max(maxFrameUniformObjectCount, stats.uniformObjectCount());
        lastFrameUniformUploadRanges = stats.uniformUploadRanges();
        maxFrameUniformUploadRanges = Math.max(maxFrameUniformUploadRanges, stats.uniformUploadRanges());
        lastFrameUniformUploadStartObject = stats.uniformUploadStartObject();
        clearPendingUploads();
    }

    private VulkanUniformWriters.GlobalSceneUniformInput globalSceneUniformInput() {
        return new VulkanUniformWriters.GlobalSceneUniformInput(
                GLOBAL_SCENE_UNIFORM_BYTES,
                viewMatrix,
                projMatrix,
                dirLightDirX,
                dirLightDirY,
                dirLightDirZ,
                dirLightColorR,
                dirLightColorG,
                dirLightColorB,
                pointLightPosX,
                pointLightPosY,
                pointLightPosZ,
                pointShadowFarPlane,
                pointLightColorR,
                pointLightColorG,
                pointLightColorB,
                pointLightDirX,
                pointLightDirY,
                pointLightDirZ,
                pointLightInnerCos,
                pointLightOuterCos,
                pointLightIsSpot,
                pointShadowEnabled,
                dirLightIntensity,
                pointLightIntensity,
                shadowEnabled,
                shadowStrength,
                shadowBias,
                shadowPcfRadius,
                shadowCascadeCount,
                shadowMapResolution,
                shadowCascadeSplitNdc,
                fogEnabled,
                fogDensity,
                fogR,
                fogG,
                fogB,
                fogSteps,
                smokeEnabled,
                smokeIntensity,
                swapchainWidth,
                swapchainHeight,
                smokeR,
                smokeG,
                smokeB,
                iblEnabled,
                iblDiffuseStrength,
                iblSpecularStrength,
                iblPrefilterStrength,
                postOffscreenActive,
                tonemapEnabled,
                tonemapExposure,
                tonemapGamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength,
                shadowLightViewProjMatrices
        );
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

}
