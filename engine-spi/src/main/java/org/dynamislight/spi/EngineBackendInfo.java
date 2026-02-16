package org.dynamislight.spi;

/**
 * Represents metadata information about an engine backend implementation.
 * This record is used to encapsulate descriptive and version-related details
 * of a specific engine backend provider.
 *
 * @param backendId   The unique identifier of the backend. Typically used to
 *                    distinguish between different implementations and for
 *                    registry or selection purposes.
 * @param displayName A human-readable name for the backend. This is usually
 *                    intended to be displayed in user interfaces or logs to
 *                    describe the backend provider in a user-friendly way.
 * @param version     The version of this backend implementation. This provides
 *                    additional context regarding the specific release or build
 *                    of the backend.
 * @param description A textual description providing further clarification or
 *                    details about the backend's functionality, use case, or
 *                    specific attributes.
 */
public record EngineBackendInfo(
        String backendId,
        String displayName,
        String version,
        String description
) {
}
