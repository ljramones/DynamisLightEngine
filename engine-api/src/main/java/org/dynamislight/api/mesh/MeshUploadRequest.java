package org.dynamislight.api.mesh;

import java.util.Objects;

public final class MeshUploadRequest {
    private final String meshId;
    private final String format;
    private final byte[] payload;
    private final long contentHash64;
    private final boolean allowDuplicate;

    public MeshUploadRequest(String meshId, String format, byte[] payload, long contentHash64) {
        this(meshId, format, payload, contentHash64, false);
    }

    public MeshUploadRequest(String meshId, String format, byte[] payload, long contentHash64, boolean allowDuplicate) {
        this.meshId = requireNonBlank(meshId, "meshId");
        this.format = requireNonBlank(format, "format");

        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }

        if (contentHash64 == 0L) {
            throw new IllegalArgumentException("contentHash64 must not be 0");
        }

        this.payload = payload.clone();
        this.contentHash64 = contentHash64;
        this.allowDuplicate = allowDuplicate;
    }

    public String meshId() {
        return meshId;
    }

    public String format() {
        return format;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public long contentHash64() {
        return contentHash64;
    }

    public boolean allowDuplicate() {
        return allowDuplicate;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
