package org.dynamislight.impl.vulkan.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamislight.impl.vulkan.state.VulkanIblState;
import org.dynamislight.impl.vulkan.state.VulkanSceneResourceState;
import org.junit.jupiter.api.Test;

class VulkanSceneMeshCoordinatorTest {
    @Test
    void setSceneMeshesWithoutDeviceOnlyUpdatesPendingScene() throws Exception {
        var backendResources = new VulkanBackendResources(); // device remains null
        var sceneResources = new VulkanSceneResourceState();
        var iblState = new VulkanIblState();
        var descriptorResources = new VulkanDescriptorResourceState();
        var descriptorRingStats = new VulkanDescriptorRingStats();

        var scene = List.of(org.dynamislight.impl.vulkan.model.VulkanSceneMeshData.defaultTriangle());

        var result = VulkanSceneMeshCoordinator.setSceneMeshes(
                new VulkanSceneMeshCoordinator.SetSceneRequest(
                        scene,
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        3,
                        1234L,
                        (path, normalMap) -> null,
                        (path, cache, def, normalMap) -> def,
                        (path, normalMap) -> (normalMap ? "n:" : "a:") + path,
                        (start, end) -> {
                        },
                        (operation, resultCode) -> new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, operation + ":" + resultCode, false)
                )
        );

        assertEquals(1234L, result.estimatedGpuMemoryBytes());
        assertEquals(1, sceneResources.pendingSceneMeshes.size());
        assertEquals(0, sceneResources.sceneFullRebuildCount);
        assertEquals(0, sceneResources.sceneReuseHitCount);
    }

    @Test
    void setSceneMeshesNullInputFallsBackToDefaultTrianglePendingScene() throws Exception {
        var sceneResources = new VulkanSceneResourceState();
        var result = VulkanSceneMeshCoordinator.setSceneMeshes(
                new VulkanSceneMeshCoordinator.SetSceneRequest(
                        null,
                        new VulkanBackendResources(),
                        sceneResources,
                        new VulkanIblState(),
                        new VulkanDescriptorResourceState(),
                        new VulkanDescriptorRingStats(),
                        3,
                        0L,
                        (path, normalMap) -> null,
                        (path, cache, def, normalMap) -> def,
                        (path, normalMap) -> (normalMap ? "n:" : "a:") + path,
                        (start, end) -> {
                        },
                        (operation, resultCode) -> new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, operation + ":" + resultCode, false)
                )
        );

        assertNotNull(result);
        assertEquals(1, sceneResources.pendingSceneMeshes.size());
        assertEquals("default-triangle-0", sceneResources.pendingSceneMeshes.getFirst().meshId());
    }
}
