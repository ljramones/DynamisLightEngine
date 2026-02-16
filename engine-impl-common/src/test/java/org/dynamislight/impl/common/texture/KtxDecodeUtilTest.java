package org.dynamislight.impl.common.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
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
            });
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
            writeKtx2(file, 37, 2, 2, 2, new byte[16]);
            assertTrue(KtxDecodeUtil.isKnownUnsupportedVariant(file));
            assertNull(KtxDecodeUtil.decodeToImageIfSupported(file));
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
            byte[] payload
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
        bb.putLong(payload.length);
        bb.position(levelOffset);
        bb.put(payload);
        Files.write(file, bb.array());
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
