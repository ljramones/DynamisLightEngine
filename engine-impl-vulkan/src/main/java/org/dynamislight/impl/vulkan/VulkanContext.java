package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandInputAssembler;
import org.dynamislight.impl.vulkan.command.VulkanFrameCommandOrchestrator;
import org.dynamislight.impl.vulkan.command.VulkanFrameSubmitCoordinator;
import org.dynamislight.impl.vulkan.command.VulkanCommandInputCoordinator;
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
import org.dynamislight.impl.vulkan.profile.VulkanContextDiagnosticsCoordinator;
import org.dynamislight.impl.vulkan.profile.VulkanContextProfileCoordinator;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
import org.dynamislight.impl.vulkan.scene.VulkanSceneRuntimeCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanSceneMeshCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanReflectionProbeRuntimeCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanReflectionProbeTextureCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanSceneSetPlanner;
import org.dynamislight.impl.vulkan.scene.VulkanSceneTextureRuntimeCoordinator;
import org.dynamislight.impl.vulkan.scene.VulkanSceneTextureCoordinator;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowMatrixStateCoordinator;
import org.dynamislight.impl.vulkan.state.VulkanFrameUploadStats;
import org.dynamislight.impl.vulkan.state.VulkanIblState;
import org.dynamislight.impl.vulkan.state.VulkanRenderParameterMutator;
import org.dynamislight.impl.vulkan.state.VulkanLightingParameterMutator;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanSceneResourceState;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.dynamislight.impl.vulkan.state.VulkanTemporalAaCoordinator;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainRecreateCoordinator;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainTimestampRuntimeHelper;
import org.dynamislight.impl.vulkan.texture.VulkanTexturePixelLoader;
import org.dynamislight.impl.vulkan.uniform.VulkanFrameUniformCoordinator;
import org.dynamislight.impl.vulkan.uniform.VulkanGlobalSceneBuildRequestFactory;
import org.dynamislight.impl.vulkan.uniform.VulkanGlobalSceneUniformCoordinator;
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
    public static final int MAX_LOCAL_LIGHTS = 8;
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
        float[] previousProjMatrix = projMatrix;
        var jitterUpdate = VulkanTemporalAaCoordinator.updateTemporalJitterState(
                renderState,
                taaJitterFrameIndex,
                backendResources.swapchainWidth,
                backendResources.swapchainHeight,
                projBaseMatrix,
                projMatrix,
                taaPrevViewProjValid,
                taaPrevViewProj,
                viewMatrix
        );
        taaJitterFrameIndex = jitterUpdate.taaJitterFrameIndex();
        projMatrix = jitterUpdate.projMatrix();
        if (projMatrix != previousProjMatrix) {
            markGlobalStateDirty();
        }
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
                var temporalHistoryUpdate = VulkanTemporalAaCoordinator.updateTemporalHistoryCameraState(
                        renderState,
                        projMatrix,
                        viewMatrix,
                        taaPrevViewProj,
                        taaPrevViewProjValid
                );
                taaPrevViewProj = temporalHistoryUpdate.taaPrevViewProj();
                taaPrevViewProjValid = temporalHistoryUpdate.taaPrevViewProjValid();
            }
        }
        promotePreviousModelMatrices();
        var aaTelemetry = VulkanTemporalAaCoordinator.updateAaTelemetry(renderState, taaConfidenceMean, taaConfidenceDropEvents);
        taaHistoryRejectRate = aaTelemetry.taaHistoryRejectRate();
        taaConfidenceMean = aaTelemetry.taaConfidenceMean();
        taaConfidenceDropEvents = aaTelemetry.taaConfidenceDropEvents();
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

    public double taaHistoryRejectRate() {
        return taaHistoryRejectRate;
    }

    public double taaConfidenceMean() {
        return taaConfidenceMean;
    }

    public long taaConfidenceDropEvents() {
        return taaConfidenceDropEvents;
    }

    public List<Integer> debugGpuMeshReflectionOverrideModes() {
        return VulkanContextDiagnosticsCoordinator.reflectionOverrideModes(
                sceneResources.gpuMeshes,
                sceneResources.pendingSceneMeshes
        );
    }

    public ReflectionProbeDiagnostics debugReflectionProbeDiagnostics() {
        return VulkanContextDiagnosticsCoordinator.reflectionProbeDiagnostics(
                reflectionProbes == null ? 0 : reflectionProbes.size(),
                Math.max(0, descriptorResources.reflectionProbeMetadataActiveCount),
                Math.max(0, reflectionProbeCubemapSlotCount),
                Math.max(0, descriptorResources.reflectionProbeMetadataMaxCount),
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

    public SceneReuseStats sceneReuseStats() {
        return VulkanContextDiagnosticsCoordinator.sceneReuseStats(
                sceneResources.sceneReuseHitCount,
                sceneResources.sceneReorderReuseCount,
                sceneResources.sceneTextureRebindCount,
                sceneResources.sceneFullRebuildCount,
                sceneResources.meshBufferRebuildCount,
                descriptorRingStats.descriptorPoolBuildCount,
                descriptorRingStats.descriptorPoolRebuildCount
        );
    }

    public FrameResourceProfile frameResourceProfile() {
        return VulkanContextDiagnosticsCoordinator.frameResourceProfile(
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

    public record ReflectionProbeDiagnostics(
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

    public ShadowCascadeProfile shadowCascadeProfile() {
        return VulkanContextDiagnosticsCoordinator.shadowCascadeProfile(
                renderState.shadowEnabled,
                renderState.shadowCascadeCount,
                renderState.shadowMapResolution,
                renderState.shadowPcfRadius,
                renderState.shadowBias,
                renderState.shadowCascadeSplitNdc
        );
    }

    public PostProcessPipelineProfile postProcessPipelineProfile() {
        return VulkanContextDiagnosticsCoordinator.postProcessPipelineProfile(
                renderState.postOffscreenRequested,
                renderState.postOffscreenActive
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
                        (texturePath, normalMap) -> VulkanSceneTextureRuntimeCoordinator.createTextureFromPath(
                                texturePath,
                                normalMap,
                                backendResources,
                                this::vkFailure
                        ),
                        (texturePath, cache, defaultTexture, normalMap) -> VulkanSceneTextureRuntimeCoordinator.resolveOrCreateTexture(
                                texturePath,
                                cache,
                                defaultTexture,
                                normalMap,
                                (path, normal) -> VulkanSceneTextureRuntimeCoordinator.createTextureFromPath(
                                        path,
                                        normal,
                                        backendResources,
                                        this::vkFailure
                                )
                        ),
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
            projMatrix = VulkanTemporalAaCoordinator.applyProjectionJitter(
                    projBaseMatrix,
                    renderState.taaJitterNdcX,
                    renderState.taaJitterNdcY
            );
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
                    VulkanSceneTextureRuntimeCoordinator.createTextureDescriptorSets(
                            backendResources,
                            sceneResources,
                            iblState,
                            descriptorResources,
                            descriptorRingStats,
                            stack
                    );
                }
            }
        }
        if (result.changed()) {
            markGlobalStateDirty();
        }
    }

    public void setShadowQualityModes(String filterPath, boolean contactShadows, String rtMode, String requestedFilterPath)
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
                        VulkanSceneTextureRuntimeCoordinator.createTextureDescriptorSets(
                                backendResources,
                                sceneResources,
                                iblState,
                                descriptorResources,
                                descriptorRingStats,
                                stack
                        );
                    }
                }
            }
            changed = true;
        }
        if (changed) {
            markGlobalStateDirty();
        }
    }

    public void setShadowQualityTuning(
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

    public void setShadowRtTuning(float denoiseStrength, float rayLength, int sampleCount) {
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

    public boolean isHardwareRtShadowTraversalSupported() {
        return backendResources.shadowRtTraversalSupported;
    }

    public boolean isHardwareRtShadowBvhSupported() {
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

    public void setPostProcessParameters(
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
            taaJitterFrameIndex = 0;
            VulkanTemporalAaCoordinator.resetTemporalJitterState(renderState);
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
        VulkanSwapchainTimestampRuntimeHelper.destroyPlanarTimestampResources(backendResources);
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
        VulkanSwapchainTimestampRuntimeHelper.createPlanarTimestampResources(stack, backendResources, framesInFlight);
        lastGpuTimingSource = "frame_estimate";
        lastPlanarCaptureGpuMs = Double.NaN;
        lastPlanarCaptureGpuMsValid = false;
    }

    private void samplePlanarCaptureTimingForFrame(MemoryStack stack, int frameIdx) throws EngineException {
        var sample = VulkanSwapchainTimestampRuntimeHelper.samplePlanarCaptureTimingForFrame(
                stack,
                backendResources,
                framesInFlight,
                frameIdx
        );
        lastGpuTimingSource = sample.gpuTimingSource();
        lastPlanarCaptureGpuMs = sample.planarCaptureGpuMs();
        lastPlanarCaptureGpuMsValid = sample.planarCaptureGpuMsValid();
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
        return VulkanCommandInputCoordinator.build(
                new VulkanCommandInputCoordinator.BuildRequest(
                        sceneResources,
                        maxDynamicSceneObjects,
                        backendResources,
                        renderState,
                        descriptorResources,
                        lightingState.pointShadowEnabled(),
                        frameIdx,
                        framesInFlight,
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES,
                        this::vkFailure
                )
        );
    }

    private void recreateSwapchainFromWindow() throws EngineException {
        VulkanSwapchainTimestampRuntimeHelper.recreateSwapchainFromWindow(
                backendResources.window,
                this::recreateSwapchain
        );
    }

    private void recreateSwapchain(int width, int height) throws EngineException {
        VulkanSwapchainTimestampRuntimeHelper.recreateSwapchain(
                backendResources,
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
        var result = VulkanShadowMatrixStateCoordinator.updateMatricesIfDirty(
                new VulkanShadowMatrixStateCoordinator.UpdateRequest(
                        shadowMatrixStateKey,
                        lightingState.pointLightIsSpot(),
                        lightingState.pointLightDirX(),
                        lightingState.pointLightDirY(),
                        lightingState.pointLightDirZ(),
                        lightingState.pointLightPosX(),
                        lightingState.pointLightPosY(),
                        lightingState.pointLightPosZ(),
                        lightingState.pointLightOuterCos(),
                        lightingState.pointShadowFarPlane(),
                        lightingState.pointShadowEnabled(),
                        renderState.shadowCascadeCount,
                        renderState.shadowMapResolution,
                        renderState.shadowDirectionalTexelSnapEnabled,
                        renderState.shadowDirectionalTexelSnapScale,
                        lightingState.dirLightDirX(),
                        lightingState.dirLightDirY(),
                        lightingState.dirLightDirZ(),
                        localLightCount,
                        localLightPosRange,
                        localLightDirInner,
                        localLightOuterTypeShadow,
                        viewMatrix,
                        projMatrix,
                        MAX_SHADOW_MATRICES,
                        MAX_SHADOW_CASCADES,
                        POINT_SHADOW_FACES
                ),
                renderState.shadowLightViewProjMatrices,
                renderState.shadowCascadeSplitNdc
        );
        shadowMatrixStateKey = result.stateKey();
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
                        (texturePath, normalMap) -> VulkanSceneTextureRuntimeCoordinator.createTextureFromPath(
                                texturePath,
                                normalMap,
                                backendResources,
                                this::vkFailure
                        ),
                        (texturePath, cache, defaultTexture, normalMap) -> VulkanSceneTextureRuntimeCoordinator.resolveOrCreateTexture(
                                texturePath,
                                cache,
                                defaultTexture,
                                normalMap,
                                (path, normal) -> VulkanSceneTextureRuntimeCoordinator.createTextureFromPath(
                                        path,
                                        normal,
                                        backendResources,
                                        this::vkFailure
                                )
                        ),
                        this::textureCacheKey,
                        this::vkFailure,
                        estimatedGpuMemoryBytes
                )
        );
        estimatedGpuMemoryBytes = result.estimatedGpuMemoryBytes();
    }

    private void destroySceneMeshes() {
        VulkanReflectionProbeTextureCoordinator.destroyOwnedProbeRadianceTexture(
                backendResources.device,
                iblState.probeRadianceTexture,
                iblState.radianceTexture
        );
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

    private String textureCacheKey(Path texturePath, boolean normalMap) {
        return VulkanSceneTextureCoordinator.textureCacheKey(texturePath, normalMap);
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
        VulkanGlobalSceneUniformCoordinator.BuildRequest globalSceneRequest =
                VulkanGlobalSceneBuildRequestFactory.build(
                        new VulkanGlobalSceneBuildRequestFactory.Inputs(
                                GLOBAL_SCENE_UNIFORM_BYTES,
                                viewMatrix,
                                projMatrix,
                                taaPrevViewProjValid,
                                taaPrevViewProj,
                                lightingState,
                                renderState,
                                localLightCount,
                                MAX_LOCAL_LIGHTS,
                                localLightPosRange,
                                localLightColorIntensity,
                                localLightDirInner,
                                localLightOuterTypeShadow,
                                backendResources.shadowRtTraversalSupported,
                                backendResources.swapchainWidth,
                                backendResources.swapchainHeight,
                                iblState
                        )
                );
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
                        VulkanGlobalSceneUniformCoordinator.build(globalSceneRequest),
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
        var result = VulkanReflectionProbeRuntimeCoordinator.updateMetadataBuffer(
                new VulkanReflectionProbeRuntimeCoordinator.MetadataUploadRequest(
                        descriptorResources.reflectionProbeMetadataMappedAddress,
                        descriptorResources.reflectionProbeMetadataBufferBytes,
                        descriptorResources.reflectionProbeMetadataMaxCount,
                        descriptorResources.reflectionProbeMetadataStrideBytes,
                        projMatrix,
                        viewMatrix,
                        reflectionProbes,
                        reflectionProbeCubemapSlots,
                        reflectionProbeCubemapSlotCount,
                        reflectionProbeFrameTick,
                        reflectionProbeUpdateCadenceFrames,
                        reflectionProbeMaxVisible,
                        reflectionProbeLodDepthScale
                )
        );
        descriptorResources.reflectionProbeMetadataActiveCount = result.activeProbeCount();
        reflectionProbeFrustumVisibleCount = result.frustumVisibleCount();
        reflectionProbeDeferredCount = result.deferredProbeCount();
        reflectionProbeVisibleUniquePathCount = result.visibleUniquePathCount();
        reflectionProbeMissingSlotPathCount = result.missingSlotPathCount();
        reflectionProbeLodTier0Count = result.lodTier0Count();
        reflectionProbeLodTier1Count = result.lodTier1Count();
        reflectionProbeLodTier2Count = result.lodTier2Count();
        reflectionProbeLodTier3Count = result.lodTier3Count();
        reflectionProbeFrameTick = result.nextFrameTick();
    }

    private void rebuildReflectionProbeResources() throws EngineException {
        int maxSlots = Math.max(1, descriptorResources.reflectionProbeMetadataMaxCount > 0
                ? descriptorResources.reflectionProbeMetadataMaxCount
                : DEFAULT_MAX_REFLECTION_PROBES);
        var slotAssignment = VulkanReflectionProbeRuntimeCoordinator.assignCubemapSlots(reflectionProbes, maxSlots);
        Map<String, Integer> slots = slotAssignment.slots();
        reflectionProbeCubemapSlots = slots;
        reflectionProbeCubemapSlotCount = slotAssignment.slotCount();
        VulkanGpuTexture newProbeTexture = VulkanReflectionProbeTextureCoordinator.buildProbeRadianceAtlasTexture(
                new VulkanReflectionProbeTextureCoordinator.BuildRequest(
                        backendResources.device,
                        backendResources.physicalDevice,
                        backendResources.commandPool,
                        backendResources.graphicsQueue,
                        this::vkFailure,
                        iblState.radianceTexture,
                        slots,
                        reflectionProbeCubemapSlotCount,
                        Boolean.getBoolean("dle.vulkan.reflections.probeCubeArrayEnabled")
                )
        );
        VulkanGpuTexture oldProbeTexture = iblState.probeRadianceTexture;
        iblState.probeRadianceTexture = newProbeTexture;
        if (oldProbeTexture != null
                && !VulkanReflectionProbeTextureCoordinator.sameTexture(oldProbeTexture, newProbeTexture)
                && !VulkanReflectionProbeTextureCoordinator.sameTexture(oldProbeTexture, iblState.radianceTexture)) {
            VulkanReflectionProbeTextureCoordinator.destroyTexture(backendResources.device, oldProbeTexture);
        }
        if (backendResources.device != null && !sceneResources.gpuMeshes.isEmpty()) {
            try (MemoryStack stack = stackPush()) {
                VulkanSceneTextureRuntimeCoordinator.createTextureDescriptorSets(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        stack
                );
            }
        }
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
    
}
