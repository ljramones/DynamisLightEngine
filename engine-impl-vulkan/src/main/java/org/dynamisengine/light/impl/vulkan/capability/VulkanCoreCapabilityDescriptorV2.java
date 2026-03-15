package org.dynamisengine.light.impl.vulkan.capability;

import java.util.List;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.spi.render.RenderDescriptorRequirement;
import org.dynamisengine.light.spi.render.RenderDescriptorType;
import org.dynamisengine.light.spi.render.RenderBindingFrequency;
import org.dynamisengine.light.spi.render.RenderFeatureCapabilityV2;
import org.dynamisengine.light.spi.render.RenderFeatureMode;
import org.dynamisengine.light.spi.render.RenderPassDeclaration;
import org.dynamisengine.light.spi.render.RenderPushConstantRequirement;
import org.dynamisengine.light.spi.render.RenderResourceDeclaration;
import org.dynamisengine.light.spi.render.RenderSchedulerDeclaration;
import org.dynamisengine.light.spi.render.RenderShaderContribution;
import org.dynamisengine.light.spi.render.RenderTelemetryDeclaration;
import org.dynamisengine.light.spi.render.RenderUniformRequirement;

/**
 * Core Vulkan descriptor baseline for Phase C composition.
 *
 * Provides mandatory descriptor lanes used by runtime writer/allocation paths.
 */
public final class VulkanCoreCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_BASELINE = new RenderFeatureMode("baseline");

    private static final List<RenderDescriptorRequirement> DESCRIPTORS = List.of(
            // Main set=0
            new RenderDescriptorRequirement("main_geometry", 0, 0, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("main_geometry", 0, 1, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_DRAW, false),
            new RenderDescriptorRequirement("main_geometry", 0, 2, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, false),
            // Main set=1 material + shadow/IBL/probe lanes used by texture descriptor writer.
            new RenderDescriptorRequirement("main_geometry", 1, 0, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_MATERIAL, false),
            new RenderDescriptorRequirement("main_geometry", 1, 1, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_MATERIAL, false),
            new RenderDescriptorRequirement("main_geometry", 1, 2, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_MATERIAL, false),
            new RenderDescriptorRequirement("main_geometry", 1, 3, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_MATERIAL, false),
            new RenderDescriptorRequirement("main_geometry", 1, 4, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("main_geometry", 1, 5, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("main_geometry", 1, 6, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("main_geometry", 1, 7, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("main_geometry", 1, 8, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, true),
            new RenderDescriptorRequirement("main_geometry", 1, 9, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_MATERIAL, false),
            // Post set=0 baseline lanes.
            new RenderDescriptorRequirement("post_composite", 0, 0, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
            new RenderDescriptorRequirement("post_composite", 0, 1, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("post_composite", 0, 2, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
            new RenderDescriptorRequirement("post_composite", 0, 3, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false),
            new RenderDescriptorRequirement("post_composite", 0, 4, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false)
    );

    @Override
    public String featureId() {
        return "vulkan.core";
    }

    @Override
    public List<RenderFeatureMode> supportedModes() {
        return List.of(MODE_BASELINE);
    }

    @Override
    public RenderFeatureMode activeMode() {
        return MODE_BASELINE;
    }

    @Override
    public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        return DESCRIPTORS;
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(List.of(), List.of(), List.of(), List.of());
    }
}
