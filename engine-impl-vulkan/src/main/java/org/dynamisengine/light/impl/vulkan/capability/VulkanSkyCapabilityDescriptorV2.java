package org.dynamisengine.light.impl.vulkan.capability;

import java.util.List;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.spi.render.RenderBindingFrequency;
import org.dynamisengine.light.spi.render.RenderDescriptorRequirement;
import org.dynamisengine.light.spi.render.RenderDescriptorType;
import org.dynamisengine.light.spi.render.RenderFeatureCapabilityV2;
import org.dynamisengine.light.spi.render.RenderFeatureMode;
import org.dynamisengine.light.spi.render.RenderPassDeclaration;
import org.dynamisengine.light.spi.render.RenderPassPhase;
import org.dynamisengine.light.spi.render.RenderPushConstantRequirement;
import org.dynamisengine.light.spi.render.RenderResourceDeclaration;
import org.dynamisengine.light.spi.render.RenderResourceLifecycle;
import org.dynamisengine.light.spi.render.RenderResourceType;
import org.dynamisengine.light.spi.render.RenderSchedulerDeclaration;
import org.dynamisengine.light.spi.render.RenderShaderContribution;
import org.dynamisengine.light.spi.render.RenderShaderInjectionPoint;
import org.dynamisengine.light.spi.render.RenderShaderModuleBinding;
import org.dynamisengine.light.spi.render.RenderShaderModuleDeclaration;
import org.dynamisengine.light.spi.render.RenderShaderStage;
import org.dynamisengine.light.spi.render.RenderTelemetryDeclaration;
import org.dynamisengine.light.spi.render.RenderUniformRequirement;

/**
 * Vulkan sky/atmosphere capability v2 descriptor scaffold.
 */
public final class VulkanSkyCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_HDRI = new RenderFeatureMode("hdri");
    public static final RenderFeatureMode MODE_PROCEDURAL = new RenderFeatureMode("procedural");
    public static final RenderFeatureMode MODE_ATMOSPHERE = new RenderFeatureMode("atmosphere");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_HDRI,
            MODE_PROCEDURAL,
            MODE_ATMOSPHERE
    );

    private final RenderFeatureMode activeMode;

    public VulkanSkyCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanSkyCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanSkyCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.sky";
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
        return List.of(new RenderPassDeclaration(
                "post_composite",
                RenderPassPhase.POST_MAIN,
                List.of("scene_color"),
                List.of("scene_color"),
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
                "sky_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                false,
                false,
                switch (active.id()) {
                    case "procedural" -> 293;
                    case "atmosphere" -> 295;
                    default -> 291;
                },
                false
        ));
    }

    @Override
    public List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        String body = switch (active.id()) {
            case "procedural" -> """
                    vec3 resolveSkyContribution(vec2 uv, vec3 colorIn) {
                        vec3 horizon = vec3(0.09, 0.16, 0.24);
                        vec3 zenith = vec3(0.30, 0.45, 0.70);
                        vec3 sky = mix(horizon, zenith, clamp(1.0 - uv.y, 0.0, 1.0));
                        return mix(colorIn, colorIn + sky * 0.08, 0.20);
                    }
                    """;
            case "atmosphere" -> """
                    vec3 resolveSkyContribution(vec2 uv, vec3 colorIn) {
                        vec3 base = mix(vec3(0.08, 0.12, 0.20), vec3(0.45, 0.62, 0.85), clamp(1.0 - uv.y, 0.0, 1.0));
                        float aerial = clamp(ubo.giDynamicSky, 0.0, 1.0);
                        return mix(colorIn, colorIn + base * 0.10, 0.18 + 0.12 * aerial);
                    }
                    """;
            default -> """
                    vec3 resolveSkyContribution(vec2 uv, vec3 colorIn) {
                        return colorIn;
                    }
                    """;
        };
        return List.of(new RenderShaderModuleDeclaration(
                "sky.post.resolve." + active.id(),
                featureId(),
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "resolveSkyContribution",
                "vec3 resolveSkyContribution(vec2 uv, vec3 colorIn)",
                body,
                List.of(new RenderShaderModuleBinding("uSkyPolicy", descriptorFor("post_composite", 0, 260))),
                uniformRequirements(active),
                List.of(),
                switch (active.id()) {
                    case "procedural" -> 293;
                    case "atmosphere" -> 295;
                    default -> 291;
                },
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        return List.of(
                descriptorFor("post_composite", 0, 260)
        );
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        if (MODE_ATMOSPHERE.id().equals(active.id())) {
            return List.of(
                    new RenderUniformRequirement("global_scene", "giDynamicSky", 0, 0),
                    new RenderUniformRequirement("global_scene", "postLumaClip", 0, 0)
            );
        }
        return List.of(new RenderUniformRequirement("global_scene", "postLumaClip", 0, 0));
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        return List.of(
                new RenderResourceDeclaration(
                        "sky_mode_state",
                        RenderResourceType.UNIFORM_BUFFER,
                        RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                        false,
                        List.of("backendOptionsChanged", "sceneEnvironmentChanged")
                )
        );
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "SKY_CAPABILITY_PLAN_ACTIVE",
                        "SKY_CAPABILITY_MODE_ACTIVE",
                        "SKY_POLICY_ACTIVE",
                        "SKY_PROMOTION_ENVELOPE",
                        "SKY_PROMOTION_ENVELOPE_BREACH",
                        "SKY_PROMOTION_READY"
                ),
                List.of(
                        "skyCapabilityDiagnostics",
                        "skyPromotionDiagnostics"
                ),
                List.of(),
                List.of("sky-phase1-lockdown")
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null || mode.id().isBlank()) {
            return MODE_HDRI;
        }
        for (RenderFeatureMode supported : SUPPORTED) {
            if (supported.id().equals(mode.id())) {
                return supported;
            }
        }
        return MODE_HDRI;
    }

    private static RenderDescriptorRequirement descriptorFor(String passId, int set, int binding) {
        return new RenderDescriptorRequirement(
                passId,
                set,
                binding,
                RenderDescriptorType.UNIFORM_BUFFER,
                RenderBindingFrequency.PER_FRAME,
                false
        );
    }
}
