# Environment and IBL Testing Design

Last updated: February 17, 2026

## 1. Goal
Ensure ambient/IBL shading consistency and prevent regressions in environment-driven lighting.

## 2. Coverage
- BRDF tier extreme scene
- Texture-heavy scene for IBL/material interaction
- Cross-backend parity under real and mock Vulkan profiles

## 3. Primary Metrics
- `compare.diffMetric`
- profile metadata correctness (`vulkan_real` vs `vulkan_mock`)
- warning-code snapshots

## 4. Execution Commands

BRDF/IBL baseline:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessBrdfTierExtremeSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Texture-heavy interaction:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTextureHeavySceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- Diff remains within thresholds for both scenes.
- No missing-environment regressions in real Vulkan runs.

## 6. Known Gaps
- Add explicit IBL asset validation command and checksum report step.
