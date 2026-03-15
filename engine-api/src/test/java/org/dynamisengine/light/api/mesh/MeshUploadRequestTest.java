package org.dynamisengine.light.api.mesh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeshUploadRequestTest {

    @Test
    void rejectsBlankMeshId() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeshUploadRequest(" ", MeshFormats.DMESH_V1, new byte[]{1}, 1L));
    }

    @Test
    void rejectsBlankFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeshUploadRequest("mesh/a", " ", new byte[]{1}, 1L));
    }

    @Test
    void rejectsNullPayload() {
        assertThrows(NullPointerException.class,
                () -> new MeshUploadRequest("mesh/a", MeshFormats.DMESH_V1, null, 1L));
    }

    @Test
    void rejectsEmptyPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeshUploadRequest("mesh/a", MeshFormats.DMESH_V1, new byte[0], 1L));
    }

    @Test
    void rejectsZeroHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeshUploadRequest("mesh/a", MeshFormats.DMESH_V1, new byte[]{1}, 0L));
    }

    @Test
    void defensivelyCopiesPayload() {
        byte[] payload = new byte[]{1, 2, 3};
        MeshUploadRequest request = new MeshUploadRequest("mesh/a", MeshFormats.DMESH_V1, payload, 5L);

        payload[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, request.payload());

        byte[] returned = request.payload();
        returned[1] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, request.payload());
    }

    @Test
    void defaultAllowDuplicateIsFalse() {
        MeshUploadRequest request = new MeshUploadRequest("mesh/a", MeshFormats.DMESH_V1, new byte[]{1}, 5L);
        assertFalse(request.allowDuplicate());
    }
}
