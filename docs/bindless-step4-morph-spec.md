# Bindless Step 4 Morph Migration Spec

Status: design approved for implementation after Step 3 parity gate.

## Gate (Hard Requirement)

Step 4 implementation starts only after Step 3 parity passes.

Required parity evidence from Step 3:

- `drawCount` match across legacy vs bindless runs
- `streamHash` match across legacy vs bindless runs

If either differs, Step 4 is blocked.

## Scope

Migrate morph-only and skinned+morph paths to bindless descriptor indexing using heap-managed morph delta and morph weight slots.

Legacy path remains unchanged when `bindlessActive=false`.

## 1. Heap Allocation and Lifetime

At morph mesh registration in `VulkanSceneMeshLifecycle`:

- `heap.allocate(MORPH_DELTA)` -> `bindlessMorphDeltaHandle`
- `heap.allocate(MORPH_WEIGHT)` -> `bindlessMorphWeightHandle`
- store both handles in `VulkanGpuMesh`

Descriptor writes:

- Morph delta buffer (static mesh data) written once at registration:
  - bindless set `set=3`, binding `1`
  - array element = resolved morph delta slot
- Morph weight buffer updated on runtime weight updates:
  - `updateMorphWeights()` writes via `vkUpdateDescriptorSets`
  - bindless set `set=3`, binding `2`
  - array element = resolved morph weight slot

At mesh teardown:

- `heap.retire(bindlessMorphDeltaHandle, currentFrame)`
- `heap.retire(bindlessMorphWeightHandle, currentFrame)`

Frame retirement policy remains heap-defined (`framesInFlight` delay).

## 2. DrawMeta Write Contract

For morph draws:

- `morphDeltaIndex = heap.resolveSlot(bindlessMorphDeltaHandle)`
- `morphWeightIndex = heap.resolveSlot(bindlessMorphWeightHandle)`
- `drawFlags |= 0x2` (`DRAW_FLAG_MORPH`)

For skinned+morph draws:

- same morph indices as above
- `jointPaletteIndex` already populated by Step 3
- `drawFlags |= 0x1 | 0x2` (`DRAW_FLAG_SKINNED | DRAW_FLAG_MORPH`)

All unused indices remain `0xFFFFFFFF`.

## 3. Shader: Bindless Morph Vertex Variant

New class:

- `VulkanBindlessMorphVertexShaderSource`

Requirements:

- draw metadata fetch keyed by `gl_DrawID`
- `#extension GL_EXT_nonuniform_qualifier : require`
- morph delta and weight heap accesses must use `nonuniformEXT(...)`

Shader pattern (contract):

```glsl
uint morphDeltaIdx = meta.morphDeltaIndex;
uint morphWeightIdx = meta.morphWeightIndex;

vec3 morphedPos = inPos;
vec3 morphedNormal = inNormal;
for (int t = 0; t < morphMeta.morphTargetCount; t++) {
    int base = (t * morphMeta.vertexCount + gl_VertexIndex) * 6;
    float w = morphWeightHeap[nonuniformEXT(morphWeightIdx)].weights[t];
    morphedPos += w * vec3(morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base], ...);
    morphedNormal += w * vec3(morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base + 3], ...);
}
```

## 4. Shader: Bindless Skinned+Morph Vertex Variant

New class:

- `VulkanBindlessSkinnedMorphVertexShaderSource`

Requirements:

- combines Step 3 skinned bindless fetch + Step 4 morph bindless fetch
- morph-before-skin order must be preserved
- uses all required indices from `DrawMeta` (`jointPaletteIndex`, `morphDeltaIndex`, `morphWeightIndex`)
- `nonuniformEXT` on all heap-indexed accesses

## 5. Pipelines

`VulkanMainPipelineBuilder` adds:

- bindless morph pipeline
- bindless skinned+morph pipeline

Pipeline requirements:

- include bindless descriptor set layout (`set=3`) in pipeline layout
- existing fragment composition unchanged
- preserve existing non-bindless pipeline variants for fallback path

Swapchain/lifecycle wiring must propagate and destroy both new bindless pipeline handles/layouts.

## 6. Recorder Routing

In `VulkanMainPassRecorderCore`:

- when `bindlessActive && drawFlags==MORPH`: bind bindless morph pipeline/layout and bind set=3
- when `bindlessActive && drawFlags==(SKINNED|MORPH)`: bind bindless skinned+morph pipeline/layout and bind set=3
- skip legacy per-draw morph/skinned descriptor binds for bindless-routed draws
- legacy path unchanged when `bindlessActive=false`

## 7. Files to Change

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanBindlessMorphVertexShaderSource.java` (new)
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanBindlessSkinnedMorphVertexShaderSource.java` (new)
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanMainPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneMeshLifecycle.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/model/VulkanGpuMesh.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanMainPassRecorderCore.java`
- swapchain/lifecycle propagation files for new bindless morph and bindless skinned+morph pipelines

## 8. Validation (Step 4)

A/B parity checks (same frame sequence):

- `vk.bindless.enabled=false` (legacy)
- `vk.bindless.enabled=true` (bindless)

Required:

- `[BINDLESS_PARITY] drawCount` matches
- `[BINDLESS_PARITY] streamHash` matches
- morph-path visual parity
- skinned+morph visual parity
- stale handle rejection log is silent

If any check fails, Step 4 is not complete.
