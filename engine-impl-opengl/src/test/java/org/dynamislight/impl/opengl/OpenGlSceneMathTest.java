package org.dynamislight.impl.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class OpenGlSceneMathTest {
    @Test
    void modelMatrixIncludesTranslationComponents() {
        TransformDesc transform = new TransformDesc(
                "xform",
                new Vec3(2f, 3f, -4f),
                new Vec3(0f, 0f, 0f),
                new Vec3(1f, 1f, 1f)
        );

        float[] model = OpenGlEngineRuntime.modelMatrixOf(transform);

        assertEquals(2f, model[12], 0.0001f);
        assertEquals(3f, model[13], 0.0001f);
        assertEquals(-4f, model[14], 0.0001f);
    }

    @Test
    void cameraMatricesBuildPerspectiveAndView() {
        CameraDesc camera = new CameraDesc(
                "cam",
                new Vec3(0f, 0f, 5f),
                new Vec3(0f, 180f, 0f),
                60f,
                0.1f,
                100f
        );

        OpenGlEngineRuntime.CameraMatrices matrices = OpenGlEngineRuntime.cameraMatricesFor(camera, 16f / 9f);

        assertEquals(16, matrices.view().length);
        assertEquals(16, matrices.proj().length);
        assertTrue(Math.abs(matrices.proj()[0]) > 0.0001f);
        assertTrue(Math.abs(matrices.view()[15] - 1f) < 0.0001f);
    }
}
