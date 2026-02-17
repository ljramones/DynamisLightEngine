# Camera Guide

Last updated: February 17, 2026

## 1. What Camera Supports
- Perspective camera setup through `CameraDesc`
- Position and Euler rotation control per camera
- Active camera selection via `SceneDescriptor.activeCameraId`

## 2. Quick Start

### Run sample with default camera
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="opengl"
```

### Run camera-sensitive stress scene
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaDisocclusionRapidPanStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
CameraDesc camera = new CameraDesc(
    "cam-main",
    new Vec3(0.0f, 1.5f, 8.0f),
    new Vec3(-8.0f, 12.0f, 0.0f),
    72.0f,
    0.1f,
    250.0f
);

SceneDescriptor scene = new SceneDescriptor(
    "camera-scene",
    List.of(camera),
    "cam-main",
    transforms,
    meshes,
    materials,
    lights,
    environment,
    fog,
    smoke,
    post
);
```

## 3. Recommended Starting Values
Balanced:
- `fovDegrees=60..74`
- `nearPlane=0.1`
- `farPlane=150..300`

Large outdoor scenes:
- `fovDegrees=70..82`
- `farPlane=250..1000`

## 4. Validation Rules and Constraints
- Camera IDs must be unique and non-blank.
- `activeCameraId` must reference an existing camera ID.
- Keep `nearPlane` positive and reasonably > 0 to avoid depth precision issues.

## 5. Troubleshooting
If depth artifacts increase:
- Raise `nearPlane` (for example `0.1 -> 0.2`) where acceptable.
- Avoid excessively large `farPlane` unless needed.

If motion instability appears in temporal AA:
- Verify camera pan/rotation rates in stress scenes.
- Use AA debug views (`velocity`, `disocclusion`, `historyWeight`).

## 6. Validation Commands

Fast camera stress test:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaDisocclusionStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Rapid-pan + animated test:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaDisocclusionRapidPanStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add formal camera path playback assets for deterministic cinematic sweeps.
