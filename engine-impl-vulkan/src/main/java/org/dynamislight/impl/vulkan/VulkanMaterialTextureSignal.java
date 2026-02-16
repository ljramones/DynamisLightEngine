package org.dynamislight.impl.vulkan;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

final class VulkanMaterialTextureSignal {
    private final float[] albedoTint;
    private final float normalStrength;

    private VulkanMaterialTextureSignal(float[] albedoTint, float normalStrength) {
        this.albedoTint = albedoTint;
        this.normalStrength = normalStrength;
    }

    static VulkanMaterialTextureSignal fromMaterialTextures(Path assetRoot, String albedoPath, String normalPath) {
        float[] tint = new float[]{1f, 1f, 1f};
        float normal = 1f;

        Path a = resolve(assetRoot, albedoPath);
        if (a != null) {
            float[] avg = averageRgb(a);
            if (avg != null) {
                tint = avg;
            }
        }

        Path n = resolve(assetRoot, normalPath);
        if (n != null) {
            float[] avg = averageRgb(n);
            if (avg != null) {
                float zBias = Math.max(0.05f, avg[2]);
                normal = Math.max(0.55f, Math.min(1.6f, zBias * 1.2f));
            }
        }

        return new VulkanMaterialTextureSignal(tint, normal);
    }

    float[] albedoTint() {
        return albedoTint;
    }

    float normalStrength() {
        return normalStrength;
    }

    private static Path resolve(Path assetRoot, String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        Path path = Path.of(texturePath);
        Path resolved = path.isAbsolute() ? path.normalize() : assetRoot.resolve(path).normalize();
        return Files.isRegularFile(resolved) ? resolved : null;
    }

    private static float[] averageRgb(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                return null;
            }
            long r = 0;
            long g = 0;
            long b = 0;
            int width = image.getWidth();
            int height = image.getHeight();
            long count = Math.max(1L, (long) width * height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                }
            }
            return new float[]{
                    (float) (r / (255.0 * count)),
                    (float) (g / (255.0 * count)),
                    (float) (b / (255.0 * count))
            };
        } catch (IOException ignored) {
            return null;
        }
    }
}
