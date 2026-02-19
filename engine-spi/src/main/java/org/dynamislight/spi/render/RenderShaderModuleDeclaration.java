package org.dynamislight.spi.render;

import java.util.List;

/**
 * Concrete shader module declaration for Phase C composition.
 *
 * A module provides a hook implementation body plus explicitly named binding symbols used by that body.
 * This record is metadata-only and backend-agnostic.
 *
 * @param moduleId stable module identifier
 * @param providerFeatureId feature providing this module
 * @param targetPassId host pass this module targets
 * @param injectionPoint host injection point
 * @param stage shader stage
 * @param hookFunction hook function symbol implemented by this module
 * @param functionSignature full GLSL signature text for hook function
 * @param glslBody GLSL function body/implementation text
 * @param bindings named binding symbols referenced by GLSL body
 * @param uniformRequirements required uniform fields consumed by module
 * @param pushConstantRequirements required push-constant ranges consumed by module
 * @param ordering declaration order inside same pass/injection point (-1 means unspecified)
 * @param conditional true when module activation is mode/runtime-dependent
 */
public record RenderShaderModuleDeclaration(
        String moduleId,
        String providerFeatureId,
        String targetPassId,
        RenderShaderInjectionPoint injectionPoint,
        RenderShaderStage stage,
        String hookFunction,
        String functionSignature,
        String glslBody,
        List<RenderShaderModuleBinding> bindings,
        List<RenderUniformRequirement> uniformRequirements,
        List<RenderPushConstantRequirement> pushConstantRequirements,
        int ordering,
        boolean conditional
) {
    public RenderShaderModuleDeclaration {
        moduleId = moduleId == null ? "" : moduleId.trim();
        providerFeatureId = providerFeatureId == null ? "" : providerFeatureId.trim();
        targetPassId = targetPassId == null ? "" : targetPassId.trim();
        injectionPoint = injectionPoint == null ? RenderShaderInjectionPoint.AUXILIARY : injectionPoint;
        stage = stage == null ? RenderShaderStage.FRAGMENT : stage;
        hookFunction = hookFunction == null ? "" : hookFunction.trim();
        functionSignature = functionSignature == null ? "" : functionSignature.trim();
        glslBody = glslBody == null ? "" : glslBody.trim();
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        uniformRequirements = uniformRequirements == null ? List.of() : List.copyOf(uniformRequirements);
        pushConstantRequirements = pushConstantRequirements == null ? List.of() : List.copyOf(pushConstantRequirements);
        ordering = Math.max(-1, ordering);
    }
}
