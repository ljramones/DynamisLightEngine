# Capability Migration Phase B Checklist (Render Graph)

Scope date: 2026-02-19  
Scope target: compile/validation/lifetime metadata (no runtime rewiring yet)

## 1. Graph model

- [x] Define graph node model from capability pass contributions.
- [x] Define compiled graph plan model.
- [x] Define resource lifetime model.

## 2. Compiler

- [x] Compile graph nodes from capability contracts.
- [x] Produce deterministic ordering.
- [x] Validate missing producers.
- [x] Validate duplicate writers (across pass groups).

## 3. Lifetime tracking

- [x] Compute first/last usage per resource.
- [x] Expose lifetime list in compiled plan.

## 4. AA/post bridge

- [x] Bridge Phase 3 planner output into Phase B graph compiler input.
- [x] Define default external input policy for AA/post compile.

## 5. Tests

- [x] Add compiler ordering test.
- [x] Add missing producer diagnostics test.
- [x] Add duplicate writer diagnostics test.
- [x] Add resource lifetime boundary test.
- [x] Add AA/post bridge compile tests.

## 6. Documentation

- [x] Add Phase B summary doc.
- [x] Add Phase B checklist.
- [x] Link Phase B artifacts from contract plan doc.
