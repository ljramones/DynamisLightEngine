# Bindless Step 5 Instanced Migration Spec

Status: design approved for implementation after Step 4 parity gate.

## Gate (Hard Requirement)

Step 5 implementation starts only after Step 4 parity passes.

Required parity evidence from Step 4:

- `drawCount` match across legacy vs bindless runs
- `streamHash` match across legacy vs bindless runs

If either differs, Step 5 is blocked.

## Scope

Migrate instanced main-pass and instanced shadow-pass descriptor usage to bindless instance heap indexing.

Legacy path remains unchanged when `bindlessActive=false`.

## 1. Heap Allocation and Lifetime

At `registerInstanceBatch()`:

- `heap.allocate(INSTANCE)` -> `bindlessInstanceHandle`
- store handle in `VulkanInstanceBatch`

At `updateInstanceBatch()`:

- resolve slot from `bindlessInstanceHandle`
- write instance SSBO descriptor to bindless set `set=3`, binding `3`, array element = resolved slot
- update underlying instance data buffer contents as existing logic requires

At `removeInstanceBatch()`:

- `heap.retire(bindlessInstanceHandle, currentFrame)`

Frame retirement policy remains heap-defined (`framesInFlight` delay).

## 2. DrawMeta Write Contract

For instanced draws:

- `instanceDataIndex = heap.resolveSlot(bindlessInstanceHandle)`
- `drawFlags |= 0x4` (`DRAW_FLAG_INSTANCED`)
- draw metadata entry is one per batch draw command, not one per instance

All unused indices remain `0xFFFFFFFF`.

## 3. Shader: Bindless Instanced Main Vertex Variant

New class:

- `VulkanBindlessInstancedVertexShaderSource`

Requirements:

- draw metadata fetch keyed by `gl_DrawID`
- `#extension GL_EXT_nonuniform_qualifier : require`
- instance heap array index access uses `nonuniformEXT(...)`

Shader pattern (contract):

```glsl
uint instanceIdx = meta.instanceDataIndex;
mat4 instanceModel = instanceHeap[nonuniformEXT(instanceIdx)].models[gl_InstanceIndex];
vec4 worldPos = instanceModel * vec4(inPos, 1.0);
```

## 4. Shader: Bindless Instanced Shadow Variant

New class:

- `VulkanBindlessInstancedShadowVertexShaderSource`

Requirements:

- same draw-meta + instance-heap fetch strategy as main pass
- shadow transform path only
- no per-draw descriptor binding dependency

## 5. Pipelines

Builders add:

- `VulkanMainPipelineBuilder`: bindless instanced main pipeline variant
- `VulkanShadowPipelineBuilder`: bindless instanced shadow pipeline variant

Pipeline requirements:

- include bindless descriptor set layout (`set=3`) in pipeline layout
- preserve existing non-bindless instanced variants for fallback path

Swapchain/lifecycle wiring must propagate and destroy new bindless instanced main/shadow pipeline handles/layouts.

## 6. Recorder Routing

In `VulkanMainPassRecorderCore`:

- when `bindlessActive && draw.instanced`: bind bindless instanced main pipeline/layout + bind set=3
- skip legacy per-draw instanced descriptor bind for bindless-routed draws

In `VulkanShadowPassRecorderCore`:

- when `bindlessActive && draw.instanced`: bind bindless instanced shadow pipeline/layout + bind set=3
- skip legacy per-draw instanced descriptor bind for bindless-routed draws

Legacy path unchanged when `bindlessActive=false`.

## 7. Files to Change

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanBindlessInstancedVertexShaderSource.java` (new)
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanBindlessInstancedShadowVertexShaderSource.java` (new)
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanMainPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanShadowPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneMeshLifecycle.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/model/VulkanInstanceBatch.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanMainPassRecorderCore.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanShadowPassRecorderCore.java`
- swapchain/lifecycle propagation for new bindless instanced main/shadow pipelines

## 8. Validation (Step 5)

A/B parity checks (same frame sequence):

- `vk.bindless.enabled=false` (legacy)
- `vk.bindless.enabled=true` (bindless)

Required:

- `[BINDLESS_PARITY] drawCount` matches
- `[BINDLESS_PARITY] streamHash` matches
- instanced main-pass visual parity
- instanced shadow-pass visual parity
- stale handle rejection log is silent

If any check fails, Step 5 is not complete.
