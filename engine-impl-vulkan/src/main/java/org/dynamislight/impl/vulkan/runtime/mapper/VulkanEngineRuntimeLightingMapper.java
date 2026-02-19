package org.dynamislight.impl.vulkan.runtime.mapper;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.dynamislight.impl.vulkan.runtime.config.AaPreset;
import org.dynamislight.impl.vulkan.runtime.config.ReflectionProfile;
import org.dynamislight.impl.vulkan.runtime.config.TsrControls;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerMode;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerQuality;
import org.dynamislight.impl.vulkan.runtime.model.FogRenderConfig;
import org.dynamislight.impl.vulkan.runtime.model.LightingConfig;
import org.dynamislight.impl.vulkan.runtime.model.PostProcessRenderConfig;
import org.dynamislight.impl.vulkan.runtime.model.ShadowRenderConfig;
import org.dynamislight.impl.vulkan.runtime.model.SmokeRenderConfig;

import java.util.List;
import java.util.Map;

public final class VulkanEngineRuntimeLightingMapper {
    private VulkanEngineRuntimeLightingMapper() {
    }

    public static PostProcessRenderConfig mapPostProcess(
            PostProcessDesc desc,
            QualityTier qualityTier,
            boolean taaLumaClipEnabledDefault,
            AaPreset aaPreset,
            AaMode aaMode,
            UpscalerMode upscalerMode,
            UpscalerQuality upscalerQuality,
            TsrControls tsrControls,
            ReflectionProfile reflectionProfile
    ) {
        return VulkanEngineRuntimePostProcessMapper.mapPostProcess(
                desc,
                qualityTier,
                taaLumaClipEnabledDefault,
                aaPreset,
                aaMode,
                upscalerMode,
                upscalerQuality,
                tsrControls,
                reflectionProfile
        );
    }

    public static LightingConfig mapLighting(
            List<LightDesc> lights,
            QualityTier qualityTier,
            int shadowMaxLocalLayers
    ) {
        return VulkanEngineRuntimeShadowMapper.mapLighting(lights, qualityTier, shadowMaxLocalLayers);
    }

    public static LightingConfig mapLighting(
            List<LightDesc> lights,
            QualityTier qualityTier,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowSchedulerEnabled,
            int shadowSchedulerHeroPeriod,
            int shadowSchedulerMidPeriod,
            int shadowSchedulerDistantPeriod,
            long shadowSchedulerFrameTick,
            Map<String, Long> shadowSchedulerLastRenderedTicks,
            Map<String, Integer> shadowLayerAssignments
    ) {
        return VulkanEngineRuntimeShadowMapper.mapLighting(
                lights,
                qualityTier,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowSchedulerEnabled,
                shadowSchedulerHeroPeriod,
                shadowSchedulerMidPeriod,
                shadowSchedulerDistantPeriod,
                shadowSchedulerFrameTick,
                shadowSchedulerLastRenderedTicks,
                shadowLayerAssignments
        );
    }

    public static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
        return VulkanEngineRuntimeShadowMapper.hasNonDirectionalShadowRequest(lights);
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame
    ) {
        return VulkanEngineRuntimeShadowMapper.mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame
        );
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame
    ) {
        return VulkanEngineRuntimeShadowMapper.mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame
        );
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowRtTraversalSupported
    ) {
        return VulkanEngineRuntimeShadowMapper.mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowRtTraversalSupported
        );
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowRtTraversalSupported,
            boolean shadowRtBvhSupported,
            boolean shadowSchedulerEnabled,
            int shadowSchedulerHeroPeriod,
            int shadowSchedulerMidPeriod,
            int shadowSchedulerDistantPeriod,
            long shadowSchedulerFrameTick,
            Map<String, Long> shadowSchedulerLastRenderedTicks
    ) {
        return VulkanEngineRuntimeShadowMapper.mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowRtTraversalSupported,
                shadowRtBvhSupported,
                shadowSchedulerEnabled,
                shadowSchedulerHeroPeriod,
                shadowSchedulerMidPeriod,
                shadowSchedulerDistantPeriod,
                shadowSchedulerFrameTick,
                shadowSchedulerLastRenderedTicks
        );
    }

    public static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        return VulkanEngineRuntimeAtmosphereMapper.mapFog(fogDesc, qualityTier);
    }

    public static SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        return VulkanEngineRuntimeAtmosphereMapper.mapSmoke(emitters, qualityTier);
    }
}
