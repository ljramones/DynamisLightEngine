package org.dynamislight.impl.vulkan.capability;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.shader.VulkanCanonicalShaderModuleBodies;
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
import org.dynamislight.spi.render.RenderShaderModuleBinding;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
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
        boolean rtDenoiseMode = MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active);

        List<String> writes = new java.util.ArrayList<>();
        writes.add("shadow_depth");
        if (momentPipeline) {
            writes.add("shadow_moment_atlas");
        }
        if (cacheMode) {
            writes.add("shadow_cache_overlay");
        }
        if (rtDenoiseMode) {
            writes.add("shadow_rt_visibility");
        }

        List<RenderPassDeclaration> passes = new java.util.ArrayList<>();
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
    public List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderShaderModuleDeclaration> modules = new java.util.ArrayList<>(List.of(new RenderShaderModuleDeclaration(
                "shadow.main.evaluate." + active.id(),
                featureId(),
                "main_geometry",
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                RenderShaderStage.FRAGMENT,
                isMomentMode(active) ? "momentVisibilityApprox" : "finalizeShadowVisibility",
                isMomentMode(active)
                        ? "float momentVisibilityApprox(vec2 uv, float compareDepth, int layer)"
                        : "float finalizeShadowVisibility("
                            + "float pcfVisibility, int shadowFilterMode, bool shadowRtEnabled, int shadowRtMode, "
                            + "int shadowRtSampleCount, float shadowRtDenoiseStrength, float shadowRtRayLength, "
                            + "vec2 uv, float compareDepth, int layer, float ndl, float depthRatio, "
                            + "float pcssSoftness, float shadowTemporalStability)",
                VulkanCanonicalShaderModuleBodies.shadowMainBody(active),
                shadowMainModuleBindings(active),
                uniformRequirements(active),
                List.of(),
                10,
                false
        )));
        if (MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            modules.add(new RenderShaderModuleDeclaration(
                    "shadow.denoise.resolve." + active.id(),
                    featureId(),
                    "shadow_rt_denoise",
                    RenderShaderInjectionPoint.AUXILIARY,
                    RenderShaderStage.FRAGMENT,
                    "rtNativeDenoiseStack",
                    "float rtNativeDenoiseStack(float baseVisibility, vec2 uv, float compareDepth, int layer, float texel, "
                            + "float shadowRtDenoiseStrength, float shadowTemporalStability)",
                    VulkanCanonicalShaderModuleBodies.shadowMainBody(active),
                    List.of(
                            new RenderShaderModuleBinding("uShadowRtVisibility", descriptorByTargetSetBinding(active, "main_geometry", 1, 10)),
                            new RenderShaderModuleBinding("uShadowRtDenoised", descriptorByTargetSetBinding(active, "main_geometry", 1, 10))
                    ),
                    List.of(new RenderUniformRequirement("global_scene", "uShadowRtDenoiseBlend", 0, 0)),
                    List.of(),
                    20,
                    true
            ));
        }
        return List.copyOf(modules);
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        return descriptorRequirementsStatic(mode);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderUniformRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderUniformRequirement("global_scene", "uShadowLightViewProj", 0, 0),
                new RenderUniformRequirement("global_scene", "shadow_filter_rt_contact", 0, 0)
        ));
        if (MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "uShadowHybridBlendParams", 0, 0));
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
        boolean rtDenoiseMode = MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active);

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
                "SHADOW_CAPABILITY_MODE_ACTIVE",
                "SHADOW_TELEMETRY_PROFILE_ACTIVE",
                "SHADOW_POLICY_ACTIVE",
                "SHADOW_LOCAL_RENDER_BASELINE",
                "SHADOW_LOCAL_RENDER_DEFERRED_POLICY",
                "SHADOW_CADENCE_ENVELOPE",
                "SHADOW_CADENCE_ENVELOPE_BREACH",
                "SHADOW_POINT_FACE_BUDGET_ENVELOPE",
                "SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH",
                "SHADOW_SPOT_PROJECTED_CONTRACT",
                "SHADOW_SPOT_PROJECTED_CONTRACT_BREACH",
                "SHADOW_TOPOLOGY_CONTRACT",
                "SHADOW_TOPOLOGY_CONTRACT_BREACH",
                "SHADOW_CACHE_POLICY_ACTIVE",
                "SHADOW_CACHE_CHURN_HIGH",
                "SHADOW_HYBRID_COMPOSITION",
                "SHADOW_HYBRID_COMPOSITION_BREACH",
                "SHADOW_TRANSPARENT_RECEIVER_POLICY",
                "SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH",
                "SHADOW_AREA_APPROX_POLICY",
                "SHADOW_DISTANCE_FIELD_SOFT_POLICY",
                "SHADOW_CADENCE_PROMOTION_READY",
                "SHADOW_POINT_FACE_BUDGET_PROMOTION_READY",
                "SHADOW_SPOT_PROJECTED_PROMOTION_READY",
                "SHADOW_TOPOLOGY_PROMOTION_READY",
                "SHADOW_PHASEA_PROMOTION_READY",
                "SHADOW_PHASED_PROMOTION_READY"
        ));
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>(List.of(
                "shadowCapabilityDiagnostics",
                "shadowCadenceDiagnostics",
                "shadowPointBudgetDiagnostics",
                "shadowSpotProjectedDiagnostics",
                "shadowCacheDiagnostics",
                "shadowRtDiagnostics",
                "shadowHybridDiagnostics",
                "shadowTransparentReceiverDiagnostics",
                "shadowExtendedModeDiagnostics",
                "shadowTopologyDiagnostics",
                "shadowPhaseAPromotionDiagnostics",
                "shadowPhaseDPromotionDiagnostics"
        ));
        java.util.ArrayList<String> ciGates = new java.util.ArrayList<>(List.of(
                "shadow-phasea-lockdown",
                "shadow-phasec-lockdown",
                "shadow-transparent-lockdown",
                "shadow-phased-lockdown"
        ));
        if (MODE_RT.equals(active) || MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            warnings.add("SHADOW_RT_PATH_REQUESTED");
            warnings.add("SHADOW_RT_PATH_FALLBACK_ACTIVE");
            warnings.add("SHADOW_RT_DENOISE_ENVELOPE");
            warnings.add("SHADOW_RT_DENOISE_ENVELOPE_BREACH");
        }
        if (MODE_TRANSPARENT_RECEIVERS.equals(active)) {
            warnings.add("SHADOW_TRANSPARENT_RECEIVER_POLICY");
            warnings.add("SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH");
        }
        if (MODE_DISTANCE_FIELD_SOFT.equals(active)) {
            warnings.add("SHADOW_DISTANCE_FIELD_SOFT_POLICY");
            warnings.add("SHADOW_DISTANCE_FIELD_REQUIRED_UNAVAILABLE_BREACH");
        }
        if (MODE_AREA_APPROX.equals(active)) {
            warnings.add("SHADOW_AREA_APPROX_POLICY");
            warnings.add("SHADOW_AREA_APPROX_REQUIRED_UNAVAILABLE_BREACH");
        }
        if (MODE_CACHED_STATIC_DYNAMIC.equals(active)) {
            warnings.add("SHADOW_CACHE_POLICY_ACTIVE");
            warnings.add("SHADOW_CACHE_CHURN_HIGH");
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

    private static List<RenderShaderModuleBinding> shadowMainModuleBindings(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderShaderModuleBinding> bindings = new java.util.ArrayList<>(List.of(
                new RenderShaderModuleBinding(
                        "uShadowMap",
                        descriptorByTargetSetBinding(active, "main_geometry", 1, 4)
                )
        ));
        if (isMomentMode(active)) {
            bindings.add(new RenderShaderModuleBinding(
                    "uShadowMomentAtlas",
                    descriptorByTargetSetBinding(active, "main_geometry", 1, 8)
            ));
        }
        if (MODE_RT_DENOISED.equals(active) || MODE_HYBRID_CASCADE_CONTACT_RT.equals(active)) {
            bindings.add(new RenderShaderModuleBinding(
                    "uShadowRtDenoised",
                    descriptorByTargetSetBinding(active, "main_geometry", 1, 10)
            ));
        }
        return List.copyOf(bindings);
    }

    private static RenderDescriptorRequirement descriptorByTargetSetBinding(
            RenderFeatureMode mode,
            String targetPassId,
            int setIndex,
            int bindingIndex
    ) {
        for (RenderDescriptorRequirement requirement : descriptorRequirementsStatic(mode)) {
            if (requirement.targetPassId().equals(targetPassId)
                    && requirement.setIndex() == setIndex
                    && requirement.bindingIndex() == bindingIndex) {
                return requirement;
            }
        }
        return new RenderDescriptorRequirement(
                targetPassId,
                setIndex,
                bindingIndex,
                RenderDescriptorType.COMBINED_IMAGE_SAMPLER,
                RenderBindingFrequency.PER_FRAME,
                true
        );
    }

    private static List<RenderDescriptorRequirement> descriptorRequirementsStatic(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        boolean momentPipeline = isMomentMode(active);
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
        return List.copyOf(requirements);
    }
}
