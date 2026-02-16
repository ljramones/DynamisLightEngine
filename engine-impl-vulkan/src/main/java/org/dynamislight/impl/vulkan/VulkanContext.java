package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandInputAssembler;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandOrchestrator;
import org.dynamislight.impl.vulkan.command.VulkanFrameSubmitCoordinator;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorLifecycleCoordinator;
import org.dynamislight.impl.vulkan.descriptor.VulkanTextureDescriptorSetCoordinator;
import org.dynamislight.impl.vulkan.lifecycle.VulkanLifecycleOrchestrator;
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
import org.dynamislight.impl.vulkan.scene.VulkanSceneMeshCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanSceneSetPlanner;
import org.dynamislight.impl.vulkan.scene.VulkanSceneTextureCoordinator;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowMatrixCoordinator;
import org.dynamislight.impl.vulkan.state.VulkanFrameUploadStats;
import org.dynamislight.impl.vulkan.state.VulkanIblState;
import org.dynamislight.impl.vulkan.state.VulkanRenderParameterMutator;
import org.dynamislight.impl.vulkan.state.VulkanLightingParameterMutator;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanSceneResourceState;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainRecreateCoordinator;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.dynamislight.impl.vulkan.uniform.VulkanFrameUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanGlobalSceneUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformFrameCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformUploadCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUploadStateTracker;
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
    private static final int GLOBAL_SCENE_UNIFORM_BYTES = 800;
    private static final int OBJECT_UNIFORM_BYTES = 96;
    private final VulkanBackendResources backendResources = new VulkanBackendResources();
    private final VulkanDescriptorResourceState descriptorResources = new VulkanDescriptorResourceState();
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
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private final VulkanSceneResourceState sceneResources = new VulkanSceneResourceState();
    private final VulkanDescriptorRingStats descriptorRingStats = new VulkanDescriptorRingStats();
    private long estimatedGpuMemoryBytes;
    private final VulkanFrameUploadStats frameUploadStats = new VulkanFrameUploadStats();
    private float[] viewMatrix = identityMatrix();
    private float[] projMatrix = identityMatrix();
    private float[] projBaseMatrix = identityMatrix();
    private int taaJitterFrameIndex;
    private final VulkanRenderState renderState = new VulkanRenderState();
    private VulkanLightingParameterMutator.LightingState lightingState = new VulkanLightingParameterMutator.LightingState(
            0.35f, -1.0f, 0.25f,
            1.0f, 0.98f, 0.95f,
            1.0f,
            0.0f, 1.2f, 1.8f,
            0.95f, 0.62f, 0.22f,
            1.0f,
            0.0f, -1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f,
            15f,
            false
    );
    private final VulkanIblState iblState = new VulkanIblState();

    VulkanContext() {}

    void configureFrameResources(int framesInFlight, int maxDynamicSceneObjects, int maxPendingUploadRanges) {
        if (backendResources.device != null) {
            return;
        }
        this.framesInFlight = clamp(framesInFlight, 2, 6);
        this.maxDynamicSceneObjects = clamp(maxDynamicSceneObjects, 256, 8192);
        this.maxPendingUploadRanges = clamp(maxPendingUploadRanges, 8, 2048);
        uploadState.reallocateFrameTracking(this.framesInFlight);
        uploadState.reallocateUploadRangeTracking(this.maxPendingUploadRanges);
    }

    void configureDynamicUploadMergeGap(int mergeGapObjects) {
        if (backendResources.device != null) {
            return;
        }
        dynamicUploadMergeGapObjects = clamp(mergeGapObjects, 0, 32);
    }

    void configureDynamicObjectSoftLimit(int softLimit) {
        if (backendResources.device != null) {
            return;
        }
        dynamicObjectSoftLimit = clamp(softLimit, 128, 8192);
    }

    void configureDescriptorRing(int maxSetCapacity) {
        if (backendResources.device != null) {
            return;
        }
        descriptorRingStats.descriptorRingMaxSetCapacity = clamp(maxSetCapacity, 256, 32768);
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
        return descriptorRingStats.descriptorRingMaxSetCapacity;
    }

    void initialize(String appName, int width, int height, boolean windowVisible) throws EngineException {
        VulkanLifecycleOrchestrator.initializeRuntime(
                new VulkanLifecycleOrchestrator.InitializeRequest(
                        appName,
                        width,
                        height,
                        windowVisible,
                        backendResources,
                        sceneResources.pendingSceneMeshes,
                        this::createDescriptorResources,
                        stack -> createSwapchainResources(stack, width, height),
                        this::createFrameSyncResources,
                        this::createShadowResources,
                        this::uploadSceneMeshes
                )
        );
    }

    VulkanFrameMetrics renderFrame() throws EngineException {
        long start = System.nanoTime();
        updateTemporalJitterState();
        if (backendResources.device != null && backendResources.graphicsQueue != null && backendResources.commandBuffers.length > 0 && backendResources.swapchain != VK_NULL_HANDLE) {
            try (MemoryStack stack = stackPush()) {
                int frameIdx = backendResources.currentFrame % backendResources.commandBuffers.length;
                int acquireResult = acquireNextImage(stack, frameIdx);
                if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR || acquireResult == VK_SUBOPTIMAL_KHR) {
                    recreateSwapchainFromWindow();
                }
                backendResources.currentFrame = (backendResources.currentFrame + 1) % Math.max(1, backendResources.commandBuffers.length);
            }
        }
        double cpuMs = (System.nanoTime() - start) / 1_000_000.0;
        return new VulkanFrameMetrics(cpuMs, cpuMs * 0.7, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, estimatedGpuMemoryBytes);
    }

    void resize(int width, int height) throws EngineException {
        if (backendResources.device == null || backendResources.swapchain == VK_NULL_HANDLE) {
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
                        sceneResources.sceneReuseHitCount,
                        sceneResources.sceneReorderReuseCount,
                        sceneResources.sceneTextureRebindCount,
                        sceneResources.sceneFullRebuildCount,
                        sceneResources.meshBufferRebuildCount,
                        descriptorRingStats.descriptorPoolBuildCount,
                        descriptorRingStats.descriptorPoolRebuildCount
                )
        );
    }

    FrameResourceProfile frameResourceProfile() {
        return VulkanContextProfileCoordinator.frameResource(
                new VulkanContextProfileCoordinator.FrameResourceRequest(
                        framesInFlight,
                        descriptorResources.frameDescriptorSets.length,
                        descriptorResources.uniformStrideBytes,
                        descriptorResources.uniformFrameSpanBytes,
                        descriptorResources.globalUniformFrameSpanBytes,
                        maxDynamicSceneObjects,
                        uploadState.pendingSceneDirtyStarts().length,
                        frameUploadStats.lastGlobalUploadBytes,
                        frameUploadStats.maxGlobalUploadBytes,
                        frameUploadStats.lastUniformUploadBytes,
                        frameUploadStats.maxUniformUploadBytes,
                        frameUploadStats.lastUniformObjectCount,
                        frameUploadStats.maxUniformObjectCount,
                        frameUploadStats.lastUniformUploadRanges,
                        frameUploadStats.maxUniformUploadRanges,
                        frameUploadStats.lastUniformUploadStartObject,
                        uploadState.pendingUploadRangeOverflowCount(),
                        descriptorRingStats.descriptorRingSetCapacity,
                        descriptorRingStats.descriptorRingPeakSetCapacity,
                        descriptorRingStats.descriptorRingActiveSetCount,
                        descriptorRingStats.descriptorRingWasteSetCount,
                        descriptorRingStats.descriptorRingPeakWasteSetCount,
                        descriptorRingStats.descriptorRingMaxSetCapacity,
                        descriptorRingStats.descriptorRingReuseHitCount,
                        descriptorRingStats.descriptorRingGrowthRebuildCount,
                        descriptorRingStats.descriptorRingSteadyRebuildCount,
                        descriptorRingStats.descriptorRingPoolReuseCount,
                        descriptorRingStats.descriptorRingPoolResetFailureCount,
                        descriptorRingStats.descriptorRingCapBypassCount,
                        dynamicUploadMergeGapObjects,
                        dynamicObjectSoftLimit,
                        uploadState.maxObservedDynamicObjects(),
                        descriptorResources.objectUniformStagingMappedAddress != 0L
                                && descriptorResources.sceneGlobalUniformStagingMappedAddress != 0L
                )
        );
    }

    ShadowCascadeProfile shadowCascadeProfile() {
        return VulkanContextProfileCoordinator.shadowCascade(
                new VulkanContextProfileCoordinator.ShadowRequest(
                        renderState.shadowEnabled,
                        renderState.shadowCascadeCount,
                        renderState.shadowMapResolution,
                        renderState.shadowPcfRadius,
                        renderState.shadowBias,
                        renderState.shadowCascadeSplitNdc
                )
        );
    }

    PostProcessPipelineProfile postProcessPipelineProfile() {
        return VulkanContextProfileCoordinator.postProcess(
                new VulkanContextProfileCoordinator.PostRequest(renderState.postOffscreenRequested, renderState.postOffscreenActive)
        );
    }

    void setSceneMeshes(List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        var result = VulkanSceneMeshCoordinator.setSceneMeshes(
                new VulkanSceneMeshCoordinator.SetSceneRequest(
                        sceneMeshes,
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        framesInFlight,
                        estimatedGpuMemoryBytes,
                        this::createTextureFromPath,
                        this::resolveOrCreateTexture,
                        this::textureCacheKey,
                        this::markSceneStateDirty,
                        this::vkFailure
                )
        );
        estimatedGpuMemoryBytes = result.estimatedGpuMemoryBytes();
    }

    void setCameraMatrices(float[] view, float[] proj) {
        var result = VulkanRenderParameterMutator.applyCameraMatrices(
                new VulkanRenderParameterMutator.CameraState(viewMatrix, projMatrix),
                new VulkanRenderParameterMutator.CameraUpdate(view, proj)
        );
        viewMatrix = result.state().view();
        if (proj != null && proj.length == 16) {
            projBaseMatrix = proj.clone();
            projMatrix = applyProjectionJitter(projBaseMatrix, renderState.taaJitterNdcX, renderState.taaJitterNdcY);
            renderState.postTaaHistoryInitialized = false;
        } else {
            projMatrix = result.state().proj();
        }
        if (result.changed() || proj != null) {
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
                lightingState,
                new VulkanLightingParameterMutator.LightingUpdate(
                        dirDir, dirColor, dirIntensity,
                        pointPos, pointColor, pointIntensity,
                        pointDirection, pointInnerCos, pointOuterCos, pointIsSpot, pointRange, pointCastsShadows
                )
        );
        lightingState = result.state();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setShadowParameters(boolean enabled, float strength, float bias, int pcfRadius, int cascadeCount, int mapResolution)
            throws EngineException {
        var result = VulkanLightingParameterMutator.applyShadow(
                new VulkanLightingParameterMutator.ShadowState(
                        renderState.shadowEnabled, renderState.shadowStrength, renderState.shadowBias, renderState.shadowPcfRadius, renderState.shadowCascadeCount, renderState.shadowMapResolution
                ),
                new VulkanLightingParameterMutator.ShadowUpdate(
                        enabled, strength, bias, pcfRadius, cascadeCount, mapResolution, MAX_SHADOW_MATRICES
                )
        );
        var state = result.state();
        renderState.shadowEnabled = state.shadowEnabled();
        renderState.shadowStrength = state.shadowStrength();
        renderState.shadowBias = state.shadowBias();
        renderState.shadowPcfRadius = state.shadowPcfRadius();
        renderState.shadowCascadeCount = state.shadowCascadeCount();
        renderState.shadowMapResolution = state.shadowMapResolution();
        if (result.resolutionChanged() && backendResources.device != null) {
            vkDeviceWaitIdle(backendResources.device);
            try (MemoryStack stack = stackPush()) {
                destroyShadowResources();
                createShadowResources(stack);
                if (!sceneResources.gpuMeshes.isEmpty()) {
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
                new VulkanRenderParameterMutator.FogState(renderState.fogEnabled, renderState.fogR, renderState.fogG, renderState.fogB, renderState.fogDensity, renderState.fogSteps),
                new VulkanRenderParameterMutator.FogUpdate(enabled, r, g, b, density, steps)
        );
        var state = result.state();
        renderState.fogEnabled = state.enabled();
        renderState.fogR = state.r();
        renderState.fogG = state.g();
        renderState.fogB = state.b();
        renderState.fogDensity = state.density();
        renderState.fogSteps = state.steps();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setSmokeParameters(boolean enabled, float r, float g, float b, float intensity) {
        var result = VulkanRenderParameterMutator.applySmoke(
                new VulkanRenderParameterMutator.SmokeState(renderState.smokeEnabled, renderState.smokeR, renderState.smokeG, renderState.smokeB, renderState.smokeIntensity),
                new VulkanRenderParameterMutator.SmokeUpdate(enabled, r, g, b, intensity)
        );
        var state = result.state();
        renderState.smokeEnabled = state.enabled();
        renderState.smokeR = state.r();
        renderState.smokeG = state.g();
        renderState.smokeB = state.b();
        renderState.smokeIntensity = state.intensity();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setIblParameters(boolean enabled, float diffuseStrength, float specularStrength, float prefilterStrength) {
        var result = VulkanRenderParameterMutator.applyIbl(
                new VulkanRenderParameterMutator.IblState(iblState.enabled, iblState.diffuseStrength, iblState.specularStrength, iblState.prefilterStrength),
                new VulkanRenderParameterMutator.IblUpdate(enabled, diffuseStrength, specularStrength, prefilterStrength)
        );
        var state = result.state();
        iblState.enabled = state.enabled();
        iblState.diffuseStrength = state.diffuseStrength();
        iblState.specularStrength = state.specularStrength();
        iblState.prefilterStrength = state.prefilterStrength();
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setIblTexturePaths(Path irradiancePath, Path radiancePath, Path brdfLutPath) {
        iblState.irradiancePath = irradiancePath;
        iblState.radiancePath = radiancePath;
        iblState.brdfLutPath = brdfLutPath;
    }

    void setPostProcessParameters(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            boolean ssaoEnabled,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend
    ) {
        var result = VulkanRenderParameterMutator.applyPost(
                new VulkanRenderParameterMutator.PostState(
                        this.renderState.tonemapEnabled,
                        renderState.tonemapExposure,
                        renderState.tonemapGamma,
                        this.renderState.bloomEnabled,
                        this.renderState.bloomThreshold,
                        this.renderState.bloomStrength,
                        this.renderState.ssaoEnabled,
                        this.renderState.ssaoStrength,
                        this.renderState.ssaoRadius,
                        this.renderState.ssaoBias,
                        this.renderState.ssaoPower,
                        this.renderState.smaaEnabled,
                        this.renderState.smaaStrength,
                        this.renderState.taaEnabled,
                        this.renderState.taaBlend
                ),
                new VulkanRenderParameterMutator.PostUpdate(
                        tonemapEnabled,
                        exposure,
                        gamma,
                        bloomEnabled,
                        bloomThreshold,
                        bloomStrength,
                        ssaoEnabled,
                        ssaoStrength,
                        ssaoRadius,
                        ssaoBias,
                        ssaoPower,
                        smaaEnabled,
                        smaaStrength,
                        taaEnabled,
                        taaBlend
                )
        );
        var state = result.state();
        this.renderState.tonemapEnabled = state.tonemapEnabled();
        renderState.tonemapExposure = state.exposure();
        renderState.tonemapGamma = state.gamma();
        this.renderState.bloomEnabled = state.bloomEnabled();
        this.renderState.bloomThreshold = state.bloomThreshold();
        this.renderState.bloomStrength = state.bloomStrength();
        this.renderState.ssaoEnabled = state.ssaoEnabled();
        this.renderState.ssaoStrength = state.ssaoStrength();
        this.renderState.ssaoRadius = state.ssaoRadius();
        this.renderState.ssaoBias = state.ssaoBias();
        this.renderState.ssaoPower = state.ssaoPower();
        this.renderState.smaaEnabled = state.smaaEnabled();
        this.renderState.smaaStrength = state.smaaStrength();
        this.renderState.taaEnabled = state.taaEnabled();
        this.renderState.taaBlend = state.taaBlend();
        if (!this.renderState.taaEnabled) {
            this.renderState.postTaaHistoryInitialized = false;
            resetTemporalJitterState();
            projMatrix = projBaseMatrix.clone();
        }
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void configurePostProcessMode(boolean requestOffscreen) {
        boolean changed = renderState.postOffscreenRequested != requestOffscreen;
        renderState.postOffscreenRequested = requestOffscreen;
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void shutdown() {
        var result = VulkanLifecycleOrchestrator.shutdown(
                new VulkanLifecycleOrchestrator.ShutdownRequest(
                        backendResources,
                        this::destroySceneMeshes,
                        this::destroyShadowResources,
                        this::destroySwapchainResources,
                        this::destroyDescriptorResources
                )
        );
        VulkanLifecycleOrchestrator.applyShutdownState(backendResources, result);
    }

    private void createDescriptorResources(MemoryStack stack) throws EngineException {
        VulkanDescriptorResources.Allocation allocation = VulkanDescriptorResources.create(
                backendResources.device,
                backendResources.physicalDevice,
                stack,
                framesInFlight,
                maxDynamicSceneObjects,
                OBJECT_UNIFORM_BYTES,
                GLOBAL_SCENE_UNIFORM_BYTES
        );
        descriptorResources.descriptorSetLayout = allocation.descriptorSetLayout();
        descriptorResources.textureDescriptorSetLayout = allocation.textureDescriptorSetLayout();
        descriptorResources.descriptorPool = allocation.descriptorPool();
        descriptorResources.frameDescriptorSets = allocation.frameDescriptorSets();
        descriptorResources.descriptorSet = descriptorResources.frameDescriptorSets[0];
        descriptorResources.objectUniformBuffer = allocation.objectUniformBuffer();
        descriptorResources.objectUniformMemory = allocation.objectUniformMemory();
        descriptorResources.objectUniformStagingBuffer = allocation.objectUniformStagingBuffer();
        descriptorResources.objectUniformStagingMemory = allocation.objectUniformStagingMemory();
        descriptorResources.objectUniformStagingMappedAddress = allocation.objectUniformStagingMappedAddress();
        descriptorResources.sceneGlobalUniformBuffer = allocation.sceneGlobalUniformBuffer();
        descriptorResources.sceneGlobalUniformMemory = allocation.sceneGlobalUniformMemory();
        descriptorResources.sceneGlobalUniformStagingBuffer = allocation.sceneGlobalUniformStagingBuffer();
        descriptorResources.sceneGlobalUniformStagingMemory = allocation.sceneGlobalUniformStagingMemory();
        descriptorResources.sceneGlobalUniformStagingMappedAddress = allocation.sceneGlobalUniformStagingMappedAddress();
        descriptorResources.uniformStrideBytes = allocation.uniformStrideBytes();
        descriptorResources.uniformFrameSpanBytes = allocation.uniformFrameSpanBytes();
        descriptorResources.globalUniformFrameSpanBytes = allocation.globalUniformFrameSpanBytes();
        estimatedGpuMemoryBytes = allocation.estimatedGpuMemoryBytes();
    }

    private void destroyDescriptorResources() {
        if (backendResources.device == null) {
            return;
        }
        VulkanDescriptorLifecycleCoordinator.ResetState state = VulkanDescriptorLifecycleCoordinator.destroyAndReset(
                new VulkanDescriptorLifecycleCoordinator.DestroyRequest(
                        backendResources.device,
                        new VulkanDescriptorResources.Allocation(
                                descriptorResources.descriptorSetLayout,
                                descriptorResources.textureDescriptorSetLayout,
                                descriptorResources.descriptorPool,
                                descriptorResources.frameDescriptorSets,
                                descriptorResources.objectUniformBuffer,
                                descriptorResources.objectUniformMemory,
                                descriptorResources.objectUniformStagingBuffer,
                                descriptorResources.objectUniformStagingMemory,
                                descriptorResources.objectUniformStagingMappedAddress,
                                descriptorResources.sceneGlobalUniformBuffer,
                                descriptorResources.sceneGlobalUniformMemory,
                                descriptorResources.sceneGlobalUniformStagingBuffer,
                                descriptorResources.sceneGlobalUniformStagingMemory,
                                descriptorResources.sceneGlobalUniformStagingMappedAddress,
                                descriptorResources.uniformStrideBytes,
                                descriptorResources.uniformFrameSpanBytes,
                                descriptorResources.globalUniformFrameSpanBytes,
                                estimatedGpuMemoryBytes
                        ),
                        OBJECT_UNIFORM_BYTES,
                        GLOBAL_SCENE_UNIFORM_BYTES
                )
        );
        descriptorResources.objectUniformBuffer = state.objectUniformBuffer();
        descriptorResources.objectUniformMemory = state.objectUniformMemory();
        descriptorResources.objectUniformStagingBuffer = state.objectUniformStagingBuffer();
        descriptorResources.objectUniformStagingMemory = state.objectUniformStagingMemory();
        descriptorResources.objectUniformStagingMappedAddress = state.objectUniformStagingMappedAddress();
        descriptorResources.sceneGlobalUniformBuffer = state.sceneGlobalUniformBuffer();
        descriptorResources.sceneGlobalUniformMemory = state.sceneGlobalUniformMemory();
        descriptorResources.sceneGlobalUniformStagingBuffer = state.sceneGlobalUniformStagingBuffer();
        descriptorResources.sceneGlobalUniformStagingMemory = state.sceneGlobalUniformStagingMemory();
        descriptorResources.sceneGlobalUniformStagingMappedAddress = state.sceneGlobalUniformStagingMappedAddress();
        descriptorResources.uniformStrideBytes = state.uniformStrideBytes();
        descriptorResources.uniformFrameSpanBytes = state.uniformFrameSpanBytes();
        descriptorResources.globalUniformFrameSpanBytes = state.globalUniformFrameSpanBytes();
        estimatedGpuMemoryBytes = state.estimatedGpuMemoryBytes();
        descriptorResources.frameDescriptorSets = state.frameDescriptorSets();
        descriptorResources.descriptorSet = state.descriptorSet();
        descriptorRingStats.descriptorRingSetCapacity = state.descriptorRingSetCapacity();
        descriptorRingStats.descriptorRingPeakSetCapacity = state.descriptorRingPeakSetCapacity();
        descriptorRingStats.descriptorRingActiveSetCount = state.descriptorRingActiveSetCount();
        descriptorRingStats.descriptorRingWasteSetCount = state.descriptorRingWasteSetCount();
        descriptorRingStats.descriptorRingPeakWasteSetCount = state.descriptorRingPeakWasteSetCount();
        descriptorRingStats.descriptorRingCapBypassCount = state.descriptorRingCapBypassCount();
        descriptorRingStats.descriptorRingPoolReuseCount = state.descriptorRingPoolReuseCount();
        descriptorRingStats.descriptorRingPoolResetFailureCount = state.descriptorRingPoolResetFailureCount();
        descriptorResources.descriptorPool = state.descriptorPool();
        descriptorResources.descriptorSetLayout = state.descriptorSetLayout();
        descriptorResources.textureDescriptorSetLayout = state.textureDescriptorSetLayout();
        frameUploadStats.lastUniformUploadBytes = state.lastFrameUniformUploadBytes();
        frameUploadStats.maxUniformUploadBytes = state.maxFrameUniformUploadBytes();
        frameUploadStats.lastGlobalUploadBytes = state.lastFrameGlobalUploadBytes();
        frameUploadStats.maxGlobalUploadBytes = state.maxFrameGlobalUploadBytes();
        frameUploadStats.lastUniformObjectCount = state.lastFrameUniformObjectCount();
        frameUploadStats.maxUniformObjectCount = state.maxFrameUniformObjectCount();
        frameUploadStats.lastUniformUploadRanges = state.lastFrameUniformUploadRanges();
        frameUploadStats.maxUniformUploadRanges = state.maxFrameUniformUploadRanges();
        frameUploadStats.lastUniformUploadStartObject = state.lastFrameUniformUploadStartObject();
        uploadState.reset();
    }

    private void createSwapchainResources(MemoryStack stack, int requestedWidth, int requestedHeight) throws EngineException {
        renderState.postIntermediateInitialized = false;
        var state = VulkanLifecycleOrchestrator.createSwapchain(
                new VulkanLifecycleOrchestrator.CreateSwapchainRequest(
                        backendResources,
                        descriptorResources,
                        renderState,
                        stack,
                        requestedWidth,
                        requestedHeight,
                        VERTEX_STRIDE_BYTES
                )
        );
        VulkanLifecycleOrchestrator.applySwapchainState(backendResources, renderState, state);
    }

    private void createShadowResources(MemoryStack stack) throws EngineException {
        var state = VulkanLifecycleOrchestrator.createShadow(
                new VulkanLifecycleOrchestrator.CreateShadowRequest(
                        backendResources,
                        descriptorResources,
                        renderState,
                        stack,
                        MAX_SHADOW_MATRICES,
                        VERTEX_STRIDE_BYTES
                )
        );
        VulkanLifecycleOrchestrator.applyShadowState(backendResources, state);
    }

    private void destroyShadowResources() {
        var state = VulkanLifecycleOrchestrator.destroyShadow(backendResources);
        VulkanLifecycleOrchestrator.applyShadowState(backendResources, state);
    }

    private void destroySwapchainResources() {
        var state = VulkanLifecycleOrchestrator.destroySwapchain(
                new VulkanLifecycleOrchestrator.DestroySwapchainRequest(backendResources)
        );
        VulkanLifecycleOrchestrator.applySwapchainState(backendResources, renderState, state);
    }

    private void createFrameSyncResources(MemoryStack stack) throws EngineException {
        var state = VulkanLifecycleOrchestrator.createFrameSync(
                new VulkanLifecycleOrchestrator.CreateFrameSyncRequest(
                        backendResources,
                        stack,
                        framesInFlight
                )
        );
        VulkanLifecycleOrchestrator.applyFrameSyncState(backendResources, state);
    }

    private int acquireNextImage(MemoryStack stack, int frameIdx) throws EngineException {
        VkCommandBuffer commandBuffer = backendResources.commandBuffers[frameIdx];
        return VulkanFrameSubmitCoordinator.acquireRecordSubmitPresent(
                new VulkanFrameSubmitCoordinator.Inputs(
                        stack,
                        backendResources.device,
                        backendResources.graphicsQueue,
                        backendResources.swapchain,
                        commandBuffer,
                        backendResources.imageAvailableSemaphores[frameIdx],
                        backendResources.renderFinishedSemaphores[frameIdx],
                        backendResources.renderFences[frameIdx],
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
                        () -> uploadFrameUniforms(commandBuffer),
                        value -> renderState.postIntermediateInitialized = value,
                        value -> renderState.postTaaHistoryInitialized = value
                ),
                commandInputs
        );
    }

    private VulkanFrameCommandOrchestrator.Inputs buildCommandInputs(int frameIdx) {
        return VulkanFrameCommandInputAssembler.build(
                new VulkanFrameCommandInputAssembler.AssemblyInputs(
                        sceneResources.gpuMeshes,
                        maxDynamicSceneObjects,
                        backendResources.swapchainWidth,
                        backendResources.swapchainHeight,
                        renderState.shadowMapResolution,
                        renderState.shadowEnabled,
                        lightingState.pointShadowEnabled(),
                        renderState.shadowCascadeCount,
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES,
                        backendResources.renderPass,
                        backendResources.framebuffers,
                        backendResources.graphicsPipeline,
                        backendResources.pipelineLayout,
                        backendResources.shadowRenderPass,
                        backendResources.shadowPipeline,
                        backendResources.shadowPipelineLayout,
                        backendResources.shadowFramebuffers,
                        renderState.postOffscreenActive,
                        renderState.postIntermediateInitialized,
                        renderState.tonemapEnabled,
                        renderState.tonemapExposure,
                        renderState.tonemapGamma,
                        renderState.ssaoEnabled,
                        renderState.bloomEnabled,
                        renderState.bloomThreshold,
                        renderState.bloomStrength,
                        renderState.ssaoStrength,
                        renderState.ssaoRadius,
                        renderState.ssaoBias,
                        renderState.ssaoPower,
                        renderState.smaaEnabled,
                        renderState.smaaStrength,
                        renderState.taaEnabled,
                        renderState.taaBlend,
                        renderState.postTaaHistoryInitialized,
                        taaJitterUvDeltaX(),
                        taaJitterUvDeltaY(),
                        backendResources.postRenderPass,
                        backendResources.postGraphicsPipeline,
                        backendResources.postPipelineLayout,
                        backendResources.postDescriptorSet,
                        backendResources.offscreenColorImage,
                        backendResources.postTaaHistoryImage,
                        backendResources.swapchainImages,
                        backendResources.postFramebuffers,
                        frame -> VulkanUniformFrameCoordinator.descriptorSetForFrame(
                                descriptorResources.frameDescriptorSets, descriptorResources.descriptorSet, frame
                        ),
                        meshIndex -> VulkanUniformFrameCoordinator.dynamicUniformOffset(descriptorResources.uniformStrideBytes, meshIndex),
                        this::vkFailure
                )
        );
    }

    private void recreateSwapchainFromWindow() throws EngineException {
        VulkanSwapchainRecreateCoordinator.recreateFromWindow(
                backendResources.window,
                this::recreateSwapchain
        );
    }

    private void recreateSwapchain(int width, int height) throws EngineException {
        VulkanSwapchainRecreateCoordinator.recreate(
                backendResources.device,
                backendResources.physicalDevice,
                backendResources.surface,
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
                        lightingState.pointLightIsSpot(),
                        lightingState.pointLightDirX(),
                        lightingState.pointLightDirY(),
                        lightingState.pointLightDirZ(),
                        lightingState.pointLightPosX(),
                        lightingState.pointLightPosY(),
                        lightingState.pointLightPosZ(),
                        lightingState.pointLightOuterCos(),
                        lightingState.pointShadowEnabled(),
                        lightingState.pointShadowFarPlane(),
                        renderState.shadowCascadeCount,
                        viewMatrix,
                        projMatrix,
                        lightingState.dirLightDirX(),
                        lightingState.dirLightDirY(),
                        lightingState.dirLightDirZ(),
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES
                ),
                renderState.shadowLightViewProjMatrices,
                renderState.shadowCascadeSplitNdc
        );
    }

    private void uploadSceneMeshes(MemoryStack stack, List<VulkanSceneMeshData> sceneMeshes) throws EngineException {
        var result = VulkanSceneMeshCoordinator.uploadSceneMeshes(
                new VulkanSceneMeshCoordinator.UploadRequest(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        framesInFlight,
                        sceneMeshes,
                        this::createTextureFromPath,
                        this::resolveOrCreateTexture,
                        this::textureCacheKey,
                        this::vkFailure,
                        estimatedGpuMemoryBytes
                )
        );
        estimatedGpuMemoryBytes = result.estimatedGpuMemoryBytes();
    }

    private void destroySceneMeshes() {
        VulkanSceneMeshCoordinator.destroySceneMeshes(
                new VulkanSceneMeshCoordinator.DestroyRequest(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources
                )
        );
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
        VulkanSceneMeshCoordinator.createTextureDescriptorSets(
                new VulkanSceneMeshCoordinator.TextureDescriptorRequest(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        stack
                )
        );
    }

    private VulkanGpuTexture createTextureFromPath(Path texturePath, boolean normalMap) throws EngineException {
        return VulkanTextureResourceOps.createTextureFromPath(
                texturePath,
                normalMap,
                new VulkanTextureResourceOps.Context(backendResources.device, backendResources.physicalDevice, backendResources.commandPool, backendResources.graphicsQueue, this::vkFailure)
        );
    }

    private void markGlobalStateDirty() { uploadState.markGlobalStateDirty(); }

    private void markSceneStateDirty(int dirtyStart, int dirtyEnd) {
        if (dirtyEnd < dirtyStart) {
            return;
        }
        uploadState.markSceneStateDirty(dirtyStart, dirtyEnd, dynamicUploadMergeGapObjects);
    }

    private void prepareFrameUniforms(int frameIdx) throws EngineException {
        VulkanUniformUploadCoordinator.prepareFrameUniforms(
                new VulkanUniformUploadCoordinator.PrepareInputs(
                        frameIdx,
                        sceneResources.gpuMeshes,
                        maxDynamicSceneObjects,
                        framesInFlight,
                        OBJECT_UNIFORM_BYTES,
                        GLOBAL_SCENE_UNIFORM_BYTES,
                        backendResources.device,
                        descriptorResources,
                        uploadState,
                        VulkanGlobalSceneUniformCoordinator.build(
                                new VulkanGlobalSceneUniformCoordinator.BuildRequest(
                                        GLOBAL_SCENE_UNIFORM_BYTES,
                                        viewMatrix,
                                        projMatrix,
                                        lightingState.dirLightDirX(),
                                        lightingState.dirLightDirY(),
                                        lightingState.dirLightDirZ(),
                                        lightingState.dirLightColorR(),
                                        lightingState.dirLightColorG(),
                                        lightingState.dirLightColorB(),
                                        lightingState.pointLightPosX(),
                                        lightingState.pointLightPosY(),
                                        lightingState.pointLightPosZ(),
                                        lightingState.pointShadowFarPlane(),
                                        lightingState.pointLightColorR(),
                                        lightingState.pointLightColorG(),
                                        lightingState.pointLightColorB(),
                                        lightingState.pointLightDirX(),
                                        lightingState.pointLightDirY(),
                                        lightingState.pointLightDirZ(),
                                        lightingState.pointLightInnerCos(),
                                        lightingState.pointLightOuterCos(),
                                        lightingState.pointLightIsSpot(),
                                        lightingState.pointShadowEnabled(),
                                        lightingState.dirLightIntensity(),
                                        lightingState.pointLightIntensity(),
                                        renderState.shadowEnabled,
                                        renderState.shadowStrength,
                                        renderState.shadowBias,
                                        renderState.shadowPcfRadius,
                                        renderState.shadowCascadeCount,
                                        renderState.shadowMapResolution,
                                        renderState.shadowCascadeSplitNdc,
                                        renderState.fogEnabled,
                                        renderState.fogDensity,
                                        renderState.fogR,
                                        renderState.fogG,
                                        renderState.fogB,
                                        renderState.fogSteps,
                                        renderState.smokeEnabled,
                                        renderState.smokeIntensity,
                                        backendResources.swapchainWidth,
                                        backendResources.swapchainHeight,
                                        renderState.smokeR,
                                        renderState.smokeG,
                                        renderState.smokeB,
                                        iblState.enabled,
                                        iblState.diffuseStrength,
                                        iblState.specularStrength,
                                        iblState.prefilterStrength,
                                        renderState.postOffscreenActive,
                                        renderState.tonemapEnabled,
                                        renderState.tonemapExposure,
                                        renderState.tonemapGamma,
                                        renderState.bloomEnabled,
                                        renderState.bloomThreshold,
                                        renderState.bloomStrength,
                                        renderState.ssaoEnabled,
                                        renderState.ssaoStrength,
                                        renderState.ssaoRadius,
                                        renderState.ssaoBias,
                                        renderState.ssaoPower,
                                        renderState.smaaEnabled,
                                        renderState.smaaStrength,
                                        renderState.shadowLightViewProjMatrices
                                )
                        ),
                        this::vkFailure
                )
        );
    }

    private void uploadFrameUniforms(VkCommandBuffer commandBuffer) {
        VulkanUniformUploadCoordinator.uploadFrameUniforms(
                new VulkanUniformUploadCoordinator.UploadInputs(
                        commandBuffer,
                        descriptorResources,
                        uploadState,
                        frameUploadStats
                )
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateTemporalJitterState() {
        renderState.taaPrevJitterNdcX = renderState.taaJitterNdcX;
        renderState.taaPrevJitterNdcY = renderState.taaJitterNdcY;
        if (!renderState.taaEnabled) {
            return;
        }
        int frame = ++taaJitterFrameIndex;
        float width = Math.max(1, backendResources.swapchainWidth);
        float height = Math.max(1, backendResources.swapchainHeight);
        float jitterX = (float) (((halton(frame, 2) - 0.5) * 2.0) / width);
        float jitterY = (float) (((halton(frame, 3) - 0.5) * 2.0) / height);
        boolean changed = Math.abs(jitterX - renderState.taaJitterNdcX) > 1e-9f || Math.abs(jitterY - renderState.taaJitterNdcY) > 1e-9f;
        renderState.taaJitterNdcX = jitterX;
        renderState.taaJitterNdcY = jitterY;
        if (changed) {
            projMatrix = applyProjectionJitter(projBaseMatrix, renderState.taaJitterNdcX, renderState.taaJitterNdcY);
            markGlobalStateDirty();
        }
    }

    private void resetTemporalJitterState() {
        taaJitterFrameIndex = 0;
        renderState.taaJitterNdcX = 0f;
        renderState.taaJitterNdcY = 0f;
        renderState.taaPrevJitterNdcX = 0f;
        renderState.taaPrevJitterNdcY = 0f;
    }

    private float taaJitterUvDeltaX() {
        return (renderState.taaPrevJitterNdcX - renderState.taaJitterNdcX) * 0.5f;
    }

    private float taaJitterUvDeltaY() {
        return (renderState.taaPrevJitterNdcY - renderState.taaJitterNdcY) * 0.5f;
    }

    private static float[] applyProjectionJitter(float[] baseProjection, float jitterNdcX, float jitterNdcY) {
        float[] jittered = baseProjection.clone();
        jittered[8] += jitterNdcX;
        jittered[9] += jitterNdcY;
        return jittered;
    }

    private static double halton(int index, int base) {
        double result = 0.0;
        double f = 1.0;
        int i = index;
        while (i > 0) {
            f /= base;
            result += f * (i % base);
            i /= base;
        }
        return result;
    }

}
