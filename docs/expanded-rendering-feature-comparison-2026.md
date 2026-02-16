# Expanded Rendering Feature Comparison (2026)

This document compares **DynamicLightEngine** (current repository implementation) with major engines in 2026.  
DynamicLightEngine status is based on implemented behavior in this repo (not roadmap-only targets).
Last updated: February 16, 2026.

## Feature Matrix

| Category | DynamicLightEngine (current) | CRYENGINE 5.7 | Unreal Engine 5.7 | Unity (HDRP, Unity 6+) | Godot 4.4 |
|---|---|---|---|---|---|
| Pipeline | Forward-focused dual backend (OpenGL + Vulkan), dedicated post pass baseline | Hybrid deferred (tiled/Forward+) | Deferred (forward optional) | Hybrid tile/cluster deferred/forward | Forward+ clustered (Vulkan), mobile/compat paths |
| PBR/Shading | PBR-leaning GGX baseline, BRDF LUT + IBL integration, tier-aware degradation | Full PBS/PBR | Full PBR | Full PBR | Full PBR |
| Global Illumination | IBL baseline + environment-driven ambient; no full dynamic GI system yet | SVOGI | Lumen | Baked probes + RTGI/SSGI variants | SDFGI/VoxelGI |
| Shadows | Cascaded directional shadows, spot/point baseline, PCF + tier controls | Advanced shadow stack | VSM + RT paths | Cascaded + RT options | Clustered shadows + CSM |
| Reflections | IBL + roughness-aware prefilter baseline; no full SSR/RT reflection suite | SSR | Lumen/SSR/RT | SSR/planar/RT options | SSR + RTR (renderer-dependent) |
| Anti-Aliasing | Full SMAA baseline (edge detect + blend weights + neighborhood resolve) + jitter-aware TAA with per-pixel velocity reprojection, per-material reactive controls (`reactiveBoost`, `taaHistoryClamp`), tiered neighborhood clipping, optional luminance clipping, and confidence-buffer decay/recovery in OpenGL and Vulkan | TAA | TSR/TAA | TAAU (+ upscalers) | TAA/MSAA |
| Geometry/Detail | glTF mesh path with normals/UV/tangents; no Nanite-class virtual geometry | Tessellation/POM | Nanite | LOD + GPU-driven options | LOD + compute-assisted paths |
| Post-Processing | Tonemap + bloom + SSAO-lite + SMAA-lite baseline (OpenGL FBO chain, Vulkan post path with fallback) | Mature cinematic stack | Extensive post suite | Extensive HDRP post suite | Compositor-driven post |
| VFX/Water | Fog/smoke baseline; no full Niagara/VFX-Graph-class stack | Strong GPU FX | Niagara + advanced water | VFX Graph + water stacks | Particles + compute FX baseline |
| Ray Tracing | Not a production feature yet | Software/hybrid options | Full hardware RT | Hardware RT (tier/platform dependent) | Experimental/limited paths |
| Graphics APIs | OpenGL + Vulkan via Java API/SPI boundary | DX12/Vulkan/DX11 | DX12/Vulkan/Metal | DX12/Vulkan/Metal | Vulkan primary + GLES3 |

## Positioning Summary

### Where DynamicLightEngine is strong now
- Clean Java-first runtime/SPI boundary with backend swap flexibility.
- Real OpenGL and Vulkan runtime paths with broad scene parity checks.
- TAA now includes confidence-buffer decay/recovery on instability/disocclusion, plus authored reactive-mask stress profiles for thin-geometry shimmer and specular flicker.
- ULTRA parity envelopes for AA stress now include tighter bounds (`taa-thin-geometry-shimmer` and `taa-specular-flicker` at `<= 0.31`; `taa-history-confidence-stress` and `taa-specular-aa-stress` at `<= 0.32`).
- Specular AA is reinforced with Toksvig-style roughness filtering in both backends to reduce glossy shimmer.
- Runtime AA telemetry is now surfaced in frame stats/events (`historyRejectRate`, `confidenceMean`, `confidenceDropEvents`) for data-driven tuning.
- Strong regression harness (`--compare`, tiered golden thresholds, stress profiles including post/SSAO).
- Good diagnostics/warnings for quality fallback and runtime pressure.

### Where DynamicLightEngine still trails AAA engines
- No full dynamic GI framework (Lumen/SVOGI-class).
- No mature RT feature stack.
- No production-grade virtual geometry/streaming system.
- Smaller post/VFX stack (intentional scope for current phase).

## Recommended Benchmarking Use

Use this document with:
- `docs/capabilities-compendium.md` for implementation truth.
- `docs/rendering-roadmap-2026.md` for upcoming feature priorities.
- Compare-harness outputs from `engine-host-sample` when evaluating parity progress.
