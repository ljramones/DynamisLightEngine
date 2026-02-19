package org.dynamislight.spi.render;

import java.util.List;

/**
 * v2 shader contribution declaration.
 *
 * @param targetPassId target host pass
 * @param injectionPoint host injection point
 * @param stage shader stage
 * @param implementationKey implementation variant key
 * @param descriptorRequirements descriptor dependencies
 * @param uniformRequirements uniform dependencies
 * @param pushConstantRequirements push-constant dependencies
 * @param perMaterialDispatch true when contribution performs per-material mode dispatch
 * @param runtimeAdaptive true when runtime adaptive policy modulates contribution
 * @param ordering declaration order within the same target pass/injection point (-1 means unspecified)
 * @param conditional true when contribution depends on active mode/runtime state
 */
public record RenderShaderContribution(
        String targetPassId,
        RenderShaderInjectionPoint injectionPoint,
        RenderShaderStage stage,
        String implementationKey,
        List<RenderDescriptorRequirement> descriptorRequirements,
        List<RenderUniformRequirement> uniformRequirements,
        List<RenderPushConstantRequirement> pushConstantRequirements,
        boolean perMaterialDispatch,
        boolean runtimeAdaptive,
        int ordering,
        boolean conditional
) {
    public RenderShaderContribution {
        targetPassId = targetPassId == null ? "" : targetPassId.trim();
        injectionPoint = injectionPoint == null ? RenderShaderInjectionPoint.AUXILIARY : injectionPoint;
        stage = stage == null ? RenderShaderStage.FRAGMENT : stage;
        implementationKey = implementationKey == null ? "" : implementationKey.trim();
        descriptorRequirements = descriptorRequirements == null ? List.of() : List.copyOf(descriptorRequirements);
        uniformRequirements = uniformRequirements == null ? List.of() : List.copyOf(uniformRequirements);
        pushConstantRequirements = pushConstantRequirements == null ? List.of() : List.copyOf(pushConstantRequirements);
        ordering = Math.max(-1, ordering);
    }
}
