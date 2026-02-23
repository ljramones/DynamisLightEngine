# Bindless Step 6 Unified Indirect Submission Spec

Status: design approved for implementation after Step 5 parity gate.

## Gate (Hard Requirement)

Step 6 implementation starts only after Step 5 parity passes.

Required parity evidence from Step 5:

- `drawCount` match across legacy vs bindless runs
- `streamHash` match across legacy vs bindless runs

If either differs, Step 6 is blocked.

## Precondition

All bindless variants are validated and active:

- static
- morph
- skinned
- skinned+morph
- instanced

No remaining correctness dependencies on per-draw descriptor binds for bindless path.

## Scope

Move from per-draw indirect submission to unified GPU-driven variant-grouped `vkCmdDrawIndexedIndirectCount` submission.

Legacy (`vk.bindless.enabled=false`) path is preserved but marked deprecated.

## 1. Unified Compacted Indirect Stream

Maintain one unified `VkDrawIndexedIndirectCommand` array, partitioned by variant in fixed order:

1. static
2. morph
3. skinned
4. skinned+morph
5. instanced

GPU culling compute writes visible commands directly into variant-specific ranges in that unified array.

## 2. Variant Count Buffer Layout

Count buffer layout:

```c
uint32 staticCount;
uint32 morphCount;
uint32 skinnedCount;
uint32 skinnedMorphCount;
uint32 instancedCount;
uint32 pad[3]; // 32-byte aligned
```

Per-variant count increments are written via `atomicAdd` by compute.

## 3. Compute Pass Update

`VulkanCullingComputeSource` and `VulkanCullingComputePass` updates:

- one dispatch covers all variants
- each visible draw writes command into its variant range in unified indirect buffer
- increment matching per-variant count in count buffer
- output is ready for graphics consumption after compute->graphics barrier

## 4. Submission Change

Replace per-variant `vkCmdDrawIndexedIndirect` loops with grouped `vkCmdDrawIndexedIndirectCount` calls:

```c
vkCmdBindPipeline(bindless_static)
vkCmdDrawIndexedIndirectCount(buffer, staticOffset, countBuffer, staticCountOffset, maxStaticDraws, stride)

vkCmdBindPipeline(bindless_morph)
vkCmdDrawIndexedIndirectCount(buffer, morphOffset, countBuffer, morphCountOffset, maxMorphDraws, stride)

// ... skinned, skinned+morph, instanced
```

Each grouped call uses the variant-specific max draw bound and command stride.

## 5. Retire Legacy Per-Draw Bind Sites (Bindless Path)

For bindless-enabled route:

- remove per-draw `vkCmdBindDescriptorSets` in `VulkanMainPassRecorderCore`
- remove per-draw `vkCmdBindDescriptorSets` in `VulkanShadowPassRecorderCore`

Legacy route:

- preserved for `vk.bindless.enabled=false`
- clearly marked deprecated in code/comments/logging

## 6. Orchestration and Buffer Management

`VulkanFrameCommandOrchestrator` responsibilities:

- unified indirect buffer management
- unified variant offsets/count-offset table publication to recorder
- per-frame count buffer reset before compute dispatch

`VulkanIndirectDrawBuffer` responsibilities:

- represent unified layout metadata (variant capacities, offsets, stride)

`VulkanCullingComputePass` responsibilities:

- compute dispatch and barriers
- expose output indirect buffer and count buffer handles/offset metadata

## 7. Files to Change

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanCullingComputeSource.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanMainPassRecorderCore.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanShadowPassRecorderCore.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanIndirectDrawBuffer.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanCullingComputePass.java`

## 8. Validation (Step 6)

Parity checks:

- total draw count across all variants matches legacy path
- per-variant draw counts each match legacy path
- `[BINDLESS_PARITY]` stays stable for same frame sequence
- visual parity spot checks across all five mesh types

Final gate:

- Step 6 parity pass is required before legacy per-draw descriptor path is formally deprecated.

If any check fails, Step 6 is not complete.
