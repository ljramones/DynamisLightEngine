package org.dynamislight.impl.vulkan.shadow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
