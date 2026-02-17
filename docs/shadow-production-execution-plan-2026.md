# Shadow Production Execution Plan (2026)

Last updated: February 17, 2026.

This plan tracks the remaining shadow-management gap to production quality:
- full multi-point cubemap scalability beyond default tier caps
- true production VSM/EVSM/PCSS/contact/RT shadow implementations

## Goal

Ship a deterministic, scalable shadow stack across OpenGL/Vulkan with:
- many concurrent local shadow lights
- stable quality under motion
- profile-gated high-end quality paths
- strong CI gates and clear fallback behavior

## Current Status

Implemented now:
- Vulkan local multi-spot layered path
- Vulkan tier-bounded point-cubemap rendering (HIGH: 1, ULTRA: 2)
- shadow filter/contact/RT mode request plumbing and runtime reporting
- scheduler override controls (execution milestone 1):
  - `vulkan.shadow.maxShadowedLocalLights`
  - `vulkan.shadow.maxLocalShadowLayers`
  - `vulkan.shadow.maxShadowFacesPerFrame`
  - hard ceiling now expanded to `24` layers/faces for Vulkan rollout validation
- cadence scheduler controls (execution milestone 2):
  - `vulkan.shadow.scheduler.enabled`
  - `vulkan.shadow.scheduler.heroPeriod`
  - `vulkan.shadow.scheduler.midPeriod`
  - `vulkan.shadow.scheduler.distantPeriod`
  - warning telemetry: `shadowSchedulerFrameTick`
  - per-light age/priority ordering backed by runtime `renderedShadowLightIds` tracking
- scheduler backlog telemetry: `deferredShadowLightCount`, `deferredShadowLightIds`
- moment-atlas budget telemetry for `vsm`/`evsm` requests: `shadowMomentAtlasBytesEstimate`
- explicit requested-vs-active filter telemetry for shadow quality-path reporting:
  - `filterPath`
  - `runtimeFilterPath`
  - `momentFilterEstimateOnly`
  - `momentPipelineRequested`
  - `momentPipelineActive`
  - note: `vsm|evsm` now correctly mark `momentPipelineRequested=true`, so pending/initializing/active warnings reflect real runtime state
- allocator telemetry for reuse/eviction pressure:
  - `shadowAllocatorAssignedLights`
  - `shadowAllocatorReusedAssignments`
  - `shadowAllocatorEvictions`
- RT mode gating now distinguishes generic traversal support from BVH-capable support for `rtMode=bvh` requests (falls back deterministically when BVH capability is unavailable).

Still pending:
- full production multi-point cubemap scalability (beyond current matrix/layer bounds)
- hardware-quality tuning/validation for the dedicated VSM/EVSM moment pipeline (write + mip prefilter is now active; neighborhood-weighted denoise/filtering landed, thresholds/quality sweeps remain)
- hardware RT shadow traversal/denoise pipeline (capability-gated hybrid traversal path is now wired end-to-end with runtime tuning inputs for denoise/ray-length/sample-count; full dedicated BVH traversal/denoiser stack remains)

## Phased Execution

## Phase 1: Scheduler and Capacity Foundation

1. Scheduler budgets and policy controls
- Add runtime-configurable local shadow layer budget.
- Add per-frame shadow-face budget for point-shadow updates.
- Emit policy telemetry and warnings for selected vs rendered shadow work.

2. Render-path alignment
- Ensure shadow pass submission respects configured face/layer budget.
- Keep deterministic ordering and stable invalidation behavior.

3. Tests and validation
- Unit tests for budget parsing and mapper policy behavior.
- Integration tests for rendered local/spot/point counters and warnings.

Status: In progress (started and partially landed).

## Phase 2: Multi-Point Cubemap Scalability

1. Dynamic allocator
- Move from fixed assumptions to dynamic cubemap face allocation and reuse.

2. Cadence-aware scheduling
- Hero/full-rate, mid/throttled, distant/low-rate updates with promotion rules.
- Status: In progress (cadence controls plus per-light age/priority ordering and stale-light bypass promotion are active; deeper per-scene tuning/lockdown continues).
- Mixed-type parity (spot vs point) in top-N selection is now enforced under eligible budgets to avoid pre-scheduler starvation.

3. Static/dynamic shadow cache layering
- strict invalidation and reuse guarantees.

4. Long-run validation
- motion shimmer and stale-shadow regression sweeps on real Vulkan.

## Phase 3: Production Filter Paths

1. VSM/EVSM
- moment render targets, prefilter/blur, variance resolve, bleed control.
 - Status: In progress (dedicated moment write + mip prefilter + mip-aware sampling + neighborhood-weighted denoise active, now extended with deep/ultra wide bilateral consistency passes, moment-neighborhood bounds clamp, and leak-risk anti-bleed shaping for higher stability).

2. PCSS
- blocker search + penumbra estimation + tiered kernel path.
 - Status: In progress (edge-aware penumbra neighborhood blend active; blocker estimate is now moment-informed when moment data exists, but a full final production blocker-search path is still pending).

3. Contact shadows
- screen-space near-contact trace + temporal stabilization.
 - Status: In progress (runtime-strength shaping active; motion-adaptive stabilization is active and now includes reject-weighted history blending, while full dedicated history-buffer stabilization remains pending).

4. PCSS refinement depth
- secondary blocker ring refinement and penumbra stability passes.
 - Status: In progress (moment-informed blocker estimate + secondary ring refinement path active; full final production blocker-search parity still pending).

## Phase 4: Hardware RT Shadows

1. Capability-gated RT path
- dedicated RT traversal pipeline and denoiser.
 - Note: current RT path now exposes two approximation lanes with denoise refinement:
   - `rtMode=bvh` -> `bvhTraversalVisibilityApprox` (hybrid traversal path)
   - `rtMode=bvh_dedicated` -> `bvhDedicatedTraversalVisibilityApprox` (dedicated-preview traversal path)
 - Runtime still emits `SHADOW_RT_BVH_PIPELINE_PENDING` for both BVH modes because full production hardware BVH traversal + dedicated denoiser pipeline is still pending.
 - Strict rollout guard via `vulkan.shadow.rtBvhStrict=true` remains available:
   - `rtMode=bvh` fails fast if BVH capability is unavailable.
   - `rtMode=bvh_dedicated` fails fast until the full dedicated hardware BVH traversal pipeline is landed.

2. Fallback stack
- deterministic fallback to non-RT paths when unsupported.

3. Profile gating
- quality-tier + hardware profile controls, not global-only switches.

## Phase 5: CI, Thresholds, and Lockdown

1. Real Vulkan repeated sampling
- lock thresholds from stable multi-run datasets.

2. CI matrix expansion
- D16/D32 divergence, cadence/static-cache correctness, long-run motion stress.
 - Status: In progress (always-on guarded lanes are live for shadow matrix + real-Vulkan long-run + production quality sweeps on `push`/`pull_request`/`schedule`; guarded threshold-lock emission is integrated in both long-run and quality-sweep scripts; one-shot lockdown runner `scripts/shadow_ci_lockdown_full.sh` is now wired as a dedicated scheduled/manual CI lane).

3. Report quality
- explicit fail reasons and metric drift windows.

## Immediate Next Steps

1. Add temporal accumulation/rejection control for contact-shadow stability in high-motion sweeps.
2. Add deeper moment-filter stress tests (`vsm`/`evsm`) in compare-harness matrix and lock updated thresholds.
3. Implement dedicated BVH RT shadow traversal + denoise path behind capability/profile gating (current hybrid RT traversal remains fallback-compatible baseline).
4. Keep `scripts/shadow_production_quality_sweeps.sh` outputs feeding threshold-lock recommendations and periodically promote stable recommendations into repo-level locked threshold profiles.
 - Implementation now includes `scripts/promote_compare_thresholds.sh` and optional sweep auto-promotion (`DLE_SHADOW_PROD_SWEEP_PROMOTE_MODE=real|mock`) to persist stable recommendations into tracked defaults.
5. Use `DLE_SHADOW_PROD_SWEEP_RT_BVH_STRICT=1` on BVH-capable hosts to make sweep lanes fail fast when strict BVH readiness regresses.
