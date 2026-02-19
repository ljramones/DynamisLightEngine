package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanTexturePixelData;
import org.dynamislight.impl.vulkan.texture.VulkanTexturePixelLoader;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

public final class VulkanReflectionProbeTextureCoordinator {
    public record BuildRequest(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long commandPool,
            VkQueue graphicsQueue,
            VulkanTextureResourceOps.FailureFactory vkFailure,
            VulkanGpuTexture fallbackRadianceTexture,
            Map<String, Integer> slots,
            int slotCount,
            boolean probeCubeArrayEnabled
    ) {
    }

    private record ProbeSlotPixels(
            VulkanTexturePixelData layer,
            List<VulkanTexturePixelData> cubeFaces
    ) {
    }

    public static VulkanGpuTexture buildProbeRadianceAtlasTexture(BuildRequest request) throws EngineException {
        if (request.device() == null || request.slotCount() <= 0 || request.slots() == null || request.slots().isEmpty()) {
            return request.fallbackRadianceTexture();
        }
        String[] pathBySlot = new String[request.slotCount()];
        for (Map.Entry<String, Integer> entry : request.slots().entrySet()) {
            int slot = entry.getValue() == null ? -1 : entry.getValue();
            if (slot >= 0 && slot < request.slotCount()) {
                pathBySlot[slot] = entry.getKey();
            }
        }

        List<ProbeSlotPixels> slotPixels = new ArrayList<>(request.slotCount());
        int layerWidth = -1;
        int layerHeight = -1;
        for (int i = 0; i < request.slotCount(); i++) {
            VulkanTexturePixelData pixels = null;
            List<VulkanTexturePixelData> cubeFaces = null;
            String rawPath = pathBySlot[i];
            if (rawPath != null && !rawPath.isBlank()) {
                try {
                    pixels = VulkanTexturePixelLoader.loadTexturePixels(Path.of(rawPath));
                    if (request.probeCubeArrayEnabled()) {
                        cubeFaces = loadProbeCubeFaces(rawPath);
                    }
                } catch (RuntimeException ignored) {
                    pixels = null;
                    cubeFaces = null;
                }
            }
            if (layerWidth < 0) {
                if (cubeFaces != null && !cubeFaces.isEmpty()) {
                    layerWidth = cubeFaces.getFirst().width();
                    layerHeight = cubeFaces.getFirst().height();
                } else if (pixels != null) {
                    layerWidth = pixels.width();
                    layerHeight = pixels.height();
                }
            }
            slotPixels.add(new ProbeSlotPixels(pixels, cubeFaces));
        }
        if (layerWidth <= 0 || layerHeight <= 0) {
            freeProbeSlotPixels(slotPixels);
            return request.fallbackRadianceTexture();
        }

        int layerBytes = layerWidth * layerHeight * 4;
        boolean cubePathReady = request.probeCubeArrayEnabled();
        if (request.probeCubeArrayEnabled()) {
            for (ProbeSlotPixels slot : slotPixels) {
                if (slot.cubeFaces == null || slot.cubeFaces.size() != 6 || !allFacesMatchDimensions(slot.cubeFaces, layerWidth, layerHeight, layerBytes)) {
                    cubePathReady = false;
                    break;
                }
            }
        }
        List<VulkanTexturePixelData> normalizedLayers = new ArrayList<>(request.slotCount());
        for (int i = 0; i < request.slotCount(); i++) {
            ProbeSlotPixels slot = slotPixels.get(i);
            VulkanTexturePixelData layer = slot.layer;
            if (layer != null && layer.width() == layerWidth && layer.height() == layerHeight && layer.data().remaining() == layerBytes) {
                normalizedLayers.add(layer);
            } else {
                ByteBuffer fallback = MemoryUtil.memAlloc(layerBytes);
                for (int p = 0; p < layerBytes / 4; p++) {
                    fallback.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
                }
                fallback.flip();
                normalizedLayers.add(new VulkanTexturePixelData(fallback, layerWidth, layerHeight));
            }
            if (cubePathReady && slot.cubeFaces != null) {
                // Keep face-load path alive for future cube-array ingestion; no-op for current 2D-array upload path.
            }
        }
        try {
            VulkanTextureResourceOps.Context context = new VulkanTextureResourceOps.Context(
                    request.device(),
                    request.physicalDevice(),
                    request.commandPool(),
                    request.graphicsQueue(),
                    request.vkFailure()
            );
            return VulkanTextureResourceOps.createTextureArrayFromPixels(normalizedLayers, context);
        } finally {
            freeProbeSlotPixels(slotPixels);
            freePixelLayers(normalizedLayers);
        }
    }

    public static void destroyOwnedProbeRadianceTexture(VkDevice device, VulkanGpuTexture probeTexture, VulkanGpuTexture sharedRadianceTexture) {
        if (probeTexture == null || sameTexture(probeTexture, sharedRadianceTexture)) {
            return;
        }
        destroyTexture(device, probeTexture);
    }

    public static void destroyTexture(VkDevice device, VulkanGpuTexture texture) {
        if (device == null || texture == null) {
            return;
        }
        if (texture.sampler() != VK_NULL_HANDLE) {
            vkDestroySampler(device, texture.sampler(), null);
        }
        if (texture.view() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, texture.view(), null);
        }
        if (texture.image() != VK_NULL_HANDLE) {
            vkDestroyImage(device, texture.image(), null);
        }
        if (texture.memory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, texture.memory(), null);
        }
    }

    public static boolean sameTexture(VulkanGpuTexture a, VulkanGpuTexture b) {
        if (a == null || b == null) {
            return false;
        }
        return a.image() == b.image() && a.view() == b.view() && a.sampler() == b.sampler();
    }

    private static boolean allFacesMatchDimensions(
            List<VulkanTexturePixelData> faces,
            int width,
            int height,
            int layerBytes
    ) {
        if (faces == null || faces.size() != 6) {
            return false;
        }
        for (VulkanTexturePixelData face : faces) {
            if (face == null || face.width() != width || face.height() != height || face.data().remaining() != layerBytes) {
                return false;
            }
        }
        return true;
    }

    private static List<VulkanTexturePixelData> loadProbeCubeFaces(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path source = Paths.get(rawPath);
        String file = source.getFileName() == null ? null : source.getFileName().toString();
        if (file == null || file.isBlank()) {
            return null;
        }
        int dot = file.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String stem = file.substring(0, dot);
        String ext = file.substring(dot);
        String[] suffixes = new String[]{"px", "nx", "py", "ny", "pz", "nz"};
        List<VulkanTexturePixelData> faces = new ArrayList<>(6);
        for (String suffix : suffixes) {
            Path facePath = source.resolveSibling(stem + "_" + suffix + ext);
            VulkanTexturePixelData face = VulkanTexturePixelLoader.loadTexturePixels(facePath);
            if (face == null) {
                freePixelLayers(faces);
                return null;
            }
            faces.add(face);
        }
        return faces;
    }

    private static void freePixelLayers(List<VulkanTexturePixelData> layers) {
        for (VulkanTexturePixelData layer : layers) {
            if (layer == null) {
                continue;
            }
            MemoryUtil.memFree(layer.data());
        }
    }

    private static void freeProbeSlotPixels(List<ProbeSlotPixels> slots) {
        for (ProbeSlotPixels slot : slots) {
            if (slot == null) {
                continue;
            }
            if (slot.layer != null) {
                MemoryUtil.memFree(slot.layer.data());
            }
            if (slot.cubeFaces != null) {
                freePixelLayers(slot.cubeFaces);
            }
        }
    }

    private VulkanReflectionProbeTextureCoordinator() {
    }
}
