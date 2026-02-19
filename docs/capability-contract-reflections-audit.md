# Reflections Capability Audit (Vulkan)

Date: 2026-02-19  
Scope: Step 1 audit for capability-contract extraction, based on implemented behavior.

## 1. Passes Contributed

### Graph-visible pass contribution

- Reflection-owned graph pass recorder:
  - `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanPlanarReflectionPassRecorder.java:14`
- Feature/pass identity:
  - `featureId = vulkan.reflections.planar` (`VulkanPlanarReflectionPassRecorder.java:15`)
  - `passId = planar_capture` (`VulkanPlanarReflectionPassRecorder.java:16`)
  - phase `PRE_MAIN` (`VulkanPlanarReflectionPassRecorder.java:52`)
- Declared graph resources:
  - reads: `scene_color` (`VulkanPlanarReflectionPassRecorder.java:53`)
  - writes: `planar_capture` (`VulkanPlanarReflectionPassRecorder.java:54`)
- Post reflections are currently composed inside the post composite pass, not a standalone graph node:
  - post pass declaration: `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanPostCompositePassRecorder.java:49`
  - reflections module active/pruned via `post.reflections.resolve` (`VulkanPostCompositePassRecorder.java:81`)

### Runtime execution shape inside reflection callbacks

- Planar capture execution entry:
  - `recordPlanarReflectionPass(...)` in `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanRenderCommandRecorder.java:501`
- Planar execution is conditional on runtime mode bits + capture image availability:
  - `isPlanarReflectionPassRequested(...)` (`VulkanRenderCommandRecorder.java:205`)
- Planar pass behavior:
  - optional timestamp writes around capture (`VulkanRenderCommandRecorder.java:509`, `VulkanRenderCommandRecorder.java:528`)
  - selective pre-main geometry rerender via `recordMainRenderPass(..., planarSelectiveOnly=true)` (`VulkanRenderCommandRecorder.java:519`)
  - selective scope filtering by per-mesh override mode + scope bits (`VulkanRenderCommandRecorder.java:695`)
  - copy capture image + transition back to shader-read (`VulkanRenderCommandRecorder.java:527`, `VulkanRenderCommandRecorder.java:837`)
- Post composite execution entry:
  - `executePostCompositePass(...)` (`VulkanRenderCommandRecorder.java:1010`)
  - reflections are resolved in post shader using `PostCompositeInputs.reflections*` push constants (`VulkanRenderCommandRecorder.java:1368`)

### Internal GPU operations hidden from graph contract

- Planar pass performs internal layout transitions/copy sequence for capture source/destination images:
  - source to transfer, destination to transfer, copy, destination to shader-read, source back to color (`VulkanRenderCommandRecorder.java:842` to `VulkanRenderCommandRecorder.java:958`)
- Post pass has internal fallback path when planar capture was requested but geometry capture did not execute:
  - fallback copy from offscreen color into planar capture (`VulkanRenderCommandRecorder.java:1171` to `VulkanRenderCommandRecorder.java:1282`)
- These are internal to callbacks; graph contract surfaces only pass-level read/write resources.

## 2. Shader Contributions Into Other Passes

### Main fragment contribution (probe sampling + overrides)

- Main shader includes reflection probe metadata SSBO and probe-radiance sampler bindings:
  - probe SSBO `set=0,binding=2` (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/shader/VulkanShaderSources.java:207`)
  - probe radiance sampler `set=1,binding=9` (`VulkanShaderSources.java:220`)
- Box-projected probe sampling + weighted blending in main fragment:
  - probe weights and priority/distance weighting (`VulkanShaderSources.java:245`)
  - box-projection direction correction (`VulkanShaderSources.java:259`)
  - runtime probe loop and blend into specular radiance (`VulkanShaderSources.java:1608` to `VulkanShaderSources.java:1649`)
- Per-material reflection override is exported through scene color alpha in main pass:
  - override mode extraction and mask write (`VulkanShaderSources.java:1200`, `VulkanShaderSources.java:1735`)

### Post fragment contribution (SSR/planar/RT/hybrid/transparency composition)

- Post shader reflection resolve entry:
  - `applyReflections(...)` (`VulkanShaderSources.java:1856`)
- Runtime mode-bit decoding in shader (SSR/planar/RT/reprojection/history/transparency):
  - packed bit unpack (`VulkanShaderSources.java:1869` to `VulkanShaderSources.java:1885`)
- SSR path, confidence/hit shaping, and contact-hardening ramp:
  - SSR march + hit scoring (`VulkanShaderSources.java:1900` to `VulkanShaderSources.java:1914`)
  - contact-hardening roughness/strength modulation (`VulkanShaderSources.java:1915` to `VulkanShaderSources.java:1918`)
- Reflection-space reprojection and stricter history rejection logic:
  - reflection-space reprojection branch (`VulkanShaderSources.java:1936`)
  - strict/disocclusion rejection gates (`VulkanShaderSources.java:1946` to `VulkanShaderSources.java:1948`)
- Planar sampling and probe volume fallback modulation:
  - planar capture texture sampling (`VulkanShaderSources.java:1931`)
  - probe-volume/post-space blending fallback (`VulkanShaderSources.java:1959`)
- RT lane (single/multi-bounce simulation), denoise, and hybrid composition:
  - RT lane path (`VulkanShaderSources.java:1967` to `VulkanShaderSources.java:2009`)
  - hybrid composition (`VulkanShaderSources.java:2018`)
- Transparency/refraction composition using RT or planar/probe fallback:
  - transparency integration branch (`VulkanShaderSources.java:2025` to `VulkanShaderSources.java:2035`)

## 3. Descriptor and Uniform Contributions

### Descriptor sets/bindings

- Scene descriptor set includes reflection probe metadata storage buffer at binding 2:
  - layout declaration (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/descriptor/VulkanDescriptorResources.java:263`)
  - per-frame descriptor writes for probe metadata buffer (`VulkanDescriptorResources.java:382`, `VulkanDescriptorResources.java:402`)
- Texture descriptor set includes dedicated probe radiance lane at binding 9:
  - writer + binding (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/descriptor/VulkanTextureDescriptorWriter.java:83`, `VulkanTextureDescriptorWriter.java:154`)
- Post descriptor set includes dedicated planar capture sampler lane:
  - post shader `uPlanarCaptureColor` binding (`VulkanShaderSources.java:1771`)

### Uniform/mode contract

- Global scene UBO includes planar matrices used by planar capture/main path:
  - `uPlanarView`, `uPlanarProj`, `uPlanarPrevViewProj` (`VulkanShaderSources.java:187` to `VulkanShaderSources.java:189`)
  - runtime population in uniform prep (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java:1589` to `VulkanContext.java:1697`)
- Main-pass push constants carry planar flag/height for clip/capture behavior:
  - push constant write (`VulkanRenderCommandRecorder.java:590`)
- Post-pass push constants include reflection controls and mode word:
  - `reflectionsA/reflectionsB` in shader (`VulkanShaderSources.java:1779`)
  - packed from post inputs (`VulkanRenderCommandRecorder.java:1368`)
- Runtime composes reflection execution mode bits per frame:
  - `composeReflectionExecutionMode(...)` (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanEngineRuntime.java:2951`)

## 4. Resources and Lifecycles

### Owned resources

- Reflection probe metadata buffer (persistent mapped storage buffer):
  - allocation metadata fields (`VulkanDescriptorResources.java:434` to `VulkanDescriptorResources.java:439`)
- Probe radiance atlas texture and slot map:
  - slot assignment + atlas rebuild (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java:1757` to `VulkanContext.java:1785`)
- Planar capture image lane + sampler consumption in post:
  - runtime bindings in frame inputs (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/command/VulkanFrameCommandOrchestrator.java:196`)
  - shader sampler lane (`VulkanShaderSources.java:1771`)
- TAA history/history velocity are cross-frame reflection inputs/outputs in post pass:
  - post pass graph resources (`VulkanPostCompositePassRecorder.java:54` to `VulkanPostCompositePassRecorder.java:55`)

### Lifecycle behavior

- Probe metadata upload is per-frame from persisted mapped buffer:
  - update call in frame prep (`VulkanContext.java:1588`)
  - upload with frustum/cadence/LOD limits (`VulkanContext.java:1723`)
- Probe metadata packing embeds per-probe blend distance, priority, slot, box-projection flag, and LOD tier:
  - pack/write details (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/scene/VulkanReflectionProbeCoordinator.java:188` to `VulkanReflectionProbeCoordinator.java:209`)
- Probe active-set selection uses frustum cull + priority sort + cadence rotation + max visible budget:
  - selection logic (`VulkanReflectionProbeCoordinator.java:136` to `VulkanReflectionProbeCoordinator.java:186`)
- Planar capture timing resources are optional and guarded via timestamp query support:
  - query pool lifecycle in context (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanContext.java:1207` to `VulkanContext.java:1249`)

## 5. Scheduling and Budget Logic

Primary reflection scheduling/budget behavior is runtime-owned and fed into pass/shader execution:

- Reflection mode/runtime policy composition:
  - RT lane request/activation/fallback-chain computation (`VulkanEngineRuntime.java:2909`)
  - composed execution bits for planar scope, RT, reprojection/history, transparency (`VulkanEngineRuntime.java:2951`)
- Probe scheduling/budgeting:
  - update cadence + max-visible + LOD depth scale parsed via options (`engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/VulkanRuntimeOptions.java:127` to `VulkanRuntimeOptions.java:129`)
  - consumed during metadata upload (`VulkanContext.java:1733` to `VulkanContext.java:1735`)
- SSR/TAA adaptive policy scheduling:
  - severity and policy activation derived from EMA reject/confidence/streak (`VulkanEngineRuntime.java:2889`)

## 6. Telemetry and Warning Outputs

### Core warning payloads

- Reflection baseline/quality + policy envelopes:
  - `REFLECTIONS_BASELINE_ACTIVE`, `REFLECTIONS_QUALITY_DEGRADED` (`VulkanEngineRuntime.java:1037`, `VulkanEngineRuntime.java:2197`)
  - override/contact policy + breach warnings (`VulkanEngineRuntime.java:1061`, `VulkanEngineRuntime.java:1091`, `VulkanEngineRuntime.java:1137`)
- Probe diagnostics/quality/streaming/churn warnings:
  - `REFLECTION_PROBE_BLEND_DIAGNOSTICS` (`VulkanEngineRuntime.java:1161`)
  - `REFLECTION_PROBE_STREAMING_DIAGNOSTICS` + envelope/breach (`VulkanEngineRuntime.java:1210`, `VulkanEngineRuntime.java:1244`, `VulkanEngineRuntime.java:1253`)
  - `REFLECTION_PROBE_QUALITY_SWEEP` + envelope breach (`VulkanEngineRuntime.java:1261`, `VulkanEngineRuntime.java:1282`)
  - `REFLECTION_PROBE_CHURN_HIGH` (`VulkanEngineRuntime.java:2187`)
- Planar/RT/transparency warnings:
  - planar contract/stability/perf/resource warnings (`VulkanEngineRuntime.java:1310`, `VulkanEngineRuntime.java:1372`, `VulkanEngineRuntime.java:1438`)
  - RT path/hybrid/denoise/AS/promotion warnings (`VulkanEngineRuntime.java:1491`, `VulkanEngineRuntime.java:1632`, `VulkanEngineRuntime.java:1682`, `VulkanEngineRuntime.java:1725`, `VulkanEngineRuntime.java:1873`)
  - transparency stage/policy warnings (`VulkanEngineRuntime.java:1815`, `VulkanEngineRuntime.java:1823`)
- SSR/TAA diagnostics + adaptive trend/SLO envelope warnings:
  - `REFLECTION_SSR_TAA_DIAGNOSTICS` (`VulkanEngineRuntime.java:2001`)
  - `REFLECTION_SSR_REPROJECTION_ENVELOPE` + breach (`VulkanEngineRuntime.java:2085`, `VulkanEngineRuntime.java:2102`)
  - adaptive trend report/SLO/high-risk (`VulkanEngineRuntime.java:2111`, `VulkanEngineRuntime.java:2136`, `VulkanEngineRuntime.java:2159`)

### Typed diagnostics/profile surfaces

- Typed debug surfaces for reflection subsystems are first-class runtime APIs:
  - probe/streaming/churn/quality (`VulkanEngineRuntime.java:3263`, `VulkanEngineRuntime.java:3281`, `VulkanEngineRuntime.java:3320`, `VulkanEngineRuntime.java:3324`)
  - planar contract/stability/perf (`VulkanEngineRuntime.java:3339`, `VulkanEngineRuntime.java:3349`, `VulkanEngineRuntime.java:3363`)
  - override/contact (`VulkanEngineRuntime.java:3383`, `VulkanEngineRuntime.java:3405`)
  - RT path/perf/pipeline/hybrid/denoise/AS/promotion (`VulkanEngineRuntime.java:3419`, `VulkanEngineRuntime.java:3441`, `VulkanEngineRuntime.java:3453`, `VulkanEngineRuntime.java:3464`, `VulkanEngineRuntime.java:3478`, `VulkanEngineRuntime.java:3492`, `VulkanEngineRuntime.java:3506`)
  - transparency + adaptive/history/trend + trend SLO (`VulkanEngineRuntime.java:3520`, `VulkanEngineRuntime.java:3539`, `VulkanEngineRuntime.java:3554`, `VulkanEngineRuntime.java:3574`, `VulkanEngineRuntime.java:3578`)
- Typed record contracts are explicit in-runtime types (`VulkanEngineRuntime.java:3599` to `VulkanEngineRuntime.java:3931`).

## 7. CI/Test Contracts Currently Enforced

- Reflection integration coverage (warnings + typed diagnostics + profile defaults + envelopes):
  - `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/VulkanEngineRuntimeIntegrationTest.java`
- Reflection runtime option parsing + bounds/defaults:
  - `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/VulkanRuntimeOptionsTest.java`
- Post modularization coverage including reflections resolve module activation/pruning:
  - `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/command/VulkanPostCompositePassRecorderTest.java`
- Dedicated reflection CI/sweep/signoff runners:
  - `scripts/planar_ci_lockdown_full.sh`
  - `scripts/planar_real_gpu_signoff.sh`
  - `scripts/rt_reflections_ci_lockdown_full.sh`
  - `scripts/rt_reflections_real_gpu_signoff.sh`
  - `scripts/rt_reflections_real_longrun_signoff.sh`
  - `scripts/rt_reflections_in_promotion_bundle.sh`
- Reflection testing guide and required checks:
  - `docs/testing/reflections-testing.md`

## 8. Contract Extraction Notes (from reflections audit only)

From reflections alone, the reusable capability shape is:

1. Capability may contribute one or more graph-visible passes, and also contribute shader logic inside existing host passes.
2. Capability behavior is mode-bit driven at runtime, with composed execution flags (planar/RT/reprojection/history/transparency).
3. Descriptor requirements span mixed frequencies/types (scene SSBO + per-material samplers + post inputs).
4. Resource ownership includes transient outputs (planar/post intermediates) plus persistent streaming/stateful resources (probe metadata/atlas/history).
5. Scheduling is split across spatial selection/budget (probe cadence/visible cap/LOD), execution-lane gating (RT/planar), and temporal risk adaptation (SSR/TAA).
6. Telemetry surface is dual: warning payloads plus typed diagnostics/events designed for parser-free CI gates.

This is the reflections-side evidence baseline for side-by-side extraction with shadows.
