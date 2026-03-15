package org.dynamisengine.light.impl.vulkan.model;

import java.nio.file.Path;

public record VulkanSceneMeshData(
        String meshId,
        float[] vertices,
        int[] indices,
        float[] modelMatrix,
        float[] color,
        float metallic,
        float roughness,
        float reactiveStrength,
        boolean alphaTested,
        boolean foliage,
        int reflectionOverrideMode,
        float reactiveBoost,
        float taaHistoryClamp,
        float emissiveReactiveBoost,
        float reactivePreset,
        Path albedoTexturePath,
        Path normalTexturePath,
        Path metallicRoughnessTexturePath,
        Path occlusionTexturePath,
        VulkanSkinnedMeshUniforms skinnedUniforms,
        boolean skinned,
        int jointCount,
        float[] morphTargetDeltas,
        int morphTargetCount,
        org.dynamisengine.gpu.vulkan.buffer.VulkanMorphTargetBuffer morphTargets
) {
    private static final int VERTEX_STRIDE_FLOATS = 11;
    private static final int SKINNED_VERTEX_STRIDE_FLOATS = 16;

    public VulkanSceneMeshData {
        if (meshId == null || meshId.isBlank()) {
            throw new IllegalArgumentException("meshId is required");
        }
        int stride = skinned ? SKINNED_VERTEX_STRIDE_FLOATS : VERTEX_STRIDE_FLOATS;
        if (vertices == null || vertices.length < stride * 3 || vertices.length % stride != 0) {
            throw new IllegalArgumentException("vertices must be interleaved as static(11f) or skinned(16f)");
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
        if (reflectionOverrideMode < 0 || reflectionOverrideMode > 3) {
            throw new IllegalArgumentException("reflectionOverrideMode must be in [0,3]");
        }
        if (jointCount < 0) {
            throw new IllegalArgumentException("jointCount must be >= 0");
        }
        if (morphTargetCount < 0) {
            throw new IllegalArgumentException("morphTargetCount must be >= 0");
        }
        if (morphTargetCount > 0) {
            int vertexCount = vertices.length / stride;
            int expectedMorphFloats = vertexCount * morphTargetCount * 6;
            if (morphTargetDeltas == null || morphTargetDeltas.length != expectedMorphFloats) {
                throw new IllegalArgumentException(
                        "morphTargetDeltas must contain " + expectedMorphFloats + " floats when morphTargetCount > 0"
                );
            }
        }
    }

    public static VulkanSceneMeshData defaultTriangle() {
        return triangle(new float[]{1f, 1f, 1f, 1f}, 0);
    }

    public VulkanSceneMeshData(
            String meshId,
            float[] vertices,
            int[] indices,
            float[] modelMatrix,
            float[] color,
            float metallic,
            float roughness,
            float reactiveStrength,
            boolean alphaTested,
            boolean foliage,
            int reflectionOverrideMode,
            float reactiveBoost,
            float taaHistoryClamp,
            float emissiveReactiveBoost,
            float reactivePreset,
            Path albedoTexturePath,
            Path normalTexturePath,
            Path metallicRoughnessTexturePath,
            Path occlusionTexturePath
    ) {
        this(
                meshId,
                vertices,
                indices,
                modelMatrix,
                color,
                metallic,
                roughness,
                reactiveStrength,
                alphaTested,
                foliage,
                reflectionOverrideMode,
                reactiveBoost,
                taaHistoryClamp,
                emissiveReactiveBoost,
                reactivePreset,
                albedoTexturePath,
                normalTexturePath,
                metallicRoughnessTexturePath,
                occlusionTexturePath,
                null,
                false,
                0,
                null,
                0,
                null
        );
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
                0f,
                false,
                false,
                0,
                1.0f,
                1.0f,
                1.0f,
                0f,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                null,
                0,
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
                0f,
                false,
                false,
                0,
                1.0f,
                1.0f,
                1.0f,
                0f,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                null,
                0,
                null
        );
    }
}
