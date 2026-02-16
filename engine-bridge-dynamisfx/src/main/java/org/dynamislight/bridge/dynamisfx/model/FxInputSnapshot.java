package org.dynamislight.bridge.dynamisfx.model;

import java.util.Set;

/**
 * DynamisFX-facing input snapshot independent of engine DTO types.
 */
public record FxInputSnapshot(
        double mouseX,
        double mouseY,
        double mouseDeltaX,
        double mouseDeltaY,
        boolean mouseLeft,
        boolean mouseRight,
        Set<String> keysDown,
        double scrollDelta
) {
}
