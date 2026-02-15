package org.dynamislight.spi;

public record EngineBackendInfo(
        String backendId,
        String displayName,
        String version,
        String description
) {
}
