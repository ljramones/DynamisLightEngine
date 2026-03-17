package org.dynamisengine.light.impl.opengl;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.scene.EnvironmentDesc;
import org.dynamisengine.light.impl.common.texture.KtxDecodeUtil;

/**
 * Resolves IBL (image-based lighting) assets from scene environment descriptors,
 * handling KTX container fallback, sidecar decode paths, and luminance sampling.
 */
final class OpenGlIblResolver {

    private OpenGlIblResolver() {
    }

    // ── record ──

    record IblRenderConfig(
            boolean enabled,
            float diffuseStrength,
            float specularStrength,
            boolean textureDriven,
            boolean skyboxDerived,
            boolean ktxContainerRequested,
            boolean ktxSkyboxFallback,
            int ktxDecodeUnavailableCount,
            int ktxTranscodeRequiredCount,
            int ktxUnsupportedVariantCount,
            float prefilterStrength,
            boolean degraded,
            int missingAssetCount,
            Path irradiancePath,
            Path radiancePath,
            Path brdfLutPath
    ) {
    }

    static final IblRenderConfig DISABLED =
            new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0, 0, 0f, false, 0, null, null, null);

    // ── main entry ──

    static IblRenderConfig mapIbl(EnvironmentDesc environment, QualityTier qualityTier, Path assetRoot) {
        if (environment == null) {
            return DISABLED;
        }
        boolean enabled = !isBlank(environment.iblIrradiancePath())
                || !isBlank(environment.iblRadiancePath())
                || !isBlank(environment.iblBrdfLutPath())
                || !isBlank(environment.skyboxAssetPath());
        if (!enabled) {
            return DISABLED;
        }
        float tierScale = switch (qualityTier) {
            case LOW -> 0.62f;
            case MEDIUM -> 0.82f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.15f;
        };
        float diffuse = 0.42f * tierScale;
        float specular = 0.30f * tierScale;
        float prefilterStrength = switch (qualityTier) {
            case LOW -> 0.38f;
            case MEDIUM -> 0.62f;
            case HIGH -> 0.85f;
            case ULTRA -> 1.0f;
        };
        boolean degraded = qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        boolean textureDriven = false;

        String fallbackSkyboxPath = environment.skyboxAssetPath();
        boolean skyboxDerived = !isBlank(fallbackSkyboxPath)
                && (isBlank(environment.iblIrradiancePath()) || isBlank(environment.iblRadiancePath()));
        Path irrSource = resolveTexturePath(
                isBlank(environment.iblIrradiancePath()) ? fallbackSkyboxPath : environment.iblIrradiancePath(),
                assetRoot
        );
        Path radSource = resolveTexturePath(
                isBlank(environment.iblRadiancePath()) ? fallbackSkyboxPath : environment.iblRadiancePath(),
                assetRoot
        );
        Path brdfSource = resolveTexturePath(environment.iblBrdfLutPath(), assetRoot);
        boolean ktxContainerRequested = isKtxContainerPath(irrSource)
                || isKtxContainerPath(radSource)
                || isKtxContainerPath(brdfSource);

        Path irr = resolveContainerSourcePath(irrSource);
        Path rad = resolveContainerSourcePath(radSource);
        Path brdf = resolveContainerSourcePath(brdfSource);
        boolean ktxSkyboxFallback = false;
        Path skyboxResolved = resolveContainerSourcePath(resolveTexturePath(fallbackSkyboxPath, assetRoot));
        if (isKtxContainerPath(irrSource) && !isRegularFile(irr) && isRegularFile(skyboxResolved)) {
            irr = skyboxResolved;
            ktxSkyboxFallback = true;
        }
        if (isKtxContainerPath(radSource) && !isRegularFile(rad) && isRegularFile(skyboxResolved)) {
            rad = skyboxResolved;
            ktxSkyboxFallback = true;
        }
        boolean skyboxDerivedActive = skyboxDerived || ktxSkyboxFallback;
        int ktxDecodeUnavailableCount = 0;
        ktxDecodeUnavailableCount += decodeUnavailableChannelCount(irrSource, irr);
        ktxDecodeUnavailableCount += decodeUnavailableChannelCount(radSource, rad);
        ktxDecodeUnavailableCount += decodeUnavailableChannelCount(brdfSource, brdf);
        int ktxTranscodeRequiredCount = 0;
        ktxTranscodeRequiredCount += transcodeRequiredChannelCount(irrSource);
        ktxTranscodeRequiredCount += transcodeRequiredChannelCount(radSource);
        ktxTranscodeRequiredCount += transcodeRequiredChannelCount(brdfSource);
        int ktxUnsupportedVariantCount = 0;
        ktxUnsupportedVariantCount += unsupportedVariantChannelCount(irrSource);
        ktxUnsupportedVariantCount += unsupportedVariantChannelCount(radSource);
        ktxUnsupportedVariantCount += unsupportedVariantChannelCount(brdfSource);
        int missingAssetCount = countMissingFiles(irr, rad, brdf);
        float irrSignal = imageLuminanceSignal(irr);
        float radSignal = imageLuminanceSignal(rad);
        float brdfSignal = imageLuminanceSignal(brdf);
        if (irrSignal >= 0f || radSignal >= 0f || brdfSignal >= 0f) {
            float irrUsed = irrSignal < 0f ? 0.5f : irrSignal;
            float radUsed = radSignal < 0f ? 0.5f : radSignal;
            float brdfUsed = brdfSignal < 0f ? 0.5f : brdfSignal;
            float diffuseScale = 0.82f + 0.36f * irrUsed;
            float specScale = 0.78f + 0.42f * ((radUsed * 0.6f) + (brdfUsed * 0.4f));
            diffuse *= diffuseScale;
            specular *= specScale;
            prefilterStrength = Math.max(prefilterStrength, 0.65f + 0.35f * radUsed);
            textureDriven = true;
        }

        return new IblRenderConfig(
                true,
                Math.max(0f, Math.min(2.0f, diffuse)),
                Math.max(0f, Math.min(2.0f, specular)),
                textureDriven,
                skyboxDerivedActive,
                ktxContainerRequested,
                ktxSkyboxFallback,
                ktxDecodeUnavailableCount,
                ktxTranscodeRequiredCount,
                ktxUnsupportedVariantCount,
                Math.max(0f, Math.min(1f, prefilterStrength)),
                degraded,
                missingAssetCount,
                irr,
                rad,
                brdf
        );
    }

    // ── helpers ──

    static int countMissingFiles(Path... paths) {
        int missing = 0;
        for (Path path : paths) {
            if (!isRegularFile(path)) {
                missing++;
            }
        }
        return missing;
    }

    static boolean isRegularFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    static int decodeUnavailableChannelCount(Path requestedPath, Path resolvedPath) {
        if (!isKtxContainerPath(requestedPath) || !isRegularFile(requestedPath)) {
            return 0;
        }
        if (!Objects.equals(requestedPath, resolvedPath)) {
            return 0;
        }
        if (KtxDecodeUtil.canDecodeSupported(requestedPath)) {
            return 0;
        }
        if (KtxDecodeUtil.requiresTranscode(requestedPath)) {
            return 0;
        }
        return canDecodeViaStb(requestedPath) ? 0 : 1;
    }

    static int transcodeRequiredChannelCount(Path requestedPath) {
        if (!isKtxContainerPath(requestedPath) || !isRegularFile(requestedPath)) {
            return 0;
        }
        if (KtxDecodeUtil.canDecodeSupported(requestedPath)) {
            return 0;
        }
        if (!KtxDecodeUtil.requiresTranscode(requestedPath)) {
            return 0;
        }
        return canDecodeViaStb(requestedPath) ? 0 : 1;
    }

    static int unsupportedVariantChannelCount(Path requestedPath) {
        if (!isKtxContainerPath(requestedPath) || !isRegularFile(requestedPath)) {
            return 0;
        }
        if (KtxDecodeUtil.requiresTranscode(requestedPath)) {
            return 0;
        }
        if (!KtxDecodeUtil.isKnownUnsupportedVariant(requestedPath)) {
            return 0;
        }
        return canDecodeViaStb(requestedPath) ? 0 : 1;
    }

    static boolean canDecodeViaStb(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            java.nio.ByteBuffer pixels = org.lwjgl.stb.STBImage.stbi_load(
                    path.toAbsolutePath().toString(),
                    x,
                    y,
                    channels,
                    4
            );
            if (pixels == null || x.get(0) <= 0 || y.get(0) <= 0) {
                return false;
            }
            org.lwjgl.stb.STBImage.stbi_image_free(pixels);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Path resolveTexturePath(String texturePath, Path assetRoot) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        Path path = Path.of(texturePath);
        return path.isAbsolute() ? path : assetRoot.resolve(path).normalize();
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

    static float imageLuminanceSignal(Path path) {
        Path sourcePath = resolveContainerSourcePath(path);
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return -1f;
        }
        String name = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString().toLowerCase();
        boolean imageIoSupported = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        boolean hdrSupported = name.endsWith(".hdr");
        if (!imageIoSupported && !hdrSupported) {
            BufferedImage ktxImage = KtxDecodeUtil.decodeToImageIfSupported(sourcePath);
            return ktxImage == null ? -1f : bufferedImageLuminanceSignal(ktxImage);
        }
        if (hdrSupported) {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var x = stack.mallocInt(1);
                var y = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                FloatBuffer hdr = org.lwjgl.stb.STBImage.stbi_loadf(sourcePath.toAbsolutePath().toString(), x, y, channels, 3);
                if (hdr == null || x.get(0) <= 0 || y.get(0) <= 0) {
                    return -1f;
                }
                try {
                    int width = x.get(0);
                    int height = y.get(0);
                    int stepX = Math.max(1, width / 64);
                    int stepY = Math.max(1, height / 64);
                    double sum = 0.0;
                    int count = 0;
                    for (int yIdx = 0; yIdx < height; yIdx += stepY) {
                        for (int xIdx = 0; xIdx < width; xIdx += stepX) {
                            int idx = (yIdx * width + xIdx) * 3;
                            float r = hdr.get(idx);
                            float g = hdr.get(idx + 1);
                            float b = hdr.get(idx + 2);
                            float ldrR = toneMapLdr(r);
                            float ldrG = toneMapLdr(g);
                            float ldrB = toneMapLdr(b);
                            sum += (0.2126 * ldrR) + (0.7152 * ldrG) + (0.0722 * ldrB);
                            count++;
                        }
                    }
                    if (count == 0) {
                        return -1f;
                    }
                    return (float) Math.max(0.0, Math.min(1.0, sum / count));
                } finally {
                    org.lwjgl.stb.STBImage.stbi_image_free(hdr);
                }
            } catch (Throwable ignored) {
                return -1f;
            }
        }
        try {
            BufferedImage image = javax.imageio.ImageIO.read(sourcePath.toFile());
            return bufferedImageLuminanceSignal(image);
        } catch (IOException ignored) {
            return -1f;
        }
    }

    static float bufferedImageLuminanceSignal(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return -1f;
        }
        int stepX = Math.max(1, image.getWidth() / 64);
        int stepY = Math.max(1, image.getHeight() / 64);
        double sum = 0.0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int argb = image.getRGB(x, y);
                float r = ((argb >> 16) & 0xFF) / 255f;
                float g = ((argb >> 8) & 0xFF) / 255f;
                float b = (argb & 0xFF) / 255f;
                sum += (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
                count++;
            }
        }
        if (count == 0) {
            return -1f;
        }
        return (float) Math.max(0.0, Math.min(1.0, sum / count));
    }

    private static float toneMapLdr(float hdrValue) {
        float toneMapped = hdrValue / (1.0f + Math.max(0f, hdrValue));
        return (float) Math.pow(Math.max(0f, toneMapped), 1.0 / 2.2);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
