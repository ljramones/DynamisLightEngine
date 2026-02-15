package org.dynamislight.bridge.dynamisfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.dynamislight.api.EngineInput;
import org.dynamislight.api.KeyCode;
import org.junit.jupiter.api.Test;

class InputMapperTest {
    @Test
    void mapsKeyAliasesToCanonicalMovementKeys() {
        InputMapper mapper = new InputMapper();

        EngineInput input = mapper.mapInput(
                Set.of("w", "arrow_left", "space", "shift", "unknown"),
                100,
                120,
                4,
                -2,
                true,
                false,
                0.5
        );

        assertTrue(input.keysDown().contains(KeyCode.W));
        assertTrue(input.keysDown().contains(KeyCode.A));
        assertTrue(input.keysDown().contains(KeyCode.SPACE));
        assertTrue(input.keysDown().contains(KeyCode.SHIFT));
        assertFalse(input.keysDown().contains(KeyCode.D));
        assertEquals(4.0, input.mouseDeltaX());
        assertEquals(-2.0, input.mouseDeltaY());
        assertEquals(0.5, input.scrollDelta());
    }

    @Test
    void movementIntentCombinesAndCancelsAxes() {
        InputMapper mapper = new InputMapper();
        EngineInput input = mapper.mapInput(
                Set.of("W", "S", "D", "Q", "SHIFT"),
                0,
                0,
                0,
                0,
                false,
                false,
                0
        );

        InputMapper.MovementIntent movement = mapper.toMovementIntent(input);

        assertEquals(1.0f, movement.x());
        assertEquals(-1.0f, movement.y());
        assertEquals(0.0f, movement.z());
        assertTrue(movement.boost());
    }

    @Test
    void cameraLookIntentUsesMouseDeltasAndSensitivity() {
        InputMapper mapper = new InputMapper();
        EngineInput input = mapper.mapInput(
                0,
                0,
                10,
                -4,
                false,
                false,
                Set.of(),
                1.25
        );

        InputMapper.CameraLookIntent look = mapper.toCameraLookIntent(input, 0.2);

        assertEquals(2.0, look.yawDelta());
        assertEquals(-0.8, look.pitchDelta());
        assertEquals(1.25, look.scrollDelta());
    }
}
