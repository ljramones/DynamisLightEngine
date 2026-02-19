# Capability Migration Phase A: Pass Recorder Extraction

Date: 2026-02-19  
Status: Started (AA/post + shadow/main recorder facades extracted)

## Goal

Extract feature-owned pass recorders with zero rendering behavior change.

## Delivered in this slice

- Added `VulkanShadowMainPassRecorder` wrapper for shadow/main recording.
- Added `VulkanPostCompositePassRecorder` wrapper for post composite recording.
- Updated `VulkanFrameCommandOrchestrator` to delegate to these recorders.
- Added module-level post boundaries in `VulkanPostCompositePassRecorder` for:
  - tonemap / bloom / SSAO
  - AA resolve (TAA)
  - reflection resolve
- Added `VulkanPostModulePlan` metadata output for active/pruned module tracking.

## Behavior boundary

- Recording logic still executes through existing `VulkanRenderCommandRecorder`.
- No pass ordering changes.
- No shader/descriptor/runtime behavior changes.

## Phase A closeout

- Phase A structural extraction goals in this lane are complete.
- Next migration stage is Phase B (graph build/validation/lifetime orchestration).
