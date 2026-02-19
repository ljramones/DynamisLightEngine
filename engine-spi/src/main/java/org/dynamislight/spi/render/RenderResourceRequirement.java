package org.dynamislight.spi.render;

/**
 * Resource requirement declaration for capability composition.
 *
 * @param name stable symbolic resource name
 * @param type resource type
 * @param frequency binding/update frequency
 * @param descriptorSetHint descriptor-set hint (non-negative)
 * @param bindingHint descriptor-binding hint (non-negative)
 * @param required whether resource is required for capability activation
 */
public record RenderResourceRequirement(
        String name,
        RenderResourceType type,
        RenderBindingFrequency frequency,
        int descriptorSetHint,
        int bindingHint,
        boolean required
) {
    public RenderResourceRequirement {
        name = name == null ? "" : name.trim();
        type = type == null ? RenderResourceType.UNIFORM_BUFFER : type;
        frequency = frequency == null ? RenderBindingFrequency.PER_FRAME : frequency;
        descriptorSetHint = Math.max(0, descriptorSetHint);
        bindingHint = Math.max(0, bindingHint);
    }
}
