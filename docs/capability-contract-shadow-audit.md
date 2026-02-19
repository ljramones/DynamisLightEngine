# Shadow Capability Audit (Vulkan)

Date: 2026-02-19  
Scope: Step 1 audit for capability-contract extraction, based on implemented behavior.

## 1. Passes Contributed

### Graph-visible pass contribution

- Feature recorder: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanShadowPassRecorder.java:14`
- Feature/pass identity:
  - `featureId = vulkan.shadow` (`VulkanShadowPassRecorder.java:15`)
  - `passId = shadow_passes` (`VulkanShadowPassRecorder.java:16`)
  - phase `PRE_MAIN` (`VulkanShadowPassRecorder.java:52`)
- Declared graph resources:
  - reads: none (`VulkanShadowPassRecorder.java:53`)
  - writes:
    - always `shadow_depth`
    - conditional `shadow_moment_atlas` when moment pipeline requested (`VulkanShadowPassRecorder.java:45`)

### Runtime execution shape inside shadow pass callback

- Entry: `recordShadowPasses(...)` in `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanRenderCommandRecorder.java:215`
- Pass count is dynamic per frame:
  - computed by `shadowPassCount(...)` (`VulkanRenderCommandRecorder.java:998`)
  - clamps to requested cascades, max cascades, max matrices
  - point-shadow legacy mode enforces cubemap-face floor (`pointShadowFaces`) (`VulkanRenderCommandRecorder.java:1002`)
- Internal draw loop:
  - one render pass per cascade/face (`VulkanRenderCommandRecorder.java:261`)
  - depth-only baseline; optional moment color attachment clear/write (`VulkanRenderCommandRecorder.java:262`)
  - push constant selects cascade index (`VulkanRenderCommandRecorder.java:289`)
  - per-mesh indexed draw (`VulkanRenderCommandRecorder.java:292`)

### Internal GPU operations hidden from graph contract

- If moment pipeline enabled:
  - transitions moment atlas to color-attachment before shadow rendering (`VulkanRenderCommandRecorder.java:222`)
  - generates mip chain via `vkCmdBlitImage` and per-mip barriers (`VulkanRenderCommandRecorder.java:310`)
  - transitions moment atlas to shader-read at end (`VulkanRenderCommandRecorder.java:423`)
- These are internal to the pass callback; graph contract only exposes final write of `shadow_moment_atlas`.

## 2. Shader Contributions Into Other Passes

### Shadow-only shaders

- Shadow vertex: transforms with `uShadowLightViewProj[cascade]` (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderSources.java:7`)
- Shadow fragment:
  - depth-only variant (`VulkanShaderSources.java:60`)
  - moment variant outputs `(d, d^2, 0, 0)` (`VulkanShaderSources.java:67`)

### Main fragment shadow contribution

- Main fragment consumes shadow capability via:
  - shadow map sampling (`uShadowMap`, set1 binding4) (`VulkanShaderSources.java:215`)
  - optional moment atlas sampling (`uShadowMomentMap`, set1 binding8) (`VulkanShaderSources.java:219`)
- Technique behavior is selected by uniform-packed mode values:
  - PCF / PCSS / VSM / EVSM branches (`VulkanShaderSources.java:925`, `VulkanShaderSources.java:1043`)
  - optional RT traversal/denoise branches (`VulkanShaderSources.java:1050`)
  - optional contact shadow modulation (`VulkanShaderSources.java:1402`)

## 3. Descriptor and Uniform Contributions

### Descriptor sets/bindings

- Scene-level set (`set=0`) includes shadow-related data through global UBO and per-object UBO:
  - descriptor set layout bindings 0/1/2 in `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/descriptor/VulkanDescriptorResources.java:251`
- Texture/material set (`set=1`) includes shadow samplers:
  - binding 4: shadow depth array (`VulkanTextureDescriptorWriter.java:115`)
  - binding 8: shadow moment array (`VulkanTextureDescriptorWriter.java:143`)
- Shadow pipeline layout uses scene descriptor set + push constant (cascade index):
  - `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/pipeline/VulkanShadowPipelineBuilder.java:179`

### Uniform contract

- Global scene UBO includes shadow parameters and matrices:
  - filter/RT/contact tuning and cascade metadata in `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/uniform/VulkanGlobalSceneInputBuilder.java:42`
  - shadow matrices array `uShadowLightViewProj[24]` in shader (`VulkanShaderSources.java:38`)
- Current uniform sizes:
  - global UBO `2736` bytes (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java:82`)
  - per-object UBO `176` bytes (`VulkanContext.java:83`)

## 4. Resources and Lifecycles

### Owned resources

- Shadow depth array image/view/sampler + per-layer views (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shadow/VulkanShadowResources.java:76`)
- Optional moment array image/view/sampler + per-layer views + mip levels (`VulkanShadowResources.java:143`)
- Shadow render pass, pipeline layout, graphics pipeline, framebuffers (`VulkanShadowResources.java:126`)

### Lifecycle behavior

- Create/destroy coordinated by `VulkanShadowLifecycleCoordinator`:
  - create (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shadow/VulkanShadowLifecycleCoordinator.java:33`)
  - destroy (`VulkanShadowLifecycleCoordinator.java:76`)
- Recreated when:
  - shadow map resolution changes (`VulkanContext.java:587`)
  - moment pipeline mode/request changes (`VulkanContext.java:640`)
- After recreation:
  - moment initialization reset (`VulkanContext.java:592`, `VulkanContext.java:649`)
  - texture descriptor sets refreshed if meshes exist (`VulkanContext.java:593`)

## 5. Scheduling and Budget Logic

Primary shadow scheduling/budget logic is currently owned by runtime mapping, not the recorder:

- Mapper entry: `mapShadows(...)` in `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntimeLightingMapper.java:724`
- Tier-aware budgets:
  - max shadowed local lights, max local layers, optional face budget (`VulkanEngineRuntimeLightingMapper.java:754`)
- Cadence scheduler:
  - hero/mid/distant periods (`VulkanEngineRuntimeLightingMapper.java:1197`)
  - due check (`VulkanEngineRuntimeLightingMapper.java:1207`)
  - staleness bypass to prevent starvation (`VulkanEngineRuntimeLightingMapper.java:1214`)
- Deferred/selected IDs and stale-bypass counts are tracked in returned `LocalShadowSchedule` (`VulkanEngineRuntimeLightingMapper.java:1259`)
- Runtime applies scheduler each frame:
  - increments frame tick (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntime.java:820`)
  - refreshes shadow config and updates last-rendered tick map (`VulkanEngineRuntime.java:822`, `VulkanEngineRuntime.java:842`)

## 6. Telemetry and Warning Outputs

### Core warning payloads

- Policy envelope warning with full shadow telemetry:
  - `SHADOW_POLICY_ACTIVE` (`VulkanEngineRuntime.java:2249`)
  - includes budgets, rendered/deferred IDs, atlas estimates, moment state, filter path, RT mode/state, scheduler settings
- Quality and capability-path warnings:
  - `SHADOW_QUALITY_DEGRADED` (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanRuntimeWarningPolicy.java:65`)
  - `SHADOW_LOCAL_RENDER_BASELINE` (`VulkanEngineRuntime.java:2321`)
  - moment state warnings (`VulkanEngineRuntime.java:2337`)
  - RT requested/fallback/pending/native warnings (`VulkanEngineRuntime.java:2367`)
- Cascade profile warning:
  - `SHADOW_CASCADE_PROFILE` (`VulkanRuntimeWarningPolicy.java:292`)

### Typed diagnostics/profile surfaces

- Shadow cascade profile typed surface:
  - `shadowCascadeProfile()` in `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java:438`
- Depth/moment format tags for diagnostics:
  - `shadowDepthFormatTag()` / `shadowMomentFormatTag()` (`VulkanContext.java:157`)

## 7. CI/Test Contracts Currently Enforced

- Unit/integration validation of shadow mapping policy, cadence, and warnings:
  - `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/VulkanEngineRuntimeLightingMapperTest.java`
  - `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/VulkanEngineRuntimeIntegrationTest.java`
  - `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/VulkanRuntimeOptionsTest.java`
- Dedicated shadow CI/sweep runners:
  - `scripts/shadow_ci_matrix.sh`
  - `scripts/shadow_ci_lockdown_full.sh`
  - `scripts/shadow_production_quality_sweeps.sh`
  - `scripts/shadow_real_longrun_guarded.sh`
  - `scripts/shadow_quality_finalize_real.sh`
- Shadow testing guide and expected warning codes:
  - `docs/testing/shadows-testing.md`

## 8. Contract Extraction Notes (from shadow audit only)

From shadow alone, the reusable capability shape is:

1. One graph-visible pass contribution with declared writes.
2. Technique-specific shader contribution consumed by main pass.
3. Descriptor requirements split between scene-level and texture-level bindings.
4. Owned resources with explicit recreate triggers (resolution/mode changes).
5. Separate scheduling policy layer (cadence/budgets) feeding render-pass count/work selection.
6. Warning/telemetry outputs as first-class runtime contract surface.

This is the shadow-side evidence baseline for side-by-side extraction with reflections.
