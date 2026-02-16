package org.dynamislight.impl.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dynamislight.api.scene.MeshDesc;
import org.junit.jupiter.api.Test;

class OpenGlMeshAssetLoaderTest {
    @Test
    void emptySceneMeshesFallsBackToDefaultTriangle() {
        var loader = new OpenGlMeshAssetLoader(Path.of("."));

        List<OpenGlContext.MeshGeometry> meshes = loader.loadSceneMeshes(List.of());

        assertEquals(1, meshes.size());
        assertEquals(3, meshes.getFirst().vertexCount());
    }

    @Test
    void glbBoxMeshResolvesAgainstAssetRootAndMapsToQuad() throws Exception {
        Path root = Files.createTempDirectory("dle-opengl-loader");
        Files.createDirectories(root.resolve("meshes"));
        Files.write(root.resolve("meshes/box.glb"), new byte[]{'g', 'l', 'T', 'F', 2, 0, 0, 0});

        var loader = new OpenGlMeshAssetLoader(root);
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/box.glb");

        List<OpenGlContext.MeshGeometry> geometries = loader.loadSceneMeshes(List.of(mesh));

        assertEquals(1, geometries.size());
        assertEquals(6, geometries.getFirst().vertexCount());
    }

    @Test
    void nonGlbOrMissingFileStillFallsBackByAssetName() {
        var loader = new OpenGlMeshAssetLoader(Path.of("."));
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/quad.glb");

        List<OpenGlContext.MeshGeometry> geometries = loader.loadSceneMeshes(List.of(mesh));

        assertEquals(1, geometries.size());
        assertEquals(6, geometries.getFirst().vertexCount());
    }
}
