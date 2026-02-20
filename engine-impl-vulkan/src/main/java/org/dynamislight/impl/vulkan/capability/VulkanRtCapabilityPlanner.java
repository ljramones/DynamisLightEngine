package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;

/**
 * Deterministic RT capability planner for contract mode/signals.
 */
public final class VulkanRtCapabilityPlanner {
    private VulkanRtCapabilityPlanner() {
    }

    public static VulkanRtCapabilityPlan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input;

        boolean rtAoActive = safe.rtAoRequested()
                && safe.rtTraversalSupported()
                && safe.qualityTier().ordinal() >= QualityTier.MEDIUM.ordinal();
        boolean rtTranslucencyCausticsActive = safe.rtTranslucencyCausticsRequested()
                && safe.rtTraversalSupported()
                && safe.qualityTier().ordinal() >= QualityTier.ULTRA.ordinal();
        boolean bvhCompactionActive = safe.bvhCompactionRequested() && safe.rtBvhSupported();
        boolean denoiserFrameworkActive = safe.denoiserFrameworkRequested()
                && (safe.reflectionRtLaneActive() || safe.giRtActive() || rtAoActive || rtTranslucencyCausticsActive);
        boolean hybridCompositionActive = safe.hybridCompositionRequested()
                && (safe.reflectionRtLaneActive() || safe.giRtActive() || rtAoActive || rtTranslucencyCausticsActive);
        boolean qualityTiersActive = safe.qualityTiersRequested();
        boolean inlineRayQueryActive = safe.inlineRayQueryRequested() && safe.rtTraversalSupported();
        boolean dedicatedRaygenActive = safe.dedicatedRaygenRequested() && safe.rtBvhSupported();

        List<String> active = new ArrayList<>();
        if (rtAoActive) active.add("vulkan.rt.ao");
        if (rtTranslucencyCausticsActive) active.add("vulkan.rt.translucency_caustics");
        if (bvhCompactionActive) active.add("vulkan.rt.bvh_compaction");
        if (denoiserFrameworkActive) active.add("vulkan.rt.denoiser_framework");
        if (hybridCompositionActive) active.add("vulkan.rt.hybrid_composition");
        if (qualityTiersActive) active.add("vulkan.rt.quality_tiers");
        if (inlineRayQueryActive) active.add("vulkan.rt.inline_ray_query");
        if (dedicatedRaygenActive) active.add("vulkan.rt.dedicated_raygen");

        List<String> pruned = new ArrayList<>();
        if (safe.rtAoRequested() && !rtAoActive) {
            pruned.add("vulkan.rt.ao (rt unavailable or quality tier too low)");
        }
        if (safe.rtTranslucencyCausticsRequested() && !rtTranslucencyCausticsActive) {
            pruned.add("vulkan.rt.translucency_caustics (rt unavailable or quality tier too low)");
        }
        if (safe.bvhCompactionRequested() && !bvhCompactionActive) {
            pruned.add("vulkan.rt.bvh_compaction (bvh unavailable)");
        }
        if (safe.denoiserFrameworkRequested() && !denoiserFrameworkActive) {
            pruned.add("vulkan.rt.denoiser_framework (rt lanes inactive)");
        }
        if (safe.hybridCompositionRequested() && !hybridCompositionActive) {
            pruned.add("vulkan.rt.hybrid_composition (rt lanes inactive)");
        }
        if (safe.inlineRayQueryRequested() && !inlineRayQueryActive) {
            pruned.add("vulkan.rt.inline_ray_query (rt unavailable)");
        }
        if (safe.dedicatedRaygenRequested() && !dedicatedRaygenActive) {
            pruned.add("vulkan.rt.dedicated_raygen (bvh unavailable)");
        }

        String mode = resolveMode(
                rtAoActive,
                rtTranslucencyCausticsActive,
                bvhCompactionActive,
                denoiserFrameworkActive,
                hybridCompositionActive,
                qualityTiersActive,
                inlineRayQueryActive,
                dedicatedRaygenActive
        );
        List<String> signals = List.of(
                "resolvedMode=" + mode,
                "rtAvailable=" + safe.rtTraversalSupported(),
                "rtBvhSupported=" + safe.rtBvhSupported(),
                "rtAoRequested=" + safe.rtAoRequested(),
                "rtAoActive=" + rtAoActive,
                "rtTranslucencyCausticsRequested=" + safe.rtTranslucencyCausticsRequested(),
                "rtTranslucencyCausticsActive=" + rtTranslucencyCausticsActive,
                "bvhCompactionRequested=" + safe.bvhCompactionRequested(),
                "bvhCompactionActive=" + bvhCompactionActive,
                "denoiserFrameworkRequested=" + safe.denoiserFrameworkRequested(),
                "denoiserFrameworkActive=" + denoiserFrameworkActive,
                "hybridCompositionRequested=" + safe.hybridCompositionRequested(),
                "hybridCompositionActive=" + hybridCompositionActive,
                "qualityTiersRequested=" + safe.qualityTiersRequested(),
                "qualityTiersActive=" + qualityTiersActive,
                "inlineRayQueryRequested=" + safe.inlineRayQueryRequested(),
                "inlineRayQueryActive=" + inlineRayQueryActive,
                "dedicatedRaygenRequested=" + safe.dedicatedRaygenRequested(),
                "dedicatedRaygenActive=" + dedicatedRaygenActive,
                "reflectionRtLaneActive=" + safe.reflectionRtLaneActive(),
                "giRtActive=" + safe.giRtActive()
        );
        return new VulkanRtCapabilityPlan(
                mode,
                safe.rtTraversalSupported(),
                safe.rtAoRequested(),
                rtAoActive,
                safe.rtTranslucencyCausticsRequested(),
                rtTranslucencyCausticsActive,
                safe.bvhCompactionRequested(),
                bvhCompactionActive,
                safe.denoiserFrameworkRequested(),
                denoiserFrameworkActive,
                safe.hybridCompositionRequested(),
                hybridCompositionActive,
                safe.qualityTiersRequested(),
                qualityTiersActive,
                safe.inlineRayQueryRequested(),
                inlineRayQueryActive,
                safe.dedicatedRaygenRequested(),
                dedicatedRaygenActive,
                active,
                pruned,
                signals
        );
    }

    private static String resolveMode(
            boolean rtAoActive,
            boolean rtTranslucencyCausticsActive,
            boolean bvhCompactionActive,
            boolean denoiserFrameworkActive,
            boolean hybridCompositionActive,
            boolean qualityTiersActive,
            boolean inlineRayQueryActive,
            boolean dedicatedRaygenActive
    ) {
        if (rtAoActive && rtTranslucencyCausticsActive && bvhCompactionActive
                && denoiserFrameworkActive && hybridCompositionActive
                && qualityTiersActive && inlineRayQueryActive && dedicatedRaygenActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_FULL_STACK.id();
        }
        if (rtTranslucencyCausticsActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_RT_TRANSLUCENCY_CAUSTICS.id();
        }
        if (rtAoActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_RT_AO_DENOISED.id();
        }
        if (dedicatedRaygenActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_DEDICATED_RAYGEN.id();
        }
        if (inlineRayQueryActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_INLINE_RAY_QUERY.id();
        }
        if (hybridCompositionActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_RT_HYBRID_RASTER.id();
        }
        if (denoiserFrameworkActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_DENOISER_FRAMEWORK.id();
        }
        if (bvhCompactionActive) {
            return VulkanRtCapabilityDescriptorV2.MODE_BVH_MANAGEMENT.id();
        }
        return VulkanRtCapabilityDescriptorV2.MODE_QUALITY_TIERS.id();
    }

    public record PlanInput(
            QualityTier qualityTier,
            boolean rtTraversalSupported,
            boolean rtBvhSupported,
            boolean reflectionRtLaneActive,
            boolean giRtActive,
            boolean rtAoRequested,
            boolean rtTranslucencyCausticsRequested,
            boolean bvhCompactionRequested,
            boolean denoiserFrameworkRequested,
            boolean hybridCompositionRequested,
            boolean qualityTiersRequested,
            boolean inlineRayQueryRequested,
            boolean dedicatedRaygenRequested
    ) {
        public PlanInput {
            qualityTier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    false
            );
        }
    }
}
