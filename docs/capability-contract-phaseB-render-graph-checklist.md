# Capability Migration Phase B Checklist (Render Graph)

Scope date: 2026-02-19  
Scope target: compile/validation/lifetime metadata (no runtime rewiring yet)

## 1. Graph model

- [x] Define graph node model from capability pass contributions.
- [x] Define compiled graph plan model.
- [x] Define resource lifetime model.

## 2. Compiler

- [x] Compile graph nodes from capability contracts.
- [x] Produce deterministic Kahn topological ordering with phase/insertion tie-breaks.
- [x] Validate missing producers.
- [x] Validate duplicate writers (across pass groups).
- [x] Validate dependency cycles.
- [x] Allow in-pass read/write self dependency (no false cycle).

## 3. Lifetime tracking

- [x] Compute first/last usage per resource.
- [x] Expose lifetime list in compiled plan.

## 4. AA/post bridge

- [x] Bridge Phase 3 planner output into Phase B graph compiler input.
- [x] Define default external input policy for AA/post compile.
- [x] Add typed imported-resource metadata (provider/lifetime).

## 5. Tests

- [x] Add compiler ordering test.
- [x] Add missing producer diagnostics test.
- [x] Add duplicate writer diagnostics test.
- [x] Add resource lifetime boundary test.
- [x] Add AA/post bridge compile tests.
- [x] Add cycle detection test.
- [x] Add imported-resource and previous-frame tests.
- [x] Add dependency-overrides-phase test.
- [x] Add resource-centric access-order diagnostics test.

## 6. Documentation

- [x] Add Phase B summary doc.
- [x] Add Phase B checklist.
- [x] Link Phase B artifacts from contract plan doc.

## 7. B.2 barrier planning

- [x] Add barrier plan model with hazard classification.
- [x] Derive barriers from resource access order.
- [x] Model execution-only WAR hazards.
- [x] Model image layout transitions for image resources.
- [x] Keep buffer resources layout-less.
- [x] Add barrier debug dump output.
- [x] Add runtime barrier trace hook in command recorder.
- [x] Add semantic equivalence helper (planned vs runtime trace).
- [x] Add barrier planner + equivalence tests.
