# Reflection Probe Streaming Checklist (To `In`)

Scope date: 2026-02-18  
Scope target: Vulkan production path (`engine-impl-vulkan`)

## 0. Scope Lock

- [x] Promotion scope locked to Vulkan path for this cycle.
- [x] OpenGL parity treated as separate follow-up track.

## 1. Residency + Selection Core

- [x] Frustum-visible probe selection with priority ordering.
- [x] Cadence-based deferred selection under budget.
- [x] Max-visible budget enforcement.
- [x] Deterministic probe slot assignment path.

## 2. LOD + Metadata

- [x] Per-probe LOD tier tagging in uploaded metadata.
- [x] LOD distribution counters exposed for diagnostics.
- [x] Missing-slot / unique-path counters exposed for diagnostics.

## 3. Runtime Diagnostics

- [x] Typed probe streaming diagnostics accessor.
- [x] Runtime warning telemetry for streaming state each frame.
- [x] Budget-pressure warning emission path.

## 4. Envelope Gates

- [x] Streaming envelope thresholds configurable via backend options.
- [x] Streak + cooldown breach gating implemented.
- [x] Breach warning (`REFLECTION_PROBE_STREAMING_ENVELOPE_BREACH`) emitted.

## 5. Tests

- [x] Coordinator tests updated for packed metadata + selection behavior.
- [x] Integration tests assert streaming diagnostics and breach path.
- [x] Guarded real-Vulkan contract test for streaming diagnostics.

## 6. CI Scripts + Signoff

- [x] RT lockdown lane includes streaming breach contract coverage.
- [x] Promotion bundle replay passes with streaming gates active.

## 7. Final Promotion

- [x] Exit criteria doc updated and aligned.
- [x] Wishlist status updated from `Partial` to `In` (Vulkan scoped).
