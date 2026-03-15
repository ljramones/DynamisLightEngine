package org.dynamisengine.light.sample.save;

import org.dynamisengine.ecs.api.component.ComponentKey;

public final class DemoKeys {

    private DemoKeys() {
    }

    public static final ComponentKey<TranslationComponent> TRANSLATION =
            ComponentKey.of("demo.translation", TranslationComponent.class);

    public static final ComponentKey<BoundsSphereComponent> BOUNDS =
            ComponentKey.of("demo.boundsSphere", BoundsSphereComponent.class);

    public static final ComponentKey<RenderableComponent> RENDERABLE =
            ComponentKey.of("demo.renderable", RenderableComponent.class);
}
