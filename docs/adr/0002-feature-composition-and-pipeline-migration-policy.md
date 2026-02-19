# ADR 0002: Feature Composition and Pipeline Migration Policy

- Status: Accepted
- Date: 2026-02-18

## Context

DynamicLightEngine is adding feature capabilities across AA, shadows, reflections, and post-processing. The long-term goal is composable features with graph-based pipeline assembly, without regressing frame stability or exploding shader/permutation complexity.

Current Vulkan implementation is linear-orchestrated and already contains multi-feature composition inside shared passes. We need a policy that preserves momentum while avoiding premature abstraction.

## Decision

Adopt the following architecture and sequencing policy.

1. Product stance:
- Blessed profiles (tiers) are the shipping contract.
- Arbitrary feature composition is a design goal, not a release promise.

2. Composition model:
- Features contribute to passes and shader interfaces.
- Features do not own isolated full pipelines by default.
- Composition operates at two levels:
  - pass-level composition (graph nodes/resources/dependencies)
  - shader-level composition (hook implementations + declared bindings/uniform requirements)

3. Capability contract timing:
- Do not extract a generic capability interface yet.
- First bring reflections to parity depth with shadows.
- Extract shared interfaces from implemented evidence, not speculative design.

4. Pipeline/layout rebuild posture:
- Tier/profile changes may rebuild pipelines and descriptor layouts.
- This is acceptable baseline behavior for infrequent switches.
- High-frequency region/Volume capability swaps are future optimization scope.

5. Migration sequence and gates:
- Phase A: extract feature-owned pass recorders with no behavior change.
- Phase B: introduce graph build/validation/resource lifetime management.
- Phase C: introduce shader-module + descriptor-layout composition.
- Each phase requires a stabilization period and regression pass before proceeding.

6. Validation policy:
- Graph compiler must reject illegal combinations at build/compile time.
- Examples: missing producers, incompatible mode dependencies, descriptor conflicts.

7. Testing policy:
- Keep per-feature isolation tests.
- Add/maintain full-tier golden-image integration tests for complete pipelines.
- Run rotating non-blessed combinations in nightly CI as interaction fuzzing.

8. Next deep feature sequencing:
- Reflections is the required second deep feature domain after shadows.
- Defer GI until composition contracts are proven by shadows + reflections.
- Defer full RT feature investment until composition contracts and graph validation are stable.
- Defer AA/post hardening until reflections clarifies upstream contracts and pass topology needs.

## Rationale

- Prevents combinatorial/permutation explosion from top-down abstraction.
- Keeps implementation-driven learning visible before interface extraction.
- Reduces risk by sequencing highest-risk changes last.
- Ensures composition quality is measured at full-pipeline level, not only per-feature unit scope.
- Uses reflections to expose multi-topology capability composition before generic interface extraction.

## Sequencing Rationale (Reflections Next)

Reflections should be implemented in depth next because it introduces multiple fundamentally different pass topologies within one capability:

- SSR: post-style pass that consumes main pass outputs.
- Planar: mirrored scene re-render path (pre/side pass topology).
- Probe-driven: capture/bake style workflow with different cadence/lifetime.
- RT-oriented reflection path: distinct data dependencies and scheduling constraints.

This makes reflections the strongest test of pass-level and shader-level composition boundaries. Shadows alone do not exercise this topology diversity.

Why not GI next:

- GI is architecture-heavy and depends on clean composition/resource contracts.
- Implementing GI before contract stabilization would likely create GI-specific infrastructure that must later be generalized or replaced.

Why not RT next:

- RT has additional hardware/runtime variability and should sit on stable capability and graph validation foundations.

Why not harden AA/post first:

- AA/post are downstream consumers of contracts that reflections will help define.
- Hardening them first risks converging on incomplete or brittle interfaces.

## Consequences

### Positive

- Preserves delivery velocity while building toward composability.
- Avoids speculative contracts that miss real feature constraints.
- Makes architectural transitions auditable and test-gated.

### Trade-offs

- Arbitrary composition support is deferred.
- Some duplicate wiring may remain until Phase C extraction.
- Tier switches can remain relatively heavyweight until later optimization.

## Reflections Observation Log Format

During reflections implementation, maintain a running log. This is input to capability contract extraction.

For each notable change or issue, capture:

1. What reflections needed:
- required inputs
- produced resources
- pass ordering constraints
- shader hook needs
- descriptor needs

2. What overlaps with shadows:
- shared patterns
- reusable lifecycle/validation concepts

3. What differs from shadows:
- domain-specific requirements
- incompatible assumptions

4. Surprises:
- what was unexpected
- which prior assumptions broke
- lifecycle or dataflow behavior that did not match expectations
- implications for future interface boundaries

5. Testing impact:
- new test cases added
- tier-level regressions found/fixed
- interaction bugs discovered with other features

## Exit Criteria for Contract Extraction

Begin generic capability interface extraction only when:

- Reflections has parity depth with shadows.
- Observation log has enough repeated patterns and documented differences.
- Tier golden tests are stable for current blessed profiles.

## Follow-on ADR

- Contract extraction outcome is recorded in `docs/adr/0003-capability-contract-v1.md`.
