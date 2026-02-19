package org.dynamislight.impl.vulkan.warning.aa;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.MaterialDesc;

/**
 * Emits AA material-reactive/history-clamp policy warnings and envelope breaches.
 */
public final class VulkanAaTemporalMaterialWarningEmitter {
    private VulkanAaTemporalMaterialWarningEmitter() {
    }

    public static Result emit(Input input) {
        Input safe = input == null ? Input.defaults() : input;
        Analysis analysis = analyze(safe.materials());
        List<EngineWarning> warnings = new ArrayList<>();

        boolean reactiveRisk = safe.temporalPathActive() && analysis.reactiveCoverage() < safe.reactiveCoverageWarnMin();
        boolean historyRisk = safe.temporalPathActive()
                && analysis.historyClampCustomizedRatio() < safe.historyClampCustomizedWarnMin();

        int reactiveHighStreak = reactiveRisk ? safe.previousReactiveHighStreak() + 1 : 0;
        int reactiveCooldownRemaining = safe.previousReactiveCooldownRemaining() <= 0
                ? 0
                : safe.previousReactiveCooldownRemaining() - 1;
        boolean reactiveBreach = false;
        if (reactiveRisk && reactiveHighStreak >= safe.reactiveWarnMinFrames() && reactiveCooldownRemaining <= 0) {
            reactiveBreach = true;
            reactiveCooldownRemaining = safe.reactiveWarnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_REACTIVE_MASK_ENVELOPE_BREACH",
                    "AA reactive mask envelope breach (coverage=" + analysis.reactiveCoverage()
                            + ", warnMin=" + safe.reactiveCoverageWarnMin() + ")"
            ));
        }

        int historyHighStreak = historyRisk ? safe.previousHistoryHighStreak() + 1 : 0;
        int historyCooldownRemaining = safe.previousHistoryCooldownRemaining() <= 0
                ? 0
                : safe.previousHistoryCooldownRemaining() - 1;
        boolean historyBreach = false;
        if (historyRisk && historyHighStreak >= safe.historyWarnMinFrames() && historyCooldownRemaining <= 0) {
            historyBreach = true;
            historyCooldownRemaining = safe.historyWarnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_HISTORY_CLAMP_ENVELOPE_BREACH",
                    "AA history clamp envelope breach (customizedRatio=" + analysis.historyClampCustomizedRatio()
                            + ", warnMin=" + safe.historyClampCustomizedWarnMin() + ")"
            ));
        }

        warnings.add(new EngineWarning(
                "AA_REACTIVE_MASK_POLICY",
                "AA reactive mask policy (temporalActive=" + safe.temporalPathActive()
                        + ", materialCount=" + analysis.materialCount()
                        + ", authoredReactiveCount=" + analysis.reactiveAuthoredCount()
                        + ", coverage=" + analysis.reactiveCoverage()
                        + ", warnMinCoverage=" + safe.reactiveCoverageWarnMin()
                        + ", breached=" + reactiveBreach + ")"
        ));
        warnings.add(new EngineWarning(
                "AA_HISTORY_CLAMP_POLICY",
                "AA history clamp policy (temporalActive=" + safe.temporalPathActive()
                        + ", materialCount=" + analysis.materialCount()
                        + ", customizedCount=" + analysis.historyClampCustomizedCount()
                        + ", customizedRatio=" + analysis.historyClampCustomizedRatio()
                        + ", clampMean=" + analysis.historyClampMean()
                        + ", warnMinCustomizedRatio=" + safe.historyClampCustomizedWarnMin()
                        + ", breached=" + historyBreach + ")"
        ));

        return new Result(
                warnings,
                analysis.materialCount(),
                analysis.reactiveAuthoredCount(),
                analysis.reactiveCoverage(),
                analysis.historyClampCustomizedCount(),
                analysis.historyClampCustomizedRatio(),
                analysis.historyClampMean(),
                reactiveRisk,
                historyRisk,
                reactiveBreach,
                historyBreach,
                reactiveHighStreak,
                reactiveCooldownRemaining,
                historyHighStreak,
                historyCooldownRemaining
        );
    }

    private static Analysis analyze(List<MaterialDesc> materials) {
        if (materials == null || materials.isEmpty()) {
            return new Analysis(0, 0, 0.0, 0, 0.0, 1.0);
        }
        int materialCount = 0;
        int authoredReactiveCount = 0;
        int clampCustomizedCount = 0;
        double clampSum = 0.0;
        for (MaterialDesc material : materials) {
            if (material == null) {
                continue;
            }
            materialCount++;
            float reactiveStrength = clamp01(material.reactiveStrength());
            float reactiveBoost = finiteOrDefault(material.reactiveBoost(), 1.0f);
            float emissiveReactiveBoost = finiteOrDefault(material.emissiveReactiveBoost(), 1.0f);
            boolean authoredReactive = reactiveStrength > 0.001f
                    || material.alphaTested()
                    || material.foliage()
                    || material.reactivePreset() != org.dynamislight.api.scene.ReactivePreset.AUTO
                    || emissiveReactiveBoost > 1.001f
                    || reactiveBoost > 1.001f;
            if (authoredReactive) {
                authoredReactiveCount++;
            }
            float clampValue = clamp01(material.taaHistoryClamp());
            clampSum += clampValue;
            if (clampValue < 0.999f) {
                clampCustomizedCount++;
            }
        }
        if (materialCount <= 0) {
            return new Analysis(0, 0, 0.0, 0, 0.0, 1.0);
        }
        double reactiveCoverage = (double) authoredReactiveCount / (double) materialCount;
        double customizedRatio = (double) clampCustomizedCount / (double) materialCount;
        double clampMean = clampSum / (double) materialCount;
        return new Analysis(materialCount, authoredReactiveCount, reactiveCoverage, clampCustomizedCount, customizedRatio, clampMean);
    }

    public record Input(
            List<MaterialDesc> materials,
            boolean temporalPathActive,
            double reactiveCoverageWarnMin,
            int reactiveWarnMinFrames,
            int reactiveWarnCooldownFrames,
            double historyClampCustomizedWarnMin,
            int historyWarnMinFrames,
            int historyWarnCooldownFrames,
            int previousReactiveHighStreak,
            int previousReactiveCooldownRemaining,
            int previousHistoryHighStreak,
            int previousHistoryCooldownRemaining
    ) {
        public Input {
            materials = materials == null ? List.of() : List.copyOf(materials);
            reactiveCoverageWarnMin = clamp01(reactiveCoverageWarnMin);
            reactiveWarnMinFrames = Math.max(1, reactiveWarnMinFrames);
            reactiveWarnCooldownFrames = Math.max(0, reactiveWarnCooldownFrames);
            historyClampCustomizedWarnMin = clamp01(historyClampCustomizedWarnMin);
            historyWarnMinFrames = Math.max(1, historyWarnMinFrames);
            historyWarnCooldownFrames = Math.max(0, historyWarnCooldownFrames);
            previousReactiveHighStreak = Math.max(0, previousReactiveHighStreak);
            previousReactiveCooldownRemaining = Math.max(0, previousReactiveCooldownRemaining);
            previousHistoryHighStreak = Math.max(0, previousHistoryHighStreak);
            previousHistoryCooldownRemaining = Math.max(0, previousHistoryCooldownRemaining);
        }

        static Input defaults() {
            return new Input(
                    List.of(),
                    false,
                    0.0,
                    3,
                    120,
                    0.0,
                    3,
                    120,
                    0,
                    0,
                    0,
                    0
            );
        }
    }

    public record Result(
            List<EngineWarning> warnings,
            int materialCount,
            int reactiveAuthoredCount,
            double reactiveCoverage,
            int historyClampCustomizedCount,
            double historyClampCustomizedRatio,
            double historyClampMean,
            boolean reactiveRisk,
            boolean historyRisk,
            boolean reactiveBreach,
            boolean historyBreach,
            int nextReactiveHighStreak,
            int nextReactiveCooldownRemaining,
            int nextHistoryHighStreak,
            int nextHistoryCooldownRemaining
    ) {
        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            materialCount = Math.max(0, materialCount);
            reactiveAuthoredCount = Math.max(0, reactiveAuthoredCount);
            reactiveCoverage = clamp01(reactiveCoverage);
            historyClampCustomizedCount = Math.max(0, historyClampCustomizedCount);
            historyClampCustomizedRatio = clamp01(historyClampCustomizedRatio);
            historyClampMean = clamp01(historyClampMean);
            nextReactiveHighStreak = Math.max(0, nextReactiveHighStreak);
            nextReactiveCooldownRemaining = Math.max(0, nextReactiveCooldownRemaining);
            nextHistoryHighStreak = Math.max(0, nextHistoryHighStreak);
            nextHistoryCooldownRemaining = Math.max(0, nextHistoryCooldownRemaining);
        }
    }

    private record Analysis(
            int materialCount,
            int reactiveAuthoredCount,
            double reactiveCoverage,
            int historyClampCustomizedCount,
            double historyClampCustomizedRatio,
            double historyClampMean
    ) {
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static float finiteOrDefault(float value, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return fallback;
        }
        return value;
    }
}
