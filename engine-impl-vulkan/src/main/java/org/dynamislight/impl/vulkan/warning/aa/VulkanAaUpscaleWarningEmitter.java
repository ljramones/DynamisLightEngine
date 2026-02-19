package org.dynamislight.impl.vulkan.warning.aa;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerMode;

/**
 * Emits AA upscaling policy/envelope warnings and promotion readiness for TSR/TUUA.
 */
public final class VulkanAaUpscaleWarningEmitter {
    private VulkanAaUpscaleWarningEmitter() {
    }

    public static Result emit(Input input) {
        Input safe = input == null ? Input.defaults() : input;
        boolean upscaleMode = safe.aaMode() == AaMode.TSR || safe.aaMode() == AaMode.TUUA;
        if (!upscaleMode) {
            return Result.inactive();
        }
        String modeId = safe.aaMode().name().toLowerCase(java.util.Locale.ROOT);
        double maxRenderScale = safe.aaMode() == AaMode.TSR ? safe.tsrWarnMaxRenderScale() : safe.tuuaWarnMaxRenderScale();
        boolean risk = !safe.temporalPathActive()
                || safe.renderScale() > maxRenderScale
                || safe.renderScale() < safe.warnMinRenderScale()
                || (safe.upscalerMode() != UpscalerMode.NONE && !safe.nativeUpscalerActive());
        int highStreak = risk ? safe.previousHighStreak() + 1 : 0;
        int cooldownRemaining = safe.previousCooldownRemaining() <= 0 ? 0 : safe.previousCooldownRemaining() - 1;
        boolean breached = false;
        List<EngineWarning> warnings = new ArrayList<>();
        if (risk && highStreak >= safe.warnMinFrames() && cooldownRemaining <= 0) {
            breached = true;
            cooldownRemaining = safe.warnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_UPSCALE_ENVELOPE_BREACH",
                    "AA upscale envelope breach (mode=" + modeId
                            + ", temporalActive=" + safe.temporalPathActive()
                            + ", renderScale=" + safe.renderScale()
                            + ", minRenderScale=" + safe.warnMinRenderScale()
                            + ", maxRenderScale=" + maxRenderScale
                            + ", upscalerMode=" + safe.upscalerMode().name().toLowerCase(java.util.Locale.ROOT)
                            + ", nativeUpscalerActive=" + safe.nativeUpscalerActive() + ")"
            ));
        }
        int stableStreak = risk ? 0 : safe.previousStableStreak() + 1;
        boolean promotionReady = stableStreak >= safe.promotionReadyMinFrames();
        warnings.add(new EngineWarning(
                "AA_UPSCALE_ENVELOPE",
                "AA upscale envelope (mode=" + modeId
                        + ", risk=" + risk
                        + ", temporalActive=" + safe.temporalPathActive()
                        + ", renderScale=" + safe.renderScale()
                        + ", minRenderScale=" + safe.warnMinRenderScale()
                        + ", maxRenderScale=" + maxRenderScale
                        + ", upscalerMode=" + safe.upscalerMode().name().toLowerCase(java.util.Locale.ROOT)
                        + ", nativeUpscalerActive=" + safe.nativeUpscalerActive()
                        + ", stableStreak=" + stableStreak
                        + ", promotionReadyMinFrames=" + safe.promotionReadyMinFrames()
                        + ", promotionReady=" + promotionReady + ")"
        ));
        warnings.add(new EngineWarning(
                "AA_UPSCALE_POLICY_ACTIVE",
                "AA upscale policy active (mode=" + modeId
                        + ", renderScale=" + safe.renderScale()
                        + ", upscalerMode=" + safe.upscalerMode().name().toLowerCase(java.util.Locale.ROOT)
                        + ", nativeUpscalerActive=" + safe.nativeUpscalerActive()
                        + ", nativeProvider=" + safe.nativeUpscalerProvider() + ")"
        ));
        if (promotionReady) {
            warnings.add(new EngineWarning(
                    "AA_UPSCALE_PROMOTION_READY",
                    "AA upscale promotion-ready envelope satisfied (mode=" + modeId + ")"
            ));
        }
        return new Result(
                warnings,
                true,
                modeId,
                safe.temporalPathActive(),
                safe.renderScale(),
                safe.upscalerMode().name().toLowerCase(java.util.Locale.ROOT),
                safe.nativeUpscalerActive(),
                safe.nativeUpscalerProvider(),
                safe.warnMinRenderScale(),
                maxRenderScale,
                safe.promotionReadyMinFrames(),
                stableStreak,
                risk,
                breached,
                promotionReady,
                highStreak,
                cooldownRemaining
        );
    }

    public record Input(
            AaMode aaMode,
            boolean temporalPathActive,
            double renderScale,
            UpscalerMode upscalerMode,
            boolean nativeUpscalerActive,
            String nativeUpscalerProvider,
            double warnMinRenderScale,
            double tsrWarnMaxRenderScale,
            double tuuaWarnMaxRenderScale,
            int warnMinFrames,
            int warnCooldownFrames,
            int promotionReadyMinFrames,
            int previousStableStreak,
            int previousHighStreak,
            int previousCooldownRemaining
    ) {
        public Input {
            aaMode = aaMode == null ? AaMode.TAA : aaMode;
            renderScale = clamp(renderScale, 0.1, 2.0);
            upscalerMode = upscalerMode == null ? UpscalerMode.NONE : upscalerMode;
            nativeUpscalerProvider = nativeUpscalerProvider == null ? "" : nativeUpscalerProvider;
            warnMinRenderScale = clamp(warnMinRenderScale, 0.1, 2.0);
            tsrWarnMaxRenderScale = clamp(tsrWarnMaxRenderScale, 0.1, 2.0);
            tuuaWarnMaxRenderScale = clamp(tuuaWarnMaxRenderScale, 0.1, 2.0);
            warnMinFrames = Math.max(1, warnMinFrames);
            warnCooldownFrames = Math.max(0, warnCooldownFrames);
            promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
            previousStableStreak = Math.max(0, previousStableStreak);
            previousHighStreak = Math.max(0, previousHighStreak);
            previousCooldownRemaining = Math.max(0, previousCooldownRemaining);
        }

        static Input defaults() {
            return new Input(
                    AaMode.TAA,
                    false,
                    1.0,
                    UpscalerMode.NONE,
                    false,
                    "none",
                    0.5,
                    0.95,
                    0.95,
                    3,
                    120,
                    6,
                    0,
                    0,
                    0
            );
        }
    }

    public record Result(
            List<EngineWarning> warnings,
            boolean upscaleModeActive,
            String aaModeId,
            boolean temporalPathActive,
            double renderScale,
            String upscalerModeId,
            boolean nativeUpscalerActive,
            String nativeUpscalerProvider,
            double warnMinRenderScale,
            double warnMaxRenderScale,
            int promotionReadyMinFrames,
            int stableStreak,
            boolean envelopeRiskLastFrame,
            boolean envelopeBreachedLastFrame,
            boolean promotionReadyLastFrame,
            int nextHighStreak,
            int nextCooldownRemaining
    ) {
        static Result inactive() {
            return new Result(
                    List.of(),
                    false,
                    "",
                    false,
                    1.0,
                    "none",
                    false,
                    "none",
                    0.5,
                    1.0,
                    1,
                    0,
                    false,
                    false,
                    false,
                    0,
                    0
            );
        }

        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            aaModeId = aaModeId == null ? "" : aaModeId;
            renderScale = clamp(renderScale, 0.1, 2.0);
            upscalerModeId = upscalerModeId == null ? "none" : upscalerModeId;
            nativeUpscalerProvider = nativeUpscalerProvider == null ? "none" : nativeUpscalerProvider;
            warnMinRenderScale = clamp(warnMinRenderScale, 0.1, 2.0);
            warnMaxRenderScale = clamp(warnMaxRenderScale, 0.1, 2.0);
            promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
            stableStreak = Math.max(0, stableStreak);
            nextHighStreak = Math.max(0, nextHighStreak);
            nextCooldownRemaining = Math.max(0, nextCooldownRemaining);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
