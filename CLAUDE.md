# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Requires **JDK 25** (`maven.compiler.release=25` enforced by Maven Enforcer plugin).

```bash
# Compile all modules
mvn clean compile

# Run all tests
mvn test

# Build a single backend and its dependencies
mvn -pl engine-impl-opengl -am compile
mvn -pl engine-impl-vulkan -am compile

# Run sample host (default: opengl backend)
mvn -DskipTests install && mvn -f engine-host-sample/pom.xml exec:java

# Run sample host with a specific backend and options
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --tier=ultra --shadow=on --shadow-cascades=4"

# Run a single test class
mvn -pl engine-impl-opengl -am test -Dtest=OpenGlEngineRuntimeLifecycleTest

# Run a single test method
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTieredGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false

# Run compare-harness parity tests (cross-backend image diff)
mvn -pl engine-host-sample -am test -Ddle.compare.tests=true -Dtest=BackendParityIntegrationTest

# Run real-device Vulkan integration tests
mvn -pl engine-impl-vulkan -am test -Ddle.test.vulkan.real=true -Dtest=VulkanEngineRuntimeIntegrationTest

# JDK 25 wrapper (if JAVA_HOME is stale)
./scripts/mvnw25 test
```

### Guarded Test Flags (system properties)

| Flag | Purpose |
|---|---|
| `-Ddle.test.vulkan.real=true` | Real-device Vulkan integration tests |
| `-Ddle.test.vulkan.real.long=true` | Long endurance Vulkan tests |
| `-Ddle.compare.tests=true` | Compare-harness image diff integration tests |
| `-Ddle.test.resource.watch=true` | Resource watcher auto-reload tests |

### Compare Harness Scripts

```bash
# Standard rebaseline (macOS)
./scripts/aa_rebaseline_real_mac.sh

# Real Vulkan compare
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh

# Preflight check before real Vulkan runs
./scripts/aa_rebaseline_real_mac.sh preflight

# Lock thresholds from sampled runs
./scripts/aa_rebaseline_real_mac.sh lock-thresholds artifacts/compare

# Long-run motion stability
./scripts/aa_rebaseline_real_mac.sh longrun-motion

# Vendor upscaler matrix
./scripts/aa_rebaseline_real_mac.sh upscaler-matrix
```

Key env vars: `DLE_COMPARE_VULKAN_MODE=mock|auto|real`, `DLE_COMPARE_OUTPUT_DIR`, `DLE_COMPARE_TEST_CLASS`, `DLE_COMPARE_UPSCALER_MODE=none|fsr|xess|dlss`.

## Architecture

DynamisLightEngine is a multi-module Maven rendering engine with a strict layered architecture:

```
engine-api          Pure Java contracts (DTOs, interfaces). No JavaFX, no native types.
    ↑
engine-spi          Backend discovery via ServiceLoader (EngineBackendProvider)
    ↑
engine-impl-common  Shared base: AbstractEngineRuntime (lifecycle state machine,
                    resource cache, hot-reload, event dispatch), FrameGraph, ShadowAtlasPlanner
    ↑
engine-impl-opengl  OpenGL backend (LWJGL 3.3.6: glfw + opengl + stb)
engine-impl-vulkan  Vulkan backend (LWJGL 3.3.6: vulkan + glfw + shaderc + stb)
    ↑
engine-bridge-dynamisfx   Host bridge mapping FX models → engine API DTOs
engine-host-sample        Minimal console host + BackendCompareHarness
```

**Hard boundary:** No OpenGL/Vulkan handles or JavaFX types may cross `engine-api`. Backend-native objects must stay in backend modules. Cross-backend policies (error mapping, lifecycle semantics, log categories) belong in `engine-impl-common`.

### Runtime Lifecycle

`EngineRuntime` state machine: `NEW → INITIALIZED → SHUTDOWN`. Hosts call `initialize(config, callbacks)` once, then `loadScene()` / `update(dt, input)` / `render()` in a loop, then `shutdown()`. Single engine thread contract; no callback reentrancy. Callbacks (`onEvent`, `onLog`, `onError`) must be non-blocking and must not call runtime methods synchronously.

### Backend Selection & Discovery

Backends register via `META-INF/services/org.dynamislight.spi.EngineBackendProvider`. `BackendRegistry` discovers them with `ServiceLoader`. Each backend supports mock context mode for headless CI (`opengl.mockContext=true`, `vulkan.mockContext=true` via `EngineConfig.backendOptions`). API version compatibility: major must match, runtime minor >= host required minor.

### Vulkan Module Organization

The Vulkan backend is heavily decomposed into subpackages under `org.dynamislight.impl.vulkan`: `asset`, `bootstrap`, `command`, `descriptor`, `lifecycle`, `math`, `memory`, `model`, `pipeline`, `profile`, `scene`, `shader`, `shadow`, `state`, `swapchain`, `texture`, `uniform`. Design rule: keep render orchestration/state transitions in `VulkanContext`; data carriers/parsers go in subpackages. New Vulkan features should default to subpackage classes first. The OpenGL backend is flat (single package).

### Key Subsystems

- **FrameGraph** (`engine-impl-common`): immutable pass-based render graph with dependency ordering (`clear → geometry → fog → smoke → post`)
- **ShadowAtlasPlanner** (`engine-impl-common`): power-of-two atlas packing with descending-size shelf placement and stable reuse/eviction
- **Resource Management** (`AbstractEngineRuntime`): in-memory cache with SHA-256 hot-reload, virtual-thread filesystem watcher, LRU eviction under configurable max entries (default 256)
- **AA modes**: TAA, TSR, TUUA, MSAA_SELECTIVE, HYBRID_TUUA_MSAA, DLAA, FXAA_LOW. Scene-level control via `AntiAliasingDesc` on `PostProcessDesc`, or backend options (`<backend>.aaPreset`, `<backend>.aaMode`)
- **Upscaler bridge**: FSR/XeSS/DLSS via `ExternalUpscalerBridge` with optional native bridge path
- **Reflection modes**: IBL_ONLY, SSR, PLANAR, HYBRID, RT_HYBRID
- **Post-process pipeline**: dedicated offscreen FBO → fullscreen post shader (tonemap, bloom, SSAO, SMAA, TAA) with automatic fallback
- **Quality tiers**: `LOW` / `MEDIUM` / `HIGH` / `ULTRA` — affects fog sampling, smoke quality, shadow budgets, AA neighborhood clipping, IBL quality, SSAO kernel, and per-tier warning emission

### Backend Config Options

Configured through `EngineConfig.backendOptions` map. Key options:

- `opengl.mockContext` / `vulkan.mockContext` — headless CI mode
- `opengl.windowVisible` / `vulkan.windowVisible` — show GLFW window
- `vulkan.postOffscreen` — dedicated post-process pass (default `true`)
- `vulkan.framesInFlight` — multi-frame-in-flight count (default `3`, range `2..6`)
- `<backend>.aaPreset` — `performance|balanced|quality|stability`
- `<backend>.aaMode` — AA algorithm selection
- `resource.watch.enabled` / `resource.cache.maxEntries` — resource system tuning

Full option catalog: `docs/capabilities-compendium.md` §10.

## Documentation Map

The `docs/` folder contains extensive per-feature documentation:

| Path | Content |
|---|---|
| `docs/architecture/backend-strategy.md` | Module responsibilities, boundaries, Vulkan package layout, implementation rules |
| `docs/adr/0001-backend-strategy.md` | ADR: LWJGL-first with engine abstraction rationale |
| `docs/api-reference.md` | Full `engine-api` reference (lifecycle, callbacks, scene model, errors, resources, validation) |
| `docs/capabilities-compendium.md` | Exhaustive "what works now" matrix — all implemented features, backend options, test confidence areas |
| `docs/rendering-roadmap-2026.md` | Active rendering phases (visual quality, lighting/shadow, runtime hardening) |
| `docs/release-workflow.md` | SemVer tagging, release checklist, local snapshot publish |
| `docs/guides/` | Per-feature usage guides (AA, shadows, camera, fog/smoke, materials/lighting, post-process, IBL, reflections, resources, scene assembly, quality tiers, compare harness, backend selection, runtime events) |
| `docs/testing/` | Per-feature test design docs mirroring guides (strategies, coverage, commands, pass/fail criteria) |
| `docs/guides/feature-guide-template.md` | Template for new feature guides |
| `docs/testing/feature-testing-template.md` | Template for new feature test plans |

When implementing a new feature, check the relevant guide and testing doc for existing patterns, validation rules, and expected warning signals.

## Coding Conventions

- 4-space indentation, UTF-8 sources
- DTOs as immutable Java `record` types with defensive copying (`List.copyOf`, `Map.copyOf`)
- Classes/records/interfaces: `PascalCase`; methods/fields: `camelCase`; enums/constants: `UPPER_SNAKE_CASE`
- Packages: `org.dynamislight.*` (all lowercase)
- Tests mirror main package structure, named by behavior (e.g., `OpenGlEngineRuntimeLifecycleTest`)
- Conventional Commits: `feat(api):`, `fix(opengl):`, `test(spi):`, etc.
- PRs must include: concise problem/solution summary, linked issue/task ID, verification commands run, logs/screenshots when behavior changes are visible

## Testing Requirements for New Features

- Lifecycle state transitions: `initialize`, `loadScene`, `render`, `shutdown`
- Invalid state/argument error paths via `EngineException`/`EngineErrorCode`
- ServiceLoader backend discovery
- Cross-backend parity: both OpenGL and Vulkan should produce comparable results; use compare-harness profiles with bounded diff thresholds
- Quality-tier behavior: features should respect tier policy and emit appropriate degradation warnings (e.g., `SHADOW_QUALITY_DEGRADED`, `IBL_QUALITY_DEGRADED`, `SMOKE_QUALITY_DEGRADED`)
- New feature guides and test plans should follow `docs/guides/feature-guide-template.md` and `docs/testing/feature-testing-template.md`
