package org.dynamislight.impl.vulkan.runtime.mapper;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.impl.vulkan.runtime.model.FogRenderConfig;
import org.dynamislight.impl.vulkan.runtime.model.SmokeRenderConfig;

import java.util.List;

final class VulkanEngineRuntimeAtmosphereMapper {
    private VulkanEngineRuntimeAtmosphereMapper() {
    }

    public static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        if (fogDesc == null || !fogDesc.enabled() || fogDesc.mode() == FogMode.NONE) {
            return new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
        }
        float tierDensityScale = switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.2f;
        };
        int tierSteps = switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case ULTRA -> 0;
        };
        float density = Math.max(0f, fogDesc.density() * tierDensityScale);
        return new FogRenderConfig(
                true,
                fogDesc.color() == null ? 0.5f : fogDesc.color().x(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().y(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().z(),
                density,
                tierSteps,
                qualityTier == QualityTier.LOW
        );
    }

    public static SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        if (emitters == null || emitters.isEmpty()) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }
        int enabledCount = 0;
        float densityAccum = 0f;
        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (SmokeEmitterDesc emitter : emitters) {
            if (!emitter.enabled()) {
                continue;
            }
            enabledCount++;
            densityAccum += Math.max(0f, emitter.density());
            r += emitter.albedo() == null ? 0.6f : emitter.albedo().x();
            g += emitter.albedo() == null ? 0.6f : emitter.albedo().y();
            b += emitter.albedo() == null ? 0.6f : emitter.albedo().z();
        }
        if (enabledCount == 0) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }
        float avgR = r / enabledCount;
        float avgG = g / enabledCount;
        float avgB = b / enabledCount;
        float baseIntensity = Math.min(0.85f, densityAccum / enabledCount);
        float tierScale = switch (qualityTier) {
            case LOW -> 0.45f;
            case MEDIUM -> 0.7f;
            case HIGH -> 0.9f;
            case ULTRA -> 1.0f;
        };
        return new SmokeRenderConfig(
                true,
                avgR,
                avgG,
                avgB,
                Math.min(0.85f, baseIntensity * tierScale),
                qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM
        );
    }
}
