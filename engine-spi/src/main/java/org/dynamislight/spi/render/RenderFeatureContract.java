package org.dynamislight.spi.render;

import java.util.List;

/**
 * Immutable top-level capability metadata declaration.
 *
 * @param featureId stable capability identifier
 * @param version capability schema/version label
 * @param passContributions pass-level contribution declarations
 * @param shaderHooks shader-hook contribution declarations
 * @param resourceRequirements resource and binding declarations
 * @param dependencies declared dependencies on producer capabilities/resources
 */
public record RenderFeatureContract(
        String featureId,
        String version,
        List<RenderPassContribution> passContributions,
        List<RenderShaderHookContribution> shaderHooks,
        List<RenderResourceRequirement> resourceRequirements,
        List<RenderCapabilityDependency> dependencies
) {
    public RenderFeatureContract {
        featureId = featureId == null ? "" : featureId.trim();
        version = version == null || version.isBlank() ? "v1" : version.trim();
        passContributions = passContributions == null ? List.of() : List.copyOf(passContributions);
        shaderHooks = shaderHooks == null ? List.of() : List.copyOf(shaderHooks);
        resourceRequirements = resourceRequirements == null ? List.of() : List.copyOf(resourceRequirements);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
