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
| OpenGL backend (`engine-impl-opengl`) | Implemented (advanced baseline) | Real context/shaders, scene meshes, fog/smoke, material/texture + PBR-leaning shading baseline |
| Vulkan backend (`engine-impl-vulkan`) | Implemented (advanced baseline) | Real Vulkan init, swapchain/pipeline, attribute-rich mesh path, descriptor-backed camera/material/light uniforms, multi-frame sync + staging uploads |
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
- Scene-level post-process DTO now available:
  - `PostProcessDesc` on `SceneDescriptor` (tonemap + bloom controls)

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
  - metallic-roughness texture modulation (`G`/`B` channels)
  - occlusion texture modulation (`R` channel)
- Lighting baseline:
  - directional + point light uniforms
  - diffuse + specular-style response
  - per-material roughness/metallic modulation
  - typed light selection baseline (`LightType`: directional/point/spot) with cone-attenuated spot-light shading path
  - directional + spot + point shadow path support (point via cubemap depth sampling baseline)
  - shadow fidelity tuning: depth bias scales with effective PCF radius/cascade count to reduce acne/flicker under higher shadow quality settings
- IBL baseline hook:
  - environment-driven enablement via `EnvironmentDesc` IBL asset paths
  - lightweight diffuse/specular ambient contribution (`IBL_BASELINE_ACTIVE` warning signal)
  - shader-side IBL texture sampling path (irradiance/radiance/BRDF-LUT samplers in render shader)
- roughness-aware radiance prefilter approximation (tier-driven strength) with `IBL_PREFILTER_APPROX_ACTIVE` warning signal
- roughness-aware multi-tap IBL specular radiance filtering with runtime signal `IBL_MULTI_TAP_SPEC_ACTIVE`
- roughness-driven mip/LOD prefilter sampling path for IBL radiance with runtime signal `IBL_MIP_LOD_PREFILTER_ACTIVE`
- BRDF energy-compensation + horizon-weighted IBL response with runtime signal `IBL_BRDF_ENERGY_COMP_ACTIVE`
- deeper roughness/BRDF integration: prefilter weighting and BRDF-LUT shaping tuned to improve energy balance on glossy vs rough materials
- view-space camera-direction IBL response (replaces fixed forward-view assumption for specular reflection)
  - explicit LOW/MEDIUM tier quality policy with attenuation + warning: `IBL_QUALITY_DEGRADED`
  - texture ingestion now supports `png/jpg/jpeg` and `.hdr` fallback paths
  - texture-driven calibration path (`png/jpg/jpeg/.hdr` luminance sampling on scene load)
  - when irradiance/radiance IBL paths are absent, runtime can derive those inputs from `EnvironmentDesc.skyboxAssetPath`
  - explicit runtime signal when skybox-derived IBL inputs are active: `IBL_SKYBOX_DERIVED_ACTIVE`
  - when KTX irradiance/radiance assets are configured but unresolved, runtime can fallback those channels to skybox-derived inputs (`IBL_KTX_SKYBOX_FALLBACK_ACTIVE`)
  - `.ktx/.ktx2` IBL paths now resolve through sidecar decode paths when available (`.png/.hdr/.jpg/.jpeg`)
  - baseline native container decode path added for uncompressed KTX/KTX2 channel families (`R`, `RG`, `RGB`, `RGBA`, `BGRA`) (resolved to runtime PNG cache when needed)
  - native KTX2 zlib supercompression decode is supported for baseline decodable channel families
  - native KTX2 Zstd supercompression decode is supported for baseline decodable channel families
  - native KTX2 BasisLZ/UASTC transcode path is supported via `libktx` (`ktxTexture2_TranscodeBasis -> RGBA32`) for direct runtime ingestion
  - baseline uncompressed 16-bit KTX2 families are supported (`R16_UNORM`, `R16G16_UNORM`, `R16G16B16A16_UNORM`) via 16-bit-to-8-bit normalization for runtime ingestion
  - direct backend texture ingestion now decodes supported KTX/KTX2 payloads in-memory via raw RGBA extraction (no PNG transcode dependency for GPU upload)
  - backend texture ingestion now prefers native KTX/KTX2 decode first; sidecar assets are used only as fallback when native decode is unavailable
  - explicit runtime warning when KTX container paths are requested: `IBL_KTX_CONTAINER_FALLBACK`
  - explicit runtime warning when KTX containers exist but remain undecodable in current build: `IBL_KTX_DECODE_UNAVAILABLE`
  - explicit runtime warning when KTX2 assets require BasisLZ/UASTC transcoding that is not yet enabled: `IBL_KTX_TRANSCODE_REQUIRED`
  - explicit runtime warning when KTX variants are outside baseline decoder support (compressed/supercompressed/non-RGBA8): `IBL_KTX_VARIANT_UNSUPPORTED`
  - explicit runtime warning when configured IBL assets are missing/unreadable: `IBL_ASSET_FALLBACK_ACTIVE`
  - OpenGL/Vulkan parity update: AO now modulates IBL diffuse ambient in both backends for closer cross-backend material response
- Fog support (`FogDesc`) with quality-tier behavior.
- Smoke support (`SmokeEmitterDesc`) with quality degradation warnings at lower tiers.
- Smoke radial falloff now uses runtime viewport dimensions (instead of fixed 1080p constants) for stronger OpenGL/Vulkan parity across window sizes.
- Tonemap + bloom post-process baseline (scene-driven exposure/gamma/threshold/strength).
- Dedicated post-pass architecture:
  - offscreen scene target (FBO color + depth/stencil) then fullscreen post shader composite
  - shader-driven post remains as fallback if offscreen resources are unavailable
- Frame graph execution path (`clear -> geometry -> fog -> smoke -> post`).
- GPU timing query when available (`GL_TIME_ELAPSED`) with CPU fallback.
- Approximate GPU memory telemetry exposed via runtime stats.

### OpenGL limitations (current)
- Material model is intentionally simplified (not full PBR correctness).
- Fog/smoke are integrated in shader path, not separate volumetric passes.
- glTF support is pragmatic baseline, not full spec coverage.

## 7) Vulkan backend capabilities
Vulkan backend provides a real rendering bootstrap and advanced baseline draw flow:
- GLFW-backed Vulkan surface/window initialization.
- Physical/logical device selection with graphics+present+swapchain checks.
- Swapchain creation/recreation + image views + render pass + framebuffers.
- Multi-frame-in-flight command/sync setup (default `3`, configurable via backend options).
- Shader compilation at runtime via `shaderc` (GLSL -> SPIR-V).
- Graphics pipeline + pipeline layout creation.
- Descriptor set path with uniform buffer binding for model/view/proj + material + lighting + fog/smoke parameters.
- Per-frame descriptor-set ring for global scene uniforms (frame-indexed binding path).
- Revision-aware per-frame uniform staging for dynamic updates:
  - frame slots track applied global/scene uniform revisions
  - global-state revision marks are value-aware (no revision bump for unchanged state payloads)
  - uniform copy/barrier is skipped when a frame slot is already synchronized
  - dirty object subranges are uploaded as sparse multi-range copies for dynamic-only scene changes
- Per-mesh sampled textures for albedo, normal, metallic-roughness, and occlusion.
- Attribute-rich vertex path (`position`, `normal`, `uv`, `tangent`) from `.gltf/.glb` mesh ingestion.
- GGX-style PBR-leaning lighting response aligned with OpenGL baseline (directional + point light path).
- Typed light selection baseline (`LightType`: directional/point/spot) with cone-attenuated spot-light shading path.
- Directional + spot + point shadow path support in baseline form (OpenGL cubemap baseline; Vulkan 6-face layered point-shadow path aligned to cubemap face directions).
 - shadow fidelity tuning: depth bias scales with effective PCF radius/cascade count to reduce acne/flicker under higher shadow quality settings.
- Adaptive point-shadow filtering/bias behavior in both backends (PCF kernel scales with configured radius and depth-from-light to reduce near/far acne and shimmer).
- IBL baseline hook:
  - environment-driven enablement via `EnvironmentDesc` IBL asset paths
  - lightweight diffuse/specular ambient contribution (`IBL_BASELINE_ACTIVE` warning signal)
  - shader-side IBL texture sampling path (irradiance/radiance/BRDF-LUT samplers in render shader)
  - roughness-aware radiance prefilter approximation (tier-driven strength) with `IBL_PREFILTER_APPROX_ACTIVE` warning signal
  - roughness-aware multi-tap IBL specular radiance filtering with runtime signal `IBL_MULTI_TAP_SPEC_ACTIVE`
  - roughness-driven mip/LOD prefilter sampling path for IBL radiance with runtime signal `IBL_MIP_LOD_PREFILTER_ACTIVE`
  - BRDF energy-compensation + horizon-weighted IBL response with runtime signal `IBL_BRDF_ENERGY_COMP_ACTIVE`
  - deeper roughness/BRDF integration: prefilter weighting and BRDF-LUT shaping tuned to improve energy balance on glossy vs rough materials
  - view-space camera-direction IBL response (replaces fixed forward-view assumption for specular reflection)
  - explicit LOW/MEDIUM tier quality policy with attenuation + warning: `IBL_QUALITY_DEGRADED`
  - texture ingestion now supports `png/jpg/jpeg` and `.hdr` fallback paths
  - texture-driven calibration path (`png/jpg/jpeg/.hdr` luminance sampling on scene load)
  - when irradiance/radiance IBL paths are absent, runtime can derive those inputs from `EnvironmentDesc.skyboxAssetPath`
  - explicit runtime signal when skybox-derived IBL inputs are active: `IBL_SKYBOX_DERIVED_ACTIVE`
  - when KTX irradiance/radiance assets are configured but unresolved, runtime can fallback those channels to skybox-derived inputs (`IBL_KTX_SKYBOX_FALLBACK_ACTIVE`)
  - `.ktx/.ktx2` IBL paths now resolve through sidecar decode paths when available (`.png/.hdr/.jpg/.jpeg`)
  - baseline native container decode path added for uncompressed KTX/KTX2 channel families (`R`, `RG`, `RGB`, `RGBA`, `BGRA`) (resolved to runtime PNG cache when needed)
  - native KTX2 zlib supercompression decode is supported for baseline decodable channel families
  - native KTX2 Zstd supercompression decode is supported for baseline decodable channel families
  - native KTX2 BasisLZ/UASTC transcode path is supported via `libktx` (`ktxTexture2_TranscodeBasis -> RGBA32`) for direct runtime ingestion
  - baseline uncompressed 16-bit KTX2 families are supported (`R16_UNORM`, `R16G16_UNORM`, `R16G16B16A16_UNORM`) via 16-bit-to-8-bit normalization for runtime ingestion
  - direct backend texture ingestion now decodes supported KTX/KTX2 payloads in-memory via raw RGBA extraction (no PNG transcode dependency for GPU upload)
  - backend texture ingestion now prefers native KTX/KTX2 decode first; sidecar assets are used only as fallback when native decode is unavailable
  - explicit runtime warning when KTX container paths are requested: `IBL_KTX_CONTAINER_FALLBACK`
  - explicit runtime warning when KTX containers exist but remain undecodable in current build: `IBL_KTX_DECODE_UNAVAILABLE`
  - explicit runtime warning when KTX2 assets require BasisLZ/UASTC transcoding that is not yet enabled: `IBL_KTX_TRANSCODE_REQUIRED`
  - explicit runtime warning when KTX variants are outside baseline decoder support (compressed/supercompressed/non-RGBA8): `IBL_KTX_VARIANT_UNSUPPORTED`
  - explicit runtime warning when configured IBL assets are missing/unreadable: `IBL_ASSET_FALLBACK_ACTIVE`
- Device-local vertex/index buffer uploads via staging copy path.
- Render loop clear + scene-driven indexed draws with quality-tier-dependent fog/smoke behavior.
- Smoke radial falloff now uses runtime swapchain viewport dimensions (instead of fixed 1080p constants) for stronger OpenGL/Vulkan parity across window sizes.
- Dedicated post-process pass path:
  - Vulkan can run a dedicated post composite pass (`vulkan.postOffscreen=true`) using an intermediate sampled scene image.
  - Runtime automatically falls back to shader-driven post if post resources are unavailable.
  - `VULKAN_POST_PROCESS_PIPELINE` warning/profile details report requested vs active mode.
- Resize/out-of-date/suboptimal handling with swapchain recreation.
- Device-loss error mapping and `DeviceLostEvent` propagation.
- Forced device-loss test path (`vulkan.forceDeviceLostOnRender=true`) validated in both mock and real-context runtime paths.
- Approximate GPU memory telemetry exposed via runtime stats.

### Vulkan limitations (current)
- glTF support is pragmatic baseline, not full spec coverage.
- Feature set remains intentionally lean compared to production Vulkan engines (no full deferred path, no advanced framegraph composition, etc.).

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
  - quality-tier selection (`--tier=...`)
  - shadow tuning flags (`--shadow`, `--shadow-cascades`, `--shadow-pcf`, `--shadow-bias`, `--shadow-res`)
  - post-process tuning flags (`--post`, `--tonemap`, `--exposure`, `--gamma`, `--bloom`, `--bloom-threshold`, `--bloom-strength`)
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

Vulkan runtime emits profiling warnings (real-context mode) for:
- `SCENE_REUSE_PROFILE`
  - includes `textureRebindHits` to track texture-only scene updates that reused geometry buffers
- `VULKAN_FRAME_RESOURCE_PROFILE`
- `SHADOW_CASCADE_PROFILE`
- `DESCRIPTOR_RING_WASTE_HIGH` (when descriptor-ring waste ratio stays above configured threshold for configured consecutive frames)
- `DESCRIPTOR_RING_CAP_PRESSURE` (when descriptor-ring cap bypass count reaches configured threshold for configured consecutive frames)
- `PENDING_UPLOAD_RANGE_SOFT_LIMIT_EXCEEDED` (when dynamic sparse-upload range count exceeds configured soft limit)

`VULKAN_FRAME_RESOURCE_PROFILE` now includes per-frame ring diagnostics:
- `framesInFlight`
- `descriptorSetsInRing`
- `uniformStrideBytes` / `uniformFrameSpanBytes` / `globalUniformFrameSpanBytes`
- `dynamicSceneCapacity` / `pendingUploadRangeCapacity`
- `lastGlobalUploadBytes` / `maxGlobalUploadBytes`
- `lastUniformUploadBytes` / `maxUniformUploadBytes`
- `lastUniformObjectCount` / `maxUniformObjectCount`
- `lastUniformUploadRanges` / `maxUniformUploadRanges`
- `lastUniformUploadStartObject`
- `pendingRangeOverflows`
- `descriptorRingSetCapacity` / `descriptorRingPeakSetCapacity`
- `descriptorRingActiveSetCount` / `descriptorRingWasteSetCount` / `descriptorRingPeakWasteSetCount`
- `descriptorRingMaxSetCapacity` / `descriptorRingCapBypasses`
- `descriptorRingReuseHits` / `descriptorRingGrowthRebuilds` / `descriptorRingSteadyRebuilds`
- `descriptorRingPoolReuses` / `descriptorRingPoolResetFailures`
- `descriptorRingWasteWarnCooldownRemaining` / `descriptorRingCapPressureWarnCooldownRemaining`
- `persistentStagingMapped`

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
- `vulkan.postOffscreen` (default `true`, dedicated pass with automatic fallback)
- `vulkan.framesInFlight` (default `3`, clamped `2..6`)
- `vulkan.maxDynamicSceneObjects` (default `2048`, clamped `256..8192`)
- `vulkan.maxPendingUploadRanges` (default `64`, clamped `8..2048`)
- `vulkan.dynamicUploadMergeGapObjects` (default `1`, clamped `0..32`)
- `vulkan.dynamicObjectSoftLimit` (default `1536`, clamped `128..8192`)
- `vulkan.uniformUploadSoftLimitBytes` (default `2097152`, clamped `4096..67108864`)
- `vulkan.uniformUploadWarnCooldownFrames` (default `120`, clamped `0..10000`)
- `vulkan.pendingUploadRangeSoftLimit` (default `48`, clamped `1..2048`)
- `vulkan.pendingUploadRangeWarnCooldownFrames` (default `120`, clamped `0..10000`)
- `vulkan.descriptorRingActiveSoftLimit` (default `2048`, clamped `64..32768`)
- `vulkan.descriptorRingActiveWarnCooldownFrames` (default `120`, clamped `0..10000`)
- `vulkan.maxTextureDescriptorSets` (default `4096`, clamped `256..32768`)
- `vulkan.meshGeometryCacheEntries` (default `256`, clamped `16..4096`)
- `vulkan.descriptorRingWasteWarnRatio` (default `0.85`, clamped `0.1..0.99`)
- `vulkan.descriptorRingWasteWarnMinFrames` (default `8`, clamped `1..600`)
- `vulkan.descriptorRingWasteWarnMinCapacity` (default `64`, clamped `1..65536`)
- `vulkan.descriptorRingWasteWarnCooldownFrames` (default `120`, clamped `0..10000`)
- `vulkan.descriptorRingCapPressureWarnMinBypasses` (default `4`, clamped `1..1000000`)
- `vulkan.descriptorRingCapPressureWarnMinFrames` (default `2`, clamped `1..600`)
- `vulkan.descriptorRingCapPressureWarnCooldownFrames` (default `120`, clamped `0..10000`)

## 11) Test-backed confidence areas
The repository includes automated tests validating:
- API validators and version compatibility behavior.
- SPI discovery and backend resolution failure modes.
- OpenGL lifecycle/error/resource/hot-reload behavior.
- Vulkan lifecycle, initialization guards, workload stats parity, and device-loss propagation.
- Guarded real-device Vulkan endurance integration (`-Ddle.test.vulkan.real=true`) covering repeated resize + scene-switch loops with frame-resource profile assertions.
- CI now runs guarded real-device Vulkan suite invocation on macOS/Linux/Windows; environments without native/runtime support skip via guards rather than failing the pipeline.
- Guarded real-device Vulkan integration now includes a forced device-loss error-path test.
- Real-device guarded tests auto-skip when LWJGL native runtime prerequisites are unavailable (instead of hard-failing test runs).
- Cross-backend parity checks in sample host integration tests (material/lighting scene, resize stability, quality-tier warning parity).
- Guarded compare-harness image diff checks (`-Ddle.compare.tests=true`) including tiered fog/smoke/shadow thresholds.
- Guarded compare-harness includes `shadow-cascade-stress`, `fog-shadow-cascade-stress`, `smoke-shadow-cascade-stress`, and `texture-heavy` profiles for deeper split/bias/fog/smoke/material interaction regression coverage.
- Guarded compare-harness includes `brdf-tier-extremes` for glossy/rough material edge-case parity coverage.
- Guarded compare-harness includes `post-process` and `post-process-bloom` profiles.
- Guarded compare-harness includes `fog-smoke-shadow-post-stress` for combined volumetric+shadow+post regression coverage.
- Guarded compare-harness includes `material-fog-smoke-shadow-cascade-stress` for mixed material+fog+smoke+cascaded-shadow coverage.
- Tiered golden envelopes also include `texture-heavy` (`LOW/MEDIUM/HIGH/ULTRA`) alongside existing fog/smoke and shadow tier checks.
- Tiered golden envelopes also include `brdf-tier-extremes` (`LOW/MEDIUM/HIGH/ULTRA`).
- Tiered golden envelopes now include `post-process` and `post-process-bloom` (`LOW/MEDIUM/HIGH/ULTRA`).
- Tiered golden envelopes now include `material-fog-smoke-shadow-cascade-stress` (`LOW/MEDIUM/HIGH/ULTRA`).
- Current ULTRA `shadow-cascade-stress` bound: `<= 0.25`.
- Current ULTRA `fog-shadow-cascade-stress` bound: `<= 0.25`.
- Current ULTRA `smoke-shadow-cascade-stress` bound: `<= 0.25`.
- Current ULTRA `fog-smoke-shadow-post-stress` bound: `<= 0.05`.
- Current ULTRA `material-fog-smoke-shadow-cascade-stress` bound: `<= 0.30`.
- Current ULTRA `brdf-tier-extremes` bound: `<= 0.29`.
- Current HIGH `post-process` bound: `<= 0.32`.
- Current HIGH `post-process-bloom` bound: `<= 0.06`.
- Vulkan frame-resource profile now also reports:
  - split uniform staging path: global scene UBO uploads are tracked separately from dynamic object UBO uploads
  - `lastUniformUploadRanges`
  - `maxUniformUploadRanges`
  - `lastUniformUploadStartObject`
  - `dynamicUploadMergeGapObjects`
  - `dynamicObjectSoftLimit`
  - `maxObservedDynamicObjects`
- Vulkan runtime now emits mesh-loader cache telemetry warning:
  - `MESH_GEOMETRY_CACHE_PROFILE` (`hits`, `misses`, `evictions`, `entries`, `maxEntries`)
- Practical floor note: attempted `fog-smoke-shadow-post-stress <= 0.04` failed (`diff=0.04049019607843137`), so stress envelopes are frozen at `0.05` / `0.06`.
- Compare harness backend toggles:
  - `dle.compare.opengl.mockContext`
  - `dle.compare.vulkan.mockContext`
  - `dle.compare.vulkan.postOffscreen`

## 12) Platform and CI coverage
- Backend modules include LWJGL runtime natives for macOS (arm64), Linux, and Windows.
- GitHub Actions CI runs test matrix on:
  - `ubuntu-latest`
  - `macos-latest`
  - `windows-latest`
- CI also runs a dedicated guarded parity compare job on `ubuntu-latest` with `dle.compare.tests=true`.
- CI also runs a dedicated guarded long-endurance Vulkan real test matrix on macOS/Linux/Windows (guarded by runtime availability).
