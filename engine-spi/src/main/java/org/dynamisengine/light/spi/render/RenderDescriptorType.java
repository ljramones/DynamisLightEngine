package org.dynamisengine.light.spi.render;

/**
 * Descriptor binding type required by capability shader code.
 */
public enum RenderDescriptorType {
    UNIFORM_BUFFER,
    STORAGE_BUFFER,
    COMBINED_IMAGE_SAMPLER,
    STORAGE_IMAGE,
    SAMPLER
}
