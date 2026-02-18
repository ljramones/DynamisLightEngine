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
import org.dynamislight.api.scene.ReflectionAdvancedDesc;
import org.dynamislight.api.scene.ReflectionDesc;
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

    static SceneDescriptor lightsLocalArrayScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.6f, 9.5f), new Vec3(0f, 1.0f, -1.2f), 58f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.8f, -1.2f), new Vec3(0f, 0f, 0f), new Vec3(10.0f, 0.20f, 5.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.14f, 0.16f, 0.19f), 0.80f, 0.0f, null, null));

        int columns = 4;
        int rows = 2;
        float spacingX = 2.4f;
        float spacingZ = 2.0f;
        float startX = -((columns - 1) * spacingX) * 0.5f;
        float startZ = -0.1f;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                String id = "object-" + row + "-" + col;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float x = startX + (col * spacingX);
                float z = startZ - (row * spacingZ);
                transforms.add(new TransformDesc(transformId, new Vec3(x, 0.2f, z), new Vec3(0f, col * 19f, 0f), new Vec3(0.7f, 0.7f, 0.7f)));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float hueBias = (row * columns + col) / 7.0f;
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.25f + 0.35f * hueBias, 0.40f + 0.30f * (1.0f - hueBias), 0.55f + 0.25f * hueBias),
                        0.35f + (0.08f * row),
                        0.20f + (0.25f * (col / 3.0f)),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(2048, 0.0012f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 11f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.10f,
                100f,
                true,
                directionalShadow
        );

        List<LightDesc> lights = new ArrayList<>();
        lights.add(sun);
        lights.add(new LightDesc("local-red", new Vec3(-3.2f, 1.8f, 0.2f), new Vec3(1.0f, 0.35f, 0.30f), 1.05f, 10.0f, false, null));
        lights.add(new LightDesc("local-green", new Vec3(-1.0f, 1.7f, -2.2f), new Vec3(0.35f, 1.0f, 0.45f), 1.00f, 9.5f, false, null));
        lights.add(new LightDesc("local-blue", new Vec3(1.1f, 1.9f, -0.4f), new Vec3(0.35f, 0.55f, 1.0f), 1.00f, 9.5f, false, null));
        lights.add(new LightDesc("local-amber", new Vec3(3.0f, 2.0f, -2.1f), new Vec3(1.0f, 0.72f, 0.30f), 1.05f, 10.0f, false, null));
        lights.add(new LightDesc("local-cyan", new Vec3(-2.6f, 1.6f, -3.3f), new Vec3(0.30f, 0.95f, 1.0f), 0.95f, 8.5f, false, null));
        lights.add(new LightDesc("local-magenta", new Vec3(0.0f, 1.6f, -4.0f), new Vec3(1.0f, 0.35f, 0.95f), 0.95f, 8.5f, false, null));
        lights.add(new LightDesc("local-lime", new Vec3(2.8f, 1.7f, -3.3f), new Vec3(0.65f, 1.0f, 0.25f), 0.95f, 8.5f, false, null));

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.06f, 0.08f, 0.10f), 0.30f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.88f, 0.72f);

        return new SceneDescriptor(
                "demo-scene-lights-local-array",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                lights,
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor shadowCascadeDebugScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 3.0f, 13.5f), new Vec3(0f, 1.0f, -8.0f), 55f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.9f, -12f), new Vec3(0f, 0f, 0f), new Vec3(8.0f, 0.20f, 18.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.15f, 0.17f, 0.20f), 0.85f, 0.0f, null, null));

        int lanes = 3;
        int depthSteps = 8;
        float laneSpacing = 2.0f;
        float depthSpacing = 3.4f;
        for (int lane = 0; lane < lanes; lane++) {
            float x = (lane - 1) * laneSpacing;
            for (int step = 0; step < depthSteps; step++) {
                String id = "cascade-" + lane + "-" + step;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float z = -1.5f - (step * depthSpacing);
                float y = 0.25f + (0.10f * (step % 2));
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, y, z),
                        new Vec3(0f, (lane * 20f) + (step * 9f), 0f),
                        new Vec3(0.55f, 0.55f + (0.04f * lane), 0.55f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float depthBias = step / (float) (depthSteps - 1);
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.28f + 0.25f * depthBias, 0.40f + 0.20f * (1.0f - depthBias), 0.72f - 0.20f * depthBias),
                        0.32f + (0.08f * lane),
                        0.18f + (0.10f * lane),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(2048, 0.0011f, 5, 4);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 14f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.20f,
                140f,
                true,
                directionalShadow
        );
        LightDesc fill = new LightDesc(
                "fill",
                new Vec3(-5.0f, 3.0f, -4.0f),
                new Vec3(0.48f, 0.62f, 0.95f),
                0.72f,
                22f,
                false,
                null
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.07f, 0.09f, 0.12f), 0.33f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.90f, 0.75f);

        return new SceneDescriptor(
                "demo-scene-shadow-cascade-debug",
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

    static SceneDescriptor shadowCascadeBaselineScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.5f, 10.5f), new Vec3(0f, 0.9f, -6.2f), 55f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.85f, -8.0f), new Vec3(0f, 0f, 0f), new Vec3(6.5f, 0.20f, 12.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.16f, 0.18f, 0.22f), 0.82f, 0.0f, null, null));

        int lanes = 3;
        int depthSteps = 5;
        float laneSpacing = 1.9f;
        float depthSpacing = 2.6f;
        for (int lane = 0; lane < lanes; lane++) {
            float x = (lane - 1) * laneSpacing;
            for (int step = 0; step < depthSteps; step++) {
                String id = "baseline-" + lane + "-" + step;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float z = -1.4f - (step * depthSpacing);
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, 0.20f, z),
                        new Vec3(0f, lane * 16f + step * 7f, 0f),
                        new Vec3(0.58f, 0.58f, 0.58f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float depthFactor = step / (float) (depthSteps - 1);
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.30f + 0.22f * depthFactor, 0.44f + 0.12f * (1.0f - depthFactor), 0.66f - 0.14f * depthFactor),
                        0.34f + (0.06f * lane),
                        0.18f + (0.08f * lane),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(2048, 0.0010f, 4, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 12f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.18f,
                120f,
                true,
                directionalShadow
        );
        LightDesc fill = new LightDesc(
                "fill",
                new Vec3(-4.5f, 2.8f, -3.5f),
                new Vec3(0.48f, 0.62f, 0.95f),
                0.62f,
                18f,
                false,
                null
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.07f, 0.09f, 0.12f), 0.31f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.90f, 0.75f);

        return new SceneDescriptor(
                "demo-scene-shadow-cascade-baseline",
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

    static SceneDescriptor shadowLocalAtlasScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 3.2f, 11.5f), new Vec3(0f, 1.1f, -5.0f), 58f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.9f, -5.8f), new Vec3(0f, 0f, 0f), new Vec3(8.5f, 0.2f, 9.5f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.13f, 0.15f, 0.18f), 0.84f, 0.0f, null, null));

        int columns = 4;
        int rows = 3;
        float spacingX = 2.2f;
        float spacingZ = 2.0f;
        float startX = -((columns - 1) * spacingX) * 0.5f;
        float startZ = -0.6f;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                String id = "atlas-" + row + "-" + col;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float x = startX + (col * spacingX);
                float z = startZ - (row * spacingZ);
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, 0.2f, z),
                        new Vec3(0f, row * 18f + col * 12f, 0f),
                        new Vec3(0.62f, 0.62f, 0.62f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float tint = (row * columns + col) / 11.0f;
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.24f + 0.36f * tint, 0.35f + 0.22f * (1.0f - tint), 0.62f - 0.18f * tint),
                        0.34f + (0.06f * row),
                        0.15f + (0.14f * (col / 3.0f)),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 12f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.05f,
                120f,
                true,
                directionalShadow
        );

        List<LightDesc> lights = new ArrayList<>();
        lights.add(sun);
        ShadowDesc localShadow = new ShadowDesc(1024, 0.0014f, 5, 1);
        lights.add(new LightDesc("atlas-local-1", new Vec3(-3.6f, 2.0f, -1.0f), new Vec3(1.0f, 0.38f, 0.30f), 1.05f, 8.5f, true, localShadow));
        lights.add(new LightDesc("atlas-local-2", new Vec3(-1.2f, 2.1f, -3.0f), new Vec3(0.35f, 1.0f, 0.45f), 1.00f, 8.0f, true, localShadow));
        lights.add(new LightDesc("atlas-local-3", new Vec3(1.1f, 2.1f, -1.6f), new Vec3(0.38f, 0.58f, 1.0f), 1.00f, 8.0f, true, localShadow));
        lights.add(new LightDesc("atlas-local-4", new Vec3(3.5f, 2.1f, -3.1f), new Vec3(1.0f, 0.72f, 0.30f), 1.02f, 8.5f, true, localShadow));
        lights.add(new LightDesc("atlas-local-5", new Vec3(-2.7f, 1.8f, -5.0f), new Vec3(0.30f, 0.95f, 1.0f), 0.95f, 7.5f, true, localShadow));
        lights.add(new LightDesc("atlas-local-6", new Vec3(0.0f, 1.9f, -5.6f), new Vec3(1.0f, 0.35f, 0.95f), 0.95f, 7.5f, true, localShadow));
        lights.add(new LightDesc("atlas-local-7", new Vec3(2.7f, 1.8f, -5.0f), new Vec3(0.65f, 1.0f, 0.25f), 0.95f, 7.5f, true, localShadow));

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.06f, 0.08f, 0.10f), 0.28f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.88f, 0.72f);

        return new SceneDescriptor(
                "demo-scene-shadow-local-atlas",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                lights,
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor shadowQualityMatrixScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 3.2f, 12.0f), new Vec3(0f, 1.0f, -5.2f), 58f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.9f, -6.0f), new Vec3(0f, 0f, 0f), new Vec3(8.2f, 0.2f, 10.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.14f, 0.16f, 0.19f), 0.84f, 0.0f, null, null));

        int rows = 3;
        int columns = 4;
        float spacingX = 2.2f;
        float spacingZ = 2.2f;
        float startX = -((columns - 1) * spacingX) * 0.5f;
        float startZ = -1.2f;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                String id = "quality-" + row + "-" + col;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float x = startX + (col * spacingX);
                float z = startZ - (row * spacingZ);
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, 0.2f, z),
                        new Vec3(0f, row * 14f + col * 10f, 0f),
                        new Vec3(0.62f, 0.62f, 0.62f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float tint = (row * columns + col) / 11.0f;
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.28f + 0.28f * tint, 0.36f + 0.20f * (1.0f - tint), 0.60f - 0.14f * tint),
                        0.34f + (0.06f * row),
                        0.18f + (0.08f * (col / 3.0f)),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 12f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.0f,
                120f,
                true,
                directionalShadow
        );

        ShadowDesc lowShadow = new ShadowDesc(512, 0.0018f, 3, 1);
        ShadowDesc mediumShadow = new ShadowDesc(1024, 0.0014f, 4, 1);
        ShadowDesc highShadow = new ShadowDesc(1536, 0.0011f, 5, 1);
        List<LightDesc> lights = new ArrayList<>();
        lights.add(sun);
        lights.add(new LightDesc("quality-low", new Vec3(-3.0f, 2.0f, -1.2f), new Vec3(1.0f, 0.38f, 0.30f), 0.95f, 8.0f, true, lowShadow));
        lights.add(new LightDesc("quality-medium", new Vec3(0.0f, 2.0f, -3.4f), new Vec3(0.38f, 0.65f, 1.0f), 1.00f, 8.5f, true, mediumShadow));
        lights.add(new LightDesc("quality-high", new Vec3(3.0f, 2.0f, -5.6f), new Vec3(0.45f, 1.0f, 0.40f), 1.05f, 9.0f, true, highShadow));

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.06f, 0.08f, 0.10f), 0.30f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.88f, 0.72f);

        return new SceneDescriptor(
                "demo-scene-shadow-quality-matrix",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                lights,
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor aaMotionStressScene(String aaMode, float blend, float renderScale) {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.2f, 9.0f), new Vec3(0f, 0.8f, -4.8f), 62f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.85f, -5.4f), new Vec3(0f, 0f, 0f), new Vec3(7.5f, 0.15f, 8.5f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.12f, 0.14f, 0.17f), 0.86f, 0.0f, null, null));

        // Thin geometry grid to provoke AA edge crawl and shimmer behavior.
        int lanes = 7;
        int depthSlices = 7;
        float laneSpacing = 0.9f;
        float sliceSpacing = 1.1f;
        float startX = -((lanes - 1) * laneSpacing) * 0.5f;
        float startZ = -0.8f;
        for (int lane = 0; lane < lanes; lane++) {
            for (int slice = 0; slice < depthSlices; slice++) {
                String id = "thin-" + lane + "-" + slice;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float x = startX + (lane * laneSpacing);
                float z = startZ - (slice * sliceSpacing);
                float y = 0.18f + ((lane + slice) % 3) * 0.04f;
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, y, z),
                        new Vec3(0f, (lane * 11f) + (slice * 15f), 0f),
                        new Vec3(0.12f, 0.70f, 0.12f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float bias = (lane + slice) / 12.0f;
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.22f + 0.62f * bias, 0.30f + 0.48f * (1.0f - bias), 0.58f + 0.30f * bias),
                        0.25f + 0.35f * ((slice % 3) / 2.0f),
                        0.10f + 0.20f * ((lane % 2)),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 11f, 0f),
                new Vec3(1f, 0.96f, 0.90f),
                1.10f,
                120f,
                true,
                directionalShadow
        );
        LightDesc kickA = new LightDesc("kick-a", new Vec3(-2.8f, 2.2f, -2.4f), new Vec3(1.0f, 0.30f, 0.35f), 0.95f, 8.0f, false, null);
        LightDesc kickB = new LightDesc("kick-b", new Vec3(2.8f, 2.1f, -4.6f), new Vec3(0.30f, 0.60f, 1.0f), 0.92f, 8.0f, false, null);

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.05f, 0.07f, 0.10f), 0.26f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, Math.max(0.55f, Math.min(0.95f, blend)), 0.74f);

        return new SceneDescriptor(
                "demo-scene-aa-motion-stress-" + aaMode + "-rs" + String.format(java.util.Locale.ROOT, "%.2f", renderScale),
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                List.of(sun, kickA, kickB),
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor reflectionsSsrHizScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.6f, 9.0f), new Vec3(0f, 1.0f, -4.6f), 58f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.9f, -5.5f), new Vec3(0f, 0f, 0f), new Vec3(8.5f, 0.2f, 9.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.20f, 0.22f, 0.26f), 0.08f, 0.95f, null, null));

        int columns = 4;
        int rows = 3;
        float spacingX = 2.1f;
        float spacingZ = 2.1f;
        float startX = -((columns - 1) * spacingX) * 0.5f;
        float startZ = -1.0f;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                String id = "reflect-" + row + "-" + col;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                float x = startX + (col * spacingX);
                float z = startZ - (row * spacingZ);
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, 0.3f, z),
                        new Vec3(0f, row * 16f + col * 18f, 0f),
                        new Vec3(0.7f, 0.7f, 0.7f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float roughness = 0.05f + (0.20f * row) + (0.08f * col);
                float metalness = 0.55f + (0.12f * col);
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.36f + 0.14f * col, 0.48f - 0.08f * row, 0.72f - 0.10f * col),
                        Math.min(0.95f, roughness),
                        Math.min(1.0f, metalness),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 11f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.05f,
                110f,
                true,
                directionalShadow
        );
        LightDesc accentA = new LightDesc("accent-a", new Vec3(-3.2f, 2.1f, -1.8f), new Vec3(0.35f, 0.60f, 1.0f), 0.92f, 8.0f, false, null);
        LightDesc accentB = new LightDesc("accent-b", new Vec3(3.0f, 2.2f, -4.8f), new Vec3(1.0f, 0.42f, 0.35f), 0.92f, 8.0f, false, null);

        ReflectionDesc reflections = new ReflectionDesc(true, "ssr", 0.78f, 0.88f, 1.05f, 0.84f, 0.18f);
        ReflectionAdvancedDesc reflectionAdvanced = new ReflectionAdvancedDesc(
                true,   // hiZEnabled
                6,      // hiZMipCount
                2,      // denoisePasses
                false,  // planarClipPlaneEnabled
                0.0f,   // planarPlaneHeight
                0.5f,   // planarFadeStart
                8.0f,   // planarFadeEnd
                false,  // probeVolumeEnabled
                false,  // probeBoxProjectionEnabled
                2.0f,   // probeBlendDistance
                false,  // rtEnabled
                0.75f,  // rtMaxRoughness
                "ssr"   // rtFallbackMode
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.07f, 0.09f, 0.12f), 0.30f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(
                true, true, 1.05f, 2.2f,
                true, 0.90f, 0.74f,
                true, 0.52f, 1.0f, 0.02f, 1.15f,
                false, 0.0f,
                true, 0.78f, false,
                null,
                reflections,
                reflectionAdvanced
        );

        return new SceneDescriptor(
                "demo-scene-reflections-ssr-hiz",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                List.of(sun, accentA, accentB),
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor reflectionsPlanarScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 1.5f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 1000f);

        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.95f, -5.2f), new Vec3(0f, 0f, 0f), new Vec3(9.0f, 0.12f, 9.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.16f, 0.18f, 0.21f), 0.03f, 0.98f, null, null));

        transforms.add(new TransformDesc("pillar-a", new Vec3(-1.8f, 0.2f, -3.7f), new Vec3(0f, 20f, 0f), new Vec3(0.8f, 1.5f, 0.8f)));
        meshes.add(new MeshDesc("mesh-pillar-a", "pillar-a", "mat-pillar-a", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-pillar-a", new Vec3(0.82f, 0.36f, 0.28f), 0.14f, 0.74f, null, null));

        transforms.add(new TransformDesc("pillar-b", new Vec3(1.9f, 0.25f, -4.2f), new Vec3(0f, -24f, 0f), new Vec3(0.9f, 1.3f, 0.9f)));
        meshes.add(new MeshDesc("mesh-pillar-b", "pillar-b", "mat-pillar-b", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-pillar-b", new Vec3(0.30f, 0.58f, 0.86f), 0.11f, 0.68f, null, null));

        transforms.add(new TransformDesc("center", new Vec3(0.0f, 0.35f, -5.1f), new Vec3(0f, 35f, 0f), new Vec3(1.2f, 0.75f, 1.2f)));
        meshes.add(new MeshDesc("mesh-center", "center", "mat-center", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-center", new Vec3(0.80f, 0.83f, 0.88f), 0.08f, 0.92f, null, null));

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 11f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.10f,
                110f,
                true,
                directionalShadow
        );
        LightDesc accentA = new LightDesc("accent-a", new Vec3(-2.8f, 2.1f, -3.0f), new Vec3(0.30f, 0.58f, 1.0f), 0.88f, 8.0f, false, null);
        LightDesc accentB = new LightDesc("accent-b", new Vec3(2.9f, 2.2f, -6.2f), new Vec3(1.0f, 0.44f, 0.35f), 0.88f, 8.0f, false, null);

        ReflectionDesc reflections = new ReflectionDesc(true, "planar", 0.84f, 0.92f, 1.0f, 0.90f, 0.12f);
        ReflectionAdvancedDesc reflectionAdvanced = new ReflectionAdvancedDesc(
                true,   // hiZEnabled
                5,      // hiZMipCount
                2,      // denoisePasses
                true,   // planarClipPlaneEnabled
                -0.90f, // planarPlaneHeight
                0.2f,   // planarFadeStart
                7.0f,   // planarFadeEnd
                false,  // probeVolumeEnabled
                false,  // probeBoxProjectionEnabled
                2.0f,   // probeBlendDistance
                false,  // rtEnabled
                0.75f,  // rtMaxRoughness
                "ssr"   // rtFallbackMode
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.06f, 0.08f, 0.11f), 0.28f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(
                true, true, 1.03f, 2.1f,
                true, 0.90f, 0.72f,
                true, 0.52f, 1.0f, 0.02f, 1.10f,
                false, 0.0f,
                true, 0.80f, false,
                null,
                reflections,
                reflectionAdvanced
        );

        return new SceneDescriptor(
                "demo-scene-reflections-planar",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                List.of(sun, accentA, accentB),
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor reflectionsHybridScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.2f, 8.7f), new Vec3(0f, 0.8f, -4.8f), 58f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.95f, -5.6f), new Vec3(0f, 0f, 0f), new Vec3(8.8f, 0.15f, 9.4f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.18f, 0.20f, 0.24f), 0.07f, 0.96f, null, null));

        int columns = 3;
        int rows = 2;
        float spacingX = 2.4f;
        float spacingZ = 2.3f;
        float startX = -((columns - 1) * spacingX) * 0.5f;
        float startZ = -2.4f;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                String id = "hybrid-" + row + "-" + col;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(startX + col * spacingX, 0.35f, startZ - row * spacingZ),
                        new Vec3(0f, row * 15f + col * 22f, 0f),
                        new Vec3(0.9f, 0.9f, 0.9f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float roughness = 0.06f + row * 0.22f + col * 0.10f;
                float metalness = 0.52f + col * 0.13f;
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.30f + 0.18f * col, 0.45f - 0.07f * row, 0.74f - 0.11f * col),
                        Math.min(0.95f, roughness),
                        Math.min(1.0f, metalness),
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 11f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.05f,
                115f,
                true,
                directionalShadow
        );
        LightDesc accentA = new LightDesc("accent-a", new Vec3(-3.1f, 2.1f, -2.2f), new Vec3(0.34f, 0.60f, 1.0f), 0.90f, 8.0f, false, null);
        LightDesc accentB = new LightDesc("accent-b", new Vec3(3.0f, 2.2f, -5.7f), new Vec3(1.0f, 0.42f, 0.35f), 0.90f, 8.0f, false, null);

        ReflectionDesc reflections = new ReflectionDesc(true, "hybrid", 0.80f, 0.90f, 1.02f, 0.86f, 0.16f);
        ReflectionAdvancedDesc reflectionAdvanced = new ReflectionAdvancedDesc(
                true,   // hiZEnabled
                6,      // hiZMipCount
                2,      // denoisePasses
                true,   // planarClipPlaneEnabled
                -0.90f, // planarPlaneHeight
                0.3f,   // planarFadeStart
                7.5f,   // planarFadeEnd
                true,   // probeVolumeEnabled
                true,   // probeBoxProjectionEnabled
                2.5f,   // probeBlendDistance
                true,   // rtEnabled
                0.72f,  // rtMaxRoughness
                "hybrid" // rtFallbackMode
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.06f, 0.08f, 0.11f), 0.30f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(
                true, true, 1.05f, 2.2f,
                true, 0.90f, 0.74f,
                true, 0.52f, 1.0f, 0.02f, 1.15f,
                false, 0.0f,
                true, 0.78f, false,
                null,
                reflections,
                reflectionAdvanced
        );

        return new SceneDescriptor(
                "demo-scene-reflections-hybrid",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                List.of(sun, accentA, accentB),
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor fogSmokePostScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0f, 2.0f, 10.5f), new Vec3(0f, 1.0f, -6.5f), 58f, 0.1f, 1000f);
        List<TransformDesc> transforms = new ArrayList<>();
        List<MeshDesc> meshes = new ArrayList<>();
        List<MaterialDesc> materials = new ArrayList<>();

        transforms.add(new TransformDesc("floor", new Vec3(0f, -0.95f, -8.0f), new Vec3(0f, 0f, 0f), new Vec3(11.0f, 0.20f, 16.0f)));
        meshes.add(new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb"));
        materials.add(new MaterialDesc("mat-floor", new Vec3(0.14f, 0.15f, 0.17f), 0.80f, 0.0f, null, null));

        for (int lane = 0; lane < 3; lane++) {
            float x = (lane - 1) * 2.2f;
            for (int step = 0; step < 6; step++) {
                String id = "fog-" + lane + "-" + step;
                String transformId = "xform-" + id;
                String materialId = "mat-" + id;
                transforms.add(new TransformDesc(
                        transformId,
                        new Vec3(x, 0.35f + 0.05f * (step % 2), -2.0f - step * 2.2f),
                        new Vec3(0f, lane * 18f + step * 13f, 0f),
                        new Vec3(0.80f, 0.80f, 0.80f)
                ));
                meshes.add(new MeshDesc("mesh-" + id, transformId, materialId, "meshes/box.glb"));
                float depth = step / 5.0f;
                materials.add(new MaterialDesc(
                        materialId,
                        new Vec3(0.30f + 0.25f * depth, 0.34f + 0.20f * (1.0f - depth), 0.40f + 0.20f * lane / 2.0f),
                        0.24f + 0.20f * depth,
                        0.15f + 0.10f * lane,
                        null,
                        null
                ));
            }
        }

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0012f, 5, 3);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 12f, 0f),
                new Vec3(1f, 0.95f, 0.88f),
                1.00f,
                130f,
                true,
                directionalShadow
        );
        LightDesc emberA = new LightDesc("ember-a", new Vec3(-3.0f, 1.8f, -4.5f), new Vec3(1.0f, 0.45f, 0.30f), 1.05f, 10.0f, false, null);
        LightDesc emberB = new LightDesc("ember-b", new Vec3(0.0f, 1.8f, -7.5f), new Vec3(1.0f, 0.52f, 0.34f), 1.00f, 9.0f, false, null);
        LightDesc coolFill = new LightDesc("cool-fill", new Vec3(3.0f, 2.0f, -5.8f), new Vec3(0.36f, 0.60f, 1.0f), 0.78f, 12.0f, false, null);

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.06f, 0.08f, 0.10f), 0.26f, null);
        FogDesc fog = new FogDesc(
                true,
                FogMode.HEIGHT_EXPONENTIAL,
                new Vec3(0.48f, 0.52f, 0.58f),
                0.34f,
                0.30f,
                0.74f,
                0.10f,
                1.0f,
                0.20f
        );
        PostProcessDesc post = new PostProcessDesc(
                true, true, 1.10f, 2.2f,
                true, 0.75f, 0.95f,
                true, 0.62f, 1.1f, 0.02f, 1.2f,
                true, 0.88f,
                true, 0.82f, false,
                null,
                null,
                null
        );

        return new SceneDescriptor(
                "demo-scene-fog-smoke-post",
                List.of(camera),
                "main-cam",
                transforms,
                meshes,
                materials,
                List.of(sun, emberA, emberB, coolFill),
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

    static SceneDescriptor orbitCameraScene(float orbitAngleRadians) {
        float radius = 5.2f;
        float movingX = (float) Math.sin(orbitAngleRadians * 1.8f) * 1.2f;
        float movingZ = (float) Math.cos(orbitAngleRadians * 1.4f) * 0.8f;
        float camX = (float) Math.cos(orbitAngleRadians) * radius;
        float camZ = (float) Math.sin(orbitAngleRadians) * radius;
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(camX, 2.8f, camZ), new Vec3(movingX, 0.85f, movingZ), 64f, 0.1f, 1000f);

        TransformDesc floor = new TransformDesc("floor", new Vec3(0f, -1.15f, 0f), new Vec3(0f, 0f, 0f), new Vec3(7.6f, 0.18f, 7.6f));
        TransformDesc center = new TransformDesc("center", new Vec3(movingX, 0.45f, movingZ), new Vec3(0f, (float) Math.toDegrees(orbitAngleRadians) * 3.0f, 0f), new Vec3(2.1f, 2.1f, 2.1f));
        TransformDesc left = new TransformDesc("left", new Vec3(-2.2f, 0.35f, -1.1f), new Vec3(0f, 38f, 0f), new Vec3(1.25f, 1.25f, 1.25f));
        TransformDesc right = new TransformDesc("right", new Vec3(2.2f, 0.35f, 1.1f), new Vec3(0f, -42f, 0f), new Vec3(1.25f, 1.25f, 1.25f));

        MeshDesc floorMesh = new MeshDesc("mesh-floor", "floor", "mat-floor", "meshes/box.glb");
        MeshDesc centerMesh = new MeshDesc("mesh-center", "center", "mat-center", "meshes/box.glb");
        MeshDesc leftMesh = new MeshDesc("mesh-left", "left", "mat-left", "meshes/box.glb");
        MeshDesc rightMesh = new MeshDesc("mesh-right", "right", "mat-right", "meshes/box.glb");

        MaterialDesc floorMat = new MaterialDesc("mat-floor", new Vec3(0.18f, 0.20f, 0.24f), 0.80f, 0.0f, null, null);
        MaterialDesc centerMat = new MaterialDesc("mat-center", new Vec3(0.92f, 0.46f, 0.36f), 0.10f, 0.84f, null, null);
        MaterialDesc leftMat = new MaterialDesc("mat-left", new Vec3(0.30f, 0.58f, 0.90f), 0.22f, 0.60f, null, null);
        MaterialDesc rightMat = new MaterialDesc("mat-right", new Vec3(0.80f, 0.84f, 0.88f), 0.08f, 0.90f, null, null);

        ShadowDesc directionalShadow = new ShadowDesc(1536, 0.0011f, 5, 3);
        LightDesc sun = new LightDesc("sun", new Vec3(0f, 10f, 0f), new Vec3(1f, 0.97f, 0.92f), 1.18f, 100f, true, directionalShadow);
        LightDesc accentA = new LightDesc("accent-a", new Vec3(-2.6f, 2.2f, -1.0f), new Vec3(0.30f, 0.60f, 1.0f), 1.22f, 8.0f, false, null);
        LightDesc accentB = new LightDesc("accent-b", new Vec3(2.6f, 2.2f, 1.0f), new Vec3(1.0f, 0.44f, 0.35f), 1.22f, 8.0f, false, null);

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.30f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.05f, 2.2f, true, 0.88f, 0.72f);

        return new SceneDescriptor(
                "demo-scene-orbit-camera",
                List.of(camera),
                "main-cam",
                List.of(floor, center, left, right),
                List.of(centerMesh, leftMesh, rightMesh, floorMesh),
                List.of(floorMat, centerMat, leftMat, rightMat),
                List.of(sun, accentA, accentB),
                environment,
                fog,
                List.of(),
                post
        );
    }

    static SceneDescriptor sponzaScene() {
        CameraDesc camera = new CameraDesc(
                "main-cam",
                new Vec3(0f, 5f, 0f),
                new Vec3(-10f, 4f, 0f),
                65f, 0.1f, 200f
        );

        TransformDesc sceneTransform = new TransformDesc(
                "sponza-root",
                new Vec3(0f, 0f, 0f),
                new Vec3(0f, 0f, 0f),
                new Vec3(1f, 1f, 1f)
        );

        MaterialDesc defaultMat = new MaterialDesc(
                "sponza-default",
                new Vec3(0.8f, 0.8f, 0.8f),
                0f,
                0.5f,
                null,
                null
        );

        MeshDesc sponzaMesh = new MeshDesc(
                "gltf-scene:sponza",
                "sponza-root",
                "sponza-default",
                "scenes/Sponza/Sponza.gltf"
        );

        ShadowDesc directionalShadow = new ShadowDesc(2048, 0.0012f, 5, 4);
        LightDesc sun = new LightDesc(
                "sun",
                new Vec3(0f, 20f, 0f),
                new Vec3(1f, 0.97f, 0.92f),
                1.30f,
                200f,
                true,
                directionalShadow
        );
        LightDesc warmA = new LightDesc(
                "warm-a",
                new Vec3(-5f, 3f, 0f),
                new Vec3(1.0f, 0.85f, 0.65f),
                1.10f,
                15f,
                false,
                null
        );
        LightDesc warmB = new LightDesc(
                "warm-b",
                new Vec3(5f, 3f, 0f),
                new Vec3(1.0f, 0.85f, 0.65f),
                1.10f,
                15f,
                false,
                null
        );
        LightDesc coolFill = new LightDesc(
                "cool-fill",
                new Vec3(0f, 8f, -4f),
                new Vec3(0.55f, 0.70f, 1.0f),
                0.65f,
                25f,
                false,
                null
        );

        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.10f, 0.12f, 0.15f), 0.40f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(true, true, 1.10f, 2.2f, true, 0.88f, 0.80f);

        return new SceneDescriptor(
                "demo-scene-sponza",
                List.of(camera),
                "main-cam",
                List.of(sceneTransform),
                List.of(sponzaMesh),
                List.of(defaultMat),
                List.of(sun, warmA, warmB, coolFill),
                environment,
                fog,
                List.of(),
                post
        );
    }
}
