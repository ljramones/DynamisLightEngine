# Runtime, Events, and Errors Testing Design

Last updated: February 17, 2026

## 1. Goal
Keep runtime lifecycle behavior stable and error/event surfaces predictable for host apps.

## 2. Coverage
- Lifecycle order enforcement
- Event emission and callback handling
- Error classification and recoverability behavior

## 3. Primary Metrics
- event occurrence correctness by scenario
- error code correctness (`EngineErrorCode`)
- runtime loop stability in integration runs

## 4. Execution Commands

API tests:
```bash
mvn -pl engine-api -am test
```

Host integration tests:
```bash
mvn -pl engine-host-sample -am test
```

## 5. Pass/Fail Criteria
- Invalid lifecycle/order cases fail with expected codes.
- Event callbacks fire for relevant runtime transitions.
- No unclassified internal errors in baseline test runs.

## 6. Known Gaps
- Add explicit simulated device-loss recovery scenario tests.
