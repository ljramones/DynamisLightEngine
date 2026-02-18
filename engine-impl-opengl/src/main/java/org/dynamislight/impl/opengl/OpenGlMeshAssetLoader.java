package org.dynamislight.impl.opengl;

import java.nio.ByteBuffer;
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
            geometries.add(loadMeshGeometry(meshes.get(i), i));
        }
        return geometries;
    }

    OpenGlContext.MeshGeometry loadMeshGeometry(MeshDesc mesh, int index) {
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

    /**
     * Full glTF scene load — returns all primitives with geometry, material data, and embedded textures.
     * The caller is responsible for uploading textures to GL and assembling SceneMesh instances.
     */
    record LoadedGltfScene(
            List<LoadedPrimitive> primitives,
            List<OpenGlGltfMeshParser.GltfMaterial> materials,
            List<ByteBuffer> imageBuffers
    ) {
    }

    record LoadedPrimitive(
            OpenGlContext.MeshGeometry geometry,
            int materialIndex,
            String meshName,
            int meshIndex,
            int primitiveIndex
    ) {
    }

    LoadedGltfScene loadGltfScene(Path glbPath) {
        if (glbPath == null) {
            return null;
        }
        var sceneOpt = gltfParser.parseScene(glbPath);
        if (sceneOpt.isEmpty()) {
            return null;
        }
        var scene = sceneOpt.get();
        List<LoadedPrimitive> primitives = new ArrayList<>(scene.primitives().size());
        for (var p : scene.primitives()) {
            primitives.add(new LoadedPrimitive(
                    p.geometry(), p.materialIndex(), p.meshName(), p.meshIndex(), p.primitiveIndex()));
        }
        return new LoadedGltfScene(primitives, scene.materials(), scene.imageBuffers());
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
