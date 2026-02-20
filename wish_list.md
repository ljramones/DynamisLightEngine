# DynamicLightEngine Wish List

Living capability board. Update statuses as implementation evolves.

Review metadata:

- Last reviewed: 2026-02-19
- Reviewed by: Codex (with user direction)
- Next review trigger: any feature milestone closeout or tier-profile change
- Latest reflection update: 2026-02-19 12:14 ET — Reflections Vulkan closeout index added (`docs/reflections-vulkan-closeout.md`) with linked checklists, exit criteria, and promotion commit ledger.
- Latest shadow contract update: 2026-02-19 13:05 ET — Shadow v2 backlog modes/checklist added (`docs/shadow-contract-v2-backlog-checklist.md`) to track remaining shadow items through contract validation.
- Latest structure guardrails update: 2026-02-19 16:52 ET — Class-size and package hygiene guardrails now enforced in CI (`.github/workflows/ci.yml` job `structure-guardrails`) via `scripts/java_structure_guardrails.sh`.
- Latest AA temporal hardening update: 2026-02-19 18:57 ET — geometric AA + alpha-to-coverage envelope/promotion gates were added (`AA_GEOMETRIC_*`, `AA_A2C_*`) through the quality diagnostics path (`aaQualityPromotionDiagnostics()`), with lockdown coverage.
- Latest Phase C composition update: 2026-02-19 19:13 ET — Phase C shader/descriptor/profile composition completed in Vulkan (`docs/phase-c-shader-composition-checklist.md`), including composed layout runtime wiring and profile compile/cache/switch path.
- Latest lighting contract update: 2026-02-20 09:41 ET — Lighting capability v2 descriptor/planner/telemetry scaffold added (`docs/lighting-capability-v2-checklist.md`) with typed runtime diagnostics (`lightingCapabilityDiagnostics()`).

Status legend:

- `In`: implemented and usable in current runtime path.
- `Partial`: present in some form, limited, experimental, or backend-specific.
- `Not In Yet`: wishlist/target only.

Status summary snapshot (2026-02-19):

| Status | Count |
| --- | ---: |
| `In` | 52 |
| `Partial` | 35 |
| `Not In Yet` | 105 |

## Shadows

- PCF (soft, hard, variable kernel) — `In`
- PCSS (percentage-closer soft shadows with blocker search) — `In`
- VSM / EVSM (moment-based with bleed reduction) — `In`
- Contact shadows (screen-space, short-range detail) — `In`
- Cascaded shadow maps (directional, N-cascade configurable) — `In`
- Per-light atlas with cadence scheduling — `In`
- Point light cubemap shadows with face-budget control — `In`
- Spot light projected shadows — `In`
- Area light shadows (approximate or sampled) — `In`
- RT shadows (hard, soft, denoised) — `In`
- Hybrid combinations (cascade + contact + RT detail fill) — `In`
- Transparent shadow receivers — `In`
- Shadow caching (static geometry cache, dynamic overlay) — `In`
- Distance-field soft shadows (medium-range, no map needed) — `In`

Shadow notes:

- Shadow v2 contract descriptor now enumerates backlog modes (`local_atlas_cadence`, `point_cubemap_budget`, `spot_projected`, `area_approx`, `rt_denoised`, `hybrid_cascade_contact_rt`, `transparent_receivers`, `cached_static_dynamic`, `distance_field_soft`) for validator/CI composition gating.
- Contract-mode coverage and cross-capability validation checklist is tracked in `docs/shadow-contract-v2-backlog-checklist.md`.
- Vulkan runtime now emits `SHADOW_CAPABILITY_MODE_ACTIVE` each frame when shadows are active, with planner-resolved mode and signal payload for parser-friendly CI/telemetry checks.
- Engine runtime API now exposes backend-agnostic typed shadow capability diagnostics (`shadowCapabilityDiagnostics()`) so CI/hosts can assert planner-resolved mode/signals without warning-string parsing.
- Deep-dive execution sequencing and phase/exit checklist is now tracked in `docs/shadow-deep-dive-implementation-checklist.md`.
- Shadow capability mode planning now incorporates rendered topology signals (`selected/deferred local`, `rendered spot`, `rendered point cubemaps`) so mode telemetry reflects active runtime shadow topology instead of config-only hints.
- Engine runtime API now exposes typed shadow cadence diagnostics (`shadowCadenceDiagnostics()`) and Vulkan emits cadence envelope warnings (`SHADOW_CADENCE_ENVELOPE`, `SHADOW_CADENCE_ENVELOPE_BREACH`) with configurable deferred-ratio/streak/cooldown thresholds.
- Engine runtime API now exposes typed point-shadow face-budget diagnostics (`shadowPointBudgetDiagnostics()`), and Vulkan emits point-budget envelope warnings (`SHADOW_POINT_FACE_BUDGET_ENVELOPE`, `SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH`) with configurable saturation/streak/cooldown thresholds.
- Engine runtime API now exposes typed spot-projected diagnostics (`shadowSpotProjectedDiagnostics()`), and Vulkan emits explicit spot contract status warnings (`SHADOW_SPOT_PROJECTED_CONTRACT`, `SHADOW_SPOT_PROJECTED_CONTRACT_BREACH`).
- Vulkan now emits promotion-ready readiness warnings for cadence/point/spot shadow slices (`SHADOW_CADENCE_PROMOTION_READY`, `SHADOW_POINT_FACE_BUDGET_PROMOTION_READY`, `SHADOW_SPOT_PROJECTED_PROMOTION_READY`) with typed stability streak/min-window diagnostics and tier-profile defaults.
- Vulkan now emits a consolidated Phase A readiness signal (`SHADOW_PHASEA_PROMOTION_READY`) when cadence + point-budget + spot-projected promotion gates are jointly stable, with typed runtime diagnostics (`shadowPhaseAPromotionDiagnostics()`).
- Shadow Phase A has a strict promotion lockdown runner (`scripts/shadow_phasea_promotion_lockdown.sh`) and always-on CI lane (`shadow-phasea-lockdown`) with sustained-window assertions across blessed tiers.
- Vulkan shadow telemetry defaults are now locked per blessed tier profile with explicit override precedence and emitted each frame via `SHADOW_TELEMETRY_PROFILE_ACTIVE`; cadence stability CI assertions now run across `LOW|MEDIUM|HIGH|ULTRA`.
- Engine runtime API now exposes typed shadow-cache diagnostics (`shadowCacheDiagnostics()`), and Vulkan emits cache policy/churn warnings (`SHADOW_CACHE_POLICY_ACTIVE`, `SHADOW_CACHE_CHURN_HIGH`) including invalidation reason telemetry with cooldown-gated CI breach signaling.
- Engine runtime API now exposes typed shadow RT diagnostics (`shadowRtDiagnostics()`), and Vulkan emits RT denoise/perf envelope warnings (`SHADOW_RT_DENOISE_ENVELOPE`, `SHADOW_RT_DENOISE_ENVELOPE_BREACH`) with tier-locked thresholds and override precedence.
- Engine runtime API now exposes typed shadow hybrid diagnostics (`shadowHybridDiagnostics()`), and Vulkan emits hybrid composition-share telemetry/warnings (`SHADOW_HYBRID_COMPOSITION`, `SHADOW_HYBRID_COMPOSITION_BREACH`) with tier-locked share-envelope thresholds.
- Phase C shadow hardening now has a strict lockdown runner (`scripts/shadow_phasec_lockdown.sh`) and always-on CI lane (`shadow-phasec-lockdown`) for cache/RT/hybrid breach + blessed-tier stability gating.
- Engine runtime API now exposes typed transparent receiver diagnostics (`shadowTransparentReceiverDiagnostics()`), and Vulkan now emits transparent receiver policy/envelope warnings (`SHADOW_TRANSPARENT_RECEIVER_POLICY`, `SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH`) with explicit `fallback_opaque_only` policy when requested support is unavailable.
- Transparent receiver hardening now includes a strict lockdown runner (`scripts/shadow_transparent_receivers_lockdown.sh`) and always-on CI lane (`shadow-transparent-lockdown`) with cooldown and blessed-tier stability assertions.
- Engine runtime API now exposes typed extended shadow-mode diagnostics (`shadowExtendedModeDiagnostics()`), and Vulkan now emits policy/required-path breach warnings for area-approx and distance-field soft modes (`SHADOW_AREA_APPROX_POLICY`, `SHADOW_AREA_APPROX_REQUIRED_UNAVAILABLE_BREACH`, `SHADOW_DISTANCE_FIELD_SOFT_POLICY`, `SHADOW_DISTANCE_FIELD_REQUIRED_UNAVAILABLE_BREACH`).
- Engine runtime API now exposes typed shadow topology diagnostics (`shadowTopologyDiagnostics()`), and Vulkan emits strict topology contract warnings (`SHADOW_TOPOLOGY_CONTRACT`, `SHADOW_TOPOLOGY_CONTRACT_BREACH`) for local/spot/point execution coverage with tier-locked envelope defaults.
- Vulkan shadow topology diagnostics now track stability streak + promotion readiness (`topologyPromotionReadyMinFrames`) and emit explicit readiness signaling (`SHADOW_TOPOLOGY_PROMOTION_READY`) when sustained coverage meets the configured window.
- Vulkan local-shadow warning behavior is now policy-aware: scheduler/budget-driven deferral emits `SHADOW_LOCAL_RENDER_DEFERRED_POLICY` instead of rollout-gap baseline warnings.
- Engine runtime API now exposes typed Phase D promotion diagnostics (`shadowPhaseDPromotionDiagnostics()`), and Vulkan emits consolidated readiness (`SHADOW_PHASED_PROMOTION_READY`) when cache/RT/hybrid/transparent/area/distance contracts remain stable for a configured sustained window.
- Shadow Phase D has a strict lockdown runner (`scripts/shadow_phased_lockdown.sh`) and always-on CI lane (`shadow-phased-lockdown`) with sustained-window assertions across blessed tiers.

## Reflections

- IBL / environment cubemap (static, runtime-captured) — `In`
- Box-projected parallax-corrected probes — `In`
- Probe blending (distance, priority, volume-weighted) — `In`
- Screen-space reflections (Hi-Z marching, variable quality) — `In`
- Planar reflections (clip-plane re-render, selective objects) — `In`
- SSR + probe fallback (seamless blend at SSR miss) — `In`
- RT reflections (single-bounce, multi-bounce) — `In`
- RT + SSR hybrid (RT for rough, SSR for sharp, probe for miss) — `In`
- Reflection probe streaming (LOD, priority-based update) — `In`
- Per-material reflection override (force probe-only for specific surfaces) — `In`
- Contact-hardening reflections (roughness ramp near contact) — `In`
- Transparent/refractive surface reflections — `In`

Reflection notes:

- Vulkan now has per-scene probe slot assignment, frame-visible probe metadata upload, and native 2D-array per-probe reflection selection in main-fragment shading.
- Optional probe cubemap-face ingestion/discovery (`*_px/_nx/_py/_ny/_pz/_nz`) is wired behind a disabled-by-default flag as groundwork; runtime shading still consumes 2D-array probe radiance textures.
- Vulkan now supports per-material reflection overrides (`PROBE_ONLY`, `SSR_ONLY`) via scene color alpha metadata in post reflection resolve.
- Vulkan reflection baseline warning telemetry now includes per-frame override counts (`AUTO`, `PROBE_ONLY`, `SSR_ONLY`, other).
- Vulkan now emits override envelope diagnostics (`REFLECTION_OVERRIDE_POLICY_ENVELOPE`) plus cooldown-gated breach warnings (`REFLECTION_OVERRIDE_POLICY_ENVELOPE_BREACH`) with profile-tuned defaults.
- Vulkan now emits contact-hardening policy diagnostics (`REFLECTION_CONTACT_HARDENING_POLICY`) plus cooldown-gated breach warnings (`REFLECTION_CONTACT_HARDENING_ENVELOPE_BREACH`) with profile-tuned defaults.
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
- Blessed profile trend envelopes now have explicit integration-test assertions for expected window/threshold SLO bounds per profile.
- SSR/TAA reflection ghosting mitigation now surfaces explicit history-policy mode diagnostics (`surface_motion_vectors`, `reflection_region_decay`, `reflection_region_reject`) with threshold and bias telemetry.
- SSR/TAA diagnostics now include explicit reprojection policy and disocclusion-triggered rejection gates (`reflection_space_reject`) for stricter reflected-region history handling.
- Probe quality sweep now reports overlap/priority bleed metrics with configurable envelope gates and breach warnings.
- Probe quality sweep now also reports box-projection coverage, invalid blend/extents counts, and overlap-coverage ratio with breach reasons for projection/blend envelope failures.
- Vulkan probe shading now applies hardened slab-based box projection and distance-priority weighted blending to reduce overlap bleed artifacts in shared probe volumes.
- Planar reflections now expose selective scope + pass-order contract diagnostics (`planar_capture_before_main_sample_before_post`).
- RT reflection minimal lane now exposes single/multi-bounce intent + fallback-chain diagnostics (`rt->ssr->probe` vs `ssr->probe`).
- Transparency/refraction path now has an explicit production stage policy with active fallback behavior for transparent candidates.
- Vulkan reflection runtime now composes execution mode bits per frame (reprojection/reject policy, planar-selective execution, RT lane active/multi-bounce, transparency integration) and applies them in post shader logic.
- Transparency/refraction stage policy is now production-active in Vulkan with explicit fallback chain (`active_probe_fallback` without RT, `active_rt_or_probe` with RT) and typed diagnostics.
- Transparency envelope gates now enforce candidate composition risk (`probeOnly` ratio) with streak/cooldown breach warnings for CI (`REFLECTION_TRANSPARENCY_ENVELOPE_BREACH`).
- Vulkan post reflection push constants now carry RT denoise strength; runtime exposes typed debug accessors for composed mode and denoise strength.
- Vulkan now executes planar selective capture as a real pre-main geometry pass and copies capture to planar history source, using runtime-composed capture bits (`1<<18` capture + `1<<20` geometry-capture execution).
- Vulkan planar sampling now reads from a dedicated planar capture texture lane in post composite (`uPlanarCaptureColor`), decoupled from TAA history velocity.
- Vulkan now emits planar stability envelope diagnostics (`REFLECTION_PLANAR_STABILITY_ENVELOPE` / `REFLECTION_PLANAR_STABILITY_ENVELOPE_BREACH`) with threshold/cooldown controls and typed runtime diagnostics for CI gating.
- Vulkan now emits planar resource/performance gate diagnostics (`REFLECTION_PLANAR_RESOURCE_CONTRACT`, `REFLECTION_PLANAR_PERF_GATES`, `REFLECTION_PLANAR_PERF_GATES_BREACH`) and exposes typed runtime perf counters (`debugReflectionPlanarPerfDiagnostics`) for parser-free CI assertions.
- Planar selective scope policy now supports explicit include/exclude categories (auto/probe-only/ssr-only/other) via backend options and is validated in integration coverage.
- Planar CI coverage now includes interior/outdoor/multi-plane/dynamic-crossing scene matrix checks plus strict-threshold perf-breach assertions.
- Planar path is now `In` for Vulkan scope: mirrored clip-plane camera rerender, selective prepass capture, stability/perf/resource gates, stress coverage, and guarded real-Vulkan signoff runner are in place.
- SSR confidence/reprojection now has explicit envelope diagnostics and cooldown-gated breach warnings for ghost/disocclusion stress.
- Probe upload now supports cadence rotation + max-visible budget + LOD depth-tier tagging in metadata for progressive probe streaming behavior.
- Probe streaming now includes typed frustum/deferred/missing-slot/LOD-tier diagnostics and envelope breach gating (`REFLECTION_PROBE_STREAMING_ENVELOPE_BREACH`) with streak/cooldown controls.
- OpenGL parity for probe slot/array path is not yet implemented.
- Vulkan is the implementation target for current planar hardening; wishlist `In` promotion for planar should be interpreted as Vulkan-path scoped until explicit OpenGL parity lands.
- Planar maturity now has an explicit exit checklist with pass/fail criteria in `docs/planar-in-exit-criteria.md`.
- Planar perf diagnostics now report timing-source state (`gpu_timestamp` vs `frame_estimate`), use Vulkan timestamp query timing when available, and can be configured to require timestamp timing for `In` promotion gating.
- Planar promotion workflow now includes a guarded real-Vulkan signoff runner (`scripts/planar_real_gpu_signoff.sh`) for timestamp-source validation and stress replay outside mock-only CI.
- RT reflections now include an explicit strict-availability gate (`vulkan.reflections.rtRequireActive`) with breach warning (`REFLECTION_RT_PATH_REQUIRED_UNAVAILABLE_BREACH`) plus a dedicated lockdown runner (`scripts/rt_reflections_ci_lockdown_full.sh`).
- RT reflections now also include perf envelope warnings/gates (`REFLECTION_RT_PERF_GATES`, `REFLECTION_RT_PERF_GATES_BREACH`) with typed runtime diagnostics (`debugReflectionRtPerfDiagnostics`).
- RT reflections now include strict multi-bounce availability gating (`vulkan.reflections.rtRequireMultiBounce`) with explicit breach warning (`REFLECTION_RT_MULTI_BOUNCE_REQUIRED_UNAVAILABLE_BREACH`) and mode-bit contract tests.
- RT reflections now include strict dedicated pipeline availability gating (`vulkan.reflections.rtRequireDedicatedPipeline`) with explicit breach warning (`REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH`) and typed diagnostics parity.
- RT reflections now include a dedicated-path preview activation switch (`vulkan.reflections.rtDedicatedPipelineEnabled`) with capability-conditioned activation/signaling (`REFLECTION_RT_DEDICATED_PIPELINE_ACTIVE`) and strict required-path breach behavior.
- RT reflections now emit BLAS/TLAS/SBT lifecycle telemetry (`REFLECTION_RT_PIPELINE_LIFECYCLE`) with typed runtime diagnostics (`debugReflectionRtPipelineDiagnostics`) for scaffolded RT pipeline progression tracking.
- RT reflections now include a long-run guarded real-Vulkan replay runner (`scripts/rt_reflections_real_longrun_signoff.sh`) for duration stress signoff.
- RT reflections now include a promotion replay bundle runner (`scripts/rt_reflections_in_promotion_bundle.sh`) for one-command lockdown + real signoff + long-run validation.
- RT reflections `Partial -> In` promotion scope is locked to Vulkan path; OpenGL RT parity is explicitly out-of-scope for this promotion cycle.
- RT reflections now emit hybrid composition telemetry (`REFLECTION_RT_HYBRID_COMPOSITION`) with typed diagnostics (`debugReflectionRtHybridDiagnostics`) exposing normalized RT/SSR/probe share envelopes.
- RT reflections now include configurable hybrid/denoise/AS envelope gates with cooldown-based breach signaling (`REFLECTION_RT_HYBRID_COMPOSITION_BREACH`, `REFLECTION_RT_DENOISE_ENVELOPE_BREACH`, `REFLECTION_RT_AS_BUDGET_BREACH`) and typed runtime diagnostics for CI assertions.
- RT reflections now include a promotion-ready gate (`REFLECTION_RT_PROMOTION_READY`) and mode-bit contract (`1<<26`) after sustained dedicated/hybrid/denoise/AS/fallback stability.
- RT `Partial -> In` promotion tasks are tracked in `docs/rt-reflections-in-checklist.md`.
- RT reflections now include a guarded real-Vulkan signoff runner (`scripts/rt_reflections_real_gpu_signoff.sh`) for RT lane contract validation on real hardware paths.

## Anti-Aliasing

- FXAA (low-cost post-process) — `In`
- SMAA (edge detection + blend weights + resolve) — `In`
- TAA (full temporal with velocity reprojection) — `In`
- TAA with confidence buffer (decay/recovery on disocclusion) — `In`
- MSAA (selective, per-material opt-in) — `In`
- Hybrid MSAA + temporal (MSAA edges, temporal fill) — `In`
- TUUA (temporal upscaling with AA) — `In`
- TSR (temporal super resolution, internal render scale) — `In`
- DLAA (deep learning AA — native res, neural filter) — `In`
- Per-material reactive masks (alpha, emissive, specular boost) — `In`
- Per-material history clamp control — `In`
- Specular AA (Toksvig roughness filtering) — `In`
- Geometric AA (normal variance filtering for thin features) — `In`
- Alpha-to-coverage for vegetation/hair — `In`

AA notes:

- Vulkan AA now has v2 capability descriptors for all runtime AA modes (`taa`, `tsr`, `tuua`, `msaa_selective`, `hybrid_tuua_msaa`, `dlaa`, `fxaa_low`) with explicit temporal-resource contracts and ordered post injection points.
- AA v2 contracts are validated in composition with shadow/reflection/post descriptors through `RenderCapabilityContractV2Validator` and are covered by `scripts/aa_post_contract_v2_lockdown.sh`.
- Vulkan runtime now emits `AA_POST_CAPABILITY_PLAN_ACTIVE` each frame and exposes typed runtime diagnostics (`aaPostCapabilityDiagnostics()`) so hosts/CI can assert active/pruned AA+post capability planning without warning-string parsing.
- Post stack execution is now modularized with module-owned execution contracts (`vulkan.post`, `vulkan.aa`, `vulkan.reflections`) used for pass IO declaration in `post_composite`.
- Vulkan runtime now emits AA temporal hardening warnings (`AA_TEMPORAL_POLICY_ACTIVE`, `AA_TEMPORAL_ENVELOPE`, `AA_TEMPORAL_ENVELOPE_BREACH`, `AA_TEMPORAL_PROMOTION_READY`) and exposes typed backend-agnostic diagnostics (`aaTemporalPromotionDiagnostics()`).
- Vulkan runtime now emits TUUA/TSR upscale policy + envelope + promotion warnings (`AA_UPSCALE_POLICY_ACTIVE`, `AA_UPSCALE_ENVELOPE`, `AA_UPSCALE_ENVELOPE_BREACH`, `AA_UPSCALE_PROMOTION_READY`) and exposes typed backend-agnostic diagnostics (`aaUpscalePromotionDiagnostics()`).
- Vulkan runtime now emits MSAA-selective/hybrid policy + envelope + promotion warnings (`AA_MSAA_POLICY_ACTIVE`, `AA_MSAA_ENVELOPE`, `AA_MSAA_ENVELOPE_BREACH`, `AA_MSAA_PROMOTION_READY`) and exposes typed backend-agnostic diagnostics (`aaMsaaPromotionDiagnostics()`).
- Vulkan runtime now emits DLAA/specular quality policy + envelope + promotion warnings (`AA_DLAA_POLICY_ACTIVE`, `AA_DLAA_ENVELOPE`, `AA_DLAA_ENVELOPE_BREACH`, `AA_DLAA_PROMOTION_READY`, `AA_SPECULAR_POLICY_ACTIVE`, `AA_SPECULAR_ENVELOPE`, `AA_SPECULAR_ENVELOPE_BREACH`, `AA_SPECULAR_PROMOTION_READY`) and exposes typed backend-agnostic diagnostics (`aaQualityPromotionDiagnostics()`).
- Vulkan runtime now emits geometric + alpha-to-coverage quality policy/envelope/promotion warnings (`AA_GEOMETRIC_POLICY_ACTIVE`, `AA_GEOMETRIC_ENVELOPE`, `AA_GEOMETRIC_ENVELOPE_BREACH`, `AA_GEOMETRIC_PROMOTION_READY`, `AA_A2C_POLICY_ACTIVE`, `AA_A2C_ENVELOPE`, `AA_A2C_ENVELOPE_BREACH`, `AA_A2C_PROMOTION_READY`) and exposes these through the same typed quality diagnostics (`aaQualityPromotionDiagnostics()`).

## Global Illumination

- Static lightmaps (baked, UV2-based) — `Not In Yet`
- Light probes (SH, placed or auto-generated grid) — `Not In Yet`
- Irradiance volumes (3D grid, interpolated) — `Not In Yet`
- Adaptive probe volumes (dynamic density, streaming) — `Partial`
- SSGI (screen-space global illumination) — `Partial`
- Voxel GI (voxel cone tracing, real-time) — `Not In Yet`
- SDF GI (signed distance field tracing) — `Not In Yet`
- RT GI (single-bounce diffuse, denoised) — `Partial`
- RT GI multi-bounce (recursive, accumulation-based) — `Not In Yet`
- Hybrid GI (probes + SSGI fill + RT detail) — `Partial`
- Emissive GI contribution (emissive surfaces as light sources) — `Not In Yet`
- Dynamic sky GI (environment drives indirect lighting, time-of-day responsive) — `Not In Yet`
- Indirect specular from GI (feeds reflection probes or direct sample) — `Not In Yet`

GI notes:

- GI Phase 1 contract/planner scaffold is now present in Vulkan:
  - `VulkanGiCapabilityDescriptorV2` modes: `ssgi`, `probe_grid`, `rtgi_single`, `hybrid_probe_ssgi_rt`
  - deterministic planner + warning emission (`GI_CAPABILITY_PLAN_ACTIVE`)
  - typed backend-agnostic diagnostics (`giCapabilityDiagnostics()`)
  - promotion policy + ready warnings (`GI_PROMOTION_POLICY_ACTIVE`, `GI_PROMOTION_READY`) with tier/default + backend override thresholds (`vulkan.gi.promotionReadyMinFrames`)
  - typed backend-agnostic promotion diagnostics (`giPromotionDiagnostics()`) now include active path flags (`ssgiActive`, `probeGridActive`, `rtDetailActive`) plus SSGI envelope thresholds/streak/cooldown/promotion fields
  - GI now emits SSGI envelope/promotion warnings (`GI_SSGI_POLICY_ACTIVE`, `GI_SSGI_ENVELOPE`, `GI_SSGI_ENVELOPE_BREACH`, `GI_SSGI_PROMOTION_READY`) for CI-gated phase-2A integrity checks
  - checklists + lockdown runner: `docs/gi-phase1-contract-v2-checklist.md`, `docs/gi-phase2-execution-checklist.md`, `scripts/gi_phase1_contract_v2_lockdown.sh`
- GI `Partial` rows currently represent Vulkan contract/planner/telemetry realization with promotion gating; production GI shading/denoise execution paths are the next phase.
- Phase-C profile resolution now consumes runtime GI mode overrides so compiled profile identity includes `gi=...` and GI shader/descriptor composition can vary by active GI capability mode.
- GI phase-2A contract scaffolding now declares explicit SSGI graph IO/resources (`scene_normal` input, `gi_ssgi_buffer` transient output) for `ssgi` and `hybrid_probe_ssgi_rt` modes.
- GI phase-2A now has dedicated lockdown replay (`scripts/gi_phase2_ssgi_lockdown.sh`) and integration coverage for SSGI tier-default vs backend-override envelope/promotion thresholds.
- GI phase-2A SSGI lockdown is now wired into CI as `gi-phase2-ssgi-lockdown` (plus workflow-dispatch toggle `run_gi_phase2_ssgi_lockdown`).
- GI phase-2A now includes GI shader-module realization in composition (`resolveGiIndirect`) with mode-specific module declarations + descriptor-aligned bindings for `ssgi`, `probe_grid`, `rtgi_single`, and `hybrid_probe_ssgi_rt`.
- GI runtime now emits probe-grid policy/envelope/promotion telemetry (`GI_PROBE_GRID_POLICY_ACTIVE`, `GI_PROBE_GRID_ENVELOPE`, `GI_PROBE_GRID_ENVELOPE_BREACH`, `GI_PROBE_GRID_PROMOTION_READY`) with tier-default/override thresholds.
- Typed GI promotion diagnostics now expose probe-grid expected/active ratio, cooldown/streak envelope state, and probe-grid promotion readiness for parser-free CI assertions.
- GI phase-2B probe-grid gating now has a dedicated lockdown runner (`scripts/gi_phase2_probe_lockdown.sh`) and CI lane (`gi-phase2-probe-lockdown`).
- GI runtime now emits RT-detail policy/envelope/promotion telemetry (`GI_RT_DETAIL_POLICY_ACTIVE`, `GI_RT_DETAIL_ENVELOPE`, `GI_RT_DETAIL_ENVELOPE_BREACH`, `GI_RT_DETAIL_PROMOTION_READY`) with tier-default/override thresholds.
- GI runtime now emits explicit RT-detail fallback-chain telemetry (`GI_RT_DETAIL_FALLBACK_CHAIN`) for RT-active vs SSGI-fallback visibility in mode diagnostics.
- GI runtime now emits hybrid composition envelope telemetry (`GI_HYBRID_COMPOSITION`, `GI_HYBRID_COMPOSITION_BREACH`) for expected-vs-active SSGI/probe/RT component coverage in hybrid mode.
- Typed GI promotion diagnostics now expose RT-detail expected/active ratio, cooldown/streak envelope state, and RT-detail promotion readiness for parser-free CI assertions.
- GI phase-2C RT-detail gating now has a dedicated lockdown runner (`scripts/gi_phase2_rt_lockdown.sh`) and CI lane (`gi-phase2-rt-lockdown`).
- GI phase-2 now emits consolidated readiness telemetry (`GI_PHASE2_PROMOTION_READY`) with typed diagnostics state (`phase2PromotionReady`) derived from expected SSGI/probe/RT lane readiness.
- GI phase-2 now has a full lockdown bundle runner (`scripts/gi_phase2_lockdown_full.sh`) and CI lane (`gi-phase2-lockdown-full`) covering SSGI + probe-grid + RT-detail gates.

## Lighting

- Directional lights (sun, moon, with atmosphere interaction) — `In`
- Point lights (omni, attenuated, variable radius) — `In`
- Spot lights (cone, inner/outer angle, projected texture) — `In`
- Area lights (rect, disc, tube — LTC or approximate) — `In`
- IES light profiles (real-world photometric data) — `In`
- Emissive mesh lights (contribute to direct or GI budget) — `In`
- Light cookies / projector textures — `In`
- Volumetric light shafts (god rays, per-light opt-in) — `In`
- Light clustering (screen-space tile, 3D cluster, or hybrid) — `In`
- Light prioritization / budget (per-tier max active lights) — `In`
- Light layers / channels (selective light-to-object assignment) — `In`
- Physically-based light units (lumens, lux, candela, EV) — `In`

Lighting notes:

- Vulkan lighting now has a v2 capability descriptor scaffold (`vulkan.lighting`) with explicit modes: `baseline_directional_point_spot`, `light_budget_priority`, `physically_based_units`, `emissive_mesh`, `phys_units_budget_emissive`.
- Vulkan now emits per-frame lighting capability-plan telemetry (`LIGHTING_CAPABILITY_MODE_ACTIVE`) with parser-friendly active/pruned/signal payload for CI and host inspection.
- Engine runtime API now exposes backend-agnostic typed lighting diagnostics (`lightingCapabilityDiagnostics()`) so hosts/CI can validate planner-resolved mode/signals without warning-string parsing.
- Vulkan now emits lighting budget envelope telemetry (`LIGHTING_BUDGET_ENVELOPE`, `LIGHTING_BUDGET_ENVELOPE_BREACH`) and exposes typed runtime budget diagnostics (`lightingBudgetDiagnostics()`).
- Vulkan now emits lighting budget policy/promoted stability telemetry (`LIGHTING_BUDGET_POLICY`, `LIGHTING_BUDGET_PROMOTION_READY`) with cooldown-gated breach behavior and typed runtime promotion diagnostics (`lightingPromotionDiagnostics()`).
- Vulkan now emits physically-based-unit and emissive policy telemetry (`LIGHTING_PHYS_UNITS_POLICY`, `LIGHTING_EMISSIVE_POLICY`, `LIGHTING_EMISSIVE_ENVELOPE_BREACH`) and exposes typed runtime emissive diagnostics (`lightingEmissiveDiagnostics()`).
- Vulkan now emits physically-based-unit and emissive promotion readiness (`LIGHTING_PHYS_UNITS_PROMOTION_READY`, `LIGHTING_EMISSIVE_PROMOTION_READY`) with tier-profile defaults, backend override precedence, and typed promotion diagnostics fields.
- Vulkan now emits baseline directional/point/spot promotion readiness (`LIGHTING_BASELINE_PROMOTION_READY`) with tier-profile defaults, backend override precedence (`vulkan.lighting.baselinePromotionReadyMinFrames`), and typed promotion diagnostics fields.
- Vulkan now emits consolidated lighting phase-2 readiness (`LIGHTING_PHASE2_PROMOTION_READY`) when budget, physically-based units, and emissive policy (if enabled) are jointly stable.
- Vulkan lighting planner/runtime telemetry now includes advanced-mode policy signals for area-approx, IES, cookies, volumetric shafts, clustering, and light layers (`LIGHTING_ADVANCED_POLICY`) with tier-gated active/pruned capability reporting.
- Lighting phase-2 promotion now has a strict lockdown runner (`scripts/lighting_phase2_lockdown.sh`) and always-on CI lane (`lighting-phase2-lockdown`).
- Lighting advanced-stack gating now has a strict lockdown runner (`scripts/lighting_advanced_lockdown.sh`) and always-on CI lane (`lighting-advanced-lockdown`).
- Lighting now has a full lockdown bundle runner (`scripts/lighting_lockdown_full.sh`) and always-on CI lane (`lighting-lockdown-full`) for contract + phase2 + advanced gating.
- Vulkan now emits advanced-stack promotion readiness (`LIGHTING_ADVANCED_PROMOTION_READY`) with typed diagnostics (`lightingPromotionDiagnostics()`) and configurable min-frame thresholds (`vulkan.lighting.advancedPromotionReadyMinFrames`).
- Backend-agnostic lighting capability diagnostics now expose typed advanced-policy activation flags (`areaApproxEnabled`, `iesProfilesEnabled`, `cookiesEnabled`, `volumetricShaftsEnabled`, `clusteringEnabled`, `lightLayersEnabled`) without warning-string parsing.
- Vulkan now emits strict advanced required-path policy/breach warnings (`LIGHTING_ADVANCED_REQUIRED_PATH_POLICY`, `LIGHTING_ADVANCED_REQUIRED_UNAVAILABLE_BREACH`) with configurable streak/cooldown thresholds (`vulkan.lighting.advancedRequireActive`, `vulkan.lighting.advancedRequireMinFrames`, `vulkan.lighting.advancedRequireCooldownFrames`).
- Backend-agnostic advanced diagnostics now expose required-vs-active coverage and required-path breach state (`requiredAdvancedCapabilityCount`, `advancedRequire*`, `advancedRequiredUnavailableBreached`) for parser-free CI assertions.
- Vulkan now emits advanced expected-vs-active envelope telemetry (`LIGHTING_ADVANCED_ENVELOPE`, `LIGHTING_ADVANCED_ENVELOPE_BREACH`) with tier-profile defaults and configurable streak/cooldown thresholds (`vulkan.lighting.advancedEnvelopeWarnMinFrames`, `vulkan.lighting.advancedEnvelopeCooldownFrames`).
- Backend-agnostic advanced diagnostics now expose advanced-envelope state (`advancedEnvelope*`) for parser-free CI assertions and cooldown-aware gating.
- Vulkan now emits per-feature advanced lighting policy/breach/promotion warnings for area/IES/cookies/volumetric/clustering/layers (`LIGHTING_*_POLICY`, `LIGHTING_*_ENVELOPE_BREACH`, `LIGHTING_*_PROMOTION_READY`) with shared tier-default/override controls (`vulkan.lighting.advancedFeatureWarnMinFrames`, `vulkan.lighting.advancedFeatureCooldownFrames`, `vulkan.lighting.advancedFeaturePromotionReadyMinFrames`).
- Backend-agnostic advanced diagnostics now expose parser-free feature lists (`expectedFeatures`, `activeFeatures`, `breachedFeatures`, `promotionReadyFeatures`) for CI assertions without warning-string parsing.
- Engine runtime now exposes typed advanced-lighting diagnostics (`lightingAdvancedDiagnostics()`) including expected-vs-active advanced capability coverage and promotion streak state.
- Lighting advanced-stack contract modes now declare concrete Vulkan descriptor/uniform/resource requirements (cluster grid, IES profile buffer, cookie atlas, layer mask buffer) for composition-time validation and pipeline planning.
- Phase-C profile resolution now consumes runtime planner-resolved lighting mode overrides, so compiled profile keys/shader+descriptor composition track active lighting capability mode per frame.
- Lighting telemetry now applies tier-profile defaults with backend-option override precedence and emits compact profile summary telemetry (`LIGHTING_TELEMETRY_PROFILE_ACTIVE`).
- Lighting integration coverage now explicitly asserts backend-option precedence over tier defaults for budget/emissive thresholds.
- Lighting v2 contract coverage is tracked in `docs/lighting-capability-v2-checklist.md` and validated in composition with shadow/reflection/aa/post/gi descriptors.
- Phase C profile compilation now includes lighting mode in profile identity (`lighting=...`) and composes lighting shader/descriptor requirements from the resolved mode.
- Lighting contract gating is now automated via `scripts/lighting_contract_v2_lockdown.sh` and CI lane `lighting-contract-v2-lockdown`.

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

Post notes:

- Vulkan post stack now has v2 capability descriptors for core modules (`tonemap`, `bloom`, `ssao`, `smaa`, `taa_resolve`, `fog_composite`) with explicit pass/read-write/resource declarations.
- Post v2 contracts are validated in composition with AA + shadow + reflection and are enforced by the always-on CI lane `aa-post-contract-v2-lockdown`.

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
- RT reflections (single-bounce, multi-bounce, denoised) — `In`
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
- Shader module composition (assemble fragment from contributed hooks) — `In`
- Descriptor layout composition (per-pass, from declared requirements) — `In`
- Profile/preset system (blessed tier configurations) — `In`
- Graph validation (reject illegal feature combinations at build time) — `Partial`
- Hot-reload of individual capability modules (dev workflow) — `Not In Yet`
- Telemetry per-feature (budget, timing, quality metrics) — `Partial`
- Compare harness per-feature and per-profile (regression gates) — `In`
- Structural guardrails (class line-limit + package hygiene checks) — `In`

---

Notes:

- These statuses are intentionally conservative and should be refined as each domain is validated.
- For pipeline and migration policy context, see:
  - `docs/adr/0002-feature-composition-and-pipeline-migration-policy.md`
  - `docs/architecture/vulkan-render-pipeline-current.md`
- Structure guardrails now run via `scripts/java_structure_guardrails.sh` (Vulkan-scoped by default; optional full-tree scan with `SCOPE=all`).
- Phase C runtime now composes descriptor-set layouts from capability requirements, compiles/caches profile tuples (`tier + capability modes`), and binds assembled profile shader sources through swapchain pipeline creation in Vulkan.

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
