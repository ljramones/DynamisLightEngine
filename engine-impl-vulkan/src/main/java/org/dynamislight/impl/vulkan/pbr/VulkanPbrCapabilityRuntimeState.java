package org.dynamislight.impl.vulkan.pbr;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.PbrCapabilityDiagnostics;
import org.dynamislight.impl.vulkan.capability.VulkanPbrCapabilityPlan;
import org.dynamislight.impl.vulkan.capability.VulkanPbrCapabilityPlanner;

/**
 * Runtime holder for PBR capability mode telemetry and typed diagnostics.
 */
public final class VulkanPbrCapabilityRuntimeState {
    private boolean specularGlossinessEnabled;
    private boolean detailMapsEnabled;
    private boolean materialLayeringEnabled;
    private boolean clearCoatEnabled;
    private boolean anisotropicEnabled;
    private boolean transmissionEnabled;
    private boolean refractionEnabled;
    private boolean vertexColorBlendEnabled;
    private boolean emissiveBloomControlEnabled;
    private boolean energyConservationValidationEnabled;
    private int promotionReadyMinFrames = 6;
    private int stableStreak;
    private String modeLastFrame = "metallic_roughness_baseline";
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();
    private List<String> signalsLastFrame = List.of();
    private boolean promotionReadyLastFrame;

    public void reset() {
        stableStreak = 0;
        modeLastFrame = "metallic_roughness_baseline";
        activeCapabilitiesLastFrame = List.of();
        prunedCapabilitiesLastFrame = List.of();
        signalsLastFrame = List.of();
        promotionReadyLastFrame = false;
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        specularGlossinessEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.specularGlossinessEnabled", "false")
        );
        detailMapsEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.detailMapsEnabled", "false")
        );
        materialLayeringEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.materialLayeringEnabled", "false")
        );
        clearCoatEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.clearCoatEnabled", "true")
        );
        anisotropicEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.anisotropicEnabled", "true")
        );
        transmissionEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.transmissionEnabled", "true")
        );
        refractionEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.refractionEnabled", "true")
        );
        vertexColorBlendEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.vertexColorBlendEnabled", "true")
        );
        emissiveBloomControlEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.emissiveBloomControlEnabled", "true")
        );
        energyConservationValidationEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.energyConservationValidationEnabled", "true")
        );
        promotionReadyMinFrames = parseInt(
                safe.get("vulkan.pbr.promotionReadyMinFrames"),
                promotionReadyMinFrames,
                1,
                100_000
        );
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.pbr.promotionReadyMinFrames")) {
            return;
        }
        QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
        promotionReadyMinFrames = switch (safeTier) {
            case LOW -> 3;
            case MEDIUM -> 4;
            case HIGH -> 5;
            case ULTRA -> 6;
        };
    }

    public void emitFrameWarning(QualityTier qualityTier, List<EngineWarning> warnings) {
        VulkanPbrCapabilityPlan plan = VulkanPbrCapabilityPlanner.plan(
                new VulkanPbrCapabilityPlanner.PlanInput(
                        qualityTier,
                        specularGlossinessEnabled,
                        detailMapsEnabled,
                        materialLayeringEnabled,
                        clearCoatEnabled,
                        anisotropicEnabled,
                        transmissionEnabled,
                        refractionEnabled,
                        vertexColorBlendEnabled,
                        emissiveBloomControlEnabled,
                        energyConservationValidationEnabled
                )
        );
        modeLastFrame = plan.modeId();
        activeCapabilitiesLastFrame = plan.activeCapabilities();
        prunedCapabilitiesLastFrame = plan.prunedCapabilities();
        signalsLastFrame = plan.signals();

        warnings.add(new EngineWarning(
                "PBR_CAPABILITY_MODE_ACTIVE",
                "PBR capability mode active (mode=" + plan.modeId()
                        + ", active=[" + String.join(", ", plan.activeCapabilities()) + "]"
                        + ", pruned=[" + String.join(", ", plan.prunedCapabilities()) + "]"
                        + ", signals=[" + String.join(", ", plan.signals()) + "])"
        ));
        warnings.add(new EngineWarning(
                "PBR_POLICY",
                "PBR policy (specularGlossiness=" + plan.specularGlossinessEnabled()
                        + ", detailMaps=" + plan.detailMapsEnabled()
                        + ", materialLayering=" + plan.materialLayeringEnabled()
                        + ", clearCoat=" + plan.clearCoatEnabled()
                        + ", anisotropic=" + plan.anisotropicEnabled()
                        + ", transmission=" + plan.transmissionEnabled()
                        + ", refraction=" + plan.refractionEnabled()
                        + ", vertexColorBlend=" + plan.vertexColorBlendEnabled()
                        + ", emissiveBloomControl=" + plan.emissiveBloomControlEnabled()
                        + ", energyConservationValidation=" + plan.energyConservationValidationEnabled() + ")"
        ));

        boolean stable = plan.specularGlossinessEnabled() || plan.detailMapsEnabled() || plan.materialLayeringEnabled();
        stableStreak = stable ? (stableStreak + 1) : 0;
        promotionReadyLastFrame = stableStreak >= promotionReadyMinFrames;
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "PBR_PROMOTION_READY",
                    "PBR promotion-ready envelope satisfied (mode=" + plan.modeId()
                            + ", stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public PbrCapabilityDiagnostics diagnostics() {
        return new PbrCapabilityDiagnostics(
                true,
                modeLastFrame,
                activeCapabilitiesLastFrame.contains("vulkan.pbr.specular_glossiness"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.detail_maps"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.material_layering"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.clear_coat"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.anisotropic"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.transmission"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.refraction"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.vertex_color_blend"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.emissive_bloom_control"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.energy_conservation_validation"),
                activeCapabilitiesLastFrame,
                prunedCapabilitiesLastFrame,
                signalsLastFrame
        );
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
