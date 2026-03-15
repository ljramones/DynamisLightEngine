package org.dynamisengine.light.spi.render;

import java.util.List;

/**
 * Declares shader contributions for a capability/mode.
 */
public interface RenderShaderContributor {
    List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode);
}
