# Capability Migration Phase B Detailed Tasks

Date: 2026-02-19  
Goal: add render graph compile/validation/resource lifetime orchestration (no runtime behavior change yet).

## Task breakdown

1. Graph model
- Define graph node model from capability pass contributions.
- Define compiled plan model with ordered nodes, validation issues, and resource lifetimes.

2. Compiler
- Build compiler from capability declarations.
- Validate:
  - missing producers for required reads (except known external resources)
  - duplicate writers for same resource
- Produce deterministic node order.

3. Resource lifetime tracking
- Compute first/last node usage per resource (read/write).
- Expose lifetimes in compiled plan.

4. AA/post graph builder
- Bridge Phase 3 planner output into graph compiler input.
- Keep output metadata-only in this slice.

5. Tests
- Validate ordering.
- Validate missing producer diagnostics.
- Validate duplicate writer diagnostics.
- Validate lifetime boundaries.

6. Docs
- Add Phase B checklist and summary.
- Link Phase B artifacts from contract plan doc.

## Exit criteria

- Graph compiler produces deterministic plan from capability metadata.
- Validation and lifetime tests pass.
- No render-path behavior changes in runtime execution.
