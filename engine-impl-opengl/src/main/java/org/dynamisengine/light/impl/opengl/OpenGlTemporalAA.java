package org.dynamisengine.light.impl.opengl;

import static org.dynamisengine.light.impl.opengl.GlMathUtil.*;

/**
 * Encapsulates TAA (Temporal Anti-Aliasing) jitter, motion-vector, and
 * telemetry state that was previously inlined in {@link OpenGlContext}.
 *
 * <p>This is a package-private helper — all public-facing TAA configuration
 * remains on {@code OpenGlContext} and is forwarded here as method parameters.
 */
final class OpenGlTemporalAA {

    // ── Jitter state ────────────────────────────────────────────────────
    private int taaJitterFrameIndex;
    private float taaJitterNdcX;
    private float taaJitterNdcY;
    private float taaPrevJitterNdcX;
    private float taaPrevJitterNdcY;

    // ── Previous view-proj state ────────────────────────────────────────
    private float[] taaPrevViewProj = identityMatrix();
    private boolean taaPrevViewProjValid;

    // ── Motion vector state ─────────────────────────────────────────────
    private float taaMotionUvX;
    private float taaMotionUvY;

    // ── Telemetry state ─────────────────────────────────────────────────
    private double taaHistoryRejectRate;
    private double taaConfidenceMean = 1.0;
    private long taaConfidenceDropEvents;

    // ── Jitter ──────────────────────────────────────────────────────────

    /**
     * Advances the Halton jitter sequence and returns the jittered projection
     * matrix.  When TAA is disabled the jitter is cleared and
     * {@code projBaseMatrix} is returned unmodified.
     *
     * @param taaEnabled       whether TAA is currently active
     * @param sceneRenderWidth current render-target width in pixels
     * @param sceneRenderHeight current render-target height in pixels
     * @param projBaseMatrix   the un-jittered base projection matrix (16 floats)
     * @param viewMatrix       the current view matrix (16 floats), used for motion
     * @return the (possibly jittered) projection matrix to use this frame
     */
    float[] updateTemporalJitterState(
            boolean taaEnabled,
            int sceneRenderWidth,
            int sceneRenderHeight,
            float[] projBaseMatrix,
            float[] viewMatrix) {

        taaPrevJitterNdcX = taaJitterNdcX;
        taaPrevJitterNdcY = taaJitterNdcY;

        if (!taaEnabled) {
            if (taaJitterNdcX != 0f || taaJitterNdcY != 0f) {
                taaJitterNdcX = 0f;
                taaJitterNdcY = 0f;
                return projBaseMatrix.clone();
            }
            return projBaseMatrix.clone();
        }

        int frame = ++taaJitterFrameIndex;
        float widthPx = Math.max(1, sceneRenderWidth);
        float heightPx = Math.max(1, sceneRenderHeight);
        taaJitterNdcX = (float) (((halton(frame, 2) - 0.5) * 2.0) / widthPx);
        taaJitterNdcY = (float) (((halton(frame, 3) - 0.5) * 2.0) / heightPx);
        float[] projMatrix = applyProjectionJitter(projBaseMatrix, taaJitterNdcX, taaJitterNdcY);
        updateTemporalMotionVector(taaEnabled, projMatrix, viewMatrix);
        return projMatrix;
    }

    void resetTemporalJitterState() {
        taaJitterFrameIndex = 0;
        taaJitterNdcX = 0f;
        taaJitterNdcY = 0f;
        taaPrevJitterNdcX = 0f;
        taaPrevJitterNdcY = 0f;
    }

    // ── Motion vectors ──────────────────────────────────────────────────

    void resetTemporalMotionState() {
        taaMotionUvX = 0f;
        taaMotionUvY = 0f;
        taaPrevViewProjValid = false;
    }

    private void updateTemporalMotionVector(
            boolean taaEnabled,
            float[] projMatrix,
            float[] viewMatrix) {

        if (!taaEnabled) {
            taaMotionUvX = 0f;
            taaMotionUvY = 0f;
            return;
        }
        float[] currentViewProj = mul(projMatrix, viewMatrix);
        if (!taaPrevViewProjValid) {
            taaMotionUvX = 0f;
            taaMotionUvY = 0f;
            return;
        }
        float[] origin = new float[]{0f, 0f, 0f, 1f};
        float[] prevClip = mulVec4(taaPrevViewProj, origin);
        float[] currClip = mulVec4(currentViewProj, origin);
        float prevW = Math.abs(prevClip[3]) > 1e-6f ? prevClip[3] : 1f;
        float currW = Math.abs(currClip[3]) > 1e-6f ? currClip[3] : 1f;
        float prevNdcX = prevClip[0] / prevW;
        float prevNdcY = prevClip[1] / prevW;
        float currNdcX = currClip[0] / currW;
        float currNdcY = currClip[1] / currW;
        taaMotionUvX = (prevNdcX - currNdcX) * 0.5f;
        taaMotionUvY = (prevNdcY - currNdcY) * 0.5f;
    }

    // ── History camera state ────────────────────────────────────────────

    void updateTemporalHistoryCameraState(
            boolean taaEnabled,
            float[] projMatrix,
            float[] viewMatrix) {

        if (!taaEnabled) {
            return;
        }
        taaPrevViewProj = mul(projMatrix, viewMatrix);
        taaPrevViewProjValid = true;
    }

    /**
     * Invalidates the previous view-proj so the next frame treats motion as
     * zero.  Called when the projection matrix is replaced externally.
     */
    void invalidatePrevViewProj() {
        taaPrevViewProjValid = false;
    }

    boolean isPrevViewProjValid() {
        return taaPrevViewProjValid;
    }

    float[] prevViewProj() {
        return taaPrevViewProj;
    }

    // ── Telemetry ───────────────────────────────────────────────────────

    void updateAaTelemetry(
            boolean taaEnabled,
            float taaBlend,
            float taaClipScale,
            boolean taaLumaClipEnabled,
            float taaRenderScale) {

        if (!taaEnabled) {
            taaHistoryRejectRate = 0.0;
            taaConfidenceMean = 1.0;
            return;
        }
        double motion = Math.sqrt(
                (taaMotionUvX * taaMotionUvX) + (taaMotionUvY * taaMotionUvY));
        double scalePenalty = clamp01(
                (1.0 - Math.max(0.5, Math.min(1.0, taaRenderScale))) * 1.2);
        double reject = clamp01(
                (1.0 - taaBlend) * 0.50 + motion * (6.0 + scalePenalty * 1.8));
        double targetConfidence = clamp01(
                (1.0 - reject * 0.78) * Math.max(0.3, taaClipScale));
        if (taaLumaClipEnabled) {
            targetConfidence = clamp01(targetConfidence + 0.04);
        }
        if (targetConfidence + 0.08 < taaConfidenceMean) {
            taaConfidenceDropEvents++;
        }
        taaHistoryRejectRate = reject;
        taaConfidenceMean = targetConfidence;
    }

    // ── Accessors ───────────────────────────────────────────────────────

    float taaJitterUvDeltaX() {
        return (taaPrevJitterNdcX - taaJitterNdcX) * 0.5f;
    }

    float taaJitterUvDeltaY() {
        return (taaPrevJitterNdcY - taaJitterNdcY) * 0.5f;
    }

    float taaJitterNdcX() {
        return taaJitterNdcX;
    }

    float taaJitterNdcY() {
        return taaJitterNdcY;
    }

    float motionUvX() {
        return taaMotionUvX;
    }

    float motionUvY() {
        return taaMotionUvY;
    }

    double taaHistoryRejectRate() {
        return taaHistoryRejectRate;
    }

    double taaConfidenceMean() {
        return taaConfidenceMean;
    }

    long taaConfidenceDropEvents() {
        return taaConfidenceDropEvents;
    }

    void resetTelemetry() {
        taaHistoryRejectRate = 0.0;
        taaConfidenceMean = 1.0;
    }
}
