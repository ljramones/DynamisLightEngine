package org.dynamislight.impl.vulkan;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.ReflectionProbeDesc;
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
import org.dynamislight.impl.vulkan.scene.VulkanReflectionProbeCoordinator;
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
import org.dynamislight.impl.vulkan.texture.VulkanTexturePixelLoader;
import org.dynamislight.impl.vulkan.uniform.VulkanFrameUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanGlobalSceneUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformFrameCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformUploadCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanUploadStateTracker;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.dynamislight.impl.vulkan.math.VulkanMath.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanContext {
    private static final int VERTEX_STRIDE_FLOATS = 11;
    private static final int VERTEX_STRIDE_BYTES = VERTEX_STRIDE_FLOATS * Float.BYTES;
    private static final int DEFAULT_FRAMES_IN_FLIGHT = 3;
    private static final int DEFAULT_MAX_DYNAMIC_SCENE_OBJECTS = 2048;
    private static final int DEFAULT_MAX_PENDING_UPLOAD_RANGES = 64;
    private static final int DEFAULT_DYNAMIC_UPLOAD_MERGE_GAP_OBJECTS = 1;
    private static final int DEFAULT_DYNAMIC_OBJECT_SOFT_LIMIT = 1536;
    private static final int DEFAULT_MAX_REFLECTION_PROBES = 32;
    private static final int MAX_PENDING_UPLOAD_RANGES_HARD_CAP = 4096;
    private static final int MAX_SHADOW_CASCADES = 24;
    private static final int POINT_SHADOW_FACES = 6;
    private static final int MAX_SHADOW_MATRICES = 24;
    static final int MAX_LOCAL_LIGHTS = 8;
    private static final int GLOBAL_SCENE_UNIFORM_BYTES = 2736;
    private static final int OBJECT_UNIFORM_BYTES = 176;
    private static final String SHADOW_DEPTH_FORMAT_PROPERTY = "dle.vulkan.shadow.depthFormat";
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
    private String lastGpuTimingSource = "frame_estimate";
    private double lastPlanarCaptureGpuMs = Double.NaN;
    private boolean lastPlanarCaptureGpuMsValid;
    private final VulkanSceneResourceState sceneResources = new VulkanSceneResourceState();
    private final VulkanDescriptorRingStats descriptorRingStats = new VulkanDescriptorRingStats();
    private long estimatedGpuMemoryBytes;
    private final VulkanFrameUploadStats frameUploadStats = new VulkanFrameUploadStats();
    private float[] viewMatrix = identityMatrix();
    private float[] projMatrix = identityMatrix();
    private float[] projBaseMatrix = identityMatrix();
    private float[] taaPrevViewProj = identityMatrix();
    private boolean taaPrevViewProjValid;
    private int taaJitterFrameIndex;
    private final VulkanRenderState renderState = new VulkanRenderState();
    private double taaHistoryRejectRate;
    private double taaConfidenceMean = 1.0;
    private long taaConfidenceDropEvents;
    private long shadowMatrixStateKey = Long.MIN_VALUE;
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
    private int localLightCount;
    private final float[] localLightPosRange = new float[MAX_LOCAL_LIGHTS * 4];
    private final float[] localLightColorIntensity = new float[MAX_LOCAL_LIGHTS * 4];
    private final float[] localLightDirInner = new float[MAX_LOCAL_LIGHTS * 4];
    private final float[] localLightOuterTypeShadow = new float[MAX_LOCAL_LIGHTS * 4];
    private final VulkanIblState iblState = new VulkanIblState();
    private List<ReflectionProbeDesc> reflectionProbes = List.of();
    private Map<String, Integer> reflectionProbeCubemapSlots = Map.of();
    private int reflectionProbeCubemapSlotCount;
    private int reflectionProbeUpdateCadenceFrames = 1;
    private int reflectionProbeMaxVisible = 64;
    private float reflectionProbeLodDepthScale = 1.0f;
    private long reflectionProbeFrameTick;
    private int reflectionProbeFrustumVisibleCount;
    private int reflectionProbeDeferredCount;
    private int reflectionProbeVisibleUniquePathCount;
    private int reflectionProbeMissingSlotPathCount;
    private int reflectionProbeLodTier0Count;
    private int reflectionProbeLodTier1Count;
    private int reflectionProbeLodTier2Count;
    private int reflectionProbeLodTier3Count;

    VulkanContext() {
        backendResources.depthFormat = resolveConfiguredDepthFormat();
    }

    public String shadowDepthFormatTag() {
        return switch (backendResources.depthFormat) {
            case VK_FORMAT_D16_UNORM -> "d16";
            case VK_FORMAT_D32_SFLOAT -> "d32";
            default -> "vk_format_" + backendResources.depthFormat;
        };
    }

    public String shadowMomentFormatTag() {
        return switch (backendResources.shadowMomentFormat) {
            case VK10.VK_FORMAT_R16G16_SFLOAT -> "rg16f";
            case VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> "rgba16f";
            case 0 -> "none";
            default -> "vk_format_" + backendResources.shadowMomentFormat;
        };
    }

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
                if (renderState.shadowMomentPipelineRequested && backendResources.shadowMomentImage != VK_NULL_HANDLE) {
                    renderState.shadowMomentInitialized = true;
                }
                backendResources.currentFrame = (backendResources.currentFrame + 1) % Math.max(1, backendResources.commandBuffers.length);
                updateTemporalHistoryCameraState();
            }
        }
        promotePreviousModelMatrices();
        updateAaTelemetry();
        double cpuMs = (System.nanoTime() - start) / 1_000_000.0;
        if (!lastPlanarCaptureGpuMsValid) {
            lastGpuTimingSource = "frame_estimate";
        }
        return new VulkanFrameMetrics(
                cpuMs,
                cpuMs * 0.7,
                lastPlanarCaptureGpuMsValid ? lastPlanarCaptureGpuMs : Double.NaN,
                lastGpuTimingSource,
                plannedDrawCalls,
                plannedTriangles,
                plannedVisibleObjects,
                estimatedGpuMemoryBytes
        );
    }

    String gpuTimingSource() {
        return lastGpuTimingSource;
    }

    private void promotePreviousModelMatrices() {
        for (VulkanGpuMesh mesh : sceneResources.gpuMeshes) {
            if (mesh.modelMatrix == null || mesh.prevModelMatrix == null
                    || mesh.modelMatrix.length != 16 || mesh.prevModelMatrix.length != 16) {
                continue;
            }
            System.arraycopy(mesh.modelMatrix, 0, mesh.prevModelMatrix, 0, 16);
        }
    }

    double taaHistoryRejectRate() {
        return taaHistoryRejectRate;
    }

    double taaConfidenceMean() {
        return taaConfidenceMean;
    }

    long taaConfidenceDropEvents() {
        return taaConfidenceDropEvents;
    }

    List<Integer> debugGpuMeshReflectionOverrideModes() {
        if (!sceneResources.gpuMeshes.isEmpty()) {
            List<Integer> modes = new ArrayList<>(sceneResources.gpuMeshes.size());
            for (VulkanGpuMesh mesh : sceneResources.gpuMeshes) {
                if (mesh == null) {
                    continue;
                }
                modes.add(Math.max(0, Math.min(3, mesh.reflectionOverrideMode)));
            }
            return List.copyOf(modes);
        }
        if (sceneResources.pendingSceneMeshes == null || sceneResources.pendingSceneMeshes.isEmpty()) {
            return List.of();
        }
        List<Integer> modes = new ArrayList<>(sceneResources.pendingSceneMeshes.size());
        for (VulkanSceneMeshData mesh : sceneResources.pendingSceneMeshes) {
            if (mesh == null) {
                continue;
            }
            modes.add(Math.max(0, Math.min(3, mesh.reflectionOverrideMode())));
        }
        return List.copyOf(modes);
    }

    ReflectionProbeDiagnostics debugReflectionProbeDiagnostics() {
        int configuredProbeCount = reflectionProbes == null ? 0 : reflectionProbes.size();
        int activeProbeCount = Math.max(0, descriptorResources.reflectionProbeMetadataActiveCount);
        int slotCount = Math.max(0, reflectionProbeCubemapSlotCount);
        int metadataCapacity = Math.max(0, descriptorResources.reflectionProbeMetadataMaxCount);
        return new ReflectionProbeDiagnostics(
                configuredProbeCount,
                activeProbeCount,
                slotCount,
                metadataCapacity,
                reflectionProbeFrustumVisibleCount,
                reflectionProbeDeferredCount,
                reflectionProbeVisibleUniquePathCount,
                reflectionProbeMissingSlotPathCount,
                reflectionProbeLodTier0Count,
                reflectionProbeLodTier1Count,
                reflectionProbeLodTier2Count,
                reflectionProbeLodTier3Count
        );
    }

    int debugReflectionsMode() {
        return renderState.reflectionsMode;
    }

    float debugReflectionsRtDenoiseStrength() {
        return renderState.reflectionsRtDenoiseStrength;
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

    record ReflectionProbeDiagnostics(
            int configuredProbeCount,
            int activeProbeCount,
            int slotCount,
            int metadataCapacity,
            int frustumVisibleCount,
            int deferredProbeCount,
            int visibleUniquePathCount,
            int missingSlotPathCount,
            int lodTier0Count,
            int lodTier1Count,
            int lodTier2Count,
            int lodTier3Count
    ) {
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
            taaPrevViewProjValid = false;
        } else {
            projMatrix = result.state().proj();
        }
        if (result.changed() || proj != null) {
            markGlobalStateDirty();
        }
    }

    void setTaaDebugView(int debugView) {
        int clamped = Math.max(0, Math.min(5, debugView));
        if (renderState.taaDebugView != clamped) {
            renderState.taaDebugView = clamped;
            markGlobalStateDirty();
        }
    }

    void setLightingParameters(
            float[] dirDir,
            float[] dirColor,
            float dirIntensity,
            float[] shadowPointPos,
            float[] shadowPointDirection,
            boolean shadowPointIsSpot,
            float shadowPointOuterCos,
            float shadowPointRange,
            boolean shadowPointCastsShadows,
            int localCount,
            float[] localPosRange,
            float[] localColorIntensity,
            float[] localDirInner,
            float[] localOuterTypeShadow
    ) {
        var result = VulkanLightingParameterMutator.applyLighting(
                lightingState,
                new VulkanLightingParameterMutator.LightingUpdate(
                        dirDir, dirColor, dirIntensity,
                        shadowPointPos, new float[]{1f, 1f, 1f}, 1f,
                        shadowPointDirection, 1f, shadowPointOuterCos, shadowPointIsSpot, shadowPointRange, shadowPointCastsShadows
                )
        );
        lightingState = result.state();
        localLightCount = Math.max(0, Math.min(MAX_LOCAL_LIGHTS, localCount));
        for (int i = 0; i < localLightPosRange.length; i++) {
            localLightPosRange[i] = 0f;
            localLightColorIntensity[i] = 0f;
            localLightDirInner[i] = 0f;
            localLightOuterTypeShadow[i] = 0f;
        }
        if (localPosRange != null) {
            System.arraycopy(localPosRange, 0, localLightPosRange, 0, Math.min(localPosRange.length, localLightPosRange.length));
        }
        if (localColorIntensity != null) {
            System.arraycopy(localColorIntensity, 0, localLightColorIntensity, 0, Math.min(localColorIntensity.length, localLightColorIntensity.length));
        }
        if (localDirInner != null) {
            System.arraycopy(localDirInner, 0, localLightDirInner, 0, Math.min(localDirInner.length, localLightDirInner.length));
        }
        if (localOuterTypeShadow != null) {
            System.arraycopy(localOuterTypeShadow, 0, localLightOuterTypeShadow, 0, Math.min(localOuterTypeShadow.length, localLightOuterTypeShadow.length));
        }
        markGlobalStateDirty();
    }

    void setShadowParameters(
            boolean enabled,
            float strength,
            float bias,
            float normalBiasScale,
            float slopeBiasScale,
            int pcfRadius,
            int cascadeCount,
            int mapResolution
    )
            throws EngineException {
        var result = VulkanLightingParameterMutator.applyShadow(
                new VulkanLightingParameterMutator.ShadowState(
                        renderState.shadowEnabled,
                        renderState.shadowStrength,
                        renderState.shadowBias,
                        renderState.shadowNormalBiasScale,
                        renderState.shadowSlopeBiasScale,
                        renderState.shadowPcfRadius,
                        renderState.shadowCascadeCount,
                        renderState.shadowMapResolution
                ),
                new VulkanLightingParameterMutator.ShadowUpdate(
                        enabled, strength, bias, normalBiasScale, slopeBiasScale, pcfRadius, cascadeCount, mapResolution, MAX_SHADOW_MATRICES
                )
        );
        var state = result.state();
        renderState.shadowEnabled = state.shadowEnabled();
        renderState.shadowStrength = state.shadowStrength();
        renderState.shadowBias = state.shadowBias();
        renderState.shadowNormalBiasScale = state.shadowNormalBiasScale();
        renderState.shadowSlopeBiasScale = state.shadowSlopeBiasScale();
        renderState.shadowPcfRadius = state.shadowPcfRadius();
        renderState.shadowCascadeCount = state.shadowCascadeCount();
        renderState.shadowMapResolution = state.shadowMapResolution();
        if (result.resolutionChanged() && backendResources.device != null) {
            vkDeviceWaitIdle(backendResources.device);
            try (MemoryStack stack = stackPush()) {
                destroyShadowResources();
                createShadowResources(stack);
                renderState.shadowMomentInitialized = false;
                if (!sceneResources.gpuMeshes.isEmpty()) {
                    createTextureDescriptorSets(stack);
                }
            }
        }
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    void setShadowQualityModes(String filterPath, boolean contactShadows, String rtMode, String requestedFilterPath)
            throws EngineException {
        int filterMode = switch (filterPath == null ? "pcf" : filterPath.trim().toLowerCase()) {
            case "pcss" -> 1;
            case "vsm" -> 2;
            case "evsm" -> 3;
            default -> 0;
        };
        int momentMode = switch (requestedFilterPath == null ? "pcf" : requestedFilterPath.trim().toLowerCase()) {
            case "vsm" -> 1;
            case "evsm" -> 2;
            default -> 0;
        };
        boolean momentPipelineRequested = momentMode > 0;
        int rtModeInt = switch (rtMode == null ? "off" : rtMode.trim().toLowerCase()) {
            case "optional" -> 1;
            case "force" -> 2;
            case "bvh" -> 3;
            case "bvh_dedicated" -> 4;
            case "bvh_production" -> 5;
            case "rt_native" -> 6;
            case "rt_native_denoised" -> 7;
            default -> 0;
        };
        boolean changed = false;
        if (renderState.shadowFilterMode != filterMode) {
            renderState.shadowFilterMode = filterMode;
            changed = true;
        }
        if (renderState.shadowContactShadows != contactShadows) {
            renderState.shadowContactShadows = contactShadows;
            changed = true;
        }
        if (renderState.shadowRtMode != rtModeInt) {
            renderState.shadowRtMode = rtModeInt;
            changed = true;
        }
        if (renderState.shadowMomentPipelineRequested != momentPipelineRequested
                || renderState.shadowMomentMode != momentMode) {
            renderState.shadowMomentPipelineRequested = momentPipelineRequested;
            renderState.shadowMomentMode = momentMode;
            if (backendResources.device != null) {
                vkDeviceWaitIdle(backendResources.device);
                try (MemoryStack stack = stackPush()) {
                    destroyShadowResources();
                    createShadowResources(stack);
                    renderState.shadowMomentInitialized = false;
                    if (!sceneResources.gpuMeshes.isEmpty()) {
                        createTextureDescriptorSets(stack);
                    }
                }
            }
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setShadowQualityTuning(
            float pcssSoftness,
            float momentBlend,
            float momentBleedReduction,
            float contactStrength,
            float contactTemporalMotionScale,
            float contactTemporalMinStability
    ) {
        boolean changed = false;
        float clampedPcssSoftness = Math.max(0.25f, Math.min(2.0f, pcssSoftness));
        float clampedMomentBlend = Math.max(0.25f, Math.min(1.5f, momentBlend));
        float clampedMomentBleedReduction = Math.max(0.25f, Math.min(1.5f, momentBleedReduction));
        float clampedContactStrength = Math.max(0.25f, Math.min(2.0f, contactStrength));
        float clampedContactTemporalMotionScale = Math.max(0.1f, Math.min(3.0f, contactTemporalMotionScale));
        float clampedContactTemporalMinStability = Math.max(0.2f, Math.min(1.0f, contactTemporalMinStability));
        if (Math.abs(renderState.shadowPcssSoftness - clampedPcssSoftness) > 0.000001f) {
            renderState.shadowPcssSoftness = clampedPcssSoftness;
            changed = true;
        }
        if (Math.abs(renderState.shadowMomentBlend - clampedMomentBlend) > 0.000001f) {
            renderState.shadowMomentBlend = clampedMomentBlend;
            changed = true;
        }
        if (Math.abs(renderState.shadowMomentBleedReduction - clampedMomentBleedReduction) > 0.000001f) {
            renderState.shadowMomentBleedReduction = clampedMomentBleedReduction;
            changed = true;
        }
        if (Math.abs(renderState.shadowContactStrength - clampedContactStrength) > 0.000001f) {
            renderState.shadowContactStrength = clampedContactStrength;
            changed = true;
        }
        if (Math.abs(renderState.shadowContactTemporalMotionScale - clampedContactTemporalMotionScale) > 0.000001f) {
            renderState.shadowContactTemporalMotionScale = clampedContactTemporalMotionScale;
            changed = true;
        }
        if (Math.abs(renderState.shadowContactTemporalMinStability - clampedContactTemporalMinStability) > 0.000001f) {
            renderState.shadowContactTemporalMinStability = clampedContactTemporalMinStability;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    void setShadowRtTuning(float denoiseStrength, float rayLength, int sampleCount) {
        boolean changed = false;
        float clampedDenoise = Math.max(0.0f, Math.min(1.0f, denoiseStrength));
        float clampedRayLength = Math.max(1.0f, Math.min(500.0f, rayLength));
        int clampedSamples = Math.max(1, Math.min(16, sampleCount));
        if (Math.abs(renderState.shadowRtDenoiseStrength - clampedDenoise) > 0.000001f) {
            renderState.shadowRtDenoiseStrength = clampedDenoise;
            changed = true;
        }
        if (Math.abs(renderState.shadowRtRayLength - clampedRayLength) > 0.000001f) {
            renderState.shadowRtRayLength = clampedRayLength;
            changed = true;
        }
        if (renderState.shadowRtSampleCount != clampedSamples) {
            renderState.shadowRtSampleCount = clampedSamples;
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    public boolean isShadowMomentPipelineActive() {
        return renderState.shadowMomentPipelineRequested
                && backendResources.shadowMomentImage != VK_NULL_HANDLE
                && backendResources.shadowMomentImageView != VK_NULL_HANDLE
                && backendResources.shadowMomentSampler != VK_NULL_HANDLE
                && backendResources.shadowMomentFormat != 0;
    }

    public boolean hasShadowMomentResources() {
        return backendResources.shadowMomentImage != VK_NULL_HANDLE
                && backendResources.shadowMomentImageView != VK_NULL_HANDLE
                && backendResources.shadowMomentSampler != VK_NULL_HANDLE
                && backendResources.shadowMomentFormat != 0;
    }

    public boolean isShadowMomentInitialized() {
        return renderState.shadowMomentInitialized;
    }

    boolean isHardwareRtShadowTraversalSupported() {
        return backendResources.shadowRtTraversalSupported;
    }

    boolean isHardwareRtShadowBvhSupported() {
        return backendResources.shadowRtBvhSupported;
    }

    void setShadowDirectionalTexelSnap(boolean enabled, float scale) {
        boolean clampedEnabled = enabled;
        float clampedScale = Math.max(0.25f, Math.min(4.0f, scale));
        boolean changed = false;
        if (renderState.shadowDirectionalTexelSnapEnabled != clampedEnabled) {
            renderState.shadowDirectionalTexelSnapEnabled = clampedEnabled;
            changed = true;
        }
        if (Math.abs(renderState.shadowDirectionalTexelSnapScale - clampedScale) > 0.00001f) {
            renderState.shadowDirectionalTexelSnapScale = clampedScale;
            changed = true;
        }
        if (changed) {
            shadowMatrixStateKey = Long.MIN_VALUE;
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

    void setReflectionProbes(List<ReflectionProbeDesc> probes) throws EngineException {
        reflectionProbes = probes == null ? List.of() : List.copyOf(probes);
        reflectionProbeFrameTick = 0L;
        rebuildReflectionProbeResources();
        markGlobalStateDirty();
    }

    void configureReflectionProbeStreaming(int updateCadenceFrames, int maxVisible, float lodDepthScale) {
        reflectionProbeUpdateCadenceFrames = Math.max(1, Math.min(120, updateCadenceFrames));
        reflectionProbeMaxVisible = Math.max(1, Math.min(256, maxVisible));
        reflectionProbeLodDepthScale = Math.max(0.25f, Math.min(4.0f, lodDepthScale));
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
            float taaBlend,
            float taaClipScale,
            boolean taaLumaClipEnabled,
            float taaSharpenStrength,
            float taaRenderScale,
            boolean reflectionsEnabled,
            int reflectionsMode,
            float reflectionsSsrStrength,
            float reflectionsSsrMaxRoughness,
            float reflectionsSsrStepScale,
            float reflectionsTemporalWeight,
            float reflectionsPlanarStrength,
            float reflectionsPlanarPlaneHeight,
            float reflectionsRtDenoiseStrength
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
                        this.renderState.taaBlend,
                        this.renderState.taaClipScale,
                        this.renderState.taaRenderScale,
                        this.renderState.taaLumaClipEnabled,
                        this.renderState.taaSharpenStrength,
                        this.renderState.reflectionsEnabled,
                        this.renderState.reflectionsMode,
                        this.renderState.reflectionsSsrStrength,
                        this.renderState.reflectionsSsrMaxRoughness,
                        this.renderState.reflectionsSsrStepScale,
                        this.renderState.reflectionsTemporalWeight,
                        this.renderState.reflectionsPlanarStrength,
                        this.renderState.reflectionsPlanarPlaneHeight,
                        this.renderState.reflectionsRtDenoiseStrength
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
                        taaBlend,
                        taaClipScale,
                        taaRenderScale,
                        taaLumaClipEnabled,
                        taaSharpenStrength,
                        reflectionsEnabled,
                        reflectionsMode,
                        reflectionsSsrStrength,
                        reflectionsSsrMaxRoughness,
                        reflectionsSsrStepScale,
                        reflectionsTemporalWeight,
                        reflectionsPlanarStrength,
                        reflectionsPlanarPlaneHeight,
                        reflectionsRtDenoiseStrength
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
        this.renderState.taaClipScale = state.taaClipScale();
        this.renderState.taaRenderScale = state.taaRenderScale();
        this.renderState.taaLumaClipEnabled = state.taaLumaClipEnabled();
        this.renderState.taaSharpenStrength = state.taaSharpenStrength();
        this.renderState.reflectionsEnabled = state.reflectionsEnabled();
        this.renderState.reflectionsMode = state.reflectionsMode();
        this.renderState.reflectionsSsrStrength = state.reflectionsSsrStrength();
        this.renderState.reflectionsSsrMaxRoughness = state.reflectionsSsrMaxRoughness();
        this.renderState.reflectionsSsrStepScale = state.reflectionsSsrStepScale();
        this.renderState.reflectionsTemporalWeight = state.reflectionsTemporalWeight();
        this.renderState.reflectionsPlanarStrength = state.reflectionsPlanarStrength();
        this.renderState.reflectionsPlanarPlaneHeight = state.reflectionsPlanarPlaneHeight();
        this.renderState.reflectionsRtDenoiseStrength = state.reflectionsRtDenoiseStrength();
        if (!this.renderState.taaEnabled) {
            this.renderState.postTaaHistoryInitialized = false;
            resetTemporalJitterState();
            projMatrix = projBaseMatrix.clone();
            taaPrevViewProjValid = false;
            taaHistoryRejectRate = 0.0;
            taaConfidenceMean = 1.0;
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
        destroyPlanarTimestampResources();
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
                DEFAULT_MAX_REFLECTION_PROBES,
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
        descriptorResources.reflectionProbeMetadataBuffer = allocation.reflectionProbeMetadataBuffer();
        descriptorResources.reflectionProbeMetadataMemory = allocation.reflectionProbeMetadataMemory();
        descriptorResources.reflectionProbeMetadataMappedAddress = allocation.reflectionProbeMetadataMappedAddress();
        descriptorResources.reflectionProbeMetadataMaxCount = allocation.reflectionProbeMetadataMaxCount();
        descriptorResources.reflectionProbeMetadataStrideBytes = allocation.reflectionProbeMetadataStrideBytes();
        descriptorResources.reflectionProbeMetadataBufferBytes = allocation.reflectionProbeMetadataBufferBytes();
        descriptorResources.reflectionProbeMetadataActiveCount = 0;
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
                                descriptorResources.reflectionProbeMetadataBuffer,
                                descriptorResources.reflectionProbeMetadataMemory,
                                descriptorResources.reflectionProbeMetadataMappedAddress,
                                descriptorResources.reflectionProbeMetadataMaxCount,
                                descriptorResources.reflectionProbeMetadataStrideBytes,
                                descriptorResources.reflectionProbeMetadataBufferBytes,
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
        descriptorResources.reflectionProbeMetadataBuffer = state.reflectionProbeMetadataBuffer();
        descriptorResources.reflectionProbeMetadataMemory = state.reflectionProbeMetadataMemory();
        descriptorResources.reflectionProbeMetadataMappedAddress = state.reflectionProbeMetadataMappedAddress();
        descriptorResources.reflectionProbeMetadataMaxCount = state.reflectionProbeMetadataMaxCount();
        descriptorResources.reflectionProbeMetadataStrideBytes = state.reflectionProbeMetadataStrideBytes();
        descriptorResources.reflectionProbeMetadataBufferBytes = state.reflectionProbeMetadataBufferBytes();
        descriptorResources.reflectionProbeMetadataActiveCount = state.reflectionProbeMetadataActiveCount();
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
        renderState.shadowMomentInitialized = false;
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
        createPlanarTimestampResources(stack);
    }

    private void createPlanarTimestampResources(MemoryStack stack) throws EngineException {
        destroyPlanarTimestampResources();
        backendResources.planarTimestampSupported = false;
        backendResources.timestampPeriodNs = 0.0f;
        lastGpuTimingSource = "frame_estimate";
        lastPlanarCaptureGpuMs = Double.NaN;
        lastPlanarCaptureGpuMsValid = false;
        if (backendResources.device == null
                || backendResources.physicalDevice == null
                || backendResources.graphicsQueueFamilyIndex < 0) {
            return;
        }
        var pQueueFamilyCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(backendResources.physicalDevice, pQueueFamilyCount, null);
        int queueFamilyCount = pQueueFamilyCount.get(0);
        if (queueFamilyCount <= 0 || backendResources.graphicsQueueFamilyIndex >= queueFamilyCount) {
            return;
        }
        var queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount, stack);
        vkGetPhysicalDeviceQueueFamilyProperties(backendResources.physicalDevice, pQueueFamilyCount, queueFamilies);
        if (queueFamilies.get(backendResources.graphicsQueueFamilyIndex).timestampValidBits() <= 0) {
            return;
        }
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        vkGetPhysicalDeviceProperties(backendResources.physicalDevice, properties);
        backendResources.timestampPeriodNs = Math.max(0.0f, properties.limits().timestampPeriod());
        int queryCount = Math.max(1, framesInFlight) * 2;
        var queryInfo = VkQueryPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                .queryType(VK_QUERY_TYPE_TIMESTAMP)
                .queryCount(queryCount);
        var pQueryPool = stack.mallocLong(1);
        int createResult = vkCreateQueryPool(backendResources.device, queryInfo, null, pQueryPool);
        if (createResult != VK_SUCCESS || pQueryPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateQueryPool(planar) failed: " + createResult, false);
        }
        backendResources.planarTimestampQueryPool = pQueryPool.get(0);
        backendResources.planarTimestampSupported = true;
    }

    private void destroyPlanarTimestampResources() {
        if (backendResources.device != null && backendResources.planarTimestampQueryPool != VK_NULL_HANDLE) {
            vkDestroyQueryPool(backendResources.device, backendResources.planarTimestampQueryPool, null);
        }
        backendResources.planarTimestampQueryPool = VK_NULL_HANDLE;
        backendResources.planarTimestampSupported = false;
        backendResources.timestampPeriodNs = 0.0f;
    }

    private void samplePlanarCaptureTimingForFrame(MemoryStack stack, int frameIdx) throws EngineException {
        lastPlanarCaptureGpuMs = Double.NaN;
        lastPlanarCaptureGpuMsValid = false;
        lastGpuTimingSource = "frame_estimate";
        if (!backendResources.planarTimestampSupported
                || backendResources.planarTimestampQueryPool == VK_NULL_HANDLE
                || backendResources.device == null) {
            return;
        }
        int queryStart = planarTimestampQueryStartIndex(frameIdx);
        if (queryStart < 0) {
            return;
        }
        var data = stack.mallocLong(4);
        int queryResult = vkGetQueryPoolResults(
                backendResources.device,
                backendResources.planarTimestampQueryPool,
                queryStart,
                2,
                data,
                2L * Long.BYTES,
                VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT
        );
        if (queryResult == VK_NOT_READY) {
            return;
        }
        if (queryResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkGetQueryPoolResults(planar) failed: " + queryResult, false);
        }
        long startTimestamp = data.get(0);
        long startAvailable = data.get(1);
        long endTimestamp = data.get(2);
        long endAvailable = data.get(3);
        if (startAvailable != 0L && endAvailable != 0L && endTimestamp >= startTimestamp) {
            double deltaTicks = (double) (endTimestamp - startTimestamp);
            double periodNs = Math.max(0.0, backendResources.timestampPeriodNs);
            lastPlanarCaptureGpuMs = (deltaTicks * periodNs) / 1_000_000.0;
            lastPlanarCaptureGpuMsValid = true;
            lastGpuTimingSource = "gpu_timestamp";
        }
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
                        imageIndex -> recordCommandBuffer(stack, commandBuffer, imageIndex, frameIdx),
                        () -> samplePlanarCaptureTimingForFrame(stack, frameIdx)
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
                        backendResources.swapchainImageFormat,
                        backendResources.depthFormat,
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
                        backendResources.shadowDepthImage,
                        backendResources.shadowMomentImage,
                        backendResources.shadowMomentFormat,
                        backendResources.shadowMomentMipLevels,
                        renderState.shadowMomentPipelineRequested,
                        renderState.shadowMomentInitialized,
                        backendResources.depthImages,
                        backendResources.planarTimestampQueryPool,
                        planarTimestampQueryStartIndex(frameIdx),
                        planarTimestampQueryEndIndex(frameIdx),
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
                        renderState.taaMotionUvX,
                        renderState.taaMotionUvY,
                        renderState.taaClipScale,
                        renderState.taaRenderScale,
                        renderState.taaLumaClipEnabled,
                        renderState.taaSharpenStrength,
                        renderState.reflectionsEnabled,
                        renderState.reflectionsMode,
                        renderState.reflectionsSsrStrength,
                        renderState.reflectionsSsrMaxRoughness,
                        renderState.reflectionsSsrStepScale,
                        renderState.reflectionsTemporalWeight,
                        renderState.reflectionsPlanarStrength,
                        renderState.reflectionsPlanarPlaneHeight,
                        renderState.reflectionsRtDenoiseStrength,
                        renderState.taaDebugView,
                        backendResources.postRenderPass,
                        backendResources.postGraphicsPipeline,
                        backendResources.postPipelineLayout,
                        backendResources.postDescriptorSet,
                        backendResources.offscreenColorImage,
                        backendResources.postTaaHistoryImage,
                        backendResources.postTaaHistoryVelocityImage,
                        backendResources.postPlanarCaptureImage,
                        backendResources.velocityImage,
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

    private int planarTimestampQueryStartIndex(int frameIdx) {
        if (backendResources.planarTimestampQueryPool == VK_NULL_HANDLE || framesInFlight <= 0) {
            return -1;
        }
        int safeFrame = Math.max(0, Math.min(Math.max(0, framesInFlight - 1), frameIdx));
        return safeFrame * 2;
    }

    private int planarTimestampQueryEndIndex(int frameIdx) {
        int start = planarTimestampQueryStartIndex(frameIdx);
        return start < 0 ? -1 : start + 1;
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
        long key = 17L;
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightIsSpot());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightDirX());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightDirY());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightDirZ());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightPosX());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightPosY());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightPosZ());
        key = 31L * key + Float.floatToIntBits(lightingState.pointLightOuterCos());
        key = 31L * key + Float.floatToIntBits(lightingState.pointShadowFarPlane());
        key = 31L * key + (lightingState.pointShadowEnabled() ? 1 : 0);
        key = 31L * key + renderState.shadowCascadeCount;
        key = 31L * key + (renderState.shadowDirectionalTexelSnapEnabled ? 1 : 0);
        key = 31L * key + Float.floatToIntBits(renderState.shadowDirectionalTexelSnapScale);
        key = 31L * key + Float.floatToIntBits(lightingState.dirLightDirX());
        key = 31L * key + Float.floatToIntBits(lightingState.dirLightDirY());
        key = 31L * key + Float.floatToIntBits(lightingState.dirLightDirZ());
        key = 31L * key + localLightCount;
        int localFloats = Math.min(localLightCount * 4, localLightPosRange.length);
        for (int i = 0; i < localFloats; i++) {
            key = 31L * key + Float.floatToIntBits(localLightPosRange[i]);
            key = 31L * key + Float.floatToIntBits(localLightDirInner[i]);
            key = 31L * key + Float.floatToIntBits(localLightOuterTypeShadow[i]);
        }
        for (int i = 0; i < 16; i++) {
            key = 31L * key + Float.floatToIntBits(viewMatrix[i]);
            key = 31L * key + Float.floatToIntBits(projMatrix[i]);
        }
        if (key == shadowMatrixStateKey) {
            return;
        }
        shadowMatrixStateKey = key;
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
                        renderState.shadowMapResolution,
                        renderState.shadowDirectionalTexelSnapEnabled,
                        renderState.shadowDirectionalTexelSnapScale,
                        viewMatrix,
                        projMatrix,
                        localLightCount,
                        localLightPosRange,
                        localLightDirInner,
                        localLightOuterTypeShadow,
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
        destroyOwnedProbeRadianceTexture();
        VulkanSceneMeshCoordinator.destroySceneMeshes(
                new VulkanSceneMeshCoordinator.DestroyRequest(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources
                )
        );
        iblState.probeRadianceTexture = null;
        reflectionProbeCubemapSlots = Map.of();
        reflectionProbeCubemapSlotCount = 0;
        reflectionProbeFrustumVisibleCount = 0;
        reflectionProbeDeferredCount = 0;
        reflectionProbeVisibleUniquePathCount = 0;
        reflectionProbeMissingSlotPathCount = 0;
        reflectionProbeLodTier0Count = 0;
        reflectionProbeLodTier1Count = 0;
        reflectionProbeLodTier2Count = 0;
        reflectionProbeLodTier3Count = 0;
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
        updateReflectionProbeMetadataBuffer();
        float planarHeight = renderState.reflectionsPlanarPlaneHeight;
        float[] planeReflection = planarReflectionMatrix(planarHeight);
        float[] planarViewMatrix = mul(viewMatrix, planeReflection);
        float[] planarProjMatrix = projMatrix;
        float[] planarPrevViewProj = taaPrevViewProjValid
                ? mul(taaPrevViewProj, planeReflection)
                : mul(planarProjMatrix, planarViewMatrix);
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
                                        renderState.shadowPcssSoftness,
                                        lightingState.dirLightColorR(),
                                        lightingState.dirLightColorG(),
                                        lightingState.dirLightColorB(),
                                        renderState.shadowMomentBlend,
                                        lightingState.pointLightPosX(),
                                        lightingState.pointLightPosY(),
                                        lightingState.pointLightPosZ(),
                                        lightingState.pointShadowFarPlane(),
                                        lightingState.pointLightColorR(),
                                        lightingState.pointLightColorG(),
                                        lightingState.pointLightColorB(),
                                        renderState.shadowMomentBleedReduction,
                                        lightingState.pointLightDirX(),
                                        lightingState.pointLightDirY(),
                                        lightingState.pointLightDirZ(),
                                        renderState.shadowContactStrength,
                                        lightingState.pointLightInnerCos(),
                                        lightingState.pointLightOuterCos(),
                                        lightingState.pointLightIsSpot(),
                                        lightingState.pointShadowEnabled(),
                                        localLightCount,
                                        MAX_LOCAL_LIGHTS,
                                        localLightPosRange,
                                        localLightColorIntensity,
                                        localLightDirInner,
                                        localLightOuterTypeShadow,
                                        renderState.shadowFilterMode,
                                        renderState.shadowRtMode,
                                        backendResources.shadowRtTraversalSupported && renderState.shadowRtMode > 0,
                                        renderState.shadowRtDenoiseStrength,
                                        renderState.shadowRtRayLength,
                                        renderState.shadowRtSampleCount,
                                        renderState.shadowContactShadows,
                                        lightingState.dirLightIntensity(),
                                        lightingState.pointLightIntensity(),
                                        renderState.shadowContactTemporalMotionScale,
                                        renderState.shadowContactTemporalMinStability,
                                        renderState.shadowEnabled,
                                        renderState.shadowStrength,
                                        renderState.shadowBias,
                                        renderState.shadowNormalBiasScale,
                                        renderState.shadowSlopeBiasScale,
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
                                        taaPrevViewProjValid ? taaPrevViewProj : mul(projMatrix, viewMatrix),
                                        renderState.shadowLightViewProjMatrices,
                                        planarViewMatrix,
                                        planarProjMatrix,
                                        planarPrevViewProj
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

    private void updateReflectionProbeMetadataBuffer() {
        if (descriptorResources.reflectionProbeMetadataMappedAddress == 0L
                || descriptorResources.reflectionProbeMetadataBufferBytes <= 0) {
            descriptorResources.reflectionProbeMetadataActiveCount = 0;
            return;
        }
        float[] viewProj = mul(projMatrix, viewMatrix);
        VulkanReflectionProbeCoordinator.UploadStats stats = VulkanReflectionProbeCoordinator.uploadVisibleProbes(
                reflectionProbes,
                viewProj,
                descriptorResources.reflectionProbeMetadataMaxCount,
                descriptorResources.reflectionProbeMetadataStrideBytes,
                reflectionProbeCubemapSlots,
                reflectionProbeCubemapSlotCount,
                descriptorResources.reflectionProbeMetadataMappedAddress,
                descriptorResources.reflectionProbeMetadataBufferBytes,
                reflectionProbeFrameTick++,
                reflectionProbeUpdateCadenceFrames,
                reflectionProbeMaxVisible,
                reflectionProbeLodDepthScale
        );
        descriptorResources.reflectionProbeMetadataActiveCount = stats.activeProbeCount();
        reflectionProbeFrustumVisibleCount = stats.frustumVisibleCount();
        reflectionProbeDeferredCount = stats.deferredProbeCount();
        reflectionProbeVisibleUniquePathCount = stats.visibleUniquePaths();
        reflectionProbeMissingSlotPathCount = stats.missingSlotPaths();
        reflectionProbeLodTier0Count = stats.lodTier0Count();
        reflectionProbeLodTier1Count = stats.lodTier1Count();
        reflectionProbeLodTier2Count = stats.lodTier2Count();
        reflectionProbeLodTier3Count = stats.lodTier3Count();
    }

    private static float[] planarReflectionMatrix(float planeHeight) {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, -1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 2f * planeHeight, 0f, 1f
        };
    }

    private void rebuildReflectionProbeResources() throws EngineException {
        int maxSlots = Math.max(1, descriptorResources.reflectionProbeMetadataMaxCount > 0
                ? descriptorResources.reflectionProbeMetadataMaxCount
                : DEFAULT_MAX_REFLECTION_PROBES);
        TreeSet<String> uniquePaths = new TreeSet<>();
        for (ReflectionProbeDesc probe : reflectionProbes) {
            if (probe == null) {
                continue;
            }
            String path = probe.cubemapAssetPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            uniquePaths.add(path);
        }
        Map<String, Integer> slots = new HashMap<>();
        int nextSlot = 0;
        for (String path : uniquePaths) {
            if (nextSlot >= maxSlots) {
                break;
            }
            slots.put(path, nextSlot);
            nextSlot++;
        }
        reflectionProbeCubemapSlots = Map.copyOf(slots);
        reflectionProbeCubemapSlotCount = slots.size();
        VulkanGpuTexture newProbeTexture = buildProbeRadianceAtlasTexture(slots, reflectionProbeCubemapSlotCount);
        VulkanGpuTexture oldProbeTexture = iblState.probeRadianceTexture;
        iblState.probeRadianceTexture = newProbeTexture;
        if (oldProbeTexture != null && !sameTexture(oldProbeTexture, newProbeTexture) && !sameTexture(oldProbeTexture, iblState.radianceTexture)) {
            destroyTexture(oldProbeTexture);
        }
        if (backendResources.device != null && !sceneResources.gpuMeshes.isEmpty()) {
            try (MemoryStack stack = stackPush()) {
                createTextureDescriptorSets(stack);
            }
        }
    }

    private VulkanGpuTexture buildProbeRadianceAtlasTexture(Map<String, Integer> slots, int slotCount) throws EngineException {
        if (backendResources.device == null || slotCount <= 0 || slots.isEmpty()) {
            return iblState.radianceTexture;
        }
        boolean probeCubeArrayEnabled = Boolean.getBoolean("dle.vulkan.reflections.probeCubeArrayEnabled");
        String[] pathBySlot = new String[slotCount];
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            int slot = entry.getValue() == null ? -1 : entry.getValue();
            if (slot >= 0 && slot < slotCount) {
                pathBySlot[slot] = entry.getKey();
            }
        }
        List<ProbeSlotPixels> slotPixels = new ArrayList<>(slotCount);
        int layerWidth = -1;
        int layerHeight = -1;
        for (int i = 0; i < slotCount; i++) {
            org.dynamislight.impl.vulkan.model.VulkanTexturePixelData pixels = null;
            List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> cubeFaces = null;
            String rawPath = pathBySlot[i];
            if (rawPath != null && !rawPath.isBlank()) {
                try {
                    pixels = VulkanTexturePixelLoader.loadTexturePixels(Path.of(rawPath));
                    if (probeCubeArrayEnabled) {
                        cubeFaces = loadProbeCubeFaces(rawPath);
                    }
                } catch (RuntimeException ignored) {
                    pixels = null;
                    cubeFaces = null;
                }
            }
            if (layerWidth < 0) {
                if (cubeFaces != null && !cubeFaces.isEmpty()) {
                    layerWidth = cubeFaces.get(0).width();
                    layerHeight = cubeFaces.get(0).height();
                } else if (pixels != null) {
                    layerWidth = pixels.width();
                    layerHeight = pixels.height();
                }
            }
            slotPixels.add(new ProbeSlotPixels(pixels, cubeFaces));
        }
        if (layerWidth <= 0 || layerHeight <= 0) {
            freeProbeSlotPixels(slotPixels);
            return iblState.radianceTexture;
        }
        int layerBytes = layerWidth * layerHeight * 4;
        boolean cubePathReady = probeCubeArrayEnabled;
        if (probeCubeArrayEnabled) {
            for (ProbeSlotPixels slot : slotPixels) {
                if (slot.cubeFaces == null || slot.cubeFaces.size() != 6 || !allFacesMatchDimensions(slot.cubeFaces, layerWidth, layerHeight, layerBytes)) {
                    cubePathReady = false;
                    break;
                }
            }
        }
        List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> normalizedLayers = new ArrayList<>(slotCount);
        List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> cubeFaceLayers = new ArrayList<>(slotCount * 6);
        for (int i = 0; i < slotCount; i++) {
            ProbeSlotPixels slot = slotPixels.get(i);
            org.dynamislight.impl.vulkan.model.VulkanTexturePixelData layer = slot.layer;
            if (layer != null && layer.width() == layerWidth && layer.height() == layerHeight && layer.data().remaining() == layerBytes) {
                normalizedLayers.add(layer);
            } else {
                ByteBuffer fallback = MemoryUtil.memAlloc(layerBytes);
                for (int p = 0; p < layerBytes / 4; p++) {
                    fallback.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
                }
                fallback.flip();
                normalizedLayers.add(new org.dynamislight.impl.vulkan.model.VulkanTexturePixelData(fallback, layerWidth, layerHeight));
            }
            if (cubePathReady) {
                for (org.dynamislight.impl.vulkan.model.VulkanTexturePixelData face : slot.cubeFaces) {
                    cubeFaceLayers.add(face);
                }
            }
        }
        try {
            VulkanTextureResourceOps.Context context = new VulkanTextureResourceOps.Context(
                    backendResources.device,
                    backendResources.physicalDevice,
                    backendResources.commandPool,
                    backendResources.graphicsQueue,
                    this::vkFailure
            );
            return VulkanTextureResourceOps.createTextureArrayFromPixels(normalizedLayers, context);
        } finally {
            freeProbeSlotPixels(slotPixels);
            freePixelLayers(normalizedLayers);
        }
    }

    private static boolean allFacesMatchDimensions(
            List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> faces,
            int width,
            int height,
            int layerBytes
    ) {
        if (faces == null || faces.size() != 6) {
            return false;
        }
        for (org.dynamislight.impl.vulkan.model.VulkanTexturePixelData face : faces) {
            if (face == null || face.width() != width || face.height() != height || face.data().remaining() != layerBytes) {
                return false;
            }
        }
        return true;
    }

    private static List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> loadProbeCubeFaces(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path source = Paths.get(rawPath);
        String file = source.getFileName() == null ? null : source.getFileName().toString();
        if (file == null || file.isBlank()) {
            return null;
        }
        int dot = file.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String stem = file.substring(0, dot);
        String ext = file.substring(dot);
        String[] suffixes = new String[]{"px", "nx", "py", "ny", "pz", "nz"};
        List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> faces = new ArrayList<>(6);
        for (String suffix : suffixes) {
            Path facePath = source.resolveSibling(stem + "_" + suffix + ext);
            org.dynamislight.impl.vulkan.model.VulkanTexturePixelData face = VulkanTexturePixelLoader.loadTexturePixels(facePath);
            if (face == null) {
                freePixelLayers(faces);
                return null;
            }
            faces.add(face);
        }
        return faces;
    }

    private static void freePixelLayers(List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> layers) {
        for (org.dynamislight.impl.vulkan.model.VulkanTexturePixelData layer : layers) {
            if (layer == null) {
                continue;
            }
            MemoryUtil.memFree(layer.data());
        }
    }

    private static void freeProbeSlotPixels(List<ProbeSlotPixels> slots) {
        for (ProbeSlotPixels slot : slots) {
            if (slot == null) {
                continue;
            }
            if (slot.layer != null) {
                MemoryUtil.memFree(slot.layer.data());
            }
            if (slot.cubeFaces != null) {
                freePixelLayers(slot.cubeFaces);
            }
        }
    }

    private void destroyOwnedProbeRadianceTexture() {
        VulkanGpuTexture probe = iblState.probeRadianceTexture;
        if (probe == null) {
            return;
        }
        if (sameTexture(probe, iblState.radianceTexture)) {
            return;
        }
        destroyTexture(probe);
    }

    private void destroyTexture(VulkanGpuTexture texture) {
        if (backendResources.device == null || texture == null) {
            return;
        }
        if (texture.sampler() != VK_NULL_HANDLE) {
            vkDestroySampler(backendResources.device, texture.sampler(), null);
        }
        if (texture.view() != VK_NULL_HANDLE) {
            vkDestroyImageView(backendResources.device, texture.view(), null);
        }
        if (texture.image() != VK_NULL_HANDLE) {
            vkDestroyImage(backendResources.device, texture.image(), null);
        }
        if (texture.memory() != VK_NULL_HANDLE) {
            vkFreeMemory(backendResources.device, texture.memory(), null);
        }
    }

    private static boolean sameTexture(VulkanGpuTexture a, VulkanGpuTexture b) {
        if (a == null || b == null) {
            return false;
        }
        return a.image() == b.image() && a.view() == b.view() && a.sampler() == b.sampler();
    }

    private record ProbeSlotPixels(
            org.dynamislight.impl.vulkan.model.VulkanTexturePixelData layer,
            List<org.dynamislight.impl.vulkan.model.VulkanTexturePixelData> cubeFaces
    ) {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int resolveConfiguredDepthFormat() {
        String raw = System.getProperty(SHADOW_DEPTH_FORMAT_PROPERTY, "d32");
        String normalized = raw == null ? "d32" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "d16", "d16_unorm", "vk_format_d16_unorm", "124" -> VK_FORMAT_D16_UNORM;
            case "d32", "d32_sfloat", "vk_format_d32_sfloat", "126" -> VK_FORMAT_D32_SFLOAT;
            default -> VK_FORMAT_D32_SFLOAT;
        };
    }

    private void updateTemporalJitterState() {
        renderState.taaPrevJitterNdcX = renderState.taaJitterNdcX;
        renderState.taaPrevJitterNdcY = renderState.taaJitterNdcY;
        if (!renderState.taaEnabled) {
            return;
        }
        int frame = ++taaJitterFrameIndex;
        float scale = renderState.taaEnabled ? Math.max(0.5f, Math.min(1.0f, renderState.taaRenderScale)) : 1.0f;
        float width = Math.max(1, backendResources.swapchainWidth * scale);
        float height = Math.max(1, backendResources.swapchainHeight * scale);
        float jitterX = (float) (((halton(frame, 2) - 0.5) * 2.0) / width);
        float jitterY = (float) (((halton(frame, 3) - 0.5) * 2.0) / height);
        boolean changed = Math.abs(jitterX - renderState.taaJitterNdcX) > 1e-9f || Math.abs(jitterY - renderState.taaJitterNdcY) > 1e-9f;
        renderState.taaJitterNdcX = jitterX;
        renderState.taaJitterNdcY = jitterY;
        if (changed) {
            projMatrix = applyProjectionJitter(projBaseMatrix, renderState.taaJitterNdcX, renderState.taaJitterNdcY);
            updateTemporalMotionVector();
            markGlobalStateDirty();
        }
    }

    private void resetTemporalJitterState() {
        taaJitterFrameIndex = 0;
        renderState.taaJitterNdcX = 0f;
        renderState.taaJitterNdcY = 0f;
        renderState.taaPrevJitterNdcX = 0f;
        renderState.taaPrevJitterNdcY = 0f;
        renderState.taaMotionUvX = 0f;
        renderState.taaMotionUvY = 0f;
    }

    private float taaJitterUvDeltaX() {
        return (renderState.taaPrevJitterNdcX - renderState.taaJitterNdcX) * 0.5f;
    }

    private float taaJitterUvDeltaY() {
        return (renderState.taaPrevJitterNdcY - renderState.taaJitterNdcY) * 0.5f;
    }

    private void updateAaTelemetry() {
        if (!renderState.taaEnabled) {
            taaHistoryRejectRate = 0.0;
            taaConfidenceMean = 1.0;
            return;
        }
        double motion = Math.sqrt((renderState.taaMotionUvX * renderState.taaMotionUvX)
                + (renderState.taaMotionUvY * renderState.taaMotionUvY));
        double scalePenalty = clamp01((1.0 - Math.max(0.5, Math.min(1.0, renderState.taaRenderScale))) * 1.2);
        double reject = clamp01((1.0 - renderState.taaBlend) * 0.50 + motion * (6.0 + scalePenalty * 1.8));
        double targetConfidence = clamp01((1.0 - reject * 0.78) * Math.max(0.3, renderState.taaClipScale));
        if (renderState.taaLumaClipEnabled) {
            targetConfidence = clamp01(targetConfidence + 0.04);
        }
        if (targetConfidence + 0.08 < taaConfidenceMean) {
            taaConfidenceDropEvents++;
        }
        taaHistoryRejectRate = reject;
        taaConfidenceMean = targetConfidence;
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
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

    private void updateTemporalMotionVector() {
        if (!renderState.taaEnabled) {
            renderState.taaMotionUvX = 0f;
            renderState.taaMotionUvY = 0f;
            return;
        }
        float[] currentViewProj = mul(projMatrix, viewMatrix);
        if (!taaPrevViewProjValid) {
            renderState.taaMotionUvX = 0f;
            renderState.taaMotionUvY = 0f;
            return;
        }
        float[] origin = new float[]{0f, 0f, 0f, 1f};
        float[] prevClip = mulVec4(taaPrevViewProj, origin);
        float[] currClip = mulVec4(currentViewProj, origin);
        float prevW = Math.abs(prevClip[3]) > 1e-6f ? prevClip[3] : 1f;
        float currW = Math.abs(currClip[3]) > 1e-6f ? currClip[3] : 1f;
        float prevNdcX = prevClip[0] / prevW;
        float prevNdcY = prevClip[1] / prevW;
        float currNdcX = currClip[0] / currW;
        float currNdcY = currClip[1] / currW;
        renderState.taaMotionUvX = (prevNdcX - currNdcX) * 0.5f;
        renderState.taaMotionUvY = (prevNdcY - currNdcY) * 0.5f;
    }

    private void updateTemporalHistoryCameraState() {
        if (!renderState.taaEnabled) {
            return;
        }
        taaPrevViewProj = mul(projMatrix, viewMatrix);
        taaPrevViewProjValid = true;
    }

    private static float[] mulVec4(float[] m, float[] v) {
        return new float[]{
                m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3],
                m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3],
                m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3],
                m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3]
        };
    }

}
