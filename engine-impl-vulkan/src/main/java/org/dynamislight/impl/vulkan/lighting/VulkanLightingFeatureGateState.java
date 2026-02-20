package org.dynamislight.impl.vulkan.lighting;

/**
 * Shared per-feature policy/envelope/promotion gate state for advanced lighting capabilities.
 */
final class VulkanLightingFeatureGateState {
    private final String key;
    private final String warningCodePrefix;
    private int mismatchStreak;
    private int stableStreak;
    private int cooldownRemaining;
    private boolean expectedLastFrame;
    private boolean activeLastFrame;
    private boolean breachedLastFrame;
    private boolean promotionReadyLastFrame;

    VulkanLightingFeatureGateState(String key, String warningCodePrefix) {
        this.key = key == null ? "" : key;
        this.warningCodePrefix = warningCodePrefix == null ? "" : warningCodePrefix;
    }

    void reset() {
        mismatchStreak = 0;
        stableStreak = 0;
        cooldownRemaining = 0;
        expectedLastFrame = false;
        activeLastFrame = false;
        breachedLastFrame = false;
        promotionReadyLastFrame = false;
    }

    void update(boolean expected, boolean active, int warnMinFrames, int cooldownFrames, int promotionReadyMinFrames) {
        expectedLastFrame = expected;
        activeLastFrame = active;
        int clampedWarnMin = Math.max(1, warnMinFrames);
        int clampedCooldown = Math.max(0, cooldownFrames);
        int clampedPromotion = Math.max(1, promotionReadyMinFrames);
        if (expected && !active) {
            mismatchStreak++;
            stableStreak = 0;
        } else if (expected) {
            mismatchStreak = 0;
            stableStreak++;
        } else {
            mismatchStreak = 0;
            stableStreak = 0;
        }
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }
        boolean emitBreach = expected && !active && mismatchStreak >= clampedWarnMin && cooldownRemaining <= 0;
        breachedLastFrame = emitBreach;
        if (emitBreach) {
            cooldownRemaining = clampedCooldown;
        }
        promotionReadyLastFrame = expected && active && stableStreak >= clampedPromotion;
    }

    String key() {
        return key;
    }

    String policyCode() {
        return "LIGHTING_" + warningCodePrefix + "_POLICY";
    }

    String breachCode() {
        return "LIGHTING_" + warningCodePrefix + "_ENVELOPE_BREACH";
    }

    String promotionCode() {
        return "LIGHTING_" + warningCodePrefix + "_PROMOTION_READY";
    }

    int mismatchStreak() {
        return mismatchStreak;
    }

    int stableStreak() {
        return stableStreak;
    }

    int cooldownRemaining() {
        return cooldownRemaining;
    }

    boolean expectedLastFrame() {
        return expectedLastFrame;
    }

    boolean activeLastFrame() {
        return activeLastFrame;
    }

    boolean breachedLastFrame() {
        return breachedLastFrame;
    }

    boolean promotionReadyLastFrame() {
        return promotionReadyLastFrame;
    }
}
