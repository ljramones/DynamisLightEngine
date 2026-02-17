# DynamicLightEngine Rendering Roadmap (2026)

Last updated: February 17, 2026

## Scope
This roadmap tracks the current targeted execution lanes for rendering and runtime maturity while preserving API stability and OpenGL/Vulkan parity.

Related roadmap:
- `docs/mechanical-sympathy-gpu-driven-roadmap-2026.md` (Valhalla-oriented layout, GPU-driven rendering, Vulkan descriptor-model modernization, Dynamic GI/DDGI)
- `docs/superset-rendering-roadmap-2026.md` (cross-engine superset target and phased execution plan)

## Guiding Constraints
- Keep `engine-api` additive and stable.
- Keep parity measurable via guarded compare-harness thresholds.
- Favor visible gains first, then deeper architecture hardening.

## Phase A: Visual Quality and Post Stack
Status: In progress.

Completed:
- Dedicated post pipeline baseline in both backends (tonemap + bloom).
- SSAO-lite baseline in both backends.
- Compare profiles for post/bloom/SSAO and stress envelopes.

In progress:
- SSAO quality upgrade from edge-only approximation toward stronger kernel shaping.
- SSAO controls expansion with `radius`/`bias`/`power` shaping across both backends.
- SMAA-lite baseline implemented in both backends (shader-driven fallback + dedicated post-pass path).
- TAA baseline temporal-history blend is now active in both OpenGL and Vulkan post paths.
- Tighten post-process parity envelopes after each shader-quality upgrade.
- Reflections upgrade lane: Hi-Z-style SSR stepping, denoise chain, planar clip-plane weighting, probe volume blending, and `rt_hybrid` request mode with fallback tuning.

Current gap:
- Hardware RT reflections are now first-class as an API/runtime mode request (`rt_hybrid`) with fallback stack and tuning, but not yet a dedicated BVH traversal RT pipeline (that is still the remaining production RT step).

Next:
1. Add richer SSAO/HBAO-lite controls and tier policy.
2. Add anti-aliasing baseline track (SMAA-lite or TAA prototype).
3. Add deterministic post-quality golden scene set expansion.
4. Implement dedicated production BVH traversal + reflection-denoiser RT pipeline behind the existing `rt_hybrid` API lane.

## Phase B: Lighting and Shadow Realism
Status: Mostly complete; polish lane active.

Completed:
- IBL baseline with irradiance/radiance/BRDF LUT integration.
- KTX/KTX2 support for uncompressed + zlib + zstd + BasisLZ/UASTC transcoding path.
- BRDF tier-extreme consistency polish.
- Directional + spot + point shadow baselines in both backends.

Remaining:
1. Additional OpenGL/Vulkan parity tuning passes on real-device captures.
2. Further quality-tier consistency checks for extreme material/shadow mixes.

### Phase B.1 Shadow Expansion Plan (active)
1. Multi-local shadow atlas core:
   - add power-of-two atlas hierarchy
   - pack local shadow allocations largest-to-smallest (decreasing-size shelf/quadtree strategy)
   - use least-recently-visible eviction before forced repack
2. Multi-light local shadow rendering:
   - render multiple selected spot-light shadows each frame from policy budget
   - keep single-primary fallback only when budget pressure requires it
   - add point-light cubemap shadow lane with strict per-tier update budget
3. Temporal shadow stability:
   - directional cascade texel snapping to reduce shimmer
   - optional shadow-projection jitter integration for TAA-assisted softening
4. Static shadow cache + update throttling:
   - static atlas layer for static geometry/light contributions
   - mixed dynamic layer for moving casters
   - far/low-priority shadow updates at reduced cadence (e.g. ~15 Hz), hero shadows full-rate
5. CI/runtime hardening for shadows:
   - shadow memory telemetry counters (atlas bytes, per-frame update bytes)
   - depth-format divergence checks (`D16_UNORM` vs `D32_SFLOAT`) in shadow regression matrix

Implemented now:
- Shared power-of-two shadow-atlas planner in `engine-impl-common` with descending-size packing.
- Runtime shadow-policy telemetry now reports atlas tile usage/utilization and eviction count in `SHADOW_POLICY_ACTIVE`.
- OpenGL and Vulkan shadow mapping now compute atlas planning metrics from selected local shadow candidates.
- Shadow telemetry now includes memory/update-byte estimates (`atlasMemoryD16Bytes`, `atlasMemoryD32Bytes`, `shadowUpdateBytesEstimate`) for CI budget checks.
- Vulkan compare workflow now supports depth-format toggles via `dle.vulkan.shadow.depthFormat` (`d16`/`d32`) for divergence validation.

Still in progress:
- Full per-light local shadow atlas rendering/sampling path (current runtime still renders primary local shadow path).

## Phase C: Runtime Hardening and Scalability
Status: In progress.

Completed:
- Major Vulkan frame-resource ring/staging upgrades.
- Descriptor/uniform pressure telemetry + warning policy.
- Dynamic-scene reuse improvements (including texture-only rebind path).
- Expanded guarded real-device CI coverage on macOS/Linux/Windows.

Remaining:
1. Deeper staged updates for additional scene data classes.
2. Larger explicit per-frame ownership limits for descriptor/uniform resources at scale.
3. Longer endurance + forced-error validation matrix expansion.

## Phase D: Platform and Tooling Expansion
Status: Planned.

Targets:
1. Broader host/sample usability tooling for tuning and diagnostics.
2. Wider driver/hardware reporting with reproducible benchmark profiles.
3. Packaging/runtime polish for smoother cross-platform onboarding.

## Active Order of Work
1. Phase A SSAO quality upgrade and parity envelope stabilization.
2. Phase B parity polish around lighting/shadow edge cases.
3. Phase C deeper dynamic staging/resource ownership expansion.
