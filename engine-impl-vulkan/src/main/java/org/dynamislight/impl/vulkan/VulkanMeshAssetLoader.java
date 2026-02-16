package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.Locale;
import org.dynamislight.api.scene.MeshDesc;

final class VulkanMeshAssetLoader {
    private final Path assetRoot;
    private final VulkanGltfMeshParser gltfParser;

    VulkanMeshAssetLoader(Path assetRoot) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
        this.gltfParser = new VulkanGltfMeshParser(this.assetRoot);
    }

    VulkanGltfMeshParser.MeshGeometry loadMeshGeometry(MeshDesc mesh, int meshIndex) {
        if (mesh == null) {
            return triangleGeometry();
        }

        String meshPath = mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
        Path resolved = resolve(mesh.meshAssetPath());
        if (resolved != null && (meshPath.endsWith(".glb") || meshPath.endsWith(".gltf"))) {
            var parsed = gltfParser.parse(resolved);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        return fallbackByName(meshPath, meshIndex);
    }

    private Path resolve(String meshAssetPath) {
        if (meshAssetPath == null || meshAssetPath.isBlank()) {
            return null;
        }
        Path path = Path.of(meshAssetPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return assetRoot.resolve(path).normalize();
    }

    private VulkanGltfMeshParser.MeshGeometry fallbackByName(String sourceName, int meshIndex) {
        if (sourceName.contains("quad") || sourceName.contains("box") || sourceName.contains("plane")) {
            return quadGeometry();
        }
        return triangleGeometry();
    }

    private VulkanGltfMeshParser.MeshGeometry triangleGeometry() {
        return new VulkanGltfMeshParser.MeshGeometry(
                new float[]{
                        // pos                 // normal      // uv      // tangent
                        0.0f, -0.6f, 0.0f,     0f, 0f, 1f,    0.5f, 0.0f, 1f, 0f, 0f,
                        0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1.0f, 1.0f, 1f, 0f, 0f,
                        -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0.0f, 1.0f, 1f, 0f, 0f
                },
                new int[]{0, 1, 2}
        );
    }

    private VulkanGltfMeshParser.MeshGeometry quadGeometry() {
        return new VulkanGltfMeshParser.MeshGeometry(
                new float[]{
                        -0.6f, -0.6f, 0.0f,    0f, 0f, 1f,    0f, 0f,    1f, 0f, 0f,
                        0.6f, -0.6f, 0.0f,     0f, 0f, 1f,    1f, 0f,    1f, 0f, 0f,
                        0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1f, 1f,    1f, 0f, 0f,
                        -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0f, 1f,    1f, 0f, 0f
                },
                new int[]{0, 1, 2, 2, 3, 0}
        );
    }
}
