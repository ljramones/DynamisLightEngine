package org.dynamislight.api;

/**
 * Opaque resource identifier used across host/runtime boundary.
 */
public record ResourceId(String value) {
    public ResourceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ResourceId value is required");
        }
    }
}
