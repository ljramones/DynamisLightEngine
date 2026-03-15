package org.dynamisengine.light.impl.vulkan.vfx;

import org.dynamisengine.gpu.api.gpu.IndirectCommandBuffer;
import org.dynamisvfx.api.VfxDescriptorBindingWriter;
import org.dynamisvfx.api.VfxDrawContext;
import org.dynamisvfx.api.VfxIndirectCommandSink;

public final class VulkanVfxDrawContextAdapter implements VfxDrawContext {
    private static final VfxDescriptorBindingWriter NOOP_DESCRIPTOR_WRITER = new VfxDescriptorBindingWriter() {
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

    private final long frameIndex;
    private final VfxIndirectCommandSink indirectSink;

    public VulkanVfxDrawContextAdapter(IndirectCommandBuffer indirectBuffer, long frameIndex) {
        this.frameIndex = frameIndex;
        this.indirectSink = new VfxIndirectCommandSink() {
            @Override
            public void writeCommand(int slot, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
                indirectBuffer.writeCommand(slot, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
            }

            @Override
            public long bufferHandle() {
                return indirectBuffer.bufferHandle();
            }

            @Override
            public long countBufferHandle() {
                return indirectBuffer.countBufferHandle();
            }

            @Override
            public int variantOffset(int variantIndex) {
                return indirectBuffer.variantOffset(variantIndex);
            }

            @Override
            public int variantCapacity(int variantIndex) {
                return indirectBuffer.variantCapacity(variantIndex);
            }

            @Override
            public void destroy() {
                indirectBuffer.destroy();
            }
        };
    }

    @Override
    public VfxIndirectCommandSink indirectCommandSink() {
        return indirectSink;
    }

    @Override
    public VfxDescriptorBindingWriter bindlessHeapWriter() {
        return NOOP_DESCRIPTOR_WRITER;
    }

    @Override
    public long frameIndex() {
        return frameIndex;
    }
}
