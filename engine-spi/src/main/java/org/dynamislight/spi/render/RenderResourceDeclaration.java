package org.dynamislight.spi.render;

import java.util.List;

/**
 * v2 owned-resource declaration.
 *
 * @param resourceName symbolic resource name
 * @param type resource type
 * @param lifecycle lifecycle category
 * @param conditional resource exists only for specific modes/runtime paths
 * @param recreateTriggers declarative invalidation triggers
 */
public record RenderResourceDeclaration(
        String resourceName,
        RenderResourceType type,
        RenderResourceLifecycle lifecycle,
        boolean conditional,
        List<String> recreateTriggers
) {
    public RenderResourceDeclaration {
        resourceName = resourceName == null ? "" : resourceName.trim();
        type = type == null ? RenderResourceType.ATTACHMENT : type;
        lifecycle = lifecycle == null ? RenderResourceLifecycle.TRANSIENT : lifecycle;
        recreateTriggers = recreateTriggers == null ? List.of() : List.copyOf(recreateTriggers);
    }
}
