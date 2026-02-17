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
- allocator telemetry for reuse/eviction pressure:
  - `shadowAllocatorAssignedLights`
  - `shadowAllocatorReusedAssignments`
  - `shadowAllocatorEvictions`

Still pending:
- full production multi-point cubemap scalability (beyond current matrix/layer bounds)
- true VSM/EVSM moment filtering/sampling pipeline (moment-atlas resource allocation + texture descriptor binding + provisional runtime sampling are now live, including one-time command-buffer neutral initialization and fallback guards)
- true PCSS blocker/penumbra pipeline
- true contact-shadow trace/denoise pipeline
- hardware RT shadow traversal/denoise pipeline

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
- Status: In progress (initial cadence controls wired; per-light age/priority queue still pending).

3. Static/dynamic shadow cache layering
- strict invalidation and reuse guarantees.

4. Long-run validation
- motion shimmer and stale-shadow regression sweeps on real Vulkan.

## Phase 3: Production Filter Paths

1. VSM/EVSM
- moment render targets, prefilter/blur, variance resolve, bleed control.

2. PCSS
- blocker search + penumbra estimation + tiered kernel path.

3. Contact shadows
- screen-space near-contact trace + temporal stabilization.

## Phase 4: Hardware RT Shadows

1. Capability-gated RT path
- dedicated RT traversal pipeline and denoiser.

2. Fallback stack
- deterministic fallback to non-RT paths when unsupported.

3. Profile gating
- quality-tier + hardware profile controls, not global-only switches.

## Phase 5: CI, Thresholds, and Lockdown

1. Real Vulkan repeated sampling
- lock thresholds from stable multi-run datasets.

2. CI matrix expansion
- D16/D32 divergence, cadence/static-cache correctness, long-run motion stress.

3. Report quality
- explicit fail reasons and metric drift windows.

## Immediate Next Steps

1. Raise matrix/layer hard limits safely for Vulkan shadow resources.
2. Add scheduler queue metadata for per-light age/priority.
3. Wire cadence policy from metadata into actual shadow update decisions.
