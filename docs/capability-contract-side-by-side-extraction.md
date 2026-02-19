# Capability Contract Side-by-Side Extraction (Shadows vs Reflections)

Date: 2026-02-19  
Scope: extraction synthesis from `docs/capability-contract-shadow-audit.md` and `docs/capability-contract-reflections-audit.md`.

## 1. Pass Contribution

- Shadows: one graph-visible `PRE_MAIN` pass with dynamic frame count and optional moment outputs.
- Reflections: conditional `PRE_MAIN` planar capture pass + reflection resolve logic in post path.
- Both include internal barrier/copy sequences hidden from graph-level pass declarations.
- Reflection planar capture introduces recursive feature scope requirements (capture rerender depends on other capabilities).

Contract implications:

1. Capability can contribute zero or more passes.
2. Pass declarations need `conditional` and `dynamicPassCount` metadata.
3. Pass declarations need `requiredFeatureScopes` for recursive composition.
4. Internal GPU sequencing must be representable as pass-owned internal barrier sequences.

## 2. Shader Contribution

- Shadows inject into main fragment lighting.
- Reflections inject into main fragment (probe evaluation) and post fragment (SSR/planar/RT/hybrid/transparency resolve).
- Reflections require optional per-material dispatch and runtime adaptive policy modulation.

Contract implications:

1. Shader contribution must target a host pass and injection point.
2. Shader contribution must declare stage + implementation key.
3. Shader contribution must carry descriptor/uniform/push-constant requirements.
4. Shader contribution metadata must support `perMaterialDispatch` and `runtimeAdaptive`.

## 3. Descriptor and Uniform Requirements

- Shadows: scene UBO + shadow samplers (set1:4/8) and shadow push constants.
- Reflections: scene SSBO (set0:2), probe sampler lane (set1:9), post planar sampler lane, planar/post push constants.
- Binding frequencies differ (per-frame, per-material, per-pass, per-draw).

Contract implications:

1. Descriptor requirements must include set/binding/type/frequency/condition.
2. Uniform requirements must declare target block + field.
3. Push-constant requirements must declare pass/stages/range.
4. Validation must detect collisions in pass-scoped descriptor bindings.

## 4. Resource Ownership and Lifecycle

- Shadows: transient depth + conditional moment atlas; explicit recreate triggers.
- Reflections: persistent-partial-update probe metadata/atlas, transient planar/RT lanes, cross-frame temporal history resources.

Contract implications:

1. Resource declaration needs explicit lifecycle category.
2. Resource declaration needs conditional activation metadata.
3. Resource declaration needs declarative recreate triggers.

## 5. Scheduling and Budgets

- Shadows: cadence/face/layer scheduling controls pass count/activation.
- Reflections: probe streaming cadence/visible budget + RT lane gating + adaptive SSR/TAA policy.

Contract implications:

1. Scheduler declaration should be independent from pass declaration.
2. Scheduler metadata should expose budget parameters.
3. Scheduler metadata should indicate whether it affects pass count and/or pass activation.

## 6. Telemetry and CI Surface

- Both capabilities expose warning-based contracts.
- Reflections also expose broad typed diagnostics and event surfaces used by parser-free CI gates.

Contract implications:

1. Telemetry declaration is first-class contract metadata.
2. Telemetry declaration should include warning types, typed diagnostics, callback event types, CI gate identifiers.

## Implemented v2 Contract Surface

The extracted v2 SPI contract is implemented in `engine-spi` with these key types:

- `RenderFeatureCapabilityV2`
- `RenderCapabilityContractV2`
- `RenderPassDeclaration`
- `RenderShaderContribution`
- `RenderDescriptorRequirement`
- `RenderUniformRequirement`
- `RenderPushConstantRequirement`
- `RenderResourceDeclaration`
- `RenderSchedulerDeclaration`
- `RenderTelemetryDeclaration`
- supporting enums/value types for modes, injection points, descriptor types, and lifecycle categories.

## Vulkan Proof Descriptors

Metadata-only proofs that the contract can represent both mature domains:

- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/capability/VulkanShadowCapabilityDescriptorV2.java`
- `engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan/capability/VulkanReflectionCapabilityDescriptorV2.java`

Cross-capability validation tests:

- `engine-impl-vulkan/src/test/java/org/dynamislight/impl/vulkan/capability/VulkanCapabilityContractV2DescriptorsTest.java`

## Explicit Phase C Inputs (known open mechanics)

1. Shader module assembly mechanism from `RenderShaderContribution` declarations.
2. Execution mechanics for `requiredFeatureScopes` recursive composition.
3. Descriptor layout composition/conflict resolution strategy across capabilities.
4. Concrete per-material dispatch plumbing for shader/runtime wiring.

