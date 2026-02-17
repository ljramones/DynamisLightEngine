# Materials and Lighting Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate PBR/reactive material behavior and light/shadow interactions across backends and AA modes.

## 2. Coverage
- BRDF tier extremes
- Texture-heavy material scenes
- Material + fog/smoke/shadow combined stress
- Specular AA stress behavior

## 3. Primary Metrics
- `compare.diffMetric`
- `shimmerIndex` (specular-heavy scenes)
- warning-code snapshots per backend

## 4. Execution Commands

BRDF extremes:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessBrdfTierExtremeSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Material+fog+smoke+shadow stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessMaterialFogSmokeShadowStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Specular micro-highlights stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaSpecularMicroHighlightsStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- All scene diffs pass configured profile thresholds.
- No material reactive-range validation errors.

## 6. Known Gaps
- Add explicit authored-material fixture packs and expected histogram checks.
