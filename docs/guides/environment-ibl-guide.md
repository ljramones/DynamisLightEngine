# Environment and IBL Guide

Last updated: February 17, 2026

## 1. What Environment Supports
- Ambient environment lighting via `EnvironmentDesc`
- Optional skybox path
- Optional IBL texture paths (`irradiance`, `radiance`, `BRDF LUT`)

## 2. Quick Start

### Run environment-sensitive parity scene
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessBrdfTierExtremeSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### Run texture-heavy scene for IBL/material interactions
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTextureHeavySceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
EnvironmentDesc environment = new EnvironmentDesc(
    new Vec3(0.10f, 0.10f, 0.12f),
    0.25f,
    "skyboxes/studio.hdr",
    "ibl/studio_irradiance.ktx2",
    "ibl/studio_radiance.ktx2",
    "ibl/studio_brdf_lut.ktx2"
);
```

## 3. Recommended Starting Values
- `ambientIntensity=0.15..0.35` for physically plausible baseline
- Use consistent IBL set (matching irradiance/radiance/LUT generation pipeline)

## 4. Validation Rules and Constraints
- `EnvironmentDesc` accepts null texture paths; runtime behavior depends on available assets.
- Keep asset-root consistency so relative paths resolve in both backends.

## 5. Troubleshooting
If scene appears too dark:
- Verify ambient intensity and IBL texture paths.
- Check that IBL assets exist under configured `assetRoot`.

If specular response is wrong:
- Confirm radiance map format/mips and BRDF LUT path.
- Re-test with `compareHarnessBrdfTierExtremeSceneHasBoundedDiff`.

## 6. Validation Commands

BRDF/IBL regression:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessBrdfTierExtremeSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Texture/IBL interaction:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTextureHeavySceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add explicit IBL asset format recommendations and tooling guide.
