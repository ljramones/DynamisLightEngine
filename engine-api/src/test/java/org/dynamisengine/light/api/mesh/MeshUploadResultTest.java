package org.dynamisengine.light.api.mesh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MeshUploadResultTest {

    @Test
    void rejectsNonPositiveMeshHandle() {
        assertThrows(IllegalArgumentException.class, () -> new MeshUploadResult(0, false, "mesh/a"));
    }

    @Test
    void rejectsBlankMeshId() {
        assertThrows(IllegalArgumentException.class, () -> new MeshUploadResult(1, false, " "));
    }
}
