# DynamisLightEngine Architecture Boundary Ratification Review

Date: 2026-03-09

## Intent and Scope

This is a boundary-ratification review for DynamisLightEngine based on repository code/docs as implemented today.

This pass does not refactor code. It defines strict ownership, dependency, and API-surface constraints to reduce overlap with DynamisGPU, DynamisSceneGraph, MeshForge, and adjacent graphics feature repos.

## 1) Repo Overview (Grounded)

Repository shape:

- Multi-module Maven project:
  - `engine-api`
  - `engine-spi`
  - `engine-impl-common`
  - `engine-impl-opengl`
  - `engine-impl-vulkan`
  - `engine-bridge-dynamisfx`
  - `engine-demos`
  - `engine-host-sample`

Implemented architectural intent in code/docs:

- `engine-api`: host/runtime contracts, scene/material/light DTOs, events/errors/stats/capabilities, resource service.
- `engine-spi`: backend discovery plus render capability contract/planning declarations (`RenderCapabilityContractV2`, pass/resource/scheduler declarations).
- `engine-impl-common`: lifecycle shell (`AbstractEngineRuntime`), resource watch/cache/hot reload, frame-graph utilities.
- backend modules: OpenGL and Vulkan runtime implementations.

Dependency highlights (from poms + imports):

- `engine-api` depends on `dynamis-gpu-api` (notably via `EngineException` extending `GpuException`).
- `engine-impl-vulkan` depends on:
  - `dynamis-gpu-api` and `dynamis-gpu-vulkan`
  - `meshforge` + `meshforge-loader`
  - `dynamisvfx-api` + `dynamisvfx-vulkan`
  - LWJGL Vulkan stack
- main modules do not directly depend on SceneGraph/ECS/Session; those appear in `engine-host-sample` integration code.

## 2) Strict Ownership Statement

### 2.1 What DynamisLightEngine should exclusively own

DynamisLightEngine should own **render policy and orchestration above GPU execution**, including:

- render-facing runtime API for hosts
- render planning/capability contracts (pass ordering, feature coordination, quality-tier policy)
- backend selection and backend runtime lifecycle orchestration
- renderer-facing consumption contracts for scene/material/lighting descriptors
- rendering diagnostics/telemetry policy exposed to hosts

### 2.2 What is appropriate for LightEngine

Appropriate concerns:

- frame planning and pass orchestration policy
- feature coordination (lighting, shadows, post, RT, AA, VFX integration points) at policy level
- host-facing runtime lifecycle and validation
- backend-agnostic capability surfaces and reporting

### 2.3 What DynamisLightEngine must never own

DynamisLightEngine must not own:

- low-level GPU memory/resource execution primitives that belong in DynamisGPU
- mesh/geometry preparation and canonical payload shaping that belong in MeshForge
- scene hierarchy authority that belongs in DynamisSceneGraph
- world/session/ECS ownership or gameplay/scripting policy
- ownership of specialized feature subsystems (VFX/Sky/Terrain) beyond orchestration contracts

## 3) Dependency Rules

### 3.1 Allowed dependencies for DynamisLightEngine

- `DynamisGPU` public abstractions for execution delegation (preferred via `dynamis-gpu-api` only)
- `DynamisSceneGraph` extraction outputs via adapters/integration layers (not scene ownership)
- feature subsystem APIs (`DynamisVFX`, `DynamisSky`, `DynamisTerrain`) as pluggable capability inputs
- window/input host surfaces (`DynamisWindow`) for presentation/input integration points

### 3.2 Forbidden dependencies for DynamisLightEngine

- direct dependence on GPU backend internals as a long-term boundary (`org.dynamisgpu.vulkan.*`) in render-policy code
- direct geometry parsing/shaping ownership that duplicates MeshForge ingest/payload responsibilities
- ECS/session/world authority dependencies in engine core modules
- feature-subsystem implementation ownership inside LightEngine core

### 3.3 Who may depend on DynamisLightEngine

- host/application integration layers
- World/scene orchestration layers that feed render-ready descriptors
- feature subsystems through explicit capability contracts

Dependency direction intent:

- SceneGraph/World feed LightEngine; LightEngine plans/orchestrates; DynamisGPU executes.

## 4) Public vs Internal Boundary Assessment

### 4.1 Canonical public boundary

Public boundary should be:

- `engine-api` (runtime contracts + DTOs)
- `engine-spi` (backend provider and capability contract declarations)

### 4.2 Internal implementation areas

Internal by intent:

- `engine-impl-common`
- `engine-impl-opengl`
- `engine-impl-vulkan`
- backend-specific planners/recorders/runtime coordinators

### 4.3 Current boundary pressure points

1. `engine-api` currently inherits GPU exception types (`EngineException extends GpuException`), coupling top-level API to lower execution substrate semantics.

2. `engine-impl-vulkan` uses many `org.dynamisgpu.vulkan.*` internals directly (`VulkanMemoryOps`, buffer alloc types, descriptor heap internals), indicating execution concerns are still partly embedded in LightEngine backend.

3. Existing `docs/dynamisgpu-extraction-manifest.md` explicitly documents that many Vulkan utility classes are extraction candidates to DynamisGPU, confirming a known transitional boundary.

4. `engine-api` scene model (`SceneDescriptor` with transforms/meshes/materials/lights) is broad and can overlap with SceneGraph-centric ingestion patterns unless bounded to "render descriptor input" only.

## 5) Policy Leakage / Overlap Findings

### 5.1 Major clean boundaries confirmed

- Main LightEngine modules do not own ECS/session/SceneGraph authority directly.
- SceneGraph integration is currently adapter-driven in `engine-host-sample`, not embedded in core runtime modules.
- API/SPI split exists and is meaningful.
- Render capability/planning vocabulary is explicit in SPI contracts.

### 5.2 Major overlap/drift areas

1. **LightEngine <-> DynamisGPU overlap (significant)**  
`engine-impl-vulkan` still performs substantial GPU resource/memory lifecycle work via DynamisGPU Vulkan internals. This is execution-layer overlap rather than pure planning/orchestration.

2. **LightEngine <-> MeshForge overlap (present)**  
`VulkanGltfMeshParser` performs MeshForge-based parsing plus LightEngine-side attribute interleaving/normalization and mesh geometry shaping. This indicates asset/geometry shaping concerns remain in backend runtime code.

3. **Render API breadth vs SceneGraph integration (transitional)**  
LightEngine supports direct scene descriptor loading while also supporting SceneGraph-to-instance-batch adapter flow in host sample. Dual ingestion paths are practical but architecturally transitional and can blur scene ownership boundaries.

4. **Feature subsystem overlap risk (watch list)**  
Large capability/diagnostic surface for RT/VFX/Sky/Terrain in LightEngine is valuable for coordination, but must remain orchestration-level so ownership does not drift from dedicated feature repos.

5. **Window coupling risk (watch list)**  
Backends own presentation/swapchain/window-visible behavior; this must remain adapter-level and not absorb full window-system ownership that belongs in DynamisWindow.

## 6) Relationship Clarification

### 6.1 LightEngine vs DynamisGPU

- DynamisGPU should own GPU execution/resource lifecycle primitives and backend utility layers.
- LightEngine should own render planning/policy and call execution services through stable GPU boundaries.
- Current code is transitional: extraction manifest acknowledges in-progress separation.

### 6.2 LightEngine vs DynamisSceneGraph

- SceneGraph owns spatial hierarchy/transform authority.
- LightEngine consumes extracted scene transport (or equivalent render descriptors), not scene authority.
- Current sample adapter pattern is correct and should remain outside core engine policy classes.

### 6.3 LightEngine vs MeshForge

- MeshForge should own canonical geometry preparation/format shaping.
- LightEngine should avoid backend-specific geometry shaping logic beyond runtime binding/consumption.

### 6.4 LightEngine vs VFX/Sky/Terrain

- LightEngine should coordinate feature execution order/contracts.
- Feature-specific data ownership and domain logic should remain in dedicated feature repos.

## 7) Ratification Result

**Needs boundary tightening.**

Why:

- High-value planning/orchestration structure exists (API/SPI/capability contracts), but significant execution-layer overlap with DynamisGPU remains in Vulkan implementation.
- Geometry shaping overlap with MeshForge is still present.
- API boundary currently leaks lower-layer GPU exception coupling.
- Repository itself already records these as extraction/transitional concerns (`dynamisgpu-extraction-manifest.md`), so this is an acknowledged state rather than a hypothetical risk.

## 8) Strict Boundary Rules to Carry Forward

1. Treat `engine-api` + `engine-spi` as the long-lived public contract; keep them independent of backend-internal classes.
2. Keep LightEngine focused on render policy/planning/orchestration; avoid adding new low-level GPU lifecycle utilities in backend modules.
3. Route scene authority through SceneGraph/world adapters; do not encode scene ownership in LightEngine core.
4. Keep MeshForge as the canonical geometry prep/shaping owner; LightEngine should consume prepared geometry contracts.
5. Keep feature subsystem integration contract-based; avoid absorbing VFX/Sky/Terrain domain ownership.

## 9) Recommended Next Step

Next deep review should be **DynamisWorldEngine**.

Reason:

- The remaining major ambiguity after SceneGraph + LightEngine is where world authority and orchestration policy live versus render/scene/ECS substrates.
- Ratifying WorldEngine next will reduce cross-layer leakage risk before any LightEngine integration cleanup pass.
