# Backend Strategy

## Module responsibilities

- `engine-api`: host/runtime boundary contracts and DTOs only.
- `engine-spi`: backend discovery and compatibility checks.
- `engine-impl-common`: lifecycle policy, standardized logs/events/errors/stats/warnings.
- `engine-impl-opengl`: OpenGL implementation details (LWJGL + shader/resource setup).
- `engine-impl-vulkan`: Vulkan implementation details (currently scaffolded).

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
