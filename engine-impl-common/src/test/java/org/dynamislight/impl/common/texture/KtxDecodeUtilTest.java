package org.dynamislight.impl.common.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.luben.zstd.Zstd;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.ktx.KTX;
import org.lwjgl.util.ktx.ktxTexture;
import org.lwjgl.util.ktx.ktxTexture2;
import org.junit.jupiter.api.Test;

class KtxDecodeUtilTest {
    private static final byte[] KTX1_IDENTIFIER = new byte[]{
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] KTX2_IDENTIFIER = new byte[]{
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    void decodesKtx2Rgb8() throws Exception {
        Path file = Files.createTempFile("dle-ktx2-rgb8-", ".ktx2");
        try {
            writeKtx2(file, 23, 2, 1, 0, new byte[]{
                    (byte) 255, 0, 0,
                    0, (byte) 255, 0
            }, 6);
            BufferedImage image = KtxDecodeUtil.decodeToImageIfSupported(file);
            assertNotNull(image);
            assertEquals(2, image.getWidth());
            assertEquals(1, image.getHeight());
            assertEquals(0xFFFF0000, image.getRGB(0, 0));
            assertEquals(0xFF00FF00, image.getRGB(1, 0));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void decodesKtx2R16AndRg16() throws Exception {
        Path r16 = Files.createTempFile("dle-ktx2-r16-", ".ktx2");
        Path rg16 = Files.createTempFile("dle-ktx2-rg16-", ".ktx2");
        Path rgb16 = Files.createTempFile("dle-ktx2-rgb16-", ".ktx2");
        try {
            writeKtx2(r16, 70, 1, 1, 0, new byte[]{0x00, (byte) 0x80}, 2);
            writeKtx2(rg16, 77, 1, 1, 0, new byte[]{(byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0x80}, 4);
            writeKtx2(rgb16, 84, 1, 1, 0, new byte[]{
                    (byte) 0xFF, (byte) 0xFF, // R -> 255
                    0x00, (byte) 0x80,        // G -> 128
                    0x00, 0x00                // B -> 0
            }, 6);

            BufferedImage r16Image = KtxDecodeUtil.decodeToImageIfSupported(r16);
            BufferedImage rg16Image = KtxDecodeUtil.decodeToImageIfSupported(rg16);
            BufferedImage rgb16Image = KtxDecodeUtil.decodeToImageIfSupported(rgb16);
            assertNotNull(r16Image);
            assertNotNull(rg16Image);
            assertNotNull(rgb16Image);
            assertEquals(0xFF808080, r16Image.getRGB(0, 0));
            assertEquals(0xFFFF8000, rg16Image.getRGB(0, 0));
            assertEquals(0xFFFF8000, rgb16Image.getRGB(0, 0));
        } finally {
            Files.deleteIfExists(r16);
            Files.deleteIfExists(rg16);
            Files.deleteIfExists(rgb16);
        }
    }

    @Test
    void decodesKtx1RedAndBgra() throws Exception {
        Path red = Files.createTempFile("dle-ktx1-red-", ".ktx");
        Path bgra = Files.createTempFile("dle-ktx1-bgra-", ".ktx");
        try {
            writeKtx1(red, 0x1903, 1, 1, new byte[]{(byte) 120});
            writeKtx1(bgra, 0x80E1, 1, 1, new byte[]{0, (byte) 255, 0, (byte) 255});

            BufferedImage redImage = KtxDecodeUtil.decodeToImageIfSupported(red);
            BufferedImage bgraImage = KtxDecodeUtil.decodeToImageIfSupported(bgra);
            assertNotNull(redImage);
            assertNotNull(bgraImage);
            assertEquals(0xFF787878, redImage.getRGB(0, 0));
            assertEquals(0xFF00FF00, bgraImage.getRGB(0, 0));
        } finally {
            Files.deleteIfExists(red);
            Files.deleteIfExists(bgra);
        }
    }

    @Test
    void marksSupercompressedKtx2AsUnsupportedVariant() throws Exception {
        Path file = Files.createTempFile("dle-ktx2-super-", ".ktx2");
        try {
            writeKtx2(file, 37, 2, 2, 99, new byte[16], 16);
            assertTrue(KtxDecodeUtil.isKnownUnsupportedVariant(file));
            assertNull(KtxDecodeUtil.decodeToImageIfSupported(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void decodesKtx2ZlibSupercompressedRgba8() throws Exception {
        Path file = Files.createTempFile("dle-ktx2-zlib-", ".ktx2");
        try {
            byte[] raw = new byte[]{
                    (byte) 255, 0, 0, (byte) 255,
                    0, (byte) 255, 0, (byte) 255,
                    0, 0, (byte) 255, (byte) 255,
                    (byte) 255, (byte) 255, 0, (byte) 255
            };
            byte[] compressed = deflate(raw);
            writeKtx2(file, 37, 2, 2, 3, compressed, raw.length);
            KtxDecodeUtil.DecodedRgba decoded = KtxDecodeUtil.decodeToRgbaIfSupported(file);
            assertNotNull(decoded);
            assertEquals(2, decoded.width());
            assertEquals(2, decoded.height());
            assertEquals(raw.length, decoded.rgbaBytes().length);
            assertEquals((byte) 255, decoded.rgbaBytes()[0]);
            assertEquals((byte) 255, decoded.rgbaBytes()[15]);
            assertTrue(KtxDecodeUtil.canDecodeSupported(file));
            assertTrue(!KtxDecodeUtil.isKnownUnsupportedVariant(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void marksBasisLzKtx2AsTranscodeRequired() throws Exception {
        Path file = Files.createTempFile("dle-ktx2-basislz-", ".ktx2");
        try {
            writeKtx2(file, 0, 2, 2, 1, new byte[16], 16);
            assertTrue(KtxDecodeUtil.requiresTranscode(file));
            assertNull(KtxDecodeUtil.decodeToRgbaIfSupported(file));
            assertTrue(!KtxDecodeUtil.isKnownUnsupportedVariant(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void marksUndefinedFormatKtx2AsTranscodeRequired() throws Exception {
        Path file = Files.createTempFile("dle-ktx2-undefined-format-", ".ktx2");
        try {
            writeKtx2(file, 0, 2, 2, 0, new byte[16], 16);
            assertTrue(KtxDecodeUtil.requiresTranscode(file));
            assertNull(KtxDecodeUtil.decodeToRgbaIfSupported(file));
            assertTrue(!KtxDecodeUtil.isKnownUnsupportedVariant(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void decodesBasisLzKtx2ViaLibKtxTranscode() throws Exception {
        Path source = Files.createTempFile("dle-ktx2-source-", ".ktx2");
        Path basis = Files.createTempFile("dle-ktx2-basislz-", ".ktx2");
        try {
            byte[] raw = new byte[]{
                    (byte) 255, 0, 0, (byte) 255,
                    0, (byte) 255, 0, (byte) 255,
                    0, 0, (byte) 255, (byte) 255,
                    (byte) 255, (byte) 255, 0, (byte) 255
            };
            writeKtx2(source, 37, 2, 2, 0, raw, raw.length);
            assumeTrue(writeBasisLzFromSourceKtx2(source, basis), "libktx BasisLZ encode not available in this environment");

            assertTrue(KtxDecodeUtil.requiresTranscode(basis));
            KtxDecodeUtil.DecodedRgba decoded = KtxDecodeUtil.decodeToRgbaIfSupported(basis);
            assertNotNull(decoded);
            assertEquals(2, decoded.width());
            assertEquals(2, decoded.height());
            assertEquals(16, decoded.rgbaBytes().length);
            assertTrue(KtxDecodeUtil.canDecodeSupported(basis));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(basis);
        }
    }

    @Test
    void decodesKtx2ZstdSupercompressedRgba8() throws Exception {
        Path file = Files.createTempFile("dle-ktx2-zstd-", ".ktx2");
        try {
            byte[] raw = new byte[]{
                    (byte) 255, (byte) 10, (byte) 20, (byte) 255,
                    (byte) 40, (byte) 255, (byte) 60, (byte) 255,
                    (byte) 80, (byte) 90, (byte) 255, (byte) 255,
                    (byte) 255, (byte) 140, (byte) 120, (byte) 255
            };
            byte[] compressed = Zstd.compress(raw, 3);
            writeKtx2(file, 37, 2, 2, 2, compressed, raw.length);
            KtxDecodeUtil.DecodedRgba decoded = KtxDecodeUtil.decodeToRgbaIfSupported(file);
            assertNotNull(decoded);
            assertEquals(2, decoded.width());
            assertEquals(2, decoded.height());
            assertEquals(raw.length, decoded.rgbaBytes().length);
            assertEquals((byte) 255, decoded.rgbaBytes()[0]);
            assertEquals((byte) 255, decoded.rgbaBytes()[15]);
            assertTrue(KtxDecodeUtil.canDecodeSupported(file));
            assertTrue(!KtxDecodeUtil.isKnownUnsupportedVariant(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void writeKtx2(
            Path file,
            int vkFormat,
            int width,
            int height,
            int supercompressionScheme,
            byte[] payload,
            int uncompressedLength
    ) throws Exception {
        int levelOffset = 104;
        ByteBuffer bb = ByteBuffer.allocate(levelOffset + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(KTX2_IDENTIFIER);
        bb.putInt(vkFormat);
        bb.putInt(1);
        bb.putInt(width);
        bb.putInt(height);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(1);
        bb.putInt(1);
        bb.putInt(supercompressionScheme);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0);
        bb.putLong(0L);
        bb.putLong(0L);
        bb.putLong(levelOffset);
        bb.putLong(payload.length);
        bb.putLong(uncompressedLength > 0 ? uncompressedLength : payload.length);
        bb.position(levelOffset);
        bb.put(payload);
        Files.write(file, bb.array());
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            byte[] out = new byte[raw.length + 64];
            int len = deflater.deflate(out);
            byte[] exact = new byte[len];
            System.arraycopy(out, 0, exact, 0, len);
            return exact;
        } finally {
            deflater.end();
        }
    }

    private static boolean writeBasisLzFromSourceKtx2(Path source, Path out) {
        ByteBuffer sourceBytes = null;
        long texturePtr = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            byte[] bytes = Files.readAllBytes(source);
            sourceBytes = MemoryUtil.memAlloc(bytes.length);
            sourceBytes.put(bytes).flip();
            PointerBuffer textureOut = stack.mallocPointer(1);
            int create = KTX.ktxTexture2_CreateFromMemory(
                    sourceBytes,
                    KTX.KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT,
                    textureOut
            );
            if (create != KTX.KTX_SUCCESS) {
                return false;
            }
            texturePtr = textureOut.get(0);
            if (texturePtr == 0L) {
                return false;
            }
            ktxTexture2 texture2 = ktxTexture2.create(texturePtr);
            int compress = KTX.ktxTexture2_CompressBasis(texture2, 0);
            if (compress != KTX.KTX_SUCCESS) {
                return false;
            }
            int write = KTX.ktxWriteToNamedFile(ktxTexture.create(texturePtr), out.toAbsolutePath().toString());
            return write == KTX.KTX_SUCCESS;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (texturePtr != 0L) {
                try {
                    KTX.ktxTexture_Destroy(ktxTexture.create(texturePtr));
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            if (sourceBytes != null) {
                MemoryUtil.memFree(sourceBytes);
            }
        }
    }

    private static void writeKtx1(Path file, int glFormat, int width, int height, byte[] payload) throws Exception {
        int headerBytes = 64;
        int imageSizeBytes = 4;
        ByteBuffer bb = ByteBuffer.allocate(headerBytes + imageSizeBytes + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(KTX1_IDENTIFIER);
        bb.putInt(0x04030201);
        bb.putInt(0x1401);
        bb.putInt(1);
        bb.putInt(glFormat);
        bb.putInt(glFormat);
        bb.putInt(glFormat);
        bb.putInt(width);
        bb.putInt(height);
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(1);
        bb.putInt(1);
        bb.putInt(0);
        bb.putInt(payload.length);
        bb.put(payload);
        Files.write(file, bb.array());
    }
}
