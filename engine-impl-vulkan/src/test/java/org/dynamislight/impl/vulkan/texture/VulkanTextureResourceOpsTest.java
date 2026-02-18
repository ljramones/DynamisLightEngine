package org.dynamislight.impl.vulkan.texture;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanTexturePixelData;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanTextureResourceOpsTest {
    @Test
    void createTextureArrayFromPixelsRejectsEmptyLayers() {
        EngineException ex = assertThrows(
                EngineException.class,
                () -> VulkanTextureResourceOps.createTextureArrayFromPixels(List.of(), null)
        );
        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    @Test
    void createTextureArrayFromPixelsRejectsMismatchedLayerDimensions() {
        ByteBuffer a = MemoryUtil.memAlloc(4 * 4 * 4);
        ByteBuffer b = MemoryUtil.memAlloc(2 * 4 * 4);
        try {
            VulkanTexturePixelData layerA = new VulkanTexturePixelData(a, 4, 4);
            VulkanTexturePixelData layerB = new VulkanTexturePixelData(b, 2, 4);
            EngineException ex = assertThrows(
                    EngineException.class,
                    () -> VulkanTextureResourceOps.createTextureArrayFromPixels(List.of(layerA, layerB), null)
            );
            assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
        } finally {
            MemoryUtil.memFree(a);
            MemoryUtil.memFree(b);
        }
    }

    @Test
    void createCubeTextureArrayFromPixelsRejectsNonMultipleOfSixFaces() {
        ByteBuffer a = MemoryUtil.memAlloc(4 * 4 * 4);
        try {
            VulkanTexturePixelData layer = new VulkanTexturePixelData(a, 4, 4);
            EngineException ex = assertThrows(
                    EngineException.class,
                    () -> VulkanTextureResourceOps.createCubeTextureArrayFromPixels(List.of(layer), null)
            );
            assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
        } finally {
            MemoryUtil.memFree(a);
        }
    }
}
