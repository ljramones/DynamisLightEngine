# Reflection Transparency/Refractive Exit Criteria

Target: mark `Transparent/refractive surface reflections` as `In` for Vulkan production path.

## Functional

- [x] Transparent/refractive reflection path executes in post shader under runtime policy control.
- [x] Transparent integration can run without RT lane via probe fallback (`probe_only`).
- [x] RT-active path uses `rt_or_probe` fallback chain.
- [x] Transparent integration excludes non-candidate pixels through reactive threshold gating.

## Contracts + Diagnostics

- [x] Stage-gate warning reports active policy status and fallback path.
- [x] Transparency policy warning reports candidate class counts and ratios.
- [x] Typed diagnostics expose:
  - candidate count
  - alpha-tested/reactive/probe-only candidate counts
  - stage status and fallback path
  - gate streak/cooldown/breach state
- [x] Transparency envelope breach warning emits under strict threshold forcing.

## Runtime Controls

- [x] Backend options exist for:
  - candidate reactive threshold
  - probe-only ratio warn max
  - warn min frames / cooldown
- [x] Profile defaults tune these thresholds by profile.

## Validation

- [x] Integration tests cover:
  - non-RT transparent fallback path
  - RT-active transparent path
  - strict envelope breach
- [x] Targeted Vulkan reflection suite passes.

## Documentation + Status

- [x] Reflection docs/testing guide updated for transparency production behavior.
- [x] Wishlist marks transparency/refractive reflections as `In` for Vulkan path.
