# Bindless Step 3 Skinned Migration Spec

Status: design approved for implementation after Step 2 parity gate.

## Gate (Hard Requirement)

Step 3 implementation starts only after Step 2 parity passes.

Required parity evidence:

- `-Dvk.bindless.enabled=false --frames=10 --mesh=assets/meshes/box.glb`
- `-Dvk.bindless.enabled=true --frames=10 --mesh=assets/meshes/box.glb`

For matching frame sequence, `[BINDLESS_PARITY]` must match on:

- `drawCount`
- `streamHash`

If either differs, Step 3 is blocked.

## Scope

Migrate skinned draws from per-draw skinning descriptor binds to bindless set=3 routing.

Legacy path remains unchanged when `bindlessActive=false`.

## 1. Heap Allocation and Lifetime

At skinned mesh registration in `VulkanSceneMeshLifecycle`:

- call `heap.allocate(JOINT)`
- store returned handle in `VulkanGpuMesh.bindlessJointHandle`

At skinned update (`updateSkinnedMesh()` path):

- resolve slot from handle
- update descriptor array element at bindless set `set=3`, `binding=0`, resolved slot index
- descriptor update uses existing joint palette buffer handle for that mesh

At mesh teardown:

- call `heap.retire(bindlessJointHandle, currentFrame)`

Frame retirement policy remains heap-defined (`framesInFlight` fence delay).

## 2. DrawMeta Write Contract

For skinned draws in draw-meta upload path:

- `DrawMeta.jointPaletteIndex = heap.resolveSlot(bindlessJointHandle)`
- `DrawMeta.drawFlags |= 0x1` (`DRAW_FLAG_SKINNED`)
- `morphDeltaIndex = 0xFFFFFFFF`
- `morphWeightIndex = 0xFFFFFFFF`
- `instanceDataIndex = 0xFFFFFFFF`

Static and non-skinned draws keep `jointPaletteIndex = 0xFFFFFFFF`.

## 3. Shader: Bindless Skinned Vertex Variant

New class:

- `VulkanBindlessSkinnedVertexShaderSource`

Requirements:

- same vertex inputs as current skinned path (`loc0..5`)
- include draw-meta fetch from `set=3,binding=4` keyed by `gl_DrawID`
- fetch joint heap array index from `DrawMeta.jointPaletteIndex`
- require `GL_EXT_nonuniform_qualifier`

Shader pattern (contract):

```glsl
#extension GL_EXT_nonuniform_qualifier : require

uint drawId = uint(gl_DrawID);
DrawMeta meta = drawMeta.entries[drawId];
uint jointIdx = meta.jointPaletteIndex;

mat4 skinMatrix =
    inWeights.x * jointHeap[nonuniformEXT(jointIdx)].jointData[inJoints.x] +
    inWeights.y * jointHeap[nonuniformEXT(jointIdx)].jointData[inJoints.y] +
    inWeights.z * jointHeap[nonuniformEXT(jointIdx)].jointData[inJoints.z] +
    inWeights.w * jointHeap[nonuniformEXT(jointIdx)].jointData[inJoints.w];
mat3 skinRot = mat3(skinMatrix);
vec4 worldPos = obj.uModel * skinMatrix * vec4(inPos, 1.0);
vec3 worldNormal = normalize(mat3(obj.uModel) * skinRot * inNormal);
vec3 worldTangent = normalize(mat3(obj.uModel) * skinRot * inTangent);
```

`nonuniformEXT` is mandatory for correctness with mixed-draw indirect streams.

## 4. Pipeline

`VulkanMainPipelineBuilder` adds:

- `buildBindlessSkinnedPipeline()`

Pipeline requirements:

- skinned vertex input layout (`loc0..5`)
- vertex source: `VulkanBindlessSkinnedVertexShaderSource`
- existing fragment composition unchanged
- include bindless descriptor layout (set 3) in pipeline layout

Swapchain/lifecycle wiring must propagate and destroy:

- bindless skinned pipeline handle
- bindless skinned pipeline layout handle

## 5. Recorder Routing

In `VulkanMainPassRecorderCore`:

When `bindlessActive && draw.skinned` and bindless skinned pipeline is available:

- bind bindless skinned pipeline/layout
- bind bindless descriptor set (`set=3`)
- skip legacy per-draw skinned SSBO descriptor bind

When `bindlessActive=false` or bindless skinned pipeline unavailable:

- keep legacy skinned path unchanged

## 6. Files to Change

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanBindlessSkinnedVertexShaderSource.java` (new)
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanMainPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneMeshLifecycle.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/model/VulkanGpuMesh.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanMainPassRecorderCore.java`
- swapchain/lifecycle propagation files for new bindless skinned pipeline handles

## 7. Validation (Step 3)

A/B parity checks (same frame sequence):

- `vk.bindless.enabled=false` (legacy)
- `vk.bindless.enabled=true` (bindless)

Required:

- `[BINDLESS_PARITY] drawCount` matches
- `[BINDLESS_PARITY] streamHash` matches
- `bindless.heap.joint.used == skinned mesh count` per frame
- stale handle rejection log is silent

If any check fails, Step 3 is not complete.
