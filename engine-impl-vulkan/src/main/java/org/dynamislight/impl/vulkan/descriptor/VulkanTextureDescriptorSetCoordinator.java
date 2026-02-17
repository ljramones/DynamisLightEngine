package org.dynamislight.impl.vulkan.descriptor;

import java.util.List;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;

public final class VulkanTextureDescriptorSetCoordinator {
    private VulkanTextureDescriptorSetCoordinator() {
    }

    public static Result createOrReuse(Inputs inputs) throws EngineException {
        long capBypassCountIncrement = 0;
        VulkanDescriptorRingPolicy.Decision decision = VulkanDescriptorRingPolicy.decide(
                inputs.descriptorRingSetCapacity(),
                inputs.gpuMeshes().size(),
                inputs.descriptorRingMaxSetCapacity()
        );
        if (decision.capBypass()) {
            capBypassCountIncrement = 1L;
        }

        VulkanTextureDescriptorPoolManager.State state = VulkanTextureDescriptorPoolManager.createOrReuseAndWrite(
                inputs.device(),
                inputs.stack(),
                inputs.gpuMeshes(),
                inputs.textureDescriptorSetLayout(),
                inputs.textureDescriptorPool(),
                inputs.descriptorRingSetCapacity(),
                inputs.descriptorRingPeakSetCapacity(),
                inputs.descriptorRingPeakWasteSetCount(),
                inputs.descriptorPoolBuildCount(),
                inputs.descriptorPoolRebuildCount(),
                inputs.descriptorRingGrowthRebuildCount(),
                inputs.descriptorRingSteadyRebuildCount(),
                inputs.descriptorRingPoolReuseCount(),
                inputs.descriptorRingPoolResetFailureCount(),
                decision.targetCapacity(),
                inputs.shadowDepthImageView(),
                inputs.shadowSampler(),
                inputs.shadowMomentImageView(),
                inputs.shadowMomentSampler(),
                inputs.iblIrradianceTexture(),
                inputs.iblRadianceTexture(),
                inputs.iblBrdfLutTexture()
        );
        return new Result(
                state.textureDescriptorPool(),
                state.descriptorPoolBuildCount(),
                state.descriptorPoolRebuildCount(),
                state.descriptorRingGrowthRebuildCount(),
                state.descriptorRingSteadyRebuildCount(),
                state.descriptorRingPoolReuseCount(),
                state.descriptorRingPoolResetFailureCount(),
                state.descriptorRingSetCapacity(),
                state.descriptorRingPeakSetCapacity(),
                state.descriptorRingActiveSetCount(),
                state.descriptorRingWasteSetCount(),
                state.descriptorRingPeakWasteSetCount(),
                capBypassCountIncrement
        );
    }

    public record Inputs(
            VkDevice device,
            MemoryStack stack,
            List<VulkanGpuMesh> gpuMeshes,
            long textureDescriptorSetLayout,
            long textureDescriptorPool,
            int descriptorRingSetCapacity,
            int descriptorRingPeakSetCapacity,
            int descriptorRingPeakWasteSetCount,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount,
            long descriptorRingGrowthRebuildCount,
            long descriptorRingSteadyRebuildCount,
            long descriptorRingPoolReuseCount,
            long descriptorRingPoolResetFailureCount,
            int descriptorRingMaxSetCapacity,
            long shadowDepthImageView,
            long shadowSampler,
            long shadowMomentImageView,
            long shadowMomentSampler,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture
    ) {
    }

    public record Result(
            long textureDescriptorPool,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount,
            long descriptorRingGrowthRebuildCount,
            long descriptorRingSteadyRebuildCount,
            long descriptorRingPoolReuseCount,
            long descriptorRingPoolResetFailureCount,
            int descriptorRingSetCapacity,
            int descriptorRingPeakSetCapacity,
            int descriptorRingActiveSetCount,
            int descriptorRingWasteSetCount,
            int descriptorRingPeakWasteSetCount,
            long descriptorRingCapBypassCountIncrement
    ) {
    }
}
