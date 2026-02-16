package org.dynamislight.api.resource;

/**
 * Runtime-side resource status and ownership details.
 */
public record ResourceInfo(
        ResourceDescriptor descriptor,
        ResourceState state,
        int refCount,
        long lastLoadedEpochMs,
        String errorMessage,
        String resolvedPath,
        String lastChecksum
) {
}
