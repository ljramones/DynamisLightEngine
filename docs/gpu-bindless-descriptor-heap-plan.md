# GPU Bindless Descriptor Heap Plan (Vulkan)

Status: Draft for review before implementation
Scope: `engine-impl-vulkan` only

## 1. Device Feature Gate and Fallback Contract

At Vulkan device init (`VulkanBootstrap.createLogicalDevice` + physical-device capability query path), enable and require these `VkPhysicalDeviceDescriptorIndexingFeatures` fields:

- `runtimeDescriptorArray`
- `descriptorBindingPartiallyBound`
- `descriptorBindingVariableDescriptorCount`
- `descriptorBindingStorageBufferUpdateAfterBind`
- `shaderStorageBufferArrayNonUniformIndexing`
- `shaderUniformBufferArrayNonUniformIndexing`

Optional but preferred:

- `descriptorBindingUpdateUnusedWhilePending`
- `shaderSampledImageArrayNonUniformIndexing` (future texture bindless extension path)

Minimum limits required to activate bindless mode (hard gate):

- `maxBoundDescriptorSets >= 4`
- `maxPerStageDescriptorStorageBuffers >= 1024`
- `maxDescriptorSetStorageBuffers >= 2048`
- `maxPerStageDescriptorUniformBuffers >= 512`
- `maxDescriptorSetUniformBuffers >= 512`

Activation rule:

- If all required features + limits pass: `bindlessActive = true`
- Otherwise: `bindlessActive = false` and use existing grouped/per-draw descriptor path

Diagnostics on fallback:

- Warning code: `BINDLESS_DESCRIPTOR_INDEXING_UNAVAILABLE`
- Payload: missing feature names + limit shortfall key/value pairs

## 2. Global Bindless Set Layout

New descriptor set: `set = 3` (reserved for bindless geometry resources).

Bindings:

- `binding = 0`: runtime array of SSBO joint palettes
- `binding = 1`: runtime array of SSBO morph deltas
- `binding = 2`: runtime array of UBO morph weights (can migrate to SSBO later)
- `binding = 3`: runtime array of SSBO instance payloads
- `binding = 4`: DrawMetaBuffer SSBO (per-draw metadata, one entry per indirect command slot)

Initial max capacities (per descriptor array):

- Joint palettes: `8192`
- Morph deltas: `4096`
- Morph weights: `4096`
- Instance payloads: `4096`

Rationale for caps:

- `maxDynamicSceneObjects` is clamped to `8192` in `VulkanContext`
- Keeps a 1:1 ceiling for worst-case skinned meshes

Growth policy:

- Start fixed-cap for first delivery (`v1`) to reduce churn and risk.
- Expansion policy (`v2`): doubling (`oldCap * 2`) per array, capped by queried device limits.

Descriptor pool/layout flags required:

- Set layout create flag: `VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT`
- Relevant binding flags:
  - `VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT`
  - `VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT`
  - `VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT`

## 3. Per-Draw Metadata Buffer Contract

GPU-side draw metadata is a tightly-packed SSBO, one entry per indirect command slot. Use 32-byte entries:

```c
// 32 bytes total
struct DrawMeta {
    uint jointPaletteIndex;   // 0xFFFFFFFF = none
    uint morphDeltaIndex;     // 0xFFFFFFFF = none
    uint morphWeightIndex;    // 0xFFFFFFFF = none
    uint instanceDataIndex;   // 0xFFFFFFFF = none
    uint materialIndex;       // existing/next material indirection hook
    uint drawFlags;           // bitfield: skinned/morph/instanced/alpha/etc.
    uint meshIndex;           // debug/telemetry mapping
    uint reserved0;           // keep 16-byte alignment lanes clean
};
```

Sentinel value for absent resources:

- `INVALID_DESCRIPTOR_INDEX = 0xFFFFFFFFu`

Buffer binding:

- Planned in bindless set (`set=3`, dedicated binding in implementation step 1)

## 4. Allocator and Lifetime Policy

Allocator model per descriptor-array type:

- Free-list allocator with generation counters.
- Handle format: `uint64` packed as `[type:8 | generation:24 | slot:32]`.

Allocation:

- Pop slot from free-list.
- Increment generation for slot on each reuse.
- Write descriptor at slot index.

Free:

- Do not recycle immediately.
- Queue for deferred retirement with `retireFrame = currentFrame + framesInFlight`.

Retirement policy:

- `N = framesInFlight` (current runtime clamp: `2..6`, typical `2..3`).
- Slot is reusable only when `currentFrame >= retireFrame`.

Stale-handle protection:

- On resolve, compare handle generation with current slot generation.
- Mismatch = stale handle; reject and return invalid descriptor index.

## 5. Shader Access Contract (`gl_DrawID`)

Use indirect draw index via `gl_DrawID` to fetch draw metadata, then index bindless arrays.

```glsl
#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(set = 3, binding = 0) readonly buffer JointPaletteHeap { mat4 jointData[]; } jointHeap[];
layout(set = 3, binding = 1) readonly buffer MorphDeltaHeap   { float deltaData[]; } morphHeap[];
layout(set = 3, binding = 2) uniform MorphWeightHeap          { float weights[256]; } morphWeightHeap[];
layout(set = 3, binding = 3) readonly buffer InstanceHeap     { mat4 models[]; } instanceHeap[];

struct DrawMeta {
    uint jointPaletteIndex;
    uint morphDeltaIndex;
    uint morphWeightIndex;
    uint instanceDataIndex;
    uint materialIndex;
    uint drawFlags;
    uint meshIndex;
    uint reserved0;
};

layout(set = 3, binding = 4) readonly buffer DrawMetaBuffer {
    DrawMeta entries[];
} drawMeta;

const uint INVALID_DESCRIPTOR_INDEX = 0xFFFFFFFFu;

void loadDrawMeta(uint drawId,
                  out uint jointIdx,
                  out uint morphDeltaIdx,
                  out uint morphWeightIdx,
                  out uint instanceIdx,
                  out uint materialIdx,
                  out uint drawFlags) {
    DrawMeta m = drawMeta.entries[drawId];
    jointIdx = m.jointPaletteIndex;
    morphDeltaIdx = m.morphDeltaIndex;
    morphWeightIdx = m.morphWeightIndex;
    instanceIdx = m.instanceDataIndex;
    materialIdx = m.materialIndex;
    drawFlags = m.drawFlags;
}

void main() {
    uint drawId = uint(gl_DrawID);
    uint jointIdx, morphDeltaIdx, morphWeightIdx, instanceIdx, materialIdx, drawFlags;
    loadDrawMeta(drawId, jointIdx, morphDeltaIdx, morphWeightIdx, instanceIdx, materialIdx, drawFlags);

    // Example branchless-ish usage with sentinel guards
    // if (jointIdx != INVALID_DESCRIPTOR_INDEX) { ... }
    // if (morphDeltaIdx != INVALID_DESCRIPTOR_INDEX) { ... }
    // if (instanceIdx != INVALID_DESCRIPTOR_INDEX) { ... }
}
```

Notes:

- Keep existing pipeline variants during migration; bindless replaces per-draw descriptor binding first.
- Use `nonuniformEXT` at descriptor array index sites where required by compiler/driver.

## 6. Migration Plan with A/B Toggle

Runtime toggle:

- `vk.bindless.enabled` (default `false` initially)
- Effective activation requires toggle `true` + Section 1 feature gate pass.

Step plan:

1. Introduce bindless heap + draw metadata buffer + no-op bindings; keep legacy path active.
2. Static-only migration:
   - static draws write draw metadata, read via bindless path
   - parity check with legacy static path
3. Skinned migration
4. Morph migration
5. Instanced migration
6. Remove per-draw descriptor binds once all variants pass parity
7. Switch submission to unified compacted stream + `vkCmdDrawIndexedIndirectCount`

Parity strategy per step:

- Frame-level A/B run with same camera/scene and deterministic frame count.
- Compare:
  - draw count
  - cull count
  - per-variant submitted count
  - hash of draw metadata stream (CPU-side debug hash)
- Visual parity spot checks for representative assets (static/skinned/morph/instanced).

## 7. Telemetry, Debugging, and Validation

Counters to publish each frame:

- `bindless.heap.joint.used`
- `bindless.heap.joint.capacity`
- `bindless.heap.morphDelta.used`
- `bindless.heap.morphDelta.capacity`
- `bindless.heap.morphWeight.used`
- `bindless.heap.morphWeight.capacity`
- `bindless.heap.instance.used`
- `bindless.heap.instance.capacity`
- `bindless.heap.allocations`
- `bindless.heap.freesQueued`
- `bindless.heap.freesRetired`
- `bindless.heap.staleHandleRejects`
- `bindless.drawMeta.count`
- `bindless.drawMeta.invalidIndexWrites`

RenderDoc capture points (minimum):

- Capture A: after culling compute dispatch, before main pass
- Capture B: main pass first draw packet
- Capture C: first skinned+morph draw
- Capture D: first instanced draw

Expected checks in captures:

- Draw metadata buffer contents match intended indices.
- Descriptor array slot indices resolved for each variant.
- No out-of-bounds descriptor index use.

Stale-handle rejection log format:

```text
[BINDLESS_HEAP] stale_handle type=<JOINT|MORPH_DELTA|MORPH_WEIGHT|INSTANCE> slot=<u32> handleGen=<u32> currentGen=<u32> frame=<u64>
```

Related warning codes:

- `BINDLESS_HEAP_STALE_HANDLE_REJECTED`
- `BINDLESS_HEAP_CAPACITY_EXHAUSTED`
- `BINDLESS_DRAW_META_INVALID_INDEX`

---

Implementation starts only after this document is approved. First implementation scope is Step 1 (heap + metadata plumbing with legacy draw path preserved).
