package org.dynamislight.impl.vulkan.state;

import static org.dynamislight.impl.vulkan.math.VulkanMath.mul;

public final class VulkanTemporalAaCoordinator {
    public record TemporalJitterUpdate(
            int taaJitterFrameIndex,
            float[] projMatrix
    ) {
    }

    public record TemporalAaTelemetry(
            double taaHistoryRejectRate,
            double taaConfidenceMean,
            long taaConfidenceDropEvents
    ) {
    }

    public record TemporalHistoryUpdate(
            float[] taaPrevViewProj,
            boolean taaPrevViewProjValid
    ) {
    }

    public static TemporalJitterUpdate updateTemporalJitterState(
            VulkanRenderState renderState,
            int taaJitterFrameIndex,
            int swapchainWidth,
            int swapchainHeight,
            float[] projBaseMatrix,
            float[] projMatrix,
            boolean taaPrevViewProjValid,
            float[] taaPrevViewProj,
            float[] viewMatrix
    ) {
        renderState.taaPrevJitterNdcX = renderState.taaJitterNdcX;
        renderState.taaPrevJitterNdcY = renderState.taaJitterNdcY;
        if (!renderState.taaEnabled) {
            return new TemporalJitterUpdate(taaJitterFrameIndex, projMatrix);
        }

        int frame = taaJitterFrameIndex + 1;
        float scale = Math.max(0.5f, Math.min(1.0f, renderState.taaRenderScale));
        float width = Math.max(1, swapchainWidth * scale);
        float height = Math.max(1, swapchainHeight * scale);
        float jitterX = (float) (((halton(frame, 2) - 0.5) * 2.0) / width);
        float jitterY = (float) (((halton(frame, 3) - 0.5) * 2.0) / height);
        boolean changed = Math.abs(jitterX - renderState.taaJitterNdcX) > 1e-9f || Math.abs(jitterY - renderState.taaJitterNdcY) > 1e-9f;
        renderState.taaJitterNdcX = jitterX;
        renderState.taaJitterNdcY = jitterY;

        float[] updatedProj = projMatrix;
        if (changed) {
            updatedProj = applyProjectionJitter(projBaseMatrix, renderState.taaJitterNdcX, renderState.taaJitterNdcY);
            updateTemporalMotionVector(renderState, updatedProj, viewMatrix, taaPrevViewProjValid, taaPrevViewProj);
        }
        return new TemporalJitterUpdate(frame, updatedProj);
    }

    public static void resetTemporalJitterState(VulkanRenderState renderState) {
        renderState.taaJitterNdcX = 0f;
        renderState.taaJitterNdcY = 0f;
        renderState.taaPrevJitterNdcX = 0f;
        renderState.taaPrevJitterNdcY = 0f;
        renderState.taaMotionUvX = 0f;
        renderState.taaMotionUvY = 0f;
    }

    public static float taaJitterUvDeltaX(VulkanRenderState renderState) {
        return (renderState.taaPrevJitterNdcX - renderState.taaJitterNdcX) * 0.5f;
    }

    public static float taaJitterUvDeltaY(VulkanRenderState renderState) {
        return (renderState.taaPrevJitterNdcY - renderState.taaJitterNdcY) * 0.5f;
    }

    public static TemporalAaTelemetry updateAaTelemetry(
            VulkanRenderState renderState,
            double taaConfidenceMean,
            long taaConfidenceDropEvents
    ) {
        if (!renderState.taaEnabled) {
            return new TemporalAaTelemetry(0.0, 1.0, taaConfidenceDropEvents);
        }
        double motion = Math.sqrt((renderState.taaMotionUvX * renderState.taaMotionUvX)
                + (renderState.taaMotionUvY * renderState.taaMotionUvY));
        double scalePenalty = clamp01((1.0 - Math.max(0.5, Math.min(1.0, renderState.taaRenderScale))) * 1.2);
        double reject = clamp01((1.0 - renderState.taaBlend) * 0.50 + motion * (6.0 + scalePenalty * 1.8));
        double targetConfidence = clamp01((1.0 - reject * 0.78) * Math.max(0.3, renderState.taaClipScale));
        if (renderState.taaLumaClipEnabled) {
            targetConfidence = clamp01(targetConfidence + 0.04);
        }
        long dropEvents = taaConfidenceDropEvents;
        if (targetConfidence + 0.08 < taaConfidenceMean) {
            dropEvents++;
        }
        return new TemporalAaTelemetry(reject, targetConfidence, dropEvents);
    }

    public static TemporalHistoryUpdate updateTemporalHistoryCameraState(
            VulkanRenderState renderState,
            float[] projMatrix,
            float[] viewMatrix,
            float[] taaPrevViewProj,
            boolean taaPrevViewProjValid
    ) {
        if (!renderState.taaEnabled) {
            return new TemporalHistoryUpdate(taaPrevViewProj, taaPrevViewProjValid);
        }
        return new TemporalHistoryUpdate(mul(projMatrix, viewMatrix), true);
    }

    public static float[] applyProjectionJitter(float[] baseProjection, float jitterNdcX, float jitterNdcY) {
        float[] jittered = baseProjection.clone();
        jittered[8] += jitterNdcX;
        jittered[9] += jitterNdcY;
        return jittered;
    }

    private static void updateTemporalMotionVector(
            VulkanRenderState renderState,
            float[] projMatrix,
            float[] viewMatrix,
            boolean taaPrevViewProjValid,
            float[] taaPrevViewProj
    ) {
        if (!renderState.taaEnabled) {
            renderState.taaMotionUvX = 0f;
            renderState.taaMotionUvY = 0f;
            return;
        }
        float[] currentViewProj = mul(projMatrix, viewMatrix);
        if (!taaPrevViewProjValid) {
            renderState.taaMotionUvX = 0f;
            renderState.taaMotionUvY = 0f;
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
        renderState.taaMotionUvX = (prevNdcX - currNdcX) * 0.5f;
        renderState.taaMotionUvY = (prevNdcY - currNdcY) * 0.5f;
    }

    private static float[] mulVec4(float[] m, float[] v) {
        return new float[]{
                m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3],
                m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3],
                m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3],
                m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3]
        };
    }

    private static double halton(int index, int base) {
        double result = 0.0;
        double f = 1.0;
        int i = index;
        while (i > 0) {
            f /= base;
            result += f * (i % base);
            i /= base;
        }
        return result;
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private VulkanTemporalAaCoordinator() {
    }
}
