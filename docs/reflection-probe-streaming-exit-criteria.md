# Reflection Probe Streaming Exit Criteria (Partial -> In)

Scope date: 2026-02-18  
Primary scope: Vulkan production path (`engine-impl-vulkan`)

Detailed checklist: `docs/reflection-probe-streaming-in-checklist.md`.

## Definition of `In` (Vulkan-scoped)

Probe streaming is `In` when residency selection is budget-aware, diagnostics are typed and CI-verifiable, and envelope gates are enforced with deterministic breach behavior.

## Exit Checklist

- [x] Streaming diagnostics warning is emitted each frame.
  Evidence: `REFLECTION_PROBE_STREAMING_DIAGNOSTICS`.
- [x] Budget pressure warning is emitted under constrained visible budgets.
  Evidence: `REFLECTION_PROBE_STREAMING_BUDGET_PRESSURE`.
- [x] Typed streaming diagnostics accessor is available.
  Evidence: `debugReflectionProbeStreamingDiagnostics`.
- [x] Envelope warning and breach gates exist with streak/cooldown behavior.
  Evidence: `REFLECTION_PROBE_STREAMING_ENVELOPE`, `REFLECTION_PROBE_STREAMING_ENVELOPE_BREACH`.
- [x] Streaming thresholds are backend-option configurable.
  Evidence: `vulkan.reflections.probeStreaming*`.
- [x] Coordinator + integration tests cover metadata/selection and breach paths.
  Evidence: `VulkanReflectionProbeCoordinatorTest`, `VulkanEngineRuntimeIntegrationTest`.
- [x] Guarded real-Vulkan contract test exists.
  Evidence: `guardedRealVulkanProbeStreamingContractEmitsDiagnostics`.

## Status Note

As of 2026-02-18, probe streaming is `In` for Vulkan scope with diagnostics + envelope gates + CI coverage. OpenGL parity remains a separate follow-up.
