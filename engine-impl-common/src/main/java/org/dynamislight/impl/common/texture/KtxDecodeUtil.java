package org.dynamislight.impl.common.texture;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

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
    private static final int VK_FORMAT_R8G8B8A8_SRGB = 43;
    private static final int VK_FORMAT_B8G8R8A8_UNORM = 44;
    private static final int VK_FORMAT_B8G8R8A8_SRGB = 50;
    private static final int VK_FORMAT_R8G8B8_UNORM = 23;
    private static final int VK_FORMAT_R8G8_UNORM = 16;
    private static final int VK_FORMAT_R8_UNORM = 9;
    private static final int KTX2_SUPERCOMPRESSION_NONE = 0;
    private static final int KTX2_SUPERCOMPRESSION_ZLIB = 1;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_RED = 0x1903;
    private static final int GL_RG = 0x8227;
    private static final int GL_RGB = 0x1907;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_BGRA = 0x80E1;
    private static final int MAX_DECODE_BYTES = 64 * 1024 * 1024;
    private static final Map<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(long size, long modifiedMs, Path decodedPath) {
    }

    public record DecodedRgba(int width, int height, byte[] rgbaBytes) {
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
        DecodedRgba decoded = decodeToRgbaIfSupported(containerPath);
        if (decoded == null) {
            return null;
        }
        return rgbaToImage(decoded.width(), decoded.height(), decoded.rgbaBytes());
    }

    public static DecodedRgba decodeToRgbaIfSupported(Path containerPath) {
        if (containerPath == null || !Files.isRegularFile(containerPath)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(containerPath.toAbsolutePath().normalize());
            return decodeRaw(bytes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean canDecodeSupported(Path containerPath) {
        return decodeToRgbaIfSupported(containerPath) != null;
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
        DecodedRgba decoded = decodeRaw(bytes);
        if (decoded == null) {
            return null;
        }
        return rgbaToImage(decoded.width(), decoded.height(), decoded.rgbaBytes());
    }

    private static DecodedRgba decodeRaw(byte[] bytes) {
        if (startsWith(bytes, KTX2_IDENTIFIER)) {
            return decodeKtx2(bytes);
        }
        if (startsWith(bytes, KTX1_IDENTIFIER)) {
            return decodeKtx1(bytes);
        }
        return null;
    }

    private static DecodedRgba decodeKtx2(byte[] bytes) {
        if (bytes.length < 104) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int vkFormat = bb.getInt(12);
        int bytesPerPixel = bytesPerPixelForVkFormat(vkFormat);
        ChannelLayout layout = layoutForVkFormat(vkFormat);
        int pixelWidth = bb.getInt(20);
        int pixelHeight = bb.getInt(24);
        int layerCount = bb.getInt(32);
        int faceCount = bb.getInt(36);
        int levelCount = bb.getInt(40);
        int supercompression = bb.getInt(44);
        if (bytesPerPixel <= 0
                || layout == null
                || pixelWidth <= 0
                || pixelHeight <= 0
                || faceCount != 1
                || (supercompression != KTX2_SUPERCOMPRESSION_NONE && supercompression != KTX2_SUPERCOMPRESSION_ZLIB)
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
        long expected = (long) pixelWidth * (long) pixelHeight * bytesPerPixel;
        long expectedUncompressed = uncompressedLength > 0 ? uncompressedLength : expected;
        if (expectedUncompressed < expected || byteOffset < 0 || byteOffset + byteLength > bytes.length) {
            return null;
        }
        byte[] rawBytes;
        if (supercompression == KTX2_SUPERCOMPRESSION_NONE) {
            if (byteLength < expected) {
                return null;
            }
            rawBytes = new byte[(int) expected];
            System.arraycopy(bytes, (int) byteOffset, rawBytes, 0, rawBytes.length);
        } else {
            rawBytes = inflateZlib(bytes, (int) byteOffset, (int) byteLength, (int) expectedUncompressed);
            if (rawBytes == null || rawBytes.length < expected) {
                return null;
            }
        }
        return toRgba(rawBytes, 0, pixelWidth, pixelHeight, bytesPerPixel, layout);
    }

    private static boolean isUnsupportedKtx2(byte[] bytes) {
        if (bytes.length < 104) {
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int vkFormat = bb.getInt(12);
        int supercompression = bb.getInt(44);
        if (supercompression != KTX2_SUPERCOMPRESSION_NONE && supercompression != KTX2_SUPERCOMPRESSION_ZLIB) {
            return true;
        }
        return bytesPerPixelForVkFormat(vkFormat) <= 0 || layoutForVkFormat(vkFormat) == null;
    }

    private static DecodedRgba decodeKtx1(byte[] bytes) {
        if (bytes.length < 68) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int glType = bb.getInt(16);
        int glFormat = bb.getInt(24);
        int bytesPerPixel = bytesPerPixelForGlFormat(glFormat);
        ChannelLayout layout = layoutForGlFormat(glFormat);
        int pixelWidth = bb.getInt(36);
        int pixelHeight = bb.getInt(40);
        int faceCount = bb.getInt(52);
        int mipLevels = bb.getInt(56);
        int keyValueBytes = bb.getInt(60);
        if (glType != GL_UNSIGNED_BYTE
                || bytesPerPixel <= 0
                || layout == null
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
        long expected = (long) pixelWidth * (long) pixelHeight * bytesPerPixel;
        if (imageSize < expected || imageOffset + expected > bytes.length) {
            return null;
        }
        return toRgba(bytes, imageOffset, pixelWidth, pixelHeight, bytesPerPixel, layout);
    }

    private static boolean isUnsupportedKtx1(byte[] bytes) {
        if (bytes.length < 68) {
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int glType = bb.getInt(16);
        int glFormat = bb.getInt(24);
        if (glType != GL_UNSIGNED_BYTE) {
            return true;
        }
        return bytesPerPixelForGlFormat(glFormat) <= 0 || layoutForGlFormat(glFormat) == null;
    }

    private static DecodedRgba toRgba(
            byte[] bytes,
            int offset,
            int width,
            int height,
            int bytesPerPixel,
            ChannelLayout layout
    ) {
        byte[] rgba = new byte[width * height * 4];
        int idx = offset;
        int out = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                int a = 255;
                if (layout == ChannelLayout.R) {
                    r = bytes[idx++] & 0xFF;
                    g = r;
                    b = r;
                } else if (layout == ChannelLayout.RG) {
                    r = bytes[idx++] & 0xFF;
                    g = bytes[idx++] & 0xFF;
                } else if (layout == ChannelLayout.RGB) {
                    r = bytes[idx++] & 0xFF;
                    g = bytes[idx++] & 0xFF;
                    b = bytes[idx++] & 0xFF;
                } else if (layout == ChannelLayout.RGBA) {
                    r = bytes[idx++] & 0xFF;
                    g = bytes[idx++] & 0xFF;
                    b = bytes[idx++] & 0xFF;
                    a = bytes[idx++] & 0xFF;
                } else if (layout == ChannelLayout.BGRA) {
                    b = bytes[idx++] & 0xFF;
                    g = bytes[idx++] & 0xFF;
                    r = bytes[idx++] & 0xFF;
                    a = bytes[idx++] & 0xFF;
                } else {
                    idx += bytesPerPixel;
                }
                rgba[out++] = (byte) r;
                rgba[out++] = (byte) g;
                rgba[out++] = (byte) b;
                rgba[out++] = (byte) a;
            }
        }
        return new DecodedRgba(width, height, rgba);
    }

    private static BufferedImage rgbaToImage(int width, int height, byte[] rgbaBytes) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rgbaBytes[idx++] & 0xFF;
                int g = rgbaBytes[idx++] & 0xFF;
                int b = rgbaBytes[idx++] & 0xFF;
                int a = rgbaBytes[idx++] & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static byte[] inflateZlib(byte[] source, int offset, int length, int expectedLength) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(source, offset, length);
            byte[] out = new byte[Math.max(0, expectedLength)];
            int inflated = inflater.inflate(out);
            if (inflated <= 0) {
                return null;
            }
            if (inflated == out.length) {
                return out;
            }
            byte[] exact = new byte[inflated];
            System.arraycopy(out, 0, exact, 0, inflated);
            return exact;
        } catch (DataFormatException ignored) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static int bytesPerPixelForVkFormat(int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8_UNORM -> 1;
            case VK_FORMAT_R8G8_UNORM -> 2;
            case VK_FORMAT_R8G8B8_UNORM -> 3;
            case VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_SRGB, VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_B8G8R8A8_SRGB -> 4;
            default -> -1;
        };
    }

    private static ChannelLayout layoutForVkFormat(int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8_UNORM -> ChannelLayout.R;
            case VK_FORMAT_R8G8_UNORM -> ChannelLayout.RG;
            case VK_FORMAT_R8G8B8_UNORM -> ChannelLayout.RGB;
            case VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_SRGB -> ChannelLayout.RGBA;
            case VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_B8G8R8A8_SRGB -> ChannelLayout.BGRA;
            default -> null;
        };
    }

    private static int bytesPerPixelForGlFormat(int glFormat) {
        return switch (glFormat) {
            case GL_RED -> 1;
            case GL_RG -> 2;
            case GL_RGB -> 3;
            case GL_RGBA, GL_BGRA -> 4;
            default -> -1;
        };
    }

    private static ChannelLayout layoutForGlFormat(int glFormat) {
        return switch (glFormat) {
            case GL_RED -> ChannelLayout.R;
            case GL_RG -> ChannelLayout.RG;
            case GL_RGB -> ChannelLayout.RGB;
            case GL_RGBA -> ChannelLayout.RGBA;
            case GL_BGRA -> ChannelLayout.BGRA;
            default -> null;
        };
    }

    private enum ChannelLayout {
        R,
        RG,
        RGB,
        RGBA,
        BGRA
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
