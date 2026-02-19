# Capability Migration Phase A Detailed Tasks

Date: 2026-02-19  
Goal: complete structural pass-recorder extraction with module-level boundaries, zero behavior change.

## Task breakdown

1. Recorder ownership boundaries
- Extract shadow/main recording behind feature-owned recorder class.
- Extract post composite recording behind feature-owned recorder class.

2. Post module decomposition (within post recorder)
- Add explicit module planning boundary for:
  - tonemap
  - bloom
  - SSAO
  - SMAA
  - TAA resolve
  - reflection resolve
- Preserve existing monolithic shader execution path; module decomposition is structural metadata only.

3. AA + reflection boundary visibility
- Ensure post recorder explicitly tracks AA-related module activation.
- Ensure post recorder explicitly tracks reflection-related module activation.

4. Tests
- Add tests that validate module-level activation/pruning outcomes.
- Keep existing recorder behavior tests passing.

5. Docs and closeout
- Update Phase A checklist to completed state.
- Keep Phase plan links current.

## Exit criteria

- Orchestrator delegates to feature-owned recorders.
- Post recorder has module-level plan output for AA/reflection boundaries.
- No render behavior change.
- Targeted Vulkan test slice passes.
