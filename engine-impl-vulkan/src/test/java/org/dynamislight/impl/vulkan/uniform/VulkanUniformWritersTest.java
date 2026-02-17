package org.dynamislight.impl.vulkan.uniform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class VulkanUniformWritersTest {
    @Test
    void writeGlobalSceneUniformWritesLocalLightArrayBlock() {
        int maxLocalLights = 8;
        float[] posRange = new float[maxLocalLights * 4];
        float[] colorIntensity = new float[maxLocalLights * 4];
        float[] dirInner = new float[maxLocalLights * 4];
        float[] outerTypeShadow = new float[maxLocalLights * 4];
        posRange[0] = 4f;
        posRange[1] = 5f;
        posRange[2] = 6f;
        posRange[3] = 12f;
        colorIntensity[0] = 0.2f;
        colorIntensity[1] = 0.4f;
        colorIntensity[2] = 0.6f;
        colorIntensity[3] = 3.0f;
        dirInner[3] = 0.8f;
        outerTypeShadow[0] = 0.6f;
        outerTypeShadow[1] = 1.0f;
        outerTypeShadow[2] = 1.0f;

        VulkanUniformWriters.GlobalSceneUniformInput in = new VulkanUniformWriters.GlobalSceneUniformInput(
                2544,
                identity(),
                identity(),
                0f, -1f, 0f,
                1.0f,
                1f, 1f, 1f,
                1.0f,
                0f, 0f, 0f,
                15f,
                1f, 1f, 1f,
                1.0f,
                0f, -1f, 0f,
                1.0f,
                1f, 1f, 0f, false,
                2,
                maxLocalLights,
                posRange,
                colorIntensity,
                dirInner,
                outerTypeShadow,
                0,
                0,
                false,
                0.65f,
                80.0f,
                2,
                false,
                1f, 1f,
                1.0f, 0.42f,
                false, 0.4f, 0.001f, 1.0f, 1.0f, 1, 1, 1024, new float[]{0.25f, 0.5f, 0.75f},
                false, 0f, 0f, 0f, 0f, 0,
                false, 0f, 1280, 720, 0f, 0f, 0f,
                false, 0f, 0f, 0f,
                false, true, 1f, 2.2f,
                false, 1f, 0.8f,
                false, 0f, 1f, 0.02f, 1f,
                false, 0f,
                identity(),
                new float[][]{
                        identity(), identity(), identity(), identity(), identity(), identity(),
                        identity(), identity(), identity(), identity(), identity(), identity()
                }
        );

        ByteBuffer target = ByteBuffer.allocateDirect(2544).order(ByteOrder.nativeOrder());
        VulkanUniformWriters.writeGlobalSceneUniform(target, in);
        FloatBuffer fb = target.order(ByteOrder.nativeOrder()).asFloatBuffer();
        int metaIndex = findSequenceStart(fb, new float[]{2f, 0f, 0f, 0f, 4f, 5f, 6f, 12f});
        assertTrue(metaIndex >= 0);
    }

    @Test
    void writeGlobalSceneUniformPacksShadowRtBitsIntoLocalMeta() {
        int maxLocalLights = 8;
        float[] zeros = new float[maxLocalLights * 4];
        VulkanUniformWriters.GlobalSceneUniformInput in = new VulkanUniformWriters.GlobalSceneUniformInput(
                2544,
                identity(),
                identity(),
                0f, -1f, 0f,
                1.0f,
                1f, 1f, 1f,
                1.0f,
                0f, 0f, 0f,
                15f,
                1f, 1f, 1f,
                1.0f,
                0f, -1f, 0f,
                1.0f,
                1f, 1f, 0f, false,
                0,
                maxLocalLights,
                zeros,
                zeros,
                zeros,
                zeros,
                3,
                2,
                true,
                0.72f,
                120.0f,
                6,
                false,
                1f, 1f,
                1.0f, 0.42f,
                false, 0.4f, 0.001f, 1.0f, 1.0f, 1, 1, 1024, new float[]{0.25f, 0.5f, 0.75f},
                false, 0f, 0f, 0f, 0f, 0,
                false, 0f, 1280, 720, 0f, 0f, 0f,
                false, 0f, 0f, 0f,
                false, true, 1f, 2.2f,
                false, 1f, 0.8f,
                false, 0f, 1f, 0.02f, 1f,
                false, 0f,
                identity(),
                new float[][]{
                        identity(), identity(), identity(), identity(), identity(), identity(),
                        identity(), identity(), identity(), identity(), identity(), identity()
                }
        );
        ByteBuffer target = ByteBuffer.allocateDirect(2544).order(ByteOrder.nativeOrder());
        VulkanUniformWriters.writeGlobalSceneUniform(target, in);
        FloatBuffer fb = target.order(ByteOrder.nativeOrder()).asFloatBuffer();
        int metaIndex = findSequenceStart(fb, new float[]{0f, 0f, 219f, 0f});
        assertTrue(metaIndex >= 0); // filter=3, rtMode=2, rtActive=1, rtSamples=6 => 0b11011011
    }

    private static float[] identity() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static int findSequenceStart(FloatBuffer fb, float[] sequence) {
        int limit = fb.limit();
        for (int i = 0; i <= limit - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (Math.abs(fb.get(i + j) - sequence[j]) > 0.0001f) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
}
