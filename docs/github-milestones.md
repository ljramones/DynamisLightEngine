# GitHub Milestones And Issue Backlog (v1)

This backlog is ordered by dependency so you can create milestones/issues directly in GitHub and execute top-down.

## Suggested Labels

- `type:feature`
- `type:refactor`
- `type:test`
- `type:docs`
- `area:api`
- `area:spi`
- `area:opengl`
- `area:bridge`
- `area:ci`
- `priority:p0`
- `priority:p1`

## Milestone M1: API Contract Hardening

Target: freeze v1 contract behavior and validation semantics.

### Issue M1-1: Add lifecycle/threading/ownership Javadocs across engine-api
- Labels: `type:docs`, `area:api`, `priority:p0`
- Depends on: none
- Acceptance criteria:
  - Every public type in `engine-api` has class-level Javadoc.
  - `EngineRuntime` and `EngineHostCallbacks` document threading + reentrancy rules.
  - DTO ownership rules for buffers/handles are explicit.

### Issue M1-2: Implement EngineConfig and SceneDescriptor validators
- Labels: `type:feature`, `area:api`, `priority:p0`
- Depends on: M1-1
- Acceptance criteria:
  - Add validation utility for config and scene inputs.
  - Returns/throws `EngineException` with mapped `EngineErrorCode`.
  - Host sample uses validators before runtime calls.

### Issue M1-3: Add API contract tests
- Labels: `type:test`, `area:api`, `priority:p0`
- Depends on: M1-2
- Acceptance criteria:
  - Tests cover immutability copy semantics (`List.copyOf`, etc.).
  - Tests cover version compatibility behavior.
  - Tests cover validator failure paths.

## Milestone M2: SPI Discovery And Backend Selection

Target: robust backend discovery/selection behavior.

### Issue M2-1: Add BackendRegistry helper
- Labels: `type:feature`, `area:spi`, `priority:p0`
- Depends on: M1-3
- Acceptance criteria:
  - Resolve backend by id using `ServiceLoader`.
  - Expose list of discovered providers with metadata.
  - Handle no-match with `BACKEND_NOT_FOUND`.

### Issue M2-2: Add duplicate-id and version mismatch handling
- Labels: `type:feature`, `area:spi`, `priority:p0`
- Depends on: M2-1
- Acceptance criteria:
  - Duplicate backend IDs are detected deterministically.
  - Unsupported API versions fail with clear error.
  - Behavior is covered by tests.

## Milestone M3: OpenGL Real Bootstrap

Target: replace stub render path with real OpenGL bootstrap.

### Issue M3-1: Add OpenGL binding and context init
- Labels: `type:feature`, `area:opengl`, `priority:p0`
- Depends on: M2-2
- Acceptance criteria:
  - OpenGL backend initializes a real graphics context.
  - Initialization failures map to `BACKEND_INIT_FAILED`.
  - Runtime still respects lifecycle contracts.

### Issue M3-2: Implement clear-color + triangle render
- Labels: `type:feature`, `area:opengl`, `priority:p0`
- Depends on: M3-1
- Acceptance criteria:
  - `render()` performs actual draw work.
  - Resize updates viewport/swapchain resources.
  - Frame stats are populated from real timing.

### Issue M3-3: OpenGL lifecycle regression tests
- Labels: `type:test`, `area:opengl`, `priority:p0`
- Depends on: M3-2
- Acceptance criteria:
  - Tests verify init -> load -> update/render -> resize -> shutdown path.
  - Tests verify invalid state and invalid argument failures.

## Milestone M4: Bridge + Scene Ingestion Baseline

Target: minimal but real host-to-engine mapping.

### Issue M4-1: Implement SceneMapper v1 baseline mapping
- Labels: `type:feature`, `area:bridge`, `priority:p1`
- Depends on: M3-3
- Acceptance criteria:
  - Maps cameras/transforms/meshes/materials/lights/environment.
  - Invalid source data maps to `SCENE_VALIDATION_FAILED`.

### Issue M4-2: Implement InputMapper key/mouse mapping matrix
- Labels: `type:feature`, `area:bridge`, `priority:p1`
- Depends on: M4-1
- Acceptance criteria:
  - Stable mapping for movement/camera controls.
  - Unit tests cover key combinations and deltas.

## Milestone M5: Observability + CI

Target: make behavior measurable and reproducible.

### Issue M5-1: Standardize logs/events from runtime lifecycle and render loop
- Labels: `type:feature`, `area:api`, `priority:p1`
- Depends on: M4-2
- Acceptance criteria:
  - Required categories emitted (`LIFECYCLE`, `RENDER`, `SCENE`, `SHADER`, `PERF`, `ERROR`).
  - Event emission is non-blocking and documented.

### Issue M5-2: Add GitHub Actions CI (JDK 25)
- Labels: `type:feature`, `area:ci`, `priority:p0`
- Depends on: M5-1
- Acceptance criteria:
  - CI runs `mvn test` on pull requests and main.
  - Build fails if Java version is not 25.

## Milestone M6: Fog + Smoke Baseline

Target: first volumetric feature delivery.

### Issue M6-1: Implement fog baseline (exponential + height)
- Labels: `type:feature`, `area:opengl`, `priority:p1`
- Depends on: M5-2
- Acceptance criteria:
  - `FogDesc` is consumed and affects render output.
  - Quality tier impacts fog quality.

### Issue M6-2: Implement smoke emitter baseline
- Labels: `type:feature`, `area:opengl`, `priority:p1`
- Depends on: M6-1
- Acceptance criteria:
  - `SmokeEmitterDesc` is consumed and rendered.
  - Runtime emits `EngineWarning` when degrading quality.
