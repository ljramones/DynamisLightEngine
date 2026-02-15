package org.dynamislight.api;

/**
 * MeshDesc API type.
 */
public record MeshDesc(
        String id,
        String transformId,
        String materialId,
        String meshAssetPath
) {
}
