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
 * Vulkan lighting capability v2 descriptor scaffold for capability-first expansion.
 */
public final class VulkanLightingCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_BASELINE_DIRECTIONAL_POINT_SPOT =
            new RenderFeatureMode("baseline_directional_point_spot");
    public static final RenderFeatureMode MODE_LIGHT_BUDGET_PRIORITY =
            new RenderFeatureMode("light_budget_priority");
    public static final RenderFeatureMode MODE_PHYSICALLY_BASED_UNITS =
            new RenderFeatureMode("physically_based_units");
    public static final RenderFeatureMode MODE_EMISSIVE_MESH =
            new RenderFeatureMode("emissive_mesh");
    public static final RenderFeatureMode MODE_PHYS_UNITS_BUDGET_EMISSIVE =
            new RenderFeatureMode("phys_units_budget_emissive");
    public static final RenderFeatureMode MODE_ADVANCED_POLICY_STACK =
            new RenderFeatureMode("advanced_policy_stack");
    public static final RenderFeatureMode MODE_PHYS_UNITS_BUDGET_EMISSIVE_ADVANCED =
            new RenderFeatureMode("phys_units_budget_emissive_advanced");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_BASELINE_DIRECTIONAL_POINT_SPOT,
            MODE_LIGHT_BUDGET_PRIORITY,
            MODE_PHYSICALLY_BASED_UNITS,
            MODE_EMISSIVE_MESH,
            MODE_PHYS_UNITS_BUDGET_EMISSIVE,
            MODE_ADVANCED_POLICY_STACK,
            MODE_PHYS_UNITS_BUDGET_EMISSIVE_ADVANCED
    );

    private final RenderFeatureMode activeMode;

    public VulkanLightingCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanLightingCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanLightingCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.lighting";
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
                "main_lighting",
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
        int ordering = switch (active.id()) {
            case "light_budget_priority" -> 118;
            case "physically_based_units" -> 120;
            case "emissive_mesh" -> 122;
            case "phys_units_budget_emissive" -> 124;
            case "advanced_policy_stack" -> 126;
            case "phys_units_budget_emissive_advanced" -> 128;
            default -> 115;
        };
        return List.of(new RenderShaderContribution(
                "main_geometry",
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                RenderShaderStage.FRAGMENT,
                "lighting_" + active.id(),
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
    public List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return List.of(new RenderShaderModuleDeclaration(
                "lighting.main.eval." + active.id(),
                featureId(),
                "main_geometry",
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                RenderShaderStage.FRAGMENT,
                "evaluateLightingMode",
                "vec3 evaluateLightingMode(vec3 litColor, float localLightLoad)",
                """
                vec3 evaluateLightingMode(vec3 litColor, float localLightLoad) {
                    float budgetScale = clamp(1.0 - (localLightLoad * 0.04), 0.65, 1.0);
                    return litColor * budgetScale;
                }
                """,
                List.of(
                        new RenderShaderModuleBinding("uLightingPolicy", descriptorFor("main_geometry", 0, 80)),
                        new RenderShaderModuleBinding("uLightingBudget", descriptorFor("main_geometry", 0, 81))
                ),
                uniformRequirements(active),
                List.of(),
                switch (active.id()) {
                    case "light_budget_priority" -> 118;
                    case "physically_based_units" -> 120;
                    case "emissive_mesh" -> 122;
                    case "phys_units_budget_emissive" -> 124;
                    case "advanced_policy_stack" -> 126;
                    case "phys_units_budget_emissive_advanced" -> 128;
                    default -> 115;
                },
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                descriptorFor("main_geometry", 0, 80),
                descriptorFor("main_geometry", 0, 81)
        ));
        if (MODE_EMISSIVE_MESH.id().equals(active.id()) || MODE_PHYS_UNITS_BUDGET_EMISSIVE.id().equals(active.id())) {
            requirements.add(new RenderDescriptorRequirement(
                    "main_geometry",
                    0,
                    82,
                    RenderDescriptorType.STORAGE_BUFFER,
                    RenderBindingFrequency.PER_FRAME,
                    true
            ));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderUniformRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderUniformRequirement("global_scene", "lightingExposure", 0, 0),
                new RenderUniformRequirement("global_scene", "lightingUnitScale", 0, 0)
        ));
        if (MODE_LIGHT_BUDGET_PRIORITY.id().equals(active.id()) || MODE_PHYS_UNITS_BUDGET_EMISSIVE.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "lightingBudget", 0, 0));
        }
        if (MODE_EMISSIVE_MESH.id().equals(active.id()) || MODE_PHYS_UNITS_BUDGET_EMISSIVE.id().equals(active.id())) {
            requirements.add(new RenderUniformRequirement("global_scene", "emissiveLightScale", 0, 0));
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
                        "lighting_budget_state",
                        RenderResourceType.STORAGE_BUFFER,
                        RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                        false,
                        List.of("qualityTierChanged", "sceneLightsChanged")
                )
        ));
        if (MODE_EMISSIVE_MESH.id().equals(active.id()) || MODE_PHYS_UNITS_BUDGET_EMISSIVE.id().equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "lighting_emissive_candidates",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    true,
                    List.of("sceneMaterialsChanged", "sceneMeshesChanged")
            ));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of(new RenderSchedulerDeclaration(
                "lighting_budget_scheduler",
                List.of(
                        new org.dynamislight.spi.render.RenderBudgetParameter("maxLocalLightsPerFrame", "8"),
                        new org.dynamislight.spi.render.RenderBudgetParameter("maxEmissiveMeshLightsPerFrame", "32")
                ),
                true,
                true
        ));
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "LIGHTING_CAPABILITY_MODE_ACTIVE",
                        "LIGHTING_TELEMETRY_PROFILE_ACTIVE",
                        "LIGHTING_BUDGET_POLICY",
                        "LIGHTING_BUDGET_ENVELOPE",
                        "LIGHTING_BUDGET_ENVELOPE_BREACH",
                        "LIGHTING_ADVANCED_POLICY",
                        "LIGHTING_BUDGET_PROMOTION_READY",
                        "LIGHTING_PHYS_UNITS_POLICY",
                        "LIGHTING_PHYS_UNITS_PROMOTION_READY",
                        "LIGHTING_EMISSIVE_POLICY",
                        "LIGHTING_EMISSIVE_ENVELOPE_BREACH",
                        "LIGHTING_EMISSIVE_PROMOTION_READY",
                        "LIGHTING_ADVANCED_PROMOTION_READY",
                        "LIGHTING_PHASE2_PROMOTION_READY"
                ),
                List.of(
                        "lightingCapabilityDiagnostics",
                        "lightingBudgetDiagnostics",
                        "lightingPromotionDiagnostics",
                        "lightingEmissiveDiagnostics"
                ),
                List.of(),
                List.of(
                        "lighting.mode.contract",
                        "lighting.mode.telemetry",
                        "lighting.budget.envelope",
                        "lighting.emissive.envelope"
                )
        );
    }

    private static RenderDescriptorRequirement descriptorFor(String pass, int set, int binding) {
        return new RenderDescriptorRequirement(
                pass,
                set,
                binding,
                RenderDescriptorType.UNIFORM_BUFFER,
                RenderBindingFrequency.PER_FRAME,
                false
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null) {
            return MODE_BASELINE_DIRECTIONAL_POINT_SPOT;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(mode.id()))
                .findFirst()
                .orElse(MODE_BASELINE_DIRECTIONAL_POINT_SPOT);
    }
}
