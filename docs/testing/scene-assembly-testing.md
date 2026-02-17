# Scene Assembly Testing Design

Last updated: February 17, 2026

## 1. Goal
Ensure host-authored scenes are structurally valid and portable across backends before rendering.

## 2. Coverage
- DTO ID uniqueness and reference correctness
- `activeCameraId` resolution
- Cross-backend scene load parity on baseline compare profile

## 3. Core Checks
- `SceneValidator` unit tests for invalid graphs
- Compare harness smoke test on assembled scene
- Runtime load does not emit scene validation errors

## 4. Execution Commands

API validation tests:
```bash
mvn -pl engine-api -am test
```

Scene assembly compare test:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessProducesImagesWithBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- No `SCENE_VALIDATION_FAILED` errors.
- Compare harness baseline scene remains within threshold.

## 6. Known Gaps
- Add dedicated invalid-scene fixture matrix in host-sample tests.
