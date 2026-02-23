package org.dynamislight.impl.vulkan.runtime.math;

import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.vulkan.math.VulkanMath;
import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector3f;

public final class VulkanEngineRuntimeCameraMath {
    private VulkanEngineRuntimeCameraMath() {
    }

    public static CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
        CameraDesc effective = camera == null
                ? new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f)
                : camera;

        Vec3 pos = effective.position() == null ? new Vec3(0f, 0f, 5f) : effective.position();
        Vec3 rot = effective.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : effective.rotationEulerDeg();
        float yaw = radians(rot.y());
        float pitch = radians(rot.x());

        float fx = (float) (Math.cos(pitch) * Math.sin(yaw));
        float fy = (float) Math.sin(pitch);
        float fz = (float) (-Math.cos(pitch) * Math.cos(yaw));

        float[] view = VulkanMath.lookAt(
                pos.x(), pos.y(), pos.z(),
                pos.x() + fx, pos.y() + fy, pos.z() + fz,
                0f, 1f, 0f
        );

        float near = effective.nearPlane() > 0f ? effective.nearPlane() : 0.1f;
        float far = effective.farPlane() > near ? effective.farPlane() : 100f;
        float fov = effective.fovDegrees() > 1f ? effective.fovDegrees() : 60f;
        float aspect = aspectRatio > 0.01f ? aspectRatio : (16f / 9f);
        float[] proj = VulkanMath.perspective(radians(fov), aspect, near, far);
        return new CameraMatrices(view, proj);
    }

    public static CameraDesc selectActiveCamera(SceneDescriptor scene) {
        if (scene == null || scene.cameras() == null || scene.cameras().isEmpty()) {
            return new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f);
        }
        if (scene.activeCameraId() != null && !scene.activeCameraId().isBlank()) {
            for (CameraDesc camera : scene.cameras()) {
                if (scene.activeCameraId().equals(camera.id())) {
                    return camera;
                }
            }
        }
        return scene.cameras().getFirst();
    }

    public static float safeAspect(int width, int height) {
        if (height <= 0) {
            return 16f / 9f;
        }
        return Math.max(0.1f, (float) width / (float) height);
    }

    public static float[] modelMatrixOf(TransformDesc transform, int meshIndex) {
        if (transform == null) {
            float[] model = VulkanMath.identityMatrix();
            model[12] = (meshIndex - 1) * 0.35f;
            return model;
        }
        Vec3 pos = transform.position() == null ? new Vec3(0f, 0f, 0f) : transform.position();
        Vec3 rot = transform.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : transform.rotationEulerDeg();
        Vec3 scl = transform.scale() == null ? new Vec3(1f, 1f, 1f) : transform.scale();

        Matrix4f translation = new Matrix4f().translation(pos.x(), pos.y(), pos.z());
        Matrix4f rotation = new Matrix4f().identity()
                .rotateZ(radians(rot.z()))
                .rotateY(radians(rot.y()))
                .rotateX(radians(rot.x()));
        Matrix4f scale = new Matrix4f().scaling(scl.x(), scl.y(), scl.z());
        return translation.mul(rotation, new Matrix4f()).mul(scale).get(new float[16]);
    }

    public static float[] normalize3(float[] v) {
        if (v == null || v.length != 3) {
            return new float[]{0f, -1f, 0f};
        }
        float len = Vector3f.length(v[0], v[1], v[2]);
        if (len < 1.0e-6f) {
            return new float[]{0f, -1f, 0f};
        }
        Vector3f normalized = new Vector3f(v[0], v[1], v[2]).normalize();
        return new float[]{normalized.x(), normalized.y(), normalized.z()};
    }

    public static float cosFromDegrees(float degrees) {
        float clamped = Math.max(0f, Math.min(89.9f, degrees));
        return (float) Math.cos(Math.toRadians(clamped));
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
