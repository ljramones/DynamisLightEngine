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
- `engine-impl-vulkan`: Vulkan implementation details (real baseline path active).

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
| `EngineRuntime` | `OpenGlEngineRuntime` (`engine-impl-opengl`), `VulkanEngineRuntime` (`engine-impl-vulkan`) | OpenGL: active baseline, Vulkan: active baseline |
| `EngineBackendProvider` (SPI) | `OpenGlBackendProvider`, `VulkanBackendProvider` | OpenGL: production path for v1, Vulkan: active baseline path |
| Host bridge/session | `DynamisFxEngineBridge`, `DynamisFxEngineSession` (`engine-bridge-dynamisfx`) | Active integration layer |
