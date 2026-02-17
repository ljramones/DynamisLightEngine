# Materials and Lighting Guide

Last updated: February 17, 2026

## 1. What Materials and Lighting Support
- PBR-leaning material inputs via `MaterialDesc`
- Temporal/reactive authoring controls per material
- Directional/spot/point lights via `LightDesc` + `LightType`
- Optional authored spot convenience via `SpotLightDesc` (convert with `toLightDesc()`)
- Shadow integration via `ShadowDesc`

## 2. Quick Start

### Run baseline material+lighting compare
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessBrdfTierExtremeSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### Run mixed material/fog/smoke/shadow stress
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessMaterialFogSmokeShadowStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
MaterialDesc mat = new MaterialDesc(
    "mat-main",
    new Vec3(0.90f, 0.58f, 0.44f),
    0.35f,
    0.40f,
    "textures/base.png",
    "textures/norm.png",
    "textures/mr.png",
    "textures/ao.png",
    0.90f,
    true,
    true,
    1.25f,
    0.75f,
    1.20f,
    ReactivePreset.BALANCED
);

LightDesc key = new LightDesc(
    "key",
    new Vec3(8f, 20f, 8f),
    new Vec3(1f, 0.98f, 0.95f),
    1.10f,
    220f,
    true,
    new ShadowDesc(2048, 0.0007f, 5, 4),
    LightType.DIRECTIONAL,
    new Vec3(-0.35f, -1f, -0.25f),
    15f,
    30f
);

SpotLightDesc rimSpot = new SpotLightDesc(
    "rim-spot",
    new Vec3(-3f, 5f, 2f),
    new Vec3(0.25f, -1f, -0.2f),
    new Vec3(0.85f, 0.90f, 1.0f),
    2.4f,
    18f,
    16f,
    30f,
    true,
    new ShadowDesc(1024, 0.0008f, 3, 1)
);
LightDesc rim = rimSpot.toLightDesc();
```

## 3. Validation Rules and Constraints
`SceneValidator` enforces:
- `reactiveStrength` in `[0,1]`
- `reactiveBoost` in `[0,2]`
- `taaHistoryClamp` in `[0,1]`
- `emissiveReactiveBoost` in `[0,3]`
- Spot cones must be valid (`outer >= inner`, both non-negative)
- Non-directional lights must have `range > 0`

## 4. Backend Notes
- OpenGL/Vulkan both support material reactive tuning and PBR baseline.
- OpenGL/Vulkan now pack and shade multiple non-directional local lights per frame (current cap: `8` point/spot lights).
- Local lights are uploaded as per-light GPU array data (`pos+range`, `color+intensity`, `dir+innerCone`, `outerCone+type+shadowFlag`) in both backends.
- A primary local light is still selected for the existing shadow map path (until full per-light shadow atlas/clustered shadowing is introduced).
- Non-directional attenuation is range-aware in both backends.
- Non-directional shadow requests apply to both point and spot lights.

## 5. Troubleshooting
If specular shimmer is high:
- Increase roughness slightly on problematic materials.
- Tune reactive fields (`reactiveBoost`, `taaHistoryClamp`).
- Validate in `taa-specular-*` scenes.

If lighting appears flat:
- Verify light intensity/range and environment ambient settings.
- Check normals/metallic-roughness texture correctness.

## 6. Validation Commands

Specular stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaSpecularMicroHighlightsStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Material interaction stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessMaterialFogSmokeShadowStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add material authoring cookbook for common surfaces.
