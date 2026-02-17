# Fog and Smoke Guide

Last updated: February 17, 2026

## 1. What Fog and Smoke Support
- Fog modes via `FogDesc` + `FogMode` (`NONE`, `EXPONENTIAL`, `HEIGHT_EXPONENTIAL`)
- Color/density/falloff/opacity/noise controls
- Volumetric-style smoke emitters via `SmokeEmitterDesc`

## 2. Quick Start

### Run fog+shadow stress profile
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessFogShadowStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### Run fog+smoke+shadow+post stress profile
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
FogDesc fog = new FogDesc(
    true,
    FogMode.HEIGHT_EXPONENTIAL,
    new Vec3(0.52f, 0.57f, 0.64f),
    0.36f,
    0.32f,
    0.72f,
    0.10f,
    1.0f,
    0.20f
);

SmokeEmitterDesc smoke = new SmokeEmitterDesc(
    "smoke-a",
    new Vec3(0f, 0.5f, 0f),
    new Vec3(1.2f, 0.8f, 1.2f),
    12f,
    0.55f,
    new Vec3(0.65f, 0.68f, 0.72f),
    0.75f,
    new Vec3(0f, 0.35f, 0f),
    0.25f,
    4.0f,
    true
);
```

## 3. Recommended Starting Values
Balanced fog:
- `density=0.20..0.40`
- `maxOpacity=0.60..0.78`
- `noiseAmount=0.05..0.15`

Balanced smoke:
- `density=0.45..0.70`
- `extinction=0.60..0.90`
- `lifetimeSeconds=3..6`

## 4. Validation Rules and Constraints
- `FogDesc` and `SmokeEmitterDesc` are host-authored; use bounded values to avoid overdriving noise/extinction.
- Validate visual behavior primarily with parity/stress tests.

## 5. Troubleshooting
If fog overwhelms scene:
- Lower `density` and `maxOpacity`.
- Reduce `noiseAmount` first before reducing density.

If smoke pops/flickers:
- Lower turbulence and emission rate.
- Increase temporal frames in compare runs for stability checks.

## 6. Validation Commands

Fog+smoke+shadow stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Tiered stress sweeps:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessStressGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add reference presets per biome/time-of-day.
