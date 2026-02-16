# DynamicLightEngine Capabilities Compendium

Last updated: February 16, 2026

## 1) Scope and intent
This document describes **implemented capabilities** in the current repository state across API, SPI, runtimes, bridge, and sample host. It is a practical “what works now” reference, not a roadmap.

## 2) Capability matrix (current)

| Area | Status | Notes |
|---|---|---|
| Engine API contract (`engine-api`) | Implemented | Stable Java DTO boundary, lifecycle/runtime contracts, validation, error model |
| Backend discovery (`engine-spi`) | Implemented | `ServiceLoader` + deterministic `BackendRegistry.resolve(...)` |
| Shared runtime policies (`engine-impl-common`) | Implemented | Lifecycle enforcement, stats, logging/events/errors, resource cache/hot-reload/watch |
| OpenGL backend (`engine-impl-opengl`) | Implemented (baseline real rendering) | Real context/shaders, scene meshes, fog/smoke, material/texture + baseline lighting |
| Vulkan backend (`engine-impl-vulkan`) | Implemented (baseline real rendering) | Real Vulkan init, swapchain/pipeline, indexed draws, descriptor-backed uniform path |
| DynamisFX bridge (`engine-bridge-dynamisfx`) | Implemented (v1 baseline) | Runtime creation, input mapping, scene mapping + validation |
| Sample host (`engine-host-sample`) | Implemented | Runs lifecycle loop, backend select, resource inspection/hot-reload demo |

## 3) Core runtime/API capabilities
- API versioning via `EngineApiVersion` and compatibility checks (`major` must match, runtime `minor >= host required minor`).
- Strict lifecycle enforcement:
  - `initialize()` exactly once before runtime calls.
  - `loadScene()`, `update()`, `render()`, `resize()` require initialized state.
  - `shutdown()` is idempotent.
- Threading/reentrancy contract documented on `EngineRuntime` and `EngineHostCallbacks`.
- Runtime stats exposed each frame (`fps`, CPU/GPU frame ms, draw calls, triangles, visible objects, GPU memory bytes).
- Error model implemented via `EngineException` + `EngineErrorCode` with recoverable flag propagation.
- Host callback channels implemented:
  - `onEvent(EngineEvent)`
  - `onLog(LogMessage)`
  - `onError(EngineErrorReport)`

## 4) Scene ingestion and validation
- `EngineConfigValidator` validates required backend/app/dimensions/dpi/fps/quality/asset root.
- `SceneValidator` validates scene name, unique IDs, active camera references, mesh transform/material references.
- Bridge `SceneMapper` remaps and validates scene payloads before runtime usage.

## 5) Resource system (runtime-integrated)
Resource functionality is provided through `EngineRuntime.resources()`:
- Acquire/release/reload APIs via `EngineResourceService`.
- Resource states: `LOADED` / `FAILED` with per-resource metadata:
  - `resolvedPath`, `lastChecksum`, `lastLoadedEpochMs`, `errorMessage`, `refCount`.
- Automatic scene resource tracking on `loadScene()` for:
  - mesh assets (`ResourceType.MESH`)
  - material textures (albedo + normal)
  - environment skybox texture
- Checksum-based reload behavior:
  - emits `ResourceHotReloadedEvent` when reload succeeds
  - distinguishes changed vs unchanged resource content
- Optional filesystem watch mode:
  - virtual-thread watcher
  - recursive directory registration under `assetRoot`
  - debounce and retry support
- Cache policy:
  - pressure-based max entry count (`resource.cache.maxEntries`)
  - evicts oldest zero-ref resources first
  - no TTL in v1
- Resource telemetry (`ResourceCacheStats`): hits, misses, reload requests/failures, evictions, watcher events.

## 6) OpenGL backend capabilities
OpenGL backend provides a real forward render baseline:
- Native context + shader pipeline init (with explicit failure mapping).
- Scene mesh rendering with per-mesh model transform + camera matrices.
- glTF/glb parsing baseline with fallback geometry heuristics.
- Material support:
  - albedo color
  - metallic + roughness inputs
  - albedo texture sampling
  - normal texture sampling (baseline influence)
- Lighting baseline:
  - directional + point light uniforms
  - diffuse + specular-style response
  - per-material roughness/metallic modulation
- Fog support (`FogDesc`) with quality-tier behavior.
- Smoke support (`SmokeEmitterDesc`) with quality degradation warnings at lower tiers.
- Frame graph execution path (`clear -> geometry -> fog -> smoke`).
- GPU timing query when available (`GL_TIME_ELAPSED`) with CPU fallback.

### OpenGL limitations (current)
- Material model is intentionally simplified (not full PBR correctness).
- Fog/smoke are integrated in shader path, not separate volumetric passes.
- glTF support is pragmatic baseline, not full spec coverage.

## 7) Vulkan backend capabilities
Vulkan backend provides a real rendering bootstrap and baseline draw flow:
- GLFW-backed Vulkan surface/window initialization.
- Physical/logical device selection with graphics+present+swapchain checks.
- Swapchain creation/recreation + image views + render pass + framebuffers.
- Command pool/buffer setup + fence/semaphore synchronization.
- Shader compilation at runtime via `shaderc` (GLSL -> SPIR-V).
- Graphics pipeline + pipeline layout creation.
- Descriptor set path with uniform buffer binding.
- Vertex/index buffer upload and indexed draw submission.
- Render loop clear + scene-driven indexed draws.
- Resize/out-of-date/suboptimal handling with swapchain recreation.
- Device-loss error mapping and `DeviceLostEvent` propagation.

### Vulkan limitations (current)
- Scene geometry source is currently baseline synthesized primitives from scene mesh descriptors (triangle/quad mapping), not full mesh-asset decode parity.
- Uniform path is baseline global data (model/color style block), not full camera/material descriptor architecture yet.
- Feature set is intentionally minimal compared to production Vulkan engines.

## 8) Bridge and host integration capabilities
- `DynamisFxEngineBridge` resolves runtime by backend id through `BackendRegistry`.
- `InputMapper`:
  - maps host key aliases (WASD/arrows/modifiers/etc.) to engine `KeyCode`
  - derives movement/camera-look intents
- `SceneMapper`:
  - maps scene payloads to engine DTOs
  - enforces validation (`SCENE_VALIDATION_FAILED` on bad input)
- Sample host supports:
  - backend selection
  - lifecycle run loop
  - callback logging/event/error output
  - optional resource inspection/hot-reload workflow

## 9) Logging/event/error observability
Implemented runtime log categories in active usage:
- `LIFECYCLE`, `RENDER`, `SCENE`, `SHADER`, `PERF`, `ERROR`

Implemented event classes in active flow:
- `SceneLoadedEvent`
- `SceneLoadFailedEvent`
- `ResourceHotReloadedEvent`
- `DeviceLostEvent`
- `PerformanceWarningEvent` (type exists; backend usage can be expanded)

## 10) Backend/runtime options
Configured through `EngineConfig.backendOptions`.

Shared resource options:
- `resource.watch.enabled` (default `false`)
- `resource.watch.debounceMs` (default `200`)
- `resource.cache.maxEntries` (default `256`)
- `resource.reload.maxRetries` (default `2`)

OpenGL options:
- `opengl.mockContext` (default `false`)
- `opengl.forceInitFailure` (default `false`)
- `opengl.windowVisible` (default `false`)

Vulkan options:
- `vulkan.mockContext` (default `true`)
- `vulkan.forceInitFailure` (default `false`)
- `vulkan.windowVisible` (default `false`)
- `vulkan.forceDeviceLostOnRender` (default `false`)

## 11) Test-backed confidence areas
The repository includes automated tests validating:
- API validators and version compatibility behavior.
- SPI discovery and backend resolution failure modes.
- OpenGL lifecycle/error/resource/hot-reload behavior.
- Vulkan lifecycle, initialization guards, workload stats parity, and device-loss propagation.
- Cross-backend parity checks in sample host integration tests.
