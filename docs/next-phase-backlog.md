# Next-Phase Backlog (Execution Ready)

Primary roadmap: `docs/rendering-roadmap-2026.md`

## Latest Completed Batch (February 2026)
- Tightened parity stress thresholds:
  - `shadow-cascade-stress` `0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30 -> 0.29 -> 0.28 -> 0.27 -> 0.26 -> 0.25`
  - `fog-shadow-cascade-stress` `0.40 -> 0.39 -> 0.38 -> 0.37 -> 0.36 -> 0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30 -> 0.29 -> 0.28 -> 0.27 -> 0.26 -> 0.25`
  - `smoke-shadow-cascade-stress` `0.40 -> 0.39 -> 0.38 -> 0.37 -> 0.36 -> 0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30 -> 0.29 -> 0.28 -> 0.27 -> 0.26 -> 0.25`
  - `fog-smoke-shadow-post-stress` `0.37 -> 0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30 -> 0.29 -> 0.28 -> 0.27 -> 0.26 -> 0.25 -> 0.24 -> 0.23 -> 0.22 -> 0.21 -> 0.20 -> 0.19 -> 0.18 -> 0.17 -> 0.16 -> 0.15 -> 0.14 -> 0.13 -> 0.12 -> 0.11 -> 0.10 -> 0.09 -> 0.08 -> 0.07 -> 0.06 -> 0.05`
  - `post-process-bloom (HIGH)` `0.36 -> 0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30 -> 0.29 -> 0.28 -> 0.27 -> 0.26 -> 0.25 -> 0.24 -> 0.23 -> 0.22 -> 0.21 -> 0.20 -> 0.19 -> 0.18 -> 0.17 -> 0.16 -> 0.15 -> 0.14 -> 0.13 -> 0.12 -> 0.11 -> 0.10 -> 0.09 -> 0.08 -> 0.07 -> 0.06`
  - practical floor detected at attempted `fog-smoke-shadow-post-stress=0.04` (`diff=0.04049019607843137`); envelopes frozen at `0.05` / `0.06`.
- Added tiered `texture-heavy` parity envelopes (`LOW/MEDIUM/HIGH/ULTRA`) in compare-harness tests.
- Added deterministic `material-fog-smoke-shadow-cascade-stress` compare profile:
  - combines textured materials with fog + smoke + cascaded shadows
  - includes tiered envelope assertions (`LOW/MEDIUM/HIGH/ULTRA`)
  - includes ULTRA stress-golden envelope assertion
- Closed an OpenGL/Vulkan IBL parity gap by applying AO modulation to OpenGL IBL diffuse ambient (matching Vulkan behavior).
- Implemented Phase 2 Item 1 baseline hook:
  - environment-driven IBL baseline in OpenGL and Vulkan
  - shader-side IBL texture sampling baseline in OpenGL and Vulkan (irradiance/radiance/BRDF-LUT)
  - roughness-aware radiance prefilter approximation in OpenGL and Vulkan (tier-driven prefilter strength)
  - roughness-aware multi-tap specular radiance filtering in OpenGL and Vulkan (`IBL_MULTI_TAP_SPEC_ACTIVE`)
  - view-space camera-direction IBL response in both backends (replaces fixed forward-view assumption)
  - texture ingestion + calibration support for configured IBL assets (`png/jpg/jpeg/.hdr`)
  - `.ktx/.ktx2` IBL container paths now resolve through sidecar decode sources when available (`.png/.hdr/.jpg/.jpeg`)
  - explicit `IBL_KTX_CONTAINER_FALLBACK` and `IBL_PREFILTER_APPROX_ACTIVE` warnings emitted for IBL runtime mode visibility
  - bridge mapping preserves `EnvironmentDesc` IBL fields and `PostProcessDesc`
  - new backend tests assert `IBL_BASELINE_ACTIVE` signal
  - IBL quality-tier policy tightened in OpenGL + Vulkan:
    - stronger LOW/MEDIUM tier attenuation for diffuse/specular/prefilter strengths
    - explicit runtime warning when tier-driven IBL degradation is active: `IBL_QUALITY_DEGRADED`
    - backend tests now assert `IBL_QUALITY_DEGRADED` on LOW and no degradation warning on ULTRA
- Phase 2 Item 2 baseline started:
  - API light-type expansion added (`LightType`: `DIRECTIONAL`, `POINT`, `SPOT`) with backward-compatible `LightDesc` constructors.
  - OpenGL/Vulkan lighting selection now prefers typed directional + point/spot lights instead of pure list-position assumptions.
  - Spot lights now use cone-attenuated shading (direction + inner/outer cone) in OpenGL and Vulkan.
  - OpenGL shadow-map execution now supports directional + spot + point lights (point via cubemap depth sampling baseline).
  - Vulkan point-light shadows now run through a 6-face layered path aligned to cubemap directions (+X/-X/+Y/-Y/+Z/-Z).
  - Adaptive point-shadow PCF/bias scaling now applied in both backends to improve near/far stability.
  - Backend tests ensure legacy `SPOT_LIGHT_APPROX_ACTIVE` warning remains absent and verify point/spot shadow warning behavior.
- Hardened Vulkan frame-resource architecture:
  - configurable ring sizing (`vulkan.framesInFlight`, default `3`, clamp `2..4`)
  - configurable dynamic-scene uniform capacity (`vulkan.maxDynamicSceneObjects`, default `2048`)
  - configurable pending upload-range capacity (`vulkan.maxPendingUploadRanges`, default `64`)
  - per-frame descriptor-set ring for global uniforms
  - persistent mapped staging for frame-uniform uploads
  - expanded frame-resource telemetry fields
  - revision-aware dynamic uniform staging:
    - per-frame revision tracking for global + scene uniform state
    - skip uniform buffer copy when frame slot is already synchronized
    - sparse multi-range uniform uploads for dynamic-only scene updates using dirty object ranges
    - global-state setters now mark revisions only on effective value changes (reduces redundant uploads under stable scene settings)
    - additional telemetry: upload ranges + start-object index
- Added Vulkan mesh-geometry cache in asset loader:
  - glTF/fallback geometry is cached by stable key
  - loader returns defensive copies to preserve cache integrity
  - reduces repeated parse/geometry-construction churn across scene reloads
  - runtime now exposes cache behavior via `MESH_GEOMETRY_CACHE_PROFILE` warning (`hits/misses/evictions/entries/maxEntries`)
  - cache capacity is configurable via `vulkan.meshGeometryCacheEntries` (default `256`)
- Expanded guarded real-Vulkan suite:
  - longer resize/scene-switch endurance loop
  - forced device-loss error-path test
  - native-runtime readiness guard to skip cleanly when LWJGL natives are unavailable
  - added targeted reuse assertions for lighting-only and post/fog-only scene updates (no full rebuilds or descriptor pool rebuilds on real-device path)

## Active Next Steps
- Threshold tightening lane complete for current stress profiles; keep envelopes frozen at established floor and revisit only after major lighting/post changes.
- Expand deterministic golden scenes for additional fog/smoke/shadow material interactions.
- Expand IBL beyond baseline (native `.ktx/.ktx2` decode/prefilter and deeper BRDF/roughness integration).
- Extend Vulkan dynamic-update staging strategy to more scene data beyond current uniform path.
- Add more real-device validation coverage across multiple machine/driver profiles.

## P0-1 Vulkan Albedo Texturing Vertical Slice
- Scope: Implement end-to-end albedo texture sampling in Vulkan for glTF meshes.
- Tasks:
  - Add Vulkan image upload path (`staging -> device-local image -> sampled layout`).
  - Add sampler/image descriptor set layout and per-mesh descriptor binding.
  - Sample `baseColorTexture` in Vulkan fragment shader using mesh UVs.
  - Keep material factor fallback when texture is missing.
- Definition of Done:
  - Vulkan renders textured output for a scene with `MaterialDesc.albedoTexturePath`.
  - No Vulkan validation errors on texture upload/binding.
  - Existing Vulkan integration tests still pass.

## P0-2 Backend Comparison Harness
- Status: Implemented (including guarded CI mode).
- Scope: Make parity measurable.
- Tasks:
  - Extend sample host with `--compare` mode.
  - Render the same scene with OpenGL and Vulkan and write PNG outputs.
  - Compute a simple diff metric and fail if above threshold.
- Definition of Done:
  - One command produces paired captures and a numeric diff.
  - CI can run comparison in a guarded mode.

## P1-1 Vulkan Normal Mapping
- Status: Implemented.
- Scope: Add normal-map support after albedo path is stable.
- Tasks:
  - Add normal texture descriptor binding and tangent-space normal sampling.
  - Reuse glTF tangents if present; fallback to geometric normal.
- Definition of Done:
  - Normal-mapped model looks materially different from albedo-only shading.

## P1-2 Material Workflow Polish (Both Backends)
- Status: Implemented (baseline).
- Scope: Improve metallic-roughness parity and fallbacks.
- Tasks:
  - Parse/use metallic-roughness texture channel data where present.
  - Keep stable defaults for missing maps.
- Definition of Done:
  - Same scene has close visual intent across OpenGL and Vulkan.
