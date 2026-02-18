package org.dynamislight.demos;

import java.util.List;
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SpotLightDesc;
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
                null,
                null,
                null,
                0.15f,
                false,
                false
        );
        MaterialDesc matBox = new MaterialDesc(
                "mat-box",
                new Vec3(0.20f, 0.55f, 0.85f),
                0.35f,
                0.30f,
                null,
                null,
                null,
                null,
                0.15f,
                false,
                false
        );

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0012f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 10f, 0f),
                new Vec3(1f, 0.96f, 0.90f),
                1.25f,
                100f,
                true,
                directionalShadow,
                LightType.DIRECTIONAL
        );

        SpotLightDesc spot = new SpotLightDesc(
                "hero-spot",
                new Vec3(2.2f, 3.0f, 1.5f),
                new Vec3(-0.6f, -0.8f, -0.3f),
                new Vec3(1.0f, 0.80f, 0.55f),
                2.0f,
                18f,
                18f,
                32f,
                true,
                new ShadowDesc(1024, 0.0010f, 5, 1)
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.28f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        AntiAliasingDesc aa = new AntiAliasingDesc(
                aaMode,
                true,
                blend,
                0.85f,
                true,
                0.12f,
                renderScale,
                0
        );
        PostProcessDesc post = new PostProcessDesc(
                postEnabled,
                true,
                1.05f,
                2.2f,
                true,
                0.90f,
                0.75f,
                true,
                0.55f,
                1.10f,
                0.02f,
                1.20f,
                true,
                0.70f,
                true,
                blend,
                true,
                aa
        );

        return new SceneDescriptor(
                "demo-scene-" + aaMode,
                List.of(camera),
                "main-cam",
                List.of(triangleTransform, boxTransform),
                List.of(triangle, box),
                List.of(matTriangle, matBox),
                List.of(sun, new LightDesc(spot)),
                environment,
                fog,
                List.of(),
                post
        );
    }
}
