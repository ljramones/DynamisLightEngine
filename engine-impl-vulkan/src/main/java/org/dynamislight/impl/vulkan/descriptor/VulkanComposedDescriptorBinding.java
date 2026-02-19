package org.dynamislight.impl.vulkan.descriptor;

import java.util.List;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderDescriptorType;

/**
 * Composed descriptor binding entry derived from capability requirements.
 */
public record VulkanComposedDescriptorBinding(
        int setIndex,
        int bindingIndex,
        RenderDescriptorType type,
        RenderBindingFrequency frequency,
        boolean conditional,
        List<String> providers
) {
    public VulkanComposedDescriptorBinding {
        setIndex = Math.max(0, setIndex);
        bindingIndex = Math.max(0, bindingIndex);
        type = type == null ? RenderDescriptorType.UNIFORM_BUFFER : type;
        frequency = frequency == null ? RenderBindingFrequency.PER_FRAME : frequency;
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}

