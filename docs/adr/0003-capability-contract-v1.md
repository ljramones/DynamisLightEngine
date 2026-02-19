# ADR 0003: Capability Contract v1 Extraction

- Status: Accepted
- Date: 2026-02-19

## Context

ADR 0002 set the sequencing policy: build shadows + reflections to maturity first, then extract capability interfaces from implemented evidence.

We now have:

- pass-first feature evidence (shadows)
- shader-hook-in-existing-pass evidence (reflections)

That is enough to define a minimal contract surface without speculative design.

## Decision

Introduce a minimal `engine-spi` capability contract (`RenderFeatureCapability` + model types) with these properties:

1. Models both composition forms:
- pass contribution declarations
- shader hook contribution declarations

2. Models resource requirements with binding frequency metadata.

3. Models capability dependencies and validation issues.

4. Is metadata-only in this phase.
- No runtime wiring in Vulkan/OpenGL yet.
- No render behavior change.

## Contract Scope (Phase 1)

- Package: `org.dynamislight.spi.render`
- Interface: `RenderFeatureCapability`
- Core models:
  - `RenderFeatureContract`
  - `RenderPassContribution`, `RenderPassPhase`
  - `RenderShaderHookContribution`, `RenderShaderStage`
  - `RenderResourceRequirement`, `RenderResourceType`, `RenderBindingFrequency`
  - `RenderCapabilityDependency`
  - `RenderCapabilityValidationIssue`

## Rationale

- Keeps extraction evidence-driven and minimal.
- Provides a stable target for AA/post hardening next.
- Avoids premature graph/runtime coupling.
- Preserves momentum while reducing future interface churn risk.

## Consequences

### Positive

- Clear contract target for subsequent migration phases.
- Explicit metadata for future graph compile/validation work.
- No immediate runtime regression risk.

### Trade-offs

- Contract is not exercised by runtime yet.
- Further refinements are expected after AA/post hardening.

## Follow-up

1. Phase 2: harden AA/post resolve path to this contract.
2. Phase 3: post stack modularization.
3. Migration A/B/C per ADR 0002.
