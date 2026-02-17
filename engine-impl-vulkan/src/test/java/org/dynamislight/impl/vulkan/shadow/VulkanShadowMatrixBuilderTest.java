package org.dynamislight.impl.vulkan.shadow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class VulkanShadowMatrixBuilderTest {
    @Test
    void updateMatricesIsDeterministicForSameInputs() {
        VulkanShadowMatrixBuilder.ShadowInputs in = new VulkanShadowMatrixBuilder.ShadowInputs(
                0f,
                0f, -1f, 0f,
                1f, 2f, 3f,
                1f,
                true,
                20f,
                4,
                1024,
                true,
                1.0f,
                identity(),
                identity(),
                0,
                new float[32],
                new float[32],
                new float[32],
                -0.3f, -1f, 0.2f,
                6,
                4,
                6
        );
        float[][] firstMatrices = matrices();
        float[][] secondMatrices = matrices();
        float[] firstSplits = new float[3];
        float[] secondSplits = new float[3];

        VulkanShadowMatrixBuilder.updateMatrices(in, firstMatrices, firstSplits);
        VulkanShadowMatrixBuilder.updateMatrices(in, secondMatrices, secondSplits);

        for (int i = 0; i < firstMatrices.length; i++) {
            assertArrayEquals(firstMatrices[i], secondMatrices[i], 0.000001f);
        }
        assertArrayEquals(firstSplits, secondSplits, 0.000001f);
    }

    @Test
    void pointShadowModeSetsPointSplitDefaults() {
        VulkanShadowMatrixBuilder.ShadowInputs in = new VulkanShadowMatrixBuilder.ShadowInputs(
                0f,
                0f, -1f, 0f,
                0f, 1f, 0f,
                1f,
                true,
                25f,
                6,
                1024,
                true,
                1.0f,
                identity(),
                identity(),
                0,
                new float[32],
                new float[32],
                new float[32],
                0f, -1f, 0f,
                6,
                4,
                6
        );
        float[][] mats = matrices();
        float[] splits = new float[]{0f, 0f, 0f};

        VulkanShadowMatrixBuilder.updateMatrices(in, mats, splits);

        assertEquals(1f, splits[0], 0.000001f);
        assertEquals(1f, splits[1], 0.000001f);
        assertEquals(1f, splits[2], 0.000001f);
    }

    @Test
    void localPointShadowAssignmentBuildsCubemapFaceMatrices() {
        float[] localPosRange = new float[32];
        localPosRange[0] = 2f;
        localPosRange[1] = 3f;
        localPosRange[2] = 4f;
        localPosRange[3] = 18f;
        float[] localDirInner = new float[32];
        localDirInner[1] = -1f;
        localDirInner[3] = 1f;
        float[] localOuterTypeShadow = new float[32];
        localOuterTypeShadow[0] = 1f;
        localOuterTypeShadow[1] = 0f; // Point light.
        localOuterTypeShadow[2] = 1f; // Casts shadows.
        localOuterTypeShadow[3] = 1f; // Base shadow layer (1-based).
        VulkanShadowMatrixBuilder.ShadowInputs in = new VulkanShadowMatrixBuilder.ShadowInputs(
                0f,
                0f, -1f, 0f,
                0f, 1f, 0f,
                1f,
                false,
                25f,
                12,
                1024,
                true,
                1.0f,
                identity(),
                identity(),
                1,
                localPosRange,
                localDirInner,
                localOuterTypeShadow,
                -0.3f, -1f, 0.2f,
                12,
                4,
                6
        );
        float[][] mats = new float[12][];
        float[] splits = new float[]{0f, 0f, 0f};

        VulkanShadowMatrixBuilder.updateMatrices(in, mats, splits);

        assertNotEquals(identity()[12], mats[0][12], 0.000001f);
        assertNotEquals(identity()[13], mats[5][13], 0.000001f);
        assertArrayEquals(identity(), mats[6], 0.000001f);
        assertEquals(1f, splits[0], 0.000001f);
    }

    @Test
    void snapToTexelQuantizesExpectedStep() {
        assertEquals(1.0f, VulkanShadowMatrixBuilder.snapToTexel(1.24f, 0.25f), 0.000001f);
        assertEquals(-1.5f, VulkanShadowMatrixBuilder.snapToTexel(-1.26f, 0.25f), 0.000001f);
        assertEquals(2.0f, VulkanShadowMatrixBuilder.snapToTexel(2.0f, 0.25f), 0.000001f);
    }

    private static float[][] matrices() {
        return new float[][]{
                identity(),
                identity(),
                identity(),
                identity(),
                identity(),
                identity()
        };
    }

    private static float[] identity() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }
}
