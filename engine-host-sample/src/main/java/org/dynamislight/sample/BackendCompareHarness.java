package org.dynamislight.sample;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.EngineBackendProvider;
import org.dynamislight.spi.registry.BackendRegistry;

final class BackendCompareHarness {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);
    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 180;

    private BackendCompareHarness() {
    }

    static CompareReport run(Path outputDir, SceneDescriptor scene, QualityTier qualityTier) throws Exception {
        return run(outputDir, scene, qualityTier, qualityTier.name().toLowerCase());
    }

    static CompareReport run(Path outputDir, SceneDescriptor scene, QualityTier qualityTier, String profileTag) throws Exception {
        Files.createDirectories(outputDir);
        String normalizedTag = normalizeTag(profileTag);
        Path openGlPng = outputDir.resolve("opengl-" + normalizedTag + ".png");
        Path vulkanPng = outputDir.resolve("vulkan-" + normalizedTag + ".png");

        BackendSnapshot openGl = renderBackend("opengl", scene, qualityTier, normalizedTag);
        BackendSnapshot vulkan = renderBackend("vulkan", scene, qualityTier, normalizedTag);

        writeDiagnosticImage(openGl, openGlPng);
        writeDiagnosticImage(vulkan, vulkanPng);
        double diff = normalizedImageDiff(openGlPng, vulkanPng);
        return new CompareReport(openGlPng, vulkanPng, diff, openGl, vulkan);
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "default";
        }
        return tag.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
    }

    private static BackendSnapshot renderBackend(
            String backendId,
            SceneDescriptor scene,
            QualityTier qualityTier,
            String profileTag
    ) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        EngineConfig config = configFor(backendId, qualityTier);
        boolean taaStress = profileTag.contains("taa-disocclusion-stress")
                || profileTag.contains("taa-reactive-authored-stress")
                || profileTag.contains("taa-thin-geometry-shimmer")
                || profileTag.contains("taa-specular-flicker");
        EngineInput input = taaStress
                ? new EngineInput(540, 360, 96, -48, false, false, Set.of(KeyCode.A, KeyCode.D), 0.0)
                : new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0.0);
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(config, new NoopCallbacks());
            runtime.loadScene(scene);
            EngineFrameResult frame = null;
            int frames = taaStress ? 5 : 1;
            for (int i = 0; i < frames; i++) {
                runtime.update(1.0 / 60.0, input);
                frame = runtime.render();
            }
            var stats = runtime.getStats();
            return new BackendSnapshot(
                    backendId,
                    stats.drawCalls(),
                    stats.triangles(),
                    stats.visibleObjects(),
                    stats.cpuFrameMs(),
                    stats.gpuFrameMs(),
                    frame.warnings().size(),
                    frame.warnings().stream().map(w -> w.code()).sorted().toList()
            );
        }
    }

    private static EngineConfig configFor(String backendId, QualityTier qualityTier) {
        Map<String, String> options = switch (backendId) {
            case "opengl" -> Map.of(
                    "opengl.mockContext", System.getProperty("dle.compare.opengl.mockContext", "true"),
                    "opengl.taaDebugView", System.getProperty("dle.taa.debugView", "0")
            );
            case "vulkan" -> Map.of(
                    "vulkan.mockContext", System.getProperty("dle.compare.vulkan.mockContext", "true"),
                    "vulkan.postOffscreen", System.getProperty("dle.compare.vulkan.postOffscreen", "true"),
                    "vulkan.taaDebugView", System.getProperty("dle.taa.debugView", "0")
            );
            default -> Map.of();
        };
        return new EngineConfig(
                backendId,
                "DynamicLightEngine Compare Harness",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                Path.of("assets"),
                options
        );
    }

    private static void writeDiagnosticImage(BackendSnapshot snapshot, Path outputFile) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int bg = "vulkan".equals(snapshot.backendId()) ? rgba(25, 38, 64) : rgba(40, 45, 32);
        fillRect(image, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, bg);

        int barY = 30;
        int barH = 18;
        int w = IMAGE_WIDTH - 32;
        drawBar(image, 16, barY, w, barH, ratio(snapshot.drawCalls(), 20), rgba(110, 200, 255));
        drawBar(image, 16, barY + 26, w, barH, ratio(snapshot.triangles(), 5000), rgba(120, 255, 170));
        drawBar(image, 16, barY + 52, w, barH, ratio(snapshot.visibleObjects(), 20), rgba(255, 210, 120));
        drawBar(image, 16, barY + 78, w, barH, ratio(Math.round(snapshot.cpuFrameMs() * 100), 3000), rgba(255, 130, 130));
        drawBar(image, 16, barY + 104, w, barH, ratio(Math.round(snapshot.gpuFrameMs() * 100), 3000), rgba(200, 150, 255));
        drawBar(image, 16, barY + 130, w, 10, ratio(snapshot.warningCount(), 8), rgba(230, 230, 230));

        ImageIO.write(image, "png", outputFile.toFile());
    }

    private static void drawBar(BufferedImage image, int x, int y, int w, int h, double ratio, int rgb) {
        fillRect(image, x, y, w, h, rgba(18, 18, 18));
        fillRect(image, x, y, (int) Math.round(w * ratio), h, rgb);
    }

    private static void fillRect(BufferedImage image, int x, int y, int w, int h, int rgb) {
        int x1 = Math.max(0, x);
        int y1 = Math.max(0, y);
        int x2 = Math.min(image.getWidth(), x + Math.max(0, w));
        int y2 = Math.min(image.getHeight(), y + Math.max(0, h));
        for (int py = y1; py < y2; py++) {
            for (int px = x1; px < x2; px++) {
                image.setRGB(px, py, rgb);
            }
        }
    }

    private static int rgba(int r, int g, int b) {
        return (0xFF << 24)
                | ((Math.max(0, Math.min(255, r)) & 0xFF) << 16)
                | ((Math.max(0, Math.min(255, g)) & 0xFF) << 8)
                | (Math.max(0, Math.min(255, b)) & 0xFF);
    }

    private static double ratio(long value, long maxValue) {
        if (maxValue <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) value / (double) maxValue));
    }

    private static double normalizedImageDiff(Path a, Path b) throws IOException {
        BufferedImage ia = ImageIO.read(a.toFile());
        BufferedImage ib = ImageIO.read(b.toFile());
        if (ia.getWidth() != ib.getWidth() || ia.getHeight() != ib.getHeight()) {
            return 1.0;
        }
        long accum = 0;
        int pixelCount = ia.getWidth() * ia.getHeight();
        for (int y = 0; y < ia.getHeight(); y++) {
            for (int x = 0; x < ia.getWidth(); x++) {
                int pa = ia.getRGB(x, y);
                int pb = ib.getRGB(x, y);
                int dr = Math.abs(((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF));
                int dg = Math.abs(((pa >> 8) & 0xFF) - ((pb >> 8) & 0xFF));
                int db = Math.abs((pa & 0xFF) - (pb & 0xFF));
                accum += dr + dg + db;
            }
        }
        return accum / (pixelCount * 3.0 * 255.0);
    }

    record CompareReport(
            Path openGlImage,
            Path vulkanImage,
            double diffMetric,
            BackendSnapshot openGlSnapshot,
            BackendSnapshot vulkanSnapshot
    ) {
    }

    record BackendSnapshot(
            String backendId,
            long drawCalls,
            long triangles,
            long visibleObjects,
            double cpuFrameMs,
            double gpuFrameMs,
            int warningCount,
            java.util.List<String> warningCodes
    ) {
    }

    private static final class NoopCallbacks implements EngineHostCallbacks {
        @Override
        public void onEvent(org.dynamislight.api.event.EngineEvent event) {
        }

        @Override
        public void onLog(org.dynamislight.api.logging.LogMessage message) {
        }

        @Override
        public void onError(org.dynamislight.api.error.EngineErrorReport error) {
        }
    }
}
