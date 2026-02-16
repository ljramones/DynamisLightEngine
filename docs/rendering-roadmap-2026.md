# DynamicLightEngine Rendering Roadmap (2026)

Last updated: February 16, 2026

## Scope
This roadmap tracks the current targeted execution lanes for rendering and runtime maturity while preserving API stability and OpenGL/Vulkan parity.

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
- TAA baseline started with temporal history blend in OpenGL post-pass; Vulkan parity path remains next.
- Tighten post-process parity envelopes after each shader-quality upgrade.

Next:
1. Add richer SSAO/HBAO-lite controls and tier policy.
2. Add anti-aliasing baseline track (SMAA-lite or TAA prototype).
3. Add deterministic post-quality golden scene set expansion.

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
