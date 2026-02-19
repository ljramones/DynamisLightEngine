package org.dynamislight.impl.vulkan.capability;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderDescriptorRequirement;
import org.dynamislight.spi.render.RenderDescriptorType;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;
import org.dynamislight.spi.render.RenderFeatureMode;
import org.dynamislight.spi.render.RenderPassDeclaration;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderPushConstantRequirement;
import org.dynamislight.spi.render.RenderResourceDeclaration;
import org.dynamislight.spi.render.RenderResourceLifecycle;
import org.dynamislight.spi.render.RenderResourceType;
import org.dynamislight.spi.render.RenderSchedulerDeclaration;
import org.dynamislight.spi.render.RenderShaderContribution;
import org.dynamislight.spi.render.RenderShaderInjectionPoint;
import org.dynamislight.spi.render.RenderShaderStage;
import org.dynamislight.spi.render.RenderTelemetryDeclaration;
import org.dynamislight.spi.render.RenderUniformRequirement;

/**
 * v2 metadata-only AA capability descriptor extracted from Vulkan runtime behavior.
 */
public final class VulkanAaCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_TAA = new RenderFeatureMode("taa");
    public static final RenderFeatureMode MODE_TSR = new RenderFeatureMode("tsr");
    public static final RenderFeatureMode MODE_TUUA = new RenderFeatureMode("tuua");
    public static final RenderFeatureMode MODE_MSAA_SELECTIVE = new RenderFeatureMode("msaa_selective");
    public static final RenderFeatureMode MODE_HYBRID_TUUA_MSAA = new RenderFeatureMode("hybrid_tuua_msaa");
    public static final RenderFeatureMode MODE_DLAA = new RenderFeatureMode("dlaa");
    public static final RenderFeatureMode MODE_FXAA_LOW = new RenderFeatureMode("fxaa_low");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_TAA,
            MODE_TSR,
            MODE_TUUA,
            MODE_MSAA_SELECTIVE,
            MODE_HYBRID_TUUA_MSAA,
            MODE_DLAA,
            MODE_FXAA_LOW
    );

    private final RenderFeatureMode activeMode;

    public VulkanAaCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanAaCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanAaCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.aa";
    }

    @Override
    public List<RenderFeatureMode> supportedModes() {
        return SUPPORTED;
    }

    @Override
    public RenderFeatureMode activeMode() {
        return activeMode;
    }

    @Override
    public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
        boolean temporal = requiresTemporalHistory(sanitizeMode(mode));
        return List.of(new RenderPassDeclaration(
                "post_composite",
                RenderPassPhase.POST_MAIN,
                temporal
                        ? List.of("scene_color", "velocity", "history_color", "history_velocity")
                        : List.of("scene_color", "velocity"),
                temporal
                        ? List.of("resolved_color", "history_color_next", "history_velocity_next")
                        : List.of("resolved_color"),
                false,
                false,
                false,
                List.of()
        ));
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        int ordering = switch (active.id()) {
            case "fxaa_low" -> 340;
            case "msaa_selective" -> 350;
            case "hybrid_tuua_msaa" -> 360;
            case "taa" -> 370;
            case "tuua" -> 380;
            case "tsr" -> 390;
            case "dlaa" -> 400;
            default -> 370;
        };
        return List.of(new RenderShaderContribution(
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "aa_resolve_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                false,
                true,
                ordering,
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        boolean temporal = requiresTemporalHistory(active);
        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderDescriptorRequirement("post_composite", 0, 20, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 21, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 22, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false)
        ));
        if (temporal) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 23, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 24, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderUniformRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderUniformRequirement("global_scene", "aaJitter", 0, 0),
                new RenderUniformRequirement("global_scene", "aaTemporalWeight", 0, 0)
        ));
        if (requiresTemporalHistory(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "taaClipParams", 0, 0));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        boolean temporal = requiresTemporalHistory(active);
        java.util.ArrayList<RenderResourceDeclaration> resources = new java.util.ArrayList<>(List.of(
                new RenderResourceDeclaration(
                        "aa_resolved_color",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("swapchainRecreated", "renderScaleChanged")
                )
        ));
        if (temporal) {
            resources.add(new RenderResourceDeclaration(
                    "aa_history_color",
                    RenderResourceType.SAMPLED_IMAGE,
                    RenderResourceLifecycle.CROSS_FRAME_TEMPORAL,
                    false,
                    List.of("taaReset", "swapchainRecreated")
            ));
            resources.add(new RenderResourceDeclaration(
                    "aa_history_velocity",
                    RenderResourceType.SAMPLED_IMAGE,
                    RenderResourceLifecycle.CROSS_FRAME_TEMPORAL,
                    false,
                    List.of("taaReset", "swapchainRecreated")
            ));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "AA_POLICY_ACTIVE",
                        "AA_TEMPORAL_ENVELOPE",
                        "AA_TEMPORAL_ENVELOPE_BREACH",
                        "AA_TEMPORAL_PROMOTION_READY",
                        "AA_REACTIVE_MASK_POLICY",
                        "AA_REACTIVE_MASK_ENVELOPE_BREACH",
                        "AA_HISTORY_CLAMP_POLICY",
                        "AA_HISTORY_CLAMP_ENVELOPE_BREACH",
                        "AA_TEMPORAL_CORE_PROMOTION_READY",
                        "AA_UPSCALE_POLICY_ACTIVE",
                        "AA_UPSCALE_ENVELOPE",
                        "AA_UPSCALE_ENVELOPE_BREACH",
                        "AA_UPSCALE_PROMOTION_READY",
                        "AA_MSAA_POLICY_ACTIVE",
                        "AA_MSAA_ENVELOPE",
                        "AA_MSAA_ENVELOPE_BREACH",
                        "AA_MSAA_PROMOTION_READY",
                        "AA_DLAA_POLICY_ACTIVE",
                        "AA_DLAA_ENVELOPE",
                        "AA_DLAA_ENVELOPE_BREACH",
                        "AA_DLAA_PROMOTION_READY",
                        "AA_SPECULAR_POLICY_ACTIVE",
                        "AA_SPECULAR_ENVELOPE",
                        "AA_SPECULAR_ENVELOPE_BREACH",
                        "AA_SPECULAR_PROMOTION_READY",
                        "AA_GEOMETRIC_POLICY_ACTIVE",
                        "AA_GEOMETRIC_ENVELOPE",
                        "AA_GEOMETRIC_ENVELOPE_BREACH",
                        "AA_GEOMETRIC_PROMOTION_READY",
                        "AA_A2C_POLICY_ACTIVE",
                        "AA_A2C_ENVELOPE",
                        "AA_A2C_ENVELOPE_BREACH",
                        "AA_A2C_PROMOTION_READY"
                ),
                List.of(
                        "aaPolicyDiagnostics",
                        "aaTemporalPromotionDiagnostics",
                        "aaUpscalePromotionDiagnostics",
                        "aaMsaaPromotionDiagnostics",
                        "aaQualityPromotionDiagnostics"
                ),
                List.of(),
                List.of(
                        "aa.temporal.history.stability",
                        "aa.mode.contract"
                )
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null) {
            return MODE_TAA;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(mode.id()))
                .findFirst()
                .orElse(MODE_TAA);
    }

    private static boolean requiresTemporalHistory(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "taa".equals(id)
                || "tsr".equals(id)
                || "tuua".equals(id)
                || "hybrid_tuua_msaa".equals(id)
                || "dlaa".equals(id);
    }
}
