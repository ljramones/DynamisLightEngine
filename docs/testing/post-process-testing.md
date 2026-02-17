# Post-Process Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate tonemap/bloom/SSAO/SMAA and AA integration stability across backends.

## 2. Coverage
- Post baseline
- Bloom-specific stress
- SSAO baseline + stress
- SMAA post profile

## 3. Primary Metrics
- `compare.diffMetric`
- scene-specific thresholds per profile mode
- temporal drift metrics for AA-enabled post scenes

## 4. Execution Commands

Post baseline:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Bloom:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessBloomSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

SSAO stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessPostProcessSsaoStressSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- All post scenes pass current thresholds.
- No unexpected post warning/error codes.

## 6. Known Gaps
- Add screenshot histogram checks for bloom shoulder clipping behavior.
