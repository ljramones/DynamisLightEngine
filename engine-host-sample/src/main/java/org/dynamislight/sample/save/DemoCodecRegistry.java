package org.dynamislight.sample.save;

import org.dynamislight.sample.save.codecs.BoundsSphereCodec;
import org.dynamislight.sample.save.codecs.RenderableCodec;
import org.dynamislight.sample.save.codecs.TranslationCodec;
import org.dynamissession.core.codec.DefaultCodecRegistry;

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
