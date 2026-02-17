# Input and Frame Loop Guide

Last updated: February 17, 2026

## 1. What Input/Frame Loop Supports
- Per-frame host input submission via `EngineInput`
- Stable update/render stepping through `EngineRuntime`
- Frame stats/warnings through `EngineFrameResult`

## 2. Quick Start

### Run interactive sample host
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --interactive"
```

### Run frame-loop-sensitive AA motion tests
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessAnimatedMotionTargetedScenesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
while (running) {
    EngineInput input = new EngineInput(
        mouseX,
        mouseY,
        mouseDx,
        mouseDy,
        leftDown,
        rightDown,
        keysDown,
        scrollDelta
    );

    EngineFrameResult updateResult = runtime.update(1.0 / 60.0, input);
    EngineFrameResult renderResult = runtime.render();

    if (!renderResult.warnings().isEmpty()) {
        // host warning handling
    }
}
```

## 3. Recommended Frame Loop Rules
- Use a deterministic `dtSeconds` policy for tests.
- Keep input state immutable per frame.
- Keep update/render calls serialized on one engine thread.

## 4. Validation Rules and Constraints
- `EngineInput.keysDown` is defensively copied to immutable set.
- Runtime contract is non-reentrant and single-threaded.
- Use `resize()` on window/dpi changes before continued rendering.

## 5. Troubleshooting
If frame pacing looks unstable:
- Verify fixed-step or capped-step policy in host.
- Check warning events and frame results for degradation signals.

If input feels delayed:
- Ensure input sampling occurs immediately before `update()`.
- Avoid host-side blocking in callback paths.

## 6. Validation Commands

AA animated motion validation:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessAnimatedMotionTargetedScenesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Full parity loop:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add documented fixed-step reference loop with interpolation sample.
