# Runtime, Events, and Errors Guide

Last updated: February 17, 2026

## 1. What Runtime/Event/Error APIs Support
- Engine lifecycle through `EngineRuntime`
- Host callback integration via `EngineHostCallbacks`
- Structured engine events (`EngineEvent` hierarchy)
- Structured errors (`EngineException`, `EngineErrorReport`, `EngineErrorCode`)

## 2. Quick Start

### Run sample host and observe runtime loop
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="opengl --interactive"
```

### Run parity test suite for runtime stability
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
try (EngineRuntime runtime = provider.createRuntime()) {
    runtime.initialize(config, new EngineHostCallbacks() {
        @Override
        public void onEvent(EngineEvent event) {
            if (event instanceof AaTelemetryEvent aa) {
                System.out.println("AA rejectRate=" + aa.historyRejectRate());
            }
        }

        @Override
        public void onError(EngineErrorReport report) {
            System.err.println(report.code() + ": " + report.message());
        }
    });

    runtime.loadScene(scene);
    EngineFrameResult update = runtime.update(1.0 / 60.0, input);
    EngineFrameResult render = runtime.render();
    runtime.shutdown();
}
```

## 3. Event Types You Should Handle
- `SceneLoadedEvent`
- `SceneLoadFailedEvent`
- `ResourceHotReloadedEvent`
- `DeviceLostEvent`
- `AaTelemetryEvent`
- `PerformanceWarningEvent`

## 4. Error Handling Guidance
- Use `EngineErrorCode` to classify response behavior.
- Treat `recoverable=true` errors as potentially non-fatal.
- For `DEVICE_LOST`, tear down and recreate runtime path according to host policy.

## 5. Troubleshooting
If runtime call order fails:
- Verify lifecycle sequence: initialize -> loadScene -> update/render -> shutdown.

If callbacks cause instability:
- Ensure callbacks are non-blocking.
- Do not synchronously call runtime methods from callback threads.

## 6. Validation Commands

Runtime/API tests:
```bash
mvn -pl engine-api -am test
```

Host runtime integration tests:
```bash
mvn -pl engine-host-sample -am test
```

## 7. Known Gaps / Next Steps
- Add a documented host policy template for recoverable vs fatal errors by code.
