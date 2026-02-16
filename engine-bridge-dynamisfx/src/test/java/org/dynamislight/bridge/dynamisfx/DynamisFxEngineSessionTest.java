package org.dynamislight.bridge.dynamisfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.bridge.dynamisfx.model.FxInputSnapshot;
import org.junit.jupiter.api.Test;

class DynamisFxEngineSessionTest {
    @Test
    void tickBeforeStartThrowsInvalidState() {
        DynamisFxEngineSession session = new DynamisFxEngineSession();

        EngineException ex = assertThrows(EngineException.class,
                () -> session.tick(1.0 / 60.0, new FxInputSnapshot(0, 0, 0, 0, false, false, java.util.Set.of(), 0)));

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void resourceCallsBeforeStartThrowInvalidState() {
        DynamisFxEngineSession session = new DynamisFxEngineSession();

        EngineException exResources = assertThrows(EngineException.class, session::resources);
        EngineException exReload = assertThrows(EngineException.class, () -> session.reloadResource("mesh:meshes/triangle.glb"));

        assertEquals(EngineErrorCode.INVALID_STATE, exResources.code());
        assertEquals(EngineErrorCode.INVALID_STATE, exReload.code());
    }
}
