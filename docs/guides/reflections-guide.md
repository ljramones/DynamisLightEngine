# Reflections Guide

This guide covers reflection controls in `PostProcessDesc`.

## API Surface

`PostProcessDesc.reflections` uses `ReflectionDesc`:

- `enabled`: master switch
- `mode`: `ibl_only`, `ssr`, `planar`, `hybrid`
- `ssrStrength`: SSR contribution `[0..1]`
- `ssrMaxRoughness`: SSR roughness cutoff `[0..1]`
- `ssrStepScale`: SSR ray step scale `[0.5..3]`
- `temporalWeight`: history stabilization `[0..0.98]`
- `planarStrength`: planar reflection contribution `[0..1]`

## Modes

- `ibl_only`: keep environment/IBL only.
- `ssr`: screen-space reflections only.
- `planar`: mirrored screen-space planar sample.
- `hybrid`: SSR + planar blend.

## Backend Profiles

Reflection quality profiles are available through backend options:

- OpenGL: `opengl.reflectionsProfile`
- Vulkan: `vulkan.reflectionsProfile`

Allowed values:

- `performance`: reduces SSR intensity/history cost.
- `balanced`: default.
- `quality`: increases reflection stability/intensity.
- `stability`: favors temporal stability over aggressive SSR.

## Example

```java
PostProcessDesc post = new PostProcessDesc(
        true, true, 1.05f, 2.2f,
        true, 1.0f, 0.8f,
        true, 0.42f, 1.0f, 0.02f, 1.0f,
        true, 0.52f,
        true, 0.56f, true,
        null,
        new ReflectionDesc(true, "hybrid", 0.72f, 0.80f, 1.2f, 0.82f, 0.42f)
);
```

## Runtime Notes

- OpenGL and Vulkan both consume the same reflection descriptor.
- LOW quality tier disables reflections; MEDIUM applies conservative scaling.
- Warnings are emitted when reflections are active:
  - `REFLECTIONS_BASELINE_ACTIVE`
  - `REFLECTIONS_QUALITY_DEGRADED` (MEDIUM tier)
