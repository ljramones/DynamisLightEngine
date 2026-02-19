package org.dynamislight.spi.render;

/**
 * Descriptor requirement for a capability contribution.
 *
 * @param targetPassId host pass where requirement applies
 * @param setIndex descriptor set index
 * @param bindingIndex descriptor binding index
 * @param type descriptor type
 * @param frequency update/bind frequency
 * @param conditional true when requirement depends on active mode
 */
public record RenderDescriptorRequirement(
        String targetPassId,
        int setIndex,
        int bindingIndex,
        RenderDescriptorType type,
        RenderBindingFrequency frequency,
        boolean conditional
) {
    public RenderDescriptorRequirement {
        targetPassId = targetPassId == null ? "" : targetPassId.trim();
        setIndex = Math.max(0, setIndex);
        bindingIndex = Math.max(0, bindingIndex);
        type = type == null ? RenderDescriptorType.UNIFORM_BUFFER : type;
        frequency = frequency == null ? RenderBindingFrequency.PER_FRAME : frequency;
    }
}
