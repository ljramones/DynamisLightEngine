package org.dynamislight.impl.vulkan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;

final class VulkanRuntimeWarningPolicy {
    static final class State {
        int descriptorRingWasteHighStreak;
        int descriptorRingWasteWarnCooldownRemaining;
        int descriptorRingCapPressureStreak;
        int descriptorRingCapPressureWarnCooldownRemaining;
        int uniformUploadWarnCooldownRemaining;
        int pendingUploadRangeWarnCooldownRemaining;
        int descriptorRingActiveWarnCooldownRemaining;
    }

    static final class Config {
        double descriptorRingWasteWarnRatio = 0.85;
        int descriptorRingWasteWarnMinFrames = 8;
        int descriptorRingWasteWarnMinCapacity = 64;
        int descriptorRingWasteWarnCooldownFrames = 120;
        long descriptorRingCapPressureWarnMinBypasses = 4;
        int descriptorRingCapPressureWarnMinFrames = 2;
        int descriptorRingCapPressureWarnCooldownFrames = 120;
        int uniformUploadSoftLimitBytes = 2 * 1024 * 1024;
        int uniformUploadWarnCooldownFrames = 120;
        int pendingUploadRangeSoftLimit = 48;
        int pendingUploadRangeWarnCooldownFrames = 120;
        int descriptorRingActiveSoftLimit = 2048;
        int descriptorRingActiveWarnCooldownFrames = 120;
    }

    void reset(State state) {
        state.descriptorRingWasteHighStreak = 0;
        state.descriptorRingCapPressureStreak = 0;
        state.descriptorRingWasteWarnCooldownRemaining = 0;
        state.descriptorRingCapPressureWarnCooldownRemaining = 0;
        state.uniformUploadWarnCooldownRemaining = 0;
        state.pendingUploadRangeWarnCooldownRemaining = 0;
        state.descriptorRingActiveWarnCooldownRemaining = 0;
    }

    List<EngineWarning> frameWarnings(State state, Config cfg, Inputs in) {
        List<EngineWarning> warnings = new ArrayList<>();
        if (in.currentSmoke().enabled() && in.currentSmoke().degraded()) {
            warnings.add(new EngineWarning(
                    "SMOKE_QUALITY_DEGRADED",
                    "Smoke rendering quality reduced for tier " + in.qualityTier() + " to maintain performance"
            ));
        }
        if (in.currentFog().enabled() && in.currentFog().degraded()) {
            warnings.add(new EngineWarning(
                    "FOG_QUALITY_DEGRADED",
                    "Fog sampling reduced at LOW quality tier"
            ));
        }
        if (in.currentShadows().enabled() && in.currentShadows().degraded()) {
            warnings.add(new EngineWarning(
                    "SHADOW_QUALITY_DEGRADED",
                    "Shadow quality reduced for tier " + in.qualityTier() + " to maintain performance"
            ));
        }
        if (in.currentPost().ssaoEnabled() && in.qualityTier() == QualityTier.MEDIUM) {
            warnings.add(new EngineWarning(
                    "SSAO_QUALITY_DEGRADED",
                    "SSAO-lite strength reduced at MEDIUM tier to maintain stable frame cost"
            ));
        }
        if (in.currentPost().smaaEnabled() && in.qualityTier() == QualityTier.MEDIUM) {
            warnings.add(new EngineWarning(
                    "SMAA_QUALITY_DEGRADED",
                    "SMAA-lite strength reduced at MEDIUM tier to maintain stable frame cost"
            ));
        }
        if (in.currentPost().taaEnabled() && in.qualityTier() == QualityTier.MEDIUM) {
            warnings.add(new EngineWarning(
                    "TAA_QUALITY_DEGRADED",
                    "TAA blend reduced at MEDIUM tier to maintain stable frame cost"
            ));
        }
        if (in.currentPost().taaEnabled()) {
            warnings.add(new EngineWarning(
                    "TAA_BASELINE_ACTIVE",
                    "TAA baseline temporal blend path is active"
            ));
        }
        if (in.upscalerMode() != VulkanEngineRuntime.UpscalerMode.NONE && in.currentPost().taaEnabled()) {
            warnings.add(new EngineWarning(
                    "UPSCALER_HOOK_ACTIVE",
                    "Upscaler hook requested (mode=" + in.upscalerMode().name().toLowerCase()
                            + ", quality=" + in.upscalerQuality().name().toLowerCase() + ")"
            ));
            warnings.add(new EngineWarning(
                    "UPSCALER_NATIVE_INTEGRATION_PENDING",
                    "Upscaler hook is active for TSR/TUUA tuning; native vendor SDK path remains optional integration work"
            ));
        }
        if (in.nonDirectionalShadowRequested()) {
            warnings.add(new EngineWarning(
                    "SHADOW_TYPE_UNSUPPORTED",
                    "Point shadow maps are not implemented yet; current shadow-map path supports directional and spot lights"
            ));
        }
        if (in.currentIbl().enabled()) {
            warnings.add(new EngineWarning(
                    "IBL_BASELINE_ACTIVE",
                    "IBL baseline enabled using "
                            + (in.currentIbl().textureDriven() ? "texture-driven" : "path-driven")
                            + " environment diffuse/specular approximations"
            ));
            warnings.add(new EngineWarning(
                    "IBL_PREFILTER_APPROX_ACTIVE",
                    "IBL roughness-aware radiance prefilter approximation active (strength="
                            + in.currentIbl().prefilterStrength() + ")"
            ));
            warnings.add(new EngineWarning("IBL_MULTI_TAP_SPEC_ACTIVE", "IBL specular radiance uses roughness-aware multi-tap filtering for improved highlight stability"));
            warnings.add(new EngineWarning("IBL_MIP_LOD_PREFILTER_ACTIVE", "IBL specular prefilter sampling uses roughness-driven mip/LOD selection"));
            warnings.add(new EngineWarning("IBL_BRDF_ENERGY_COMP_ACTIVE", "IBL diffuse/specular response uses BRDF energy-compensation and horizon weighting for improved roughness realism"));
            if (in.currentIbl().skyboxDerived()) warnings.add(new EngineWarning("IBL_SKYBOX_DERIVED_ACTIVE", "IBL irradiance/radiance inputs are derived from EnvironmentDesc.skyboxAssetPath"));
            if (in.currentIbl().ktxSkyboxFallback()) warnings.add(new EngineWarning("IBL_KTX_SKYBOX_FALLBACK_ACTIVE", "KTX IBL paths without decodable sources fell back to skybox-derived irradiance/radiance inputs"));
            if (in.currentIbl().ktxDecodeUnavailableCount() > 0) warnings.add(new EngineWarning("IBL_KTX_DECODE_UNAVAILABLE", "KTX/KTX2 IBL assets detected but could not be decoded by current baseline path (channels=" + in.currentIbl().ktxDecodeUnavailableCount() + "); runtime used sidecar/derived/default fallback inputs"));
            if (in.currentIbl().ktxTranscodeRequiredCount() > 0) warnings.add(new EngineWarning("IBL_KTX_TRANSCODE_REQUIRED", "KTX2 IBL assets require BasisLZ/UASTC transcoding not yet enabled in this build (channels=" + in.currentIbl().ktxTranscodeRequiredCount() + "); runtime used sidecar/derived/default fallback inputs"));
            if (in.currentIbl().ktxUnsupportedVariantCount() > 0) warnings.add(new EngineWarning("IBL_KTX_VARIANT_UNSUPPORTED", "KTX/KTX2 IBL assets use unsupported compressed/supercompressed/format variants in baseline decoder (channels=" + in.currentIbl().ktxUnsupportedVariantCount() + ")"));
            if (in.currentIbl().missingAssetCount() > 0) warnings.add(new EngineWarning("IBL_ASSET_FALLBACK_ACTIVE", "IBL configured assets missing/unreadable (" + in.currentIbl().missingAssetCount() + "); runtime used fallback/default lighting signals"));
            if (in.currentIbl().degraded()) warnings.add(new EngineWarning("IBL_QUALITY_DEGRADED", "IBL diffuse/specular quality reduced for tier " + in.qualityTier() + " to maintain stable frame cost"));
            if (in.currentIbl().ktxContainerRequested()) warnings.add(new EngineWarning("IBL_KTX_CONTAINER_FALLBACK", "KTX/KTX2 IBL assets are resolved through sidecar decode paths when available (.png/.hdr/.jpg/.jpeg)"));
        }

        if (!in.mockContext()) {
            warnings.add(new EngineWarning(
                    "MESH_GEOMETRY_CACHE_PROFILE",
                    "hits=" + in.meshGeometryCacheProfile().hits()
                            + " misses=" + in.meshGeometryCacheProfile().misses()
                            + " evictions=" + in.meshGeometryCacheProfile().evictions()
                            + " entries=" + in.meshGeometryCacheProfile().entries()
                            + " maxEntries=" + in.meshGeometryCacheProfile().maxEntries()
            ));
            SceneReuseStats reuse = in.context().sceneReuseStats();
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
            FrameResourceProfile frameResources = in.context().frameResourceProfile();
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
                            + " uniformUploadSoftLimitBytes=" + cfg.uniformUploadSoftLimitBytes
                            + " uniformUploadWarnCooldownRemaining=" + state.uniformUploadWarnCooldownRemaining
                            + " pendingUploadRangeSoftLimit=" + cfg.pendingUploadRangeSoftLimit
                            + " pendingUploadRangeWarnCooldownRemaining=" + state.pendingUploadRangeWarnCooldownRemaining
                            + " descriptorRingActiveSoftLimit=" + cfg.descriptorRingActiveSoftLimit
                            + " descriptorRingActiveWarnCooldownRemaining=" + state.descriptorRingActiveWarnCooldownRemaining
                            + " descriptorRingWasteWarnCooldownRemaining=" + state.descriptorRingWasteWarnCooldownRemaining
                            + " descriptorRingCapPressureWarnCooldownRemaining=" + state.descriptorRingCapPressureWarnCooldownRemaining
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
                boolean highWaste = frameResources.descriptorRingSetCapacity() >= cfg.descriptorRingWasteWarnMinCapacity
                        && wasteRatio >= cfg.descriptorRingWasteWarnRatio;
                state.descriptorRingWasteHighStreak = highWaste ? (state.descriptorRingWasteHighStreak + 1) : 0;
                if (state.descriptorRingWasteHighStreak >= cfg.descriptorRingWasteWarnMinFrames
                        && state.descriptorRingWasteWarnCooldownRemaining <= 0) {
                    warnings.add(new EngineWarning(
                            "DESCRIPTOR_RING_WASTE_HIGH",
                            "Descriptor ring waste ratio "
                                    + String.format(Locale.ROOT, "%.3f", wasteRatio)
                                    + " sustained for " + state.descriptorRingWasteHighStreak
                                    + " frames (active=" + frameResources.descriptorRingActiveSetCount()
                                    + ", capacity=" + frameResources.descriptorRingSetCapacity()
                                    + ", threshold=" + cfg.descriptorRingWasteWarnRatio + ")"
                    ));
                    state.descriptorRingWasteWarnCooldownRemaining = cfg.descriptorRingWasteWarnCooldownFrames;
                }
            } else {
                state.descriptorRingWasteHighStreak = 0;
            }
            if (frameResources.descriptorRingCapBypasses() >= cfg.descriptorRingCapPressureWarnMinBypasses) {
                state.descriptorRingCapPressureStreak++;
                if (state.descriptorRingCapPressureStreak >= cfg.descriptorRingCapPressureWarnMinFrames
                        && state.descriptorRingCapPressureWarnCooldownRemaining <= 0) {
                    warnings.add(new EngineWarning(
                            "DESCRIPTOR_RING_CAP_PRESSURE",
                            "Descriptor ring cap bypasses=" + frameResources.descriptorRingCapBypasses()
                                    + " (maxSetCapacity=" + frameResources.descriptorRingMaxSetCapacity()
                                    + ", active=" + frameResources.descriptorRingActiveSetCount()
                                    + ", capacity=" + frameResources.descriptorRingSetCapacity() + ")"
                    ));
                    state.descriptorRingCapPressureWarnCooldownRemaining = cfg.descriptorRingCapPressureWarnCooldownFrames;
                }
            } else {
                state.descriptorRingCapPressureStreak = 0;
            }
            if (state.descriptorRingWasteWarnCooldownRemaining > 0) state.descriptorRingWasteWarnCooldownRemaining--;
            if (state.descriptorRingCapPressureWarnCooldownRemaining > 0) state.descriptorRingCapPressureWarnCooldownRemaining--;
            int totalFrameUniformUploadBytes = frameResources.lastFrameUniformUploadBytes() + frameResources.lastFrameGlobalUploadBytes();
            if (totalFrameUniformUploadBytes > cfg.uniformUploadSoftLimitBytes
                    && state.uniformUploadWarnCooldownRemaining <= 0) {
                warnings.add(new EngineWarning(
                        "UNIFORM_UPLOAD_SOFT_LIMIT_EXCEEDED",
                        "Frame uniform upload bytes " + totalFrameUniformUploadBytes
                                + " exceed soft limit " + cfg.uniformUploadSoftLimitBytes
                                + " (global=" + frameResources.lastFrameGlobalUploadBytes()
                                + ", object=" + frameResources.lastFrameUniformUploadBytes()
                                + ", ranges=" + frameResources.lastFrameUniformUploadRanges()
                                + ", objects=" + frameResources.lastFrameUniformObjectCount() + ")"
                ));
                state.uniformUploadWarnCooldownRemaining = cfg.uniformUploadWarnCooldownFrames;
            }
            if (frameResources.lastFrameUniformUploadRanges() > cfg.pendingUploadRangeSoftLimit
                    && state.pendingUploadRangeWarnCooldownRemaining <= 0) {
                warnings.add(new EngineWarning(
                        "PENDING_UPLOAD_RANGE_SOFT_LIMIT_EXCEEDED",
                        "Frame uniform upload ranges " + frameResources.lastFrameUniformUploadRanges()
                                + " exceed soft limit " + cfg.pendingUploadRangeSoftLimit
                                + " (capacity=" + frameResources.pendingUploadRangeCapacity() + ")"
                ));
                state.pendingUploadRangeWarnCooldownRemaining = cfg.pendingUploadRangeWarnCooldownFrames;
            }
            if (frameResources.descriptorRingActiveSetCount() > cfg.descriptorRingActiveSoftLimit
                    && state.descriptorRingActiveWarnCooldownRemaining <= 0) {
                warnings.add(new EngineWarning(
                        "DESCRIPTOR_RING_ACTIVE_SOFT_LIMIT_EXCEEDED",
                        "Descriptor ring active set count " + frameResources.descriptorRingActiveSetCount()
                                + " exceeds soft limit " + cfg.descriptorRingActiveSoftLimit
                                + " (capacity=" + frameResources.descriptorRingSetCapacity() + ")"
                ));
                state.descriptorRingActiveWarnCooldownRemaining = cfg.descriptorRingActiveWarnCooldownFrames;
            }
            if (state.uniformUploadWarnCooldownRemaining > 0) state.uniformUploadWarnCooldownRemaining--;
            if (state.pendingUploadRangeWarnCooldownRemaining > 0) state.pendingUploadRangeWarnCooldownRemaining--;
            if (state.descriptorRingActiveWarnCooldownRemaining > 0) state.descriptorRingActiveWarnCooldownRemaining--;
            if (in.currentShadows().enabled()) {
                ShadowCascadeProfile shadow = in.context().shadowCascadeProfile();
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
            PostProcessPipelineProfile postProfile = in.context().postProcessPipelineProfile();
            warnings.add(new EngineWarning(
                    "VULKAN_POST_PROCESS_PIPELINE",
                    "offscreenRequested=" + postProfile.offscreenRequested()
                            + " offscreenActive=" + postProfile.offscreenActive()
                            + " mode=" + postProfile.mode()
            ));
        } else if (in.postOffscreenRequested()) {
            warnings.add(new EngineWarning(
                    "VULKAN_POST_PROCESS_PIPELINE",
                    "offscreenRequested=true offscreenActive=false mode=shader-fallback"
            ));
        }
        return warnings;
    }

    record Inputs(
            QualityTier qualityTier,
            VulkanEngineRuntime.FogRenderConfig currentFog,
            VulkanEngineRuntime.SmokeRenderConfig currentSmoke,
            VulkanEngineRuntime.ShadowRenderConfig currentShadows,
            VulkanEngineRuntime.PostProcessRenderConfig currentPost,
            VulkanEngineRuntime.IblRenderConfig currentIbl,
            VulkanEngineRuntime.UpscalerMode upscalerMode,
            VulkanEngineRuntime.UpscalerQuality upscalerQuality,
            boolean nonDirectionalShadowRequested,
            boolean mockContext,
            boolean postOffscreenRequested,
            VulkanEngineRuntime.MeshGeometryCacheProfile meshGeometryCacheProfile,
            VulkanContext context
    ) {
    }
}
