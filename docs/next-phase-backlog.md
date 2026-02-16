# Next-Phase Backlog (Execution Ready)

Primary roadmap: `docs/rendering-roadmap-2026.md`

## Latest Completed Batch (February 2026)
- Tightened parity stress thresholds:
  - `shadow-cascade-stress` `0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30 -> 0.29`
  - `fog-shadow-cascade-stress` `0.40 -> 0.39 -> 0.38 -> 0.37 -> 0.36 -> 0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30`
  - `smoke-shadow-cascade-stress` `0.40 -> 0.39 -> 0.38 -> 0.37 -> 0.36 -> 0.35 -> 0.34 -> 0.33 -> 0.32 -> 0.31 -> 0.30`
  - `fog-smoke-shadow-post-stress` `0.37 -> 0.35 -> 0.34 -> 0.33 -> 0.32`
  - `post-process-bloom (HIGH)` `0.36 -> 0.35 -> 0.34 -> 0.33`
- Added tiered `texture-heavy` parity envelopes (`LOW/MEDIUM/HIGH/ULTRA`) in compare-harness tests.
- Implemented Phase 2 Item 1 baseline hook:
  - environment-driven IBL baseline in OpenGL and Vulkan
  - shader-side IBL texture sampling baseline in OpenGL and Vulkan (irradiance/radiance/BRDF-LUT)
  - roughness-aware radiance prefilter approximation in OpenGL and Vulkan (tier-driven prefilter strength)
  - texture ingestion + calibration support for configured IBL assets (`png/jpg/jpeg/.hdr`)
  - `.ktx/.ktx2` IBL container paths now resolve through sidecar decode sources when available (`.png/.hdr/.jpg/.jpeg`)
  - explicit `IBL_KTX_CONTAINER_FALLBACK` and `IBL_PREFILTER_APPROX_ACTIVE` warnings emitted for IBL runtime mode visibility
  - bridge mapping preserves `EnvironmentDesc` IBL fields and `PostProcessDesc`
  - new backend tests assert `IBL_BASELINE_ACTIVE` signal
- Hardened Vulkan frame-resource architecture:
  - expanded to `MAX_FRAMES_IN_FLIGHT=3`
  - per-frame descriptor-set ring for global uniforms
  - persistent mapped staging for frame-uniform uploads
  - expanded frame-resource telemetry fields
- Expanded guarded real-Vulkan suite:
  - longer resize/scene-switch endurance loop
  - forced device-loss error-path test
  - native-runtime readiness guard to skip cleanly when LWJGL natives are unavailable

## Active Next Steps
- Continue controlled threshold tightening on remaining stress profiles where signal remains stable.
- Expand deterministic golden scenes for additional fog/smoke/shadow material interactions.
- Expand IBL beyond baseline (native `.ktx/.ktx2` decode/prefilter, stronger BRDF/roughness integration, quality-tier policy).
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
