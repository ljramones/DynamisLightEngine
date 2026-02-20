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
 * GI Phase 1 v2 contract scaffold for composition planning.
 */
public final class VulkanGiCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_SSGI = new RenderFeatureMode("ssgi");
    public static final RenderFeatureMode MODE_PROBE_GRID = new RenderFeatureMode("probe_grid");
    public static final RenderFeatureMode MODE_RTGI_SINGLE = new RenderFeatureMode("rtgi_single");
    public static final RenderFeatureMode MODE_HYBRID_PROBE_SSGI_RT = new RenderFeatureMode("hybrid_probe_ssgi_rt");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_SSGI,
            MODE_PROBE_GRID,
            MODE_RTGI_SINGLE,
            MODE_HYBRID_PROBE_SSGI_RT
    );

    private final RenderFeatureMode activeMode;

    public VulkanGiCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanGiCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanGiCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.gi";
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
                "gi_resolve",
                RenderPassPhase.POST_MAIN,
                readsFor(active),
                List.of("gi_indirect"),
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
            case "probe_grid" -> 205;
            case "ssgi" -> 210;
            case "rtgi_single" -> 215;
            case "hybrid_probe_ssgi_rt" -> 220;
            default -> 210;
        };
        return List.of(new RenderShaderContribution(
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "gi_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                false,
                false,
                ordering,
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderDescriptorRequirement("gi_resolve", 0, 70, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("gi_resolve", 0, 71, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("gi_resolve", 0, 72, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false)
        ));
        if ("rtgi_single".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            requirements.add(new RenderDescriptorRequirement("gi_resolve", 0, 73, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return switch (active.id()) {
            case "ssgi" -> List.of(new RenderUniformRequirement("global_scene", "giSsgi", 0, 0));
            case "probe_grid" -> List.of(new RenderUniformRequirement("global_scene", "giProbeGrid", 0, 0));
            case "rtgi_single" -> List.of(new RenderUniformRequirement("global_scene", "giRt", 0, 0));
            case "hybrid_probe_ssgi_rt" -> List.of(
                    new RenderUniformRequirement("global_scene", "giProbeGrid", 0, 0),
                    new RenderUniformRequirement("global_scene", "giSsgi", 0, 0),
                    new RenderUniformRequirement("global_scene", "giRt", 0, 0)
            );
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
                        "gi_indirect",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("swapchainRecreated", "renderScaleChanged")
                )
        ));
        if ("probe_grid".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "gi_probe_grid",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    false,
                    List.of("sceneProbesChanged", "giModeChanged")
            ));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of(new RenderSchedulerDeclaration(
                "gi_phase1_scheduler",
                List.of(
                        new org.dynamislight.spi.render.RenderBudgetParameter("maxProbeUpdatesPerFrame", "16"),
                        new org.dynamislight.spi.render.RenderBudgetParameter("rtSamplesPerPixel", "1")
                ),
                false,
                true
        ));
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "GI_CAPABILITY_PLAN_ACTIVE",
                        "GI_PHASE1_POLICY",
                        "GI_PROMOTION_POLICY_ACTIVE",
                        "GI_SSGI_POLICY_ACTIVE",
                        "GI_PROMOTION_READY"
                ),
                List.of(
                        "giCapabilityDiagnostics",
                        "giPromotionDiagnostics"
                ),
                List.of(),
                List.of(
                        "gi.phase1.contract",
                        "gi.mode.policy"
                )
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null) {
            return MODE_SSGI;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(mode.id()))
                .findFirst()
                .orElse(MODE_SSGI);
    }

    private static List<String> readsFor(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return switch (id) {
            case "ssgi" -> List.of("scene_color", "scene_depth", "velocity");
            case "probe_grid" -> List.of("scene_color", "scene_depth", "probe_grid");
            case "rtgi_single" -> List.of("scene_color", "scene_depth", "rt_scene");
            case "hybrid_probe_ssgi_rt" -> List.of("scene_color", "scene_depth", "probe_grid", "velocity", "rt_scene");
            default -> List.of("scene_color", "scene_depth");
        };
    }
}
