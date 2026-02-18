# Vulkan Render Pipeline (Current Implementation)

Last verified against source on February 18, 2026.

This document describes what is implemented now in `engine-impl-vulkan`. It is intentionally implementation-first and may drift as features evolve.

Feature-depth sequencing and composition migration policy (including why reflections is next) is in:

- `docs/adr/0002-feature-composition-and-pipeline-migration-policy.md`
- `docs/architecture/reflections-phase1-observations.md`

## Scope and entry points

- API lifecycle entry is `VulkanEngineRuntime`:
  - initialize: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntime.java`
  - load scene: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntime.java`
  - render: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntime.java`
- Runtime lifecycle helpers are in:
  - `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanRuntimeLifecycle.java`
- Low-level Vulkan orchestration is in:
  - `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java`
  - `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/lifecycle/VulkanLifecycleOrchestrator.java`

`mockContext=true` bypasses native Vulkan rendering and returns synthetic metrics via `VulkanRuntimeLifecycle.render(...)`.

## Initialization pipeline

Runtime startup is orchestrated by `VulkanLifecycleOrchestrator.initializeRuntime(...)`:

1. Create GLFW window.
2. Create Vulkan instance.
3. Create surface.
4. Select physical device + queue family.
5. Create logical device + graphics queue.
6. Create descriptor resources.
7. Create swapchain resources (main pipeline, framebuffers, depth/velocity, optional post resources).
8. Create frame sync resources (command pool/buffers, semaphores, fences).
9. Create shadow resources (depth + optional moment pipeline resources).
10. Upload pending scene meshes.

Primary files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/lifecycle/VulkanLifecycleOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java`

## Descriptor and uniform model

### Main descriptor set (`set=0`)

Defined in `VulkanDescriptorResources`:

- `binding 0`: global scene UBO (`VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER`)
- `binding 1`: per-object UBO (`VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC`)
- `binding 2`: reflection probe metadata SSBO (`VK_DESCRIPTOR_TYPE_STORAGE_BUFFER`)

File: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/descriptor/VulkanDescriptorResources.java`

### Texture descriptor set (`set=1`)

Layout has 10 combined image samplers, written per mesh:

- 0 albedo
- 1 normal
- 2 metallic-roughness
- 3 occlusion
- 4 shadow map array
- 5 IBL irradiance
- 6 IBL radiance
- 7 BRDF LUT
- 8 shadow moment map array
- 9 probe radiance atlas (per-scene atlas texture indexed by probe slot)

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/descriptor/VulkanDescriptorResources.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/descriptor/VulkanTextureDescriptorWriter.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneMeshCoordinator.java`

### Reflection probe path (current)

Reflection probe descriptors are scene-level API data and are mapped into Vulkan runtime state:

- API descriptor: `engine-api/src/main/java/org/dynamislight/api/scene/ReflectionProbeDesc.java`
- Runtime mapper: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntimeSceneMapper.java`
- Frame upload/cull: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanReflectionProbeCoordinator.java`

Current behavior:

- Per frame, runtime frustum-culls probe AABBs against current view-projection.
- Surviving probes are sorted by priority (descending).
- Probe metadata is packed to a persistently-mapped SSBO (`set=0`, `binding=2`).
- Probe SSBO header fields are:
  - `x`: visible probe count
  - `y`: probe atlas layer count (global slot count)
  - `z`: visible unique probe-path count
  - `w`: visible unique probe-path count that could not be assigned a slot
- Main fragment shader consumes this metadata and computes per-fragment probe weighting.
- Probe overlap uses priority-aware remaining-coverage accumulation to suppress lower-priority probes when higher-priority probes fully cover.

Current limitation:

- Vulkan currently uses a 2D probe-radiance atlas path, not native cubemap-array sampling.
- Probe texture selection by `cubemapIndex` is active, but source assets are interpreted through the existing 2D radiance projection path.

### Uniform sizes and upload

- Global scene block size: `2544` bytes.
- Per-object block size: `176` bytes.
- Staging buffers are persistently mapped; frame prep computes dirty ranges; upload records `vkCmdCopyBuffer` commands.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/uniform/VulkanUniformUploadCoordinator.java`

## Graphics pipelines and passes

## Main pass pipeline

Built by `VulkanMainPipelineBuilder`:

- Vertex shader: `VulkanShaderSources.mainVertex()`
- Fragment shader: `VulkanShaderSources.mainFragment()`
- Vertex layout: 11 floats (pos3, normal3, uv2, tangent3), 44 bytes.
- Render pass attachments:
  - color (swapchain format, present layout)
  - velocity (swapchain format, shader-read layout)
  - depth (configured depth format)

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanMainPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderSources.java`

## Shadow pass pipeline

Built by `VulkanShadowPipelineBuilder`:

- Vertex shader: `VulkanShaderSources.shadowVertex()`
- Fragment shader:
  - depth-only path: `shadowFragment()`
  - moment path: `shadowFragmentMoments()`
- Push constant (4 bytes) selects cascade index.
- Optional moment path writes moments and can generate mip levels via blit.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanShadowPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanRenderCommandRecorder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderSources.java`

## Post composite pipeline (optional)

Built by `VulkanPostPipelineBuilder` + `VulkanPostProcessResources`:

- Fullscreen triangle vertex shader: `postVertex()`
- Post fragment shader: `postFragment()`
- Post descriptor set has 4 sampled textures:
  - scene/offscreen color
  - history color
  - current velocity
  - history velocity
- Push constants: 32 floats (128 bytes).

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanPostPipelineBuilder.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanPostProcessResources.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderSources.java`

## Per-frame execution order

Frame entry is `VulkanContext.renderFrame()`.

1. Update temporal jitter state.
2. Acquire swapchain image.
3. Record command buffer:
  - update shadow matrices
  - update reflection probe metadata SSBO
  - prepare uniform payloads and dirty ranges
  - record uniform copy commands
  - record shadow pass(es)
  - record main geometry pass
  - record post composite pass if enabled
4. Submit queue and present.
5. Handle out-of-date/suboptimal by recreating swapchain resources.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanCommandSubmitter.java`

## Command orchestration details

`VulkanFrameCommandOrchestrator.record(...)` is the top-level frame recorder. It calls:

- `VulkanRenderCommandRecorder.recordShadowAndMainPasses(...)`
- `VulkanRenderCommandRecorder.executePostCompositePass(...)` when post is active.

This recorder handles explicit image layout transitions and copy/blit operations for shadow moments and post history resources.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanRenderCommandRecorder.java`

## Swapchain and frame sync

- Frames in flight default to `3` and are configurable (`2..6`).
- Sync objects per frame:
  - image-available semaphore
  - render-finished semaphore
  - render fence
- Command buffers are reset and re-recorded each frame with one-time submit usage.
- Swapchain recreation destroys and rebuilds swapchain-scoped resources.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameSyncLifecycleCoordinator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanCommandSubmitter.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/swapchain/VulkanSwapchainLifecycleCoordinator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/swapchain/VulkanSwapchainRecreateCoordinator.java`

## Scene update and reuse behavior

Scene set/load path uses a planner with three actions:

- dynamic-only reuse
- geometry reuse with texture rebind
- full rebuild/upload

This avoids unnecessary GPU reallocation when only transforms/material state changed.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneSetPlanner.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneMeshCoordinator.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanSceneRuntimeCoordinator.java`

## Notes and known characteristics

- Renderer is forward-style (lighting resolved in main fragment shader), not deferred G-buffer.
- No compute shader pipeline in this implementation path; work is graphics + transfer.
- Shader modules are compiled at runtime from GLSL sources using shaderc.

Files:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderCompiler.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderSources.java`

## Maintenance checklist for future changes

When rendering behavior changes, update this file and verify against:

- `VulkanContext.renderFrame(...)`
- `VulkanFrameCommandOrchestrator.record(...)`
- `VulkanRenderCommandRecorder.*`
- `VulkanMainPipelineBuilder`, `VulkanShadowPipelineBuilder`, `VulkanPostPipelineBuilder`
- `VulkanDescriptorResources` and `VulkanPostProcessResources`
