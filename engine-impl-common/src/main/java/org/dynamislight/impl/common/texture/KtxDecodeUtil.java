package org.dynamislight.impl.common.texture;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal native KTX/KTX2 decode utility used for IBL fallback ingestion.
 * Supports baseline uncompressed RGBA8 payload extraction.
 */
public final class KtxDecodeUtil {
    private static final byte[] KTX1_IDENTIFIER = new byte[]{
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] KTX2_IDENTIFIER = new byte[]{
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_RGBA = 0x1908;
    private static final int MAX_DECODE_BYTES = 64 * 1024 * 1024;
    private static final Map<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(long size, long modifiedMs, Path decodedPath) {
    }

    private KtxDecodeUtil() {
    }

    public static Path decodeToPngIfSupported(Path containerPath) {
        if (containerPath == null || !Files.isRegularFile(containerPath)) {
            return null;
        }
        Path source = containerPath.toAbsolutePath().normalize();
        String name = source.getFileName() == null ? "" : source.getFileName().toString().toLowerCase();
        if (!name.endsWith(".ktx") && !name.endsWith(".ktx2")) {
            return null;
        }
        try {
            long size = Files.size(source);
            if (size <= 0 || size > MAX_DECODE_BYTES) {
                return null;
            }
            long modified = Files.getLastModifiedTime(source).toMillis();
            CacheEntry cached = CACHE.get(source);
            if (cached != null
                    && cached.size() == size
                    && cached.modifiedMs() == modified
                    && Files.isRegularFile(cached.decodedPath())) {
                return cached.decodedPath();
            }
            byte[] bytes = Files.readAllBytes(source);
            BufferedImage image = decodeImage(bytes);
            if (image == null) {
                return null;
            }
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "dle-ktx-cache");
            Files.createDirectories(dir);
            Path out = dir.resolve(stableName(source, size, modified) + ".png");
            javax.imageio.ImageIO.write(image, "png", out.toFile());
            CACHE.put(source, new CacheEntry(size, modified, out));
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static BufferedImage decodeToImageIfSupported(Path containerPath) {
        if (containerPath == null || !Files.isRegularFile(containerPath)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(containerPath.toAbsolutePath().normalize());
            return decodeImage(bytes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean canDecodeSupported(Path containerPath) {
        return decodeToImageIfSupported(containerPath) != null;
    }

    public static boolean isKnownUnsupportedVariant(Path containerPath) {
        if (containerPath == null || !Files.isRegularFile(containerPath)) {
            return false;
        }
        try {
            byte[] bytes = Files.readAllBytes(containerPath.toAbsolutePath().normalize());
            if (startsWith(bytes, KTX2_IDENTIFIER)) {
                return isUnsupportedKtx2(bytes);
            }
            if (startsWith(bytes, KTX1_IDENTIFIER)) {
                return isUnsupportedKtx1(bytes);
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static String stableName(Path source, long size, long modified) {
        String base = Integer.toHexString(source.toString().hashCode());
        return "ktx_" + base + "_" + Long.toHexString(size) + "_" + Long.toHexString(modified);
    }

    private static BufferedImage decodeImage(byte[] bytes) {
        if (startsWith(bytes, KTX2_IDENTIFIER)) {
            return decodeKtx2(bytes);
        }
        if (startsWith(bytes, KTX1_IDENTIFIER)) {
            return decodeKtx1(bytes);
        }
        return null;
    }

    private static BufferedImage decodeKtx2(byte[] bytes) {
        if (bytes.length < 104) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int vkFormat = bb.getInt(12);
        int pixelWidth = bb.getInt(20);
        int pixelHeight = bb.getInt(24);
        int layerCount = bb.getInt(32);
        int faceCount = bb.getInt(36);
        int levelCount = bb.getInt(40);
        int supercompression = bb.getInt(44);
        if (vkFormat != VK_FORMAT_R8G8B8A8_UNORM
                || pixelWidth <= 0
                || pixelHeight <= 0
                || faceCount != 1
                || supercompression != 0
                || levelCount <= 0
                || layerCount > 1) {
            return null;
        }
        int levelIndexOffset = 80;
        if (bytes.length < levelIndexOffset + 24) {
            return null;
        }
        long byteOffset = bb.getLong(levelIndexOffset);
        long byteLength = bb.getLong(levelIndexOffset + 8);
        long uncompressedLength = bb.getLong(levelIndexOffset + 16);
        long expected = (long) pixelWidth * (long) pixelHeight * 4L;
        long actualLength = uncompressedLength > 0 ? uncompressedLength : byteLength;
        if (actualLength < expected || byteOffset < 0 || byteOffset + expected > bytes.length) {
            return null;
        }
        return rgbaToImage(bytes, (int) byteOffset, pixelWidth, pixelHeight);
    }

    private static boolean isUnsupportedKtx2(byte[] bytes) {
        if (bytes.length < 104) {
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int vkFormat = bb.getInt(12);
        int supercompression = bb.getInt(44);
        return supercompression != 0 || vkFormat != VK_FORMAT_R8G8B8A8_UNORM;
    }

    private static BufferedImage decodeKtx1(byte[] bytes) {
        if (bytes.length < 68) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int glType = bb.getInt(16);
        int glFormat = bb.getInt(24);
        int pixelWidth = bb.getInt(36);
        int pixelHeight = bb.getInt(40);
        int faceCount = bb.getInt(52);
        int mipLevels = bb.getInt(56);
        int keyValueBytes = bb.getInt(60);
        if (glType != GL_UNSIGNED_BYTE
                || glFormat != GL_RGBA
                || pixelWidth <= 0
                || pixelHeight <= 0
                || faceCount != 1
                || mipLevels <= 0) {
            return null;
        }
        int imageSizeOffset = 64 + Math.max(0, keyValueBytes);
        if (imageSizeOffset + 4 > bytes.length) {
            return null;
        }
        int imageSize = bb.getInt(imageSizeOffset);
        int imageOffset = imageSizeOffset + 4;
        long expected = (long) pixelWidth * (long) pixelHeight * 4L;
        if (imageSize < expected || imageOffset + expected > bytes.length) {
            return null;
        }
        return rgbaToImage(bytes, imageOffset, pixelWidth, pixelHeight);
    }

    private static boolean isUnsupportedKtx1(byte[] bytes) {
        if (bytes.length < 68) {
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int glType = bb.getInt(16);
        int glFormat = bb.getInt(24);
        return glType != GL_UNSIGNED_BYTE || glFormat != GL_RGBA;
    }

    private static BufferedImage rgbaToImage(byte[] bytes, int offset, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int idx = offset;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = bytes[idx++] & 0xFF;
                int g = bytes[idx++] & 0xFF;
                int b = bytes[idx++] & 0xFF;
                int a = bytes[idx++] & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
