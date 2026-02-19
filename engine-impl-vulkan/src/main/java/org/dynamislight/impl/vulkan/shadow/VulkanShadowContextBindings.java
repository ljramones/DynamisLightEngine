package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.impl.vulkan.runtime.model.ShadowRenderConfig;

import org.dynamislight.api.error.EngineException;

public final class VulkanShadowContextBindings {
    private VulkanShadowContextBindings() {
    }

    public static ShadowRenderConfig applySceneLoadShadowBindings(
            boolean mockContext,
            VulkanContext context,
            ShadowRenderConfig shadows,
            float shadowPcssSoftness,
            float shadowMomentBlend,
            float shadowMomentBleedReduction,
            float shadowContactStrength,
            float shadowContactTemporalMotionScale,
            float shadowContactTemporalMinStability,
            float shadowRtDenoiseStrength,
            float shadowRtProductionDenoiseStrength,
            float shadowRtDedicatedDenoiseStrength,
            float shadowRtRayLength,
            float shadowRtProductionRayLength,
            float shadowRtDedicatedRayLength,
            int shadowRtSampleCount,
            int shadowRtProductionSampleCount,
            int shadowRtDedicatedSampleCount
    ) throws EngineException {
        if (mockContext) {
            return shadows;
        }
        ShadowRenderConfig runtimeShadows = VulkanShadowRuntimeTuning.withRuntimeMomentPipelineState(shadows, context);
        context.setShadowQualityModes(
                runtimeShadows.runtimeFilterPath(),
                runtimeShadows.contactShadowsRequested(),
                runtimeShadows.rtShadowMode(),
                runtimeShadows.filterPath()
        );
        context.setShadowQualityTuning(
                shadowPcssSoftness,
                shadowMomentBlend,
                shadowMomentBleedReduction,
                shadowContactStrength,
                shadowContactTemporalMotionScale,
                shadowContactTemporalMinStability
        );
        context.setShadowRtTuning(
                VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(runtimeShadows.rtShadowMode(), shadowRtDenoiseStrength, shadowRtProductionDenoiseStrength, shadowRtDedicatedDenoiseStrength),
                VulkanShadowRuntimeTuning.effectiveShadowRtRayLength(runtimeShadows.rtShadowMode(), shadowRtRayLength, shadowRtProductionRayLength, shadowRtDedicatedRayLength),
                VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(runtimeShadows.rtShadowMode(), shadowRtSampleCount, shadowRtProductionSampleCount, shadowRtDedicatedSampleCount)
        );
        return runtimeShadows;
    }
}
