# Capability Contract Phase 3 Checklist (Post Modularization)

Scope date: 2026-02-19  
Scope target: AA/post modular activation planning (no runtime rewiring yet)

## 1. Planner

- [x] Add AA/post capability activation planner.
- [x] Add explicit active/pruned output model.
- [x] Include quality-tier pruning rule for SSAO.
- [x] Include AA-mode temporal-resolve eligibility rule.

## 2. Stability boundary

- [x] Keep render behavior unchanged in this slice.
- [x] Keep planner metadata-only for now.

## 3. Tests

- [x] Add planner tests for low-tier pruning.
- [x] Add planner tests for FXAA temporal resolve pruning.
- [x] Add planner tests for AA-disabled pruning.

## 4. Documentation

- [x] Add Phase 3 summary doc.
- [x] Link Phase 3 from contract plan doc.

## 5. Phase A readiness

- [x] Planner output can be consumed by feature-owned pass-recorder extraction.
