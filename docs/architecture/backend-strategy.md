# Backend Strategy

## Module responsibilities

- `engine-api`: host/runtime boundary contracts only, organized by domain package:
  - `org.dynamislight.api.runtime`
  - `org.dynamislight.api.config`
  - `org.dynamislight.api.input`
  - `org.dynamislight.api.scene`
  - `org.dynamislight.api.event`
  - `org.dynamislight.api.error`
  - `org.dynamislight.api.logging`
  - `org.dynamislight.api.resource`
- `engine-spi`: backend discovery and compatibility checks.
- `engine-impl-common`: lifecycle policy, standardized logs/events/errors/stats/warnings.
- `engine-impl-opengl`: OpenGL implementation details (LWJGL + shader/resource setup).
- `engine-impl-vulkan`: Vulkan implementation details (advanced baseline path active: attribute-rich mesh ingest, descriptor-driven render params, multi-frame sync, staging uploads).

## Boundaries

- No JavaFX classes in runtime boundary.
- No OpenGL/Vulkan handles in API DTOs.
- Host interacts only through `EngineRuntime`.

## Runtime flow

1. Host discovers provider with `BackendRegistry`.
2. Host creates runtime and calls `initialize`.
3. Host calls `loadScene`, then `update`/`render` loop.
4. Runtime emits logs/events/errors via callbacks.
5. Host calls `shutdown`.

## Implementation rule

Backend modules may use LWJGL directly, but cross-backend policies (error mapping, lifecycle semantics, required log categories) must stay in `engine-impl-common`.

## Interface implementation map

| Contract | Primary implementation(s) | Current maturity |
| --- | --- | --- |
| `EngineRuntime` | `OpenGlEngineRuntime` (`engine-impl-opengl`), `VulkanEngineRuntime` (`engine-impl-vulkan`) | OpenGL: advanced baseline, Vulkan: advanced baseline |
| `EngineBackendProvider` (SPI) | `OpenGlBackendProvider`, `VulkanBackendProvider` | OpenGL: production path for v1, Vulkan: production-grade baseline path |
| Host bridge/session | `DynamisFxEngineBridge`, `DynamisFxEngineSession` (`engine-bridge-dynamisfx`) | Active integration layer |

## Vulkan Package Layout

`engine-impl-vulkan` is now split to reduce monolithic class growth:

- `org.dynamislight.impl.vulkan`
  - runtime/provider orchestration (`VulkanEngineRuntime`, `VulkanBackendProvider`)
  - low-level render context execution (`VulkanContext`)
- `org.dynamislight.impl.vulkan.asset`
  - mesh ingestion/parsing (`VulkanMeshAssetLoader`, `VulkanGltfMeshParser`)
- `org.dynamislight.impl.vulkan.model`
  - scene ingestion + internal render data carriers (`VulkanSceneMeshData`, `VulkanGpuMesh`, `VulkanGpuTexture`, `VulkanBufferAlloc`, `VulkanImageAlloc`, `VulkanTexturePixelData`)
- `org.dynamislight.impl.vulkan.profile`
  - runtime telemetry/profile DTOs (`VulkanFrameMetrics`, `SceneReuseStats`, `FrameResourceProfile`, `ShadowCascadeProfile`, `PostProcessPipelineProfile`)
- `org.dynamislight.impl.vulkan.shader`
  - shader source/compile helpers (`VulkanShaderSources`, `VulkanShaderCompiler`)
- `org.dynamislight.impl.vulkan.swapchain`
  - swapchain policy/selection/allocation, image-view lifecycle, and framebuffer/depth helpers (`VulkanSwapchainSelector`, `VulkanSwapchainAllocation`, `VulkanSwapchainImageViews`, `VulkanFramebufferResources`)
- `org.dynamislight.impl.vulkan.descriptor`
  - descriptor-ring sizing + descriptor resource lifecycle + texture descriptor pool/ring manager + descriptor-set coordination/writes (`VulkanDescriptorRingPolicy`, `VulkanDescriptorResources`, `VulkanTextureDescriptorPoolManager`, `VulkanTextureDescriptorSetCoordinator`, `VulkanTextureDescriptorWriter`)
- `org.dynamislight.impl.vulkan.command`
  - frame-sync allocation/teardown, acquire+submit/present orchestration, frame-command orchestration, and command recording helpers (`VulkanFrameSyncResources`, `VulkanCommandSubmitter`, `VulkanFrameCommandOrchestrator`, `VulkanRenderCommandRecorder`)
- `org.dynamislight.impl.vulkan.scene`
  - scene mesh-reuse, dynamic-update, dirty-range tracking, and mesh lifecycle/teardown helpers (`VulkanSceneReusePolicy`, `VulkanDynamicSceneUpdater`, `VulkanDirtyRangeTrackerOps`, `VulkanSceneMeshLifecycle`)
- `org.dynamislight.impl.vulkan.math`
  - matrix/vector math helpers extracted from context (`VulkanMath`)
- `org.dynamislight.impl.vulkan.shadow`
  - shadow/cascade matrix and shadow-resource lifecycle (`VulkanShadowMatrixBuilder`, `VulkanShadowResources`)
- `org.dynamislight.impl.vulkan.uniform`
  - uniform serialization/upload and frame-uniform preparation helpers (`VulkanUniformWriters`, `VulkanUniformUploadRecorder`, `VulkanFrameUniformCoordinator`)
- `org.dynamislight.impl.vulkan.texture`
  - texture pixel/container decode and texture resource lifecycle helpers (`VulkanTexturePixelLoader`, `VulkanTextureResourceOps`)
- `org.dynamislight.impl.vulkan.memory`
  - buffer/image allocation and transfer helpers (`VulkanMemoryOps`)
- `org.dynamislight.impl.vulkan.pipeline`
  - pipeline/render-pass builders and post-process resource lifecycle (`VulkanMainPipelineBuilder`, `VulkanPostPipelineBuilder`, `VulkanShadowPipelineBuilder`, `VulkanPostProcessResources`)

Design rule: keep render orchestration/state transitions in `VulkanContext`, and place data carriers/parsers in subpackages. New Vulkan features should default to subpackage classes first; only core command submission/state mutation belongs in `VulkanContext`.
