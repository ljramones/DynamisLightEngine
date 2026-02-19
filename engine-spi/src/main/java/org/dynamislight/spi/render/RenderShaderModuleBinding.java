package org.dynamislight.spi.render;

/**
 * Named shader module binding symbol mapped to a descriptor requirement.
 *
 * @param symbolName GLSL symbol name referenced by module body (example: uShadowMap)
 * @param descriptor descriptor requirement for this symbol
 */
public record RenderShaderModuleBinding(
        String symbolName,
        RenderDescriptorRequirement descriptor
) {
    public RenderShaderModuleBinding {
        symbolName = symbolName == null ? "" : symbolName.trim();
        descriptor = descriptor == null
                ? new RenderDescriptorRequirement("", 0, 0, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, true)
                : descriptor;
    }
}
