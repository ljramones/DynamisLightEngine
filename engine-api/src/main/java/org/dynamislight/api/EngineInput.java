package org.dynamislight.api;

import java.util.Set;

public record EngineInput(
        double mouseX,
        double mouseY,
        double mouseDeltaX,
        double mouseDeltaY,
        boolean mouseLeft,
        boolean mouseRight,
        Set<KeyCode> keysDown,
        double scrollDelta
) {
    public EngineInput {
        keysDown = keysDown == null ? Set.of() : Set.copyOf(keysDown);
    }
}
