# Post-Processing In Promotion Checklist

Date: 2026-02-20  
Scope: Vulkan-first promotion of post stack vertical from current mixed `Partial` state to `In` across listed post-processing capabilities.

## Phase 0: Baseline Contract/Planner Guardrails

- [x] Post execution modularized with module-owned execution contracts (`vulkan.post`, `vulkan.aa`, `vulkan.reflections`).
- [x] Core post v2 capability descriptors in place (`tonemap`, `bloom`, `ssao`, `smaa`, `taa_resolve`, `fog_composite`).
- [x] AA/post planner exposes active/pruned post capability IDs (`AA_POST_CAPABILITY_PLAN_ACTIVE`, `aaPostCapabilityDiagnostics()`).
- [x] Existing contract lockdown lane active (`scripts/aa_post_contract_v2_lockdown.sh`, CI lane `aa-post-contract-v2-lockdown`).

## Phase A: Promote Existing Partial Core Lanes to In

Targets:
- HDR tonemap
- exposure
- bloom
- sharpening
- SSAO
- SSAO temporal accumulation
- volumetric fog

Execution checklist:
- [x] Promote tonemap policy from scaffold/partial to production execution path + typed diagnostics envelope.
- [x] Promote exposure policy (fixed/auto) with thresholded stability envelope + promotion-ready signal.
- [x] Promote bloom quality/power envelope (threshold/strength/stability) with cooldown-gated breach warnings.
- [x] Promote sharpening policy with mode-strength envelope and artifact guard thresholds.
- [x] Promote SSAO quality envelopes (radius/bias/power + temporal accumulation stability).
- [x] Promote volumetric fog policy/envelope (density/noise/stability/perf budget).
- [x] Add/extend integration tests for each promoted lane and assert promotion-ready transitions.
- [x] Add a dedicated core-post lockdown script and CI lane for Phase A (`scripts/post_phaseA_core_lockdown.sh`, CI lane `post-phasea-core-lockdown`).

Exit gate:
- [x] All Phase A lanes emit production policy + envelope + promotion-ready telemetry and pass lockdown.

## Phase B: Close Remaining Cinematic/Utility Post Gaps (Not In Yet -> Partial/In)

Targets:
- depth of field
- motion blur
- chromatic aberration
- film grain
- vignette
- lens flare
- color grading
- panini projection
- lens distortion
- cloud shadows
- screen-space bent normals

Execution checklist:
- [ ] Add/finish production shader realization per effect in post composite path.
- [ ] Add per-effect policy/envelope/promotion warning families.
- [x] Expose typed diagnostics (parser-free assertions) for cinematic effect activation/health (`postCinematicPromotionDiagnostics()`).
- [x] Add per-effect or grouped lockdown tests for artifact/perf envelopes (grouped cinematic envelope gating in `VulkanAaPostCapabilityPlanIntegrationTest`).
- [x] Add CI lane for cinematic post lockdown (`scripts/post_phaseB_cinematic_lockdown.sh`, CI lane `post-phaseb-cinematic-lockdown`).

Exit gate:
- [ ] All Phase B lanes have explicit production policy + envelope + promotion-ready coverage and pass CI lockdown.

## Phase C: Full Post Bundle Promotion

- [x] Add full-post lockdown bundle script (core + cinematic + stress/replay gates) (`scripts/post_lockdown_full.sh`).
- [x] Add CI lane `post-lockdown-full` (workflow-dispatch + schedule coverage).
- [ ] Run bundle in strict mode and capture promotion artifacts.
- [ ] Update `wish_list.md` post rows from `Partial` to `In` where gates pass.
- [ ] Document Vulkan-scope caveat where backend parity is pending.

Final exit gate:
- [ ] Post-processing vertical marked `In` (Vulkan scope) with green lockdown bundle and docs updated.

## Verification Commands (Current)

- `bash scripts/aa_post_contract_v2_lockdown.sh`
- `bash scripts/post_phaseA_core_lockdown.sh`
- `mvn -pl engine-impl-vulkan -am -Dtest=VulkanPostCompositePassRecorderTest,VulkanAaPostCapabilityPlanIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
