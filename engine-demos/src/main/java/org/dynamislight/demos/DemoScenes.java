package org.dynamislight.demos;

import java.util.ArrayList;
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

    static SceneDescriptor materialBaselineScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.5f, 8.5f), new Vec3(0f, 1.0f, 0f), 55f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        int columns = 5;
        int rows = 2;
        float spacing = 1.8f;
        float startX = -((columns - 1) * spacing) * 0.5f;
        float startZ = -0.8f;
        for (int row = 0; row < rows; row++) {
            float metalness = row == 0 ? 0.0f : 1.0f;
            for (int col = 0; col < columns; col++) {
                float roughness = col / (float) (columns - 1);
                String id = "sample-" + row + "-" + col;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float x = startX + (col * spacing);
                float z = startZ - (row * spacing);
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, 0.0f, z),
                        new Vec3(0f, 18f + (col * 9f), 0f),
                        new Vec3(0.75f, 0.75f, 0.75f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.82f, 0.82f, 0.82f),
                        roughness,
                        metalness,
                        null,
                        null
                ));
            }
        }

        transforms.add(new TransformDesc(
                "floor",
                new Vec3(0f, -0.7f, -0.8f),
                new Vec3(0f, 0f, 0f),
                new Vec3(8.0f, 0.15f, 4.0f)
        ));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc(
                "mat-floor",
                new Vec3(0.18f, 0.20f, 0.24f),
                0.85f,
                0.0f,
                null,
                null
        ));

        ShadowDesc directionalShadow = new ShadowDesc(2048, 0.0012f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 10f, 0f),
                new Vec3(1f, 0.98f, 0.93f),
                1.30f,
                100f,
                true,
                directionalShadow
        );
        LightDesc fill = new LightDesc(
                "fill",
                new Vec3(-4f, 2.5f, 2.0f),
                new Vec3(0.55f, 0.65f, 1.0f),
                0.95f,
                18f,
                false,
                null
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.36f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.90f, 0.75f);

        return new SceneDescriptor(
                "demo-scene-material-baseline",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                List.of(sun, fill),
                environment,
                fog,
                List.of(),
                post
        );
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
