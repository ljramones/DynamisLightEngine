# DynamicLightEngine

Cross-platform Java rendering/game engine scaffold using a stable Java-first API boundary and backend SPI.

## Requirements

- JDK 25 (project enforces Java 25 via Maven Enforcer)
- Maven 3.9+

## JDK 25 setup

If you use `jenv`:

```bash
jenv local 25.0.1
java -version
mvn -version
```

Or shell-only:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

## Modules

- `engine-api`: immutable DTOs and runtime contracts (`org.dynamislight.api.*`)
- `engine-spi`: backend discovery SPI (`ServiceLoader`)
- `engine-impl-common`: shared lifecycle/runtime base for backend implementations
- `engine-impl-opengl`: OpenGL backend skeleton
- `engine-impl-vulkan`: Vulkan backend skeleton
- `engine-bridge-dynamisfx`: host bridge/mapping layer
- `engine-host-sample`: minimal console host that runs the lifecycle

## Interface contracts and implementations

### API contract modules

- `org.dynamislight.api.runtime`: lifecycle and execution surface (`EngineRuntime`, callbacks, frame/stats/capabilities)
- `org.dynamislight.api.config`: runtime config and quality tier
- `org.dynamislight.api.input`: host input DTOs
- `org.dynamislight.api.scene`: scene/fog/smoke DTOs
- `org.dynamislight.api.event`: event/warning DTOs
- `org.dynamislight.api.error`: engine error model (`EngineException`, `EngineErrorCode`)
- `org.dynamislight.api.logging`: structured runtime log DTOs
- `org.dynamislight.api.resource`: resource cache/reload service contracts

### SPI and backend implementations

- SPI contract: `engine-spi` (`EngineBackendProvider`, `BackendRegistry`) discovers backends through `ServiceLoader`.
- OpenGL implementation: `engine-impl-opengl` (`OpenGlBackendProvider`, `OpenGlEngineRuntime`) is the primary active backend.
- Vulkan implementation: `engine-impl-vulkan` (`VulkanBackendProvider`, `VulkanEngineRuntime`) is scaffolded and in-progress.
- Host integration: `engine-bridge-dynamisfx` provides the DynamisFX bridge/session and mappers to engine DTOs.

## Build and test

```bash
mvn clean compile
mvn test
```

GitHub Actions CI runs the same test command on `main` and pull requests using JDK 25:
- `.github/workflows/ci.yml`

## Run sample host

Install snapshots, then run the sample host:

```bash
mvn -DskipTests install
mvn -f engine-host-sample/pom.xml exec:java
```

Select backend by argument:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

Inspect and hot-reload resources in sample host:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="opengl --resources"
```

OpenGL backend options (via `EngineConfig.backendOptions`):
- `opengl.mockContext=true|false` (default `false`) skips native context creation for headless/test runs.
- `opengl.forceInitFailure=true|false` forces `BACKEND_INIT_FAILED` for failure-path testing.
- `opengl.windowVisible=true|false` (default `false`) controls visible presentation window.

Vulkan backend options:
- `vulkan.mockContext=true|false` (default `true`) toggles real Vulkan instance initialization.

Sample-host default keeps OpenGL in mock mode for portability. To run real OpenGL init/render from sample host:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args=\"opengl\" -Ddle.opengl.mockContext=false
```

## Current status

This is compile-first scaffolding for v1 interface contracts. OpenGL and Vulkan modules currently provide lifecycle-safe stub runtimes to validate API shape, backend discovery, and host integration flow.

OpenGL now includes a baseline fog path driven by `SceneDescriptor.fog` with quality-tier dependent sampling:
- `LOW`: coarse fog steps
- `MEDIUM/HIGH`: progressively smoother fog
- `ULTRA`: unquantized fog factor

OpenGL also consumes `SceneDescriptor.smokeEmitters` with a baseline screen-space smoke blend.
At lower tiers (`LOW`, `MEDIUM`) smoke quality is degraded intentionally and reported through `EngineWarning` code `SMOKE_QUALITY_DEGRADED`.

Resource baseline is now available through `EngineRuntime.resources()`:
- in-memory asset cache with ref-count ownership
- automatic scene asset acquire/release on scene swap and shutdown
- filesystem-backed `LOADED` / `FAILED` state transitions
- checksum-aware hot reload via `EngineResourceService.reload(ResourceId)` (changed vs unchanged detection) with `ResourceHotReloadedEvent`
- per-resource metadata in `ResourceInfo` (`resolvedPath`, `lastChecksum`, `lastLoadedEpochMs`)
- v1 eviction policy: no TTL; zero-ref resources remain cacheable and are evicted by `resource.cache.maxEntries` pressure

Resource runtime options (`EngineConfig.backendOptions`):
- `resource.watch.enabled=true|false` (default `false`) enables filesystem watcher auto-reload.
- `resource.watch.debounceMs=<int>` (default `200`) debounce window for watcher-triggered reloads.
- `resource.cache.maxEntries=<int>` (default `256`) maximum cached resource records.
- `resource.reload.maxRetries=<int>` (default `2`) retry attempts for failed reload scans.

Resource telemetry is available via `EngineRuntime.resources().stats()`:
- cache hits/misses
- reload requests/failures
- evictions
- watcher event count

Optional integration-test flags:
- `-Ddle.test.resource.watch=true` enables watcher auto-reload integration test.
- `-Ddle.test.vulkan.real=true` enables real Vulkan init integration test.

## Planning

- Milestone and issue backlog: `docs/github-milestones.md`
- Architecture note: `docs/architecture/backend-strategy.md`
- ADR: `docs/adr/0001-backend-strategy.md`
