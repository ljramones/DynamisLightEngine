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
import org.dynamislight.spi.render.RenderShaderModuleBinding;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
import org.dynamislight.spi.render.RenderShaderStage;
import org.dynamislight.spi.render.RenderTelemetryDeclaration;
import org.dynamislight.spi.render.RenderUniformRequirement;

/**
 * Vulkan PBR/shading capability v2 descriptor scaffold.
 */
public final class VulkanPbrCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_METALLIC_ROUGHNESS_BASELINE =
            new RenderFeatureMode("metallic_roughness_baseline");
    public static final RenderFeatureMode MODE_SPECULAR_GLOSSINESS =
            new RenderFeatureMode("specular_glossiness");
    public static final RenderFeatureMode MODE_SPECULAR_GLOSSINESS_DETAIL =
            new RenderFeatureMode("specular_glossiness_detail");
    public static final RenderFeatureMode MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING =
            new RenderFeatureMode("specular_glossiness_detail_layering");
    public static final RenderFeatureMode MODE_ADVANCED_SURFACE_STACK =
            new RenderFeatureMode("advanced_surface_stack");
    public static final RenderFeatureMode MODE_CINEMATIC_SURFACE_STACK =
            new RenderFeatureMode("cinematic_surface_stack");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_METALLIC_ROUGHNESS_BASELINE,
            MODE_SPECULAR_GLOSSINESS,
            MODE_SPECULAR_GLOSSINESS_DETAIL,
            MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING,
            MODE_ADVANCED_SURFACE_STACK,
            MODE_CINEMATIC_SURFACE_STACK
    );

    private final RenderFeatureMode activeMode;

    public VulkanPbrCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanPbrCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanPbrCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.pbr";
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
                "main_geometry",
                RenderPassPhase.MAIN,
                List.of("scene_uniforms", "material_uniforms", "scene_depth"),
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
                "main_geometry",
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                RenderShaderStage.FRAGMENT,
                "pbr_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                true,
                false,
                switch (active.id()) {
                    case "specular_glossiness" -> 131;
                    case "specular_glossiness_detail" -> 133;
                    case "specular_glossiness_detail_layering" -> 135;
                    case "advanced_surface_stack" -> 137;
                    case "cinematic_surface_stack" -> 139;
                    default -> 129;
                },
                false
        ));
    }

    @Override
    public List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        String body = switch (active.id()) {
            case "specular_glossiness" -> """
                vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic) {
                    float gloss = 1.0 - roughness;
                    float specBoost = mix(0.85, 1.15, gloss);
                    return litColor * mix(0.95, specBoost, clamp(1.0 - metallic, 0.0, 1.0));
                }
                """;
            case "specular_glossiness_detail" -> """
                vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic) {
                    float gloss = 1.0 - roughness;
                    float detailScale = clamp(ubo.emissiveReactiveGain, 0.8, 1.2);
                    float specBoost = mix(0.90, 1.20, gloss);
                    return litColor * detailScale * mix(0.95, specBoost, clamp(1.0 - metallic, 0.0, 1.0));
                }
                """;
            case "specular_glossiness_detail_layering" -> """
                vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic) {
                    float gloss = 1.0 - roughness;
                    float detailScale = clamp(ubo.emissiveReactiveGain, 0.8, 1.3);
                    float layerScale = clamp(ubo.taaReactiveMaskStrength, 0.7, 1.3);
                    float specBoost = mix(0.95, 1.25, gloss);
                    return litColor * detailScale * layerScale * mix(0.95, specBoost, clamp(1.0 - metallic, 0.0, 1.0));
                }
                """;
            case "advanced_surface_stack" -> """
                vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic) {
                    float clearCoatScale = 1.05;
                    float anisotropicScale = 1.03;
                    return litColor * clearCoatScale * anisotropicScale * mix(0.9, 1.1, 1.0 - roughness);
                }
                """;
            case "cinematic_surface_stack" -> """
                vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic) {
                    float sss = clamp(ubo.pbrSubsurfaceWeight, 0.0, 1.0);
                    float iridescence = clamp(ubo.pbrIridescenceWeight, 0.0, 1.0);
                    float sheen = clamp(ubo.pbrSheenWeight, 0.0, 1.0);
                    float pom = clamp(ubo.pbrParallaxWeight, 0.0, 1.0);
                    float tess = clamp(ubo.pbrTessellationWeight, 0.0, 1.0);
                    float decals = clamp(ubo.pbrDecalWeight, 0.0, 1.0);
                    float eye = clamp(ubo.pbrEyeShaderWeight, 0.0, 1.0);
                    float hair = clamp(ubo.pbrHairShaderWeight, 0.0, 1.0);
                    float cloth = clamp(ubo.pbrClothShaderWeight, 0.0, 1.0);
                    float style = (sss + iridescence + sheen + pom + tess + decals + eye + hair + cloth) / 9.0;
                    float sparkle = mix(0.96, 1.12, iridescence);
                    float fabricLift = mix(1.0, 1.05, sheen + cloth * 0.5);
                    return litColor * mix(0.9, 1.2, style) * sparkle * fabricLift * mix(0.92, 1.08, 1.0 - roughness);
                }
                """;
            default -> """
                vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic) {
                    return litColor;
                }
                """;
        };
        return List.of(new RenderShaderModuleDeclaration(
                "pbr.main.eval." + active.id(),
                featureId(),
                "main_geometry",
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                RenderShaderStage.FRAGMENT,
                "evaluatePbrMode",
                "vec3 evaluatePbrMode(vec3 litColor, float roughness, float metallic)",
                body,
                List.of(
                        new RenderShaderModuleBinding("uPbrPolicy", descriptorFor("main_geometry", 0, 90)),
                        new RenderShaderModuleBinding("uPbrDetail", descriptorFor("main_geometry", 1, 15))
                ),
                uniformRequirements(active),
                List.of(),
                switch (active.id()) {
                    case "specular_glossiness" -> 131;
                    case "specular_glossiness_detail" -> 133;
                    case "specular_glossiness_detail_layering" -> 135;
                    case "advanced_surface_stack" -> 137;
                    case "cinematic_surface_stack" -> 139;
                    default -> 129;
                },
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                descriptorFor("main_geometry", 0, 90)
        ));
        if (!MODE_METALLIC_ROUGHNESS_BASELINE.id().equals(active.id())) {
            requirements.add(descriptorFor("main_geometry", 1, 15));
        }
        if (MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id().equals(active.id())) {
            requirements.add(descriptorFor("main_geometry", 1, 16));
        }
        if (MODE_CINEMATIC_SURFACE_STACK.id().equals(active.id())) {
            requirements.add(descriptorFor("main_geometry", 1, 17));
            requirements.add(descriptorFor("main_geometry", 1, 18));
            requirements.add(descriptorFor("main_geometry", 1, 19));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderUniformRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderUniformRequirement("global_scene", "pbrModeFlags", 0, 0)
        ));
        if (!MODE_METALLIC_ROUGHNESS_BASELINE.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "pbrSpecGlossWeight", 0, 0));
        }
        if (MODE_SPECULAR_GLOSSINESS_DETAIL.id().equals(active.id())
                || MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "pbrDetailMapWeight", 0, 0));
        }
        if (MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "pbrLayerBlendWeight", 0, 0));
        }
        if (MODE_ADVANCED_SURFACE_STACK.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "pbrAdvancedSurfaceWeight", 0, 0));
        }
        if (MODE_CINEMATIC_SURFACE_STACK.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "pbrSubsurfaceWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrIridescenceWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrSheenWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrParallaxWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrTessellationWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrDecalWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrEyeShaderWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrHairShaderWeight", 0, 0));
            requirements.add(new RenderUniformRequirement("global_scene", "pbrClothShaderWeight", 0, 0));
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
        java.util.ArrayList<RenderResourceDeclaration> resources = new java.util.ArrayList<>(List.of(
                new RenderResourceDeclaration(
                        "pbr_material_flags",
                        RenderResourceType.STORAGE_BUFFER,
                        RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                        false,
                        List.of("sceneMaterialsChanged")
                )
        ));
        if (MODE_SPECULAR_GLOSSINESS_DETAIL.id().equals(active.id())
                || MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id().equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "pbr_detail_map_table",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    true,
                    List.of("sceneMaterialsChanged")
            ));
        }
        if (MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id().equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "pbr_layering_mask_buffer",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    true,
                    List.of("sceneMaterialsChanged")
            ));
        }
        if (MODE_CINEMATIC_SURFACE_STACK.id().equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "pbr_cinematic_surface_table",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    true,
                    List.of("sceneMaterialsChanged")
            ));
            resources.add(new RenderResourceDeclaration(
                    "pbr_decal_projection_table",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    true,
                    List.of("sceneMaterialsChanged", "sceneTransformsChanged")
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
                        "PBR_CAPABILITY_MODE_ACTIVE",
                        "PBR_POLICY",
                        "PBR_PROMOTION_READY"
                ),
                List.of("pbrCapabilityDiagnostics"),
                List.of(),
                List.of("pbr-capability-lockdown")
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null || mode.id().isBlank()) {
            return MODE_METALLIC_ROUGHNESS_BASELINE;
        }
        for (RenderFeatureMode supported : SUPPORTED) {
            if (supported.id().equals(mode.id())) {
                return supported;
            }
        }
        return MODE_METALLIC_ROUGHNESS_BASELINE;
    }

    private static RenderDescriptorRequirement descriptorFor(String passId, int set, int binding) {
        return new RenderDescriptorRequirement(
                passId,
                set,
                binding,
                RenderDescriptorType.COMBINED_IMAGE_SAMPLER,
                RenderBindingFrequency.PER_FRAME,
                true
        );
    }
}
