package org.dynamisengine.light.impl.vulkan.vfx;

import org.dynamisvfx.api.VfxFrameContext;

import java.util.Arrays;

public final class VulkanVfxFrameContextAdapter implements VfxFrameContext {
    private final long commandBuffer;
    private final long frameIndex;
    private final float[] cameraView;
    private final float[] cameraProjection;
    private final float[] frustumPlanes;

    public VulkanVfxFrameContextAdapter(
            long commandBuffer,
            long frameIndex,
            float[] cameraView,
            float[] cameraProjection,
            float[] frustumPlanes
    ) {
        this.commandBuffer = commandBuffer;
        this.frameIndex = frameIndex;
        this.cameraView = cameraView == null ? new float[16] : Arrays.copyOf(cameraView, cameraView.length);
        this.cameraProjection = cameraProjection == null ? new float[16] : Arrays.copyOf(cameraProjection, cameraProjection.length);
        this.frustumPlanes = frustumPlanes == null ? new float[24] : Arrays.copyOf(frustumPlanes, frustumPlanes.length);
    }

    @Override
    public long commandBuffer() {
        return commandBuffer;
    }

    @Override
    public float[] cameraView() {
        return Arrays.copyOf(cameraView, cameraView.length);
    }

    @Override
    public float[] cameraProjection() {
        return Arrays.copyOf(cameraProjection, cameraProjection.length);
    }

    @Override
    public float[] frustumPlanes() {
        return Arrays.copyOf(frustumPlanes, frustumPlanes.length);
    }

    @Override
    public long frameIndex() {
        return frameIndex;
    }
}
