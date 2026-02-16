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
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
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
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_info;
import static org.lwjgl.stb.STBImage.stbi_is_hdr;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_loadf;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
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
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
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
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;
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
import org.lwjgl.vulkan.VkPresentInfoKHR;
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
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkViewport;

final class VulkanContext {
    private static final int VERTEX_STRIDE_FLOATS = 11;
    private static final int VERTEX_STRIDE_BYTES = VERTEX_STRIDE_FLOATS * Float.BYTES;
    private static final int DEFAULT_FRAMES_IN_FLIGHT = 3;
    private static final int DEFAULT_MAX_DYNAMIC_SCENE_OBJECTS = 2048;
    private static final int DEFAULT_MAX_PENDING_UPLOAD_RANGES = 64;
    private static final int MAX_SHADOW_CASCADES = 4;
    private static final int POINT_SHADOW_FACES = 6;
    private static final int MAX_SHADOW_MATRICES = 6;
    private static final int GLOBAL_UNIFORM_BYTES = 864;
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
    private long globalUniformBuffer = VK_NULL_HANDLE;
    private long globalUniformMemory = VK_NULL_HANDLE;
    private long globalUniformStagingBuffer = VK_NULL_HANDLE;
    private long globalUniformStagingMemory = VK_NULL_HANDLE;
    private long globalUniformStagingMappedAddress;
    private int uniformStrideBytes = GLOBAL_UNIFORM_BYTES;
    private int uniformFrameSpanBytes = GLOBAL_UNIFORM_BYTES;
    private int framesInFlight = DEFAULT_FRAMES_IN_FLIGHT;
    private int maxDynamicSceneObjects = DEFAULT_MAX_DYNAMIC_SCENE_OBJECTS;
    private int maxPendingUploadRanges = DEFAULT_MAX_PENDING_UPLOAD_RANGES;
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
    private long sceneFullRebuildCount;
    private long meshBufferRebuildCount;
    private long descriptorPoolBuildCount;
    private long descriptorPoolRebuildCount;
    private long estimatedGpuMemoryBytes;
    private int lastFrameUniformUploadBytes;
    private int maxFrameUniformUploadBytes;
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
    private final List<GpuMesh> gpuMeshes = new ArrayList<>();
    private List<SceneMeshData> pendingSceneMeshes = List.of(SceneMeshData.defaultTriangle());
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
    private GpuTexture iblIrradianceTexture;
    private GpuTexture iblRadianceTexture;
    private GpuTexture iblBrdfLutTexture;
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
        this.framesInFlight = clamp(framesInFlight, 2, 4);
        this.maxDynamicSceneObjects = clamp(maxDynamicSceneObjects, 256, 8192);
        this.maxPendingUploadRanges = clamp(maxPendingUploadRanges, 8, 512);
        reallocateFrameTracking();
        reallocateUploadRangeTracking();
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
                maxDynamicSceneObjects,
                maxPendingUploadRanges,
                lastFrameUniformUploadBytes,
                maxFrameUniformUploadBytes,
                lastFrameUniformObjectCount,
                maxFrameUniformObjectCount,
                lastFrameUniformUploadRanges,
                maxFrameUniformUploadRanges,
                lastFrameUniformUploadStartObject,
                pendingUploadRangeOverflowCount,
                globalUniformStagingMappedAddress != 0L
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

    void setSceneMeshes(List<SceneMeshData> sceneMeshes) throws EngineException {
        List<SceneMeshData> safe = (sceneMeshes == null || sceneMeshes.isEmpty())
                ? List.of(SceneMeshData.defaultTriangle())
                : List.copyOf(sceneMeshes);
        pendingSceneMeshes = safe;
        if (device == null) {
            return;
        }
        if (canReuseGpuMeshes(safe)) {
            sceneReuseHitCount++;
            updateDynamicSceneState(safe);
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
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
        bindings.get(0)
                .binding(0)
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
        uniformStrideBytes = alignUp(GLOBAL_UNIFORM_BYTES, (int) Math.min(Integer.MAX_VALUE, minAlign));
        uniformFrameSpanBytes = uniformStrideBytes * maxDynamicSceneObjects;
        int totalUniformBytes = uniformFrameSpanBytes * framesInFlight;

        BufferAlloc uniformDeviceAlloc = createBuffer(
                stack,
                totalUniformBytes,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        globalUniformBuffer = uniformDeviceAlloc.buffer;
        globalUniformMemory = uniformDeviceAlloc.memory;

        BufferAlloc uniformStagingAlloc = createBuffer(
                stack,
                totalUniformBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        globalUniformStagingBuffer = uniformStagingAlloc.buffer;
        globalUniformStagingMemory = uniformStagingAlloc.memory;
        PointerBuffer pMapped = stack.mallocPointer(1);
        int mapStagingResult = vkMapMemory(device, globalUniformStagingMemory, 0, totalUniformBytes, 0, pMapped);
        if (mapStagingResult != VK_SUCCESS || pMapped.get(0) == 0L) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkMapMemory(stagingPersistent) failed: " + mapStagingResult,
                    false
            );
        }
        globalUniformStagingMappedAddress = pMapped.get(0);
        estimatedGpuMemoryBytes = (long) totalUniformBytes * 2L;

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
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

        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(framesInFlight, stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(framesInFlight, stack);
        for (int i = 0; i < framesInFlight; i++) {
            long frameBase = (long) i * uniformFrameSpanBytes;
            bufferInfos.get(i)
                    .buffer(globalUniformBuffer)
                    .offset(frameBase)
                    .range(GLOBAL_UNIFORM_BYTES);
            writes.get(i)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(frameDescriptorSets[i])
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, bufferInfos.get(i)));
        }
        vkUpdateDescriptorSets(device, writes, null);
    }

    private void destroyDescriptorResources() {
        if (device == null) {
            return;
        }
        if (globalUniformBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, globalUniformBuffer, null);
            globalUniformBuffer = VK_NULL_HANDLE;
        }
        if (globalUniformMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, globalUniformMemory, null);
            globalUniformMemory = VK_NULL_HANDLE;
        }
        if (globalUniformStagingBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, globalUniformStagingBuffer, null);
            globalUniformStagingBuffer = VK_NULL_HANDLE;
        }
        if (globalUniformStagingMappedAddress != 0L && globalUniformStagingMemory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, globalUniformStagingMemory);
            globalUniformStagingMappedAddress = 0L;
        }
        if (globalUniformStagingMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, globalUniformStagingMemory, null);
            globalUniformStagingMemory = VK_NULL_HANDLE;
        }
        uniformStrideBytes = GLOBAL_UNIFORM_BYTES;
        uniformFrameSpanBytes = GLOBAL_UNIFORM_BYTES;
        estimatedGpuMemoryBytes = 0;
        lastFrameUniformUploadBytes = 0;
        maxFrameUniformUploadBytes = 0;
        lastFrameUniformObjectCount = 0;
        maxFrameUniformObjectCount = 0;
        lastFrameUniformUploadRanges = 0;
        maxFrameUniformUploadRanges = 0;
        lastFrameUniformUploadStartObject = 0;
        pendingUploadSrcOffset = -1L;
        pendingUploadDstOffset = -1L;
        pendingUploadByteCount = 0;
        pendingUploadObjectCount = 0;
        pendingUploadStartObject = 0;
        pendingSceneDirtyRangeCount = 0;
        globalStateRevision = 1;
        sceneStateRevision = 1;
        Arrays.fill(frameGlobalRevisionApplied, 0L);
        Arrays.fill(frameSceneRevisionApplied, 0L);
        frameDescriptorSets = new long[0];
        descriptorSet = VK_NULL_HANDLE;
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
        VkSurfaceFormatKHR chosenFormat = chooseSurfaceFormat(formats);

        var presentModeCount = stack.ints(0);
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null);
        var presentModes = stack.mallocInt(Math.max(1, presentModeCount.get(0)));
        if (presentModeCount.get(0) > 0) {
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes);
        }
        int presentMode = choosePresentMode(presentModes, presentModeCount.get(0));

        VkExtent2D extent = chooseExtent(capabilities, requestedWidth, requestedHeight, stack);
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
            ImageAlloc depth = createImage(
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
        ImageAlloc shadowDepth = createImage(
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
        String shadowVertSource = """
                #version 450
                layout(location = 0) in vec3 inPos;
                layout(set = 0, binding = 0) uniform SceneData {
                    mat4 uModel;
                    mat4 uView;
                    mat4 uProj;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uDirLightDir;
                    vec4 uDirLightColor;
                    vec4 uPointLightPos;
                    vec4 uPointLightColor;
                    vec4 uPointLightDir;
                    vec4 uPointLightCone;
                    vec4 uShadow;
                    vec4 uShadowCascade;
                    vec4 uShadowCascadeExt;
                    vec4 uFog;
                    vec4 uFogColorSteps;
                    vec4 uSmoke;
                    vec4 uSmokeColor;
                    vec4 uIbl;
                    vec4 uPostProcess;
                    vec4 uBloom;
                    mat4 uShadowLightViewProj[6];
                } ubo;
                layout(push_constant) uniform ShadowPush {
                    int uCascadeIndex;
                } pc;
                void main() {
                    int cascadeIndex = clamp(pc.uCascadeIndex, 0, 5);
                    gl_Position = ubo.uShadowLightViewProj[cascadeIndex] * ubo.uModel * vec4(inPos, 1.0);
                }
                """;
        String shadowFragSource = """
                #version 450
                void main() { }
                """;

        ByteBuffer vertSpv = compileGlslToSpv(shadowVertSource, shaderc_glsl_vertex_shader, "shadow.vert");
        ByteBuffer fragSpv = compileGlslToSpv(shadowFragSource, shaderc_fragment_shader, "shadow.frag");
        long vertModule = VK_NULL_HANDLE;
        long fragModule = VK_NULL_HANDLE;
        try {
            vertModule = createShaderModule(stack, vertSpv);
            fragModule = createShaderModule(stack, fragSpv);
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
        String vertexShaderSource = """
                #version 450
                layout(location = 0) in vec3 inPos;
                layout(location = 1) in vec3 inNormal;
                layout(location = 2) in vec2 inUv;
                layout(location = 3) in vec3 inTangent;
                layout(location = 0) out vec3 vWorldPos;
                layout(location = 1) out vec3 vNormal;
                layout(location = 2) out float vHeight;
                layout(location = 3) out vec2 vUv;
                layout(location = 4) out vec3 vTangent;
                layout(set = 0, binding = 0) uniform SceneData {
                    mat4 uModel;
                    mat4 uView;
                    mat4 uProj;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uDirLightDir;
                    vec4 uDirLightColor;
                    vec4 uPointLightPos;
                    vec4 uPointLightColor;
                    vec4 uPointLightDir;
                    vec4 uPointLightCone;
                    vec4 uShadow;
                    vec4 uShadowCascade;
                    vec4 uShadowCascadeExt;
                    vec4 uFog;
                    vec4 uFogColorSteps;
                    vec4 uSmoke;
                    vec4 uSmokeColor;
                    vec4 uIbl;
                    vec4 uPostProcess;
                    vec4 uBloom;
                    mat4 uShadowLightViewProj[6];
                } ubo;
                void main() {
                    vec4 world = ubo.uModel * vec4(inPos, 1.0);
                    vWorldPos = world.xyz;
                    vHeight = world.y;
                    vec3 tangent = normalize(mat3(ubo.uModel) * inTangent);
                    vec3 normal = normalize(mat3(ubo.uModel) * inNormal);
                    vNormal = normal;
                    vTangent = tangent;
                    vUv = inUv;
                    gl_Position = ubo.uProj * ubo.uView * world;
                }
                """;
        String fragmentShaderSource = """
                #version 450
                layout(location = 0) in vec3 vWorldPos;
                layout(location = 1) in vec3 vNormal;
                layout(location = 2) in float vHeight;
                layout(location = 3) in vec2 vUv;
                layout(location = 4) in vec3 vTangent;
                layout(set = 0, binding = 0) uniform SceneData {
                    mat4 uModel;
                    mat4 uView;
                    mat4 uProj;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uDirLightDir;
                    vec4 uDirLightColor;
                    vec4 uPointLightPos;
                    vec4 uPointLightColor;
                    vec4 uPointLightDir;
                    vec4 uPointLightCone;
                    vec4 uShadow;
                    vec4 uShadowCascade;
                    vec4 uShadowCascadeExt;
                    vec4 uFog;
                    vec4 uFogColorSteps;
                    vec4 uSmoke;
                    vec4 uSmokeColor;
                    vec4 uIbl;
                    vec4 uPostProcess;
                    vec4 uBloom;
                    mat4 uShadowLightViewProj[6];
                } ubo;
                layout(set = 1, binding = 0) uniform sampler2D uAlbedoTexture;
                layout(set = 1, binding = 1) uniform sampler2D uNormalTexture;
                layout(set = 1, binding = 2) uniform sampler2D uMetallicRoughnessTexture;
                layout(set = 1, binding = 3) uniform sampler2D uOcclusionTexture;
                layout(set = 1, binding = 4) uniform sampler2DArrayShadow uShadowMap;
                layout(set = 1, binding = 5) uniform sampler2D uIblIrradianceTexture;
                layout(set = 1, binding = 6) uniform sampler2D uIblRadianceTexture;
                layout(set = 1, binding = 7) uniform sampler2D uIblBrdfLutTexture;
                layout(location = 0) out vec4 outColor;
                float distributionGGX(float ndh, float roughness) {
                    float a = roughness * roughness;
                    float a2 = a * a;
                    float d = (ndh * ndh) * (a2 - 1.0) + 1.0;
                    return a2 / max(3.14159 * d * d, 0.0001);
                }
                float geometrySchlickGGX(float ndv, float roughness) {
                    float r = roughness + 1.0;
                    float k = (r * r) / 8.0;
                    return ndv / max(ndv * (1.0 - k) + k, 0.0001);
                }
                float geometrySmith(float ndv, float ndl, float roughness) {
                    return geometrySchlickGGX(ndv, roughness) * geometrySchlickGGX(ndl, roughness);
                }
                vec3 fresnelSchlick(float cosTheta, vec3 f0) {
                    return f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);
                }
                vec3 sampleIblRadiance(vec2 specUv, vec2 baseUv, float roughness, float prefilter) {
                    float roughMix = clamp(roughness * (0.45 + 0.55 * prefilter), 0.0, 1.0);
                    vec2 roughUv = mix(specUv, baseUv, roughMix);
                    vec2 texel = 1.0 / vec2(textureSize(uIblRadianceTexture, 0));
                    vec2 axis = normalize(vec2(0.37, 0.93) + vec2(roughMix, 1.0 - roughMix) * 0.45);
                    vec2 side = vec2(-axis.y, axis.x);
                    float spread = mix(0.75, 7.5, roughMix);
                    vec3 c0 = texture(uIblRadianceTexture, roughUv).rgb;
                    vec3 c1 = texture(uIblRadianceTexture, clamp(roughUv + axis * texel * spread, vec2(0.0), vec2(1.0))).rgb;
                    vec3 c2 = texture(uIblRadianceTexture, clamp(roughUv - axis * texel * spread, vec2(0.0), vec2(1.0))).rgb;
                    vec3 c3 = texture(uIblRadianceTexture, clamp(roughUv + side * texel * spread * 0.65, vec2(0.0), vec2(1.0))).rgb;
                    vec3 c4 = texture(uIblRadianceTexture, clamp(roughUv - side * texel * spread * 0.65, vec2(0.0), vec2(1.0))).rgb;
                    return (c0 * 0.38) + (c1 * 0.19) + (c2 * 0.19) + (c3 * 0.12) + (c4 * 0.12);
                }
                void main() {
                    vec3 n0 = normalize(vNormal);
                    vec3 t = normalize(vTangent - dot(vTangent, n0) * n0);
                    vec3 b = normalize(cross(n0, t));
                    vec3 normalTex = texture(uNormalTexture, vUv).xyz * 2.0 - 1.0;
                    vec3 n = normalize(mat3(t, b, n0) * normalTex);
                    vec3 sampledAlbedo = texture(uAlbedoTexture, vUv).rgb;
                    vec3 baseColor = ubo.uBaseColor.rgb * sampledAlbedo;
                    vec3 mrTex = texture(uMetallicRoughnessTexture, vUv).rgb;
                    float metallic = clamp(ubo.uMaterial.x * mrTex.b, 0.0, 1.0);
                    float roughness = clamp(ubo.uMaterial.y * max(mrTex.g, 0.04), 0.04, 1.0);
                    float dirIntensity = max(0.0, ubo.uMaterial.z);
                    float pointIntensity = max(0.0, ubo.uMaterial.w);

                    float ao = clamp(texture(uOcclusionTexture, vUv).r, 0.0, 1.0);
                    vec3 lDir = normalize(-ubo.uDirLightDir.xyz);
                    vec3 pDir = normalize(ubo.uPointLightPos.xyz - vWorldPos);
                    float dist = max(length(ubo.uPointLightPos.xyz - vWorldPos), 0.1);
                    float attenuation = 1.0 / (1.0 + 0.35 * dist + 0.1 * dist * dist);
                    float spotAttenuation = 1.0;
                    if (ubo.uPointLightCone.z > 0.5) {
                        vec3 lightToFrag = normalize(vWorldPos - ubo.uPointLightPos.xyz);
                        float cosTheta = dot(normalize(ubo.uPointLightDir.xyz), lightToFrag);
                        float coneRange = max(ubo.uPointLightCone.x - ubo.uPointLightCone.y, 0.0001);
                        spotAttenuation = clamp((cosTheta - ubo.uPointLightCone.y) / coneRange, 0.0, 1.0);
                        spotAttenuation *= spotAttenuation;
                    }
                    vec3 viewPos = (ubo.uView * vec4(vWorldPos, 1.0)).xyz;
                    vec3 viewDir = normalize(-viewPos);

                    float ndl = max(dot(n, lDir), 0.0);
                    float ndv = max(dot(n, viewDir), 0.0);
                    vec3 halfVec = normalize(lDir + viewDir);
                    float ndh = max(dot(n, halfVec), 0.0);
                    float vdh = max(dot(viewDir, halfVec), 0.0);
                    vec3 f0 = mix(vec3(0.04), baseColor, metallic);
                    vec3 f = fresnelSchlick(vdh, f0);
                    float d = distributionGGX(ndh, roughness);
                    float g = geometrySmith(ndv, ndl, roughness);
                    vec3 numerator = d * g * f;
                    float denominator = max(4.0 * ndv * ndl, 0.0001);
                    vec3 specular = numerator / denominator;
                    vec3 kd = (1.0 - f) * (1.0 - metallic);
                    vec3 diffuse = kd * baseColor / 3.14159;
                    vec3 directional = (diffuse + specular) * ubo.uDirLightColor.rgb * (ndl * dirIntensity);

                    float pNdl = max(dot(n, pDir), 0.0);
                    vec3 pointLit = (kd * baseColor / 3.14159)
                            * ubo.uPointLightColor.rgb
                            * (pNdl * attenuation * spotAttenuation * pointIntensity);
                    vec3 ambient = (0.08 + 0.1 * (1.0 - roughness)) * baseColor * ao;

                    vec3 color = ambient + directional + pointLit;
                    if (ubo.uShadow.x > 0.5 && ubo.uPointLightCone.w < 0.5) {
                        int cascadeCount = clamp(int(ubo.uShadowCascade.x + 0.5), 1, 4);
                        int cascadeIndex = 0;
                        float depthNdc = clamp(gl_FragCoord.z, 0.0, 1.0);
                        if (cascadeCount >= 2 && depthNdc > ubo.uShadowCascade.z) {
                            cascadeIndex = 1;
                        }
                        if (cascadeCount >= 3 && depthNdc > ubo.uShadowCascade.w) {
                            cascadeIndex = 2;
                        }
                        if (cascadeCount >= 4 && depthNdc > ubo.uShadowCascadeExt.y) {
                            cascadeIndex = 3;
                        }
                        vec4 shadowPos = ubo.uShadowLightViewProj[cascadeIndex] * vec4(vWorldPos, 1.0);
                        vec3 shadowCoord = shadowPos.xyz / max(shadowPos.w, 0.0001);
                        shadowCoord = shadowCoord * 0.5 + 0.5;
                        float shadowVisibility = 1.0;
                        if (shadowCoord.z > 0.0
                                && shadowCoord.z < 1.0
                                && shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0
                                && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0) {
                            float cascadeT = float(cascadeIndex) / max(float(cascadeCount - 1), 1.0);
                            int radius = clamp(int(ubo.uShadow.w + 0.5) + (cascadeIndex / 2), 0, 4);
                            float texel = (1.0 / max(ubo.uShadowCascade.y, 1.0)) * mix(1.0, 2.25, cascadeT);
                            float compareBias = ubo.uShadow.z * mix(0.7, 1.8, cascadeT);
                            float compareDepth = clamp(shadowCoord.z - compareBias, 0.0, 1.0);
                            float total = 0.0;
                            float taps = 0.0;
                            for (int y = -4; y <= 4; y++) {
                                for (int x = -4; x <= 4; x++) {
                                    if (abs(x) > radius || abs(y) > radius) {
                                        continue;
                                    }
                                    vec2 offset = vec2(float(x), float(y)) * texel;
                                    total += texture(uShadowMap, vec4(shadowCoord.xy + offset, float(cascadeIndex), compareDepth));
                                    taps += 1.0;
                                }
                            }
                            shadowVisibility = (taps > 0.0) ? (total / taps) : 1.0;
                        }
                        float shadowOcclusion = 1.0 - shadowVisibility;
                        float shadowFactor = clamp(shadowOcclusion * clamp(ubo.uShadow.y, 0.0, 1.0), 0.0, 0.9);
                        color *= (1.0 - shadowFactor);
                    }
                    if (ubo.uPointLightCone.w > 0.5) {
                        int pointLayerCount = clamp(int(ubo.uShadowCascade.x + 0.5), 1, 6);
                        vec3 pointVec = normalize(vWorldPos - ubo.uPointLightPos.xyz);
                        int pointLayer = 0;
                        if (pointLayerCount >= 6) {
                            vec3 absVec = abs(pointVec);
                            if (absVec.x >= absVec.y && absVec.x >= absVec.z) {
                                pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                            } else if (absVec.y >= absVec.z) {
                                pointLayer = pointVec.y >= 0.0 ? 2 : 3;
                            } else {
                                pointLayer = pointVec.z >= 0.0 ? 4 : 5;
                            }
                        } else if (pointLayerCount >= 4) {
                            if (abs(pointVec.x) >= abs(pointVec.z)) {
                                pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                            } else {
                                pointLayer = pointVec.z >= 0.0 ? 2 : 3;
                            }
                        } else if (pointLayerCount == 3) {
                            if (abs(pointVec.x) >= abs(pointVec.z)) {
                                pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                            } else {
                                pointLayer = 2;
                            }
                        } else if (pointLayerCount == 2) {
                            pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                        }
                        vec4 pointShadowPos = ubo.uShadowLightViewProj[pointLayer] * vec4(vWorldPos, 1.0);
                        vec3 pointShadowCoord = pointShadowPos.xyz / max(pointShadowPos.w, 0.0001);
                        pointShadowCoord = pointShadowCoord * 0.5 + 0.5;
                        if (pointShadowCoord.z > 0.0
                                && pointShadowCoord.z < 1.0
                                && pointShadowCoord.x >= 0.0 && pointShadowCoord.x <= 1.0
                                && pointShadowCoord.y >= 0.0 && pointShadowCoord.y <= 1.0) {
                            float pointDepthRatio = clamp(dist / max(ubo.uPointLightPos.w, 0.0001), 0.0, 1.0);
                            int pointRadius = clamp(int(ubo.uShadow.w + 0.5), 0, 4);
                            float texel = (1.0 / max(ubo.uShadowCascade.y, 1.0)) * mix(0.85, 2.0, pointDepthRatio);
                            float compareBias = ubo.uShadow.z * mix(0.85, 1.65, pointDepthRatio) * (1.0 + (1.0 - pNdl) * 0.6);
                            float compareDepth = clamp(pointShadowCoord.z - compareBias, 0.0, 1.0);
                            float visibility = 0.0;
                            float taps = 0.0;
                            for (int y = -4; y <= 4; y++) {
                                for (int x = -4; x <= 4; x++) {
                                    if (abs(x) > pointRadius || abs(y) > pointRadius) {
                                        continue;
                                    }
                                    vec2 offset = vec2(float(x), float(y)) * texel;
                                    visibility += texture(uShadowMap, vec4(pointShadowCoord.xy + offset, float(pointLayer), compareDepth));
                                    taps += 1.0;
                                }
                            }
                            float pointOcclusion = 1.0 - ((taps > 0.0) ? (visibility / taps) : 1.0);
                            float pointShadowFactor = clamp(pointOcclusion * min(clamp(ubo.uShadow.y, 0.0, 1.0), 0.85), 0.0, 0.9);
                            color *= (1.0 - pointShadowFactor);
                        }
                    }
                    if (ubo.uFog.x > 0.5) {
                        float normalizedHeight = clamp((vHeight + 1.0) * 0.5, 0.0, 1.0);
                        float fogFactor = clamp(exp(-ubo.uFog.y * (1.0 - normalizedHeight)), 0.0, 1.0);
                        if (ubo.uFogColorSteps.w > 0.0) {
                            fogFactor = floor(fogFactor * ubo.uFogColorSteps.w) / ubo.uFogColorSteps.w;
                        }
                        color = mix(ubo.uFogColorSteps.rgb, color, fogFactor);
                    }
                    if (ubo.uSmoke.x > 0.5) {
                        float radial = clamp(1.0 - length(gl_FragCoord.xy / vec2(1920.0, 1080.0) - vec2(0.5)), 0.0, 1.0);
                        float smokeFactor = clamp(ubo.uSmoke.y * (0.35 + radial * 0.65), 0.0, 0.85);
                        color = mix(color, ubo.uSmokeColor.rgb, smokeFactor);
                    }
                    if (ubo.uIbl.x > 0.5) {
                        float iblDiffuseWeight = clamp(ubo.uIbl.y, 0.0, 2.0);
                        float iblSpecWeight = clamp(ubo.uIbl.z, 0.0, 2.0);
                        float prefilter = clamp(ubo.uIbl.w, 0.0, 1.0);
                        vec3 irr = texture(uIblIrradianceTexture, vUv).rgb;
                        vec3 reflectDir = reflect(-viewDir, n);
                        vec2 specUv = clamp(reflectDir.xy * 0.5 + vec2(0.5), vec2(0.0), vec2(1.0));
                        vec3 rad = sampleIblRadiance(specUv, vUv, roughness, prefilter);
                        vec2 brdfUv = vec2(clamp(ndv, 0.0, 1.0), clamp(roughness, 0.0, 1.0));
                        vec2 brdf = texture(uIblBrdfLutTexture, brdfUv).rg;
                        float fresnel = pow(1.0 - ndv, 5.0);
                        vec3 iblDiffuse = baseColor * ao * irr * (0.2 + 0.55 * (1.0 - roughness)) * iblDiffuseWeight;
                        vec3 iblSpec = rad * mix(vec3(0.03), f0, fresnel) * (0.1 + 0.55 * (1.0 - roughness))
                                * (0.4 + 0.6 * brdf.x + 0.3 * brdf.y) * iblSpecWeight;
                        color += iblDiffuse + iblSpec;
                    }
                    if (ubo.uPostProcess.x > 0.5) {
                        float exposure = max(ubo.uPostProcess.y, 0.0001);
                        float gamma = max(ubo.uPostProcess.z, 0.0001);
                        color = vec3(1.0) - exp(-color * exposure);
                        color = pow(max(color, vec3(0.0)), vec3(1.0 / gamma));
                    }
                    if (ubo.uBloom.x > 0.5) {
                        float threshold = clamp(ubo.uBloom.y, 0.0, 4.0);
                        float strength = clamp(ubo.uBloom.z, 0.0, 2.0);
                        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        float bright = max(0.0, luma - threshold);
                        float bloom = bright * strength;
                        color += color * bloom;
                    }
                    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
                }
                """;

        ByteBuffer vertSpv = compileGlslToSpv(vertexShaderSource, shaderc_glsl_vertex_shader, "triangle.vert");
        ByteBuffer fragSpv = compileGlslToSpv(fragmentShaderSource, shaderc_fragment_shader, "triangle.frag");

        long vertModule = VK_NULL_HANDLE;
        long fragModule = VK_NULL_HANDLE;
        try {
            vertModule = createShaderModule(stack, vertSpv);
            fragModule = createShaderModule(stack, fragSpv);

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

    private long createShaderModule(MemoryStack stack, ByteBuffer code) throws EngineException {
        VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(code);
        var pShaderModule = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateShaderModule(device, moduleInfo, null, pShaderModule);
        if (result != VK_SUCCESS || pShaderModule.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateShaderModule failed: " + result, false);
        }
        return pShaderModule.get(0);
    }

    private ByteBuffer compileGlslToSpv(String source, int shaderKind, String sourceName) throws EngineException {
        long compiler = shaderc_compiler_initialize();
        if (compiler == 0L) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "shaderc_compiler_initialize failed", false);
        }
        long options = shaderc_compile_options_initialize();
        if (options == 0L) {
            shaderc_compiler_release(compiler);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "shaderc_compile_options_initialize failed", false);
        }
        long result = 0L;
        try {
            result = shaderc_compile_into_spv(compiler, source, shaderKind, sourceName, "main", options);
            if (result == 0L) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "shaderc_compile_into_spv failed", false);
            }
            int status = shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Shader compile failed for " + sourceName + ": " + shaderc_result_get_error_message(result),
                        false
                );
            }
            ByteBuffer bytes = shaderc_result_get_bytes(result);
            ByteBuffer out = ByteBuffer.allocateDirect(bytes.remaining());
            out.put(bytes);
            out.flip();
            return out;
        } finally {
            if (result != 0L) {
                shaderc_result_release(result);
            }
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
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

        ImageAlloc intermediate = createImage(
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
        String vertexShaderSource = """
                #version 450
                layout(location = 0) out vec2 vUv;
                vec2 POS[3] = vec2[](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
                void main() {
                    vec2 p = POS[gl_VertexIndex];
                    vUv = p * 0.5 + vec2(0.5);
                    gl_Position = vec4(p, 0.0, 1.0);
                }
                """;
        String fragmentShaderSource = """
                #version 450
                layout(location = 0) in vec2 vUv;
                layout(location = 0) out vec4 outColor;
                layout(set = 0, binding = 0) uniform sampler2D uSceneColor;
                layout(push_constant) uniform PostPush {
                    vec4 tonemap;
                    vec4 bloom;
                } pc;
                void main() {
                    vec3 color = texture(uSceneColor, vUv).rgb;
                    if (pc.tonemap.x > 0.5) {
                        float exposure = max(pc.tonemap.y, 0.0001);
                        float gamma = max(pc.tonemap.z, 0.0001);
                        color = vec3(1.0) - exp(-color * exposure);
                        color = pow(max(color, vec3(0.0)), vec3(1.0 / gamma));
                    }
                    if (pc.bloom.x > 0.5) {
                        float threshold = clamp(pc.bloom.y, 0.0, 4.0);
                        float strength = clamp(pc.bloom.z, 0.0, 2.0);
                        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        float bright = max(0.0, luma - threshold);
                        color += color * (bright * strength);
                    }
                    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
                }
                """;
        ByteBuffer vertSpv = compileGlslToSpv(vertexShaderSource, shaderc_glsl_vertex_shader, "post.vert");
        ByteBuffer fragSpv = compileGlslToSpv(fragmentShaderSource, shaderc_fragment_shader, "post.frag");
        long vertModule = VK_NULL_HANDLE;
        long fragModule = VK_NULL_HANDLE;
        try {
            vertModule = createShaderModule(stack, vertSpv);
            fragModule = createShaderModule(stack, fragSpv);
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
            return submitAndPresent(stack, commandBuffer, imageIndex, imageAvailableSemaphore, renderFinishedSemaphore, renderFence);
        }
        if (acquireResult != VK_ERROR_OUT_OF_DATE_KHR) {
            throw vkFailure("vkAcquireNextImageKHR", acquireResult);
        }
        return acquireResult;
    }

    private void recordCommandBuffer(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIndex, int frameIdx) throws EngineException {
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        int beginResult = vkBeginCommandBuffer(commandBuffer, beginInfo);
        if (beginResult != VK_SUCCESS) {
            throw vkFailure("vkBeginCommandBuffer", beginResult);
        }

        updateShadowLightViewProjMatrices();
        prepareFrameUniforms(frameIdx);
        uploadFrameUniforms(commandBuffer, frameIdx);
        long frameDescriptorSet = descriptorSetForFrame(frameIdx);
        int drawCount = gpuMeshes.isEmpty() ? 1 : Math.min(maxDynamicSceneObjects, gpuMeshes.size());
        if (shadowEnabled
                && shadowRenderPass != VK_NULL_HANDLE
                && shadowFramebuffers.length >= Math.min(MAX_SHADOW_MATRICES, Math.max(1, shadowCascadeCount))
                && shadowPipeline != VK_NULL_HANDLE
                && frameDescriptorSet != VK_NULL_HANDLE
                && !gpuMeshes.isEmpty()) {
            int cascades = pointShadowEnabled
                    ? POINT_SHADOW_FACES
                    : Math.min(MAX_SHADOW_CASCADES, Math.max(1, shadowCascadeCount));
            for (int cascadeIndex = 0; cascadeIndex < cascades; cascadeIndex++) {
                VkClearValue.Buffer shadowClearValues = VkClearValue.calloc(1, stack);
                shadowClearValues.get(0).depthStencil().depth(1.0f).stencil(0);
                VkRenderPassBeginInfo shadowPassInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(shadowRenderPass)
                        .framebuffer(shadowFramebuffers[cascadeIndex])
                        .pClearValues(shadowClearValues);
                shadowPassInfo.renderArea()
                        .offset(it -> it.set(0, 0))
                        .extent(VkExtent2D.calloc(stack).set(shadowMapResolution, shadowMapResolution));
                vkCmdBeginRenderPass(commandBuffer, shadowPassInfo, VK_SUBPASS_CONTENTS_INLINE);
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, shadowPipeline);
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        shadowPipelineLayout,
                        0,
                        stack.longs(frameDescriptorSet),
                        stack.ints(dynamicUniformOffset(frameIdx, 0))
                );
                ByteBuffer cascadePush = stack.malloc(Integer.BYTES);
                cascadePush.putInt(0, cascadeIndex);
                vkCmdPushConstants(commandBuffer, shadowPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, cascadePush);
                for (int meshIndex = 0; meshIndex < drawCount; meshIndex++) {
                    GpuMesh mesh = gpuMeshes.get(meshIndex);
                    vkCmdBindDescriptorSets(
                            commandBuffer,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            shadowPipelineLayout,
                            0,
                            stack.longs(frameDescriptorSet),
                            stack.ints(dynamicUniformOffset(frameIdx, meshIndex))
                    );
                    vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(mesh.vertexBuffer), stack.longs(0));
                    vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer, 0, VK_INDEX_TYPE_UINT32);
                    vkCmdDrawIndexed(commandBuffer, mesh.indexCount, 1, 0, 0, 0);
                }
                vkCmdEndRenderPass(commandBuffer);
            }
        }

        VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
        clearValues.get(0).color().float32(0, 0.08f);
        clearValues.get(0).color().float32(1, 0.09f);
        clearValues.get(0).color().float32(2, 0.12f);
        clearValues.get(0).color().float32(3, 1.0f);
        clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass)
                .framebuffer(framebuffers[imageIndex])
                .pClearValues(clearValues);
        renderPassInfo.renderArea()
                .offset(it -> it.set(0, 0))
                .extent(VkExtent2D.calloc(stack).set(swapchainWidth, swapchainHeight));

        vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
        for (int meshIndex = 0; meshIndex < drawCount && meshIndex < gpuMeshes.size(); meshIndex++) {
            GpuMesh mesh = gpuMeshes.get(meshIndex);
            if (frameDescriptorSet != VK_NULL_HANDLE && mesh.textureDescriptorSet != VK_NULL_HANDLE) {
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(frameDescriptorSet, mesh.textureDescriptorSet),
                        stack.ints(dynamicUniformOffset(frameIdx, meshIndex))
                );
            }
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(mesh.vertexBuffer), stack.longs(0));
            vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer, 0, VK_INDEX_TYPE_UINT32);
            vkCmdDrawIndexed(commandBuffer, mesh.indexCount, 1, 0, 0, 0);
        }
        if (gpuMeshes.isEmpty()) {
            vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        }
        vkCmdEndRenderPass(commandBuffer);

        if (postOffscreenActive) {
            executePostCompositePass(stack, commandBuffer, imageIndex);
        }

        int endResult = vkEndCommandBuffer(commandBuffer);
        if (endResult != VK_SUCCESS) {
            throw vkFailure("vkEndCommandBuffer", endResult);
        }
    }

    private void executePostCompositePass(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIndex) {
        if (postRenderPass == VK_NULL_HANDLE
                || postGraphicsPipeline == VK_NULL_HANDLE
                || postPipelineLayout == VK_NULL_HANDLE
                || postDescriptorSet == VK_NULL_HANDLE
                || postFramebuffers.length <= imageIndex
                || offscreenColorImage == VK_NULL_HANDLE) {
            return;
        }

        // Transition the rendered swapchain image for transfer-src copy.
        VkImageMemoryBarrier.Buffer swapToTransferSrc = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(swapchainImages[imageIndex]);
        swapToTransferSrc.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                swapToTransferSrc
        );

        int intermediateOldLayout = postIntermediateInitialized
                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                : VK_IMAGE_LAYOUT_UNDEFINED;
        VkImageMemoryBarrier.Buffer intermediateToTransferDst = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(postIntermediateInitialized ? VK10.VK_ACCESS_SHADER_READ_BIT : 0)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                .oldLayout(intermediateOldLayout)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(offscreenColorImage);
        intermediateToTransferDst.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                postIntermediateInitialized ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                intermediateToTransferDst
        );

        VkImageCopy.Buffer copyRegion = VkImageCopy.calloc(1, stack);
        copyRegion.get(0)
                .srcSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                .srcOffset(it -> it.set(0, 0, 0))
                .dstSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                .dstOffset(it -> it.set(0, 0, 0))
                .extent(it -> it.set(swapchainWidth, swapchainHeight, 1));
        VK10.vkCmdCopyImage(
                commandBuffer,
                swapchainImages[imageIndex],
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                offscreenColorImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                copyRegion
        );

        VkImageMemoryBarrier.Buffer intermediateToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(offscreenColorImage);
        intermediateToShaderRead.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,
                null,
                null,
                intermediateToShaderRead
        );
        postIntermediateInitialized = true;

        VkImageMemoryBarrier.Buffer swapToColorAttachment = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(swapchainImages[imageIndex]);
        swapToColorAttachment.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0,
                null,
                null,
                swapToColorAttachment
        );

        VkClearValue.Buffer clear = VkClearValue.calloc(1, stack);
        clear.get(0).color().float32(0, 0.08f);
        clear.get(0).color().float32(1, 0.09f);
        clear.get(0).color().float32(2, 0.12f);
        clear.get(0).color().float32(3, 1.0f);
        VkRenderPassBeginInfo postPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(postRenderPass)
                .framebuffer(postFramebuffers[imageIndex])
                .pClearValues(clear);
        postPassInfo.renderArea()
                .offset(it -> it.set(0, 0))
                .extent(VkExtent2D.calloc(stack).set(swapchainWidth, swapchainHeight));

        vkCmdBeginRenderPass(commandBuffer, postPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, postGraphicsPipeline);
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                postPipelineLayout,
                0,
                stack.longs(postDescriptorSet),
                null
        );
        ByteBuffer postPush = stack.malloc(8 * Float.BYTES);
        postPush.asFloatBuffer().put(new float[]{
                tonemapEnabled ? 1f : 0f, tonemapExposure, tonemapGamma, 0f,
                bloomEnabled ? 1f : 0f, bloomThreshold, bloomStrength, 0f
        });
        vkCmdPushConstants(commandBuffer, postPipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, postPush);
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderPass(commandBuffer);
    }

    private int submitAndPresent(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            int imageIndex,
            long imageAvailableSemaphore,
            long renderFinishedSemaphore,
            long renderFence
    ) throws EngineException {
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                .pCommandBuffers(stack.pointers(commandBuffer.address()))
                .pSignalSemaphores(stack.longs(renderFinishedSemaphore));
        int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, renderFence);
        if (submitResult != VK_SUCCESS) {
            throw vkFailure("vkQueueSubmit", submitResult);
        }

        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                .swapchainCount(1)
                .pSwapchains(stack.longs(swapchain))
                .pImageIndices(stack.ints(imageIndex));
        int presentResult = vkQueuePresentKHR(graphicsQueue, presentInfo);
        glfwPollEvents();
        if (presentResult != VK_SUCCESS && presentResult != VK_SUBOPTIMAL_KHR && presentResult != VK_ERROR_OUT_OF_DATE_KHR) {
            throw vkFailure("vkQueuePresentKHR", presentResult);
        }
        return presentResult;
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

    private VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return formats.get(0);
    }

    private int choosePresentMode(java.nio.IntBuffer presentModes, int count) {
        for (int i = 0; i < count; i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private static int alignUp(int value, int alignment) {
        int safeAlignment = Math.max(1, alignment);
        int remainder = value % safeAlignment;
        if (remainder == 0) {
            return value;
        }
        return value + (safeAlignment - remainder);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) <= 1.0e-6f;
    }

    private static float[] normalize3(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6f) {
            return new float[]{0f, -1f, 0f};
        }
        return new float[]{x / len, y / len, z / len};
    }

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float projectionNear(float[] proj) {
        float a = proj[10];
        float b = proj[14];
        return b / (a - 1f);
    }

    private static float projectionFar(float[] proj) {
        float a = proj[10];
        float b = proj[14];
        return b / (a + 1f);
    }

    private static float viewDistanceToNdcDepth(float[] proj, float distance) {
        float clipZ = proj[10] * (-distance) + proj[14];
        float clipW = proj[11] * (-distance) + proj[15];
        if (Math.abs(clipW) < 0.000001f) {
            return 1f;
        }
        return clipZ / clipW;
    }

    private static float[] transformPoint(float[] m, float x, float y, float z) {
        float tx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float ty = m[1] * x + m[5] * y + m[9] * z + m[13];
        float tz = m[2] * x + m[6] * y + m[10] * z + m[14];
        float tw = m[3] * x + m[7] * y + m[11] * z + m[15];
        if (Math.abs(tw) > 0.000001f) {
            return new float[]{tx / tw, ty / tw, tz / tw};
        }
        return new float[]{tx, ty, tz};
    }

    private static float[] unproject(float[] invViewProj, float ndcX, float ndcY, float ndcZ) {
        float x = invViewProj[0] * ndcX + invViewProj[4] * ndcY + invViewProj[8] * ndcZ + invViewProj[12];
        float y = invViewProj[1] * ndcX + invViewProj[5] * ndcY + invViewProj[9] * ndcZ + invViewProj[13];
        float z = invViewProj[2] * ndcX + invViewProj[6] * ndcY + invViewProj[10] * ndcZ + invViewProj[14];
        float w = invViewProj[3] * ndcX + invViewProj[7] * ndcY + invViewProj[11] * ndcZ + invViewProj[15];
        if (Math.abs(w) < 0.000001f) {
            return new float[]{x, y, z};
        }
        return new float[]{x / w, y / w, z / w};
    }

    private static float[] invert(float[] m) {
        float[] inv = new float[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15]
                + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15]
                - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15]
                + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14]
                - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15]
                - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15]
                + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15]
                - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14]
                + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15]
                + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15]
                - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15]
                + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14]
                - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11]
                - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11]
                + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11]
                - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10]
                + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (Math.abs(det) < 0.0000001f) {
            return null;
        }
        float invDet = 1.0f / det;
        for (int i = 0; i < 16; i++) {
            inv[i] *= invDet;
        }
        return inv;
    }

    private void updateShadowLightViewProjMatrices() {
        if (pointLightIsSpot > 0.5f) {
            float[] spotDir = normalize3(pointLightDirX, pointLightDirY, pointLightDirZ);
            float targetX = pointLightPosX + spotDir[0];
            float targetY = pointLightPosY + spotDir[1];
            float targetZ = pointLightPosZ + spotDir[2];
            float upX = 0f;
            float upY = 1f;
            float upZ = 0f;
            if (Math.abs(spotDir[1]) > 0.95f) {
                upX = 0f;
                upY = 0f;
                upZ = 1f;
            }
            float[] lightView = lookAt(pointLightPosX, pointLightPosY, pointLightPosZ, targetX, targetY, targetZ, upX, upY, upZ);
            float outerCos = Math.max(0.0001f, Math.min(1f, pointLightOuterCos));
            float coneHalfAngle = (float) Math.acos(outerCos);
            float fov = Math.max((float) Math.toRadians(20.0), Math.min((float) Math.toRadians(120.0), coneHalfAngle * 2.0f));
            float[] lightProj = perspective(fov, 1f, 0.1f, 30f);
            shadowLightViewProjMatrices[0] = mul(lightProj, lightView);
            for (int i = 1; i < MAX_SHADOW_MATRICES; i++) {
                shadowLightViewProjMatrices[i] = shadowLightViewProjMatrices[0];
            }
            shadowCascadeSplitNdc[0] = 1f;
            shadowCascadeSplitNdc[1] = 1f;
            shadowCascadeSplitNdc[2] = 1f;
            return;
        }
        if (pointShadowEnabled) {
            float[][] pointDirs = new float[][]{
                    {1f, 0f, 0f},
                    {-1f, 0f, 0f},
                    {0f, 1f, 0f},
                    {0f, -1f, 0f},
                    {0f, 0f, 1f},
                    {0f, 0f, -1f}
            };
            float[][] pointUp = new float[][]{
                    {0f, -1f, 0f},
                    {0f, -1f, 0f},
                    {0f, 0f, 1f},
                    {0f, 0f, -1f},
                    {0f, -1f, 0f},
                    {0f, -1f, 0f}
            };
            float[] lightProj = perspective((float) Math.toRadians(90.0), 1f, 0.1f, pointShadowFarPlane);
            int availableLayers = Math.max(1, Math.min(POINT_SHADOW_FACES, shadowCascadeCount));
            for (int i = 0; i < MAX_SHADOW_MATRICES; i++) {
                int dirIndex = Math.min(i, pointDirs.length - 1);
                if (i >= availableLayers) {
                    shadowLightViewProjMatrices[i] = shadowLightViewProjMatrices[availableLayers - 1];
                    continue;
                }
                float[] dir = pointDirs[dirIndex];
                float[] lightView = lookAt(
                        pointLightPosX,
                        pointLightPosY,
                        pointLightPosZ,
                        pointLightPosX + dir[0],
                        pointLightPosY + dir[1],
                        pointLightPosZ + dir[2],
                        pointUp[dirIndex][0], pointUp[dirIndex][1], pointUp[dirIndex][2]
                );
                shadowLightViewProjMatrices[i] = mul(lightProj, lightView);
            }
            shadowCascadeSplitNdc[0] = 1f;
            shadowCascadeSplitNdc[1] = 1f;
            shadowCascadeSplitNdc[2] = 1f;
            return;
        }
        float[] viewProj = mul(projMatrix, viewMatrix);
        float[] invViewProj = invert(viewProj);
        if (invViewProj == null) {
            for (int i = 0; i < MAX_SHADOW_MATRICES; i++) {
                shadowLightViewProjMatrices[i] = identityMatrix();
            }
            shadowCascadeSplitNdc[0] = 1f;
            shadowCascadeSplitNdc[1] = 1f;
            shadowCascadeSplitNdc[2] = 1f;
            return;
        }

        float near = projectionNear(projMatrix);
        float far = projectionFar(projMatrix);
        if (!(near > 0f) || !(far > near)) {
            near = 0.1f;
            far = 100f;
        }

        int cascades = Math.max(1, Math.min(MAX_SHADOW_CASCADES, shadowCascadeCount));
        float lambda = 0.7f;
        float prevSplitDist = near;
        float[] splitDist = new float[MAX_SHADOW_CASCADES];
        for (int i = 0; i < cascades; i++) {
            float p = (i + 1f) / cascades;
            float log = near * (float) Math.pow(far / near, p);
            float lin = near + (far - near) * p;
            splitDist[i] = log * lambda + lin * (1f - lambda);
        }

        float len = (float) Math.sqrt(dirLightDirX * dirLightDirX + dirLightDirY * dirLightDirY + dirLightDirZ * dirLightDirZ);
        if (len < 0.0001f) {
            len = 1f;
        }
        float lx = dirLightDirX / len;
        float ly = dirLightDirY / len;
        float lz = dirLightDirZ / len;
        float upX = 0f;
        float upY = 1f;
        float upZ = 0f;
        if (Math.abs(ly) > 0.95f) {
            upX = 0f;
            upY = 0f;
            upZ = 1f;
        }

        shadowCascadeSplitNdc[0] = 1f;
        shadowCascadeSplitNdc[1] = 1f;
        shadowCascadeSplitNdc[2] = 1f;
        for (int cascade = 0; cascade < MAX_SHADOW_CASCADES; cascade++) {
            if (cascade >= cascades) {
                shadowLightViewProjMatrices[cascade] = shadowLightViewProjMatrices[Math.max(0, cascades - 1)];
                continue;
            }
            float nearDist = prevSplitDist;
            float farDist = splitDist[cascade];
            prevSplitDist = farDist;
            float nearNdc = viewDistanceToNdcDepth(projMatrix, nearDist);
            float farNdc = viewDistanceToNdcDepth(projMatrix, farDist);
            if (cascade < 3) {
                shadowCascadeSplitNdc[cascade] = farNdc * 0.5f + 0.5f;
            }

            float[][] corners = new float[8][3];
            int idx = 0;
            for (int z = 0; z < 2; z++) {
                float ndcZ = z == 0 ? nearNdc : farNdc;
                for (int y = 0; y < 2; y++) {
                    float ndcY = y == 0 ? -1f : 1f;
                    for (int x = 0; x < 2; x++) {
                        float ndcX = x == 0 ? -1f : 1f;
                        float[] world = unproject(invViewProj, ndcX, ndcY, ndcZ);
                        corners[idx][0] = world[0];
                        corners[idx][1] = world[1];
                        corners[idx][2] = world[2];
                        idx++;
                    }
                }
            }

            float centerX = 0f;
            float centerY = 0f;
            float centerZ = 0f;
            for (float[] c : corners) {
                centerX += c[0];
                centerY += c[1];
                centerZ += c[2];
            }
            centerX /= 8f;
            centerY /= 8f;
            centerZ /= 8f;

            float radius = 0f;
            for (float[] c : corners) {
                float dx = c[0] - centerX;
                float dy = c[1] - centerY;
                float dz = c[2] - centerZ;
                radius = Math.max(radius, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            radius = Math.max(radius, 1f);
            float eyeX = centerX - lx * (radius * 2.0f);
            float eyeY = centerY - ly * (radius * 2.0f);
            float eyeZ = centerZ - lz * (radius * 2.0f);
            float[] lightView = lookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);

            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (float[] c : corners) {
                float[] l = transformPoint(lightView, c[0], c[1], c[2]);
                minX = Math.min(minX, l[0]);
                minY = Math.min(minY, l[1]);
                minZ = Math.min(minZ, l[2]);
                maxX = Math.max(maxX, l[0]);
                maxY = Math.max(maxY, l[1]);
                maxZ = Math.max(maxZ, l[2]);
            }
            float zPad = Math.max(10f, radius);
            float[] lightProj = ortho(minX, maxX, minY, maxY, minZ - zPad, maxZ + zPad);
            shadowLightViewProjMatrices[cascade] = mul(lightProj, lightView);
        }
    }

    private static float[] lookAt(float eyeX, float eyeY, float eyeZ, float targetX, float targetY, float targetZ,
                                  float upX, float upY, float upZ) {
        float fx = targetX - eyeX;
        float fy = targetY - eyeY;
        float fz = targetZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen < 0.00001f) {
            return identityMatrix();
        }
        fx /= fLen;
        fy /= fLen;
        fz /= fLen;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 0.00001f) {
            return identityMatrix();
        }
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        return new float[]{
                sx, ux, -fx, 0f,
                sy, uy, -fy, 0f,
                sz, uz, -fz, 0f,
                -(sx * eyeX + sy * eyeY + sz * eyeZ),
                -(ux * eyeX + uy * eyeY + uz * eyeZ),
                (fx * eyeX + fy * eyeY + fz * eyeZ),
                1f
        };
    }

    private static float[] ortho(float left, float right, float bottom, float top, float near, float far) {
        float rl = right - left;
        float tb = top - bottom;
        float fn = far - near;
        return new float[]{
                2f / rl, 0f, 0f, 0f,
                0f, 2f / tb, 0f, 0f,
                0f, 0f, -2f / fn, 0f,
                -(right + left) / rl, -(top + bottom) / tb, -(far + near) / fn, 1f
        };
    }

    private static float[] perspective(float fovRad, float aspect, float near, float far) {
        float f = 1.0f / (float) Math.tan(fovRad * 0.5f);
        float nf = 1.0f / (near - far);
        return new float[]{
                f / aspect, 0f, 0f, 0f,
                0f, f, 0f, 0f,
                0f, 0f, (far + near) * nf, -1f,
                0f, 0f, (2f * far * near) * nf, 0f
        };
    }

    private static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
        return out;
    }

    private VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities, int width, int height, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent());
        }
        int clampedWidth = Math.max(capabilities.minImageExtent().width(), Math.min(capabilities.maxImageExtent().width(), width));
        int clampedHeight = Math.max(capabilities.minImageExtent().height(), Math.min(capabilities.maxImageExtent().height(), height));
        return VkExtent2D.calloc(stack).set(clampedWidth, clampedHeight);
    }

    private boolean canReuseGpuMeshes(List<SceneMeshData> sceneMeshes) {
        if (gpuMeshes.isEmpty() || sceneMeshes.size() != gpuMeshes.size()) {
            return false;
        }
        Map<String, GpuMesh> byId = new HashMap<>();
        for (GpuMesh gpuMesh : gpuMeshes) {
            if (byId.put(gpuMesh.meshId, gpuMesh) != null) {
                return false;
            }
        }
        for (SceneMeshData sceneMesh : sceneMeshes) {
            GpuMesh gpuMesh = byId.get(sceneMesh.meshId());
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

    private void updateDynamicSceneState(List<SceneMeshData> sceneMeshes) {
        Map<String, GpuMesh> byId = new HashMap<>();
        for (GpuMesh mesh : gpuMeshes) {
            byId.put(mesh.meshId, mesh);
        }
        List<GpuMesh> ordered = new ArrayList<>(sceneMeshes.size());
        boolean reordered = false;
        int dirtyStart = Integer.MAX_VALUE;
        int dirtyEnd = -1;
        for (int i = 0; i < sceneMeshes.size(); i++) {
            SceneMeshData sceneMesh = sceneMeshes.get(i);
            GpuMesh mesh = byId.get(sceneMesh.meshId());
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

    private void uploadSceneMeshes(MemoryStack stack, List<SceneMeshData> sceneMeshes) throws EngineException {
        meshBufferRebuildCount++;
        destroySceneMeshes();
        Map<String, GpuTexture> textureCache = new HashMap<>();
        GpuTexture defaultAlbedo = createTextureFromPath(null, false);
        GpuTexture defaultNormal = createTextureFromPath(null, true);
        GpuTexture defaultMetallicRoughness = createTextureFromPath(null, false);
        GpuTexture defaultOcclusion = createTextureFromPath(null, false);
        iblIrradianceTexture = resolveOrCreateTexture(iblIrradiancePath, textureCache, defaultAlbedo, false);
        iblRadianceTexture = resolveOrCreateTexture(iblRadiancePath, textureCache, defaultAlbedo, false);
        iblBrdfLutTexture = resolveOrCreateTexture(iblBrdfLutPath, textureCache, defaultAlbedo, false);
        for (SceneMeshData mesh : sceneMeshes) {
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

            BufferAlloc vertexAlloc = createDeviceLocalBufferWithStaging(
                    stack,
                    vertexData,
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
            );
            BufferAlloc indexAlloc = createDeviceLocalBufferWithStaging(
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
            GpuTexture albedoTexture = resolveOrCreateTexture(mesh.albedoTexturePath(), textureCache, defaultAlbedo, false);
            GpuTexture normalTexture = resolveOrCreateTexture(mesh.normalTexturePath(), textureCache, defaultNormal, true);
            GpuTexture metallicRoughnessTexture = resolveOrCreateTexture(
                    mesh.metallicRoughnessTexturePath(),
                    textureCache,
                    defaultMetallicRoughness,
                    false
            );
            GpuTexture occlusionTexture = resolveOrCreateTexture(mesh.occlusionTexturePath(), textureCache, defaultOcclusion, false);

            gpuMeshes.add(new GpuMesh(
                    vertexAlloc.buffer,
                    vertexAlloc.memory,
                    indexAlloc.buffer,
                    indexAlloc.memory,
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
        Set<GpuTexture> uniqueTextures = new HashSet<>();
        for (GpuMesh mesh : gpuMeshes) {
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

    private void destroySceneMeshes() {
        if (device == null) {
            gpuMeshes.clear();
            return;
        }
        Set<GpuTexture> uniqueTextures = new HashSet<>();
        for (GpuMesh mesh : gpuMeshes) {
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
        for (GpuTexture texture : uniqueTextures) {
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
        if (textureDescriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, textureDescriptorPool, null);
            textureDescriptorPool = VK_NULL_HANDLE;
        }
        iblIrradianceTexture = null;
        iblRadianceTexture = null;
        iblBrdfLutTexture = null;
        gpuMeshes.clear();
    }

    private GpuTexture resolveOrCreateTexture(
            Path texturePath,
            Map<String, GpuTexture> cache,
            GpuTexture defaultTexture,
            boolean normalMap
    )
            throws EngineException {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return defaultTexture;
        }
        String cacheKey = textureCacheKey(texturePath, normalMap);
        GpuTexture cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        GpuTexture created = createTextureFromPath(texturePath, normalMap);
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
        if (textureDescriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, textureDescriptorPool, null);
            textureDescriptorPool = VK_NULL_HANDLE;
            descriptorPoolRebuildCount++;
        }
        descriptorPoolBuildCount++;

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(gpuMeshes.size() * 8);

        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(gpuMeshes.size())
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

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(textureDescriptorPool);
        LongBufferWrapper setLayouts = LongBufferWrapper.allocate(stack, gpuMeshes.size());
        for (int i = 0; i < gpuMeshes.size(); i++) {
            setLayouts.buffer().put(i, textureDescriptorSetLayout);
        }
        allocInfo.pSetLayouts(setLayouts.buffer());

        LongBufferWrapper allocatedSets = LongBufferWrapper.allocate(stack, gpuMeshes.size());
        int setResult = vkAllocateDescriptorSets(device, allocInfo, allocatedSets.buffer());
        if (setResult != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkAllocateDescriptorSets(texture) failed: " + setResult,
                    false
            );
        }

        for (int i = 0; i < gpuMeshes.size(); i++) {
            GpuMesh mesh = gpuMeshes.get(i);
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

    private GpuTexture createTextureFromPath(Path texturePath, boolean normalMap) throws EngineException {
        TexturePixelData pixels = loadTexturePixels(texturePath);
        if (pixels == null) {
            ByteBuffer data = memAlloc(4);
            if (normalMap) {
                data.put((byte) 0x80).put((byte) 0x80).put((byte) 0xFF).put((byte) 0xFF).flip();
            } else {
                data.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
            }
            pixels = new TexturePixelData(data, 1, 1);
        }
        try {
            return createTextureFromPixels(pixels);
        } finally {
            memFree(pixels.data());
        }
    }

    private TexturePixelData loadTexturePixels(Path texturePath) {
        Path sourcePath = resolveContainerSourcePath(texturePath);
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(sourcePath.toFile());
            if (image != null) {
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
                return new TexturePixelData(buffer, width, height);
            }
        } catch (IOException ignored) {
            // Fall through to stb path.
        }
        return loadTexturePixelsViaStb(sourcePath);
    }

    private TexturePixelData loadTexturePixelsViaStb(Path texturePath) {
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
                    return new TexturePixelData(buffer, width, height);
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
                return new TexturePixelData(copy, width, height);
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

    private GpuTexture createTextureFromPixels(TexturePixelData pixels) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            BufferAlloc staging = createBuffer(
                    stack,
                    pixels.data().remaining(),
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            try {
                uploadToMemory(staging.memory, pixels.data());
                ImageAlloc imageAlloc = createImage(
                        stack,
                        pixels.width(),
                        pixels.height(),
                        VK10.VK_FORMAT_R8G8B8A8_SRGB,
                        VK10.VK_IMAGE_TILING_OPTIMAL,
                        VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );
                transitionImageLayout(imageAlloc.image(), VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                copyBufferToImage(staging.buffer, imageAlloc.image(), pixels.width(), pixels.height());
                transitionImageLayout(imageAlloc.image(), VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

                long imageView = createImageView(stack, imageAlloc.image(), VK10.VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT);
                long sampler = createSampler(stack);
                return new GpuTexture(imageAlloc.image(), imageAlloc.memory(), imageView, sampler, (long) pixels.width() * pixels.height() * 4L);
            } finally {
                if (staging.buffer != VK_NULL_HANDLE) {
                    vkDestroyBuffer(device, staging.buffer, null);
                }
                if (staging.memory != VK_NULL_HANDLE) {
                    vkFreeMemory(device, staging.memory, null);
                }
            }
        }
    }

    private ImageAlloc createImage(
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

    private ImageAlloc createImage(
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
        return new ImageAlloc(image, memory);
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
        if (pendingSceneDirtyRangeCount >= maxPendingUploadRanges) {
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
            if (currStart <= (prevEnd + 1)) {
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

        int uploadRangeCount;
        int[] uploadStarts = new int[maxPendingUploadRanges];
        int[] uploadEnds = new int[maxPendingUploadRanges];
        if (globalStale) {
            uploadRangeCount = 1;
            uploadStarts[0] = 0;
            uploadEnds[0] = meshCount - 1;
        } else if (sceneStale && pendingSceneDirtyRangeCount > 0) {
            uploadRangeCount = 0;
            for (int i = 0; i < pendingSceneDirtyRangeCount && uploadRangeCount < maxPendingUploadRanges; i++) {
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
        if (globalUniformStagingMappedAddress != 0L) {
            mapped = memByteBuffer(globalUniformStagingMappedAddress + frameBase, uniformFrameSpanBytes);
        } else {
            mapped = memAlloc(uniformFrameSpanBytes);
        }
        mapped.order(ByteOrder.nativeOrder());
        try {
            for (int range = 0; range < uploadRangeCount; range++) {
                int rangeStart = uploadStarts[range];
                int rangeEnd = uploadEnds[range];
                for (int meshIndex = rangeStart; meshIndex <= rangeEnd; meshIndex++) {
                    GpuMesh mesh = gpuMeshes.isEmpty() ? null : gpuMeshes.get(meshIndex);
                    writeSceneUniform(mapped, meshIndex * uniformStrideBytes, mesh);
                }
            }
            if (globalUniformStagingMappedAddress == 0L) {
                try (MemoryStack stack = stackPush()) {
                    PointerBuffer pData = stack.mallocPointer(1);
                    int mapResult = vkMapMemory(device, globalUniformStagingMemory, frameBase, uniformFrameSpanBytes, 0, pData);
                    if (mapResult != VK_SUCCESS) {
                        throw vkFailure("vkMapMemory(staging)", mapResult);
                    }
                    for (int range = 0; range < uploadRangeCount; range++) {
                        int rangeStartByte = uploadStarts[range] * uniformStrideBytes;
                        int rangeByteCount = ((uploadEnds[range] - uploadStarts[range]) + 1) * uniformStrideBytes;
                        memCopy(memAddress(mapped) + rangeStartByte, pData.get(0) + rangeStartByte, rangeByteCount);
                    }
                    vkUnmapMemory(device, globalUniformStagingMemory);
                }
            }
        } finally {
            if (globalUniformStagingMappedAddress == 0L) {
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

    private void writeSceneUniform(ByteBuffer target, int offset, GpuMesh mesh) {
        ByteBuffer slice = target.duplicate();
        slice.position(offset);
        slice.limit(offset + GLOBAL_UNIFORM_BYTES);
        FloatBuffer fb = slice.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        if (mesh == null) {
            fb.put(identityMatrix());
            fb.put(viewMatrix);
            fb.put(projMatrix);
            fb.put(new float[]{1f, 1f, 1f, 1f});
            fb.put(new float[]{0f, 0.8f, dirLightIntensity, pointLightIntensity});
        } else {
            fb.put(mesh.modelMatrix);
            fb.put(viewMatrix);
            fb.put(projMatrix);
            fb.put(new float[]{mesh.colorR, mesh.colorG, mesh.colorB, 1f});
            fb.put(new float[]{mesh.metallic, mesh.roughness, dirLightIntensity, pointLightIntensity});
        }
        fb.put(new float[]{dirLightDirX, dirLightDirY, dirLightDirZ, 0f});
        fb.put(new float[]{dirLightColorR, dirLightColorG, dirLightColorB, 0f});
        fb.put(new float[]{pointLightPosX, pointLightPosY, pointLightPosZ, pointShadowFarPlane});
        fb.put(new float[]{pointLightColorR, pointLightColorG, pointLightColorB, 0f});
        fb.put(new float[]{pointLightDirX, pointLightDirY, pointLightDirZ, 0f});
        fb.put(new float[]{pointLightInnerCos, pointLightOuterCos, pointLightIsSpot, pointShadowEnabled ? 1f : 0f});
        fb.put(new float[]{shadowEnabled ? 1f : 0f, shadowStrength, shadowBias, (float) shadowPcfRadius});
        float split1 = shadowCascadeSplitNdc[0];
        float split2 = shadowCascadeSplitNdc[1];
        float split3 = shadowCascadeSplitNdc[2];
        fb.put(new float[]{(float) shadowCascadeCount, (float) shadowMapResolution, split1, split2});
        fb.put(new float[]{0f, split3, 0f, 0f});
        fb.put(new float[]{fogEnabled ? 1f : 0f, fogDensity, 0f, 0f});
        fb.put(new float[]{fogR, fogG, fogB, (float) fogSteps});
        fb.put(new float[]{smokeEnabled ? 1f : 0f, smokeIntensity, 0f, 0f});
        fb.put(new float[]{smokeR, smokeG, smokeB, 0f});
        fb.put(new float[]{iblEnabled ? 1f : 0f, iblDiffuseStrength, iblSpecularStrength, iblPrefilterStrength});
        boolean scenePostEnabled = !postOffscreenActive;
        fb.put(new float[]{scenePostEnabled && tonemapEnabled ? 1f : 0f, tonemapExposure, tonemapGamma, 0f});
        fb.put(new float[]{scenePostEnabled && bloomEnabled ? 1f : 0f, bloomThreshold, bloomStrength, 0f});
        for (int i = 0; i < MAX_SHADOW_MATRICES; i++) {
            fb.put(shadowLightViewProjMatrices[i]);
        }
    }

    private void uploadFrameUniforms(VkCommandBuffer commandBuffer, int frameIdx) {
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
            vkCmdCopyBuffer(commandBuffer, globalUniformStagingBuffer, globalUniformBuffer, copy);
            copy.free();

            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_UNIFORM_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(globalUniformBuffer)
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

    private BufferAlloc createBuffer(MemoryStack stack, int sizeBytes, int usage, int memoryProperties) throws EngineException {
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
        return new BufferAlloc(buffer, memory);
    }

    private BufferAlloc createDeviceLocalBufferWithStaging(MemoryStack stack, ByteBuffer source, int usage) throws EngineException {
        int sizeBytes = source.remaining();
        BufferAlloc staging = createBuffer(
                stack,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        BufferAlloc deviceLocal = createBuffer(
                stack,
                sizeBytes,
                usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        try {
            uploadToMemory(staging.memory, source);
            copyBuffer(staging.buffer, deviceLocal.buffer, sizeBytes);
            return deviceLocal;
        } finally {
            if (staging.buffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, staging.buffer, null);
            }
            if (staging.memory != VK_NULL_HANDLE) {
                vkFreeMemory(device, staging.memory, null);
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

    private record BufferAlloc(long buffer, long memory) {
    }

    private static final class GpuMesh {
        private final long vertexBuffer;
        private final long vertexMemory;
        private final long indexBuffer;
        private final long indexMemory;
        private final int indexCount;
        private final long vertexBytes;
        private final long indexBytes;
        private final String meshId;
        private float[] modelMatrix;
        private float colorR;
        private float colorG;
        private float colorB;
        private float metallic;
        private float roughness;
        private final GpuTexture albedoTexture;
        private final GpuTexture normalTexture;
        private final GpuTexture metallicRoughnessTexture;
        private final GpuTexture occlusionTexture;
        private final int vertexHash;
        private final int indexHash;
        private final String albedoKey;
        private final String normalKey;
        private final String metallicRoughnessKey;
        private final String occlusionKey;
        private long textureDescriptorSet = VK_NULL_HANDLE;

        private GpuMesh(
                long vertexBuffer,
                long vertexMemory,
                long indexBuffer,
                long indexMemory,
                int indexCount,
                long vertexBytes,
                long indexBytes,
                float[] modelMatrix,
                float colorR,
                float colorG,
                float colorB,
                float metallic,
                float roughness,
                GpuTexture albedoTexture,
                GpuTexture normalTexture,
                GpuTexture metallicRoughnessTexture,
                GpuTexture occlusionTexture,
                String meshId,
                int vertexHash,
                int indexHash,
                String albedoKey,
                String normalKey,
                String metallicRoughnessKey,
                String occlusionKey
        ) {
            this.vertexBuffer = vertexBuffer;
            this.vertexMemory = vertexMemory;
            this.indexBuffer = indexBuffer;
            this.indexMemory = indexMemory;
            this.indexCount = indexCount;
            this.vertexBytes = vertexBytes;
            this.indexBytes = indexBytes;
            this.meshId = meshId;
            this.modelMatrix = modelMatrix;
            this.colorR = colorR;
            this.colorG = colorG;
            this.colorB = colorB;
            this.metallic = metallic;
            this.roughness = roughness;
            this.albedoTexture = albedoTexture;
            this.normalTexture = normalTexture;
            this.metallicRoughnessTexture = metallicRoughnessTexture;
            this.occlusionTexture = occlusionTexture;
            this.vertexHash = vertexHash;
            this.indexHash = indexHash;
            this.albedoKey = albedoKey;
            this.normalKey = normalKey;
            this.metallicRoughnessKey = metallicRoughnessKey;
            this.occlusionKey = occlusionKey;
        }

        private boolean updateDynamicState(
                float[] modelMatrix,
                float colorR,
                float colorG,
                float colorB,
                float metallic,
                float roughness
        ) {
            boolean changed = !Arrays.equals(this.modelMatrix, modelMatrix)
                    || this.colorR != colorR
                    || this.colorG != colorG
                    || this.colorB != colorB
                    || this.metallic != metallic
                    || this.roughness != roughness;
            this.modelMatrix = modelMatrix;
            this.colorR = colorR;
            this.colorG = colorG;
            this.colorB = colorB;
            this.metallic = metallic;
            this.roughness = roughness;
            return changed;
        }
    }

    private record GpuTexture(long image, long memory, long view, long sampler, long bytes) {
    }

    private record ImageAlloc(long image, long memory) {
    }

    private record TexturePixelData(ByteBuffer data, int width, int height) {
    }

    static record SceneMeshData(
            String meshId,
            float[] vertices,
            int[] indices,
            float[] modelMatrix,
            float[] color,
            float metallic,
            float roughness,
            Path albedoTexturePath,
            Path normalTexturePath,
            Path metallicRoughnessTexturePath,
            Path occlusionTexturePath
    ) {
        SceneMeshData {
            if (meshId == null || meshId.isBlank()) {
                throw new IllegalArgumentException("meshId is required");
            }
            if (vertices == null || vertices.length < VERTEX_STRIDE_FLOATS * 3 || vertices.length % VERTEX_STRIDE_FLOATS != 0) {
                throw new IllegalArgumentException("vertices must be interleaved as pos/normal/uv/tangent");
            }
            if (indices == null || indices.length < 3 || indices.length % 3 != 0) {
                throw new IllegalArgumentException("indices must be non-empty triangles");
            }
            if (modelMatrix == null || modelMatrix.length != 16) {
                throw new IllegalArgumentException("modelMatrix must be 16 floats");
            }
            if (color == null || color.length != 4) {
                throw new IllegalArgumentException("color must be rgba");
            }
        }

        static SceneMeshData defaultTriangle() {
            return triangle(new float[]{1f, 1f, 1f, 1f}, 0);
        }

        static SceneMeshData triangle(float[] color, int meshIndex) {
            float offsetX = (meshIndex % 2 == 0 ? -0.25f : 0.25f) * Math.min(meshIndex, 3);
            return new SceneMeshData(
                    "default-triangle-" + meshIndex,
                    new float[]{
                            0.0f, -0.6f, 0.0f,     0f, 0f, 1f,    0.5f, 0.0f,    1f, 0f, 0f,
                            0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1.0f, 1.0f,    1f, 0f, 0f,
                            -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0.0f, 1.0f,    1f, 0f, 0f
                    },
                    new int[]{0, 1, 2},
                    new float[]{
                            1f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f,
                            0f, 0f, 1f, 0f,
                            offsetX, 0f, 0f, 1f
                    },
                    color,
                    0.0f,
                    0.6f,
                    null,
                    null,
                    null,
                    null
            );
        }

        static SceneMeshData quad(float[] color, int meshIndex) {
            float offsetX = (meshIndex - 1) * 0.35f;
            return new SceneMeshData(
                    "default-quad-" + meshIndex,
                    new float[]{
                            -0.6f, -0.6f, 0.0f,    0f, 0f, 1f,    0f, 0f,    1f, 0f, 0f,
                            0.6f, -0.6f, 0.0f,     0f, 0f, 1f,    1f, 0f,    1f, 0f, 0f,
                            0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1f, 1f,    1f, 0f, 0f,
                            -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0f, 1f,    1f, 0f, 0f
                    },
                    new int[]{0, 1, 2, 2, 3, 0},
                    new float[]{
                            1f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f,
                            0f, 0f, 1f, 0f,
                            offsetX, 0f, 0f, 1f
                    },
                    color,
                    0.0f,
                    0.6f,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    record VulkanFrameMetrics(
            double cpuFrameMs,
            double gpuFrameMs,
            long drawCalls,
            long triangles,
            long visibleObjects,
            long gpuMemoryBytes
    ) {
    }

    record SceneReuseStats(
            long reuseHits,
            long reorderReuseHits,
            long fullRebuilds,
            long meshBufferRebuilds,
            long descriptorPoolBuilds,
            long descriptorPoolRebuilds
    ) {
    }

    record FrameResourceProfile(
            int framesInFlight,
            int descriptorSetsInRing,
            int uniformStrideBytes,
            int uniformFrameSpanBytes,
            int dynamicSceneCapacity,
            int pendingUploadRangeCapacity,
            int lastFrameUniformUploadBytes,
            int maxFrameUniformUploadBytes,
            int lastFrameUniformObjectCount,
            int maxFrameUniformObjectCount,
            int lastFrameUniformUploadRanges,
            int maxFrameUniformUploadRanges,
            int lastFrameUniformUploadStartObject,
            long pendingUploadRangeOverflows,
            boolean persistentStagingMapped
    ) {
    }

    record ShadowCascadeProfile(
            boolean enabled,
            int cascadeCount,
            int mapResolution,
            int pcfRadius,
            float bias,
            float split1Ndc,
            float split2Ndc,
            float split3Ndc
    ) {
    }

    record PostProcessPipelineProfile(
            boolean offscreenRequested,
            boolean offscreenActive,
            String mode
    ) {
    }

    private void reallocateFrameTracking() {
        frameGlobalRevisionApplied = new long[framesInFlight];
        frameSceneRevisionApplied = new long[framesInFlight];
    }

    private void reallocateUploadRangeTracking() {
        pendingSceneDirtyStarts = new int[maxPendingUploadRanges];
        pendingSceneDirtyEnds = new int[maxPendingUploadRanges];
        pendingUploadSrcOffsets = new long[maxPendingUploadRanges];
        pendingUploadDstOffsets = new long[maxPendingUploadRanges];
        pendingUploadByteCounts = new int[maxPendingUploadRanges];
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
