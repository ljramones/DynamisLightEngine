package org.dynamislight.api;

public record MeshDesc(
        String id,
        String transformId,
        String materialId,
        String meshAssetPath
) {
}
