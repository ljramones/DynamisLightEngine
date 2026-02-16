package org.dynamislight.impl.vulkan.model;

import java.nio.file.Path;

public record VulkanSceneMeshData(
        String meshId,
        float[] vertices,
        int[] indices,
        float[] modelMatrix,
        float[] color,
        float metallic,
        float roughness,
        Path albedoTexturePath,
        Path normalTexturePath,
        Path metallicRoughnessTexturePath,
        Path occlusionTexturePath
) {
    private static final int VERTEX_STRIDE_FLOATS = 11;

    public VulkanSceneMeshData {
        if (meshId == null || meshId.isBlank()) {
            throw new IllegalArgumentException("meshId is required");
        }
        if (vertices == null || vertices.length < VERTEX_STRIDE_FLOATS * 3 || vertices.length % VERTEX_STRIDE_FLOATS != 0) {
            throw new IllegalArgumentException("vertices must be interleaved as pos/normal/uv/tangent");
        }
        if (indices == null || indices.length < 3 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("indices must be non-empty triangles");
        }
        if (modelMatrix == null || modelMatrix.length != 16) {
            throw new IllegalArgumentException("modelMatrix must be 16 floats");
        }
        if (color == null || color.length != 4) {
            throw new IllegalArgumentException("color must be rgba");
        }
    }

    public static VulkanSceneMeshData defaultTriangle() {
        return triangle(new float[]{1f, 1f, 1f, 1f}, 0);
    }

    public static VulkanSceneMeshData triangle(float[] color, int meshIndex) {
        float offsetX = (meshIndex % 2 == 0 ? -0.25f : 0.25f) * Math.min(meshIndex, 3);
        return new VulkanSceneMeshData(
                "default-triangle-" + meshIndex,
                new float[]{
                        0.0f, -0.6f, 0.0f,     0f, 0f, 1f,    0.5f, 0.0f,    1f, 0f, 0f,
                        0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1.0f, 1.0f,    1f, 0f, 0f,
                        -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0.0f, 1.0f,    1f, 0f, 0f
                },
                new int[]{0, 1, 2},
                new float[]{
                        1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        offsetX, 0f, 0f, 1f
                },
                color,
                0.0f,
                0.6f,
                null,
                null,
                null,
                null
        );
    }

    public static VulkanSceneMeshData quad(float[] color, int meshIndex) {
        float offsetX = (meshIndex - 1) * 0.35f;
        return new VulkanSceneMeshData(
                "default-quad-" + meshIndex,
                new float[]{
                        -0.6f, -0.6f, 0.0f,    0f, 0f, 1f,    0f, 0f,    1f, 0f, 0f,
                        0.6f, -0.6f, 0.0f,     0f, 0f, 1f,    1f, 0f,    1f, 0f, 0f,
                        0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1f, 1f,    1f, 0f, 0f,
                        -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0f, 1f,    1f, 0f, 0f
                },
                new int[]{0, 1, 2, 2, 3, 0},
                new float[]{
                        1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        offsetX, 0f, 0f, 1f
                },
                color,
                0.0f,
                0.6f,
                null,
                null,
                null,
                null
        );
    }
}
