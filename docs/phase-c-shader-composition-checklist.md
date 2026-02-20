# Phase C Shader/Descriptor Composition Checklist

Date: 2026-02-19  
Scope: final architecture migration for composed shaders, composed descriptor layouts, and profile-compiled pipelines.

## C.1 Shader Module System

- [x] C.1.1 Define shader module format in SPI (`RenderShaderModuleDeclaration`, `RenderShaderModuleBinding`, `RenderShaderModuleContributor`).
- [x] C.1.2 Extract shadow shader module(s) from Vulkan shader sources.
- [x] C.1.3 Extract reflection shader module(s) from Vulkan shader sources.
- [x] C.1.4 Extract AA shader module(s) from Vulkan shader sources.
- [x] C.1.5 Implement shader assembler (host template + module injection).
  - Canonical alignment complete: shadow/reflection/AA module bodies now extracted from Vulkan monolithic shader functions.
- [x] C.1.6 Parallel validation compile for blessed profiles (no runtime cutover).
  - Added blessed profile compile/equivalence matrix test (`performance`, `balanced`, `quality`, `stability`) for monolithic vs assembled canonical shader paths.
- [x] C.1.7 Cutover Vulkan pipelines to assembled shader sources.
  - `VulkanShaderSources.mainFragment()` and `postFragment()` now resolve to canonical assembled runtime sources.
- [x] C.1.8 Remove legacy monolithic shader source path.
  - Removed monolithic runtime accessors from `VulkanShaderSources`; validation now asserts assembled-only compile + module coverage.

## C.2 Descriptor Layout Composition

- [x] C.2.1 Build descriptor layout composer from capability requirements.
  - Added deterministic per-pass descriptor composer with collision rejection and integration tests.
- [x] C.2.2 Add per-profile layout caching.
  - Added `VulkanPipelineProfileCache` keyed by `VulkanPipelineProfileKey` with compile-once reuse.
- [x] C.2.3 Switch descriptor allocation path to composed layouts.
  - Main and texture descriptor-set layouts now build from composed descriptor plans (`VulkanDescriptorResources.create(..., mainGeometryPlan)`).
  - Descriptor pool sizing now derives from composed binding counts; texture ring pool uses `textureDescriptorBindingCount`.
- [x] C.2.4 Bind composed layouts in pipeline builders.
  - Main/post pipeline builders now accept composed fragment shader source payloads.
  - Swapchain creation path now passes composed post descriptor plan + assembled sources through lifecycle coordinators.

## C.3 Profile Compilation/Caching

- [x] C.3.1 Define profile identity (`tier + capability mode set`).
  - Added `VulkanPipelineProfileKey` (`tier + shadow/reflection/aa/post modes`).
- [x] C.3.2 Build profile compiler (shader + layout + pipeline tuple).
  - Added `VulkanPipelineProfileCompiler` and `VulkanPipelineProfileCompilation` (assembled main/post shader source + composed main/post descriptor plans).
- [x] C.3.3 Add runtime profile switching to compiled tuples.
  - `VulkanContext` now resolves active profile from render state, recompiles/caches via profile cache, and performs guarded runtime resource rebuild when profile identity changes.
- [x] C.3.4 Stabilize with full visual/contract regression gates.
  - Added Phase C profile tests:
    - `VulkanPipelineProfileCompilerTest`
    - `VulkanPipelineProfileCacheTest`
    - `VulkanPipelineProfileResolverTest`
  - Compilation verification run for affected modules completed.
