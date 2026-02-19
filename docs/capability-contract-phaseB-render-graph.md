# Capability Migration Phase B: Render Graph Compile/Validation/Lifetime

Date: 2026-02-19  
Status: Completed (B.1 compiler + B.2 barrier planning slice)

## Goal

Introduce deterministic render-graph metadata compilation from capability declarations, with:

- compile-time validation diagnostics
- resource lifetime boundaries
- AA/post planner bridge output ready for later runtime graph execution wiring

## Delivered

- `VulkanRenderGraphNode`: graph node model from pass contributions.
- `VulkanRenderGraphPlan`: ordered nodes + validation issues + lifetimes.
- `VulkanRenderGraphResourceLifetime`: first/last usage boundaries.
- `VulkanRenderGraphCompiler`:
  - deterministic Kahn topological ordering (dependency-first)
  - phase/insertion tie-break behavior for equivalent candidates
  - duplicate-writer validation (different pass groups only)
  - missing-producer validation with imported-resource awareness
  - cycle detection diagnostics
  - in-pass read/write self-dependency acceptance
  - resource lifetime computation
- `VulkanAaPostRenderGraphPlanner`:
  - bridges `VulkanAaPostCapabilityPlanner` output into compiled graph metadata
  - includes typed default imported resources (`scene_color`, `velocity`, `depth`, `history_color`, `history_velocity`)
- Imported resource model:
  - `VulkanImportedResource` with provider/lifetime/type metadata
- Resource access diagnostics:
  - `VulkanRenderGraphAccessOrder`
  - per-resource `READ/WRITE/READ_WRITE` ordered access events

## Behavior boundary

- No runtime command recording order changes.
- No Vulkan pass execution rewiring in this slice.
- Phase B output remains metadata consumed by migration phases.

## B.2 delivered

- `VulkanRenderGraphBarrierPlanner`:
  - derives barriers from resource access order
  - classifies hazards (`IMPORT_TO_READ`, `IMPORT_TO_WRITE`, `RAW`, `WAR`, `WAW`)
  - emits execution-only barriers for `WAR`
  - emits image layout transitions for image resources
  - skips layout transitions for buffer resources
- `VulkanRenderGraphBarrierPlan`:
  - stores derived barriers
  - provides human-readable `debugDump()`
- runtime trace hook:
  - `VulkanRuntimeBarrierTrace`
  - `VulkanRenderCommandRecorder.installBarrierTrace(...)` / `clearBarrierTrace()`
  - automatic capture of image barrier calls from `vkCmdPipelineBarrier` wrapper in recorder
- semantic equivalence helper:
  - `VulkanRenderGraphBarrierEquivalence`
  - compares planned barrier signatures with runtime barrier traces

## Next

- Stop point reached at end of B.2.
- B.3 (graph-driven execution cutover) is intentionally not started.
