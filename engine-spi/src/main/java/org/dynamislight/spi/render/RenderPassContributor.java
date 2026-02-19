package org.dynamislight.spi.render;

import java.util.List;
import org.dynamislight.api.config.QualityTier;

/**
 * Declares graph-visible pass contributions for a capability/mode.
 */
public interface RenderPassContributor {
    List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode);
}
