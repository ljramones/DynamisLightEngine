package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.MainPassInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.MeshDrawCmd;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.PlanarReflectionPassInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.PostCompositeInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.PostCompositeState;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.RenderPassInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.ShadowPassInputs;

final class VulkanRenderCommandRecorderCore {
    private VulkanRenderCommandRecorderCore() {
    }

    static boolean isPlanarReflectionPassRequested(int reflectionsMode, long planarCaptureImage) {
        return VulkanMainPassRecorderCore.isPlanarReflectionPassRequested(reflectionsMode, planarCaptureImage);
    }

    static void recordShadowPasses(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            ShadowPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanShadowPassRecorderCore.recordShadowPasses(stack, commandBuffer, in, meshes, dynamicUniformOffset);
    }

    static void recordPlanarReflectionPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PlanarReflectionPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanMainPassRecorderCore.recordPlanarReflectionPass(stack, commandBuffer, in, meshes, dynamicUniformOffset);
    }

    static void recordMainPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            MainPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanMainPassRecorderCore.recordMainPass(stack, commandBuffer, in, meshes, dynamicUniformOffset);
    }

    static int shadowPassCount(RenderPassInputs in) {
        return VulkanShadowPassRecorderCore.shadowPassCount(in);
    }

    static PostCompositeState executePostCompositePass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PostCompositeInputs in
    ) {
        return VulkanPostCompositePassRecorderCore.executePostCompositePass(stack, commandBuffer, in);
    }
}
