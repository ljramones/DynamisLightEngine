package org.dynamisengine.light.spi.render;

import java.util.List;
import org.dynamisengine.light.api.config.QualityTier;

/**
 * Declares graph-visible pass contributions for a capability/mode.
 */
public interface RenderPassContributor {
    List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode);
}
