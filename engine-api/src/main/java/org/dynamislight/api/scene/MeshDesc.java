package org.dynamislight.api.scene;

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
