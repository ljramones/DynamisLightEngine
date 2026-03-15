package org.dynamisengine.light.sample.save;

import org.dynamisengine.light.sample.save.codecs.BoundsSphereCodec;
import org.dynamisengine.light.sample.save.codecs.RenderableCodec;
import org.dynamisengine.light.sample.save.codecs.TranslationCodec;
import org.dynamisengine.session.core.codec.DefaultCodecRegistry;

public final class DemoCodecRegistry {

    private DemoCodecRegistry() {
    }

    public static DefaultCodecRegistry build() {
        DefaultCodecRegistry registry = new DefaultCodecRegistry();
        registry.register(new TranslationCodec());
        registry.register(new BoundsSphereCodec());
        registry.register(new RenderableCodec());
        return registry;
    }
}
