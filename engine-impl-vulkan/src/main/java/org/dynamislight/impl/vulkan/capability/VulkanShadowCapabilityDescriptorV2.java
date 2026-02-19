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
 * v2 metadata-only shadow capability descriptor extracted from Vulkan runtime behavior.
 */
public final class VulkanShadowCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_PCF = new RenderFeatureMode("pcf");
    public static final RenderFeatureMode MODE_PCSS = new RenderFeatureMode("pcss");
    public static final RenderFeatureMode MODE_VSM = new RenderFeatureMode("vsm");
    public static final RenderFeatureMode MODE_EVSM = new RenderFeatureMode("evsm");
    public static final RenderFeatureMode MODE_RT = new RenderFeatureMode("rt");
    public static final RenderFeatureMode MODE_LOCAL_ATLAS_CADENCE = new RenderFeatureMode("local_atlas_cadence");
    public static final RenderFeatureMode MODE_POINT_CUBEMAP_BUDGET = new RenderFeatureMode("point_cubemap_budget");
    public static final RenderFeatureMode MODE_SPOT_PROJECTED = new RenderFeatureMode("spot_projected");
    public static final RenderFeatureMode MODE_AREA_APPROX = new RenderFeatureMode("area_approx");
    public static final RenderFeatureMode MODE_RT_DENOISED = new RenderFeatureMode("rt_denoised");
    public static final RenderFeatureMode MODE_HYBRID_CASCADE_CONTACT_RT = new RenderFeatureMode("hybrid_cascade_contact_rt");
    public static final RenderFeatureMode MODE_TRANSPARENT_RECEIVERS = new RenderFeatureMode("transparent_receivers");
    public static final RenderFeatureMode MODE_CACHED_STATIC_DYNAMIC = new RenderFeatureMode("cached_static_dynamic");
    public static final RenderFeatureMode MODE_DISTANCE_FIELD_SOFT = new RenderFeatureMode("distance_field_soft");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_PCF,
            MODE_PCSS,
            MODE_VSM,
            MODE_EVSM,
            MODE_RT,
            MODE_LOCAL_ATLAS_CADENCE,
            MODE_POINT_CUBEMAP_BUDGET,
            MODE_SPOT_PROJECTED,
            MODE_AREA_APPROX,
            MODE_RT_DENOISED,
            MODE_HYBRID_CASCADE_CONTACT_RT,
            MODE_TRANSPARENT_RECEIVERS,
            MODE_CACHED_STATIC_DYNAMIC,
            MODE_DISTANCE_FIELD_SOFT
    );

    private final RenderFeatureMode activeMode;

    public VulkanShadowCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanShadowCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanShadowCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.shadow";
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
        boolean momentPipeline = isMomentMode(active);
        boolean cacheMode = MODE_CACHED_STATIC_DYNAMIC.equals(active);
        boolean distanceFieldMode = MODE_DISTANCE_FIELD_SOFT.equals(active);
        boolean rtDenoiseMode = MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active);
        boolean transparentReceivers = MODE_TRANSPARENT_RECEIVERS.equals(active);

        List<String> writes = new java.util.ArrayList<>();
        writes.add("shadow_depth");
        if (momentPipeline) {
            writes.add("shadow_moment_atlas");
        }
        if (cacheMode) {
            writes.add("shadow_cache_overlay");
        }
        if (transparentReceivers) {
            writes.add("shadow_transparency_mask");
        }
        if (rtDenoiseMode) {
            writes.add("shadow_rt_visibility");
        }

        List<RenderPassDeclaration> passes = new java.util.ArrayList<>();
        if (distanceFieldMode) {
            passes.add(new RenderPassDeclaration(
                    "shadow_distance_field_prepass",
                    RenderPassPhase.PRE_MAIN,
                    List.of(),
                    List.of("shadow_distance_field"),
                    false,
                    false,
                    false,
                    List.of()
            ));
        }

        passes.add(new RenderPassDeclaration(
                "shadow_passes",
                RenderPassPhase.PRE_MAIN,
                cacheMode ? List.of("shadow_cache_static") : List.of(),
                writes,
                false,
                true,
                momentPipeline || rtDenoiseMode,
                List.of()
        ));

        if (rtDenoiseMode) {
            passes.add(new RenderPassDeclaration(
                    "shadow_rt_denoise",
                    RenderPassPhase.PRE_MAIN,
                    List.of("shadow_rt_visibility"),
                    List.of("shadow_rt_denoised"),
                    false,
                    false,
                    false,
                    List.of()
            ));
        }
        return List.copyOf(passes);
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        return List.of(new RenderShaderContribution(
                "main_geometry",
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                RenderShaderStage.FRAGMENT,
                "shadow_sample_" + sanitizeMode(mode).id(),
                descriptorRequirements(mode),
                uniformRequirements(mode),
                pushConstantRequirements(mode),
                false,
                false,
                10,
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        boolean momentPipeline = isMomentMode(active);
        boolean transparentReceivers = MODE_TRANSPARENT_RECEIVERS.equals(active);
        boolean distanceFieldMode = MODE_DISTANCE_FIELD_SOFT.equals(active);
        boolean rtDenoiseMode = MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active);

        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderDescriptorRequirement("main_geometry", 1, 4, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
                new RenderDescriptorRequirement("shadow_passes", 0, 0, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false),
                new RenderDescriptorRequirement("shadow_passes", 0, 1, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_DRAW, false)
        ));
        if (momentPipeline) {
            requirements.add(new RenderDescriptorRequirement("main_geometry", 1, 8, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
        }
        if (rtDenoiseMode) {
            requirements.add(new RenderDescriptorRequirement("main_geometry", 1, 10, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
        }
        if (transparentReceivers) {
            requirements.add(new RenderDescriptorRequirement("main_geometry", 1, 11, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
        }
        if (distanceFieldMode) {
            requirements.add(new RenderDescriptorRequirement("main_geometry", 1, 12, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderUniformRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderUniformRequirement("global_scene", "uShadowLightViewProj", 0, 0),
                new RenderUniformRequirement("global_scene", "shadow_filter_rt_contact", 0, 0)
        ));
        if (MODE_AREA_APPROX.equals(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "uAreaShadowLtcParams", 0, 0));
        }
        if (MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "uShadowHybridBlendParams", 0, 0));
        }
        if (MODE_TRANSPARENT_RECEIVERS.equals(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "uShadowTransparencyParams", 0, 0));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderPushConstantRequirement> requirements = new java.util.ArrayList<>(List.of(new RenderPushConstantRequirement(
                "shadow_passes",
                List.of(RenderShaderStage.VERTEX),
                0,
                Integer.BYTES
        )));
        if (MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            requirements.add(new RenderPushConstantRequirement(
                    "shadow_rt_denoise",
                    List.of(RenderShaderStage.FRAGMENT),
                    0,
                    Integer.BYTES * 4
            ));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        boolean momentPipeline = isMomentMode(active);
        boolean cacheMode = MODE_CACHED_STATIC_DYNAMIC.equals(active);
        boolean distanceFieldMode = MODE_DISTANCE_FIELD_SOFT.equals(active);
        boolean rtDenoiseMode = MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active);
        boolean transparentReceivers = MODE_TRANSPARENT_RECEIVERS.equals(active);

        java.util.ArrayList<RenderResourceDeclaration> resources = new java.util.ArrayList<>(List.of(
                new RenderResourceDeclaration(
                        "shadow_depth",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("shadowMapResolutionChanged", "shadowCascadeCountChanged")
                )
        ));
        if (momentPipeline) {
            resources.add(new RenderResourceDeclaration(
                        "shadow_moment_atlas",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        true,
                        List.of("shadowMomentModeChanged", "shadowMapResolutionChanged")
                ));
        }
        if (cacheMode) {
            resources.add(new RenderResourceDeclaration(
                    "shadow_cache_static",
                    RenderResourceType.ATTACHMENT,
                    RenderResourceLifecycle.PERSISTENT,
                    false,
                    List.of("staticShadowCastersChanged", "shadowCachePolicyChanged")
            ));
            resources.add(new RenderResourceDeclaration(
                    "shadow_cache_overlay",
                    RenderResourceType.ATTACHMENT,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    false,
                    List.of("dynamicShadowCastersChanged", "shadowCachePolicyChanged")
            ));
        }
        if (distanceFieldMode) {
            resources.add(new RenderResourceDeclaration(
                    "shadow_distance_field",
                    RenderResourceType.STORAGE_IMAGE,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    false,
                    List.of("distanceFieldVolumeChanged", "distanceFieldResolutionChanged")
            ));
        }
        if (rtDenoiseMode) {
            resources.add(new RenderResourceDeclaration(
                    "shadow_rt_visibility",
                    RenderResourceType.STORAGE_IMAGE,
                    RenderResourceLifecycle.TRANSIENT,
                    false,
                    List.of("shadowRtModeChanged", "swapchainRecreated")
            ));
            resources.add(new RenderResourceDeclaration(
                    "shadow_rt_denoised",
                    RenderResourceType.STORAGE_IMAGE,
                    RenderResourceLifecycle.TRANSIENT,
                    false,
                    List.of("shadowRtModeChanged", "swapchainRecreated")
            ));
        }
        if (transparentReceivers) {
            resources.add(new RenderResourceDeclaration(
                    "shadow_transparency_mask",
                    RenderResourceType.ATTACHMENT,
                    RenderResourceLifecycle.TRANSIENT,
                    false,
                    List.of("transparentReceiverSetChanged", "swapchainRecreated")
            ));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderSchedulerDeclaration> schedulers = new java.util.ArrayList<>(List.of(new RenderSchedulerDeclaration(
                "shadow_cadence_scheduler",
                List.of(
                        new RenderBudgetParameter("maxShadowedLocalLights", "tier bounded max local lights with shadows"),
                        new RenderBudgetParameter("maxShadowLayers", "max atlas layers available per frame"),
                        new RenderBudgetParameter("maxFacesPerFrame", "point/cubemap face budget per frame")
                ),
                true,
                true
        )));
        if (MODE_POINT_CUBEMAP_BUDGET.equals(active)) {
            schedulers.add(new RenderSchedulerDeclaration(
                    "shadow_point_face_scheduler",
                    List.of(
                            new RenderBudgetParameter("pointFaceBudget", "max cubemap faces updated per frame"),
                            new RenderBudgetParameter("pointHeroRefreshFrames", "full-rate refresh cadence for hero point lights")
                    ),
                    true,
                    true
            ));
        }
        if (MODE_CACHED_STATIC_DYNAMIC.equals(active)) {
            schedulers.add(new RenderSchedulerDeclaration(
                    "shadow_cache_scheduler",
                    List.of(
                            new RenderBudgetParameter("cacheStaticRefreshFrames", "static cache refresh cadence"),
                            new RenderBudgetParameter("cacheOverlayBudget", "dynamic overlay update budget per frame")
                    ),
                    true,
                    true
            ));
        }
        return List.copyOf(schedulers);
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>(List.of(
                "SHADOW_POLICY_ACTIVE",
                "SHADOW_QUALITY_DEGRADED",
                "SHADOW_LOCAL_RENDER_BASELINE",
                "SHADOW_CASCADE_PROFILE"
        ));
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>(List.of(
                "shadowCascadeProfile",
                "shadowDepthFormatTag",
                "shadowMomentFormatTag"
        ));
        java.util.ArrayList<String> ciGates = new java.util.ArrayList<>(List.of(
                "shadow_ci_matrix",
                "shadow_lockdown",
                "shadow_real_longrun"
        ));
        if (MODE_RT.equals(active) || MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            warnings.add("SHADOW_RT_PATH_REQUIRED_UNAVAILABLE_BREACH");
            warnings.add("SHADOW_RT_PERF_GATES_BREACH");
            diagnostics.add("debugShadowRtDiagnostics");
            ciGates.add("shadow_rt_lockdown");
        }
        if (MODE_TRANSPARENT_RECEIVERS.equals(active)) {
            warnings.add("SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH");
            diagnostics.add("debugShadowTransparentReceiverDiagnostics");
        }
        if (MODE_DISTANCE_FIELD_SOFT.equals(active)) {
            warnings.add("SHADOW_DISTANCE_FIELD_ENVELOPE_BREACH");
            diagnostics.add("debugShadowDistanceFieldDiagnostics");
        }
        if (MODE_CACHED_STATIC_DYNAMIC.equals(active)) {
            warnings.add("SHADOW_CACHE_POLICY_ACTIVE");
            warnings.add("SHADOW_CACHE_CHURN_HIGH");
            diagnostics.add("debugShadowCacheDiagnostics");
            ciGates.add("shadow_cache_stability");
        }
        return new RenderTelemetryDeclaration(
                List.copyOf(warnings),
                List.copyOf(diagnostics),
                List.of(),
                List.copyOf(ciGates)
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id().isBlank()) {
            return MODE_PCF;
        }
        for (RenderFeatureMode candidate : SUPPORTED) {
            if (candidate.id().equalsIgnoreCase(mode.id())) {
                return candidate;
            }
        }
        return MODE_PCF;
    }

    private static boolean isMomentMode(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "vsm".equals(id) || "evsm".equals(id);
    }
}
