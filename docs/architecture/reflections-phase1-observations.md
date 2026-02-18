# Reflections Phase 1 Observations (Vulkan)

Last updated: February 18, 2026.

This log captures concrete findings from the current probe-focused reflection depth pass.
It is intended as extraction input for future capability contracts and render-graph migration.

## Implemented now

- Scene-level probe descriptors are available through `ReflectionAdvancedDesc.probes`.
- Vulkan maps probes into runtime state and uploads frame-visible probe metadata into a per-frame SSBO.
- Main fragment shader consumes probe metadata, evaluates per-fragment probe influence, applies optional box-projected direction shaping, and blends probe influence into the existing IBL radiance path.
- Probe overlap uses priority-aware remaining-coverage blending.

## Capability observations

### 1. Inputs needed

- Per scene:
  - `ReflectionProbeDesc` list (position, extents, cubemap path, priority, blend distance, intensity, boxProjection)
- Per frame:
  - camera view-projection matrix for frustum culling
- Per fragment:
  - world position, normal/view-derived reflection direction, roughness

### 2. Resources produced and consumed

- Produced:
  - reflection probe metadata SSBO payload (`set=0`, `binding=2`)
  - SSBO header semantics:
    - `x`: visible probe count uploaded this frame
    - `y`: global probe-array layer count (slot count)
    - `z`: unique probe asset paths requested by visible probes
    - `w`: visible unique paths that could not be assigned a slot
- Consumed:
  - same probe metadata SSBO in main fragment shader
  - existing IBL radiance texture path for probe-weighted radiance sampling (transitional path)

### 3. Pass/order constraints

- Probe metadata upload occurs during frame uniform prep before main draw recording.
- No dedicated probe resolve pass yet.
- Probe contribution is currently shader-level composition inside the main forward pass, not pass-level composition.

### 4. Shader hook and binding implications

- Probe integration demonstrates a capability that contributes shader logic into an existing pass.
- This differs from shadow capability behavior, which contributes dedicated passes.
- Capability contracts must represent both:
  - pass contributors
  - shader contributors
- Binding frequency distinction surfaced:
  - probe metadata is per-frame (`set=0`)
  - material textures remain per-mesh (`set=1`)

### 5. Current limitations discovered

- Probe texture selection now uses a native 2D-array path, not native cubemap-array sampling.
- Source probe assets are projected through the existing 2D radiance UV sampling model.
- Native cubemap-array/image-array upload and sampling remains a future enhancement.

### 6. Surprises vs shadow domain

- Reflection probe capability gave value without introducing a new pass, while shadows were pass-first.
- This confirms the feature contract cannot assume "one feature = one pass contribution."
- Reflection overlap behavior required priority-aware accumulation in shader to avoid exterior bleed in interior volumes.

## Current extraction candidates (not final contract)

- Feature-declared per-frame resource requirements (SSBO/Ubo/samplers)
- Feature-declared shader fragment hooks for existing pass injection
- Feature-declared optional pass contributions when topology requires it (SSR/planar/RT phases)
- Explicit binding frequency metadata (`per-frame`, `per-pass`, `per-material`)

## Next reflection implementation target

- Replace 2D-array probe path with native cubemap-array/image-array probe sampling while keeping current slot-map and header contracts stable.
