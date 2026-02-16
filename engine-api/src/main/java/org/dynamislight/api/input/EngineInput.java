package org.dynamislight.api.input;

import java.util.Set;

/**
 * Represents the input state for the engine, including mouse movements, mouse button states,
 * keyboard key states, and scroll wheel interactions.
 *
 * This record is used to encapsulate the state of user input that drives interactions within
 * the application. It is immutable, with defensive copying of the key set to ensure safety.
 *
 * Fields:
 * - mouseX: The x-coordinate of the mouse position, typically in screen coordinates.
 * - mouseY: The y-coordinate of the mouse position, typically in screen coordinates.
 * - mouseDeltaX: The change in the mouse's x-coordinate since the last input frame.
 * - mouseDeltaY: The change in the mouse's y-coordinate since the last input frame.
 * - mouseLeft: A flag indicating whether the left mouse button is currently pressed.
 * - mouseRight: A flag indicating whether the right mouse button is currently pressed.
 * - keysDown: A set of keyboard keys currently pressed. Each key is represented by its {@code KeyCode}.
 * - scrollDelta: The amount of scrolling input from the mouse's scroll wheel, typically measured
 *   as a delta value since the previous input frame.
 */
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
