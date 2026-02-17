# Shadows Guide

Last updated: February 17, 2026

## 1. What Shadowing Supports
DynamicLightEngine currently supports:
- Cascaded directional shadows
- Spot-light shadow baseline with local shadow-atlas sampling in OpenGL (`MAX_LOCAL_SHADOWS=4`)
- Point-light shadow baseline (with backend-dependent maturity/perf)
- PCF filtering and depth-bias controls
- Shadow policy budgeting per quality tier (`maxShadowedLocalLights`)
- Per-type shadow bias scaling (normal/slope bias multipliers)
- Quality-tier shadow degradation/warnings

## 2. Quick Start

### Run sample host with shadows on
```bash
mvn -f engine-host-sample/pom.xml exec:java \
  -Dexec.args="vulkan --shadow=on --shadow-cascades=4 --shadow-pcf=5 --shadow-bias=0.001 --shadow-res=2048"
```

### Interactive shadow tuning in sample host
```bash
mvn -f engine-host-sample/pom.xml exec:java \
  -Dexec.args="vulkan --interactive --shadow=on"
```

Interactive commands:
- `shadow on|off`
- `shadow_cascades <1-4>`
- `shadow_pcf <1-9>`
- `shadow_bias <float>`
- `shadow_res <256-4096>`

## 2.1 Programmatic Setup (Java API)

Attach `ShadowDesc` to lights in your `SceneDescriptor`.

```java
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.Vec3;

ShadowDesc shadow = new ShadowDesc(
    2048,    // mapResolution
    0.0008f, // depthBias
    5,       // pcfKernelSize
    4        // cascadeCount
);

LightDesc directional = new LightDesc(
    "sun",
    new Vec3(7f, 18f, 6f),
    new Vec3(1f, 0.98f, 0.95f),
    1.1f,
    220f,
    true,
    shadow,
    LightType.DIRECTIONAL,
    new Vec3(-0.35f, -1.0f, -0.25f),
    15f,
    30f
);
```

For spot lights, set `LightType.SPOT` and cone angles (`innerConeDegrees`, `outerConeDegrees`).

## 2.2 Recommended Starting Values

Balanced default:
- `mapResolution=2048`
- `depthBias=0.0008..0.0012`
- `pcfKernelSize=3..5`
- `cascadeCount=3`

Quality-first:
- `mapResolution=2048..4096`
- `depthBias=0.0005..0.0010`
- `pcfKernelSize=5`
- `cascadeCount=4`

Performance-first:
- `mapResolution=1024`
- `depthBias=0.0010..0.0018`
- `pcfKernelSize=3`
- `cascadeCount=2`

## 3. Validation Rules and Constraints

Scene validator enforces:
- `shadow.mapResolution > 0`
- `shadow.pcfKernelSize >= 1`
- `shadow.cascadeCount >= 1`

Sample host clamps:
- `shadow_res`: `256..4096`
- `shadow_cascades`: `1..4`
- `shadow_pcf`: `1..9`
- `shadow_bias`: `0.00002..0.02`

## 4. Backend Notes

- Both OpenGL and Vulkan support core shadow baseline behavior.
- Runtime can emit `SHADOW_QUALITY_DEGRADED` on lower tiers.
- Runtime emits `SHADOW_POLICY_ACTIVE` with primary shadow light/type and current local-light budget/selection.
- `SHADOW_POLICY_ACTIVE` now also includes atlas planning telemetry:
  - `atlasTiles=<allocated>/<capacity>`
  - `atlasUtilization=<0..1>`
  - `atlasEvictions=<count>`
  - `atlasMemoryD16Bytes=<bytes>`
  - `atlasMemoryD32Bytes=<bytes>`
  - `shadowUpdateBytesEstimate=<bytes>`
- Vulkan shadow depth format can be selected for validation runs with:
  - `-Ddle.vulkan.shadow.depthFormat=d16`
  - `-Ddle.vulkan.shadow.depthFormat=d32`
- Non-directional shadow coverage is supported at baseline level but may differ in quality/perf characteristics by backend/profile.
- OpenGL now applies local spot shadows through an atlas path in the main local-light loop.
- Vulkan now renders multi-local **spot** shadow layers and supports tier-bounded point-cubemap rendering (`HIGH`: 1 cubemap / 6 faces, `ULTRA`: 2 cubemaps / 12 faces). Local point-light shadow layer assignments now map to cubemap-face shadow matrices per allocated layer base; full production per-light atlas/cubemap parity remains in rollout.
- Shadow quality-path requests are now first-class backend options:
  - `vulkan.shadow.filterPath=pcf|pcss|vsm|evsm`
  - `vulkan.shadow.contactShadows=true|false`
  - `vulkan.shadow.pcssSoftness=0.25..2.0` (default `1.0`)
  - `vulkan.shadow.momentBlend=0.25..1.5` (default `1.0`)
  - `vulkan.shadow.momentBleedReduction=0.25..1.5` (default `1.0`)
  - `vulkan.shadow.contactStrength=0.25..2.0` (default `1.0`)
  - `vulkan.shadow.contactTemporalMotionScale=0.1..3.0` (default `1.0`)
  - `vulkan.shadow.contactTemporalMinStability=0.2..1.0` (default `0.42`)
  - `vulkan.shadow.rtMode=off|optional|force|bvh|bvh_dedicated`
  - `vulkan.shadow.rtBvhStrict=true|false` (default `false`; fail fast if `rtMode=bvh` lacks BVH capability or if `rtMode=bvh_dedicated` is requested before dedicated BVH traversal is available)
  - `vulkan.shadow.rtDenoiseStrength=0..1` (default `0.65`, hybrid traversal denoise shaping)
  - `vulkan.shadow.rtRayLength=1..500` (default `80`, hybrid traversal march distance shaping)
  - `vulkan.shadow.rtSampleCount=1..16` (default `2`, hybrid traversal step budget shaping)
  - `vulkan.shadow.maxShadowedLocalLights=0..8` (`0` = tier default)
  - `vulkan.shadow.maxLocalShadowLayers=0..24` (`0` = tier default scheduler budget)
  - `vulkan.shadow.maxShadowFacesPerFrame=0..24` (`0` = tier default scheduler budget)
  - `vulkan.shadow.scheduler.enabled=true|false` (`true` default)
  - `vulkan.shadow.scheduler.heroPeriod=1..16` (default `1`)
  - `vulkan.shadow.scheduler.midPeriod=1..32` (default `2`)
  - `vulkan.shadow.scheduler.distantPeriod=1..64` (default `4`)
  - `vulkan.shadow.directionalTexelSnapEnabled=true|false` (default `true`)
  - `vulkan.shadow.directionalTexelSnapScale=0.25..4.0` (default `1.0`)
  Runtime tracks and reports these requests. Production-active runtime filter path now follows requested mode (`pcf|pcss|vsm|evsm`) with mode-specific shader shaping for PCSS/VSM/EVSM and strengthened contact-shadow modulation. Vulkan now also runs a dedicated layered moment pipeline for `vsm|evsm` (moment write + mip prefilter + mip-aware sampling), including light-bleed reduction and tuned penumbra/contact shaping. Capability-gated RT hybrid shadow traversal/denoise shaping is now wired (`rtActive=true` only when traversal extensions are available); full dedicated BVH traversal remains a separate step.
  Advanced quality tuning is now runtime-configurable via backend options (`pcssSoftness`, `momentBlend`, `momentBleedReduction`, `contactStrength`) without shader edits. Current Vulkan shading path also applies neighborhood-weighted plus edge-aware moment denoise, deep+ultra wide bilateral consistency passes, moment neighborhood bounds clamping to reduce light leaking, leak-risk-adaptive anti-bleed shaping, moment-informed PCSS blocker estimation with a secondary ring refinement pass (improved penumbra stability), dedicated BVH-mode traversal approximation functions (`bvhTraversalVisibilityApprox`, `bvhDedicatedTraversalVisibilityApprox`) with RT denoise refinement, and motion-adaptive contact stabilization with reject-weighted history blending (`contactReject`, `contactTemporalHistoryWeight`) to reduce high-motion flicker and edge bleeding.
  Motion-adaptive contact stabilization is further tunable via `contactTemporalMotionScale` and `contactTemporalMinStability`.
  Vulkan fragment texture descriptor sets now include a dedicated shadow-moment binding (`set=1,binding=8`) so moment-map sampling can be wired without further descriptor-layout churn.

Shadow scheduler override examples:
- Raise local point shadow concurrency ceiling:
  - `-Dvulkan.shadow.maxLocalShadowLayers=24`
- Cap point-shadow face work per frame:
  - `-Dvulkan.shadow.maxShadowFacesPerFrame=6`
- Adjust cadence policy:
  - `-Dvulkan.shadow.scheduler.heroPeriod=1 -Dvulkan.shadow.scheduler.midPeriod=2 -Dvulkan.shadow.scheduler.distantPeriod=6`

Scheduler behavior notes:
- Vulkan scheduler now uses cadence + age/priority ordering for local shadow lights.
- Runtime warning telemetry includes:
  - `maxShadowedLocalLightsConfigured`
  - `shadowSchedulerFrameTick`
  - `renderedShadowLightIds`
  - `deferredShadowLightCount`
  - `deferredShadowLightIds`
  - `shadowMomentAtlasBytesEstimate` (non-zero when `vsm`/`evsm` is requested)
  - `runtimeFilterPath` (active filter path used by runtime shading)
  - `momentFilterEstimateOnly`
  - `momentPipelineRequested` / `momentPipelineActive`
  - `momentResourceAllocated` / `momentResourceFormat`
  - `momentInitialized`
  - `momentPhase` (`pending|initializing|active`)
  - warning codes: `SHADOW_MOMENT_PIPELINE_PENDING`, `SHADOW_MOMENT_PIPELINE_INITIALIZING`, `SHADOW_MOMENT_APPROX_ACTIVE`, `SHADOW_RT_PATH_REQUESTED`, `SHADOW_RT_PATH_FALLBACK_ACTIVE`, `SHADOW_RT_BVH_PIPELINE_PENDING`
  - RT groundwork telemetry: `rtDenoiseStrength`, `rtRayLength`, `rtSampleCount`
  - `directionalTexelSnapEnabled`
  - `directionalTexelSnapScale`
  - `shadowAllocatorAssignedLights`
  - `shadowAllocatorReusedAssignments`
  - `shadowAllocatorEvictions`

Current default local shadow budgets by tier:
- `LOW`: 1 local shadow light
- `MEDIUM`: 2 local shadow lights
- `HIGH`: 3 local shadow lights
- `ULTRA`: 4 local shadow lights

## 4.1 Implementation Policy Guidance

Atlas packing/eviction expectations:
- Keep atlas dimensions and tile requests power-of-two aligned (`256/512/1024/...`).
- Sort tile requests descending by tile size before placement.
- Prefer stable placement reuse for lights already in atlas.
- Evict least-recently-visible entries first under pressure.
- Do not clear evicted pages eagerly unless needed for a new placement.

Texel-snapping runtime (directional cascades):
- Snap cascade shadow-camera translation to shadow texel units to reduce shimmer.
- Use a world-space snap step derived from cascade extent and map resolution.
- Recommended form: `snapped = floor(position / texelSize) * texelSize`.
- Keep snapping enabled for gameplay cameras; disable only in offline debug if needed.
- Runtime controls:
  - `-Dvulkan.shadow.directionalTexelSnapEnabled=true|false`
  - `-Dvulkan.shadow.directionalTexelSnapScale=1.0`

Static vs dynamic shadow layers:
- Use a static layer for static geometry + static light contributions.
- Use a dynamic layer for moving casters/receivers and animated light transforms.
- Composite static + dynamic shadow factors in final lighting pass.
- Invalidate static pages only when relevant static content or light state changes.

Hero/full-rate vs distant/throttled updates:
- Hero/near lights: update every frame.
- Mid-range lights: update at reduced cadence (for example every 2-4 frames).
- Distant/non-critical lights: update at low cadence (for example ~15 Hz).
- Promote throttled lights to full-rate immediately on rapid visibility/importance changes.
- Keep cadence policy tier-aware so LOW/MEDIUM tiers throttle more aggressively than HIGH/ULTRA.

## 5. Troubleshooting

If shadows look too noisy/flickery:
- Increase `pcfKernelSize` (e.g. 3 -> 5).
- Slightly raise `depthBias`.
- Increase `mapResolution`.
- Reduce extreme camera/light angles where possible.

If shadows look detached/peter-panning:
- Lower `depthBias`.
- Keep PCF moderate before increasing bias too much.

If performance drops:
- Lower `mapResolution` first.
- Reduce `cascadeCount`.
- Reduce `pcfKernelSize`.

## 6. Validation Commands

Automated shadow CI matrix (policy + stress + depth-format checks):
```bash
./scripts/shadow_ci_matrix.sh
```

The same flow is available through the AA runner wrapper:
```bash
./scripts/aa_rebaseline_real_mac.sh shadow-matrix
```

Optional real Vulkan matrix/long-run:
```bash
DLE_SHADOW_CI_REAL_MATRIX=1 \
DLE_SHADOW_CI_LONGRUN=1 \
DLE_COMPARE_VULKAN_MODE=real \
./scripts/shadow_ci_matrix.sh
```

Guarded threshold-lock generation from real-Vulkan long-run outputs:
```bash
./scripts/shadow_lock_thresholds_guarded.sh artifacts/compare/shadow-real-longrun artifacts/compare/shadow-threshold-lock
```

Production profile quality sweeps + threshold lock (guarded real Vulkan):
```bash
./scripts/shadow_production_quality_sweeps.sh
```
The default production sweep set includes `rtMode=bvh` and `rtMode=bvh_dedicated` lanes for readiness tracking.

Optional strict BVH enforcement in sweep runs:
```bash
DLE_SHADOW_PROD_SWEEP_RT_BVH_STRICT=1 ./scripts/shadow_production_quality_sweeps.sh
```

Optional auto-promotion of generated recommendations into repo defaults:
```bash
DLE_SHADOW_PROD_SWEEP_PROMOTE_MODE=real ./scripts/shadow_production_quality_sweeps.sh
```

Parity/stress suite (includes shadow profiles):
```bash
mvn -pl engine-host-sample -am test \
  -Ddle.compare.tests=true \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Real Vulkan compare run:
```bash
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

Targeted shadow stress outputs are generated in compare artifacts:
- `shadow-cascade-stress`
- `fog-shadow-cascade-stress`
- `smoke-shadow-cascade-stress`

## 7. Remaining Rollout Gaps

- Tier/override-bounded multi-point cubemap concurrency is now active in Vulkan local-shadow layering (`HIGH`: 1, `ULTRA`: 2, overrides controlled by scheduler/budgets); further scale-up beyond current scheduler/budget caps is still pending.
- Dedicated VSM/EVSM moment-atlas write/prefilter pipeline is active in Vulkan (layered moment write, per-frame mip prefilter chain, mip-aware moment sampling).
- Dedicated hardware RT shadow traversal/denoise path is still pending (request+fallback tracking is live).
