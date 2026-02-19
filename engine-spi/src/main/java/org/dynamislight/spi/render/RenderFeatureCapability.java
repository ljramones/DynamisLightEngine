package org.dynamislight.spi.render;

/**
 * Declares a feature capability contract that can contribute render passes,
 * shader hooks, and resource requirements.
 *
 * This interface is metadata-only in v1; runtime integration is a later phase.
 */
public interface RenderFeatureCapability {
    /**
     * Returns the immutable contract describing this capability.
     *
     * @return capability contract metadata
     */
    RenderFeatureContract contract();

    /**
     * Returns capability ID convenience accessor.
     *
     * @return stable capability ID
     */
    default String featureId() {
        return contract().featureId();
    }
}
