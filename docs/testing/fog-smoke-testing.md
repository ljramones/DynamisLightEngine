# Fog and Smoke Testing Design

Last updated: February 17, 2026

## 1. Goal
Prevent regressions in volumetric-style fog/smoke behavior, especially under shadows and post effects.

## 2. Coverage
- Fog + shadow stress
- Smoke + shadow stress
- Combined fog + smoke + shadow + post stress
- Tiered golden envelope checks

## 3. Primary Metrics
- `compare.diffMetric`
- temporal drift metrics when AA is enabled
- warning-code snapshots in compare metadata

## 4. Execution Commands

Fog+shadow:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessFogShadowStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Smoke+shadow:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessSmokeShadowStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Combined stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- Combined stress scene remains within locked threshold envelope.
- Tiered profile tests stay bounded (`LOW/MEDIUM/HIGH/ULTRA`).

## 6. Known Gaps
- Add parameter sweep tests for fog-noise and smoke-turbulence extremes.
