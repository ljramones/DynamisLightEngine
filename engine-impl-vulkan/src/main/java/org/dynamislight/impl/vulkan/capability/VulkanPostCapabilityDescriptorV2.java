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
 * v2 metadata-only post capability descriptor extracted from Vulkan runtime behavior.
 */
public final class VulkanPostCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_TONEMAP = new RenderFeatureMode("tonemap");
    public static final RenderFeatureMode MODE_BLOOM = new RenderFeatureMode("bloom");
    public static final RenderFeatureMode MODE_SSAO = new RenderFeatureMode("ssao");
    public static final RenderFeatureMode MODE_SMAA = new RenderFeatureMode("smaa");
    public static final RenderFeatureMode MODE_TAA_RESOLVE = new RenderFeatureMode("taa_resolve");
    public static final RenderFeatureMode MODE_FOG_COMPOSITE = new RenderFeatureMode("fog_composite");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_TONEMAP,
            MODE_BLOOM,
            MODE_SSAO,
            MODE_SMAA,
            MODE_TAA_RESOLVE,
            MODE_FOG_COMPOSITE
    );

    private final RenderFeatureMode activeMode;

    public VulkanPostCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanPostCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanPostCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.post";
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
        RenderFeatureMode active = sanitizeMode(mode);
        return List.of(new RenderPassDeclaration(
                "post_composite",
                RenderPassPhase.POST_MAIN,
                readsFor(active),
                writesFor(active),
                false,
                false,
                false,
                List.of()
        ));
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return List.of(new RenderShaderContribution(
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "post_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                false,
                false,
                orderingFor(active),
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderDescriptorRequirement("post_composite", 0, 30, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 31, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false)
        ));
        if (MODE_SSAO.equals(active) || MODE_FOG_COMPOSITE.equals(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 32, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, true));
        }
        if (MODE_TAA_RESOLVE.equals(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 33, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 34, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return switch (active.id()) {
            case "tonemap" -> List.of(new RenderUniformRequirement("global_scene", "postTonemap", 0, 0));
            case "bloom" -> List.of(new RenderUniformRequirement("global_scene", "postBloom", 0, 0));
            case "ssao" -> List.of(new RenderUniformRequirement("global_scene", "postSsao", 0, 0));
            case "smaa" -> List.of(new RenderUniformRequirement("global_scene", "postSmaa", 0, 0));
            case "taa_resolve" -> List.of(new RenderUniformRequirement("global_scene", "postTaa", 0, 0));
            case "fog_composite" -> List.of(new RenderUniformRequirement("global_scene", "postFog", 0, 0));
            default -> List.of();
        };
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderResourceDeclaration> resources = new java.util.ArrayList<>(List.of(
                new RenderResourceDeclaration(
                        "post_resolved_color",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("swapchainRecreated", "renderScaleChanged")
                )
        ));
        if (MODE_TAA_RESOLVE.equals(active)) {
            resources.add(new RenderResourceDeclaration(
                    "post_taa_history",
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
                        "POST_POLICY_ACTIVE",
                        "POST_STACK_ORDER_ACTIVE"
                ),
                List.of(
                        "postPolicyDiagnostics",
                        "postStackDiagnostics"
                ),
                List.of(),
                List.of(
                        "post.stack.contract",
                        "post.resolve.order"
                )
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null) {
            return MODE_TONEMAP;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(mode.id()))
                .findFirst()
                .orElse(MODE_TONEMAP);
    }

    private static List<String> readsFor(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return switch (id) {
            case "tonemap", "bloom", "smaa" -> List.of("scene_color");
            case "ssao" -> List.of("scene_color", "scene_depth");
            case "taa_resolve" -> List.of("scene_color", "velocity", "history_color", "history_velocity");
            case "fog_composite" -> List.of("scene_color", "scene_depth");
            default -> List.of("scene_color");
        };
    }

    private static List<String> writesFor(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return switch (id) {
            case "taa_resolve" -> List.of("resolved_color", "history_color_next", "history_velocity_next");
            default -> List.of("resolved_color");
        };
    }

    private static int orderingFor(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return switch (id) {
            case "fog_composite" -> 230;
            case "ssao" -> 240;
            case "bloom" -> 250;
            case "tonemap" -> 260;
            case "smaa" -> 270;
            case "taa_resolve" -> 280;
            default -> 260;
        };
    }
}
