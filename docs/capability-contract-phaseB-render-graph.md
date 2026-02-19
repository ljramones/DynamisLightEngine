# Capability Migration Phase B: Render Graph Compile/Validation/Lifetime

Date: 2026-02-19  
Status: Completed (metadata compile slice)

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
  - deterministic ordering by phase, pass, feature
  - duplicate-writer validation (different pass groups only)
  - missing-producer validation with pass-group awareness
  - resource lifetime computation
- `VulkanAaPostRenderGraphPlanner`:
  - bridges `VulkanAaPostCapabilityPlanner` output into compiled graph metadata
  - includes default external input policy (`scene_color`, `velocity`, `depth`, `history_color`, `history_velocity`)

## Behavior boundary

- No runtime command recording order changes.
- No Vulkan pass execution rewiring in this slice.
- Phase B output remains metadata consumed by migration phases.

## Next

Phase C: shader + descriptor composition wiring on top of capability and graph metadata.
