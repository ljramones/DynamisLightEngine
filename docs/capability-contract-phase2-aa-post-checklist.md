# Capability Contract Phase 2 Checklist (AA/Post Hardening)

Scope date: 2026-02-19  
Scope target: contract alignment for AA/post (metadata-only wiring in this slice)

## 1. Contract Mapping

- [x] Define AA capability modules mapped to runtime AA modes.
- [x] Define post-stack capability modules (tonemap, bloom, SSAO, SMAA, TAA resolve, fog composite).
- [x] Ensure each module declares pass contributions.
- [x] Ensure each module declares shader hook contributions.
- [x] Ensure each module declares resource requirements with binding frequency.
- [x] Ensure each module declares dependency metadata.

## 2. Catalog + Discoverability

- [x] Add Vulkan AA/post capability catalog for grouped access.

## 3. Behavior Boundary

- [x] No runtime render-path behavior changes in this phase.
- [x] No graph compiler/wiring changes in this phase.

## 4. Tests

- [x] Add capability catalog tests for AA mode coverage.
- [x] Add tests for temporal-history requirement differences (TAA vs FXAA).
- [x] Add tests for post module presence.

## 5. Validation

- [x] Run targeted Vulkan module tests including new capability tests.

## 6. Follow-on Readiness

- [x] Catalog/module declarations are ready to be consumed by Phase A extractor and later graph composition.
