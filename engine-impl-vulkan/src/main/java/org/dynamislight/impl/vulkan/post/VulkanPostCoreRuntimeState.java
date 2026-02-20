package org.dynamislight.impl.vulkan.post;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.PostCorePromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.model.FogRenderConfig;
import org.dynamislight.impl.vulkan.runtime.model.PostProcessRenderConfig;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime holder for core post stack policy/envelope/promotion diagnostics.
 */
public final class VulkanPostCoreRuntimeState {
    private int warnMinFrames = 3;
    private int warnCooldownFrames = 120;
    private int promotionReadyMinFrames = 6;
    private float tonemapWarnExposureMin = 0.25f;
    private float tonemapWarnExposureMax = 2.5f;
    private float tonemapWarnGammaMin = 1.8f;
    private float tonemapWarnGammaMax = 2.6f;
    private float bloomWarnThresholdMax = 2.2f;
    private float bloomWarnStrengthMax = 1.4f;
    private float ssaoWarnRadiusMax = 2.5f;
    private float ssaoWarnBiasMax = 0.12f;
    private float ssaoWarnPowerMin = 0.7f;
    private float ssaoWarnPowerMax = 3.0f;
    private float sharpenWarnMax = 0.25f;
    private float volumetricFogWarnDensityMax = 0.08f;
    private int highStreak;
    private int stableStreak;
    private int cooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean promotionReadyLastFrame;
    private boolean tonemapEnabledLastFrame;
    private float exposureLastFrame = 1.0f;
    private float gammaLastFrame = 2.2f;
    private boolean bloomEnabledLastFrame;
    private float bloomThresholdLastFrame = 1.0f;
    private float bloomStrengthLastFrame;
    private boolean ssaoEnabledLastFrame;
    private float ssaoRadiusLastFrame = 1.0f;
    private float ssaoBiasLastFrame;
    private float ssaoPowerLastFrame = 1.0f;
    private boolean sharpeningEnabledLastFrame;
    private float sharpenStrengthLastFrame;
    private boolean volumetricFogEnabledLastFrame;
    private float fogDensityLastFrame;

    public void reset() {
        highStreak = 0;
        stableStreak = 0;
        cooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
        tonemapEnabledLastFrame = false;
        exposureLastFrame = 1.0f;
        gammaLastFrame = 2.2f;
        bloomEnabledLastFrame = false;
        bloomThresholdLastFrame = 1.0f;
        bloomStrengthLastFrame = 0.0f;
        ssaoEnabledLastFrame = false;
        ssaoRadiusLastFrame = 1.0f;
        ssaoBiasLastFrame = 0.0f;
        ssaoPowerLastFrame = 1.0f;
        sharpeningEnabledLastFrame = false;
        sharpenStrengthLastFrame = 0.0f;
        volumetricFogEnabledLastFrame = false;
        fogDensityLastFrame = 0.0f;
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.post.coreWarnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.post.coreWarnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.post.corePromotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
        tonemapWarnExposureMin = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.tonemapWarnExposureMin", tonemapWarnExposureMin, 0.0, 10.0);
        tonemapWarnExposureMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.tonemapWarnExposureMax", tonemapWarnExposureMax, 0.0, 10.0);
        tonemapWarnGammaMin = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.tonemapWarnGammaMin", tonemapWarnGammaMin, 0.5, 4.0);
        tonemapWarnGammaMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.tonemapWarnGammaMax", tonemapWarnGammaMax, 0.5, 4.0);
        bloomWarnThresholdMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.bloomWarnThresholdMax", bloomWarnThresholdMax, 0.1, 10.0);
        bloomWarnStrengthMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.bloomWarnStrengthMax", bloomWarnStrengthMax, 0.1, 4.0);
        ssaoWarnRadiusMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.ssaoWarnRadiusMax", ssaoWarnRadiusMax, 0.1, 10.0);
        ssaoWarnBiasMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.ssaoWarnBiasMax", ssaoWarnBiasMax, 0.0, 1.0);
        ssaoWarnPowerMin = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.ssaoWarnPowerMin", ssaoWarnPowerMin, 0.0, 10.0);
        ssaoWarnPowerMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.ssaoWarnPowerMax", ssaoWarnPowerMax, 0.0, 10.0);
        sharpenWarnMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.sharpenWarnMax", sharpenWarnMax, 0.0, 1.0);
        volumetricFogWarnDensityMax = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.volumetricFogWarnDensityMax", volumetricFogWarnDensityMax, 0.0, 1.0);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions == null || !backendOptions.containsKey("vulkan.post.corePromotionReadyMinFrames")) {
            QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
            promotionReadyMinFrames = switch (safeTier) {
                case LOW -> 3;
                case MEDIUM -> 4;
                case HIGH -> 5;
                case ULTRA -> 6;
            };
        }
    }

    public void emitFrameWarnings(
            PostProcessRenderConfig post,
            FogRenderConfig fog,
            boolean sharpeningEnabled,
            boolean volumetricFogEnabled,
            List<EngineWarning> warnings
    ) {
        PostProcessRenderConfig safePost = post == null
                ? new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.0f, false, 0.0f, 1.0f, 0.0f, 1.0f,
                false, 0.0f, false, 0.0f, 1.0f, false, 0.0f, 1.0f, false, 0, 0.0f, 1.0f, 1.0f, 0.8f, 0.35f, 0.0f)
                : post;
        FogRenderConfig safeFog = fog == null ? new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0.0f, 0, false) : fog;

        tonemapEnabledLastFrame = safePost.tonemapEnabled();
        exposureLastFrame = safePost.exposure();
        gammaLastFrame = safePost.gamma();
        bloomEnabledLastFrame = safePost.bloomEnabled();
        bloomThresholdLastFrame = safePost.bloomThreshold();
        bloomStrengthLastFrame = safePost.bloomStrength();
        ssaoEnabledLastFrame = safePost.ssaoEnabled();
        ssaoRadiusLastFrame = safePost.ssaoRadius();
        ssaoBiasLastFrame = safePost.ssaoBias();
        ssaoPowerLastFrame = safePost.ssaoPower();
        sharpeningEnabledLastFrame = sharpeningEnabled;
        sharpenStrengthLastFrame = safePost.taaSharpenStrength();
        volumetricFogEnabledLastFrame = volumetricFogEnabled && safeFog.enabled();
        fogDensityLastFrame = safeFog.density();

        boolean tonemapRisk = tonemapEnabledLastFrame
                && (exposureLastFrame < tonemapWarnExposureMin
                || exposureLastFrame > tonemapWarnExposureMax
                || gammaLastFrame < tonemapWarnGammaMin
                || gammaLastFrame > tonemapWarnGammaMax);
        boolean bloomRisk = bloomEnabledLastFrame
                && (bloomThresholdLastFrame > bloomWarnThresholdMax || bloomStrengthLastFrame > bloomWarnStrengthMax);
        boolean ssaoRisk = ssaoEnabledLastFrame
                && (ssaoRadiusLastFrame > ssaoWarnRadiusMax
                || ssaoBiasLastFrame > ssaoWarnBiasMax
                || ssaoPowerLastFrame < ssaoWarnPowerMin
                || ssaoPowerLastFrame > ssaoWarnPowerMax);
        boolean sharpenRisk = sharpeningEnabledLastFrame && sharpenStrengthLastFrame > sharpenWarnMax;
        boolean volumetricFogRisk = volumetricFogEnabledLastFrame && fogDensityLastFrame > volumetricFogWarnDensityMax;
        boolean risk = tonemapRisk || bloomRisk || ssaoRisk || sharpenRisk || volumetricFogRisk;

        if (risk) {
            highStreak += 1;
            stableStreak = 0;
            if (cooldownRemaining > 0) {
                cooldownRemaining -= 1;
            }
        } else {
            highStreak = 0;
            stableStreak += 1;
            if (cooldownRemaining > 0) {
                cooldownRemaining -= 1;
            }
        }

        envelopeBreachedLastFrame = risk && highStreak >= warnMinFrames;
        promotionReadyLastFrame = !risk && stableStreak >= promotionReadyMinFrames;

        warnings.add(new EngineWarning(
                "POST_CORE_POLICY_ACTIVE",
                "Post core policy active (tonemap=" + tonemapEnabledLastFrame
                        + ", bloom=" + bloomEnabledLastFrame
                        + ", ssao=" + ssaoEnabledLastFrame
                        + ", sharpening=" + sharpeningEnabledLastFrame
                        + ", volumetricFog=" + volumetricFogEnabledLastFrame + ")"
        ));
        warnings.add(new EngineWarning(
                "POST_CORE_ENVELOPE",
                "Post core envelope (risk=" + risk
                        + ", tonemapRisk=" + tonemapRisk
                        + ", bloomRisk=" + bloomRisk
                        + ", ssaoRisk=" + ssaoRisk
                        + ", sharpenRisk=" + sharpenRisk
                        + ", volumetricFogRisk=" + volumetricFogRisk
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && cooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "POST_CORE_ENVELOPE_BREACH",
                    "Post core envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + cooldownRemaining + ")"
            ));
            cooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "POST_CORE_PROMOTION_READY",
                    "Post core promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public PostCorePromotionDiagnostics diagnostics() {
        return new PostCorePromotionDiagnostics(
                true,
                tonemapEnabledLastFrame,
                exposureLastFrame,
                gammaLastFrame,
                bloomEnabledLastFrame,
                bloomThresholdLastFrame,
                bloomStrengthLastFrame,
                ssaoEnabledLastFrame,
                ssaoRadiusLastFrame,
                ssaoBiasLastFrame,
                ssaoPowerLastFrame,
                sharpeningEnabledLastFrame,
                sharpenStrengthLastFrame,
                volumetricFogEnabledLastFrame,
                fogDensityLastFrame,
                envelopeBreachedLastFrame,
                promotionReadyLastFrame,
                stableStreak,
                highStreak,
                cooldownRemaining,
                warnMinFrames,
                warnCooldownFrames,
                promotionReadyMinFrames
        );
    }
}
