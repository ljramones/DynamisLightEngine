package org.dynamislight.spi.render;

import java.util.List;

/**
 * Immutable v2 capability contract assembled for a specific mode/tier snapshot.
 *
 * @param featureId capability ID
 * @param mode active mode
 * @param passes pass declarations
 * @param shaderContributions shader declarations
 * @param descriptorRequirements descriptor requirements
 * @param uniformRequirements uniform requirements
 * @param pushConstantRequirements push-constant requirements
 * @param ownedResources owned-resource declarations
 * @param schedulers scheduler declarations
 * @param telemetry telemetry declaration
 */
public record RenderCapabilityContractV2(
        String featureId,
        RenderFeatureMode mode,
        List<RenderPassDeclaration> passes,
        List<RenderShaderContribution> shaderContributions,
        List<RenderDescriptorRequirement> descriptorRequirements,
        List<RenderUniformRequirement> uniformRequirements,
        List<RenderPushConstantRequirement> pushConstantRequirements,
        List<RenderResourceDeclaration> ownedResources,
        List<RenderSchedulerDeclaration> schedulers,
        RenderTelemetryDeclaration telemetry
) {
    public RenderCapabilityContractV2 {
        featureId = featureId == null ? "" : featureId.trim();
        mode = mode == null ? new RenderFeatureMode("") : mode;
        passes = passes == null ? List.of() : List.copyOf(passes);
        shaderContributions = shaderContributions == null ? List.of() : List.copyOf(shaderContributions);
        descriptorRequirements = descriptorRequirements == null ? List.of() : List.copyOf(descriptorRequirements);
        uniformRequirements = uniformRequirements == null ? List.of() : List.copyOf(uniformRequirements);
        pushConstantRequirements = pushConstantRequirements == null ? List.of() : List.copyOf(pushConstantRequirements);
        ownedResources = ownedResources == null ? List.of() : List.copyOf(ownedResources);
        schedulers = schedulers == null ? List.of() : List.copyOf(schedulers);
        telemetry = telemetry == null ? new RenderTelemetryDeclaration(List.of(), List.of(), List.of(), List.of()) : telemetry;
    }
}
