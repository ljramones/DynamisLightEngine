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

    VulkanContext.SceneMeshData loadMeshData(MeshDesc mesh, float[] color, int meshIndex) {
        if (mesh == null) {
            return VulkanContext.SceneMeshData.defaultTriangle();
        }

        String meshPath = mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
        Path resolved = resolve(mesh.meshAssetPath());
        if (resolved != null && (meshPath.endsWith(".glb") || meshPath.endsWith(".gltf"))) {
            var parsed = gltfParser.parse(resolved);
            if (parsed.isPresent()) {
                VulkanGltfMeshParser.MeshGeometry geometry = parsed.get();
                float offsetX = (meshIndex - 1) * 0.35f;
                return new VulkanContext.SceneMeshData(
                        geometry.positions(),
                        geometry.indices(),
                        color,
                        offsetX
                );
            }
        }
        return fallbackByName(meshPath, color, meshIndex);
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

    private VulkanContext.SceneMeshData fallbackByName(String sourceName, float[] color, int meshIndex) {
        if (sourceName.contains("quad") || sourceName.contains("box") || sourceName.contains("plane")) {
            return VulkanContext.SceneMeshData.quad(color, meshIndex);
        }
        return VulkanContext.SceneMeshData.triangle(color, meshIndex);
    }
}
