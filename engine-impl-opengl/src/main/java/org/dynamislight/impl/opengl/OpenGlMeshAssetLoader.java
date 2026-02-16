package org.dynamislight.impl.opengl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.dynamislight.api.scene.MeshDesc;

final class OpenGlMeshAssetLoader {
    private final OpenGlGltfMeshParser gltfParser;

    OpenGlMeshAssetLoader(Path assetRoot) {
        this.gltfParser = new OpenGlGltfMeshParser(assetRoot == null ? Path.of(".") : assetRoot);
    }

    List<OpenGlContext.MeshGeometry> loadSceneMeshes(List<MeshDesc> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            return List.of(OpenGlContext.defaultTriangleGeometry());
        }
        List<OpenGlContext.MeshGeometry> geometries = new ArrayList<>(meshes.size());
        for (int i = 0; i < meshes.size(); i++) {
            geometries.add(loadGeometry(meshes.get(i), i));
        }
        return geometries;
    }

    private OpenGlContext.MeshGeometry loadGeometry(MeshDesc mesh, int index) {
        if (mesh == null) {
            return OpenGlContext.defaultTriangleGeometry();
        }

        String meshPath = mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
        Path resolved = resolve(mesh.meshAssetPath());
        if (resolved != null && (meshPath.endsWith(".glb") || meshPath.endsWith(".gltf"))) {
            var parsed = gltfParser.parse(resolved);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        return mapByName(meshPath, index);
    }

    private Path resolve(String meshAssetPath) {
        if (meshAssetPath == null || meshAssetPath.isBlank()) {
            return null;
        }
        Path path = Path.of(meshAssetPath);
        return path;
    }

    private OpenGlContext.MeshGeometry mapByName(String sourceName, int index) {
        float tint = (index % 5) * 0.08f;
        if (sourceName.contains("quad") || sourceName.contains("box")) {
            return OpenGlContext.quadGeometry(0.25f + tint, 0.55f, 0.9f - tint);
        }
        return OpenGlContext.triangleGeometry(0.95f - tint, 0.35f + tint, 0.3f + tint);
    }
}
