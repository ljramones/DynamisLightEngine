# Reflections Testing

This document defines reflection validation for OpenGL and Vulkan.

## Scope

- `ReflectionDesc` validation in API layer.
- `ReflectionAdvancedDesc` validation in API layer.
- Runtime parity of reflection mode wiring across backends.
- Compare-harness coverage for reflection-heavy scenes.

## Required Checks

1. API validation
- Reject invalid `mode`.
- Enforce numeric ranges for SSR/temporal/planar controls.
- Enforce advanced ranges (`hiZMipCount`, `denoisePasses`, fade ranges, probe blend, RT limits/fallback mode).

2. Runtime warnings
- Reflection-enabled scenes emit `REFLECTIONS_BASELINE_ACTIVE`.
- MEDIUM-tier reflection scenes emit `REFLECTIONS_QUALITY_DEGRADED`.

3. Profile validation
- Run each reflection mode with `performance`, `balanced`, `quality`, `stability`.
- Confirm profile changes are reflected in metadata/warnings and bounded diffs.

3. Scene-level rendering checks
- `ssr` scene: confirm visible reflected highlights from on-screen content.
- `planar` scene: confirm vertical mirror sample behavior.
- `hybrid` scene: confirm stable blend under camera motion.
- `hybrid_hiz_probe` scene: confirm Hi-Z SSR stepping + denoise + probe blending remains bounded.
- `rt_hybrid` scene: confirm RT-request lane falls back predictably with bounded parity diff.

## Commands

```bash
# API validation tests
mvn -pl engine-api -am -Dtest=EngineApiContractTest test

# OpenGL reflections runtime tests
mvn -pl engine-impl-opengl -am -Dtest=OpenGlEngineRuntimeLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test

# Vulkan reflections runtime tests
mvn -pl engine-impl-vulkan -am -Dtest=VulkanEngineRuntimeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Compare Harness Additions

- Add reflection-tagged scenes:
  - glossy SSR stress
  - planar mirror stress
  - hybrid motion stress
- Track per-scene diff and temporal metrics, same as AA gates.

Current profile tags in parity tests:

- `reflections-ssr`
- `reflections-planar`
- `reflections-hybrid`
- `reflections-hiz-probe`
- `reflections-rt-fallback`

## Regression Triggers

- New reflection mode added.
- Reflection mode-bit packing layout changed.
- SSR step logic/temporal blend tuning changed.
- Post shader push-constant layout changed (Vulkan).
