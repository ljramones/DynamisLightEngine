package org.dynamislight.impl.vulkan.math;

import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector3f;

public final class VulkanMath {
    private VulkanMath() {
    }

    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) <= 1.0e-6f;
    }

    public static float[] normalize3(float x, float y, float z) {
        float len = Vector3f.length(x, y, z);
        if (len < 1.0e-6f) {
            return new float[]{0f, -1f, 0f};
        }
        Vector3f normalized = new Vector3f(x, y, z).normalize();
        return new float[]{normalized.x(), normalized.y(), normalized.z()};
    }

    public static float[] identityMatrix() {
        return new Matrix4f().identity().get(new float[16]);
    }

    public static float projectionNear(float[] proj) {
        float a = proj[10];
        float b = proj[14];
        return b / (a - 1f);
    }

    public static float projectionFar(float[] proj) {
        float a = proj[10];
        float b = proj[14];
        return b / (a + 1f);
    }

    public static float viewDistanceToNdcDepth(float[] proj, float distance) {
        float clipZ = proj[10] * (-distance) + proj[14];
        float clipW = proj[11] * (-distance) + proj[15];
        if (Math.abs(clipW) < 0.000001f) {
            return 1f;
        }
        return clipZ / clipW;
    }

    public static float[] transformPoint(float[] m, float x, float y, float z) {
        Vector3f out = arrayToMatrix4f(m).transformProject(x, y, z, new Vector3f());
        return new float[]{out.x(), out.y(), out.z()};
    }

    public static float[] unproject(float[] invViewProj, float ndcX, float ndcY, float ndcZ) {
        Vector3f out = arrayToMatrix4f(invViewProj).transformProject(ndcX, ndcY, ndcZ, new Vector3f());
        return new float[]{out.x(), out.y(), out.z()};
    }

    public static float[] invert(float[] m) {
        Matrix4f matrix = arrayToMatrix4f(m);
        if (Math.abs(matrix.determinant()) < 0.0000001f) {
            return null;
        }
        return matrix.invert(new Matrix4f()).get(new float[16]);
    }

    public static float[] lookAt(
            float eyeX, float eyeY, float eyeZ,
            float targetX, float targetY, float targetZ,
            float upX, float upY, float upZ
    ) {
        return new Matrix4f()
                .lookAt(eyeX, eyeY, eyeZ, targetX, targetY, targetZ, upX, upY, upZ)
                .get(new float[16]);
    }

    public static float[] ortho(float left, float right, float bottom, float top, float near, float far) {
        return new Matrix4f().ortho(left, right, bottom, top, near, far).get(new float[16]);
    }

    public static float[] perspective(float fovRad, float aspect, float near, float far) {
        return new Matrix4f().perspective(fovRad, aspect, near, far).get(new float[16]);
    }

    public static float[] mul(float[] a, float[] b) {
        Matrix4f ma = arrayToMatrix4f(a);
        Matrix4f mb = arrayToMatrix4f(b);
        return ma.mul(mb, new Matrix4f()).get(new float[16]);
    }

    private static Matrix4f arrayToMatrix4f(float[] matrix) {
        if (matrix == null || matrix.length != 16) {
            throw new IllegalArgumentException("Expected 16-float matrix");
        }
        return new Matrix4f().set(matrix);
    }
}
