# Mechanical Sympathy and GPU-Driven Roadmap (2026+)

Last updated: February 17, 2026

Primary roadmap: `docs/rendering-roadmap-2026.md`

## Purpose
This roadmap captures the next competitive rendering architecture steps for:
- JVM mechanical sympathy (Valhalla-oriented data paths)
- GPU-driven rendering (compute culling/LOD/indirect draw)
- Vulkan descriptor-model modernization for Vulkan-primary execution
- Dynamic GI (DDGI-first, voxel/SDF follow-up)

It is written as an execution plan you can return to and sequence over multiple releases.

## Guardrails
- Keep `engine-api` additive and source-compatible.
- Keep OpenGL functional as fallback while Vulkan becomes the performance lane.
- Keep compare-harness quality gates active (temporal metrics first, PNG diff second).
- Prefer feature flags and capability checks over hard backend assumptions.

## Track 1: Valhalla-Oriented Data Layout
Status: Planned foundation work.

### Goal
Reduce allocation pressure and pointer-chasing in math-heavy/render-setup loops by using value-like layouts where runtime support is production-safe.

### Phase 1 (Preparation)
1. Define hot-path DTO boundaries:
   - transform arrays
   - culling input records
   - draw packet records
2. Isolate math/storage behind internal interfaces so representation can switch.
3. Add JMH benchmarks for:
   - transform update throughput
   - culling packet build throughput
   - per-frame allocation rate

### Phase 2 (Dual Representation)
1. Introduce value-layout implementation for:
   - `Vec3`/`Mat4`-equivalent internal runtime types
   - draw/culling packet structs
2. Keep object-layout fallback in parallel.
3. Runtime-select representation using capability/config flag.

### Phase 3 (Adoption)
1. Move hottest loops to value-layout path by default on validated JVMs.
2. Keep fallback path for unsupported runtimes.
3. Re-baseline compare/perf gates before default changes.

### Exit Criteria
- Lower allocation rate in frame build path.
- Measurable frame-time reduction in scene update + culling phases.
- No behavior regressions in parity tests.

## Track 2: GPU-Driven Rendering (Vulkan-First)
Status: Planned.

### Goal
Shift visibility/LOD/draw preparation from CPU to GPU to scale scene complexity and reduce CPU bottlenecks.

### Phase 1 (Visibility Backbone)
1. Add depth pyramid (HZB) generation pass.
2. Add compute frustum + occlusion culling pass.
3. Emit visibility buffers for indirect draw construction.

### Phase 2 (GPU LOD and Draw Compaction)
1. Move LOD selection into compute pass.
2. Build compacted indirect draw lists (`vkCmdDrawIndexedIndirectCount` path).
3. Add scene-class toggles for targeted AA stress classes:
   - foliage alpha
   - specular micro-highlights
   - thin geometry motion
   - disocclusion/rapid pan

### Phase 3 (Renderer Integration)
1. Integrate GPU-generated draw lists into main render passes.
2. Add per-pass fallback to CPU path for validation.
3. Add profiling counters:
   - culled instance count
   - LOD distribution
   - indirect draw count
   - CPU time saved vs CPU culling path

### Exit Criteria
- Lower CPU frame time in geometry-heavy scenes.
- Stable visual output across OpenGL parity thresholds (within expected envelope).
- No regressions in AA temporal drift gates.

## Track 3: Vulkan Descriptor-Model Modernization
Status: Planned SPI hardening.

### Goal
Future-proof Vulkan-primary path for modern descriptor models (including descriptor-heap/buffer-era capabilities) without breaking current backends.

### Phase 1 (SPI Capability Layer)
1. Introduce internal capability model:
   - classic descriptor-set path
   - modern descriptor-buffer/heap-like path
2. Keep SPI backend contract abstract (not descriptor-set-specific).
3. Add backend capability negotiation during runtime init.

### Phase 2 (Binding Abstraction)
1. Create unified resource-binding interface used by frame graph/passes.
2. Implement adapter A: classic descriptors.
3. Implement adapter B: modern descriptor model (feature-gated).

### Phase 3 (Adoption + Telemetry)
1. Default to modern path where supported and validated.
2. Add telemetry:
   - descriptor update volume
   - bind cost per frame
   - fallback usage counts
3. Keep deterministic fallback to classic path.

### Exit Criteria
- Vulkan runtime can switch binding model by capability.
- No API breakage at `engine-api` boundary.
- Parity + stability tests pass in both binding modes.

## Track 4: Dynamic GI (DDGI-First)
Status: Planned.

### Goal
Move from static/baked-era global illumination behavior to runtime-updated diffuse GI suitable for modern dynamic scenes.

### Why DDGI First
- Better near-term integration cost/risk than full real-time GI replacement.
- Works with existing raster pipeline and quality tiers.
- Scales incrementally from "single-bounce diffuse baseline" to richer probes and update policies.

### Phase 1 (Foundations)
1. Add GI capability and feature flags in runtime config:
   - `giMode=off|ddgi|voxel`
   - `giQuality=low|medium|high|ultra`
2. Define GI data interfaces in internal runtime (not `engine-api` breaking).
3. Add telemetry and warning codes:
   - GI update cost
   - probe update coverage
   - fallback/degraded mode signaling

### Phase 2 (DDGI Baseline)
1. Implement probe grid placement + probe volume management.
2. Add ray/sample update pass for probe irradiance + visibility.
3. Integrate probe sampling into diffuse lighting path in OpenGL and Vulkan.
4. Add temporal stabilization and leak reduction controls.

### Phase 3 (DDGI Productionization)
1. Add adaptive probe update scheduling (camera/scene-change-aware).
2. Add probe relocation/reclassification to reduce light leaking in complex interiors.
3. Add tier policy:
   - lower tier: sparser probes / slower updates
   - higher tier: denser probes / faster updates
4. Add compare-harness GI profiles for dynamic lighting motion scenes.

### Phase 4 (Voxel/SDFGI Optional Lane)
1. Evaluate voxel GI or SDFGI for scenes where probe GI is insufficient.
2. Keep DDGI as default baseline path; voxel/SDF as opt-in high-end path.
3. Reuse capability model so both GI backends can coexist.

### Exit Criteria
- Dynamic light changes influence indirect diffuse response at runtime.
- GI quality tiers are deterministic and measurable.
- Performance/quality tradeoffs are explicit and test-gated.

## Cross-Track Dependencies
1. Track 3 Phase 1 should land before Track 2 Phase 3 default enablement.
2. Track 1 benchmarks should run before and after Track 2 major milestones.
3. Track 3 capability model should be in place before Track 4 defaults on Vulkan-primary path.
4. Threshold freezes should happen only after at least 3 stable real-Vulkan runs.

## Suggested Milestone Order
1. M1: Track 3 Phase 1 + Track 1 Phase 1.
2. M2: Track 2 Phase 1 + Track 1 Phase 2.
3. M3: Track 2 Phase 2 + Track 3 Phase 2.
4. M4: Track 4 Phase 1 + Phase 2 (DDGI baseline).
5. M5: Track 2 Phase 3 + Track 1 Phase 3 + Track 3 Phase 3 + Track 4 Phase 3.

## Definition of “Competitive” for This Plan
- CPU frame prep scales with scene complexity better than current baseline.
- Real Vulkan lane is deterministic and performance-primary.
- AA quality remains bounded by temporal metric gates, not just image diff.
- Descriptor model can evolve without forcing API churn.
- Dynamic GI is available as a runtime-updated path (at least DDGI baseline).
