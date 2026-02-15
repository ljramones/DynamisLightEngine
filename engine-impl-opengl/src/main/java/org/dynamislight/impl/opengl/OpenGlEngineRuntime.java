package org.dynamislight.impl.opengl;

import java.util.Set;
import org.dynamislight.api.EngineCapabilities;
import org.dynamislight.api.QualityTier;
import org.dynamislight.impl.common.AbstractEngineRuntime;

public final class OpenGlEngineRuntime extends AbstractEngineRuntime {
    public OpenGlEngineRuntime() {
        super(
                "OpenGL",
                new EngineCapabilities(
                        Set.of("opengl"),
                        true,
                        false,
                        false,
                        false,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH)
                ),
                16.6,
                8.3
        );
    }
}
