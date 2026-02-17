# Expanded Rendering Feature Comparison (2026)

This document compares **DynamicLightEngine** (current repository implementation) with major engines in 2026.  
DynamicLightEngine status is based on implemented behavior in this repo (not roadmap-only targets).
Last updated: February 17, 2026.

## Feature Matrix

| Category | DynamicLightEngine (current) | CRYENGINE 5.7 | Unreal Engine 5.7 | Unity (HDRP, Unity 6+) | Godot 4.4 |
|---|---|---|---|---|---|
| Pipeline | Forward-focused dual backend (OpenGL + Vulkan), dedicated post pass baseline | Hybrid deferred (tiled/Forward+) | Deferred (forward optional) | Hybrid tile/cluster deferred/forward | Forward+ clustered (Vulkan), mobile/compat paths |
| PBR/Shading | PBR-leaning GGX baseline, BRDF LUT + IBL integration, tier-aware degradation | Full PBS/PBR | Full PBR | Full PBR | Full PBR |
| Global Illumination | IBL baseline + environment-driven ambient; no full dynamic GI system yet | SVOGI | Lumen | Baked probes + RTGI/SSGI variants | SDFGI/VoxelGI |
| Shadows | Cascaded directional shadows, multi-local point/spot lighting (current cap `8`), shadow-policy local-light budgets (`1/2/3/4` by tier), per-type normal/slope bias scaling, PCF + tier controls, OpenGL local spot atlas render/sampling path with cadence/cache reuse, Vulkan multi-local spot layers + tier-bounded concurrent point cubemap rendering (`HIGH`: 1, `ULTRA`: 2, overrides to scheduler/budget limits) with local point layer-base to cubemap-face matrix generation and concurrent shadow-pass recording (validated under ULTRA/override integration tests), scheduler overrides (`vulkan.shadow.maxShadowedLocalLights`, `vulkan.shadow.maxLocalShadowLayers`, `vulkan.shadow.maxShadowFacesPerFrame`), cadence controls (`vulkan.shadow.scheduler.*`), runtime directional cascade texel-snap controls (`vulkan.shadow.directionalTexelSnapEnabled`, `vulkan.shadow.directionalTexelSnapScale`), age/priority ordering telemetry (`renderedShadowLightIds`), backlog telemetry (`deferredShadowLightCount`, `deferredShadowLightIds`), and moment-atlas budget telemetry (`shadowMomentAtlasBytesEstimate`) with requested-vs-runtime filter reporting (`filterPath` vs `runtimeFilterPath`) and runtime state reporting (`momentFilterEstimateOnly`, `momentPipelineRequested`, `momentPipelineActive`, `momentResourceAllocated`, `momentResourceFormat`, `momentInitialized`, `momentPhase`) plus quality-path controls for `pcss`/`vsm`/`evsm`/contact/RT modes and dedicated Vulkan layered moment write + mip prefilter + mip-aware sampling for `vsm|evsm` with light-bleed reduction, neighborhood-weighted moment denoise, motion-adaptive contact stabilization, and tuned penumbra/contact shaping, now with runtime shadow-quality tuning knobs (`vulkan.shadow.pcssSoftness`, `vulkan.shadow.momentBlend`, `vulkan.shadow.momentBleedReduction`, `vulkan.shadow.contactStrength`, `vulkan.shadow.contactTemporalMotionScale`, `vulkan.shadow.contactTemporalMinStability`) and RT-groundwork controls (`vulkan.shadow.rtDenoiseStrength`, `vulkan.shadow.rtRayLength`, `vulkan.shadow.rtSampleCount`) | Advanced shadow stack | VSM + RT paths | Cascaded + RT options | Clustered shadows + CSM |
| Reflections | IBL + roughness-aware prefilter plus post-pass reflection modes (`ssr`, `planar`, `hybrid`, `rt_hybrid`) in OpenGL/Vulkan, reflection quality profiles (`performance`/`balanced`/`quality`/`stability`), Hi-Z-style SSR stepping + denoise chain, planar clip-plane weighting, probe-volume/box-projection blend controls, and RT-requested fallback stack (full hardware RT traversal not yet a dedicated pipeline) | SSR | Lumen/SSR/RT | SSR/planar/RT options | SSR + RTR (renderer-dependent) |
| Anti-Aliasing | Full SMAA baseline (edge detect + blend weights + neighborhood resolve) + temporal AA stack in OpenGL/Vulkan with runtime `aaMode` (`taa`, `tuua`, `tsr`, `msaa_selective`, `hybrid_tuua_msaa`, plus `dlaa`/`fxaa_low` profile hooks), per-pixel velocity reprojection, per-object previous-transform velocity support, per-material reactive controls (`reactiveBoost`, `taaHistoryClamp`, `emissiveReactiveBoost`, `reactivePreset`), tiered neighborhood clipping, optional luminance clipping, tiered sharpen strength, confidence-buffer decay/recovery, neighborhood confidence dilation, and TSR controls (`tsrHistoryWeight`, `tsrResponsiveMask`, `tsrNeighborhoodClamp`, `tsrReprojectionConfidence`, `tsrSharpen`, `tsrAntiRinging`); runtime AA presets (`performance`, `balanced`, `quality`, `stability`) | TAA | TSR/TAA | TAAU (+ upscalers) | TAA/MSAA |
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
- Motion-vector quality is upgraded with per-object previous-transform coverage in both backends for improved thin/fast geometry rejection.
- ULTRA parity envelopes for AA stress now include tighter bounds (`taa-thin-geometry-shimmer` and `taa-specular-flicker` at `<= 0.31`; `taa-history-confidence-stress` and `taa-specular-aa-stress` tightened further to `<= 0.29`).
- New authored AA stress profiles are gated: `taa-reactive-authored-dense-stress`, `taa-alpha-pan-stress`, `taa-aa-preset-quality-stress`, `taa-confidence-dilation-stress`.
- AA compare matrix now runs targeted scene classes across `taa`, `tuua`, `tsr`, `msaa_selective`, and `hybrid_tuua_msaa`, including animated fast-motion suites (`taa-fast-camera-pan-animated-objects-stress`, `taa-animated-object-crossing-thin-geo-stress`, `taa-emissive-alpha-motion-stress`).
- TSR compare runs now apply scene-class tuning packs for foliage alpha, specular micro-highlights, thin-geometry motion, and disocclusion rapid-pan stress.
- Temporal stability gates now enforce drift limits on `shimmerIndex`, `historyRejectRate`, `confidenceMean`, and `confidenceDropEvents` in addition to PNG diff.
- Motion-focused long-run workflows are available (`aa_rebaseline_real_mac.sh longrun-motion`) to repeatedly sample animated stress scenes before threshold locking.
- CI now includes always-on shadow matrix (push/PR) plus scheduled long-run motion sampling, with manual dispatch toggles for extended shadow matrix lanes.
- Compare outputs are mode-separated (`vulkan_real` vs `vulkan_mock`) with profile-specific thresholds (strict real Vulkan; fallback mock Vulkan for CI safety).
- Real-Vulkan preflight support validates loader/extensions before compare runs to fail fast with actionable diagnostics.
- Specular AA is reinforced with Toksvig-style roughness filtering in both backends to reduce glossy shimmer.
- Runtime AA telemetry is now surfaced in frame stats/events (`historyRejectRate`, `confidenceMean`, `confidenceDropEvents`) for data-driven tuning.
- External native upscaler bridge path is available for FSR/XeSS/DLSS integration (`<backend>.upscaler.bridgeClass` plus optional bridge library preload).
- Upscaler vendor-matrix workflow is available (`aa_rebaseline_real_mac.sh upscaler-matrix`) and records hook/native warning states in compare metadata for FSR/XeSS/DLSS qualification passes.
- Strong regression harness (`--compare`, tiered golden thresholds, stress profiles including post/SSAO).
- Reflection stress coverage now includes Hi-Z/probe and RT-fallback scenes (`reflections-hiz-probe`, `reflections-rt-fallback`) in addition to SSR/planar/hybrid parity gates.
- Local light management now supports multiple point/spot lights in both backends with per-light GPU array packing; OpenGL includes local spot shadow-atlas rendering/sampling with cadence+cache reuse, while Vulkan now renders multi-local spot layers with tier-bounded point-cubemap scheduling and keeps broader per-light atlas/cubemap parity rollout in progress.
- Shadow telemetry now includes atlas memory/update-byte estimates and Vulkan depth-format compare controls (`d16`/`d32`) for CI divergence checks.
- VSM/EVSM request-state telemetry now reports correctly (`momentPipelineRequested=true` for `vsm|evsm`), with pending/initializing warnings emitted when requested moment resources are not active.
- Moment shadow filtering now adds edge-aware denoise weighting on top of neighborhood-weighted sampling to reduce blur bleeding across high-depth-gradient edges.
- Capability-gated RT shadow hybrid traversal now consumes runtime tuning values (`vulkan.shadow.rtDenoiseStrength`, `vulkan.shadow.rtRayLength`, `vulkan.shadow.rtSampleCount`) directly in shader traversal/denoise shaping.
- Vulkan moment filtering now includes a deep wide bilateral consistency pass on top of neighborhood/edge-aware denoise for higher VSM/EVSM stability in motion-heavy scenes.
- Shadow CI matrix now includes an explicit cadence guard (`shadowSchedulerCadenceDefersPointWorkUnderFaceBudget`) to enforce deferred-work behavior under local face-budget pressure.
- Guarded real-Vulkan long-run shadow CI lane now auto-generates threshold-lock recommendations from collected compare metadata when hardware support is present.
- Guarded production shadow quality sweeps now run as a dedicated CI lane (`pcf`, `pcss/contact`, `vsm`, `evsm`, `rt optional`, `rt bvh`, `rt bvh_dedicated`) and auto-generate threshold-lock recommendations from repeated real-Vulkan runs when available.
- Scheduled CI now enforces stricter real-Vulkan shadow lockdown cadence (3-run longrun/quality sweeps with strict BVH lane), while push/PR lanes remain reduced-run for cost control.
- Local CI-parity lockdown runner is now available (`scripts/shadow_ci_lockdown_full.sh`) for repeatable matrix+longrun+quality strict-BVH sweeps before promotion.
- Full lockdown sequence is now also a dedicated CI workflow lane (`shadow-lockdown-full`) on schedule/manual dispatch for always-on regression containment.
- Compare harness now supports repo-owned default threshold profiles by Vulkan mode (`vulkan-real.properties` / `vulkan-mock.properties`) plus a promotion script (`scripts/promote_compare_thresholds.sh`) to ingest lock recommendations into tracked defaults.
- Good diagnostics/warnings for quality fallback and runtime pressure.
- Shadow roadmap alignment is explicit: multi-local atlas rollout, temporal stability controls (texel snapping/jitter), static-vs-dynamic cache layering, cadence policy validation, and CI depth-format divergence checks (`D16_UNORM` vs `D32_SFLOAT`).
- Shadow cadence now includes stale-light bypass promotion (age-aware scheduling) to prevent long-tail local-light starvation under throttled budgets.
- Policy telemetry now reports stale-bypass activity (`staleBypassShadowLightCount`) for CI observability.

### Where DynamicLightEngine still trails AAA engines
- No full dynamic GI framework (Lumen/SVOGI-class).
- No dedicated production BVH traversal/reflection-denoiser RT pipeline yet (RT mode now includes BVH approximation lanes: `bvh` uses hybrid traversal shaping and `bvh_dedicated` uses a dedicated-preview traversal shaping lane, but both still gate/fallback and remain pre-production until true hardware BVH traversal + denoise lands).
- Remaining shadow gap is explicit: full Vulkan per-light atlas/cubemap parity is still rolling out, and hardware RT traversal/denoise shadow path is still pending. Multi-point cubemap concurrency is now landed within current tier/override scheduler budgets, and dedicated Vulkan moment-atlas write/prefilter is active for `vsm|evsm`.
- No production-grade virtual geometry/streaming system.
- Smaller post/VFX stack (intentional scope for current phase).

## Recommended Benchmarking Use

Use this document with:
- `docs/capabilities-compendium.md` for implementation truth.
- `docs/rendering-roadmap-2026.md` for upcoming feature priorities.
- Compare-harness outputs from `engine-host-sample` when evaluating parity progress.
