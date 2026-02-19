# Capability Contract v1 Plan

Date: 2026-02-19  
Scope: post-reflections sequencing kickoff

## Agreed sequence

1. Phase 1: capability contract extraction (from shadows + reflections evidence).
2. AA/post resolve hardening to that contract.
3. Post stack modularization.
4. Migration Phase A/B/C:
   - Phase A: feature-owned pass recorders.
   - Phase B: render graph compile/validation/lifetime.
   - Phase C: shader + descriptor composition.
5. Next deep domains: lighting expansion, then GI, then RT cross-cutting upgrades.

## Phase 1 deliverables

1. Architecture decision record for `Capability Contract v1`.
2. Minimal SPI contract types (no runtime wiring, no behavior change).
3. Checklist with explicit completion criteria.
4. Validation run (compile/tests) and local commit.

## Non-goals for Phase 1

- No render-path behavior changes.
- No Vulkan/OpenGL runtime integration yet.
- No graph compiler implementation.
- No shader code assembly changes.

## Exit criteria

- Contract types are present in `engine-spi` and compile.
- Types represent both composition forms:
  - pass contributions
  - shader hook contributions
- Types include resource requirements with binding frequency metadata.
- Basic test coverage exists for immutability/default contract behavior.
- ADR documents rationale and sequencing boundary.

## Phase 2 status

- AA/post contract hardening artifacts are tracked in:
  - `docs/capability-contract-phase2-aa-post.md`
  - `docs/capability-contract-phase2-aa-post-checklist.md`

## Phase 3 status

- Post modularization activation/planning artifacts are tracked in:
  - `docs/capability-contract-phase3-post-modularization.md`
  - `docs/capability-contract-phase3-post-modularization-checklist.md`
