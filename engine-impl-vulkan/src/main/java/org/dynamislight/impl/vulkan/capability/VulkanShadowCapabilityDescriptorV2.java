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

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_PCF,
            MODE_PCSS,
            MODE_VSM,
            MODE_EVSM,
            MODE_RT
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
        boolean momentPipeline = isMomentMode(mode);
        return List.of(new RenderPassDeclaration(
                "shadow_passes",
                RenderPassPhase.PRE_MAIN,
                List.of(),
                momentPipeline
                        ? List.of("shadow_depth", "shadow_moment_atlas")
                        : List.of("shadow_depth"),
                false,
                true,
                momentPipeline,
                List.of()
        ));
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
        boolean momentPipeline = isMomentMode(mode);
        List<RenderDescriptorRequirement> base = List.of(
                new RenderDescriptorRequirement("main_geometry", 1, 4, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
                new RenderDescriptorRequirement("shadow_passes", 0, 0, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false),
                new RenderDescriptorRequirement("shadow_passes", 0, 1, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_DRAW, false)
        );
        if (!momentPipeline) {
            return base;
        }
        return List.of(
                base.get(0),
                base.get(1),
                base.get(2),
                new RenderDescriptorRequirement("main_geometry", 1, 8, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true)
        );
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        return List.of(
                new RenderUniformRequirement("global_scene", "uShadowLightViewProj", 0, 0),
                new RenderUniformRequirement("global_scene", "shadow_filter_rt_contact", 0, 0)
        );
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of(new RenderPushConstantRequirement(
                "shadow_passes",
                List.of(RenderShaderStage.VERTEX),
                0,
                Integer.BYTES
        ));
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        boolean momentPipeline = isMomentMode(mode);
        if (!momentPipeline) {
            return List.of(new RenderResourceDeclaration(
                    "shadow_depth",
                    RenderResourceType.ATTACHMENT,
                    RenderResourceLifecycle.TRANSIENT,
                    false,
                    List.of("shadowMapResolutionChanged", "shadowCascadeCountChanged")
            ));
        }
        return List.of(
                new RenderResourceDeclaration(
                        "shadow_depth",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("shadowMapResolutionChanged", "shadowCascadeCountChanged")
                ),
                new RenderResourceDeclaration(
                        "shadow_moment_atlas",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        true,
                        List.of("shadowMomentModeChanged", "shadowMapResolutionChanged")
                )
        );
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of(new RenderSchedulerDeclaration(
                "shadow_cadence_scheduler",
                List.of(
                        new RenderBudgetParameter("maxShadowedLocalLights", "tier bounded max local lights with shadows"),
                        new RenderBudgetParameter("maxShadowLayers", "max atlas layers available per frame"),
                        new RenderBudgetParameter("maxFacesPerFrame", "point/cubemap face budget per frame")
                ),
                true,
                true
        ));
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "SHADOW_POLICY_ACTIVE",
                        "SHADOW_QUALITY_DEGRADED",
                        "SHADOW_LOCAL_RENDER_BASELINE",
                        "SHADOW_CASCADE_PROFILE"
                ),
                List.of("shadowCascadeProfile", "shadowDepthFormatTag", "shadowMomentFormatTag"),
                List.of(),
                List.of("shadow_ci_matrix", "shadow_lockdown", "shadow_real_longrun")
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
