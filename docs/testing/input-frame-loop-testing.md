# Input and Frame Loop Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate host update/render loop correctness, input propagation, and frame stability.

## 2. Coverage
- Input sampling and propagation into update loop
- Frame result stability under stress scenes
- Warning handling behavior in high-load conditions

## 3. Primary Metrics
- `EngineFrameResult` timing/warning fields
- compare-harness diff/temporal metrics for motion scenes
- runtime warning/event incidence

## 4. Execution Commands

Animated motion suite:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessAnimatedMotionTargetedScenesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Full parity suite:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- Motion suites remain within threshold envelopes.
- No regression in frame-loop stability warnings for baseline runs.

## 6. Known Gaps
- Add fixed-step jitter simulation tests for host-side timing edge cases.
