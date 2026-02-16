package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.bootstrap.VulkanBootstrap;
import org.dynamislight.impl.vulkan.bootstrap.VulkanShutdownCoordinator;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandInputAssembler;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandOrchestrator;
import org.dynamislight.impl.vulkan.command.VulkanFrameSyncLifecycleCoordinator;
import org.dynamislight.impl.vulkan.command.VulkanFrameSubmitCoordinator;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorLifecycleCoordinator;
import org.dynamislight.impl.vulkan.descriptor.VulkanTextureDescriptorSetCoordinator;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanContextProfileCoordinator;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
import org.dynamislight.impl.vulkan.scene.VulkanSceneRuntimeCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanSceneSetPlanner;
import org.dynamislight.impl.vulkan.scene.VulkanSceneTextureCoordinator;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowMatrixCoordinator;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowLifecycleCoordinator;
import org.dynamislight.impl.vulkan.state.VulkanRenderParameterMutator;
import org.dynamislight.impl.vulkan.state.VulkanLightingParameterMutator;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainLifecycleCoordinator;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainRecreateCoordinator;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.dynamislight.impl.vulkan.uniform.VulkanFrameUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanGlobalSceneUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformFrameCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUploadStateTracker;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformUploadRecorder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.dynamislight.impl.vulkan.math.VulkanMath.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

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
    private final VulkanUploadStateTracker uploadState = new VulkanUploadStateTracker(
            DEFAULT_FRAMES_IN_FLIGHT,
            DEFAULT_MAX_PENDING_UPLOAD_RANGES,
            MAX_PENDING_UPLOAD_RANGES_HARD_CAP
    );
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

    VulkanContext() {}

    void configureFrameResources(int framesInFlight, int maxDynamicSceneObjects, int maxPendingUploadRanges) {
        if (device != null) {
            return;
        }
        this.framesInFlight = clamp(framesInFlight, 2, 6);
        this.maxDynamicSceneObjects = clamp(maxDynamicSceneObjects, 256, 8192);
        this.maxPendingUploadRanges = clamp(maxPendingUploadRanges, 8, 2048);
        uploadState.reallocateFrameTracking(this.framesInFlight);
        uploadState.reallocateUploadRangeTracking(this.maxPendingUploadRanges);
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
        window = VulkanBootstrap.initWindow(appName, width, height, windowVisible);
        try (MemoryStack stack = stackPush()) {
            instance = VulkanBootstrap.createInstance(stack, appName);
            surface = VulkanBootstrap.createSurface(instance, window, stack);
            var selection = VulkanBootstrap.selectPhysicalDevice(instance, surface, stack);
            physicalDevice = selection.physicalDevice();
            graphicsQueueFamilyIndex = selection.graphicsQueueFamilyIndex();
            var deviceAndQueue = VulkanBootstrap.createLogicalDevice(physicalDevice, graphicsQueueFamilyIndex, stack);
            device = deviceAndQueue.device();
            graphicsQueue = deviceAndQueue.graphicsQueue();
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
        return VulkanContextProfileCoordinator.sceneReuse(
                new VulkanContextProfileCoordinator.SceneReuseRequest(
                        sceneReuseHitCount,
                        sceneReorderReuseCount,
                        sceneTextureRebindCount,
                        sceneFullRebuildCount,
                        meshBufferRebuildCount,
                        descriptorPoolBuildCount,
                        descriptorPoolRebuildCount
                )
        );
    }

    FrameResourceProfile frameResourceProfile() {
        return VulkanContextProfileCoordinator.frameResource(
                new VulkanContextProfileCoordinator.FrameResourceRequest(
                        framesInFlight,
                        frameDescriptorSets.length,
                        uniformStrideBytes,
                        uniformFrameSpanBytes,
                        globalUniformFrameSpanBytes,
                        maxDynamicSceneObjects,
                        uploadState.pendingSceneDirtyStarts().length,
                        lastFrameGlobalUploadBytes,
                        maxFrameGlobalUploadBytes,
                        lastFrameUniformUploadBytes,
                        maxFrameUniformUploadBytes,
                        lastFrameUniformObjectCount,
                        maxFrameUniformObjectCount,
                        lastFrameUniformUploadRanges,
                        maxFrameUniformUploadRanges,
                        lastFrameUniformUploadStartObject,
                        uploadState.pendingUploadRangeOverflowCount(),
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
                        uploadState.maxObservedDynamicObjects(),
                        objectUniformStagingMappedAddress != 0L && sceneGlobalUniformStagingMappedAddress != 0L
                )
        );
    }

    ShadowCascadeProfile shadowCascadeProfile() {
        return VulkanContextProfileCoordinator.shadowCascade(
                new VulkanContextProfileCoordinator.ShadowRequest(
                        shadowEnabled,
                        shadowCascadeCount,
                        shadowMapResolution,
                        shadowPcfRadius,
                        shadowBias,
                        shadowCascadeSplitNdc
                )
        );
    }

    PostProcessPipelineProfile postProcessPipelineProfile() {
        return VulkanContextProfileCoordinator.postProcess(
                new VulkanContextProfileCoordinator.PostRequest(postOffscreenRequested, postOffscreenActive)
        );
    }

    void setSceneMeshes(List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        VulkanSceneSetPlanner.Plan plan = VulkanSceneSetPlanner.plan(gpuMeshes, sceneMeshes, this::textureCacheKey);
        List<VulkanSceneMeshData> safe = plan.sceneMeshes();
        pendingSceneMeshes = safe;
        if (device == null) {
            return;
        }
        if (plan.action() == VulkanSceneSetPlanner.Action.REUSE_DYNAMIC_ONLY) {
            sceneReuseHitCount++;
            descriptorRingReuseHitCount++;
            var dynamicResult = VulkanSceneRuntimeCoordinator.updateDynamicState(gpuMeshes, safe);
            if (dynamicResult.reordered()) {
                sceneReorderReuseCount++;
            }
            if (dynamicResult.dirtyEnd() >= dynamicResult.dirtyStart()) {
                markSceneStateDirty(dynamicResult.dirtyStart(), dynamicResult.dirtyEnd());
            }
            return;
        }
        if (plan.action() == VulkanSceneSetPlanner.Action.REUSE_GEOMETRY_REBIND_TEXTURES) {
            sceneReuseHitCount++;
            sceneTextureRebindCount++;
            descriptorRingReuseHitCount++;
            var rebindResult = VulkanSceneRuntimeCoordinator.rebind(
                    new VulkanSceneRuntimeCoordinator.RebindRequest(
                            device,
                            safe,
                            gpuMeshes,
                            iblIrradianceTexture,
                            iblRadianceTexture,
                            iblBrdfLutTexture,
                            iblIrradiancePath,
                            iblRadiancePath,
                            iblBrdfLutPath,
                            this::createTextureFromPath,
                            this::resolveOrCreateTexture,
                            this::textureCacheKey,
                            this::refreshTextureDescriptorSets,
                            this::markSceneStateDirty
                    )
            );
            iblIrradianceTexture = rebindResult.iblIrradianceTexture();
            iblRadianceTexture = rebindResult.iblRadianceTexture();
            iblBrdfLutTexture = rebindResult.iblBrdfLutTexture();
            return;
        }
        sceneFullRebuildCount++;
        try (MemoryStack stack = stackPush()) {
            uploadSceneMeshes(stack, safe);
        }
        markSceneStateDirty(0, Math.max(0, safe.size() - 1));
    }

    void setCameraMatrices(float[] view, float[] proj) {
        var result = VulkanRenderParameterMutator.applyCameraMatrices(
                new VulkanRenderParameterMutator.CameraState(viewMatrix, projMatrix),
                new VulkanRenderParameterMutator.CameraUpdate(view, proj)
        );
        viewMatrix = result.state().view();
        projMatrix = result.state().proj();
        if (result.changed()) {
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
        var result = VulkanLightingParameterMutator.applyLighting(
                new VulkanLightingParameterMutator.LightingState(
                        dirLightDirX, dirLightDirY, dirLightDirZ,
                        dirLightColorR, dirLightColorG, dirLightColorB,
                        dirLightIntensity,
                        pointLightPosX, pointLightPosY, pointLightPosZ,
                        pointLightColorR, pointLightColorG, pointLightColorB,
                        pointLightIntensity,
                        pointLightDirX, pointLightDirY, pointLightDirZ,
                        pointLightInnerCos, pointLightOuterCos, pointLightIsSpot,
                        pointShadowFarPlane, pointShadowEnabled
                ),
                new VulkanLightingParameterMutator.LightingUpdate(
                        dirDir, dirColor, dirIntensity,
                        pointPos, pointColor, pointIntensity,
                        pointDirection, pointInnerCos, pointOuterCos, pointIsSpot, pointRange, pointCastsShadows
                )
        );
        var state = result.state();
        dirLightDirX = state.dirLightDirX();
        dirLightDirY = state.dirLightDirY();
        dirLightDirZ = state.dirLightDirZ();
        dirLightColorR = state.dirLightColorR();
        dirLightColorG = state.dirLightColorG();
        dirLightColorB = state.dirLightColorB();
        dirLightIntensity = state.dirLightIntensity();
        pointLightPosX = state.pointLightPosX();
        pointLightPosY = state.pointLightPosY();
        pointLightPosZ = state.pointLightPosZ();
        pointLightColorR = state.pointLightColorR();
        pointLightColorG = state.pointLightColorG();
        pointLightColorB = state.pointLightColorB();
        pointLightIntensity = state.pointLightIntensity();
        pointLightDirX = state.pointLightDirX();
        pointLightDirY = state.pointLightDirY();
        pointLightDirZ = state.pointLightDirZ();
        pointLightInnerCos = state.pointLightInnerCos();
        pointLightOuterCos = state.pointLightOuterCos();
        pointLightIsSpot = state.pointLightIsSpot();
        pointShadowFarPlane = state.pointShadowFarPlane();
        pointShadowEnabled = state.pointShadowEnabled();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setShadowParameters(boolean enabled, float strength, float bias, int pcfRadius, int cascadeCount, int mapResolution)
            throws EngineException {
        var result = VulkanLightingParameterMutator.applyShadow(
                new VulkanLightingParameterMutator.ShadowState(
                        shadowEnabled, shadowStrength, shadowBias, shadowPcfRadius, shadowCascadeCount, shadowMapResolution
                ),
                new VulkanLightingParameterMutator.ShadowUpdate(
                        enabled, strength, bias, pcfRadius, cascadeCount, mapResolution, MAX_SHADOW_MATRICES
                )
        );
        var state = result.state();
        shadowEnabled = state.shadowEnabled();
        shadowStrength = state.shadowStrength();
        shadowBias = state.shadowBias();
        shadowPcfRadius = state.shadowPcfRadius();
        shadowCascadeCount = state.shadowCascadeCount();
        shadowMapResolution = state.shadowMapResolution();
        if (result.resolutionChanged() && device != null) {
            vkDeviceWaitIdle(device);
            try (MemoryStack stack = stackPush()) {
                destroyShadowResources();
                createShadowResources(stack);
                if (!gpuMeshes.isEmpty()) {
                    createTextureDescriptorSets(stack);
                }
            }
        }
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setFogParameters(boolean enabled, float r, float g, float b, float density, int steps) {
        var result = VulkanRenderParameterMutator.applyFog(
                new VulkanRenderParameterMutator.FogState(fogEnabled, fogR, fogG, fogB, fogDensity, fogSteps),
                new VulkanRenderParameterMutator.FogUpdate(enabled, r, g, b, density, steps)
        );
        var state = result.state();
        fogEnabled = state.enabled();
        fogR = state.r();
        fogG = state.g();
        fogB = state.b();
        fogDensity = state.density();
        fogSteps = state.steps();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setSmokeParameters(boolean enabled, float r, float g, float b, float intensity) {
        var result = VulkanRenderParameterMutator.applySmoke(
                new VulkanRenderParameterMutator.SmokeState(smokeEnabled, smokeR, smokeG, smokeB, smokeIntensity),
                new VulkanRenderParameterMutator.SmokeUpdate(enabled, r, g, b, intensity)
        );
        var state = result.state();
        smokeEnabled = state.enabled();
        smokeR = state.r();
        smokeG = state.g();
        smokeB = state.b();
        smokeIntensity = state.intensity();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setIblParameters(boolean enabled, float diffuseStrength, float specularStrength, float prefilterStrength) {
        var result = VulkanRenderParameterMutator.applyIbl(
                new VulkanRenderParameterMutator.IblState(iblEnabled, iblDiffuseStrength, iblSpecularStrength, iblPrefilterStrength),
                new VulkanRenderParameterMutator.IblUpdate(enabled, diffuseStrength, specularStrength, prefilterStrength)
        );
        var state = result.state();
        iblEnabled = state.enabled();
        iblDiffuseStrength = state.diffuseStrength();
        iblSpecularStrength = state.specularStrength();
        iblPrefilterStrength = state.prefilterStrength();
        if (result.changed()) {
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
        var result = VulkanRenderParameterMutator.applyPost(
                new VulkanRenderParameterMutator.PostState(
                        this.tonemapEnabled,
                        tonemapExposure,
                        tonemapGamma,
                        this.bloomEnabled,
                        this.bloomThreshold,
                        this.bloomStrength
                ),
                new VulkanRenderParameterMutator.PostUpdate(
                        tonemapEnabled,
                        exposure,
                        gamma,
                        bloomEnabled,
                        bloomThreshold,
                        bloomStrength
                )
        );
        var state = result.state();
        this.tonemapEnabled = state.tonemapEnabled();
        tonemapExposure = state.exposure();
        tonemapGamma = state.gamma();
        this.bloomEnabled = state.bloomEnabled();
        this.bloomThreshold = state.bloomThreshold();
        this.bloomStrength = state.bloomStrength();
        if (result.changed()) {
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
        VulkanShutdownCoordinator.Result result = VulkanShutdownCoordinator.shutdown(
                new VulkanShutdownCoordinator.Inputs(
                        instance,
                        physicalDevice,
                        device,
                        graphicsQueue,
                        graphicsQueueFamilyIndex,
                        window,
                        surface,
                        commandPool,
                        commandBuffers,
                        imageAvailableSemaphores,
                        renderFinishedSemaphores,
                        renderFences,
                        this::destroySceneMeshes,
                        this::destroyShadowResources,
                        this::destroySwapchainResources,
                        this::destroyDescriptorResources
                )
        );
        instance = result.instance();
        physicalDevice = result.physicalDevice();
        device = result.device();
        graphicsQueue = result.graphicsQueue();
        graphicsQueueFamilyIndex = result.graphicsQueueFamilyIndex();
        window = result.window();
        surface = result.surface();
        commandPool = result.commandPool();
        commandBuffers = result.commandBuffers();
        imageAvailableSemaphores = result.imageAvailableSemaphores();
        renderFinishedSemaphores = result.renderFinishedSemaphores();
        renderFences = result.renderFences();
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
        VulkanDescriptorLifecycleCoordinator.ResetState state = VulkanDescriptorLifecycleCoordinator.destroyAndReset(
                new VulkanDescriptorLifecycleCoordinator.DestroyRequest(
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
                        ),
                        OBJECT_UNIFORM_BYTES,
                        GLOBAL_SCENE_UNIFORM_BYTES
                )
        );
        objectUniformBuffer = state.objectUniformBuffer();
        objectUniformMemory = state.objectUniformMemory();
        objectUniformStagingBuffer = state.objectUniformStagingBuffer();
        objectUniformStagingMemory = state.objectUniformStagingMemory();
        objectUniformStagingMappedAddress = state.objectUniformStagingMappedAddress();
        sceneGlobalUniformBuffer = state.sceneGlobalUniformBuffer();
        sceneGlobalUniformMemory = state.sceneGlobalUniformMemory();
        sceneGlobalUniformStagingBuffer = state.sceneGlobalUniformStagingBuffer();
        sceneGlobalUniformStagingMemory = state.sceneGlobalUniformStagingMemory();
        sceneGlobalUniformStagingMappedAddress = state.sceneGlobalUniformStagingMappedAddress();
        uniformStrideBytes = state.uniformStrideBytes();
        uniformFrameSpanBytes = state.uniformFrameSpanBytes();
        globalUniformFrameSpanBytes = state.globalUniformFrameSpanBytes();
        estimatedGpuMemoryBytes = state.estimatedGpuMemoryBytes();
        frameDescriptorSets = state.frameDescriptorSets();
        descriptorSet = state.descriptorSet();
        descriptorRingSetCapacity = state.descriptorRingSetCapacity();
        descriptorRingPeakSetCapacity = state.descriptorRingPeakSetCapacity();
        descriptorRingActiveSetCount = state.descriptorRingActiveSetCount();
        descriptorRingWasteSetCount = state.descriptorRingWasteSetCount();
        descriptorRingPeakWasteSetCount = state.descriptorRingPeakWasteSetCount();
        descriptorRingCapBypassCount = state.descriptorRingCapBypassCount();
        descriptorRingPoolReuseCount = state.descriptorRingPoolReuseCount();
        descriptorRingPoolResetFailureCount = state.descriptorRingPoolResetFailureCount();
        descriptorPool = state.descriptorPool();
        descriptorSetLayout = state.descriptorSetLayout();
        textureDescriptorSetLayout = state.textureDescriptorSetLayout();
        lastFrameUniformUploadBytes = state.lastFrameUniformUploadBytes();
        maxFrameUniformUploadBytes = state.maxFrameUniformUploadBytes();
        lastFrameGlobalUploadBytes = state.lastFrameGlobalUploadBytes();
        maxFrameGlobalUploadBytes = state.maxFrameGlobalUploadBytes();
        lastFrameUniformObjectCount = state.lastFrameUniformObjectCount();
        maxFrameUniformObjectCount = state.maxFrameUniformObjectCount();
        lastFrameUniformUploadRanges = state.lastFrameUniformUploadRanges();
        maxFrameUniformUploadRanges = state.maxFrameUniformUploadRanges();
        lastFrameUniformUploadStartObject = state.lastFrameUniformUploadStartObject();
        uploadState.reset();
    }

    private void createSwapchainResources(MemoryStack stack, int requestedWidth, int requestedHeight) throws EngineException {
        postIntermediateInitialized = false;
        VulkanSwapchainLifecycleCoordinator.State state = VulkanSwapchainLifecycleCoordinator.create(
                new VulkanSwapchainLifecycleCoordinator.CreateRequest(
                        physicalDevice,
                        device,
                        stack,
                        surface,
                        requestedWidth,
                        requestedHeight,
                        depthFormat,
                        VERTEX_STRIDE_BYTES,
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        postOffscreenRequested
                )
        );
        applySwapchainState(state);
    }

    private void createShadowResources(MemoryStack stack) throws EngineException {
        VulkanShadowLifecycleCoordinator.State state = VulkanShadowLifecycleCoordinator.create(
                new VulkanShadowLifecycleCoordinator.CreateRequest(
                        device,
                        physicalDevice,
                        stack,
                        depthFormat,
                        shadowMapResolution,
                        MAX_SHADOW_MATRICES,
                        VERTEX_STRIDE_BYTES,
                        descriptorSetLayout
                )
        );
        applyShadowState(state);
    }

    private void destroyShadowResources() {
        VulkanShadowLifecycleCoordinator.State state = VulkanShadowLifecycleCoordinator.destroy(
                new VulkanShadowLifecycleCoordinator.DestroyRequest(
                        device,
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
        applyShadowState(state);
    }

    private void applyShadowState(VulkanShadowLifecycleCoordinator.State state) {
        shadowDepthImage = state.shadowDepthImage();
        shadowDepthMemory = state.shadowDepthMemory();
        shadowDepthImageView = state.shadowDepthImageView();
        shadowDepthLayerImageViews = state.shadowDepthLayerImageViews();
        shadowSampler = state.shadowSampler();
        shadowRenderPass = state.shadowRenderPass();
        shadowPipelineLayout = state.shadowPipelineLayout();
        shadowPipeline = state.shadowPipeline();
        shadowFramebuffers = state.shadowFramebuffers();
    }

    private void destroySwapchainResources() {
        VulkanSwapchainLifecycleCoordinator.State state = VulkanSwapchainLifecycleCoordinator.destroy(
                new VulkanSwapchainLifecycleCoordinator.DestroyRequest(
                        device,
                        framebuffers,
                        graphicsPipeline,
                        pipelineLayout,
                        renderPass,
                        swapchainImageViews,
                        depthImages,
                        depthMemories,
                        depthImageViews,
                        swapchain,
                        swapchainImageFormat,
                        swapchainWidth,
                        swapchainHeight,
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
        applySwapchainState(state);
    }

    private void applySwapchainState(VulkanSwapchainLifecycleCoordinator.State state) {
        if (state == null) {
            return;
        }
        swapchain = state.swapchain();
        swapchainImageFormat = state.swapchainImageFormat();
        swapchainWidth = state.swapchainWidth();
        swapchainHeight = state.swapchainHeight();
        swapchainImages = state.swapchainImages();
        swapchainImageViews = state.swapchainImageViews();
        depthImages = state.depthImages();
        depthMemories = state.depthMemories();
        depthImageViews = state.depthImageViews();
        renderPass = state.renderPass();
        pipelineLayout = state.pipelineLayout();
        graphicsPipeline = state.graphicsPipeline();
        framebuffers = state.framebuffers();
        postOffscreenActive = state.postOffscreenActive();
        offscreenColorImage = state.offscreenColorImage();
        offscreenColorMemory = state.offscreenColorMemory();
        offscreenColorImageView = state.offscreenColorImageView();
        offscreenColorSampler = state.offscreenColorSampler();
        postDescriptorSetLayout = state.postDescriptorSetLayout();
        postDescriptorPool = state.postDescriptorPool();
        postDescriptorSet = state.postDescriptorSet();
        postRenderPass = state.postRenderPass();
        postPipelineLayout = state.postPipelineLayout();
        postGraphicsPipeline = state.postGraphicsPipeline();
        postFramebuffers = state.postFramebuffers();
        postIntermediateInitialized = state.postIntermediateInitialized();
    }

    private void createFrameSyncResources(MemoryStack stack) throws EngineException {
        VulkanFrameSyncLifecycleCoordinator.State state = VulkanFrameSyncLifecycleCoordinator.create(
                new VulkanFrameSyncLifecycleCoordinator.CreateRequest(
                        device,
                        stack,
                        graphicsQueueFamilyIndex,
                        framesInFlight
                )
        );
        commandPool = state.commandPool();
        commandBuffers = state.commandBuffers();
        imageAvailableSemaphores = state.imageAvailableSemaphores();
        renderFinishedSemaphores = state.renderFinishedSemaphores();
        renderFences = state.renderFences();
        currentFrame = state.currentFrame();
    }

    private int acquireNextImage(MemoryStack stack, int frameIdx) throws EngineException {
        VkCommandBuffer commandBuffer = commandBuffers[frameIdx];
        return VulkanFrameSubmitCoordinator.acquireRecordSubmitPresent(
                new VulkanFrameSubmitCoordinator.Inputs(
                        stack,
                        device,
                        graphicsQueue,
                        swapchain,
                        commandBuffer,
                        imageAvailableSemaphores[frameIdx],
                        renderFinishedSemaphores[frameIdx],
                        renderFences[frameIdx],
                        imageIndex -> recordCommandBuffer(stack, commandBuffer, imageIndex, frameIdx)
                )
        );
    }

    private void recordCommandBuffer(MemoryStack stack, VkCommandBuffer commandBuffer, int imageIndex, int frameIdx) throws EngineException {
        VulkanFrameCommandOrchestrator.Inputs commandInputs = buildCommandInputs(frameIdx);
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
                commandInputs
        );
    }

    private VulkanFrameCommandOrchestrator.Inputs buildCommandInputs(int frameIdx) {
        return VulkanFrameCommandInputAssembler.build(
                new VulkanFrameCommandInputAssembler.AssemblyInputs(
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
                        frame -> VulkanUniformFrameCoordinator.descriptorSetForFrame(frameDescriptorSets, descriptorSet, frame),
                        meshIndex -> VulkanUniformFrameCoordinator.dynamicUniformOffset(uniformStrideBytes, meshIndex),
                        this::vkFailure
                )
        );
    }

    private void recreateSwapchainFromWindow() throws EngineException {
        VulkanSwapchainRecreateCoordinator.recreateFromWindow(
                window,
                this::recreateSwapchain
        );
    }

    private void recreateSwapchain(int width, int height) throws EngineException {
        VulkanSwapchainRecreateCoordinator.recreate(
                device,
                physicalDevice,
                surface,
                width,
                height,
                this::destroySwapchainResources,
                this::createSwapchainResources
        );
    }

    private EngineException vkFailure(String operation, int result) {
        EngineErrorCode code = result == VK_ERROR_DEVICE_LOST ? EngineErrorCode.DEVICE_LOST : EngineErrorCode.BACKEND_INIT_FAILED;
        return new EngineException(code, operation + " failed: " + result, false);
    }

    private void updateShadowLightViewProjMatrices() {
        VulkanShadowMatrixCoordinator.updateMatrices(
                new VulkanShadowMatrixCoordinator.UpdateInputs(
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

    private void uploadSceneMeshes(MemoryStack stack, List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        var result = VulkanSceneRuntimeCoordinator.upload(
                new VulkanSceneRuntimeCoordinator.UploadRequest(
                        meshBufferRebuildCount,
                        this::destroySceneMeshes,
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
                )
        );
        meshBufferRebuildCount = result.meshBufferRebuildCount();
        iblIrradianceTexture = result.iblIrradianceTexture();
        iblRadianceTexture = result.iblRadianceTexture();
        iblBrdfLutTexture = result.iblBrdfLutTexture();
        estimatedGpuMemoryBytes = result.estimatedGpuMemoryBytes();
    }

    private void destroySceneMeshes() {
        var destroyResult = VulkanSceneRuntimeCoordinator.destroy(
                device,
                gpuMeshes,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture,
                textureDescriptorPool
        );
        textureDescriptorPool = destroyResult.textureDescriptorPool();
        iblIrradianceTexture = null;
        iblRadianceTexture = null;
        iblBrdfLutTexture = null;
    }

    private VulkanGpuTexture resolveOrCreateTexture(
            Path texturePath,
            Map<String, VulkanGpuTexture> cache,
            VulkanGpuTexture defaultTexture,
            boolean normalMap
    )
            throws EngineException {
        return VulkanSceneTextureCoordinator.resolveOrCreateTexture(
                texturePath,
                cache,
                defaultTexture,
                normalMap,
                this::createTextureFromPath
        );
    }

    private String textureCacheKey(Path texturePath, boolean normalMap) {
        return VulkanSceneTextureCoordinator.textureCacheKey(texturePath, normalMap);
    }

    private void createTextureDescriptorSets(MemoryStack stack) throws EngineException {
        VulkanTextureDescriptorSetCoordinator.Result state = VulkanSceneTextureCoordinator.createTextureDescriptorSets(
                new VulkanSceneTextureCoordinator.CreateInputs(
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
                        descriptorRingMaxSetCapacity,
                        shadowDepthImageView,
                        shadowSampler,
                        iblIrradianceTexture,
                        iblRadianceTexture,
                        iblBrdfLutTexture
                )
        );
        if (state == null) {
            return;
        }
        applyTextureDescriptorCoordinatorState(state);
    }

    private void applyTextureDescriptorCoordinatorState(VulkanTextureDescriptorSetCoordinator.Result state) {
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
        descriptorRingCapBypassCount += state.descriptorRingCapBypassCountIncrement();
    }

    private VulkanGpuTexture createTextureFromPath(Path texturePath, boolean normalMap) throws EngineException {
        return VulkanTextureResourceOps.createTextureFromPath(
                texturePath,
                normalMap,
                new VulkanTextureResourceOps.Context(device, physicalDevice, commandPool, graphicsQueue, this::vkFailure)
        );
    }

    private void refreshTextureDescriptorSets() throws EngineException {
        try (MemoryStack stack = stackPush()) {
            createTextureDescriptorSets(stack);
        }
    }

    private void markGlobalStateDirty() { uploadState.markGlobalStateDirty(); }

    private void markSceneStateDirty(int dirtyStart, int dirtyEnd) {
        if (dirtyEnd < dirtyStart) {
            return;
        }
        uploadState.markSceneStateDirty(dirtyStart, dirtyEnd, dynamicUploadMergeGapObjects);
    }

    private void prepareFrameUniforms(int frameIdx) throws EngineException {
        VulkanFrameUniformCoordinator.Result result = VulkanUniformFrameCoordinator.prepare(
                new VulkanUniformFrameCoordinator.PrepareRequest(
                        frameIdx,
                        gpuMeshes.size(),
                        uploadState.maxObservedDynamicObjects(),
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
                        uploadState.globalStateRevision(),
                        uploadState.sceneStateRevision(),
                        uploadState.frameGlobalRevisionApplied(),
                        uploadState.frameSceneRevisionApplied(),
                        uploadState.pendingSceneDirtyRangeCount(),
                        uploadState.pendingSceneDirtyStarts(),
                        uploadState.pendingSceneDirtyEnds(),
                        uploadState.pendingUploadSrcOffsets(),
                        uploadState.pendingUploadDstOffsets(),
                        uploadState.pendingUploadByteCounts(),
                        idx -> gpuMeshes.isEmpty() ? null : gpuMeshes.get(idx),
                        VulkanGlobalSceneUniformCoordinator.build(
                                new VulkanGlobalSceneUniformCoordinator.BuildRequest(
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
                                )
                        ),
                        this::vkFailure
                )
        );
        uploadState.applyPrepareResult(result);
    }

    private void uploadFrameUniforms(VkCommandBuffer commandBuffer, int frameIdx) {
        VulkanUniformUploadRecorder.UploadStats stats = VulkanUniformFrameCoordinator.upload(
                new VulkanUniformFrameCoordinator.UploadRequest(
                        commandBuffer,
                        sceneGlobalUniformStagingBuffer,
                        sceneGlobalUniformBuffer,
                        objectUniformStagingBuffer,
                        objectUniformBuffer,
                        uploadState.pendingGlobalUploadSrcOffset(),
                        uploadState.pendingGlobalUploadDstOffset(),
                        uploadState.pendingGlobalUploadByteCount(),
                        uploadState.pendingUploadObjectCount(),
                        uploadState.pendingUploadStartObject(),
                        uploadState.pendingUploadSrcOffsets(),
                        uploadState.pendingUploadDstOffsets(),
                        uploadState.pendingUploadByteCounts(),
                        uploadState.pendingUploadRangeCount()
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
        uploadState.clearPendingUploads();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
