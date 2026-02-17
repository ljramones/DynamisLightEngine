# Superset Rendering Roadmap (2026)

Last updated: February 17, 2026

Primary roadmap: `docs/rendering-roadmap-2026.md`  
Related deep roadmap: `docs/mechanical-sympathy-gpu-driven-roadmap-2026.md`

## Scope
Execution plan to close feature gaps versus UE5.7, CryEngine 5.7 CE, Unity 6 HDRP, Godot 4.4, and O3DE Atom while preserving DLE strengths:
- Vulkan/OpenGL dual backend
- data-driven runtime/SPI boundaries
- reactive temporal AA profile system

## Superset Matrix (Condensed)
1. Pipeline: Forward+/Deferred/Hybrid path selection; data-driven passes.  
Status: Forward+ baseline present.  
Effort: 2

2. PBR/Shading: layered materials + skin + graph-style authoring.  
Status: GGX/IBL baseline present.  
Effort: 2

3. GI: dynamic GI (DDGI first, SDFGI/voxel follow-up).  
Status: major gap.  
Effort: 5

4. Shadows: VSM/contact/inset plus existing cascades, multi-local atlas/cubemap path, static-cache and cadence-aware updates.  
Status: cascades/PCF + policy budgets/bias scaling present; atlas/cubemap/static-cache lane in progress.  
Effort: 3

5. Reflections: SSR + planar + IBL hybrid, RT path later.  
Status: IBL baseline present.  
Effort: 2

6. AA/Upscale: TAA/TUAA/TSR + external upscaler hooks.  
Status: in active delivery.  
Effort: 1

7. Geometry scaling: meshlet/virtual-geometry lane.  
Status: gap.  
Effort: 4

8. Post stack: volumetrics/atmospheric polish/sharpening.  
Status: tonemap/bloom/SSAO baseline present.  
Effort: 2

9. VFX/Water: compute particles + volumetric fog/caustics lane.  
Status: baseline fog/smoke present.  
Effort: 3

10. RT/Path tracing: hardware RT + software fallback.  
Status: gap.  
Effort: 4

11. Tooling/Other: procedural foliage/editor/pipeline extension polish.  
Status: partial via config/SPI.  
Effort: 2

## Phase Plan
## Phase 1: Parity Core (1-2 weeks)
1. Ship SSR baseline in post stack (depth/normal/roughness-based).
2. Add VSM option for cascaded shadows (tier-gated).
3. Land Dynamic GI skeleton:
   - `giMode`/`giQuality` config flags
   - telemetry/warnings for GI active/degraded states

## Phase 2: Superset Power (3-6 weeks)
1. DDGI baseline integration (probe update + irradiance sampling).
2. Compute VFX baseline (GPU particles, configurable emitters).
3. Atmospheric/post expansion:
   - volumetric fog improvements
   - sky/ozone/night calibration
   - sharpening stage

## Phase 3: Scalability (2-3 months)
1. Meshlet-driven culling/LOD in Vulkan.
2. Indirect draw compaction and larger-scene stress validation.
3. Hardware RT experimental lane (feature-gated) with software fallback.

## Phase 4: Differentiators (ongoing)
1. Java shader-node/graph SPI for custom shading pipelines.
2. External upscaler plugin hooks (FSR/XeSS class integrations).
3. Benchmark suite mapped to representative “next-gen scene classes”.

## Immediate Next 30 Days (Ordered)
1. Finalize TSR acceptance thresholds from real Vulkan runs.
2. Implement SSR baseline and add compare profile gates.
3. Implement GI config/capability scaffold (no visual GI yet).
4. Start DDGI probe-grid prototype on Vulkan path.
5. Add VSM option and tier policy for shadow pipeline.

## Acceptance Criteria
1. New features must be profile-gated in compare harness.
2. Real Vulkan runs must remain deterministic on macOS preflight path.
3. Temporal metric gates remain primary for AA/upsampling validation.
4. SPI/API remain additive and backward-compatible.
