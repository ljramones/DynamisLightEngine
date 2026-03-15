package org.dynamisengine.light.api.mesh;

import java.util.Objects;

public record MeshUploadResult(int meshHandle, boolean reused, String meshId) {
    public MeshUploadResult {
        if (meshHandle <= 0) {
            throw new IllegalArgumentException("meshHandle must be > 0");
        }
        Objects.requireNonNull(meshId, "meshId");
        if (meshId.isBlank()) {
            throw new IllegalArgumentException("meshId must not be blank");
        }
    }
}
