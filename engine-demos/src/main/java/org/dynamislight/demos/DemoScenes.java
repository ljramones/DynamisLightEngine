package org.dynamislight.demos;

import java.util.List;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;

final class DemoScenes {
    private DemoScenes() {
    }

    static SceneDescriptor helloTriangleScene() {
        return sceneWithAa("taa", true, 0.75f, 1.0f);
    }

    static SceneDescriptor sceneWithAa(String aaMode, boolean postEnabled, float blend, float renderScale) {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 1.5f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 1000f);
        TransformDesc triangleTransform = new TransformDesc("triangle", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 0f), new Vec3(1f, 1f, 1f));
        TransformDesc boxTransform = new TransformDesc("box", new Vec3(1.4f, 0f, -0.2f), new Vec3(0f, 35f, 0f), new Vec3(0.8f, 0.8f, 0.8f));

        MeshDesc triangle = new MeshDesc("mesh-triangle", "triangle", "mat-triangle", "meshes/triangle.glb");
        MeshDesc box = new MeshDesc("mesh-box", "box", "mat-box", "meshes/box.glb");

        MaterialDesc matTriangle = new MaterialDesc(
                "mat-triangle",
                new Vec3(0.85f, 0.35f, 0.30f),
                0.05f,
                0.65f,
                null,
                null
        );
        MaterialDesc matBox = new MaterialDesc(
                "mat-box",
                new Vec3(0.20f, 0.55f, 0.85f),
                0.35f,
                0.30f,
                null,
                null
        );

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0012f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 10f, 0f),
                new Vec3(1f, 0.96f, 0.90f),
                1.25f,
                100f,
                true,
                directionalShadow
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.28f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(
                postEnabled,
                true,
                1.05f,
                2.2f,
                true,
                0.90f,
                0.75f
        );

        return new SceneDescriptor(
                "demo-scene-" + aaMode,
                List.of(camera),
                "main-cam",
                List.of(triangleTransform, boxTransform),
                List.of(triangle, box),
                List.of(matTriangle, matBox),
                List.of(sun),
                environment,
                fog,
                List.of(),
                post
        );
    }
}
