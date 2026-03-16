package org.dynamisengine.light.impl.opengl;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameterf;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_info;
import static org.lwjgl.stb.STBImage.stbi_is_hdr;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.stb.STBImage.stbi_loadf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.dynamisengine.light.impl.common.texture.KtxDecodeUtil;
import org.lwjgl.system.MemoryStack;

final class OpenGlTextureLoader {

    record TextureData(int id, long bytes, int maxLod) {
    }

    private final float maxAnisotropy;

    OpenGlTextureLoader(float maxAnisotropy) {
        this.maxAnisotropy = maxAnisotropy;
    }

    TextureData loadTexture(Path texturePath) {
        return loadTexture(texturePath, false);
    }

    TextureData loadTexture(Path texturePath, boolean sRgb) {
        Path sourcePath = texturePath;
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return new TextureData(0, 0, 0);
        }
        if (isKtxContainerPath(sourcePath)) {
            TextureData decoded = loadTextureFromKtx(sourcePath, sRgb);
            if (decoded.id() != 0) {
                return decoded;
            }
            sourcePath = resolveContainerSourcePath(sourcePath);
            if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
                return new TextureData(0, 0, 0);
            }
        }
        try {
            BufferedImage image = ImageIO.read(sourcePath.toFile());
            if (image != null) {
                return uploadBufferedImageTexture(image, sRgb);
            }
        } catch (IOException ignored) {
            // Fall through to stb path.
        }
        return loadTextureViaStb(sourcePath, sRgb);
    }

    TextureData loadTextureFromKtx(Path containerPath, boolean sRgb) {
        KtxDecodeUtil.DecodedRgba decoded = KtxDecodeUtil.decodeToRgbaIfSupported(containerPath);
        if (decoded == null) {
            return new TextureData(0, 0, 0);
        }
        ByteBuffer rgba = ByteBuffer.allocateDirect(decoded.rgbaBytes().length).order(ByteOrder.nativeOrder());
        rgba.put(decoded.rgbaBytes());
        rgba.flip();
        return uploadRgbaTexture(rgba, decoded.width(), decoded.height(), sRgb);
    }

    TextureData uploadBufferedImageTexture(BufferedImage image, boolean sRgb) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                rgba.put((byte) ((argb >> 16) & 0xFF));
                rgba.put((byte) ((argb >> 8) & 0xFF));
                rgba.put((byte) (argb & 0xFF));
                rgba.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        rgba.flip();
        return uploadRgbaTexture(rgba, width, height, sRgb);
    }

    TextureData loadTextureViaStb(Path texturePath, boolean sRgb) {
        String path = texturePath.toAbsolutePath().toString();
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            if (!stbi_info(path, x, y, channels)) {
                return new TextureData(0, 0, 0);
            }
            int width = x.get(0);
            int height = y.get(0);
            if (width <= 0 || height <= 0) {
                return new TextureData(0, 0, 0);
            }

            if (stbi_is_hdr(path)) {
                FloatBuffer hdr = stbi_loadf(path, x, y, channels, 4);
                if (hdr == null) {
                    return new TextureData(0, 0, 0);
                }
                try {
                    ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
                    for (int i = 0; i < width * height; i++) {
                        float r = hdr.get(i * 4);
                        float g = hdr.get(i * 4 + 1);
                        float b = hdr.get(i * 4 + 2);
                        float a = hdr.get(i * 4 + 3);
                        int rb = toLdrByte(r);
                        int gb = toLdrByte(g);
                        int bb = toLdrByte(b);
                        int ab = Math.max(0, Math.min(255, Math.round(Math.max(0f, Math.min(1f, a)) * 255f)));
                        rgba.put((byte) rb).put((byte) gb).put((byte) bb).put((byte) ab);
                    }
                    rgba.flip();
                    return uploadRgbaTexture(rgba, width, height, sRgb);
                } finally {
                    stbi_image_free(hdr);
                }
            }

            ByteBuffer ldr = stbi_load(path, x, y, channels, 4);
            if (ldr == null) {
                return new TextureData(0, 0, 0);
            }
            try {
                return uploadRgbaTexture(ldr, width, height, sRgb);
            } finally {
                stbi_image_free(ldr);
            }
        } catch (Throwable ignored) {
            return new TextureData(0, 0, 0);
        }
    }

    int loadTextureFromMemory(ByteBuffer imageData) {
        return loadTextureFromMemory(imageData, false);
    }

    int loadTextureFromMemory(ByteBuffer imageData, boolean sRgb) {
        if (imageData == null || imageData.remaining() == 0) {
            return 0;
        }
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            ByteBuffer ldr = stbi_load_from_memory(imageData, x, y, channels, 4);
            if (ldr == null) {
                return 0;
            }
            try {
                return uploadRgbaTexture(ldr, x.get(0), y.get(0), sRgb).id();
            } finally {
                stbi_image_free(ldr);
            }
        } catch (Throwable ignored) {
            return 0;
        }
    }

    TextureData uploadRgbaTexture(ByteBuffer rgba, int width, int height) {
        return uploadRgbaTexture(rgba, width, height, false);
    }

    TextureData uploadRgbaTexture(ByteBuffer rgba, int width, int height, boolean sRgb) {
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        int maxLod = (int) Math.floor(Math.log(Math.max(1, Math.max(width, height))) / Math.log(2));
        maxLod = Math.max(0, maxLod);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, maxLod);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        if (maxAnisotropy > 0f) {
            glTexParameterf(GL_TEXTURE_2D,
                    org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    Math.min(maxAnisotropy, 16f));
        }
        int internalFormat = sRgb ? GL_SRGB8_ALPHA8 : GL_RGBA;
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
        return new TextureData(textureId, (long) width * height * 4L, maxLod);
    }

    int toLdrByte(float hdrValue) {
        float toneMapped = hdrValue / (1.0f + Math.max(0f, hdrValue));
        float gammaCorrected = (float) Math.pow(Math.max(0f, toneMapped), 1.0 / 2.2);
        return Math.max(0, Math.min(255, Math.round(gammaCorrected * 255f)));
    }

    static Path resolveContainerSourcePath(Path requestedPath) {
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

    static boolean isKtxContainerPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".ktx") || name.endsWith(".ktx2");
    }
}
