package org.dynamislight.impl.vulkan.scene;

import java.util.List;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.descriptor.VulkanTextureDescriptorSetCoordinator;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamislight.impl.vulkan.state.VulkanIblState;
import org.dynamislight.impl.vulkan.state.VulkanSceneResourceState;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryStack.stackPush;

public final class VulkanSceneMeshCoordinator {
    private VulkanSceneMeshCoordinator() {
    }

    public static SetSceneResult setSceneMeshes(SetSceneRequest in) throws EngineException {
        VulkanSceneSetPlanner.Plan plan = VulkanSceneSetPlanner.plan(
                in.sceneResources().gpuMeshes,
                in.sceneMeshes(),
                (path, normalMap) -> in.textureCacheKey().key(path, normalMap)
        );
        List<org.dynamislight.impl.vulkan.model.VulkanSceneMeshData> safe = plan.sceneMeshes();
        in.sceneResources().pendingSceneMeshes = safe;
        if (in.backendResources().device == null) {
            return new SetSceneResult(in.estimatedGpuMemoryBytes());
        }
        if (plan.action() == VulkanSceneSetPlanner.Action.REUSE_DYNAMIC_ONLY) {
            in.sceneResources().sceneReuseHitCount++;
            in.descriptorRingStats().descriptorRingReuseHitCount++;
            var dynamicResult = VulkanSceneRuntimeCoordinator.updateDynamicState(in.sceneResources().gpuMeshes, safe);
            if (dynamicResult.reordered()) {
                in.sceneResources().sceneReorderReuseCount++;
            }
            if (dynamicResult.dirtyEnd() >= dynamicResult.dirtyStart()) {
                in.markSceneStateDirty().run(dynamicResult.dirtyStart(), dynamicResult.dirtyEnd());
            }
            return new SetSceneResult(in.estimatedGpuMemoryBytes());
        }
        if (plan.action() == VulkanSceneSetPlanner.Action.REUSE_GEOMETRY_REBIND_TEXTURES) {
            in.sceneResources().sceneReuseHitCount++;
            in.sceneResources().sceneTextureRebindCount++;
            in.descriptorRingStats().descriptorRingReuseHitCount++;
            var rebindResult = VulkanSceneRuntimeCoordinator.rebind(
                    new VulkanSceneRuntimeCoordinator.RebindRequest(
                            in.backendResources().device,
                            safe,
                            in.sceneResources().gpuMeshes,
                            in.iblState().irradianceTexture,
                            in.iblState().radianceTexture,
                            in.iblState().brdfLutTexture,
                            in.iblState().irradiancePath,
                            in.iblState().radiancePath,
                            in.iblState().brdfLutPath,
                            in.createTextureFromPath(),
                            in.resolveOrCreateTexture(),
                            in.textureCacheKey(),
                            () -> refreshTextureDescriptorSets(in),
                            in.markSceneStateDirty()
                    )
            );
            in.iblState().irradianceTexture = rebindResult.iblIrradianceTexture();
            in.iblState().radianceTexture = rebindResult.iblRadianceTexture();
            in.iblState().brdfLutTexture = rebindResult.iblBrdfLutTexture();
            return new SetSceneResult(in.estimatedGpuMemoryBytes());
        }
        in.sceneResources().sceneFullRebuildCount++;
        long estimatedGpuMemoryBytes = uploadSceneMeshes(
                new UploadRequest(
                        in.backendResources(),
                        in.sceneResources(),
                        in.iblState(),
                        in.descriptorResources(),
                        in.descriptorRingStats(),
                        in.framesInFlight(),
                        safe,
                        in.createTextureFromPath(),
                        in.resolveOrCreateTexture(),
                        in.textureCacheKey(),
                        in.vkFailure(),
                        in.estimatedGpuMemoryBytes()
                )
        ).estimatedGpuMemoryBytes();
        in.markSceneStateDirty().run(0, Math.max(0, safe.size() - 1));
        return new SetSceneResult(estimatedGpuMemoryBytes);
    }

    public static UploadResult uploadSceneMeshes(UploadRequest in) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            var result = VulkanSceneRuntimeCoordinator.upload(
                    new VulkanSceneRuntimeCoordinator.UploadRequest(
                            in.sceneResources().meshBufferRebuildCount,
                            () -> destroySceneMeshes(
                                    new DestroyRequest(
                                            in.backendResources(),
                                            in.sceneResources(),
                                            in.iblState(),
                                            in.descriptorResources()
                                    )
                            ),
                            in.backendResources().device,
                            in.backendResources().physicalDevice,
                            in.backendResources().commandPool,
                            in.backendResources().graphicsQueue,
                            stack,
                            in.sceneMeshes(),
                            in.sceneResources().gpuMeshes,
                            in.iblState().irradiancePath,
                            in.iblState().radiancePath,
                            in.iblState().brdfLutPath,
                            in.descriptorResources().uniformFrameSpanBytes,
                            in.framesInFlight(),
                            in.createTextureFromPath(),
                            in.resolveOrCreateTexture(),
                            in.textureCacheKey(),
                            ignoredStack -> createTextureDescriptorSets(
                                    new TextureDescriptorRequest(
                                            in.backendResources(),
                                            in.sceneResources(),
                                            in.iblState(),
                                            in.descriptorResources(),
                                            in.descriptorRingStats(),
                                            ignoredStack
                                    )
                            ),
                            in.vkFailure()
                    )
            );
            in.sceneResources().meshBufferRebuildCount = result.meshBufferRebuildCount();
            in.iblState().irradianceTexture = result.iblIrradianceTexture();
            in.iblState().radianceTexture = result.iblRadianceTexture();
            in.iblState().brdfLutTexture = result.iblBrdfLutTexture();
            return new UploadResult(result.estimatedGpuMemoryBytes());
        }
    }

    public static void destroySceneMeshes(DestroyRequest in) {
        var destroyResult = VulkanSceneRuntimeCoordinator.destroy(
                in.backendResources().device,
                in.sceneResources().gpuMeshes,
                in.iblState().irradianceTexture,
                in.iblState().radianceTexture,
                in.iblState().brdfLutTexture,
                in.descriptorResources().textureDescriptorPool
        );
        in.descriptorResources().textureDescriptorPool = destroyResult.textureDescriptorPool();
        in.iblState().irradianceTexture = null;
        in.iblState().radianceTexture = null;
        in.iblState().brdfLutTexture = null;
    }

    public static void createTextureDescriptorSets(TextureDescriptorRequest in) throws EngineException {
        VulkanTextureDescriptorSetCoordinator.Result state = VulkanSceneTextureCoordinator.createTextureDescriptorSets(
                new VulkanSceneTextureCoordinator.CreateInputs(
                        in.backendResources().device,
                        in.stack(),
                        in.sceneResources().gpuMeshes,
                        in.descriptorResources().textureDescriptorSetLayout,
                        in.descriptorResources().textureDescriptorPool,
                        in.descriptorRingStats().descriptorRingSetCapacity,
                        in.descriptorRingStats().descriptorRingPeakSetCapacity,
                        in.descriptorRingStats().descriptorRingPeakWasteSetCount,
                        in.descriptorRingStats().descriptorPoolBuildCount,
                        in.descriptorRingStats().descriptorPoolRebuildCount,
                        in.descriptorRingStats().descriptorRingGrowthRebuildCount,
                        in.descriptorRingStats().descriptorRingSteadyRebuildCount,
                        in.descriptorRingStats().descriptorRingPoolReuseCount,
                        in.descriptorRingStats().descriptorRingPoolResetFailureCount,
                        in.descriptorRingStats().descriptorRingMaxSetCapacity,
                        in.backendResources().shadowDepthImageView,
                        in.backendResources().shadowSampler,
                        in.iblState().irradianceTexture,
                        in.iblState().radianceTexture,
                        in.iblState().brdfLutTexture
                )
        );
        if (state == null) {
            return;
        }
        in.descriptorResources().textureDescriptorPool = state.textureDescriptorPool();
        in.descriptorRingStats().descriptorPoolBuildCount = state.descriptorPoolBuildCount();
        in.descriptorRingStats().descriptorPoolRebuildCount = state.descriptorPoolRebuildCount();
        in.descriptorRingStats().descriptorRingGrowthRebuildCount = state.descriptorRingGrowthRebuildCount();
        in.descriptorRingStats().descriptorRingSteadyRebuildCount = state.descriptorRingSteadyRebuildCount();
        in.descriptorRingStats().descriptorRingPoolReuseCount = state.descriptorRingPoolReuseCount();
        in.descriptorRingStats().descriptorRingPoolResetFailureCount = state.descriptorRingPoolResetFailureCount();
        in.descriptorRingStats().descriptorRingSetCapacity = state.descriptorRingSetCapacity();
        in.descriptorRingStats().descriptorRingPeakSetCapacity = state.descriptorRingPeakSetCapacity();
        in.descriptorRingStats().descriptorRingActiveSetCount = state.descriptorRingActiveSetCount();
        in.descriptorRingStats().descriptorRingWasteSetCount = state.descriptorRingWasteSetCount();
        in.descriptorRingStats().descriptorRingPeakWasteSetCount = state.descriptorRingPeakWasteSetCount();
        in.descriptorRingStats().descriptorRingCapBypassCount += state.descriptorRingCapBypassCountIncrement();
    }

    private static void refreshTextureDescriptorSets(SetSceneRequest in) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            createTextureDescriptorSets(
                    new TextureDescriptorRequest(
                            in.backendResources(),
                            in.sceneResources(),
                            in.iblState(),
                            in.descriptorResources(),
                            in.descriptorRingStats(),
                            stack
                    )
            );
        }
    }

    public record SetSceneRequest(
            List<org.dynamislight.impl.vulkan.model.VulkanSceneMeshData> sceneMeshes,
            VulkanBackendResources backendResources,
            VulkanSceneResourceState sceneResources,
            VulkanIblState iblState,
            VulkanDescriptorResourceState descriptorResources,
            VulkanDescriptorRingStats descriptorRingStats,
            int framesInFlight,
            long estimatedGpuMemoryBytes,
            VulkanSceneMeshLifecycle.TextureFactory createTextureFromPath,
            VulkanSceneMeshLifecycle.TextureResolver resolveOrCreateTexture,
            VulkanSceneMeshLifecycle.TextureKeyer textureCacheKey,
            VulkanSceneUploadCoordinator.MarkSceneDirtyAction markSceneStateDirty,
            VulkanSceneMeshLifecycle.FailureFactory vkFailure
    ) {
    }

    public record UploadRequest(
            VulkanBackendResources backendResources,
            VulkanSceneResourceState sceneResources,
            VulkanIblState iblState,
            VulkanDescriptorResourceState descriptorResources,
            VulkanDescriptorRingStats descriptorRingStats,
            int framesInFlight,
            List<org.dynamislight.impl.vulkan.model.VulkanSceneMeshData> sceneMeshes,
            VulkanSceneMeshLifecycle.TextureFactory createTextureFromPath,
            VulkanSceneMeshLifecycle.TextureResolver resolveOrCreateTexture,
            VulkanSceneMeshLifecycle.TextureKeyer textureCacheKey,
            VulkanSceneMeshLifecycle.FailureFactory vkFailure,
            long estimatedGpuMemoryBytes
    ) {
    }

    public record DestroyRequest(
            VulkanBackendResources backendResources,
            VulkanSceneResourceState sceneResources,
            VulkanIblState iblState,
            VulkanDescriptorResourceState descriptorResources
    ) {
    }

    public record TextureDescriptorRequest(
            VulkanBackendResources backendResources,
            VulkanSceneResourceState sceneResources,
            VulkanIblState iblState,
            VulkanDescriptorResourceState descriptorResources,
            VulkanDescriptorRingStats descriptorRingStats,
            MemoryStack stack
    ) {
    }

    public record SetSceneResult(long estimatedGpuMemoryBytes) {
    }

    public record UploadResult(long estimatedGpuMemoryBytes) {
    }
}
