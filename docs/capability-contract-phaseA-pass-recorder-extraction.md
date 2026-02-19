# Capability Migration Phase A: Pass Recorder Extraction

Date: 2026-02-19  
Status: Started (AA/post + shadow/main recorder facades extracted)

## Goal

Extract feature-owned pass recorders with zero rendering behavior change.

## Delivered in this slice

- Added `VulkanShadowMainPassRecorder` wrapper for shadow/main recording.
- Added `VulkanPostCompositePassRecorder` wrapper for post composite recording.
- Updated `VulkanFrameCommandOrchestrator` to delegate to these recorders.

## Behavior boundary

- Recording logic still executes through existing `VulkanRenderCommandRecorder`.
- No pass ordering changes.
- No shader/descriptor/runtime behavior changes.

## Next in Phase A

- Split wrappers into per-feature ownership boundaries aligned with capability catalog:
  - post modules (tonemap/bloom/ssao/smaa/taa/fog)
  - reflection module recorder boundary
  - AA resolve recorder boundary
