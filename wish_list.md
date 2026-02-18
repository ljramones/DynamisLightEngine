# DynamicLightEngine Wish List

Living capability board. Update statuses as implementation evolves.

Review metadata:

- Last reviewed: 2026-02-18
- Reviewed by: Codex (with user direction)
- Next review trigger: any feature milestone closeout or tier-profile change
- Latest reflection update: 2026-02-18 14:10 ET — Wired reflection trend fail/high-risk warnings into `PerformanceWarningEvent` emission path for callback-driven CI alerting (in addition to frame warnings).

Status legend:

- `In`: implemented and usable in current runtime path.
- `Partial`: present in some form, limited, experimental, or backend-specific.
- `Not In Yet`: wishlist/target only.

Status summary snapshot (2026-02-18):

| Status | Count |
| --- | ---: |
| `In` | 21 |
| `Partial` | 57 |
| `Not In Yet` | 113 |

## Shadows

- PCF (soft, hard, variable kernel) — `In`
- PCSS (percentage-closer soft shadows with blocker search) — `In`
- VSM / EVSM (moment-based with bleed reduction) — `In`
- Contact shadows (screen-space, short-range detail) — `In`
- Cascaded shadow maps (directional, N-cascade configurable) — `In`
- Per-light atlas with cadence scheduling — `Partial`
- Point light cubemap shadows with face-budget control — `Partial`
- Spot light projected shadows — `Partial`
- Area light shadows (approximate or sampled) — `Not In Yet`
- RT shadows (hard, soft, denoised) — `Partial`
- Hybrid combinations (cascade + contact + RT detail fill) — `Partial`
- Transparent shadow receivers — `Not In Yet`
- Shadow caching (static geometry cache, dynamic overlay) — `Partial`
- Distance-field soft shadows (medium-range, no map needed) — `Not In Yet`

## Reflections

- IBL / environment cubemap (static, runtime-captured) — `In`
- Box-projected parallax-corrected probes — `Partial`
- Probe blending (distance, priority, volume-weighted) — `Partial`
- Screen-space reflections (Hi-Z marching, variable quality) — `In`
- Planar reflections (clip-plane re-render, selective objects) — `Partial`
- SSR + probe fallback (seamless blend at SSR miss) — `In`
- RT reflections (single-bounce, multi-bounce) — `Partial`
- RT + SSR hybrid (RT for rough, SSR for sharp, probe for miss) — `Partial`
- Reflection probe streaming (LOD, priority-based update) — `Not In Yet`
- Per-material reflection override (force probe-only for specific surfaces) — `Partial`
- Contact-hardening reflections (roughness ramp near contact) — `Not In Yet`
- Transparent/refractive surface reflections — `Partial`

Reflection notes:

- Vulkan now has per-scene probe slot assignment, frame-visible probe metadata upload, and native 2D-array per-probe reflection selection in main-fragment shading.
- Optional probe cubemap-face ingestion/discovery (`*_px/_nx/_py/_ny/_pz/_nz`) is wired behind a disabled-by-default flag as groundwork; runtime shading still consumes 2D-array probe radiance textures.
- Vulkan now supports per-material reflection overrides (`PROBE_ONLY`, `SSR_ONLY`) via scene color alpha metadata in post reflection resolve.
- Vulkan reflection baseline warning telemetry now includes per-frame override counts (`AUTO`, `PROBE_ONLY`, `SSR_ONLY`, other).
- Vulkan reflection warnings now include probe telemetry (`configured`, `active`, `slots`, `capacity`) with dedicated `REFLECTION_PROBE_BLEND_DIAGNOSTICS` emission.
- Vulkan runtime now exposes probe diagnostics directly for integration/telemetry validation without warning-string parsing.
- Vulkan now tracks probe active-set churn across frames and emits `REFLECTION_PROBE_CHURN_HIGH` when instability persists.
- Vulkan probe-churn warning thresholds are configurable per tier/profile through backend options.
- Probe diagnostics warnings now report configured churn threshold values alongside live churn metrics.
- Vulkan now emits SSR/TAA diagnostics warning telemetry for reflection-temporal interaction monitoring.
- SSR/TAA instability-risk warning thresholds are configurable per profile and included in diagnostic warning payloads.
- SSR/TAA diagnostics now include persistence metrics (risk streak, cooldown, EMA reject/confidence) for better temporal stability analysis.
- Reflection profile selection now drives default telemetry/risk thresholds when explicit backend overrides are absent.
- Reflection warning envelope now includes a compact profile-threshold summary warning (`REFLECTION_TELEMETRY_PROFILE_ACTIVE`).
- Vulkan now supports an optional adaptive SSR/TAA stabilization policy that tunes active temporal weight, SSR strength, and SSR step scale from EMA/streak risk signals.
- Reflection profiles (`performance`, `quality`, `stability`) now also control adaptive SSR/TAA defaults unless explicitly overridden by backend options.
- Vulkan runtime now exposes typed adaptive-policy diagnostics for reflection telemetry validation (`debugReflectionAdaptivePolicyDiagnostics`).
- Vulkan now emits a typed adaptive reflection telemetry event each frame (`ReflectionAdaptiveTelemetryEvent`) for callback-driven trend analysis.
- Vulkan now emits a fixed-window adaptive trend warning report (`REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT`) with severity bucket ratios and mean adaptive deltas.
- Vulkan now includes a CI-friendly high-risk trend gate warning with configurable ratio/streak/sample/cooldown thresholds and a typed trend diagnostics accessor (`debugReflectionAdaptiveTrendDiagnostics`).
- Vulkan now includes a CI SLO audit warning for adaptive trend quality with explicit `status=pass|pending|fail` and fail-only warning emission.
- Vulkan now exposes a machine-readable adaptive trend SLO diagnostics snapshot (`debugReflectionAdaptiveTrendSloDiagnostics`) for parser-free CI assertions.
- Reflection adaptive trend SLO diagnostics are now exposed through the backend-agnostic runtime API surface, with `unavailable` fallback for backends that do not publish it.
- Reflection adaptive trend fail/high-risk warnings now also propagate as `PerformanceWarningEvent` callbacks for parser-free host-side alerting.
- OpenGL parity for probe slot/array path is not yet implemented.

## Anti-Aliasing

- FXAA (low-cost post-process) — `In`
- SMAA (edge detection + blend weights + resolve) — `In`
- TAA (full temporal with velocity reprojection) — `In`
- TAA with confidence buffer (decay/recovery on disocclusion) — `Partial`
- MSAA (selective, per-material opt-in) — `Partial`
- Hybrid MSAA + temporal (MSAA edges, temporal fill) — `Partial`
- TUUA (temporal upscaling with AA) — `Partial`
- TSR (temporal super resolution, internal render scale) — `Partial`
- DLAA (deep learning AA — native res, neural filter) — `Partial`
- Per-material reactive masks (alpha, emissive, specular boost) — `Partial`
- Per-material history clamp control — `Partial`
- Specular AA (Toksvig roughness filtering) — `Partial`
- Geometric AA (normal variance filtering for thin features) — `Not In Yet`
- Alpha-to-coverage for vegetation/hair — `Not In Yet`

## Global Illumination

- Static lightmaps (baked, UV2-based) — `Not In Yet`
- Light probes (SH, placed or auto-generated grid) — `Not In Yet`
- Irradiance volumes (3D grid, interpolated) — `Not In Yet`
- Adaptive probe volumes (dynamic density, streaming) — `Not In Yet`
- SSGI (screen-space global illumination) — `Not In Yet`
- Voxel GI (voxel cone tracing, real-time) — `Not In Yet`
- SDF GI (signed distance field tracing) — `Not In Yet`
- RT GI (single-bounce diffuse, denoised) — `Not In Yet`
- RT GI multi-bounce (recursive, accumulation-based) — `Not In Yet`
- Hybrid GI (probes + SSGI fill + RT detail) — `Not In Yet`
- Emissive GI contribution (emissive surfaces as light sources) — `Not In Yet`
- Dynamic sky GI (environment drives indirect lighting, time-of-day responsive) — `Not In Yet`
- Indirect specular from GI (feeds reflection probes or direct sample) — `Not In Yet`

## Lighting

- Directional lights (sun, moon, with atmosphere interaction) — `In`
- Point lights (omni, attenuated, variable radius) — `In`
- Spot lights (cone, inner/outer angle, projected texture) — `In`
- Area lights (rect, disc, tube — LTC or approximate) — `Not In Yet`
- IES light profiles (real-world photometric data) — `Not In Yet`
- Emissive mesh lights (contribute to direct or GI budget) — `Partial`
- Light cookies / projector textures — `Not In Yet`
- Volumetric light shafts (god rays, per-light opt-in) — `Not In Yet`
- Light clustering (screen-space tile, 3D cluster, or hybrid) — `Not In Yet`
- Light prioritization / budget (per-tier max active lights) — `Partial`
- Light layers / channels (selective light-to-object assignment) — `Not In Yet`
- Physically-based light units (lumens, lux, candela, EV) — `Partial`

## Post-Processing

- HDR tonemap (ACES, filmic, Khronos PBR Neutral, AgX, custom curve) — `Partial`
- Exposure (fixed, auto with histogram, auto with center-weighted) — `Partial`
- Bloom (threshold, multi-pass blur, energy-conserving) — `Partial`
- Depth of field (bokeh, circle of confusion, near/far) — `Not In Yet`
- Motion blur (per-object velocity, camera velocity, tile-based) — `Not In Yet`
- Chromatic aberration — `Not In Yet`
- Film grain — `Not In Yet`
- Vignette — `Not In Yet`
- Lens flare (screen-space, data-driven) — `Not In Yet`
- Color grading (LUT, lift/gamma/gain, channel mixer) — `Not In Yet`
- Sharpening (CAS, RCAS, unsharp mask) — `Partial`
- SSAO (GTAO, HBAO-style, multi-scale) — `Partial`
- SSAO with temporal accumulation — `Partial`
- Screen-space bent normals (indirect occlusion direction) — `Not In Yet`
- Fog (linear, exponential, height-based, volumetric) — `In`
- Volumetric fog (froxel-based, light-participating, density noise) — `Partial`
- Cloud shadows (projected noise, animated) — `Not In Yet`
- Panini projection (wide FOV correction) — `Not In Yet`
- Lens distortion — `Not In Yet`

## PBR / Shading

- Standard metallic-roughness (GGX/Smith) — `In`
- Specular-glossiness workflow — `Not In Yet`
- Clear coat (automotive paint, wet surfaces) — `Partial`
- Anisotropic specular (brushed metal, hair highlights) — `Partial`
- Subsurface scattering (skin, wax, marble — preintegrated or separable) — `Not In Yet`
- Thin-film iridescence (soap bubbles, beetle shells) — `Not In Yet`
- Sheen (fabric, velvet — Charlie distribution) — `Not In Yet`
- Transmission / thin translucency (leaves, paper, curtains) — `Partial`
- Refraction (thick glass, water surface, per-material IOR) — `Partial`
- Detail maps (tiled micro-detail overlay) — `Not In Yet`
- Parallax occlusion mapping (height-based depth) — `Not In Yet`
- Tessellation (displacement mapping, adaptive) — `Not In Yet`
- Decals (deferred or forward-projected, PBR-full) — `Not In Yet`
- Vertex color blending (terrain, weathering) — `Partial`
- Material layering (blend multiple PBR stacks by mask) — `Not In Yet`
- Emissive with bloom contribution control — `Partial`
- Eye shader (refraction, caustic, iris depth) — `Not In Yet`
- Hair shader (Marschner or dual-lobe specular) — `Not In Yet`
- Cloth shader (subsurface + sheen combination) — `Not In Yet`
- Energy conservation validation (diffuse + specular ≤ 1) — `Partial`

## Geometry / Detail

- Static mesh rendering (glTF, optimized draw) — `In`
- Instanced rendering (per-instance transforms, material overrides) — `Partial`
- GPU-driven rendering (indirect dispatch, visibility buffer) — `Not In Yet`
- LOD system (discrete, cross-fade/dither transition) — `Not In Yet`
- Continuous LOD (tessellation-driven) — `Not In Yet`
- Virtual geometry (Nanite-class mesh streaming, cluster culling) — `Not In Yet`
- Occlusion culling (Hi-Z, software rasterized, GPU-readback) — `Not In Yet`
- Frustum culling (CPU, GPU compute) — `Partial`
- Mesh streaming (progressive load, distance-prioritized) — `Partial`
- Impostor/billboard (far-distance LOD replacement) — `Not In Yet`
- Procedural geometry (runtime mesh generation, compute-driven) — `Not In Yet`
- Skinned mesh / skeletal animation — `Not In Yet`
- Morph targets / blend shapes — `Not In Yet`
- Vegetation (wind animation, alpha-tested, two-sided) — `Partial`

## VFX / Particles

- CPU particle system (small-scale, simple emitters) — `Not In Yet`
- GPU compute particles (millions, simulation on GPU) — `Not In Yet`
- Particle lighting (receive shadows, receive GI, emit light) — `Not In Yet`
- Particle shadows (opacity shadow maps, or shadow-receiving) — `Not In Yet`
- Soft particles (depth-fade near surfaces) — `Not In Yet`
- Particle collision (depth buffer, signed distance field) — `Not In Yet`
- Ribbon / trail renderers — `Not In Yet`
- Mesh particles (instanced mesh emission) — `Not In Yet`
- Vector field advection — `Not In Yet`
- Flipbook / sprite sheet animation — `Not In Yet`
- Sub-UV blending — `Not In Yet`
- GPU simulation graph (node-based, data-driven — Niagara/VFX-Graph class) — `Not In Yet`

## Water / Ocean

- Flat water plane (reflective, refractive, PBR) — `Not In Yet`
- Wave simulation (Gerstner, FFT-based ocean) — `Not In Yet`
- Foam generation (Jacobian-based, shoreline) — `Not In Yet`
- Underwater rendering (absorption, scattering, caustics) — `Not In Yet`
- Caustics projection (animated texture or ray-based) — `Not In Yet`
- Shore interaction (depth-based foam, wave deformation) — `Not In Yet`
- River/flow maps (directional surface flow) — `Not In Yet`
- Waterfall/splash VFX integration — `Not In Yet`
- Water depth fog (color absorption by depth) — `Not In Yet`
- Dynamic buoyancy (physics feedback from wave height) — `Not In Yet`

## Ray Tracing

- RT shadows (hard, soft, denoised, area light accurate) — `Partial`
- RT reflections (single-bounce, multi-bounce, denoised) — `Partial`
- RT GI (diffuse single-bounce, multi-bounce) — `Not In Yet`
- RT AO (medium-range, denoised) — `Not In Yet`
- RT translucency / caustics — `Not In Yet`
- BVH management (build, refit, compaction) — `Partial`
- Denoiser framework (temporal, spatial, bilateral) — `Partial`
- Hybrid RT + rasterized composition (RT for hero surfaces, raster for fill) — `Partial`
- RT quality tiers (ray count, bounce count, denoise strength) — `Partial`
- Inline / ray query support (forward pass ray queries) — `Partial`
- Dedicated ray generation shaders — `Not In Yet`

## Sky / Atmosphere

- Procedural sky (Preetham, Hosek-Wilkie) — `Not In Yet`
- Physically-based atmosphere (Bruneton, multi-scattering LUT) — `Not In Yet`
- HDRI skybox (static, rotatable) — `Partial`
- Dynamic time-of-day (sun position, color temperature shift) — `Not In Yet`
- Volumetric clouds (ray-marched, weather-driven density) — `Not In Yet`
- Cloud shadow projection (animated, directional) — `Not In Yet`
- Moon / celestial bodies — `Not In Yet`
- Star field (procedural or data-driven) — `Not In Yet`
- Aerial perspective (atmospheric scattering on distant objects) — `Not In Yet`
- Night sky (Milky Way, light pollution falloff) — `Not In Yet`
- Aurora / atmospheric phenomena — `Not In Yet`

## Terrain

- Heightmap terrain (LOD, clipmap or quadtree) — `Not In Yet`
- Virtual texturing (streaming megatexture, indirection) — `Not In Yet`
- Terrain material splatting (4-16 layers, height-blend) — `Not In Yet`
- Grass / vegetation scattering (GPU-instanced, density-driven) — `Not In Yet`
- Terrain holes (caves, tunnels) — `Not In Yet`
- Terrain deformation (runtime, paintable) — `Not In Yet`
- Snow / sand accumulation (directional, thickness-based) — `Not In Yet`
- Erosion simulation (hydraulic, thermal — offline or runtime) — `Not In Yet`
- Terrain streaming (paged, distance-prioritized) — `Not In Yet`

## Graphics API / Backend

- OpenGL 4.x (compatibility, broad reach) — `In`
- Vulkan (primary performance path) — `In`
- Metal (macOS/iOS — future) — `Not In Yet`
- WebGPU (browser — future) — `Not In Yet`
- DirectX 12 (Windows — future if needed) — `Not In Yet`
- Backend-agnostic resource abstraction — `Partial`
- Backend-agnostic shader compilation (GLSL → SPIR-V pipeline) — `Partial`
- Async compute queue (Vulkan/DX12/Metal) — `Not In Yet`
- Multi-GPU (split-frame, alternate-frame — future) — `Not In Yet`

## Engine Infrastructure (Composability Glue)

- Render graph with pass declaration, resource lifetime, barrier generation — `Partial`
- Tier-based graph pruning (passes removed, not branched) — `Partial`
- Volume system (spatial overrides for any rendering parameter) — `Not In Yet`
- Per-feature capability modules (shader hook + bindings + uniforms) — `Partial`
- Shader module composition (assemble fragment from contributed hooks) — `Not In Yet`
- Descriptor layout composition (per-pass, from declared requirements) — `Not In Yet`
- Profile/preset system (blessed tier configurations) — `In`
- Graph validation (reject illegal feature combinations at build time) — `Partial`
- Hot-reload of individual capability modules (dev workflow) — `Not In Yet`
- Telemetry per-feature (budget, timing, quality metrics) — `Partial`
- Compare harness per-feature and per-profile (regression gates) — `In`

---

Notes:

- These statuses are intentionally conservative and should be refined as each domain is validated.
- For pipeline and migration policy context, see:
  - `docs/adr/0002-feature-composition-and-pipeline-migration-policy.md`
  - `docs/architecture/vulkan-render-pipeline-current.md`

## Status Update Checklist

Use this checklist whenever changing an item status:

- Confirm scope: backend (`OpenGL`, `Vulkan`, both) and tier/profile coverage.
- Pick status by evidence:
  - `In`: implemented, reachable, and validated in intended path.
  - `Partial`: implemented but limited (quality/path/backend/test gaps).
  - `Not In Yet`: not implemented in production path.
- Add a short note in commit/PR verification section with:
  - demo/test used
  - command(s) run
  - known caveats
- If the change affects composition architecture/pipeline sequencing, also update:
  - `docs/adr/0002-feature-composition-and-pipeline-migration-policy.md`
  - `docs/architecture/vulkan-render-pipeline-current.md`
