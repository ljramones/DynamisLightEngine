package org.dynamislight.impl.vulkan.profile;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanPostCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityPlanner;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.dynamislight.spi.render.RenderFeatureMode;

/**
 * Resolves active Phase C profile identity from runtime state.
 */
public final class VulkanPipelineProfileResolver {
    private VulkanPipelineProfileResolver() {
    }

    public static VulkanPipelineProfileKey resolve(
            QualityTier tier,
            VulkanRenderState renderState,
            int selectedLocalShadowLights,
            int deferredShadowLightCount,
            int renderedSpotShadowLights,
            int renderedPointShadowCubemaps,
            boolean schedulerEnabled,
            boolean shadowCacheEnabled,
            boolean areaLightShadowsEnabled,
            boolean spotProjectedEnabled,
            boolean transparentReceiversEnabled,
            boolean distanceFieldSoftEnabled
    ) {
        VulkanRenderState state = renderState == null ? new VulkanRenderState() : renderState;
        RenderFeatureMode shadowMode = VulkanShadowCapabilityPlanner.plan(new VulkanShadowCapabilityPlanner.PlanInput(
                tier,
                shadowFilterModeId(state.shadowFilterMode),
                state.shadowContactShadows,
                shadowRtModeId(state.shadowRtMode),
                0,
                0,
                0,
                selectedLocalShadowLights,
                deferredShadowLightCount,
                renderedSpotShadowLights,
                renderedPointShadowCubemaps,
                schedulerEnabled,
                shadowCacheEnabled,
                areaLightShadowsEnabled,
                spotProjectedEnabled,
                transparentReceiversEnabled,
                distanceFieldSoftEnabled
        )).mode();

        RenderFeatureMode reflectionMode = switch (state.reflectionsMode & 0x7) {
            case 1 -> VulkanReflectionCapabilityDescriptorV2.MODE_SSR;
            case 2 -> VulkanReflectionCapabilityDescriptorV2.MODE_PLANAR;
            case 4 -> VulkanReflectionCapabilityDescriptorV2.MODE_RT_HYBRID;
            case 3 -> VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID;
            default -> VulkanReflectionCapabilityDescriptorV2.MODE_IBL_ONLY;
        };

        RenderFeatureMode aaMode = state.taaEnabled
                ? VulkanAaCapabilityDescriptorV2.MODE_TAA
                : VulkanAaCapabilityDescriptorV2.MODE_FXAA_LOW;
        RenderFeatureMode postMode = state.taaEnabled
                ? VulkanPostCapabilityDescriptorV2.MODE_TAA_RESOLVE
                : VulkanPostCapabilityDescriptorV2.MODE_TONEMAP;

        return new VulkanPipelineProfileKey(
                tier == null ? QualityTier.MEDIUM : tier,
                shadowMode,
                reflectionMode,
                aaMode,
                postMode
        );
    }

    private static String shadowFilterModeId(int mode) {
        return switch (mode) {
            case 1 -> "pcss";
            case 2 -> "vsm";
            case 3 -> "evsm";
            default -> "pcf";
        };
    }

    private static String shadowRtModeId(int mode) {
        return switch (mode) {
            case 1 -> "optional";
            case 2 -> "force";
            case 3 -> "bvh";
            case 4 -> "bvh_dedicated";
            case 5 -> "bvh_production";
            case 6 -> "rt_native";
            case 7 -> "rt_native_denoised";
            default -> "off";
        };
    }
}
