# DynamicLightEngine Rendering Roadmap (2026)

Last updated: February 16, 2026

## Scope
This plan defines the next 3-9 months of rendering-focused work after the current baseline:
- OpenGL + Vulkan parity for core PBR-leaning forward rendering
- cascaded shadows, fog/smoke quality tiers, compare harness, and guarded real-device tests

The goal is to move from "strong baseline" to "demo-ready rendering platform" while preserving API stability and dual-backend parity.

## Guiding Constraints
- Keep `engine-api` stable; prefer additive API changes.
- Maintain OpenGL/Vulkan visual intent parity with compare-harness thresholds.
- Land features behind test coverage first, then tighten thresholds.
- Prioritize visible improvements before deep architectural complexity.

## Phase 1 (2-5 weeks): Immediate Polish and Usability
1. Post-processing baseline (top priority)
- Add tonemapping + bloom as optional post path.
- API: add `PostProcessDesc` (or equivalent config block) with tier-aware defaults.
- Backend: OpenGL FBO post pass; Vulkan offscreen color + post pass.
- Tests: add compare profile for post-enabled scenes.

Current progress:
- Started.
- `PostProcessDesc` added at API boundary.
- Tonemap baseline is wired in OpenGL and Vulkan scene render paths.
- Bloom baseline is wired in OpenGL and Vulkan scene render paths (shader-driven threshold/strength path).
- Compare harness includes `post-process` and `post-process-bloom` parity profiles.
- Sample host now exposes CLI controls for quality tier, shadow parameters, and post-process parameters.
- Current refinement state:
  - OpenGL dedicated post-pass is active (offscreen FBO + fullscreen post composite) with fallback retained.
  - Vulkan dedicated post-pass baseline is active (intermediate scene image + fullscreen post composite) with automatic shader fallback and explicit mode diagnostics.

2. Shadow control surfacing
- Expose `shadowResolution`, `cascadeCount`, `pcfKernel`, `bias` controls in API-level scene data.
- Keep quality-tier clamping and emit `SHADOW_QUALITY_DEGRADED` when constrained.

3. Sample host usability pass
- Add runtime tweak panel (tier, shadows, fog/smoke, post values, camera speed).
- Add stats/resource overlay and explicit scene reload controls.
- Provide 3-4 curated benchmark/demo scenes.

Exit criteria:
- Post stack enabled on both backends with passing compare profiles.
- Shadow controls configurable from host path.
- Sample host supports live tuning and diagnostics.

## Phase 2 (6-12 weeks): Lighting and Shadow Depth
1. IBL approximation
- Add irradiance + prefiltered radiance + BRDF LUT integration.
- Status: baseline hook + texture-driven shader sampling implemented (`EnvironmentDesc` IBL paths + backend baseline signal + irradiance/radiance/BRDF-LUT shader samplers).
- Status addendum: `.ktx/.ktx2` container paths now resolve via sidecar decode sources when present (`.png/.hdr/.jpg/.jpeg`) with explicit fallback warning signaling.
- Status addendum: roughness-aware radiance prefilter approximation is active in both backends with tier-driven prefilter strength.
- Status addendum: LOW/MEDIUM quality tiers now explicitly degrade IBL diffuse/specular/prefilter response with runtime warning `IBL_QUALITY_DEGRADED`; ULTRA retains full-strength policy.
- Status addendum: both backends now apply roughness-aware multi-tap specular radiance filtering (`IBL_MULTI_TAP_SPEC_ACTIVE`) and use view-space camera direction for more stable IBL highlights.

2. Point/spot shadow baseline
- Add shadow type expansion in light model.
- Start with point-light cubemap shadows (tier-gated).
- Status: API/runtime light-type expansion delivered (`DIRECTIONAL`/`POINT`/`SPOT`) with backend lighting selection wiring.
- Status addendum: spot-light cone attenuation is now implemented in OpenGL and Vulkan shading paths (direction + inner/outer cone).
- Status addendum: OpenGL includes a point-light cubemap baseline; Vulkan now runs a 6-face layered point-shadow path aligned to cubemap face directions for baseline parity.
- Status addendum: point-shadow filtering now uses adaptive PCF/bias scaling in both backends for improved near/far stability.

3. Optional SSAO pass
- Add basic SSAO/HBAO-lite as post pass before bloom/tonemap.

Exit criteria:
- Major realism gain in material/lighting scenes with stable parity thresholds.

## Phase 3 (2-4 months): Performance and Scalability
1. Async resource loading/streaming
- Add async load path in resource service using virtual threads.
- Remove scene-load stalls for larger assets.

2. Culling + draw efficiency
- Frustum culling baseline and initial instancing for repeated meshes.

3. Anti-aliasing track
- Start with SMAA or baseline TAA prototype.

Exit criteria:
- Noticeable frame-time stability improvements under larger scene workloads.

## Ongoing Quality Gates
- Keep compare harness green while tightening stress thresholds incrementally.
- Expand deterministic golden scenes for fog+smoke+shadow+material combinations.
- Grow guarded real-device Vulkan tests (endurance, resize, scene-switch, error paths).

Current progress:
- Added deterministic compare profile `material-fog-smoke-shadow-cascade-stress` with tiered and ULTRA stress-golden bounds.
- Closed an IBL parity gap: OpenGL now applies AO modulation to IBL diffuse ambient, aligned with Vulkan behavior.
- Expanded Vulkan frame-resource strategy with revision-aware dynamic uniform staging:
  - per-frame ring slots now track global/scene uniform revision sync
  - command recording skips uniform copy/barrier when no sync work is needed
  - dynamic-only scene changes upload sparse dirty object uniform ranges (multi-range) where safe
  - global-state revision updates are value-aware to avoid no-op churn when host re-sends unchanged settings each frame

## Near-Term Task Queue (Execution Order)
1. Post-processing baseline (tonemap then bloom).
2. API shadow controls + host wiring.
3. Sample host live tuning panel + diagnostics overlay.
4. IBL baseline.
5. Async resource loading.
