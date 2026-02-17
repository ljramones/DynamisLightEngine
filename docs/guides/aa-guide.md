# Anti-Aliasing Guide

Last updated: February 17, 2026

## 1. What AA Supports
DynamicLightEngine supports:
- `taa`
- `tuua`
- `tsr`
- `msaa_selective`
- `hybrid_tuua_msaa`
- profile hooks: `dlaa`, `fxaa_low`

You can control AA through:
- backend options (`opengl.*`, `vulkan.*`)
- scene-level `PostProcessDesc.antiAliasing` (`AntiAliasingDesc`)
- compare-harness env/system properties

## 2. Quick Start

### Run with Vulkan + default AA behavior
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

### Force a mode through compare harness
```bash
mvn -pl engine-host-sample -am test \
  -Ddle.compare.tests=true \
  -Ddle.compare.outputDir=artifacts/compare \
  -Ddle.compare.aaPreset=quality \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

Use scene-level AA configuration through `AntiAliasingDesc` on `PostProcessDesc`.

```java
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.PostProcessDesc;

AntiAliasingDesc aa = new AntiAliasingDesc(
    "tsr",   // mode
    true,    // enabled
    0.90f,   // blend
    0.82f,   // clipScale
    true,    // lumaClipEnabled
    0.12f,   // sharpenStrength
    0.64f,   // renderScale
    5        // debugView (composite overlay)
);

PostProcessDesc post = new PostProcessDesc(
    true,   // enabled
    true,   // tonemapEnabled
    1.10f,  // exposure
    2.2f,   // gamma
    true,   // bloomEnabled
    0.9f,   // bloomThreshold
    0.8f,   // bloomStrength
    true,   // ssaoEnabled
    0.6f,   // ssaoStrength
    1.1f,   // ssaoRadius
    0.02f,  // ssaoBias
    1.2f,   // ssaoPower
    true,   // smaaEnabled
    0.7f,   // smaaStrength
    true,   // taaEnabled
    0.75f,  // taaBlend
    true,   // taaLumaClipEnabled (legacy field still honored)
    aa      // scene-level AA descriptor
);
```

Attach `post` to your `SceneDescriptor`.

## 2.2 Programmatic Backend Overrides (EngineConfig)

Use backend options for global/runtime defaults:

```java
Map<String, String> backendOptions = Map.of(
    "vulkan.aaPreset", "quality",
    "vulkan.aaMode", "tsr",
    "vulkan.tsrHistoryWeight", "0.90",
    "vulkan.tsrResponsiveMask", "0.70",
    "vulkan.tsrNeighborhoodClamp", "0.84",
    "vulkan.tsrReprojectionConfidence", "0.88",
    "vulkan.tsrSharpen", "0.12",
    "vulkan.tsrAntiRinging", "0.85",
    "vulkan.tsrRenderScale", "0.64",
    "vulkan.taaDebugView", "5"
);
```

Rule of thumb:
- Use scene-level `AntiAliasingDesc` for per-scene/profile tuning.
- Use backend options for app-level defaults and global experiments.

## 3. Mode Guidance

### `taa`
- Default temporal AA baseline.
- Good all-around starting point.

### `tuua`
- Temporal upsampling focus (balanced quality/perf).
- Use when you want lower render scale with stable history.

### `tsr`
- Highest temporal reconstruction path.
- Best for hard scenes (foliage/specular/thin-geo/disocclusion), with more tuning knobs.

### `msaa_selective`
- Extra edge stability for selective geometry cases.
- Useful where temporal instability is dominated by edge crawl.

### `hybrid_tuua_msaa`
- Compromise mode: temporal upsampling + selective edge help.
- Good when `tuua` alone still shows edge shimmer.

### `dlaa` (profile hook)
- Native-res high-quality temporal profile.

### `fxaa_low` (profile hook)
- Low-cost fallback quality mode.

## 4. Runtime Tuning Knobs

Backend options (per backend):
- `<backend>.aaPreset=performance|balanced|quality|stability`
- `<backend>.aaMode=taa|tuua|tsr|msaa_selective|hybrid_tuua_msaa|dlaa|fxaa_low`
- `<backend>.tsrHistoryWeight`
- `<backend>.tsrResponsiveMask`
- `<backend>.tsrNeighborhoodClamp`
- `<backend>.tsrReprojectionConfidence`
- `<backend>.tsrSharpen`
- `<backend>.tsrAntiRinging`
- `<backend>.tsrRenderScale`
- `<backend>.tuuaRenderScale`

Scene-level AA DTO (`AntiAliasingDesc`):
- `mode`
- `enabled`
- `blend`
- `clipScale`
- `lumaClipEnabled`
- `sharpenStrength`
- `renderScale`
- `debugView`

Validation ranges (enforced by scene validation):
- `blend`: `[0.0, 0.95]`
- `clipScale`: `[0.5, 1.6]`
- `sharpenStrength`: `[0.0, 0.35]`
- `renderScale`: `[0.5, 1.0]`
- `debugView`: `[0, 5]`

## 5. Debug Views

Set `dle.taa.debugView`:
- `0` off
- `1` reactive
- `2` disocclusion
- `3` historyWeight
- `4` velocity
- `5` composite overlay (4-way tiled)

Example:
```bash
mvn -f engine-host-sample/pom.xml exec:java \
  -Dexec.args="vulkan --interactive --taa-debug=overlay"
```

## 6. Upscaler Hook/Bridge Use

Hook selection:
- `dle.compare.upscaler.mode=none|fsr|xess|dlss`
- `dle.compare.upscaler.quality=performance|balanced|quality|ultra_quality`

Native bridge options:
- `<backend>.upscaler.nativeEnabled=true|false`
- `<backend>.upscaler.bridgeClass=com.example.MyUpscalerBridge`
- `<backend>.upscaler.bridgeLibrary=/abs/path/libvendor_bridge.dylib`
  - built-in bridge: `org.dynamislight.impl.common.upscale.VendorSdkUpscalerBridge`
- Optional vendor SDK readiness keys (mode-gated for `fsr|xess|dlss`):
  - `<backend>.upscaler.vendor.fsr.library=/abs/path/libfsr_sdk.dylib` (or shared-library name)
  - `<backend>.upscaler.vendor.xess.library=/abs/path/libxess_sdk.dylib` (or shared-library name)
  - `<backend>.upscaler.vendor.dlss.library=/abs/path/libdlss_sdk.dylib` (or shared-library name)
  - global fallbacks also supported: `dle.upscaler.vendor.<vendor>.library`
- Optional per-vendor SDK provider classes (for real SDK bindings through bridge):
  - `<backend>.upscaler.vendor.fsr.providerClass=com.example.FsrProvider`
  - `<backend>.upscaler.vendor.xess.providerClass=com.example.XessProvider`
  - `<backend>.upscaler.vendor.dlss.providerClass=com.example.DlssProvider`
  - global fallback: `dle.upscaler.vendor.<vendor>.providerClass`

## 7. Recommended Presets

Quality-first:
- `aaMode=tsr`
- `aaPreset=quality`
- `tsrRenderScale=0.60..0.67`

Balanced:
- `aaMode=tuua`
- `aaPreset=balanced`
- `tuuaRenderScale=0.70..0.80`

Stability-sensitive foliage/specular:
- `aaMode=tsr`
- `aaPreset=stability`
- higher responsive mask, anti-ringing enabled

Low-cost fallback:
- `aaMode=fxaa_low`
- `aaPreset=performance`

## 8. Validation Commands

Fast parity validation:
```bash
mvn -pl engine-host-sample -am test \
  -Ddle.compare.tests=true \
  -Ddle.compare.opengl.mockContext=true \
  -Ddle.compare.vulkan.mockContext=true \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Real Vulkan run:
```bash
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

Motion long-run:
```bash
./scripts/aa_rebaseline_real_mac.sh longrun-motion
```

Vendor matrix:
```bash
./scripts/aa_rebaseline_real_mac.sh upscaler-matrix
```
