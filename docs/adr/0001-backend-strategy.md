# ADR 0001: Backend Strategy (LWJGL-First With Engine Abstraction)

- Status: Accepted
- Date: 2026-02-15

## Context

DynamicLightEngine must support multiple rendering backends (`opengl` now, `vulkan` later) while keeping `engine-api` stable and Java-first. We need a strategy that ships quickly without locking the runtime into brittle backend-specific code.

## Decision

Use a two-layer approach:

1. **Backend bindings via LWJGL** in backend modules (`engine-impl-opengl`, future real `engine-impl-vulkan`).
2. **Engine-owned abstraction layer** in `engine-impl-common` for lifecycle, error mapping, warning propagation, stats/event/log contracts, and shared runtime policies.

No backend-native objects cross `engine-api`.

## Rationale

- Fastest path to deliver real rendering on Java 25.
- Keeps backend-specific calls isolated in backend modules.
- Avoids premature native/JNI layer complexity.
- Preserves future option to introduce native interop for hot paths without breaking API/SPI.

## Consequences

### Positive

- OpenGL and Vulkan can evolve independently behind common contracts.
- Host integration remains stable (`ServiceLoader`, `EngineRuntime`).
- Shared error/lifecycle behavior is testable once in `engine-impl-common`.

### Trade-offs

- Some platform-specific tuning remains backend-module-local.
- Vulkan implementation still requires explicit design for resource lifetime and synchronization details.

## Migration Path

If profiling shows bottlenecks in Java bindings, add an internal native acceleration layer behind backend modules while preserving:

- `engine-api` contracts
- `engine-spi` discovery semantics
- existing error/log/event model
