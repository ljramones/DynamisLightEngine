package org.dynamislight.impl.opengl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.dynamislight.api.scene.MeshDesc;

final class OpenGlMeshAssetLoader {
    private static final byte[] GLB_MAGIC = new byte[]{'g', 'l', 'T', 'F'};

    private final Path assetRoot;

    OpenGlMeshAssetLoader(Path assetRoot) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
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

        if (resolved != null && Files.isRegularFile(resolved) && meshPath.endsWith(".glb") && isGlbFile(resolved)) {
            return mapByName(resolved.getFileName().toString().toLowerCase(Locale.ROOT), index);
        }
        return mapByName(meshPath, index);
    }

    private Path resolve(String meshAssetPath) {
        if (meshAssetPath == null || meshAssetPath.isBlank()) {
            return null;
        }
        Path path = Path.of(meshAssetPath);
        return path.isAbsolute() ? path : assetRoot.resolve(path).normalize();
    }

    private boolean isGlbFile(Path path) {
        try (var in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(4);
            if (header.length < 4) {
                return false;
            }
            return header[0] == GLB_MAGIC[0]
                    && header[1] == GLB_MAGIC[1]
                    && header[2] == GLB_MAGIC[2]
                    && header[3] == GLB_MAGIC[3];
        } catch (IOException ignored) {
            return false;
        }
    }

    private OpenGlContext.MeshGeometry mapByName(String sourceName, int index) {
        float tint = (index % 5) * 0.08f;
        if (sourceName.contains("quad") || sourceName.contains("box")) {
            return OpenGlContext.quadGeometry(0.25f + tint, 0.55f, 0.9f - tint);
        }
        return OpenGlContext.triangleGeometry(0.95f - tint, 0.35f + tint, 0.3f + tint);
    }
}
