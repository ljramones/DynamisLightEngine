package org.dynamisengine.light.impl.vulkan.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.runtime.PostCinematicPromotionDiagnostics;
import org.dynamisengine.light.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime holder for cinematic post capability envelope/promotion diagnostics.
 */
public final class VulkanPostCinematicRuntimeState {
    private double warnMinActiveRatio = 1.0;
    private int warnMinFrames = 2;
    private int warnCooldownFrames = 120;
    private int promotionReadyMinFrames = 4;
    private int highStreak;
    private int stableStreak;
    private int cooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean promotionReadyLastFrame;
    private int expectedCountLastFrame;
    private int activeCountLastFrame;
    private double activeRatioLastFrame = 1.0;
    private List<String> expectedLastFrame = List.of();
    private List<String> activeLastFrame = List.of();

    public void reset() {
        highStreak = 0;
        stableStreak = 0;
        cooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
        expectedCountLastFrame = 0;
        activeCountLastFrame = 0;
        activeRatioLastFrame = 1.0;
        expectedLastFrame = List.of();
        activeLastFrame = List.of();
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        warnMinActiveRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.post.cinematicWarnMinActiveRatio", warnMinActiveRatio, 0.0, 2.0);
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.post.cinematicWarnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.post.cinematicWarnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.post.cinematicPromotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
    }

    public void emitFrameWarnings(
            Map<String, String> backendOptions,
            List<String> activeCapabilities,
            List<EngineWarning> warnings
    ) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        List<String> expected = new ArrayList<>();
        addExpected(expected, safe, "vulkan.post.depthOfField", "vulkan.post.depth_of_field");
        addExpected(expected, safe, "vulkan.post.motionBlur", "vulkan.post.motion_blur");
        addExpected(expected, safe, "vulkan.post.chromaticAberration", "vulkan.post.chromatic_aberration");
        addExpected(expected, safe, "vulkan.post.filmGrain", "vulkan.post.film_grain");
        addExpected(expected, safe, "vulkan.post.vignette", "vulkan.post.vignette");
        addExpected(expected, safe, "vulkan.post.colorGrading", "vulkan.post.color_grading");
        addExpected(expected, safe, "vulkan.post.cloudShadows", "vulkan.post.cloud_shadows");
        addExpected(expected, safe, "vulkan.post.screenSpaceBentNormals", "vulkan.post.screen_space_bent_normals");
        addExpected(expected, safe, "vulkan.post.lensFlare", "vulkan.post.lens_flare");
        addExpected(expected, safe, "vulkan.post.panini", "vulkan.post.panini");
        addExpected(expected, safe, "vulkan.post.lensDistortion", "vulkan.post.lens_distortion");

        List<String> safeActive = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        int activeCount = 0;
        List<String> activeMatched = new ArrayList<>();
        for (String capability : expected) {
            if (safeActive.contains(capability)) {
                activeCount += 1;
                activeMatched.add(capability);
            }
        }
        int expectedCount = expected.size();
        double ratio = expectedCount == 0 ? 1.0 : (double) activeCount / (double) expectedCount;
        boolean risk = expectedCount > 0 && ratio < warnMinActiveRatio;
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

        expectedCountLastFrame = expectedCount;
        activeCountLastFrame = activeCount;
        activeRatioLastFrame = ratio;
        expectedLastFrame = List.copyOf(expected);
        activeLastFrame = List.copyOf(activeMatched);
        envelopeBreachedLastFrame = risk && highStreak >= warnMinFrames;
        promotionReadyLastFrame = !risk && stableStreak >= promotionReadyMinFrames;

        warnings.add(new EngineWarning(
                "POST_CINEMATIC_POLICY_ACTIVE",
                "Post cinematic policy active (expectedCount=" + expectedCount
                        + ", activeCount=" + activeCount
                        + ", ratio=" + ratio
                        + ", warnMinActiveRatio=" + warnMinActiveRatio + ")"
        ));
        warnings.add(new EngineWarning(
                "POST_CINEMATIC_ENVELOPE",
                "Post cinematic envelope (risk=" + risk
                        + ", expected=[" + String.join(", ", expected) + "]"
                        + ", active=[" + String.join(", ", activeMatched) + "]"
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && cooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "POST_CINEMATIC_ENVELOPE_BREACH",
                    "Post cinematic envelope breach (expected=" + expectedCount
                            + ", active=" + activeCount
                            + ", ratio=" + ratio + ")"
            ));
            cooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "POST_CINEMATIC_PROMOTION_READY",
                    "Post cinematic promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public PostCinematicPromotionDiagnostics diagnostics() {
        return new PostCinematicPromotionDiagnostics(
                true,
                expectedCountLastFrame,
                activeCountLastFrame,
                activeRatioLastFrame,
                warnMinActiveRatio,
                envelopeBreachedLastFrame,
                promotionReadyLastFrame,
                stableStreak,
                highStreak,
                cooldownRemaining,
                warnMinFrames,
                warnCooldownFrames,
                promotionReadyMinFrames,
                expectedLastFrame,
                activeLastFrame
        );
    }

    private static void addExpected(List<String> out, Map<String, String> options, String key, String capability) {
        if (Boolean.parseBoolean(options.getOrDefault(key, "false"))) {
            out.add(capability);
        }
    }
}
