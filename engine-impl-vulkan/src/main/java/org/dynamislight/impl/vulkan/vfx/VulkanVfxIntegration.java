package org.dynamislight.impl.vulkan.vfx;

import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.impl.vulkan.command.VulkanIndirectDrawBuffer;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamisvfx.api.ParticleEmitterDescriptor;
import org.dynamisvfx.api.VfxBudgetStats;
import org.dynamisvfx.api.VfxHandle;
import org.dynamisvfx.api.VfxStats;
import org.dynamisvfx.vulkan.VulkanVfxService;
import org.dynamisvfx.vulkan.descriptor.VulkanVfxDescriptorSetLayout;

import java.util.ArrayList;
import java.util.List;

public final class VulkanVfxIntegration {
    private final VulkanVfxService vfxService;
    private final List<VfxHandle> activeHandles = new ArrayList<>();

    private VulkanVfxIntegration(VulkanVfxService vfxService) {
        this.vfxService = vfxService;
    }

    public static VulkanVfxIntegration create(VulkanContext ctx, VulkanBackendResources backendResources) {
        if (backendResources == null || backendResources.device == null) {
            return new VulkanVfxIntegration(null);
        }
        long deviceHandle = backendResources.device.address();
        VulkanVfxDescriptorSetLayout layout = VulkanVfxDescriptorSetLayout.create(deviceHandle);
        VulkanVfxService service = new VulkanVfxService(deviceHandle, null, layout);
        service.setPhysicsHandoff(new VulkanVfxPhysicsHandoffAdapter());
        return new VulkanVfxIntegration(service);
    }

    public void simulate(
            VulkanContext ctx,
            long commandBuffer,
            long frameIndex,
            float deltaTime,
            float[] cameraView,
            float[] cameraProjection,
            float[] frustumPlanes
    ) {
        if (vfxService == null) {
            return;
        }
        VulkanVfxFrameContextAdapter frameCtx = new VulkanVfxFrameContextAdapter(
                commandBuffer,
                frameIndex,
                cameraView,
                cameraProjection,
                frustumPlanes
        );
        vfxService.simulate(activeHandles, deltaTime, frameCtx);
    }

    public void recordDraws(VulkanBackendResources backendResources, long frameIndex) {
        if (vfxService == null
                || backendResources == null
                || backendResources.indirectDrawBuffers == null
                || backendResources.indirectDrawBuffers.length == 0) {
            return;
        }
        int slot = (int) (Math.floorMod(frameIndex, backendResources.indirectDrawBuffers.length));
        VulkanIndirectDrawBuffer indirect = backendResources.indirectDrawBuffers[slot];
        if (indirect == null) {
            return;
        }
        VulkanVfxDrawContextAdapter drawCtx = new VulkanVfxDrawContextAdapter(indirect, frameIndex);
        vfxService.recordDraws(activeHandles, drawCtx);
    }

    public VfxHandle spawnEffect(ParticleEmitterDescriptor desc, float[] transform) {
        if (vfxService == null) {
            return null;
        }
        VfxHandle handle = vfxService.spawn(desc, transform);
        if (handle != null) {
            activeHandles.add(handle);
        }
        return handle;
    }

    public void despawnEffect(VfxHandle handle) {
        if (vfxService == null || handle == null) {
            return;
        }
        activeHandles.removeIf(h -> h.id() == handle.id() && h.generation() == handle.generation());
        vfxService.despawn(handle);
    }

    public VfxStats stats() {
        if (vfxService == null) {
            return new VfxStats(0, 0, 0, 0, 0L, new VfxBudgetStats(0, 0, 0, 0, 0, 0, 0));
        }
        return vfxService.getStats();
    }

    public void destroy() {
        if (vfxService != null) {
            vfxService.destroy();
        }
        activeHandles.clear();
    }
}
