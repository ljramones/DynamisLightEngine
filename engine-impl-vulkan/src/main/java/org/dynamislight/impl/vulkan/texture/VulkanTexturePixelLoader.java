package org.dynamislight.impl.vulkan.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.dynamislight.impl.common.texture.KtxDecodeUtil;
import org.dynamislight.impl.vulkan.model.VulkanTexturePixelData;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_info;
import static org.lwjgl.stb.STBImage.stbi_is_hdr;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_loadf;
import static org.lwjgl.system.MemoryUtil.memAlloc;

public final class VulkanTexturePixelLoader {
    private VulkanTexturePixelLoader() {
    }

    public static VulkanTexturePixelData loadTexturePixels(Path texturePath) {
        Path sourcePath = texturePath;
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return null;
        }
        if (isKtxContainerPath(sourcePath)) {
            VulkanTexturePixelData decoded = loadTexturePixelsFromKtx(sourcePath);
            if (decoded != null) {
                return decoded;
            }
            sourcePath = resolveContainerSourcePath(sourcePath);
            if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
                return null;
            }
        }
        try {
            BufferedImage image = ImageIO.read(sourcePath.toFile());
            if (image != null) {
                return bufferedImageToPixels(image);
            }
        } catch (IOException ignored) {
            // Fall through to stb path.
        }
        return loadTexturePixelsViaStb(sourcePath);
    }

    private static VulkanTexturePixelData loadTexturePixelsFromKtx(Path containerPath) {
        KtxDecodeUtil.DecodedRgba decoded = KtxDecodeUtil.decodeToRgbaIfSupported(containerPath);
        if (decoded == null) {
            return null;
        }
        int width = decoded.width();
        int height = decoded.height();
        byte[] src = decoded.rgbaBytes();
        ByteBuffer buffer = memAlloc(src.length);
        int rowBytes = width * 4;
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            buffer.put(src, srcY * rowBytes, rowBytes);
        }
        buffer.flip();
        return new VulkanTexturePixelData(buffer, width, height);
    }

    private static VulkanTexturePixelData bufferedImageToPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer buffer = memAlloc(width * height * 4);
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, srcY);
                buffer.put((byte) ((argb >> 16) & 0xFF));
                buffer.put((byte) ((argb >> 8) & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buffer.flip();
        return new VulkanTexturePixelData(buffer, width, height);
    }

    private static VulkanTexturePixelData loadTexturePixelsViaStb(Path texturePath) {
        String path = texturePath.toAbsolutePath().toString();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!stbi_info(path, x, y, channels)) {
                return null;
            }
            int width = x.get(0);
            int height = y.get(0);
            if (width <= 0 || height <= 0) {
                return null;
            }
            if (stbi_is_hdr(path)) {
                FloatBuffer hdr = stbi_loadf(path, x, y, channels, 4);
                if (hdr == null) {
                    return null;
                }
                try {
                    ByteBuffer buffer = memAlloc(width * height * 4);
                    for (int i = 0; i < width * height; i++) {
                        float r = hdr.get(i * 4);
                        float g = hdr.get(i * 4 + 1);
                        float b = hdr.get(i * 4 + 2);
                        float a = hdr.get(i * 4 + 3);
                        buffer.put((byte) toLdrByte(r));
                        buffer.put((byte) toLdrByte(g));
                        buffer.put((byte) toLdrByte(b));
                        buffer.put((byte) Math.max(0, Math.min(255, Math.round(Math.max(0f, Math.min(1f, a)) * 255f))));
                    }
                    buffer.flip();
                    return new VulkanTexturePixelData(buffer, width, height);
                } finally {
                    stbi_image_free(hdr);
                }
            }
            ByteBuffer ldr = stbi_load(path, x, y, channels, 4);
            if (ldr == null) {
                return null;
            }
            try {
                ByteBuffer copy = memAlloc(width * height * 4);
                copy.put(ldr);
                copy.flip();
                return new VulkanTexturePixelData(copy, width, height);
            } finally {
                stbi_image_free(ldr);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int toLdrByte(float hdrValue) {
        float toneMapped = hdrValue / (1.0f + Math.max(0f, hdrValue));
        float gammaCorrected = (float) Math.pow(Math.max(0f, toneMapped), 1.0 / 2.2);
        return Math.max(0, Math.min(255, Math.round(gammaCorrected * 255f)));
    }

    private static Path resolveContainerSourcePath(Path requestedPath) {
        if (requestedPath == null || !Files.isRegularFile(requestedPath) || !isKtxContainerPath(requestedPath)) {
            return requestedPath;
        }
        String fileName = requestedPath.getFileName() == null ? null : requestedPath.getFileName().toString();
        if (fileName == null) {
            return requestedPath;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return requestedPath;
        }
        String baseName = fileName.substring(0, dot);
        for (String ext : new String[]{".png", ".hdr", ".jpg", ".jpeg"}) {
            Path candidate = requestedPath.resolveSibling(baseName + ext);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return requestedPath;
    }

    private static boolean isKtxContainerPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".ktx") || name.endsWith(".ktx2");
    }
}
