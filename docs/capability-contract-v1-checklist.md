# Capability Contract v1 Checklist

Scope date: 2026-02-19  
Scope target: API/SPI extraction milestone only (no runtime behavior change)

## 1. Decision Record

- [x] Add ADR for capability contract extraction and boundaries.
- [x] Explicitly scope to evidence-driven extraction from shadows + reflections.

## 2. SPI Contract Types

- [x] Add `RenderFeatureCapability` interface.
- [x] Add immutable `RenderFeatureContract`.
- [x] Add pass contribution model types.
- [x] Add shader hook contribution model types.
- [x] Add resource requirement model types including binding frequency metadata.
- [x] Add dependency + validation issue model types.

## 3. Behavior Boundary

- [x] No runtime wiring in Vulkan/OpenGL yet.
- [x] No changes to rendering behavior.

## 4. Tests

- [x] Add SPI tests for immutable defensive copies and interface defaults.

## 5. Validation

- [x] Run targeted module tests for `engine-spi`.
- [x] Ensure repository compiles with new SPI types.

## 6. Follow-up Readiness

- [x] Phase 2 (AA/post hardening) can target this contract surface.
