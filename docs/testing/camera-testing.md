# Camera Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate camera-driven stability for temporal reconstruction and scene framing.

## 2. Coverage
- Static camera parity scenes
- Rapid camera pan + disocclusion stress
- Fast-motion camera with animated objects (AA targeted scenes)

## 3. Primary Metrics
- `compare.diffMetric`
- `shimmerIndex`
- `taaHistoryRejectRate`
- `taaConfidenceMean`

## 4. Execution Commands

Disocclusion stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaDisocclusionStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Rapid-pan stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTaaDisocclusionRapidPanStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Animated motion aggregate:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessAnimatedMotionTargetedScenesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- No threshold breaches in targeted camera/motion scenarios.
- Trend windows stable for reject/confidence metrics in real Vulkan runs.

## 6. Known Gaps
- Add deterministic camera path playback fixtures for long-run sweeps.
