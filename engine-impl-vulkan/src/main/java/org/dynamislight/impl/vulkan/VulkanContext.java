package org.dynamislight.impl.vulkan;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
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
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
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
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final int GLOBAL_UNIFORM_BYTES = 352;
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
    private long renderPass = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long graphicsPipeline = VK_NULL_HANDLE;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long textureDescriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    private long textureDescriptorPool = VK_NULL_HANDLE;
    private long globalUniformBuffer = VK_NULL_HANDLE;
    private long globalUniformMemory = VK_NULL_HANDLE;
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
    private long estimatedGpuMemoryBytes;
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

    void setSceneMeshes(List<SceneMeshData> sceneMeshes) throws EngineException {
        List<SceneMeshData> safe = (sceneMeshes == null || sceneMeshes.isEmpty())
                ? List.of(SceneMeshData.defaultTriangle())
                : List.copyOf(sceneMeshes);
        pendingSceneMeshes = safe;
        if (device == null) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            uploadSceneMeshes(stack, safe);
        }
    }

    void setCameraMatrices(float[] view, float[] proj) {
        if (view != null && view.length == 16) {
            viewMatrix = view.clone();
        }
        if (proj != null && proj.length == 16) {
            projMatrix = proj.clone();
        }
    }

    void setLightingParameters(
            float[] dirDir,
            float[] dirColor,
            float dirIntensity,
            float[] pointPos,
            float[] pointColor,
            float pointIntensity
    ) {
        if (dirDir != null && dirDir.length == 3) {
            dirLightDirX = dirDir[0];
            dirLightDirY = dirDir[1];
            dirLightDirZ = dirDir[2];
        }
        if (dirColor != null && dirColor.length == 3) {
            dirLightColorR = dirColor[0];
            dirLightColorG = dirColor[1];
            dirLightColorB = dirColor[2];
        }
        dirLightIntensity = Math.max(0f, dirIntensity);
        if (pointPos != null && pointPos.length == 3) {
            pointLightPosX = pointPos[0];
            pointLightPosY = pointPos[1];
            pointLightPosZ = pointPos[2];
        }
        if (pointColor != null && pointColor.length == 3) {
            pointLightColorR = pointColor[0];
            pointLightColorG = pointColor[1];
            pointLightColorB = pointColor[2];
        }
        pointLightIntensity = Math.max(0f, pointIntensity);
    }

    void setFogParameters(boolean enabled, float r, float g, float b, float density, int steps) {
        fogEnabled = enabled;
        fogR = r;
        fogG = g;
        fogB = b;
        fogDensity = Math.max(0f, density);
        fogSteps = Math.max(0, steps);
    }

    void setSmokeParameters(boolean enabled, float r, float g, float b, float intensity) {
        smokeEnabled = enabled;
        smokeR = r;
        smokeG = g;
        smokeB = b;
        smokeIntensity = Math.max(0f, Math.min(1f, intensity));
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
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
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

        VkDescriptorSetLayoutBinding.Buffer textureBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
        textureBindings.get(0)
                .binding(0)
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

        BufferAlloc uniformAlloc = createBuffer(
                stack,
                GLOBAL_UNIFORM_BYTES,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        globalUniformBuffer = uniformAlloc.buffer;
        globalUniformMemory = uniformAlloc.memory;
        estimatedGpuMemoryBytes = GLOBAL_UNIFORM_BYTES;

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1)
                .pPoolSizes(poolSizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorPool failed: " + poolResult, false);
        }
        descriptorPool = pPool.get(0);

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayout));
        var pSet = stack.longs(VK_NULL_HANDLE);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, pSet);
        if (setResult != VK_SUCCESS || pSet.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateDescriptorSets failed: " + setResult, false);
        }
        descriptorSet = pSet.get(0);

        VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
        bufferInfo.get(0)
                .buffer(globalUniformBuffer)
                .offset(0)
                .range(GLOBAL_UNIFORM_BYTES);
        VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
        write.get(0)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(bufferInfo);
        vkUpdateDescriptorSets(device, write, null);
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
        estimatedGpuMemoryBytes = 0;
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
        createRenderPass(stack);
        createGraphicsPipeline(stack);
        createFramebuffers(stack);
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

    private void createRenderPass(MemoryStack stack) throws EngineException {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
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
                    vec4 uFog;
                    vec4 uFogColorSteps;
                    vec4 uSmoke;
                    vec4 uSmokeColor;
                } ubo;
                void main() {
                    vec4 world = ubo.uModel * vec4(inPos, 1.0);
                    vWorldPos = world.xyz;
                    vHeight = world.y;
                    vec3 tangent = normalize(mat3(ubo.uModel) * inTangent);
                    vec3 normal = normalize(mat3(ubo.uModel) * inNormal);
                    normal = normalize(mix(normal, tangent, 0.08));
                    vNormal = normal;
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
                    vec4 uFog;
                    vec4 uFogColorSteps;
                    vec4 uSmoke;
                    vec4 uSmokeColor;
                } ubo;
                layout(set = 1, binding = 0) uniform sampler2D uAlbedoTexture;
                layout(location = 0) out vec4 outColor;
                void main() {
                    vec3 n = normalize(vNormal);
                    vec3 sampledAlbedo = texture(uAlbedoTexture, vUv).rgb;
                    vec3 baseColor = ubo.uBaseColor.rgb * sampledAlbedo;
                    float metallic = clamp(ubo.uMaterial.x, 0.0, 1.0);
                    float roughness = clamp(ubo.uMaterial.y, 0.04, 1.0);
                    float dirIntensity = max(0.0, ubo.uMaterial.z);
                    float pointIntensity = max(0.0, ubo.uMaterial.w);

                    vec3 ambient = 0.18 * baseColor;
                    vec3 lDir = normalize(-ubo.uDirLightDir.xyz);
                    float ndl = max(dot(n, lDir), 0.0);
                    vec3 diffuse = baseColor * ubo.uDirLightColor.rgb * (ndl * dirIntensity);

                    vec3 pDir = normalize(ubo.uPointLightPos.xyz - vWorldPos);
                    float pNdl = max(dot(n, pDir), 0.0);
                    float dist = max(length(ubo.uPointLightPos.xyz - vWorldPos), 0.1);
                    float attenuation = 1.0 / (1.0 + 0.35 * dist + 0.1 * dist * dist);
                    vec3 pointLit = baseColor * ubo.uPointLightColor.rgb * (pNdl * attenuation * pointIntensity);

                    vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0));
                    vec3 halfVec = normalize(lDir + viewDir);
                    float gloss = 1.0 - roughness;
                    float specPow = mix(8.0, 96.0, gloss);
                    float spec = pow(max(dot(n, halfVec), 0.0), specPow) * mix(0.08, 0.9, metallic);

                    vec3 color = ambient + diffuse + pointLit + vec3(spec);
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
                    outColor = vec4(clamp(color, 0.0, 2.2), 1.0);
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
                    .pAttachments(stack.longs(swapchainImageViews[i]))
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

    private void destroySwapchainResources() {
        if (device == null) {
            return;
        }
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
        swapchainImageViews = new long[0];
        swapchainImages = new long[0];
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK_NULL_HANDLE;
        }
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
                .commandBufferCount(MAX_FRAMES_IN_FLIGHT);

        PointerBuffer pCommandBuffer = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
        int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        if (allocResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers failed: " + allocResult, false);
        }
        commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            commandBuffers[i] = new VkCommandBuffer(pCommandBuffer.get(i), device);
        }
        currentFrame = 0;
    }

    private void createSyncObjects(MemoryStack stack) throws EngineException {
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

        imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        renderFinishedSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        renderFences = new long[MAX_FRAMES_IN_FLIGHT];

        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
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

        VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
        clearValues.color().float32(0, 0.08f);
        clearValues.color().float32(1, 0.09f);
        clearValues.color().float32(2, 0.12f);
        clearValues.color().float32(3, 1.0f);

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
        for (GpuMesh mesh : gpuMeshes) {
            updateGlobalUniform(mesh);
            if (descriptorSet != VK_NULL_HANDLE && mesh.textureDescriptorSet != VK_NULL_HANDLE) {
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(descriptorSet, mesh.textureDescriptorSet),
                        null
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
        int endResult = vkEndCommandBuffer(commandBuffer);
        if (endResult != VK_SUCCESS) {
            throw vkFailure("vkEndCommandBuffer", endResult);
        }
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

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities, int width, int height, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent());
        }
        int clampedWidth = Math.max(capabilities.minImageExtent().width(), Math.min(capabilities.maxImageExtent().width(), width));
        int clampedHeight = Math.max(capabilities.minImageExtent().height(), Math.min(capabilities.maxImageExtent().height(), height));
        return VkExtent2D.calloc(stack).set(clampedWidth, clampedHeight);
    }

    private void uploadSceneMeshes(MemoryStack stack, List<SceneMeshData> sceneMeshes) throws EngineException {
        destroySceneMeshes();
        Map<Path, GpuTexture> textureCache = new HashMap<>();
        GpuTexture defaultTexture = createTextureFromPath(null);
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
            GpuTexture albedoTexture = resolveOrCreateTexture(mesh.albedoTexturePath(), textureCache, defaultTexture);

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
                    albedoTexture
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
        }
        estimatedGpuMemoryBytes = GLOBAL_UNIFORM_BYTES + meshBytes + textureBytes;
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
        }
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
        gpuMeshes.clear();
    }

    private GpuTexture resolveOrCreateTexture(Path texturePath, Map<Path, GpuTexture> cache, GpuTexture defaultTexture) throws EngineException {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return defaultTexture;
        }
        GpuTexture cached = cache.get(texturePath);
        if (cached != null) {
            return cached;
        }
        GpuTexture created = createTextureFromPath(texturePath);
        cache.put(texturePath, created);
        return created;
    }

    private void createTextureDescriptorSets(MemoryStack stack) throws EngineException {
        if (gpuMeshes.isEmpty() || textureDescriptorSetLayout == VK_NULL_HANDLE) {
            return;
        }
        if (textureDescriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, textureDescriptorPool, null);
            textureDescriptorPool = VK_NULL_HANDLE;
        }

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(gpuMeshes.size());

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

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(mesh.albedoTexture.view())
                    .sampler(mesh.albedoTexture.sampler());
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(device, write, null);
        }
    }

    private GpuTexture createTextureFromPath(Path texturePath) throws EngineException {
        TexturePixelData pixels = loadTexturePixels(texturePath);
        if (pixels == null) {
            ByteBuffer data = memAlloc(4);
            data.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
            pixels = new TexturePixelData(data, 1, 1);
        }
        try {
            return createTextureFromPixels(pixels);
        } finally {
            memFree(pixels.data());
        }
    }

    private TexturePixelData loadTexturePixels(Path texturePath) {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(texturePath.toFile());
            if (image == null) {
                return null;
            }
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
        } catch (IOException ignored) {
            return null;
        }
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

                long imageView = createImageView(stack, imageAlloc.image(), VK10.VK_FORMAT_R8G8B8A8_SRGB);
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

    private ImageAlloc createImage(MemoryStack stack, int width, int height, int format, int tiling, int usage, int properties)
            throws EngineException {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK10.VK_IMAGE_TYPE_2D)
                .extent(e -> e.width(width).height(height).depth(1))
                .mipLevels(1)
                .arrayLayers(1)
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

    private long createImageView(MemoryStack stack, long image, int format) throws EngineException {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(format);
        viewInfo.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
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

    private void updateGlobalUniform(GpuMesh mesh) throws EngineException {
        ByteBuffer mapped = memAlloc(GLOBAL_UNIFORM_BYTES);
        mapped.order(ByteOrder.nativeOrder());
        FloatBuffer fb = mapped.asFloatBuffer();
        fb.put(mesh.modelMatrix);
        fb.put(viewMatrix);
        fb.put(projMatrix);
        fb.put(new float[]{mesh.colorR, mesh.colorG, mesh.colorB, 1f});
        fb.put(new float[]{mesh.metallic, mesh.roughness, dirLightIntensity, pointLightIntensity});
        fb.put(new float[]{dirLightDirX, dirLightDirY, dirLightDirZ, 0f});
        fb.put(new float[]{dirLightColorR, dirLightColorG, dirLightColorB, 0f});
        fb.put(new float[]{pointLightPosX, pointLightPosY, pointLightPosZ, 0f});
        fb.put(new float[]{pointLightColorR, pointLightColorG, pointLightColorB, 0f});
        fb.put(new float[]{fogEnabled ? 1f : 0f, fogDensity, 0f, 0f});
        fb.put(new float[]{fogR, fogG, fogB, (float) fogSteps});
        fb.put(new float[]{smokeEnabled ? 1f : 0f, smokeIntensity, 0f, 0f});
        fb.put(new float[]{smokeR, smokeG, smokeB, 0f});
        mapped.limit(GLOBAL_UNIFORM_BYTES);
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, globalUniformMemory, 0, GLOBAL_UNIFORM_BYTES, 0, pData);
            if (mapResult != VK_SUCCESS) {
                throw vkFailure("vkMapMemory", mapResult);
            }
            memCopy(memAddress(mapped), pData.get(0), GLOBAL_UNIFORM_BYTES);
            vkUnmapMemory(device, globalUniformMemory);
        } finally {
            memFree(mapped);
        }
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
        private final float[] modelMatrix;
        private final float colorR;
        private final float colorG;
        private final float colorB;
        private final float metallic;
        private final float roughness;
        private final GpuTexture albedoTexture;
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
                GpuTexture albedoTexture
        ) {
            this.vertexBuffer = vertexBuffer;
            this.vertexMemory = vertexMemory;
            this.indexBuffer = indexBuffer;
            this.indexMemory = indexMemory;
            this.indexCount = indexCount;
            this.vertexBytes = vertexBytes;
            this.indexBytes = indexBytes;
            this.modelMatrix = modelMatrix;
            this.colorR = colorR;
            this.colorG = colorG;
            this.colorB = colorB;
            this.metallic = metallic;
            this.roughness = roughness;
            this.albedoTexture = albedoTexture;
        }
    }

    private record GpuTexture(long image, long memory, long view, long sampler, long bytes) {
    }

    private record ImageAlloc(long image, long memory) {
    }

    private record TexturePixelData(ByteBuffer data, int width, int height) {
    }

    static record SceneMeshData(
            float[] vertices,
            int[] indices,
            float[] modelMatrix,
            float[] color,
            float metallic,
            float roughness,
            Path albedoTexturePath
    ) {
        SceneMeshData {
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
                    null
            );
        }

        static SceneMeshData quad(float[] color, int meshIndex) {
            float offsetX = (meshIndex - 1) * 0.35f;
            return new SceneMeshData(
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
