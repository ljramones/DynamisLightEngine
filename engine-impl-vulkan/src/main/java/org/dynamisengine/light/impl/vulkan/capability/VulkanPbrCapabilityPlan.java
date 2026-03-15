package org.dynamisengine.light.impl.vulkan.capability;

import java.util.List;

/**
 * Deterministic plan for Vulkan PBR/shading capability activation.
 */
public record VulkanPbrCapabilityPlan(
        String modeId,
        boolean specularGlossinessEnabled,
        boolean detailMapsEnabled,
        boolean materialLayeringEnabled,
        boolean clearCoatEnabled,
        boolean anisotropicEnabled,
        boolean transmissionEnabled,
        boolean refractionEnabled,
        boolean subsurfaceScatteringEnabled,
        boolean thinFilmIridescenceEnabled,
        boolean sheenEnabled,
        boolean parallaxOcclusionEnabled,
        boolean tessellationEnabled,
        boolean decalsEnabled,
        boolean eyeShaderEnabled,
        boolean hairShaderEnabled,
        boolean clothShaderEnabled,
        boolean vertexColorBlendEnabled,
        boolean emissiveBloomControlEnabled,
        boolean energyConservationValidationEnabled,
        List<String> activeCapabilities,
        List<String> prunedCapabilities,
        List<String> signals
) {
    public VulkanPbrCapabilityPlan {
        modeId = modeId == null ? VulkanPbrCapabilityDescriptorV2.MODE_METALLIC_ROUGHNESS_BASELINE.id() : modeId;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
