package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.common.AbstractEngineRuntime;
import org.dynamislight.impl.vulkan.asset.VulkanGltfMeshParser;
import org.dynamislight.impl.vulkan.asset.VulkanMeshAssetLoader;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
    private static final int DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES = 256;
    private final VulkanContext context = new VulkanContext();
    private boolean mockContext = true;
    private boolean windowVisible;
    private boolean forceDeviceLostOnRender;
    private boolean deviceLostRaised;
    private boolean postOffscreenRequested;
    private double descriptorRingWasteWarnRatio = 0.85;
    private int descriptorRingWasteWarnMinFrames = 8;
    private int descriptorRingWasteWarnMinCapacity = 64;
    private int descriptorRingWasteHighStreak;
    private int descriptorRingWasteWarnCooldownFrames = 120;
    private int descriptorRingWasteWarnCooldownRemaining;
    private long descriptorRingCapPressureWarnMinBypasses = 4;
    private int descriptorRingCapPressureWarnMinFrames = 2;
    private int descriptorRingCapPressureStreak;
    private int descriptorRingCapPressureWarnCooldownFrames = 120;
    private int descriptorRingCapPressureWarnCooldownRemaining;
    private int uniformUploadSoftLimitBytes = 2 * 1024 * 1024;
    private int uniformUploadWarnCooldownFrames = 120;
    private int uniformUploadWarnCooldownRemaining;
    private int pendingUploadRangeSoftLimit = 48;
    private int pendingUploadRangeWarnCooldownFrames = 120;
    private int pendingUploadRangeWarnCooldownRemaining;
    private int descriptorRingActiveSoftLimit = 2048;
    private int descriptorRingActiveWarnCooldownFrames = 120;
    private int descriptorRingActiveWarnCooldownRemaining;
    private QualityTier qualityTier = QualityTier.MEDIUM;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private Path assetRoot = Path.of(".");
    private VulkanMeshAssetLoader meshLoader = new VulkanMeshAssetLoader(assetRoot);
    private int meshGeometryCacheMaxEntries = DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES;
    private MeshGeometryCacheProfile meshGeometryCacheProfile =
            new MeshGeometryCacheProfile(0, 0, 0, 0, DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES);
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private FogRenderConfig currentFog = new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
    private SmokeRenderConfig currentSmoke = new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
    private ShadowRenderConfig currentShadows = new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
    private PostProcessRenderConfig currentPost = new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f);
    private IblRenderConfig currentIbl = new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0, 0, 0f, false, 0, null, null, null);
    private boolean nonDirectionalShadowRequested;

    public VulkanEngineRuntime() {
        super(
                "Vulkan",
                new EngineCapabilities(
                        Set.of("vulkan"),
                        true,
                        true,
                        true,
                        true,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH, QualityTier.ULTRA)
                ),
                16.2,
                7.8
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        Map<String, String> backendOptions = config.backendOptions();
        mockContext = Boolean.parseBoolean(backendOptions.getOrDefault("vulkan.mockContext", "true"));
        windowVisible = Boolean.parseBoolean(backendOptions.getOrDefault("vulkan.windowVisible", "false"));
        forceDeviceLostOnRender = Boolean.parseBoolean(backendOptions.getOrDefault("vulkan.forceDeviceLostOnRender", "false"));
        postOffscreenRequested = Boolean.parseBoolean(backendOptions.getOrDefault("vulkan.postOffscreen", "true"));
        meshGeometryCacheMaxEntries = parseIntOption(
                backendOptions,
                "vulkan.meshGeometryCacheEntries",
                DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES,
                16,
                4096
        );
        int framesInFlight = parseIntOption(backendOptions, "vulkan.framesInFlight", 3, 2, 6);
        int maxDynamicSceneObjects = parseIntOption(backendOptions, "vulkan.maxDynamicSceneObjects", 2048, 256, 8192);
        int maxPendingUploadRanges = parseIntOption(backendOptions, "vulkan.maxPendingUploadRanges", 64, 8, 2048);
        int dynamicUploadMergeGapObjects = parseIntOption(
                backendOptions,
                "vulkan.dynamicUploadMergeGapObjects",
                1,
                0,
                32
        );
        int dynamicObjectSoftLimit = parseIntOption(
                backendOptions,
                "vulkan.dynamicObjectSoftLimit",
                1536,
                128,
                8192
        );
        int descriptorRingMaxSetCapacity = parseIntOption(
                backendOptions,
                "vulkan.maxTextureDescriptorSets",
                4096,
                256,
                32768
        );
        descriptorRingWasteWarnRatio = parseDoubleOption(
                backendOptions,
                "vulkan.descriptorRingWasteWarnRatio",
                0.85,
                0.1,
                0.99
        );
        descriptorRingWasteWarnMinFrames = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingWasteWarnMinFrames",
                8,
                1,
                600
        );
        descriptorRingWasteWarnMinCapacity = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingWasteWarnMinCapacity",
                64,
                1,
                65536
        );
        descriptorRingWasteWarnCooldownFrames = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingWasteWarnCooldownFrames",
                120,
                0,
                10000
        );
        descriptorRingCapPressureWarnMinBypasses = parseLongOption(
                backendOptions,
                "vulkan.descriptorRingCapPressureWarnMinBypasses",
                4,
                1,
                1_000_000
        );
        descriptorRingCapPressureWarnMinFrames = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingCapPressureWarnMinFrames",
                2,
                1,
                600
        );
        descriptorRingCapPressureWarnCooldownFrames = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingCapPressureWarnCooldownFrames",
                120,
                0,
                10000
        );
        uniformUploadSoftLimitBytes = parseIntOption(
                backendOptions,
                "vulkan.uniformUploadSoftLimitBytes",
                2 * 1024 * 1024,
                4096,
                64 * 1024 * 1024
        );
        uniformUploadWarnCooldownFrames = parseIntOption(
                backendOptions,
                "vulkan.uniformUploadWarnCooldownFrames",
                120,
                0,
                10000
        );
        pendingUploadRangeSoftLimit = parseIntOption(
                backendOptions,
                "vulkan.pendingUploadRangeSoftLimit",
                48,
                1,
                2048
        );
        pendingUploadRangeWarnCooldownFrames = parseIntOption(
                backendOptions,
                "vulkan.pendingUploadRangeWarnCooldownFrames",
                120,
                0,
                10000
        );
        descriptorRingActiveSoftLimit = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingActiveSoftLimit",
                2048,
                64,
                32768
        );
        descriptorRingActiveWarnCooldownFrames = parseIntOption(
                backendOptions,
                "vulkan.descriptorRingActiveWarnCooldownFrames",
                120,
                0,
                10000
        );
        context.configureFrameResources(framesInFlight, maxDynamicSceneObjects, maxPendingUploadRanges);
        context.configureDynamicUploadMergeGap(dynamicUploadMergeGapObjects);
        context.configureDynamicObjectSoftLimit(dynamicObjectSoftLimit);
        context.configureDescriptorRing(descriptorRingMaxSetCapacity);
        assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        meshLoader = new VulkanMeshAssetLoader(assetRoot, meshGeometryCacheMaxEntries);
        qualityTier = config.qualityTier();
        viewportWidth = config.initialWidthPx();
        viewportHeight = config.initialHeightPx();
        deviceLostRaised = false;
        descriptorRingWasteHighStreak = 0;
        descriptorRingCapPressureStreak = 0;
        descriptorRingWasteWarnCooldownRemaining = 0;
        descriptorRingCapPressureWarnCooldownRemaining = 0;
        uniformUploadWarnCooldownRemaining = 0;
        pendingUploadRangeWarnCooldownRemaining = 0;
        descriptorRingActiveWarnCooldownRemaining = 0;
        if (Boolean.parseBoolean(backendOptions.getOrDefault("vulkan.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced Vulkan init failure", false);
        }
        if (!mockContext) {
            context.configurePostProcessMode(postOffscreenRequested);
            context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), windowVisible);
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
        CameraDesc camera = VulkanEngineRuntimeSceneMapper.selectActiveCamera(scene);
        CameraMatrices cameraMatrices = VulkanEngineRuntimeSceneMapper.cameraMatricesFor(
                camera,
                VulkanEngineRuntimeSceneMapper.safeAspect(viewportWidth, viewportHeight)
        );
        LightingConfig lighting = VulkanEngineRuntimeSceneMapper.mapLighting(scene == null ? null : scene.lights());
        ShadowRenderConfig shadows = VulkanEngineRuntimeSceneMapper.mapShadows(scene == null ? null : scene.lights(), qualityTier);
        FogRenderConfig fog = VulkanEngineRuntimeSceneMapper.mapFog(scene == null ? null : scene.fog(), qualityTier);
        SmokeRenderConfig smoke = VulkanEngineRuntimeSceneMapper.mapSmoke(scene == null ? null : scene.smokeEmitters(), qualityTier);
        PostProcessRenderConfig post = VulkanEngineRuntimeSceneMapper.mapPostProcess(scene == null ? null : scene.postProcess(), qualityTier);
        IblRenderConfig ibl = VulkanEngineRuntimeSceneMapper.mapIbl(scene == null ? null : scene.environment(), qualityTier, assetRoot);
        currentFog = fog;
        currentSmoke = smoke;
        currentShadows = shadows;
        currentPost = post;
        currentIbl = ibl;
        nonDirectionalShadowRequested = VulkanEngineRuntimeSceneMapper.hasNonDirectionalShadowRequest(scene == null ? null : scene.lights());
        List<VulkanSceneMeshData> sceneMeshes = VulkanEngineRuntimeSceneMapper.buildSceneMeshes(scene, meshLoader, assetRoot);
        VulkanMeshAssetLoader.CacheProfile cache = meshLoader.cacheProfile();
        meshGeometryCacheProfile = new MeshGeometryCacheProfile(
                cache.hits(),
                cache.misses(),
                cache.evictions(),
                cache.entries(),
                cache.maxEntries()
        );
        plannedDrawCalls = sceneMeshes.size();
        plannedTriangles = sceneMeshes.stream().mapToLong(m -> m.indices().length / 3).sum();
        plannedVisibleObjects = plannedDrawCalls;
        if (!mockContext) {
            context.setCameraMatrices(cameraMatrices.view(), cameraMatrices.proj());
            context.setLightingParameters(
                    lighting.directionalDirection(),
                    lighting.directionalColor(),
                    lighting.directionalIntensity(),
                    lighting.pointPosition(),
                    lighting.pointColor(),
                    lighting.pointIntensity(),
                    lighting.pointDirection(),
                    lighting.pointInnerCos(),
                    lighting.pointOuterCos(),
                    lighting.pointIsSpot(),
                    lighting.pointRange(),
                    lighting.pointCastsShadows()
            );
            context.setShadowParameters(
                    shadows.enabled(),
                    shadows.strength(),
                    shadows.bias(),
                    shadows.pcfRadius(),
                    shadows.cascadeCount(),
                    shadows.mapResolution()
            );
            context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
            context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
            context.setIblParameters(ibl.enabled(), ibl.diffuseStrength(), ibl.specularStrength(), ibl.prefilterStrength());
            context.setIblTexturePaths(
                    ibl.irradiancePath(),
                    ibl.radiancePath(),
                    ibl.brdfLutPath()
            );
            context.setPostProcessParameters(
                    post.tonemapEnabled(),
                    post.exposure(),
                    post.gamma(),
                    post.bloomEnabled(),
                    post.bloomThreshold(),
                    post.bloomStrength()
            );
            context.setSceneMeshes(sceneMeshes);
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        if (forceDeviceLostOnRender && !deviceLostRaised) {
            deviceLostRaised = true;
            throw new EngineException(EngineErrorCode.DEVICE_LOST, "Forced Vulkan device loss on render", false);
        }
        if (mockContext) {
            return renderMetrics(0.2, 0.1, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, 0);
        }
        VulkanFrameMetrics frame = context.renderFrame();
        return renderMetrics(
                frame.cpuFrameMs(),
                frame.gpuFrameMs(),
                frame.drawCalls(),
                frame.triangles(),
                frame.visibleObjects(),
                frame.gpuMemoryBytes()
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        viewportWidth = widthPx;
        viewportHeight = heightPx;
        if (!mockContext) {
            context.resize(widthPx, heightPx);
        }
    }

    @Override
    protected void onShutdown() {
        if (!mockContext) {
            context.shutdown();
        }
    }

    @Override
    protected java.util.List<EngineWarning> baselineWarnings() {
        return java.util.List.of(new EngineWarning("FEATURE_BASELINE", "Vulkan backend active with baseline indexed render path"));
    }

    @Override
    protected java.util.List<EngineWarning> frameWarnings() {
        java.util.List<EngineWarning> warnings = new java.util.ArrayList<>();
        if (currentSmoke.enabled() && currentSmoke.degraded()) {
            warnings.add(new EngineWarning(
                    "SMOKE_QUALITY_DEGRADED",
                    "Smoke rendering quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (currentFog.enabled() && currentFog.degraded()) {
            warnings.add(new EngineWarning(
                    "FOG_QUALITY_DEGRADED",
                    "Fog sampling reduced at LOW quality tier"
            ));
        }
        if (currentShadows.enabled() && currentShadows.degraded()) {
            warnings.add(new EngineWarning(
                    "SHADOW_QUALITY_DEGRADED",
                    "Shadow quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (nonDirectionalShadowRequested) {
            warnings.add(new EngineWarning(
                    "SHADOW_TYPE_UNSUPPORTED",
                    "Point shadow maps are not implemented yet; current shadow-map path supports directional and spot lights"
            ));
        }
        if (currentIbl.enabled()) {
            warnings.add(new EngineWarning(
                    "IBL_BASELINE_ACTIVE",
                    "IBL baseline enabled using "
                            + (currentIbl.textureDriven() ? "texture-driven" : "path-driven")
                            + " environment diffuse/specular approximations"
            ));
            warnings.add(new EngineWarning(
                    "IBL_PREFILTER_APPROX_ACTIVE",
                    "IBL roughness-aware radiance prefilter approximation active (strength="
                            + currentIbl.prefilterStrength() + ")"
            ));
            warnings.add(new EngineWarning(
                    "IBL_MULTI_TAP_SPEC_ACTIVE",
                    "IBL specular radiance uses roughness-aware multi-tap filtering for improved highlight stability"
            ));
            warnings.add(new EngineWarning(
                    "IBL_MIP_LOD_PREFILTER_ACTIVE",
                    "IBL specular prefilter sampling uses roughness-driven mip/LOD selection"
            ));
            warnings.add(new EngineWarning(
                    "IBL_BRDF_ENERGY_COMP_ACTIVE",
                    "IBL diffuse/specular response uses BRDF energy-compensation and horizon weighting for improved roughness realism"
            ));
            if (currentIbl.skyboxDerived()) {
                warnings.add(new EngineWarning(
                        "IBL_SKYBOX_DERIVED_ACTIVE",
                        "IBL irradiance/radiance inputs are derived from EnvironmentDesc.skyboxAssetPath"
                ));
            }
            if (currentIbl.ktxSkyboxFallback()) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_SKYBOX_FALLBACK_ACTIVE",
                        "KTX IBL paths without decodable sources fell back to skybox-derived irradiance/radiance inputs"
                ));
            }
            if (currentIbl.ktxDecodeUnavailableCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_DECODE_UNAVAILABLE",
                        "KTX/KTX2 IBL assets detected but could not be decoded by current baseline path (channels="
                                + currentIbl.ktxDecodeUnavailableCount()
                                + "); runtime used sidecar/derived/default fallback inputs"
                ));
            }
            if (currentIbl.ktxTranscodeRequiredCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_TRANSCODE_REQUIRED",
                        "KTX2 IBL assets require BasisLZ/UASTC transcoding not yet enabled in this build (channels="
                                + currentIbl.ktxTranscodeRequiredCount()
                                + "); runtime used sidecar/derived/default fallback inputs"
                ));
            }
            if (currentIbl.ktxUnsupportedVariantCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_VARIANT_UNSUPPORTED",
                        "KTX/KTX2 IBL assets use unsupported compressed/supercompressed/format variants in baseline decoder (channels="
                                + currentIbl.ktxUnsupportedVariantCount() + ")"
                ));
            }
            if (currentIbl.missingAssetCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_ASSET_FALLBACK_ACTIVE",
                        "IBL configured assets missing/unreadable (" + currentIbl.missingAssetCount()
                                + "); runtime used fallback/default lighting signals"
                ));
            }
            if (currentIbl.degraded()) {
                warnings.add(new EngineWarning(
                        "IBL_QUALITY_DEGRADED",
                        "IBL diffuse/specular quality reduced for tier " + qualityTier + " to maintain stable frame cost"
                ));
            }
            if (currentIbl.ktxContainerRequested()) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_CONTAINER_FALLBACK",
                        "KTX/KTX2 IBL assets are resolved through sidecar decode paths when available (.png/.hdr/.jpg/.jpeg)"
                ));
            }
        }
        if (!mockContext) {
            warnings.add(new EngineWarning(
                    "MESH_GEOMETRY_CACHE_PROFILE",
                    "hits=" + meshGeometryCacheProfile.hits()
                            + " misses=" + meshGeometryCacheProfile.misses()
                            + " evictions=" + meshGeometryCacheProfile.evictions()
                            + " entries=" + meshGeometryCacheProfile.entries()
                            + " maxEntries=" + meshGeometryCacheProfile.maxEntries()
            ));
            SceneReuseStats reuse = context.sceneReuseStats();
            warnings.add(new EngineWarning(
                    "SCENE_REUSE_PROFILE",
                    "reuseHits=" + reuse.reuseHits()
                            + " reorderReuseHits=" + reuse.reorderReuseHits()
                            + " textureRebindHits=" + reuse.textureRebindHits()
                            + " fullRebuilds=" + reuse.fullRebuilds()
                            + " meshBufferRebuilds=" + reuse.meshBufferRebuilds()
                            + " descriptorPoolBuilds=" + reuse.descriptorPoolBuilds()
                            + " descriptorPoolRebuilds=" + reuse.descriptorPoolRebuilds()
            ));
            FrameResourceProfile frameResources = context.frameResourceProfile();
            warnings.add(new EngineWarning(
                    "VULKAN_FRAME_RESOURCE_PROFILE",
                    "framesInFlight=" + frameResources.framesInFlight()
                            + " descriptorSetsInRing=" + frameResources.descriptorSetsInRing()
                            + " uniformStrideBytes=" + frameResources.uniformStrideBytes()
                            + " uniformFrameSpanBytes=" + frameResources.uniformFrameSpanBytes()
                            + " globalUniformFrameSpanBytes=" + frameResources.globalUniformFrameSpanBytes()
                            + " dynamicSceneCapacity=" + frameResources.dynamicSceneCapacity()
                            + " pendingUploadRangeCapacity=" + frameResources.pendingUploadRangeCapacity()
                            + " lastGlobalUploadBytes=" + frameResources.lastFrameGlobalUploadBytes()
                            + " maxGlobalUploadBytes=" + frameResources.maxFrameGlobalUploadBytes()
                            + " lastUniformUploadBytes=" + frameResources.lastFrameUniformUploadBytes()
                            + " maxUniformUploadBytes=" + frameResources.maxFrameUniformUploadBytes()
                            + " lastUniformObjectCount=" + frameResources.lastFrameUniformObjectCount()
                            + " maxUniformObjectCount=" + frameResources.maxFrameUniformObjectCount()
                            + " lastUniformUploadRanges=" + frameResources.lastFrameUniformUploadRanges()
                            + " maxUniformUploadRanges=" + frameResources.maxFrameUniformUploadRanges()
                            + " lastUniformUploadStartObject=" + frameResources.lastFrameUniformUploadStartObject()
                            + " pendingRangeOverflows=" + frameResources.pendingUploadRangeOverflows()
                            + " descriptorRingSetCapacity=" + frameResources.descriptorRingSetCapacity()
                            + " descriptorRingPeakSetCapacity=" + frameResources.descriptorRingPeakSetCapacity()
                            + " descriptorRingActiveSetCount=" + frameResources.descriptorRingActiveSetCount()
                            + " descriptorRingWasteSetCount=" + frameResources.descriptorRingWasteSetCount()
                            + " descriptorRingPeakWasteSetCount=" + frameResources.descriptorRingPeakWasteSetCount()
                            + " descriptorRingMaxSetCapacity=" + frameResources.descriptorRingMaxSetCapacity()
                            + " descriptorRingReuseHits=" + frameResources.descriptorRingReuseHits()
                            + " descriptorRingGrowthRebuilds=" + frameResources.descriptorRingGrowthRebuilds()
                            + " descriptorRingSteadyRebuilds=" + frameResources.descriptorRingSteadyRebuilds()
                            + " descriptorRingPoolReuses=" + frameResources.descriptorRingPoolReuses()
                            + " descriptorRingPoolResetFailures=" + frameResources.descriptorRingPoolResetFailures()
                            + " descriptorRingCapBypasses=" + frameResources.descriptorRingCapBypasses()
                            + " dynamicUploadMergeGapObjects=" + frameResources.dynamicUploadMergeGapObjects()
                            + " dynamicObjectSoftLimit=" + frameResources.dynamicObjectSoftLimit()
                            + " maxObservedDynamicObjects=" + frameResources.maxObservedDynamicObjects()
                            + " uniformUploadSoftLimitBytes=" + uniformUploadSoftLimitBytes
                            + " uniformUploadWarnCooldownRemaining=" + uniformUploadWarnCooldownRemaining
                            + " pendingUploadRangeSoftLimit=" + pendingUploadRangeSoftLimit
                            + " pendingUploadRangeWarnCooldownRemaining=" + pendingUploadRangeWarnCooldownRemaining
                            + " descriptorRingActiveSoftLimit=" + descriptorRingActiveSoftLimit
                            + " descriptorRingActiveWarnCooldownRemaining=" + descriptorRingActiveWarnCooldownRemaining
                            + " descriptorRingWasteWarnCooldownRemaining=" + descriptorRingWasteWarnCooldownRemaining
                            + " descriptorRingCapPressureWarnCooldownRemaining=" + descriptorRingCapPressureWarnCooldownRemaining
                            + " persistentStagingMapped=" + frameResources.persistentStagingMapped()
            ));
            if (frameResources.maxObservedDynamicObjects() > frameResources.dynamicObjectSoftLimit()) {
                warnings.add(new EngineWarning(
                        "DYNAMIC_SCENE_SOFT_LIMIT_EXCEEDED",
                        "Observed dynamic scene objects " + frameResources.maxObservedDynamicObjects()
                                + " exceed configured soft limit " + frameResources.dynamicObjectSoftLimit()
                                + " (hard capacity=" + frameResources.dynamicSceneCapacity() + ")"
                ));
            }
            if (frameResources.descriptorRingSetCapacity() > 0) {
                double wasteRatio = (double) frameResources.descriptorRingWasteSetCount()
                        / (double) frameResources.descriptorRingSetCapacity();
                boolean highWaste = frameResources.descriptorRingSetCapacity() >= descriptorRingWasteWarnMinCapacity
                        && wasteRatio >= descriptorRingWasteWarnRatio;
                descriptorRingWasteHighStreak = highWaste ? (descriptorRingWasteHighStreak + 1) : 0;
                if (descriptorRingWasteHighStreak >= descriptorRingWasteWarnMinFrames) {
                    if (descriptorRingWasteWarnCooldownRemaining <= 0) {
                        warnings.add(new EngineWarning(
                                "DESCRIPTOR_RING_WASTE_HIGH",
                                "Descriptor ring waste ratio "
                                        + String.format(java.util.Locale.ROOT, "%.3f", wasteRatio)
                                        + " sustained for " + descriptorRingWasteHighStreak
                                        + " frames (active=" + frameResources.descriptorRingActiveSetCount()
                                        + ", capacity=" + frameResources.descriptorRingSetCapacity()
                                        + ", threshold=" + descriptorRingWasteWarnRatio + ")"
                        ));
                        descriptorRingWasteWarnCooldownRemaining = descriptorRingWasteWarnCooldownFrames;
                    }
                }
            } else {
                descriptorRingWasteHighStreak = 0;
            }
            if (frameResources.descriptorRingCapBypasses() >= descriptorRingCapPressureWarnMinBypasses) {
                descriptorRingCapPressureStreak++;
                if (descriptorRingCapPressureStreak >= descriptorRingCapPressureWarnMinFrames) {
                    if (descriptorRingCapPressureWarnCooldownRemaining <= 0) {
                        warnings.add(new EngineWarning(
                                "DESCRIPTOR_RING_CAP_PRESSURE",
                                "Descriptor ring cap bypasses=" + frameResources.descriptorRingCapBypasses()
                                        + " (maxSetCapacity=" + frameResources.descriptorRingMaxSetCapacity()
                                        + ", active=" + frameResources.descriptorRingActiveSetCount()
                                        + ", capacity=" + frameResources.descriptorRingSetCapacity() + ")"
                        ));
                        descriptorRingCapPressureWarnCooldownRemaining = descriptorRingCapPressureWarnCooldownFrames;
                    }
                }
            } else {
                descriptorRingCapPressureStreak = 0;
            }
            if (descriptorRingWasteWarnCooldownRemaining > 0) {
                descriptorRingWasteWarnCooldownRemaining--;
            }
            if (descriptorRingCapPressureWarnCooldownRemaining > 0) {
                descriptorRingCapPressureWarnCooldownRemaining--;
            }
            int totalFrameUniformUploadBytes = frameResources.lastFrameUniformUploadBytes() + frameResources.lastFrameGlobalUploadBytes();
            if (totalFrameUniformUploadBytes > uniformUploadSoftLimitBytes) {
                if (uniformUploadWarnCooldownRemaining <= 0) {
                    warnings.add(new EngineWarning(
                            "UNIFORM_UPLOAD_SOFT_LIMIT_EXCEEDED",
                            "Frame uniform upload bytes " + totalFrameUniformUploadBytes
                                    + " exceed soft limit " + uniformUploadSoftLimitBytes
                                    + " (global=" + frameResources.lastFrameGlobalUploadBytes()
                                    + ", object=" + frameResources.lastFrameUniformUploadBytes()
                                    + ", ranges=" + frameResources.lastFrameUniformUploadRanges()
                                    + ", objects=" + frameResources.lastFrameUniformObjectCount() + ")"
                    ));
                    uniformUploadWarnCooldownRemaining = uniformUploadWarnCooldownFrames;
                }
            }
            if (frameResources.lastFrameUniformUploadRanges() > pendingUploadRangeSoftLimit) {
                if (pendingUploadRangeWarnCooldownRemaining <= 0) {
                    warnings.add(new EngineWarning(
                            "PENDING_UPLOAD_RANGE_SOFT_LIMIT_EXCEEDED",
                            "Frame uniform upload ranges " + frameResources.lastFrameUniformUploadRanges()
                                    + " exceed soft limit " + pendingUploadRangeSoftLimit
                                    + " (capacity=" + frameResources.pendingUploadRangeCapacity() + ")"
                    ));
                    pendingUploadRangeWarnCooldownRemaining = pendingUploadRangeWarnCooldownFrames;
                }
            }
            if (frameResources.descriptorRingActiveSetCount() > descriptorRingActiveSoftLimit) {
                if (descriptorRingActiveWarnCooldownRemaining <= 0) {
                    warnings.add(new EngineWarning(
                            "DESCRIPTOR_RING_ACTIVE_SOFT_LIMIT_EXCEEDED",
                            "Descriptor ring active set count " + frameResources.descriptorRingActiveSetCount()
                                    + " exceeds soft limit " + descriptorRingActiveSoftLimit
                                    + " (capacity=" + frameResources.descriptorRingSetCapacity() + ")"
                    ));
                    descriptorRingActiveWarnCooldownRemaining = descriptorRingActiveWarnCooldownFrames;
                }
            }
            if (uniformUploadWarnCooldownRemaining > 0) {
                uniformUploadWarnCooldownRemaining--;
            }
            if (pendingUploadRangeWarnCooldownRemaining > 0) {
                pendingUploadRangeWarnCooldownRemaining--;
            }
            if (descriptorRingActiveWarnCooldownRemaining > 0) {
                descriptorRingActiveWarnCooldownRemaining--;
            }
            if (currentShadows.enabled()) {
                ShadowCascadeProfile shadow = context.shadowCascadeProfile();
                warnings.add(new EngineWarning(
                        "SHADOW_CASCADE_PROFILE",
                        "enabled=" + shadow.enabled()
                                + " cascades=" + shadow.cascadeCount()
                                + " mapRes=" + shadow.mapResolution()
                                + " pcfRadius=" + shadow.pcfRadius()
                                + " bias=" + shadow.bias()
                                + " splitNdc=[" + shadow.split1Ndc() + "," + shadow.split2Ndc() + "," + shadow.split3Ndc() + "]"
                ));
            }
            PostProcessPipelineProfile postProfile = context.postProcessPipelineProfile();
            warnings.add(new EngineWarning(
                    "VULKAN_POST_PROCESS_PIPELINE",
                    "offscreenRequested=" + postProfile.offscreenRequested()
                            + " offscreenActive=" + postProfile.offscreenActive()
                            + " mode=" + postProfile.mode()
            ));
        } else if (postOffscreenRequested) {
            warnings.add(new EngineWarning(
                    "VULKAN_POST_PROCESS_PIPELINE",
                    "offscreenRequested=true offscreenActive=false mode=shader-fallback"
            ));
        }
        return warnings;
    }

    SceneReuseStats debugSceneReuseStats() {
        return context.sceneReuseStats();
    }

    FrameResourceProfile debugFrameResourceProfile() {
        return context.frameResourceProfile();
    }

    FrameResourceConfig debugFrameResourceConfig() {
        return new FrameResourceConfig(
                context.configuredFramesInFlight(),
                context.configuredMaxDynamicSceneObjects(),
                context.configuredMaxPendingUploadRanges(),
                context.configuredDescriptorRingMaxSetCapacity(),
                meshGeometryCacheMaxEntries
        );
    }

    private static int parseIntOption(Map<String, String> options, String key, int fallback, int min, int max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return clampInt(Integer.parseInt(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLongOption(Map<String, String> options, String key, long fallback, long min, long max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return clampLong(Long.parseLong(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDoubleOption(Map<String, String> options, String key, double fallback, double min, double max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return clampDouble(Double.parseDouble(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private record MeshGeometryCacheProfile(long hits, long misses, long evictions, int entries, int maxEntries) {
    }

    record FrameResourceConfig(
            int framesInFlight,
            int maxDynamicSceneObjects,
            int maxPendingUploadRanges,
            int maxTextureDescriptorSets,
            int meshGeometryCacheEntries
    ) {
    }

    static record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps, boolean degraded) {
    }

    static record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
    }

    static record PostProcessRenderConfig(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
    }

    static record IblRenderConfig(
            boolean enabled,
            float diffuseStrength,
            float specularStrength,
            boolean textureDriven,
            boolean skyboxDerived,
            boolean ktxContainerRequested,
            boolean ktxSkyboxFallback,
            int ktxDecodeUnavailableCount,
            int ktxTranscodeRequiredCount,
            int ktxUnsupportedVariantCount,
            float prefilterStrength,
            boolean degraded,
            int missingAssetCount,
            Path irradiancePath,
            Path radiancePath,
            Path brdfLutPath
    ) {
    }


    static record ShadowRenderConfig(
            boolean enabled,
            float strength,
            float bias,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            boolean degraded
    ) {
    }

    static record CameraMatrices(float[] view, float[] proj) {
    }

    static record LightingConfig(
            float[] directionalDirection,
            float[] directionalColor,
            float directionalIntensity,
            float[] pointPosition,
            float[] pointColor,
            float pointIntensity,
            float[] pointDirection,
            float pointInnerCos,
            float pointOuterCos,
            boolean pointIsSpot,
            float pointRange,
            boolean pointCastsShadows
    ) {
    }

}
