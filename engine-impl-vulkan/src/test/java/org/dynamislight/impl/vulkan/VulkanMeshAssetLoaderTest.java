package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.dynamislight.api.scene.MeshDesc;
import org.junit.jupiter.api.Test;

class VulkanMeshAssetLoaderTest {
    @Test
    void nonExistingMeshFallsBackByNameHeuristic() {
        var loader = new VulkanMeshAssetLoader(Path.of("."));
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/quad.glb");

        VulkanGltfMeshParser.MeshGeometry data = loader.loadMeshGeometry(mesh, 0);

        assertEquals(4, data.vertices().length / 11);
        assertEquals(6, data.indices().length);
    }

    @Test
    void fallbackGeometryCacheReturnsDefensiveCopies() {
        var loader = new VulkanMeshAssetLoader(Path.of("."));
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/quad.glb");

        VulkanGltfMeshParser.MeshGeometry first = loader.loadMeshGeometry(mesh, 0);
        first.vertices()[0] = 123.0f;
        first.indices()[0] = 77;

        VulkanGltfMeshParser.MeshGeometry second = loader.loadMeshGeometry(mesh, 0);

        assertNotSame(first.vertices(), second.vertices());
        assertNotSame(first.indices(), second.indices());
        assertEquals(-0.6f, second.vertices()[0]);
        assertEquals(0, second.indices()[0]);
        VulkanMeshAssetLoader.CacheProfile profile = loader.cacheProfile();
        assertEquals(1, profile.misses());
        assertEquals(1, profile.hits());
        assertEquals(1, profile.entries());
    }

    @Test
    void gltfPositionsAndIndicesAreParsed() throws Exception {
        Path root = Files.createTempDirectory("dle-vk-gltf");
        Files.createDirectories(root.resolve("meshes"));

        byte[] vertexBytes = new byte[9 * Float.BYTES];
        ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
        vb.putFloat(-0.5f).putFloat(-0.5f).putFloat(0.0f);
        vb.putFloat(0.5f).putFloat(-0.5f).putFloat(0.0f);
        vb.putFloat(0.0f).putFloat(0.5f).putFloat(0.0f);

        byte[] indexBytes = new byte[3 * Short.BYTES];
        ByteBuffer ib = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
        ib.putShort((short) 0).putShort((short) 1).putShort((short) 2);

        byte[] all = new byte[vertexBytes.length + indexBytes.length];
        System.arraycopy(vertexBytes, 0, all, 0, vertexBytes.length);
        System.arraycopy(indexBytes, 0, all, vertexBytes.length, indexBytes.length);

        String encoded = Base64.getEncoder().encodeToString(all);
        String gltf = """
                {
                  "asset": { "version": "2.0" },
                  "buffers": [{ "uri": "data:application/octet-stream;base64,%s", "byteLength": %d }],
                  "bufferViews": [
                    { "buffer": 0, "byteOffset": 0, "byteLength": %d },
                    { "buffer": 0, "byteOffset": %d, "byteLength": %d }
                  ],
                  "accessors": [
                    { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                    { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
                  ],
                  "meshes": [{ "primitives": [{ "attributes": { "POSITION": 0 }, "indices": 1, "mode": 4 }] }]
                }
                """.formatted(encoded, all.length, vertexBytes.length, vertexBytes.length, indexBytes.length);

        Files.writeString(root.resolve("meshes/triangle.gltf"), gltf);

        var loader = new VulkanMeshAssetLoader(root);
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/triangle.gltf");

        VulkanGltfMeshParser.MeshGeometry data = loader.loadMeshGeometry(mesh, 0);

        assertEquals(3, data.vertices().length / 11);
        assertEquals(3, data.indices().length);
    }

    @Test
    void gltfGeometryCacheReturnsDefensiveCopies() throws Exception {
        Path root = Files.createTempDirectory("dle-vk-gltf-cache");
        Files.createDirectories(root.resolve("meshes"));

        byte[] vertexBytes = new byte[9 * Float.BYTES];
        ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
        vb.putFloat(-0.5f).putFloat(-0.5f).putFloat(0.0f);
        vb.putFloat(0.5f).putFloat(-0.5f).putFloat(0.0f);
        vb.putFloat(0.0f).putFloat(0.5f).putFloat(0.0f);

        byte[] indexBytes = new byte[3 * Short.BYTES];
        ByteBuffer ib = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
        ib.putShort((short) 0).putShort((short) 1).putShort((short) 2);

        byte[] all = new byte[vertexBytes.length + indexBytes.length];
        System.arraycopy(vertexBytes, 0, all, 0, vertexBytes.length);
        System.arraycopy(indexBytes, 0, all, vertexBytes.length, indexBytes.length);

        String encoded = Base64.getEncoder().encodeToString(all);
        String gltf = """
                {
                  "asset": { "version": "2.0" },
                  "buffers": [{ "uri": "data:application/octet-stream;base64,%s", "byteLength": %d }],
                  "bufferViews": [
                    { "buffer": 0, "byteOffset": 0, "byteLength": %d },
                    { "buffer": 0, "byteOffset": %d, "byteLength": %d }
                  ],
                  "accessors": [
                    { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                    { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
                  ],
                  "meshes": [{ "primitives": [{ "attributes": { "POSITION": 0 }, "indices": 1, "mode": 4 }] }]
                }
                """.formatted(encoded, all.length, vertexBytes.length, vertexBytes.length, indexBytes.length);
        Files.writeString(root.resolve("meshes/triangle.gltf"), gltf);

        var loader = new VulkanMeshAssetLoader(root);
        var mesh = new MeshDesc("mesh", "xform", "mat", "meshes/triangle.gltf");

        VulkanGltfMeshParser.MeshGeometry first = loader.loadMeshGeometry(mesh, 0);
        float expectedVertex0 = first.vertices()[0];
        int expectedIndex0 = first.indices()[0];
        first.vertices()[0] = 222.0f;
        first.indices()[0] = 9;

        VulkanGltfMeshParser.MeshGeometry second = loader.loadMeshGeometry(mesh, 0);

        assertNotSame(first.vertices(), second.vertices());
        assertNotSame(first.indices(), second.indices());
        assertEquals(expectedVertex0, second.vertices()[0]);
        assertEquals(expectedIndex0, second.indices()[0]);
        VulkanMeshAssetLoader.CacheProfile profile = loader.cacheProfile();
        assertEquals(1, profile.misses());
        assertEquals(1, profile.hits());
        assertEquals(1, profile.entries());
    }
}
