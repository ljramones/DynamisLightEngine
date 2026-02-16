package org.dynamislight.impl.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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

    @Test
    void validGltfDataOverridesFilenameHeuristic() throws Exception {
        Path root = Files.createTempDirectory("dle-opengl-gltf");
        Files.createDirectories(root.resolve("meshes"));
        byte[] vertexBytes = new byte[9 * Float.BYTES];
        ByteBuffer bb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
        bb.putFloat(-0.5f).putFloat(-0.5f).putFloat(0.0f);
        bb.putFloat(0.5f).putFloat(-0.5f).putFloat(0.0f);
        bb.putFloat(0.0f).putFloat(0.5f).putFloat(0.0f);
        String encoded = Base64.getEncoder().encodeToString(vertexBytes);
        String gltf = """
                {
                  "asset": { "version": "2.0" },
                  "buffers": [{ "uri": "data:application/octet-stream;base64,%s", "byteLength": %d }],
                  "bufferViews": [{ "buffer": 0, "byteOffset": 0, "byteLength": %d }],
                  "accessors": [{ "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" }],
                  "meshes": [{ "primitives": [{ "attributes": { "POSITION": 0 }, "mode": 4 }] }]
                }
                """.formatted(encoded, vertexBytes.length, vertexBytes.length);
        Files.writeString(root.resolve("meshes/box.gltf"), gltf);

        var loader = new OpenGlMeshAssetLoader(root);
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/box.gltf");

        List<OpenGlContext.MeshGeometry> geometries = loader.loadSceneMeshes(List.of(mesh));

        assertEquals(1, geometries.size());
        assertEquals(3, geometries.getFirst().vertexCount());
    }
}
