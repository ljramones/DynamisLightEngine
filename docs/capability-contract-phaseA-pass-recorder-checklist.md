# Capability Migration Phase A Checklist (Pass Recorder Extraction)

Scope date: 2026-02-19  
Scope target: structural extraction only, zero behavior change

## 1. Orchestrator delegation

- [x] Extract shadow/main recording behind feature-owned recorder facade.
- [x] Extract post composite recording behind feature-owned recorder facade.
- [x] Keep existing recorder logic and pass order unchanged.

## 2. Behavior boundary

- [x] No graph/runtime behavior changes.
- [x] No shader/descriptor wiring changes.

## 3. Validation

- [x] Run targeted Vulkan test slice after extraction.

## 4. Next

- [ ] Decompose post composite wrapper into module-level recorder boundaries.
- [ ] Decompose reflection/AA recorder boundaries for graph-ready ownership.
