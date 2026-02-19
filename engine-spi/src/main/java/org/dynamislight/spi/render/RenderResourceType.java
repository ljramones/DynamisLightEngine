package org.dynamislight.spi.render;

/**
 * Resource type declared by a capability requirement.
 */
public enum RenderResourceType {
    UNIFORM_BUFFER,
    STORAGE_BUFFER,
    SAMPLED_IMAGE,
    STORAGE_IMAGE,
    ATTACHMENT,
    SAMPLER,
    PUSH_CONSTANTS
}
