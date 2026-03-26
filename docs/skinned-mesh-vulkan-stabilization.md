# LightEngine Vulkan Backend: Skinned Mesh Stabilization

**Date:** 2026-03-26
**Status:** Active blocker — backend render-graph correctness
**Reproducer:** `DynamisGames/proving/presentation/skinned-mesh-basics`

## Objective

Achieve stable multi-frame Vulkan rendering of a skinned GLB mesh (CesiumMan.glb)
through the real DynamisLightEngine pipeline on macOS/MoltenVK. Then verify
skinning data flows correctly into the GPU skinning shader.

## Confirmed Findings

The proving module has validated:

| Component | Status |
|-----------|--------|
| GLB asset loading (MeshForge) | WORKS — 3273 vertices, 14016 indices |
| Skeleton/clip extraction (Animis) | WORKS — 19 joints, 1 animation clip (2.00s) |
| Vulkan runtime initialization | WORKS — UI, font atlas, timestamps all initialize |
| Scene upload / mesh upload | WORKS — drawCount=1, mesh in draw list |
| Descriptor allocation for skinned mesh | WORKS — after pool sizing fix |
| Command recording entry | WORKS — bindless parity emitted |
| First frame presentation | PARTIAL — reached once (with validation disabled) |

## Engine Bugs Fixed During Proving (8)

| # | Bug | Fix | Commit |
|---|-----|-----|--------|
| 1 | Missing GL_ARB_shader_draw_parameters in 6 bindless shaders | Added extension declaration | cf8386d |
| 2 | Shaderc targeting Vulkan 1.0 (gl_DrawID unavailable) | Set target env to Vulkan 1.1 | 20249df |
| 3 | Shadow pipeline crash on unsupported shader | Graceful fallback, skip bindless variant | 33a4b88 |
| 4 | Skinned mesh descriptor pool undersized | Pool now allocates 3 SSBO + 1 UBO | c7da541 |
| 5 | VFX debris readback null memoryOps | VFX disabled by default (opt-in flag) | d55783f |
| 6 | Sky SPI bridge NPE from StubGpuMemoryOps | Catch-and-disable in updateAndRecord | 4b4f7bc (DynamisSky) |
| 7 | VulkanSkinnedMeshUniforms SIGSEGV on vkUnmapMemory | Skip unmap during destroy | bd5c182 |
| 8 | Descriptor writer crash on null imageView/sampler | Pre-call validation added | 245a75c |

## Current Primary Blocker

### Render Graph Layout Mismatch for `scene_color`

```
WARNING: Render graph layout mismatch for 'scene_color': binding=5 plannedOld=2
```

The `scene_color` image resource has a layout disagreement between what the render
graph barrier plan expects (layout 2 = `VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL`)
and what the binding table tracks (layout 5 = `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`).

This causes MoltenVK to abort (`Trace/BPT trap: 5`) because the image layout
transition is invalid. On permissive Vulkan implementations this would be a
validation warning; on MoltenVK it is fatal.

**Implicated files:**
- `VulkanRenderGraphExecutor.java` — emits barriers with planned layouts
- `VulkanResourceBindingTable.java` — tracks current binding layouts
- `VulkanAaPostRenderGraphPlanner.java` — plans imported resource layouts
- `VulkanExecutableRenderGraphBuilder.java` — builds the render graph plan

**Root cause hypothesis:** The scene_color resource is imported with an assumed
initial layout that doesn't match what the previous frame/pass left it in.
This is likely because the post-processing pass (disabled in this scene)
normally transitions the image, and without it the layout tracking diverges.

### Secondary Blocker: Shadow Pipeline Re-activation

`VulkanShadowPipelineBuilder.create` is invoked a second time after the first
frame, likely triggered by a swapchain resize or shadow config change event.
The scene has no shadow-enabled lights, so shadow resources should not be
touched after initial creation.

**Implicated files:**
- `VulkanContext.createShadowResources()` — called from lifecycle orchestrator
- `VulkanShadowRuntimeTuning` — may trigger shadow refresh
- `VulkanEngineRuntime.onRender()` — shadow scheduler tick

### Tertiary: Skinning Data Not Flowing

Bindless parity reports `jointUsed=0` for a scene with a skinned mesh.
This means `updateSkinnedMesh()` data is either not being called, not being
consumed, or the joint palette binding is not connected to the render path.

**Implicated files:**
- `VulkanSceneMeshLifecycle.updateSkinnedMesh()` — joint matrix upload
- `VulkanSkinnedMeshUniforms.upload()` — GPU buffer write
- `VulkanBindlessDescriptorHeap` — joint handle allocation

This should only be investigated AFTER stable multi-frame rendering is achieved.

## Blocker Priority Order

1. **scene_color layout mismatch** — fix render graph layout tracking for scenes
   without post-processing
2. **Shadow re-activation** — ensure shadow pipeline is not re-created for
   no-shadow scenes
3. **Stable multi-frame execution** — app survives 60+ frames without crash
4. **Skinning data activation** — jointUsed > 0, updateSkinnedMesh consumed
5. **Visual verification** — CesiumMan visible in bind pose
6. **Animation** — clip advancing, pose changing

## Execution Rules

- The proving module is the **reproducer only** — no more prover-side workarounds
- All fixes go into LightEngine base repos
- Each fix must be verified by re-running the proving module
- MoltenVK validation should be ON (not disabled) for the final verification

## Run Command

```bash
cd DynamisGames/proving/presentation/skinned-mesh-basics
./build.sh
./run.sh
```

## Definition of Done

- [ ] `skinned-mesh-basics` runs 60+ frames without crash (validation ON)
- [ ] CesiumMan is visually present on screen
- [ ] Animation clip plays (pose changes over time)
- [ ] `jointUsed > 0` in bindless parity output
- [ ] No `Trace/BPT trap` or `SIGSEGV` during normal execution
- [ ] Clean shutdown (no crash during resource destruction)
