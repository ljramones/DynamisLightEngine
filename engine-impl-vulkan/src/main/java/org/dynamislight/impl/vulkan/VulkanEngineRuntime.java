package org.dynamislight.impl.vulkan;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private IblRenderConfig currentIbl = new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0f, false, 0, null, null, null);
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
        int framesInFlight = parseIntOption(backendOptions, "vulkan.framesInFlight", 3, 2, 4);
        int maxDynamicSceneObjects = parseIntOption(backendOptions, "vulkan.maxDynamicSceneObjects", 2048, 256, 8192);
        int maxPendingUploadRanges = parseIntOption(backendOptions, "vulkan.maxPendingUploadRanges", 64, 8, 512);
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
        context.configureFrameResources(framesInFlight, maxDynamicSceneObjects, maxPendingUploadRanges);
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
        CameraDesc camera = selectActiveCamera(scene);
        CameraMatrices cameraMatrices = cameraMatricesFor(camera, safeAspect(viewportWidth, viewportHeight));
        LightingConfig lighting = mapLighting(scene == null ? null : scene.lights());
        ShadowRenderConfig shadows = mapShadows(scene == null ? null : scene.lights(), qualityTier);
        FogRenderConfig fog = mapFog(scene == null ? null : scene.fog(), qualityTier);
        SmokeRenderConfig smoke = mapSmoke(scene == null ? null : scene.smokeEmitters(), qualityTier);
        PostProcessRenderConfig post = mapPostProcess(scene == null ? null : scene.postProcess(), qualityTier);
        IblRenderConfig ibl = mapIbl(scene == null ? null : scene.environment(), qualityTier);
        currentFog = fog;
        currentSmoke = smoke;
        currentShadows = shadows;
        currentPost = post;
        currentIbl = ibl;
        nonDirectionalShadowRequested = hasNonDirectionalShadowRequest(scene == null ? null : scene.lights());
        List<VulkanContext.SceneMeshData> sceneMeshes = buildSceneMeshes(scene);
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
        VulkanContext.VulkanFrameMetrics frame = context.renderFrame();
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
                        "KTX/KTX2 IBL assets detected but no native decode path is active (channels="
                                + currentIbl.ktxDecodeUnavailableCount()
                                + "); runtime used sidecar/derived/default fallback inputs"
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
            VulkanContext.SceneReuseStats reuse = context.sceneReuseStats();
            warnings.add(new EngineWarning(
                    "SCENE_REUSE_PROFILE",
                    "reuseHits=" + reuse.reuseHits()
                            + " reorderReuseHits=" + reuse.reorderReuseHits()
                            + " fullRebuilds=" + reuse.fullRebuilds()
                            + " meshBufferRebuilds=" + reuse.meshBufferRebuilds()
                            + " descriptorPoolBuilds=" + reuse.descriptorPoolBuilds()
                            + " descriptorPoolRebuilds=" + reuse.descriptorPoolRebuilds()
            ));
            VulkanContext.FrameResourceProfile frameResources = context.frameResourceProfile();
            warnings.add(new EngineWarning(
                    "VULKAN_FRAME_RESOURCE_PROFILE",
                    "framesInFlight=" + frameResources.framesInFlight()
                            + " descriptorSetsInRing=" + frameResources.descriptorSetsInRing()
                            + " uniformStrideBytes=" + frameResources.uniformStrideBytes()
                            + " uniformFrameSpanBytes=" + frameResources.uniformFrameSpanBytes()
                            + " dynamicSceneCapacity=" + frameResources.dynamicSceneCapacity()
                            + " pendingUploadRangeCapacity=" + frameResources.pendingUploadRangeCapacity()
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
                            + " descriptorRingWasteWarnCooldownRemaining=" + descriptorRingWasteWarnCooldownRemaining
                            + " descriptorRingCapPressureWarnCooldownRemaining=" + descriptorRingCapPressureWarnCooldownRemaining
                            + " persistentStagingMapped=" + frameResources.persistentStagingMapped()
            ));
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
            if (currentShadows.enabled()) {
                VulkanContext.ShadowCascadeProfile shadow = context.shadowCascadeProfile();
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
            VulkanContext.PostProcessPipelineProfile postProfile = context.postProcessPipelineProfile();
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

    private List<VulkanContext.SceneMeshData> buildSceneMeshes(SceneDescriptor scene) {
        if (scene == null || scene.meshes() == null || scene.meshes().isEmpty()) {
            return List.of(VulkanContext.SceneMeshData.defaultTriangle());
        }
        Map<String, MaterialDesc> materials = scene.materials() == null ? Map.of() : scene.materials().stream()
                .filter(m -> m != null && m.id() != null)
                .collect(java.util.stream.Collectors.toMap(MaterialDesc::id, m -> m, (a, b) -> a));
        Map<String, TransformDesc> transforms = scene.transforms() == null ? Map.of() : scene.transforms().stream()
                .filter(t -> t != null && t.id() != null)
                .collect(java.util.stream.Collectors.toMap(TransformDesc::id, t -> t, (a, b) -> a));

        List<VulkanContext.SceneMeshData> out = new java.util.ArrayList<>(scene.meshes().size());
        for (int i = 0; i < scene.meshes().size(); i++) {
            MeshDesc mesh = scene.meshes().get(i);
            if (mesh == null) {
                continue;
            }
            VulkanGltfMeshParser.MeshGeometry geometry = meshLoader.loadMeshGeometry(mesh, i);
            MaterialDesc material = materials.get(mesh.materialId());
            float[] color = materialToColor(material);
            float metallic = material == null ? 0.0f : clamp01(material.metallic());
            float roughness = material == null ? 0.6f : clamp01(material.roughness());
            float[] model = modelMatrixOf(transforms.get(mesh.transformId()), i);
            String stableMeshId = (mesh.id() == null || mesh.id().isBlank()) ? ("mesh-index-" + i) : mesh.id();
            VulkanContext.SceneMeshData meshData = new VulkanContext.SceneMeshData(
                    stableMeshId,
                    geometry.vertices(),
                    geometry.indices(),
                    model,
                    color,
                    metallic,
                    roughness,
                    resolveTexturePath(material == null ? null : material.albedoTexturePath()),
                    resolveTexturePath(material == null ? null : material.normalTexturePath()),
                    resolveTexturePath(material == null ? null : material.metallicRoughnessTexturePath()),
                    resolveTexturePath(material == null ? null : material.occlusionTexturePath())
            );
            out.add(meshData);
        }
        return out.isEmpty() ? List.of(VulkanContext.SceneMeshData.defaultTriangle()) : List.copyOf(out);
    }

    VulkanContext.SceneReuseStats debugSceneReuseStats() {
        return context.sceneReuseStats();
    }

    VulkanContext.FrameResourceProfile debugFrameResourceProfile() {
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

    private float[] materialToColor(MaterialDesc material) {
        Vec3 albedo = material == null ? null : material.albedo();
        if (albedo == null) {
            return new float[]{1f, 1f, 1f, 1f};
        }
        return new float[]{
                clamp01(albedo.x()),
                clamp01(albedo.y()),
                clamp01(albedo.z()),
                1f
        };
    }

    private Path resolveTexturePath(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        Path path = Path.of(texturePath);
        return path.isAbsolute() ? path.normalize() : assetRoot.resolve(path).normalize();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
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

    private record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps, boolean degraded) {
    }

    private record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
    }

    private record PostProcessRenderConfig(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
    }

    private record IblRenderConfig(
            boolean enabled,
            float diffuseStrength,
            float specularStrength,
            boolean textureDriven,
            boolean skyboxDerived,
            boolean ktxContainerRequested,
            boolean ktxSkyboxFallback,
            int ktxDecodeUnavailableCount,
            float prefilterStrength,
            boolean degraded,
            int missingAssetCount,
            Path irradiancePath,
            Path radiancePath,
            Path brdfLutPath
    ) {
    }

    private static PostProcessRenderConfig mapPostProcess(PostProcessDesc desc, QualityTier qualityTier) {
        if (desc == null || !desc.enabled()) {
            return new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f);
        }
        float tierExposureScale = switch (qualityTier) {
            case LOW -> 0.9f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.05f;
            case ULTRA -> 1.1f;
        };
        float exposure = Math.max(0.25f, Math.min(4.0f, desc.exposure() * tierExposureScale));
        float gamma = Math.max(1.6f, Math.min(2.6f, desc.gamma()));
        float bloomThreshold = Math.max(0.2f, Math.min(2.5f, desc.bloomThreshold()));
        float bloomStrength = Math.max(0f, Math.min(1.6f, desc.bloomStrength()));
        boolean bloomEnabled = desc.bloomEnabled() && qualityTier != QualityTier.LOW;
        return new PostProcessRenderConfig(
                desc.tonemapEnabled(),
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength
        );
    }

    private IblRenderConfig mapIbl(EnvironmentDesc environment, QualityTier qualityTier) {
        return mapIbl(environment, qualityTier, assetRoot);
    }

    private static IblRenderConfig mapIbl(EnvironmentDesc environment, QualityTier qualityTier, Path assetRoot) {
        if (environment == null) {
            return new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0f, false, 0, null, null, null);
        }
        boolean enabled = !isBlank(environment.iblIrradiancePath())
                || !isBlank(environment.iblRadiancePath())
                || !isBlank(environment.iblBrdfLutPath())
                || !isBlank(environment.skyboxAssetPath());
        if (!enabled) {
            return new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0f, false, 0, null, null, null);
        }
        float tierScale = switch (qualityTier) {
            case LOW -> 0.62f;
            case MEDIUM -> 0.82f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.15f;
        };
        float diffuse = 0.42f * tierScale;
        float specular = 0.30f * tierScale;
        float prefilterStrength = switch (qualityTier) {
            case LOW -> 0.38f;
            case MEDIUM -> 0.62f;
            case HIGH -> 0.85f;
            case ULTRA -> 1.0f;
        };
        boolean degraded = qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        boolean textureDriven = false;

        String fallbackSkyboxPath = environment.skyboxAssetPath();
        boolean skyboxDerived = !isBlank(fallbackSkyboxPath)
                && (isBlank(environment.iblIrradiancePath()) || isBlank(environment.iblRadiancePath()));
        Path irrSource = resolveScenePath(
                isBlank(environment.iblIrradiancePath()) ? fallbackSkyboxPath : environment.iblIrradiancePath(),
                assetRoot
        );
        Path radSource = resolveScenePath(
                isBlank(environment.iblRadiancePath()) ? fallbackSkyboxPath : environment.iblRadiancePath(),
                assetRoot
        );
        Path brdfSource = resolveScenePath(environment.iblBrdfLutPath(), assetRoot);
        boolean ktxContainerRequested = isKtxContainerPath(irrSource)
                || isKtxContainerPath(radSource)
                || isKtxContainerPath(brdfSource);

        Path irr = resolveContainerSourcePath(irrSource);
        Path rad = resolveContainerSourcePath(radSource);
        Path brdf = resolveContainerSourcePath(brdfSource);
        boolean ktxSkyboxFallback = false;
        Path skyboxResolved = resolveContainerSourcePath(resolveScenePath(fallbackSkyboxPath, assetRoot));
        if (isKtxContainerPath(irrSource) && !isRegularFile(irr) && isRegularFile(skyboxResolved)) {
            irr = skyboxResolved;
            ktxSkyboxFallback = true;
        }
        if (isKtxContainerPath(radSource) && !isRegularFile(rad) && isRegularFile(skyboxResolved)) {
            rad = skyboxResolved;
            ktxSkyboxFallback = true;
        }
        boolean skyboxDerivedActive = skyboxDerived || ktxSkyboxFallback;
        int ktxDecodeUnavailableCount = 0;
        ktxDecodeUnavailableCount += decodeUnavailableChannelCount(irrSource, irr);
        ktxDecodeUnavailableCount += decodeUnavailableChannelCount(radSource, rad);
        ktxDecodeUnavailableCount += decodeUnavailableChannelCount(brdfSource, brdf);
        int missingAssetCount = countMissingFiles(irr, rad, brdf);
        float irrSignal = imageLuminanceSignal(irr);
        float radSignal = imageLuminanceSignal(rad);
        float brdfSignal = imageLuminanceSignal(brdf);
        if (irrSignal >= 0f || radSignal >= 0f || brdfSignal >= 0f) {
            float irrUsed = irrSignal < 0f ? 0.5f : irrSignal;
            float radUsed = radSignal < 0f ? 0.5f : radSignal;
            float brdfUsed = brdfSignal < 0f ? 0.5f : brdfSignal;
            float diffuseScale = 0.82f + 0.36f * irrUsed;
            float specScale = 0.78f + 0.42f * ((radUsed * 0.6f) + (brdfUsed * 0.4f));
            diffuse *= diffuseScale;
            specular *= specScale;
            prefilterStrength = Math.max(prefilterStrength, 0.65f + 0.35f * radUsed);
            textureDriven = true;
        }

        return new IblRenderConfig(
                true,
                Math.max(0f, Math.min(2.0f, diffuse)),
                Math.max(0f, Math.min(2.0f, specular)),
                textureDriven,
                skyboxDerivedActive,
                ktxContainerRequested,
                ktxSkyboxFallback,
                ktxDecodeUnavailableCount,
                Math.max(0f, Math.min(1f, prefilterStrength)),
                degraded,
                missingAssetCount,
                irr,
                rad,
                brdf
        );
    }

    private static int countMissingFiles(Path... paths) {
        int missing = 0;
        for (Path path : paths) {
            if (!isRegularFile(path)) {
                missing++;
            }
        }
        return missing;
    }

    private static boolean isRegularFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    private static int decodeUnavailableChannelCount(Path requestedPath, Path resolvedPath) {
        if (!isKtxContainerPath(requestedPath) || !isRegularFile(requestedPath)) {
            return 0;
        }
        return Objects.equals(requestedPath, resolvedPath) ? 1 : 0;
    }

    private record ShadowRenderConfig(
            boolean enabled,
            float strength,
            float bias,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            boolean degraded
    ) {
    }

    private record CameraMatrices(float[] view, float[] proj) {
    }

    private record LightingConfig(
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

    private static CameraDesc selectActiveCamera(SceneDescriptor scene) {
        if (scene == null || scene.cameras() == null || scene.cameras().isEmpty()) {
            return new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f);
        }
        if (scene.activeCameraId() != null && !scene.activeCameraId().isBlank()) {
            for (CameraDesc camera : scene.cameras()) {
                if (scene.activeCameraId().equals(camera.id())) {
                    return camera;
                }
            }
        }
        return scene.cameras().getFirst();
    }

    private static CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
        CameraDesc effective = camera == null
                ? new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f)
                : camera;

        Vec3 pos = effective.position() == null ? new Vec3(0f, 0f, 5f) : effective.position();
        Vec3 rot = effective.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : effective.rotationEulerDeg();
        float yaw = radians(rot.y());
        float pitch = radians(rot.x());

        float fx = (float) (Math.cos(pitch) * Math.sin(yaw));
        float fy = (float) Math.sin(pitch);
        float fz = (float) (-Math.cos(pitch) * Math.cos(yaw));

        float[] view = lookAt(
                pos.x(), pos.y(), pos.z(),
                pos.x() + fx, pos.y() + fy, pos.z() + fz,
                0f, 1f, 0f
        );

        float near = effective.nearPlane() > 0f ? effective.nearPlane() : 0.1f;
        float far = effective.farPlane() > near ? effective.farPlane() : 100f;
        float fov = effective.fovDegrees() > 1f ? effective.fovDegrees() : 60f;
        float aspect = aspectRatio > 0.01f ? aspectRatio : (16f / 9f);
        float[] proj = perspective(radians(fov), aspect, near, far);
        return new CameraMatrices(view, proj);
    }

    private static LightingConfig mapLighting(List<LightDesc> lights) {
        float[] dir = new float[]{0.35f, -1.0f, 0.25f};
        float[] dirColor = new float[]{1.0f, 0.98f, 0.95f};
        float dirIntensity = 1.0f;
        float[] pointPos = new float[]{0f, 1.3f, 1.8f};
        float[] pointColor = new float[]{0.95f, 0.62f, 0.22f};
        float pointIntensity = 1.0f;
        float[] pointDir = new float[]{0f, -1f, 0f};
        float pointInnerCos = 1.0f;
        float pointOuterCos = 1.0f;
        boolean pointIsSpot = false;
        float pointRange = 15f;
        boolean pointCastsShadows = false;
        if (lights == null || lights.isEmpty()) {
            return new LightingConfig(
                    dir, dirColor, dirIntensity,
                    pointPos, pointColor, pointIntensity,
                    pointDir, pointInnerCos, pointOuterCos, pointIsSpot, pointRange, pointCastsShadows
            );
        }
        LightDesc directional = null;
        LightDesc pointLike = null;
        for (LightDesc light : lights) {
            if (light == null) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (directional == null && type == LightType.DIRECTIONAL) {
                directional = light;
            }
            if (pointLike == null && (type == LightType.POINT || type == LightType.SPOT)) {
                pointLike = light;
            }
        }
        if (directional == null) {
            directional = lights.getFirst();
        }
        if (pointLike == null && lights.size() > 1) {
            pointLike = lights.get(1);
        }
        if (directional != null && directional.color() != null) {
            dirColor = new float[]{
                    clamp01(directional.color().x()),
                    clamp01(directional.color().y()),
                    clamp01(directional.color().z())
            };
        }
        if (directional != null) {
            dirIntensity = Math.max(0f, directional.intensity());
            if (directional.direction() != null) {
                dir = normalize3(new float[]{
                        directional.direction().x(),
                        directional.direction().y(),
                        directional.direction().z()
                });
            }
        }
        if (pointLike != null && pointLike.color() != null) {
            pointColor = new float[]{
                    clamp01(pointLike.color().x()),
                    clamp01(pointLike.color().y()),
                    clamp01(pointLike.color().z())
            };
        }
        if (pointLike != null) {
            pointIntensity = Math.max(0f, pointLike.intensity());
            pointRange = pointLike.range() > 0f ? pointLike.range() : 15f;
            if (pointLike.position() != null) {
                pointPos = new float[]{pointLike.position().x(), pointLike.position().y(), pointLike.position().z()};
            }
            LightType pointType = pointLike.type() == null ? LightType.DIRECTIONAL : pointLike.type();
            if (pointType == LightType.SPOT) {
                pointIsSpot = true;
                if (pointLike.direction() != null) {
                    pointDir = normalize3(new float[]{
                            pointLike.direction().x(),
                            pointLike.direction().y(),
                            pointLike.direction().z()
                    });
                }
                float inner = cosFromDegrees(pointLike.innerConeDegrees());
                float outer = cosFromDegrees(pointLike.outerConeDegrees());
                pointInnerCos = Math.max(inner, outer);
                pointOuterCos = Math.min(inner, outer);
            }
            pointCastsShadows = pointType == LightType.POINT && pointLike.castsShadows();
        }
        return new LightingConfig(
                dir, dirColor, dirIntensity,
                pointPos, pointColor, pointIntensity,
                pointDir, pointInnerCos, pointOuterCos, pointIsSpot, pointRange, pointCastsShadows
        );
    }

    private static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
        return false;
    }

    private static float[] normalize3(float[] v) {
        if (v == null || v.length != 3) {
            return new float[]{0f, -1f, 0f};
        }
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0e-6f) {
            return new float[]{0f, -1f, 0f};
        }
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }

    private static float cosFromDegrees(float degrees) {
        float clamped = Math.max(0f, Math.min(89.9f, degrees));
        return (float) Math.cos(Math.toRadians(clamped));
    }

    private static ShadowRenderConfig mapShadows(List<LightDesc> lights, QualityTier qualityTier) {
        if (lights == null || lights.isEmpty()) {
            return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
        }
        for (LightDesc light : lights) {
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            ShadowDesc shadow = light.shadow();
            int kernel = shadow == null ? 3 : Math.max(1, shadow.pcfKernelSize());
            int cascades = shadow == null ? 1 : Math.max(1, shadow.cascadeCount());
            if (type == LightType.SPOT) {
                cascades = 1;
            } else if (type == LightType.POINT) {
                cascades = 6;
            }
            int resolution = shadow == null ? 1024 : Math.max(256, Math.min(4096, shadow.mapResolution()));
            float bias = shadow == null ? 0.0015f : Math.max(0.00002f, shadow.depthBias());
            int maxKernel = switch (qualityTier) {
                case LOW -> 3;
                case MEDIUM -> 5;
                case HIGH -> 7;
                case ULTRA -> 9;
            };
            int kernelClamped = Math.min(kernel, maxKernel);
            int radius = Math.max(0, (kernelClamped - 1) / 2);
            int maxCascades = switch (qualityTier) {
                case LOW -> type == LightType.POINT ? 6 : 1;
                case MEDIUM -> type == LightType.POINT ? 6 : 2;
                case HIGH -> type == LightType.POINT ? 6 : 3;
                case ULTRA -> type == LightType.POINT ? 6 : 4;
            };
            int cascadesClamped = Math.min(cascades, maxCascades);
            float base = Math.min(0.9f, 0.25f + (kernelClamped * 0.04f) + (cascadesClamped * 0.05f));
            float tierScale = switch (qualityTier) {
                case LOW -> 0.55f;
                case MEDIUM -> 0.75f;
                case HIGH -> 1.0f;
                case ULTRA -> 1.15f;
            };
            boolean degraded = kernelClamped != kernel || cascadesClamped != cascades || qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
            return new ShadowRenderConfig(
                    true,
                    Math.max(0.2f, Math.min(0.9f, base * tierScale)),
                    bias,
                    radius,
                    cascadesClamped,
                    resolution,
                    degraded
            );
        }
        return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
    }

    private static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        if (fogDesc == null || !fogDesc.enabled() || fogDesc.mode() == FogMode.NONE) {
            return new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
        }
        float tierDensityScale = switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.2f;
        };
        int tierSteps = switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case ULTRA -> 0;
        };
        float density = Math.max(0f, fogDesc.density() * tierDensityScale);
        return new FogRenderConfig(
                true,
                fogDesc.color() == null ? 0.5f : fogDesc.color().x(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().y(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().z(),
                density,
                tierSteps,
                qualityTier == QualityTier.LOW
        );
    }

    private static SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        if (emitters == null || emitters.isEmpty()) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }
        int enabledCount = 0;
        float densityAccum = 0f;
        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (SmokeEmitterDesc emitter : emitters) {
            if (!emitter.enabled()) {
                continue;
            }
            enabledCount++;
            densityAccum += Math.max(0f, emitter.density());
            r += emitter.albedo() == null ? 0.6f : emitter.albedo().x();
            g += emitter.albedo() == null ? 0.6f : emitter.albedo().y();
            b += emitter.albedo() == null ? 0.6f : emitter.albedo().z();
        }
        if (enabledCount == 0) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }
        float avgR = r / enabledCount;
        float avgG = g / enabledCount;
        float avgB = b / enabledCount;
        float baseIntensity = Math.min(0.85f, densityAccum / enabledCount);
        float tierScale = switch (qualityTier) {
            case LOW -> 0.45f;
            case MEDIUM -> 0.7f;
            case HIGH -> 0.9f;
            case ULTRA -> 1.0f;
        };
        return new SmokeRenderConfig(true, avgR, avgG, avgB, Math.min(0.85f, baseIntensity * tierScale),
                qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM);
    }

    private static float[] modelMatrixOf(TransformDesc transform, int meshIndex) {
        if (transform == null) {
            float[] model = identityMatrix();
            model[12] = (meshIndex - 1) * 0.35f;
            return model;
        }
        Vec3 pos = transform.position() == null ? new Vec3(0f, 0f, 0f) : transform.position();
        Vec3 rot = transform.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : transform.rotationEulerDeg();
        Vec3 scl = transform.scale() == null ? new Vec3(1f, 1f, 1f) : transform.scale();

        float[] translation = translationMatrix(pos.x(), pos.y(), pos.z());
        float[] rotation = mul(mul(rotationZ(radians(rot.z())), rotationY(radians(rot.y()))), rotationX(radians(rot.x())));
        float[] scale = scaleMatrix(scl.x(), scl.y(), scl.z());
        return mul(translation, mul(rotation, scale));
    }

    private static float safeAspect(int width, int height) {
        if (height <= 0) {
            return 16f / 9f;
        }
        return Math.max(0.1f, (float) width / (float) height);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Path resolveScenePath(String sourcePath, Path assetRoot) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        Path path = Path.of(sourcePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (assetRoot == null) {
            return path.normalize();
        }
        return assetRoot.resolve(path).normalize();
    }

    private static float imageLuminanceSignal(Path path) {
        Path sourcePath = resolveContainerSourcePath(path);
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return -1f;
        }
        String name = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString().toLowerCase();
        boolean imageIoSupported = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        boolean hdrSupported = name.endsWith(".hdr");
        if (!imageIoSupported && !hdrSupported) {
            return -1f;
        }
        if (hdrSupported) {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var x = stack.mallocInt(1);
                var y = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                FloatBuffer hdr = org.lwjgl.stb.STBImage.stbi_loadf(sourcePath.toAbsolutePath().toString(), x, y, channels, 3);
                if (hdr == null || x.get(0) <= 0 || y.get(0) <= 0) {
                    return -1f;
                }
                try {
                    int width = x.get(0);
                    int height = y.get(0);
                    int stepX = Math.max(1, width / 64);
                    int stepY = Math.max(1, height / 64);
                    double sum = 0.0;
                    int count = 0;
                    for (int yIdx = 0; yIdx < height; yIdx += stepY) {
                        for (int xIdx = 0; xIdx < width; xIdx += stepX) {
                            int idx = (yIdx * width + xIdx) * 3;
                            float r = hdr.get(idx);
                            float g = hdr.get(idx + 1);
                            float b = hdr.get(idx + 2);
                            float ldrR = toneMapLdr(r);
                            float ldrG = toneMapLdr(g);
                            float ldrB = toneMapLdr(b);
                            sum += (0.2126 * ldrR) + (0.7152 * ldrG) + (0.0722 * ldrB);
                            count++;
                        }
                    }
                    if (count == 0) {
                        return -1f;
                    }
                    return (float) Math.max(0.0, Math.min(1.0, sum / count));
                } finally {
                    org.lwjgl.stb.STBImage.stbi_image_free(hdr);
                }
            } catch (Throwable ignored) {
                return -1f;
            }
        }
        try {
            BufferedImage image = javax.imageio.ImageIO.read(sourcePath.toFile());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return -1f;
            }
            int stepX = Math.max(1, image.getWidth() / 64);
            int stepY = Math.max(1, image.getHeight() / 64);
            double sum = 0.0;
            int count = 0;
            for (int y = 0; y < image.getHeight(); y += stepY) {
                for (int x = 0; x < image.getWidth(); x += stepX) {
                    int argb = image.getRGB(x, y);
                    float r = ((argb >> 16) & 0xFF) / 255f;
                    float g = ((argb >> 8) & 0xFF) / 255f;
                    float b = (argb & 0xFF) / 255f;
                    sum += (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
                    count++;
                }
            }
            if (count == 0) {
                return -1f;
            }
            return (float) Math.max(0.0, Math.min(1.0, sum / count));
        } catch (IOException ignored) {
            return -1f;
        }
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

    private static float toneMapLdr(float hdrValue) {
        float toneMapped = hdrValue / (1.0f + Math.max(0f, hdrValue));
        return (float) Math.pow(Math.max(0f, toneMapped), 1.0 / 2.2);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] translationMatrix(float x, float y, float z) {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f
        };
    }

    private static float[] scaleMatrix(float x, float y, float z) {
        return new float[]{
                x, 0f, 0f, 0f,
                0f, y, 0f, 0f,
                0f, 0f, z, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] rotationX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, c, s, 0f,
                0f, -s, c, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] rotationY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                c, 0f, -s, 0f,
                0f, 1f, 0f, 0f,
                s, 0f, c, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] rotationZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                c, s, 0f, 0f,
                -s, c, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
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
}
