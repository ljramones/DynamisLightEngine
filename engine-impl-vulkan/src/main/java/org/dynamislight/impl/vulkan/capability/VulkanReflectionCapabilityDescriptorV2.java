package org.dynamislight.impl.vulkan.capability;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderBudgetParameter;
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
 * v2 metadata-only reflections capability descriptor extracted from Vulkan runtime behavior.
 */
public final class VulkanReflectionCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_IBL_ONLY = new RenderFeatureMode("ibl_only");
    public static final RenderFeatureMode MODE_SSR = new RenderFeatureMode("ssr");
    public static final RenderFeatureMode MODE_PLANAR = new RenderFeatureMode("planar");
    public static final RenderFeatureMode MODE_HYBRID = new RenderFeatureMode("hybrid");
    public static final RenderFeatureMode MODE_RT_HYBRID = new RenderFeatureMode("rt_hybrid");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_IBL_ONLY,
            MODE_SSR,
            MODE_PLANAR,
            MODE_HYBRID,
            MODE_RT_HYBRID
    );

    private final RenderFeatureMode activeMode;

    public VulkanReflectionCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanReflectionCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanReflectionCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.reflections";
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
        boolean planar = usesPlanar(active);
        boolean reflectionsInPost = !MODE_IBL_ONLY.equals(active);
        if (planar) {
            return List.of(
                    new RenderPassDeclaration(
                            "planar_capture",
                            RenderPassPhase.PRE_MAIN,
                            List.of("scene_color"),
                            List.of("planar_capture"),
                            true,
                            false,
                            true,
                            List.of("vulkan.shadow")
                    ),
                    new RenderPassDeclaration(
                            "post_composite",
                            RenderPassPhase.POST_MAIN,
                            List.of("scene_color", "velocity", "history_color", "history_velocity", "planar_capture"),
                            List.of("resolved_color", "history_color_next", "history_velocity_next"),
                            reflectionsInPost,
                            false,
                            true,
                            List.of()
                    )
            );
        }
        return List.of(new RenderPassDeclaration(
                "post_composite",
                RenderPassPhase.POST_MAIN,
                List.of("scene_color", "velocity", "history_color", "history_velocity"),
                List.of("resolved_color", "history_color_next", "history_velocity_next"),
                reflectionsInPost,
                false,
                true,
                List.of()
        ));
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return List.of(
                new RenderShaderContribution(
                        "main_geometry",
                        RenderShaderInjectionPoint.LIGHTING_EVAL,
                        RenderShaderStage.FRAGMENT,
                        "probe_sampling_" + active.id(),
                        List.of(
                                new RenderDescriptorRequirement("main_geometry", 0, 2, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, false),
                                new RenderDescriptorRequirement("main_geometry", 1, 9, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_MATERIAL, false)
                        ),
                        List.of(
                                new RenderUniformRequirement("global_scene", "uPlanarView", 0, 0),
                                new RenderUniformRequirement("global_scene", "uPlanarProj", 0, 0),
                                new RenderUniformRequirement("global_scene", "uPlanarPrevViewProj", 0, 0)
                        ),
                        List.of(new RenderPushConstantRequirement("main_geometry", List.of(RenderShaderStage.VERTEX, RenderShaderStage.FRAGMENT), 0, 16)),
                        true,
                        false,
                        20,
                        false
                ),
                new RenderShaderContribution(
                        "post_composite",
                        RenderShaderInjectionPoint.POST_RESOLVE,
                        RenderShaderStage.FRAGMENT,
                        "reflection_resolve_" + active.id(),
                        List.of(
                                new RenderDescriptorRequirement("post_composite", 0, 0, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                                new RenderDescriptorRequirement("post_composite", 0, 1, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true),
                                new RenderDescriptorRequirement("post_composite", 0, 2, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                                new RenderDescriptorRequirement("post_composite", 0, 3, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true),
                                new RenderDescriptorRequirement("post_composite", 0, 4, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true)
                        ),
                        List.of(
                                new RenderUniformRequirement("post_push", "reflectionsA", 0, 16),
                                new RenderUniformRequirement("post_push", "reflectionsB", 0, 16)
                        ),
                        List.of(new RenderPushConstantRequirement("post_composite", List.of(RenderShaderStage.FRAGMENT), 0, 128)),
                        true,
                        true,
                        10,
                        !MODE_IBL_ONLY.equals(active)
                )
        );
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        return shaderContributions(mode).stream()
                .flatMap(sc -> sc.descriptorRequirements().stream())
                .toList();
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        return shaderContributions(mode).stream()
                .flatMap(sc -> sc.uniformRequirements().stream())
                .toList();
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return shaderContributions(mode).stream()
                .flatMap(sc -> sc.pushConstantRequirements().stream())
                .toList();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        boolean planar = usesPlanar(active);
        boolean rt = MODE_RT_HYBRID.equals(active);
        return List.of(
                new RenderResourceDeclaration(
                        "probe_metadata",
                        RenderResourceType.STORAGE_BUFFER,
                        RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                        false,
                        List.of("probeSetChanged", "probeStreamingBudgetChanged")
                ),
                new RenderResourceDeclaration(
                        "probe_radiance_atlas",
                        RenderResourceType.SAMPLED_IMAGE,
                        RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                        false,
                        List.of("probeSlotsChanged", "probeTextureAssetsChanged")
                ),
                new RenderResourceDeclaration(
                        "planar_capture",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        !planar,
                        List.of("planarModeChanged", "swapchainRecreated")
                ),
                new RenderResourceDeclaration(
                        "history_color",
                        RenderResourceType.SAMPLED_IMAGE,
                        RenderResourceLifecycle.CROSS_FRAME_TEMPORAL,
                        false,
                        List.of("taaReset", "swapchainRecreated")
                ),
                new RenderResourceDeclaration(
                        "history_velocity",
                        RenderResourceType.SAMPLED_IMAGE,
                        RenderResourceLifecycle.CROSS_FRAME_TEMPORAL,
                        false,
                        List.of("taaReset", "swapchainRecreated")
                ),
                new RenderResourceDeclaration(
                        "rt_reflection_lane",
                        RenderResourceType.STORAGE_IMAGE,
                        RenderResourceLifecycle.TRANSIENT,
                        !rt,
                        List.of("rtLaneActivationChanged", "swapchainRecreated")
                )
        );
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of(
                new RenderSchedulerDeclaration(
                        "probe_streaming_scheduler",
                        List.of(
                                new RenderBudgetParameter("probeMaxVisible", "max probes active per frame"),
                                new RenderBudgetParameter("probeUpdateCadenceFrames", "cadence rotation period for visible probes"),
                                new RenderBudgetParameter("probeLodDepthScale", "depth-based LOD tier scale")
                        ),
                        false,
                        true
                ),
                new RenderSchedulerDeclaration(
                        "reflection_lane_scheduler",
                        List.of(
                                new RenderBudgetParameter("rtRequireActive", "strict RT lane availability gate"),
                                new RenderBudgetParameter("rtRequireMultiBounce", "strict multi-bounce availability gate"),
                                new RenderBudgetParameter("planarScopeInclude*", "planar selective include/exclude policy")
                        ),
                        false,
                        true
                )
        );
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "REFLECTIONS_BASELINE_ACTIVE",
                        "REFLECTION_OVERRIDE_POLICY_ENVELOPE",
                        "REFLECTION_CONTACT_HARDENING_POLICY",
                        "REFLECTION_PROBE_STREAMING_DIAGNOSTICS",
                        "REFLECTION_PLANAR_PERF_GATES",
                        "REFLECTION_RT_PATH_REQUESTED",
                        "REFLECTION_SSR_TAA_DIAGNOSTICS",
                        "REFLECTION_TRANSPARENCY_POLICY"
                ),
                List.of(
                        "debugReflectionProbeDiagnostics",
                        "debugReflectionProbeStreamingDiagnostics",
                        "debugReflectionPlanarContractDiagnostics",
                        "debugReflectionPlanarPerfDiagnostics",
                        "debugReflectionRtPathDiagnostics",
                        "debugReflectionRtPerfDiagnostics",
                        "debugReflectionTransparencyDiagnostics",
                        "debugReflectionAdaptiveTrendSloDiagnostics"
                ),
                List.of("ReflectionAdaptiveTelemetryEvent", "PerformanceWarningEvent"),
                List.of("planar_ci_lockdown", "rt_reflections_lockdown", "adaptive_trend_slo")
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id().isBlank()) {
            return MODE_HYBRID;
        }
        for (RenderFeatureMode candidate : SUPPORTED) {
            if (candidate.id().equalsIgnoreCase(mode.id())) {
                return candidate;
            }
        }
        return MODE_HYBRID;
    }

    private static boolean usesPlanar(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return MODE_PLANAR.equals(active) || MODE_HYBRID.equals(active) || MODE_RT_HYBRID.equals(active);
    }
}
