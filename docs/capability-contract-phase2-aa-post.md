# Capability Contract Phase 2: AA/Post Hardening

Date: 2026-02-19  
Status: Completed (metadata alignment slice)

## Goal

Align AA/post domains to `Capability Contract v1` by declaring feature modules with:

- pass contributions
- shader hook contributions
- resource requirements (with binding frequency)
- capability dependencies

## Delivered

- Vulkan AA capability modules for:
  - `taa`, `tsr`, `tuua`, `msaa_selective`, `hybrid_tuua_msaa`, `dlaa`, `fxaa_low`
- Vulkan post capability modules for:
  - `tonemap`, `bloom`, `ssao`, `smaa`, `taa_resolve`, `fog_composite`
- Capability catalog for grouped retrieval.
- Tests validating mode/module coverage and temporal-history requirement shape.

## Non-goals (this slice)

- No runtime render-path rewiring.
- No pass-order/graph behavior change.
- No shader assembly system changes.

## Next

Phase 3: post stack modularization workflow and Phase A extraction prep using the declared capability catalog as source metadata.
