# Scene Assembly Guide

Last updated: February 17, 2026

## 1. What Scene Assembly Supports
- Immutable scene DTO composition via `SceneDescriptor`
- ID-linked graph (`CameraDesc`, `TransformDesc`, `MeshDesc`, `MaterialDesc`)
- Optional feature blocks (`EnvironmentDesc`, `FogDesc`, `SmokeEmitterDesc`, `PostProcessDesc`)
- Validation through `SceneValidator`

## 2. Quick Start

### Run sample host with default assembled scene
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

### Run compare-harness scene assembly regression
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessProducesImagesWithBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
SceneDescriptor scene = new SceneDescriptor(
    "example-scene",
    List.of(new CameraDesc("cam-main", new Vec3(0f, 2f, 6f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 500f)),
    "cam-main",
    List.of(new TransformDesc("root", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 0f), new Vec3(1f, 1f, 1f))),
    List.of(new MeshDesc("mesh-1", "root", "mat-1", "meshes/triangle.glb")),
    List.of(new MaterialDesc("mat-1", new Vec3(1f, 1f, 1f), 0.1f, 0.7f, null, null)),
    List.of(new LightDesc("sun", new Vec3(0f, 10f, 0f), new Vec3(1f, 1f, 1f), 1.0f, 100f, false, null)),
    new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.12f), 0.25f, null),
    new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f),
    List.of(),
    null
);

SceneValidator.validate(scene);
```

## 3. Validation Rules and Constraints
- `sceneName` must be non-blank.
- IDs must be unique per DTO type.
- `activeCameraId` must reference an existing camera if provided.
- Each mesh must reference existing `transformId` and `materialId`.

## 4. Backend Notes
- Scene assembly contract is backend-agnostic.
- OpenGL and Vulkan share the same validated scene graph input.

## 5. Troubleshooting
If scene load fails with `SCENE_VALIDATION_FAILED`:
- Check blank IDs and duplicate IDs first.
- Verify mesh references point to existing transform/material IDs.
- Run validator in host-side tests before runtime load.

## 6. Validation Commands

Core validation tests:
```bash
mvn -pl engine-api -am test
```

Scene parity regression:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessProducesImagesWithBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add explicit docs for scene patch/hot-reload workflow once exposed.
- Add a reusable scene-builder helper layer for host apps.
