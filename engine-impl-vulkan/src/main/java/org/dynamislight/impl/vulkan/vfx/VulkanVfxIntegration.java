package org.dynamislight.impl.vulkan.vfx;

import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamisvfx.api.ParticleEmitterDescriptor;
import org.dynamisvfx.api.VfxBudgetStats;
import org.dynamisvfx.api.VfxHandle;
import org.dynamisvfx.api.VfxStats;
import org.dynamisvfx.vulkan.VulkanVfxService;

import java.util.ArrayList;
import java.util.List;

public final class VulkanVfxIntegration {
    private final VulkanVfxService vfxService;
    private final VulkanVfxIndirectResources indirectResources;
    private final VulkanVfxTextureRegistry textureRegistry;
    private final List<VfxHandle> activeHandles = new ArrayList<>();
    private int lastDrawCount;

    private VulkanVfxIntegration(
            VulkanVfxService vfxService,
            VulkanVfxIndirectResources indirectResources,
            VulkanVfxTextureRegistry textureRegistry
    ) {
        this.vfxService = vfxService;
        this.indirectResources = indirectResources;
        this.textureRegistry = textureRegistry;
    }

    public static VulkanVfxIntegration create(VulkanContext ctx, VulkanBackendResources backendResources)
            throws EngineException {
        if (backendResources == null || backendResources.device == null) {
            return new VulkanVfxIntegration(null, null, null);
        }
        long deviceHandle = backendResources.device.address();
        VulkanVfxService service = VulkanVfxService.createDefault(deviceHandle);
        service.setPhysicsHandoff(new VulkanVfxPhysicsHandoffAdapter());
        VulkanVfxIndirectResources vfxIndirect = VulkanVfxIndirectResources.create(
                backendResources.device,
                backendResources.physicalDevice
        );
        VulkanVfxTextureRegistry textureRegistry = new VulkanVfxTextureRegistry(backendResources.bindlessDescriptorHeap);
        textureRegistry.registerFallback(deviceHandle, null);
        return new VulkanVfxIntegration(service, vfxIndirect, textureRegistry);
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

    public void recordDraws(long frameIndex) {
        if (vfxService == null || indirectResources == null) {
            lastDrawCount = 0;
            return;
        }
        var indirect = indirectResources.indirectBuffer();
        indirect.clear();
        VulkanVfxDrawContextAdapter drawCtx = new VulkanVfxDrawContextAdapter(indirect, frameIndex);
        vfxService.recordDraws(activeHandles, drawCtx);
        lastDrawCount = Math.max(0, indirect.commandCount());
    }

    public long vfxIndirectBufferHandle() {
        if (indirectResources == null) {
            return 0L;
        }
        return indirectResources.indirectBuffer().bufferHandle();
    }

    public int vfxDrawCount() {
        return lastDrawCount;
    }

    public VulkanVfxTextureRegistry textureRegistry() {
        return textureRegistry;
    }

    public boolean hasActiveDecals() {
        return activeHandles.stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .anyMatch(id -> id.toLowerCase(java.util.Locale.ROOT).contains("decal"));
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
        if (indirectResources != null) {
            indirectResources.destroy();
        }
        activeHandles.clear();
        lastDrawCount = 0;
    }
}
