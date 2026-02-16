package org.dynamislight.bridge.dynamisfx;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.bridge.dynamisfx.model.FxInputSnapshot;

public final class InputMapper {
    private static final Map<String, KeyCode> KEY_MATRIX = defaultKeyMatrix();

    public record MovementIntent(float x, float y, float z, boolean boost) {
    }

    public record CameraLookIntent(double yawDelta, double pitchDelta, double scrollDelta) {
    }

    /**
     * Maps host key tokens into canonical engine key codes.
     */
    public EngineInput mapInput(
            Set<String> hostKeysDown,
            double mouseX,
            double mouseY,
            double mouseDeltaX,
            double mouseDeltaY,
            boolean mouseLeft,
            boolean mouseRight,
            double scrollDelta
    ) {
        Set<KeyCode> mapped = hostKeysDown == null
                ? Set.of()
                : hostKeysDown.stream()
                        .map(InputMapper::normalizeKey)
                        .map(KEY_MATRIX::get)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

        return new EngineInput(
                mouseX,
                mouseY,
                mouseDeltaX,
                mouseDeltaY,
                mouseLeft,
                mouseRight,
                mapped,
                scrollDelta
        );
    }

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

    public MovementIntent toMovementIntent(EngineInput input) {
        Set<KeyCode> keys = input.keysDown();
        float x = axis(keys, KeyCode.A, KeyCode.D);
        float y = axis(keys, KeyCode.Q, KeyCode.E);
        float z = axis(keys, KeyCode.S, KeyCode.W);
        boolean boost = keys.contains(KeyCode.SHIFT);
        return new MovementIntent(x, y, z, boost);
    }

    public CameraLookIntent toCameraLookIntent(EngineInput input, double sensitivity) {
        return new CameraLookIntent(
                input.mouseDeltaX() * sensitivity,
                input.mouseDeltaY() * sensitivity,
                input.scrollDelta()
        );
    }

    public EngineInput mapInput(FxInputSnapshot snapshot) {
        return mapInput(
                snapshot.keysDown(),
                snapshot.mouseX(),
                snapshot.mouseY(),
                snapshot.mouseDeltaX(),
                snapshot.mouseDeltaY(),
                snapshot.mouseLeft(),
                snapshot.mouseRight(),
                snapshot.scrollDelta()
        );
    }

    private static float axis(Set<KeyCode> keys, KeyCode negative, KeyCode positive) {
        boolean neg = keys.contains(negative);
        boolean pos = keys.contains(positive);
        if (neg == pos) {
            return 0f;
        }
        return pos ? 1f : -1f;
    }

    private static Map<String, KeyCode> defaultKeyMatrix() {
        Map<String, KeyCode> matrix = new HashMap<>();
        bind(matrix, KeyCode.W, "W", "UP", "ARROW_UP");
        bind(matrix, KeyCode.A, "A", "LEFT", "ARROW_LEFT");
        bind(matrix, KeyCode.S, "S", "DOWN", "ARROW_DOWN");
        bind(matrix, KeyCode.D, "D", "RIGHT", "ARROW_RIGHT");
        bind(matrix, KeyCode.Q, "Q");
        bind(matrix, KeyCode.E, "E");
        bind(matrix, KeyCode.SPACE, "SPACE");
        bind(matrix, KeyCode.SHIFT, "SHIFT");
        bind(matrix, KeyCode.CTRL, "CTRL", "CONTROL");
        bind(matrix, KeyCode.ALT, "ALT", "OPTION");
        bind(matrix, KeyCode.ESC, "ESC", "ESCAPE");
        bind(matrix, KeyCode.ENTER, "ENTER", "RETURN");
        bind(matrix, KeyCode.TAB, "TAB");
        return Map.copyOf(matrix);
    }

    private static void bind(Map<String, KeyCode> matrix, KeyCode keyCode, String... aliases) {
        for (String alias : aliases) {
            matrix.put(normalizeKey(alias), keyCode);
        }
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }
}
