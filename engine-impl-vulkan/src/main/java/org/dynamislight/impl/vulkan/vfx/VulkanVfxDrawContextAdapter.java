package org.dynamislight.impl.vulkan.vfx;

import org.dynamisgpu.api.gpu.DescriptorWriter;
import org.dynamisgpu.api.gpu.IndirectCommandBuffer;
import org.dynamisvfx.api.VfxDrawContext;

public final class VulkanVfxDrawContextAdapter implements VfxDrawContext {
    private static final DescriptorWriter NOOP_DESCRIPTOR_WRITER = new DescriptorWriter() {
        @Override
        public void writeStorageBuffer(long descriptorSet, int binding, int arrayElement, long buffer, long offset, long range) {
            // No-op until DLE bindless descriptor writer wiring is required by VFX.
        }

        @Override
        public void writeUniformBuffer(long descriptorSet, int binding, int arrayElement, long buffer, long offset, long range) {
            // No-op until DLE bindless descriptor writer wiring is required by VFX.
        }

        @Override
        public void writeSampledImage(long descriptorSet, int binding, int arrayElement, long imageView, long sampler) {
            // No-op until DLE bindless descriptor writer wiring is required by VFX.
        }
    };

    private final IndirectCommandBuffer indirectBuffer;
    private final long frameIndex;

    public VulkanVfxDrawContextAdapter(IndirectCommandBuffer indirectBuffer, long frameIndex) {
        this.indirectBuffer = indirectBuffer;
        this.frameIndex = frameIndex;
    }

    @Override
    public IndirectCommandBuffer indirectBuffer() {
        return indirectBuffer;
    }

    @Override
    public DescriptorWriter bindlessHeap() {
        return NOOP_DESCRIPTOR_WRITER;
    }

    @Override
    public long frameIndex() {
        return frameIndex;
    }
}
