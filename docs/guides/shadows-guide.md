# Shadows Guide

Last updated: February 17, 2026

## 1. What Shadowing Supports
DynamicLightEngine currently supports:
- Cascaded directional shadows
- Spot-light shadow baseline
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
- Non-directional shadow coverage is supported at baseline level but may differ in quality/perf characteristics by backend/profile.

Current default local shadow budgets by tier:
- `LOW`: 1 local shadow light
- `MEDIUM`: 2 local shadow lights
- `HIGH`: 3 local shadow lights
- `ULTRA`: 4 local shadow lights

Implementation policy notes:
- Local shadow-atlas allocation should remain power-of-two aligned.
- Atlas packing should sort requested shadow pages by descending size before placement.
- Eviction should prefer least-recently-visible local lights, keeping pages warm until reused.
- Directional cascades should use texel snapping to reduce camera-motion shimmer.
- For temporal pipelines, optional shadow-projection jitter may be enabled and resolved through TAA/TUUA/TSR.
- Static geometry should move to a static-shadow cache layer; dynamic casters remain in per-frame update layers.
- Local-shadow updates should be cadence-aware (hero lights full-rate, distant lights throttled).

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
