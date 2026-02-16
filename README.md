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
- `engine-impl-opengl`: OpenGL backend implementation
- `engine-impl-vulkan`: Vulkan backend implementation
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
- Vulkan implementation: `engine-impl-vulkan` (`VulkanBackendProvider`, `VulkanEngineRuntime`) provides a real baseline render path (context, swapchain, pipeline, indexed draw).
- Host integration: `engine-bridge-dynamisfx` provides the DynamisFX bridge/session and mappers to engine DTOs.

## Build and test

```bash
mvn clean compile
mvn test
```

If `JAVA_HOME` is stale in your shell, use the repo launcher to force `.java-version` (25):

```bash
./scripts/mvnw25 test
```

Run backend compare-harness parity tests explicitly:

```bash
mvn -pl engine-host-sample -am test -Ddle.compare.tests=true -Dtest=BackendParityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Write compare outputs to a stable folder for inspection/artifacts:

```bash
mvn -pl engine-host-sample -am test -Ddle.compare.tests=true -Ddle.compare.outputDir=artifacts/compare -Dtest=BackendParityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```
This includes tiered fog/smoke, shadow, and texture-heavy checks plus `shadow-cascade-stress`, `fog-shadow-cascade-stress`, `smoke-shadow-cascade-stress`, and `texture-heavy` compare profiles.

GitHub Actions CI runs:
- matrix build/test (`mvn test`) on `main` and pull requests using JDK 25 across Linux, macOS, and Windows
- guarded backend parity compare harness tests on Ubuntu (`dle.compare.tests=true`)
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

Run compare harness from sample host (writes images under `artifacts/compare`):

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="--compare --compare-tier=HIGH --compare-tag=shadow-high"
```

Compare mode backend toggles:
- `--compare-opengl-mock=true|false`
- `--compare-vulkan-mock=true|false`
- `--compare-vulkan-offscreen=true|false`

Tune sample host shadow/post parameters from CLI:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --tier=HIGH --shadow=on --shadow-cascades=4 --shadow-pcf=5 --shadow-bias=0.001 --shadow-res=2048 --post=on --tonemap=on --exposure=1.1 --gamma=2.2 --bloom=on --bloom-threshold=1.0 --bloom-strength=0.8"
```

Interactive tuning + diagnostics overlay:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --interactive --overlay --frames=99999"
```

Interactive commands:
- `help`, `show`, `reload`, `quit`
- `tier LOW|MEDIUM|HIGH|ULTRA`
- `shadow on|off`, `shadow_cascades <1-4>`, `shadow_pcf <1-9>`, `shadow_bias <float>`, `shadow_res <256-4096>`
- `post on|off`, `tonemap on|off`, `exposure <0.25-4.0>`, `gamma <1.2-3.0>`
- `bloom on|off`, `bloom_threshold <0.2-2.5>`, `bloom_strength <0.0-1.6>`

OpenGL backend options (via `EngineConfig.backendOptions`):
- `opengl.mockContext=true|false` (default `false`) skips native context creation for headless/test runs.
- `opengl.forceInitFailure=true|false` forces `BACKEND_INIT_FAILED` for failure-path testing.
- `opengl.windowVisible=true|false` (default `false`) controls visible presentation window.

Vulkan backend options:
- `vulkan.mockContext=true|false` (default `true`) toggles real Vulkan instance initialization.
- `vulkan.forceInitFailure=true|false` forces `BACKEND_INIT_FAILED` for failure-path testing.
- `vulkan.windowVisible=true|false` (default `false`) controls visible presentation window.
- `vulkan.forceDeviceLostOnRender=true|false` forces `DEVICE_LOST` for failure-path testing.
- `vulkan.postOffscreen=true|false` (default `true`) enables dedicated Vulkan post pass (intermediate copy + fullscreen composite) with automatic shader fallback if unavailable.

Sample-host default keeps OpenGL in mock mode for portability. To run real OpenGL init/render from sample host:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args=\"opengl\" -Ddle.opengl.mockContext=false
```

## Current status

DynamicLightEngine now provides real baseline rendering in both OpenGL and Vulkan backends behind the same API/SPI contracts.

Vulkan now includes:
- attribute-rich mesh ingestion (`POSITION`, `NORMAL`, `TEXCOORD_0`, `TANGENT`) with `.gltf/.glb` parsing
- descriptor-backed camera/material/lighting data
- 3-frames-in-flight global uniform + descriptor-set ring path
- persistently mapped Vulkan staging memory for frame-uniform uploads
- fog/smoke quality-tier behavior and degradation warnings aligned with OpenGL warning semantics
- multi-frame-in-flight command/sync model and device-local mesh buffers uploaded via staging transfers

Cross-backend parity tests now cover:
- lifecycle/error parity
- material/lighting scene behavior parity signals
- repeated resize stability
- quality-tier fog/smoke degradation warning parity
- tonemap-enabled post-process parity profile (`post-process`)

Post-processing status:
- scene-level `PostProcessDesc` is supported on OpenGL and Vulkan.
- OpenGL now uses a dedicated post pass (offscreen FBO color target + fullscreen post shader) with shader-driven fallback safety.
- Vulkan now runs a dedicated post pass when available and surfaces explicit `VULKAN_POST_PROCESS_PIPELINE` diagnostics (including fallback mode when needed).

OpenGL includes a fog path driven by `SceneDescriptor.fog` with quality-tier dependent sampling:
- `LOW`: coarse fog steps
- `MEDIUM/HIGH`: progressively smoother fog
- `ULTRA`: unquantized fog factor

OpenGL also consumes `SceneDescriptor.smokeEmitters` with a screen-space smoke blend.
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
- `-Ddle.test.vulkan.real=true` enables guarded real-Vulkan integration tests (init/reuse/reorder/resize-endurance/device-loss paths). Tests skip automatically if LWJGL native runtime prerequisites are unavailable.
- `-Ddle.compare.tests=true` enables compare-harness image diff integration tests.
- `-Ddle.compare.opengl.mockContext=true|false`, `-Ddle.compare.vulkan.mockContext=true|false`, and
  `-Ddle.compare.vulkan.postOffscreen=true|false` control compare-harness backend modes.

## Planning

- Rendering roadmap (2026): `docs/rendering-roadmap-2026.md`
- Capabilities compendium: `docs/capabilities-compendium.md`
- API reference: `docs/api-reference.md`
- Release workflow: `docs/release-workflow.md`
- Milestone and issue backlog: `docs/github-milestones.md`
- Architecture note: `docs/architecture/backend-strategy.md`
- ADR: `docs/adr/0001-backend-strategy.md`
