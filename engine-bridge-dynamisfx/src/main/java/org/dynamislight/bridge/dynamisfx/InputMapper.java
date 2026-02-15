package org.dynamislight.bridge.dynamisfx;

import java.util.Set;
import org.dynamislight.api.EngineInput;
import org.dynamislight.api.KeyCode;

public final class InputMapper {
    public EngineInput mapInput(
            double mouseX,
            double mouseY,
            double mouseDeltaX,
            double mouseDeltaY,
            boolean mouseLeft,
            boolean mouseRight,
            Set<KeyCode> keysDown,
            double scrollDelta
    ) {
        return new EngineInput(
                mouseX,
                mouseY,
                mouseDeltaX,
                mouseDeltaY,
                mouseLeft,
                mouseRight,
                keysDown,
                scrollDelta
        );
    }
}
