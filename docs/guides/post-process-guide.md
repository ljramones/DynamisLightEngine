# Post-Process Guide

Last updated: February 17, 2026

## 1. What Post-Process Supports
- Tonemapping/exposure/gamma
- Bloom threshold and intensity controls
- SSAO-lite tuning controls
- SMAA and TAA integration via `PostProcessDesc`
- Scene-level AA override via `AntiAliasingDesc`

## 2. Quick Start

### Run post-process baseline suite
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### Run bloom-specific stress
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessBloomSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
PostProcessDesc post = new PostProcessDesc(
    true,
    true,
    1.10f,
    2.2f,
    true,
    0.92f,
    0.84f,
    true,
    0.58f,
    1.05f,
    0.02f,
    1.20f,
    true,
    0.66f,
    true,
    0.68f,
    true,
    new AntiAliasingDesc("tsr", true, 0.90f, 0.82f, true, 0.12f, 0.64f, 0)
);
```

## 3. Recommended Starting Values
Balanced:
- `exposure=1.05..1.15`
- `gamma=2.2`
- `bloomThreshold=0.9..1.0`
- `bloomStrength=0.75..0.9`
- `ssaoStrength=0.55..0.65`

## 4. Validation Rules and Constraints
- Use scene validation for AA descriptor ranges (blend/clip/sharpen/renderScale/debugView).
- Prefer parity and stress profiles for final pass/fail on post chains.

## 5. Troubleshooting
If bloom clips too aggressively:
- Raise `bloomThreshold`.
- Lower `bloomStrength`.

If SSAO looks too harsh:
- Reduce `ssaoStrength`.
- Increase `ssaoBias` slightly.
- Lower `ssaoPower`.

## 6. Validation Commands

Post baseline:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Post SSAO stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessSsaoStressSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add authored post presets (`cinematic`, `neutral`, `performance`).
