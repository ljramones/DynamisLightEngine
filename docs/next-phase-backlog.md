# Next-Phase Backlog (Execution Ready)

## P0-1 Vulkan Albedo Texturing Vertical Slice
- Scope: Implement end-to-end albedo texture sampling in Vulkan for glTF meshes.
- Tasks:
  - Add Vulkan image upload path (`staging -> device-local image -> sampled layout`).
  - Add sampler/image descriptor set layout and per-mesh descriptor binding.
  - Sample `baseColorTexture` in Vulkan fragment shader using mesh UVs.
  - Keep material factor fallback when texture is missing.
- Definition of Done:
  - Vulkan renders textured output for a scene with `MaterialDesc.albedoTexturePath`.
  - No Vulkan validation errors on texture upload/binding.
  - Existing Vulkan integration tests still pass.

## P0-2 Backend Comparison Harness
- Scope: Make parity measurable.
- Tasks:
  - Extend sample host with `--compare` mode.
  - Render the same scene with OpenGL and Vulkan and write PNG outputs.
  - Compute a simple diff metric and fail if above threshold.
- Definition of Done:
  - One command produces paired captures and a numeric diff.
  - CI can run comparison in a guarded mode.

## P1-1 Vulkan Normal Mapping
- Scope: Add normal-map support after albedo path is stable.
- Tasks:
  - Add normal texture descriptor binding and tangent-space normal sampling.
  - Reuse glTF tangents if present; fallback to geometric normal.
- Definition of Done:
  - Normal-mapped model looks materially different from albedo-only shading.

## P1-2 Material Workflow Polish (Both Backends)
- Scope: Improve metallic-roughness parity and fallbacks.
- Tasks:
  - Parse/use metallic-roughness texture channel data where present.
  - Keep stable defaults for missing maps.
- Definition of Done:
  - Same scene has close visual intent across OpenGL and Vulkan.
