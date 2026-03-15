package org.dynamisengine.light.sample.save;

import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector3f;

public final class DemoViewProj {

    private DemoViewProj() {
    }

    public static Matrix4f build(float aspect) {
        Matrix4f projection = new Matrix4f().identity().perspective((float) Math.toRadians(60.0), aspect, 0.1f, 200.0f);
        Matrix4f view = new Matrix4f().identity().lookAt(
                new Vector3f(0f, 0f, 10f),
                new Vector3f(0f, 0f, 0f),
                new Vector3f(0f, 1f, 0f)
        );
        return projection.mul(view, new Matrix4f());
    }
}
