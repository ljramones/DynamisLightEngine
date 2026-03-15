# Backend Strategy

## Module responsibilities

- `engine-api`: host/runtime boundary contracts only, organized by domain package:
  - `org.dynamisengine.light.api.runtime`
  - `org.dynamisengine.light.api.config`
  - `org.dynamisengine.light.api.input`
  - `org.dynamisengine.light.api.scene`
  - `org.dynamisengine.light.api.event`
  - `org.dynamisengine.light.api.error`
  - `org.dynamisengine.light.api.logging`
  - `org.dynamisengine.light.api.resource`
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

Current Vulkan frame execution details are documented in:

- `docs/architecture/vulkan-render-pipeline-current.md`

Feature-composition and migration sequencing policy is documented in:

- `docs/adr/0002-feature-composition-and-pipeline-migration-policy.md`

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

- `org.dynamisengine.light.impl.vulkan`
  - runtime/provider orchestration (`VulkanEngineRuntime`, `VulkanBackendProvider`)
  - low-level render context execution (`VulkanContext`)
- `org.dynamisengine.light.impl.vulkan.asset`
  - mesh ingestion/parsing (`VulkanMeshAssetLoader`, `VulkanGltfMeshParser`)
- `org.dynamisengine.light.impl.vulkan.model`
  - scene ingestion + internal render data carriers (`VulkanSceneMeshData`, `VulkanGpuMesh`, `VulkanGpuTexture`, `VulkanBufferAlloc`, `VulkanImageAlloc`, `VulkanTexturePixelData`)
- `org.dynamisengine.light.impl.vulkan.profile`
  - runtime telemetry/profile DTOs (`VulkanFrameMetrics`, `SceneReuseStats`, `FrameResourceProfile`, `ShadowCascadeProfile`, `PostProcessPipelineProfile`)
- `org.dynamisengine.light.impl.vulkan.shader`
  - shader source/compile helpers (`VulkanShaderSources`, `VulkanShaderCompiler`)
- `org.dynamisengine.light.impl.vulkan.swapchain`
  - swapchain policy/selection/allocation, create/destroy resource coordination, image-view lifecycle, and framebuffer/depth helpers (`VulkanSwapchainSelector`, `VulkanSwapchainAllocation`, `VulkanSwapchainResourceCoordinator`, `VulkanSwapchainDestroyCoordinator`, `VulkanSwapchainImageViews`, `VulkanFramebufferResources`)
- `org.dynamisengine.light.impl.vulkan.descriptor`
  - descriptor-ring sizing + descriptor resource lifecycle + texture descriptor pool/ring manager + descriptor-set coordination/writes (`VulkanDescriptorRingPolicy`, `VulkanDescriptorResources`, `VulkanTextureDescriptorPoolManager`, `VulkanTextureDescriptorSetCoordinator`, `VulkanTextureDescriptorWriter`)
- `org.dynamisengine.light.impl.vulkan.command`
  - frame-sync allocation/teardown, acquire+submit/present orchestration, frame-command orchestration/input-factory, and command recording helpers (`VulkanFrameSyncResources`, `VulkanCommandSubmitter`, `VulkanFrameCommandOrchestrator`, `VulkanFrameCommandInputsFactory`, `VulkanRenderCommandRecorder`)
- `org.dynamisengine.light.impl.vulkan.bootstrap`
  - Vulkan startup/bootstrap helpers for window, instance, surface, and logical-device creation (`VulkanBootstrap`)
- `org.dynamisengine.light.impl.vulkan.scene`
  - scene mesh-reuse, dynamic-update/upload orchestration, dirty-range tracking, and mesh lifecycle/teardown helpers (`VulkanSceneReusePolicy`, `VulkanDynamicSceneUpdater`, `VulkanSceneUploadCoordinator`, `VulkanDirtyRangeTrackerOps`, `VulkanSceneMeshLifecycle`)
- `org.dynamisengine.light.impl.vulkan.math`
  - matrix/vector math helpers extracted from context (`VulkanMath`)
- `org.dynamisengine.light.impl.vulkan.shadow`
  - shadow/cascade matrix and shadow-resource lifecycle (`VulkanShadowMatrixBuilder`, `VulkanShadowResources`)
- `org.dynamisengine.light.impl.vulkan.uniform`
  - uniform serialization/upload, global-scene input building, and frame-uniform preparation helpers (`VulkanUniformWriters`, `VulkanGlobalSceneInputBuilder`, `VulkanUniformUploadRecorder`, `VulkanFrameUniformCoordinator`)
- `org.dynamisengine.light.impl.vulkan.texture`
  - texture pixel/container decode and texture resource lifecycle helpers (`VulkanTexturePixelLoader`, `VulkanTextureResourceOps`)
- `org.dynamisengine.light.impl.vulkan.memory`
  - buffer/image allocation and transfer helpers (`VulkanMemoryOps`)
- `org.dynamisengine.light.impl.vulkan.pipeline`
  - pipeline/render-pass builders and post-process resource lifecycle (`VulkanMainPipelineBuilder`, `VulkanPostPipelineBuilder`, `VulkanShadowPipelineBuilder`, `VulkanPostProcessResources`)

Design rule: keep render orchestration/state transitions in `VulkanContext`, and place data carriers/parsers in subpackages. New Vulkan features should default to subpackage classes first; only core command submission/state mutation belongs in `VulkanContext`.
