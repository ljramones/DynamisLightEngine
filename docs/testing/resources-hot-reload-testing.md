# Resources and Hot-Reload Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate resource lifecycle correctness and hot-reload stability.

## 2. Coverage
- Acquire/release/reload success/failure paths
- Resource event emission (`ResourceHotReloadedEvent`)
- Cache telemetry behavior under repeated access

## 3. Primary Metrics
- `ResourceCacheStats` counters
- resource state transitions in `ResourceInfo`
- reload failure count and error surfaces

## 4. Execution Commands

API tests:
```bash
mvn -pl engine-api -am test
```

Sample runtime probe:
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --resources"
```

## 5. Pass/Fail Criteria
- Reloadable resources can be reloaded without runtime failure.
- Non-recoverable resource failures report deterministic error codes.

## 6. Known Gaps
- Add deterministic file-change simulation tests for hot-reload pipelines.
