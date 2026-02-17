# TSR Temporal Upsampling Notes (2026)

This file captures the concrete implementation shape for DynamisLightEngine TSR as a true temporal upsampler (render scale < 1.0 + reconstruction + temporal resolve), plus the exact jitter/reprojection references used by compare harness validation.

## 1) Production Order (Implemented Path)

1. Render at scaled internal resolution (`taaRenderScale` / `tsrRenderScale`) with jittered projection.
2. Produce current-frame inputs:
   - low-res color
   - low-res depth
   - low-res velocity (+ authored reactive in alpha)
3. Reproject history using velocity + jitter delta.
4. Compute reactive/disocclusion mask.
5. Neighborhood clamp + confidence-driven history weight.
6. Resolve to output with anti-ringing + sharpen.
7. Export debug views (`reactive`, `disocclusion`, `historyWeight`, `velocity`).

## 2) TSR Resolve Shader Skeleton

```glsl
// Inputs:
// uSceneColorLowRes, uSceneDepthLowRes, uSceneVelocityLowRes, uHistoryColor, uHistoryVelocity
// uJitterDeltaUv, uMotionUv, uTsrHistoryWeight, uTsrResponsiveMask, uTsrNeighborhoodClamp
// uTsrReprojectionConfidence, uTsrSharpen, uTsrAntiRinging, uUpsampleScale

vec3 sampleCurrentUpsampled(vec2 uv) {
    // Replace with Catmull-Rom / Lanczos for production quality.
    return texture(uSceneColorLowRes, uv).rgb;
}

void main() {
    vec2 uv = vUv;
    vec2 texel = 1.0 / vec2(textureSize(uSceneColorLowRes, 0));

    vec4 velocitySample = texture(uSceneVelocityLowRes, uv);
    vec2 velocityUv = velocitySample.rg * 2.0 - 1.0;
    float materialReactive = velocitySample.a;

    vec3 current = sampleCurrentUpsampled(uv);
    float currentDepth = texture(uSceneDepthLowRes, uv).r;

    vec2 historyUv = clamp(uv + uJitterDeltaUv + uMotionUv + velocityUv * 0.5, vec2(0.0), vec2(1.0));
    vec4 historySample = texture(uHistoryColor, historyUv);
    vec3 historyColor = historySample.rgb;
    float historyConfidence = clamp(historySample.a, 0.0, 1.0);
    float historyDepth = texture(uHistoryVelocity, historyUv).b;

    vec3 n1 = sampleCurrentUpsampled(clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0)));
    vec3 n2 = sampleCurrentUpsampled(clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0)));
    vec3 n3 = sampleCurrentUpsampled(clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0)));
    vec3 n4 = sampleCurrentUpsampled(clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0)));
    vec3 neighMin = min(min(min(current, n1), min(n2, n3)), n4);
    vec3 neighMax = max(max(max(current, n1), max(n2, n3)), n4);

    float lCurr = dot(current, vec3(0.2126, 0.7152, 0.0722));
    float lHist = dot(historyColor, vec3(0.2126, 0.7152, 0.0722));
    float depthDelta = abs(historyDepth - currentDepth);
    float depthReject = smoothstep(0.0012, 0.0120, depthDelta);
    float disocclusionReject = smoothstep(0.0012, 0.0095, depthDelta);

    float upsamplePenalty = clamp((1.0 - clamp(uUpsampleScale, 0.5, 1.0)) * 1.6, 0.0, 0.75);
    float reactive = clamp(
        abs(lCurr - lHist) * 2.4 +
        length(velocityUv) * 1.25 +
        depthReject * 0.95 +
        materialReactive * 1.35 +
        uTsrResponsiveMask * upsamplePenalty,
        0.0, 1.0
    );
    float mask = max(reactive, disocclusionReject);

    float clipExpand = mix(0.06, 0.015, mask) * uTsrNeighborhoodClamp;
    vec3 clampedHistory = clamp(historyColor, neighMin - vec3(clipExpand), neighMax + vec3(clipExpand));

    float confidence = clamp(historyConfidence * (1.0 - mask * 0.65) * uTsrReprojectionConfidence, 0.02, 1.0);
    float historyWeight = clamp(uTsrHistoryWeight * confidence * (1.0 - mask * 0.88), 0.0, 0.95);

    vec3 resolved = mix(current, clampedHistory, historyWeight);

    // Anti-ringing guard.
    vec3 localMean = (current + n1 + n2 + n3 + n4) * 0.2;
    resolved = mix(resolved, clamp(resolved, localMean - vec3(uTsrAntiRinging * 0.1), localMean + vec3(uTsrAntiRinging * 0.1)), 0.5);

    // Final sharpen.
    vec3 blur = (n1 + n2 + n3 + n4) * 0.25;
    resolved = clamp(resolved + (resolved - blur) * uTsrSharpen * (1.0 - mask), vec3(0.0), vec3(1.0));

    outColor = vec4(resolved, clamp(max(confidence * 0.94, 1.0 - mask * 0.86), 0.02, 1.0));
}
```

## 3) Exact Halton Jitter Table (8-sample, bases 2/3)

Reference points (index starts at 1):

| frame | halton2 | halton3 |
|---|---:|---:|
| 1 | 0.5 | 0.3333333333 |
| 2 | 0.25 | 0.6666666667 |
| 3 | 0.75 | 0.1111111111 |
| 4 | 0.125 | 0.4444444444 |
| 5 | 0.625 | 0.7777777778 |
| 6 | 0.375 | 0.2222222222 |
| 7 | 0.875 | 0.5555555556 |
| 8 | 0.0625 | 0.8888888889 |

NDC jitter from table:

```java
float jitterNdcX = (float) (((halton2 - 0.5) * 2.0) / renderWidth);
float jitterNdcY = (float) (((halton3 - 0.5) * 2.0) / renderHeight);
proj[8] += jitterNdcX;
proj[9] += jitterNdcY;
```

## 4) Reprojection Snippet

```glsl
vec2 velocityUv = texture(uVelocity, uv).rg * 2.0 - 1.0;
vec2 historyUv = clamp(
    uv + jitterDeltaUv + cameraMotionUv + velocityUv * 0.5,
    vec2(0.0), vec2(1.0)
);
vec4 history = texture(uHistoryColor, historyUv);
```

## 5) Validation Gates (Rolling Window)

Compare harness uses rolling-window temporal drift metrics per scene/mode:
- shimmer drift
- reject-rate drift
- confidence-mean drift
- confidence-drop drift
- reject-trend-window drift
- confidence-trend-window drift
- confidence-drop-window drift

Window size is configurable with:

```bash
DLE_COMPARE_TEMPORAL_WINDOW=5
```

Recommended range: `5..10` frames for stress scenes.
