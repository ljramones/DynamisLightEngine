package org.dynamislight.sample;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
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
        String vulkanModeTag = vulkanModeTag();
        String acceptanceProfile = acceptanceProfileTag();
        String aaMode = selectAaMode(normalizedTag);
        String aaPreset = selectAaPreset(normalizedTag, aaMode);

        BackendSnapshot openGl = renderBackend("opengl", scene, qualityTier, normalizedTag, aaPreset, aaMode);
        BackendSnapshot vulkan = renderBackend("vulkan", scene, qualityTier, normalizedTag, aaPreset, aaMode);

        writeDiagnosticImage(openGl, openGlPng);
        writeDiagnosticImage(vulkan, vulkanPng);
        openGl = openGl.withShimmerIndex(estimateShimmerIndex(openGlPng));
        vulkan = vulkan.withShimmerIndex(estimateShimmerIndex(vulkanPng));
        double diff = normalizedImageDiff(openGlPng, vulkanPng);
        writeModeMetadata(outputDir, normalizedTag, qualityTier, vulkanModeTag, acceptanceProfile, aaMode, aaPreset, diff, openGl, vulkan);
        return new CompareReport(openGlPng, vulkanPng, diff, openGl, vulkan);
    }

    private static String vulkanModeTag() {
        boolean vulkanMock = Boolean.parseBoolean(System.getProperty("dle.compare.vulkan.mockContext", "true"));
        return vulkanMock ? "vulkan_mock" : "vulkan_real";
    }

    private static String acceptanceProfileTag() {
        boolean vulkanMock = Boolean.parseBoolean(System.getProperty("dle.compare.vulkan.mockContext", "true"));
        return vulkanMock ? "fallback" : "strict";
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
            String profileTag,
            String aaPreset,
            String aaMode
    ) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        boolean taaStress = profileTag.contains("taa-disocclusion-stress")
                || profileTag.contains("taa-reactive-authored-stress")
                || profileTag.contains("taa-thin-geometry-shimmer")
                || profileTag.contains("taa-specular-flicker")
                || profileTag.contains("taa-history-confidence-stress")
                || profileTag.contains("taa-specular-aa-stress")
                || profileTag.contains("taa-reactive-authored-dense-stress")
                || profileTag.contains("taa-alpha-pan-stress")
                || profileTag.contains("taa-aa-preset-quality-stress")
                || profileTag.contains("taa-confidence-dilation-stress")
                || profileTag.contains("taa-subpixel-alpha-foliage-stress")
                || profileTag.contains("taa-specular-micro-highlights-stress")
                || profileTag.contains("taa-thin-geometry-motion-stress")
                || profileTag.contains("taa-disocclusion-rapid-pan-stress");
        boolean smaaStress = profileTag.contains("smaa-full-edge-crawl");
        EngineInput input = (taaStress || smaaStress)
                ? new EngineInput(540, 360, 96, -48, false, false, Set.of(KeyCode.A, KeyCode.D), 0.0)
                : new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0.0);
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(configFor(backendId, qualityTier, aaPreset, aaMode), new NoopCallbacks());
            runtime.loadScene(scene);
            EngineFrameResult frame = null;
            int frames = taaStress ? 5 : (smaaStress ? 3 : 1);
            double rejectMin = Double.POSITIVE_INFINITY;
            double rejectMax = Double.NEGATIVE_INFINITY;
            double confidenceMin = Double.POSITIVE_INFINITY;
            double confidenceMax = Double.NEGATIVE_INFINITY;
            long firstDropCount = Long.MIN_VALUE;
            long lastDropCount = 0L;
            for (int i = 0; i < frames; i++) {
                runtime.update(1.0 / 60.0, input);
                frame = runtime.render();
                var stats = runtime.getStats();
                rejectMin = Math.min(rejectMin, stats.taaHistoryRejectRate());
                rejectMax = Math.max(rejectMax, stats.taaHistoryRejectRate());
                confidenceMin = Math.min(confidenceMin, stats.taaConfidenceMean());
                confidenceMax = Math.max(confidenceMax, stats.taaConfidenceMean());
                if (firstDropCount == Long.MIN_VALUE) {
                    firstDropCount = stats.taaConfidenceDropEvents();
                }
                lastDropCount = stats.taaConfidenceDropEvents();
            }
            var stats = runtime.getStats();
            if (rejectMin == Double.POSITIVE_INFINITY) {
                rejectMin = stats.taaHistoryRejectRate();
                rejectMax = stats.taaHistoryRejectRate();
                confidenceMin = stats.taaConfidenceMean();
                confidenceMax = stats.taaConfidenceMean();
                firstDropCount = stats.taaConfidenceDropEvents();
                lastDropCount = stats.taaConfidenceDropEvents();
            }
            return new BackendSnapshot(
                    backendId,
                    stats.drawCalls(),
                    stats.triangles(),
                    stats.visibleObjects(),
                    stats.cpuFrameMs(),
                    stats.gpuFrameMs(),
                    0.0,
                    stats.taaHistoryRejectRate(),
                    stats.taaConfidenceMean(),
                    stats.taaConfidenceDropEvents(),
                    Math.max(0.0, rejectMax - rejectMin),
                    Math.max(0.0, confidenceMax - confidenceMin),
                    Math.max(0L, lastDropCount - firstDropCount),
                    frame.warnings().size(),
                    frame.warnings().stream().map(w -> w.code()).sorted().toList()
            );
        }
    }

    private static String selectAaPreset(String profileTag, String aaMode) {
        if ("tuua".equals(aaMode)) {
            return "quality";
        }
        if ("hybrid_tuua_msaa".equals(aaMode)) {
            return "quality";
        }
        if ("msaa_selective".equals(aaMode)) {
            return "stability";
        }
        if (profileTag.contains("taa-aa-preset-quality")) {
            return "quality";
        }
        if (profileTag.contains("taa-alpha-pan")) {
            return "stability";
        }
        return System.getProperty("dle.compare.aaPreset", "balanced");
    }

    private static String selectAaMode(String profileTag) {
        if (profileTag.contains("dl aa") || profileTag.contains("dlaa")) {
            return "dlaa";
        }
        if (profileTag.contains("fxaa")) {
            return "fxaa_low";
        }
        if (profileTag.contains("hybrid-tuua-msaa")) {
            return "hybrid_tuua_msaa";
        }
        if (profileTag.contains("msaa-selective")) {
            return "msaa_selective";
        }
        if (profileTag.contains("tsr")) {
            return "tsr";
        }
        if (profileTag.contains("tuua")) {
            return "tuua";
        }
        return "taa";
    }

    private static EngineConfig configFor(String backendId, QualityTier qualityTier, String aaPreset, String aaMode) {
        Map<String, String> options = switch (backendId) {
            case "opengl" -> Map.ofEntries(
                    Map.entry("opengl.mockContext", System.getProperty("dle.compare.opengl.mockContext", "true")),
                    Map.entry("opengl.taaDebugView", System.getProperty("dle.taa.debugView", "0")),
                    Map.entry("opengl.aaPreset", aaPreset),
                    Map.entry("opengl.aaMode", aaMode),
                    Map.entry("opengl.tsrHistoryWeight", System.getProperty("dle.compare.tsr.historyWeight", "0.90")),
                    Map.entry("opengl.tsrResponsiveMask", System.getProperty("dle.compare.tsr.responsiveMask", "0.65")),
                    Map.entry("opengl.tsrNeighborhoodClamp", System.getProperty("dle.compare.tsr.neighborhoodClamp", "0.88")),
                    Map.entry("opengl.tsrReprojectionConfidence", System.getProperty("dle.compare.tsr.reprojectionConfidence", "0.85")),
                    Map.entry("opengl.tsrSharpen", System.getProperty("dle.compare.tsr.sharpen", "0.14")),
                    Map.entry("opengl.tsrAntiRinging", System.getProperty("dle.compare.tsr.antiRinging", "0.75")),
                    Map.entry("opengl.tsrRenderScale", System.getProperty("dle.compare.tsr.renderScale", "0.60")),
                    Map.entry("opengl.tuuaRenderScale", System.getProperty("dle.compare.tuua.renderScale", "0.72"))
            );
            case "vulkan" -> Map.ofEntries(
                    Map.entry("vulkan.mockContext", System.getProperty("dle.compare.vulkan.mockContext", "true")),
                    Map.entry("vulkan.postOffscreen", System.getProperty("dle.compare.vulkan.postOffscreen", "true")),
                    Map.entry("vulkan.taaDebugView", System.getProperty("dle.taa.debugView", "0")),
                    Map.entry("vulkan.aaPreset", aaPreset),
                    Map.entry("vulkan.aaMode", aaMode),
                    Map.entry("vulkan.tsrHistoryWeight", System.getProperty("dle.compare.tsr.historyWeight", "0.90")),
                    Map.entry("vulkan.tsrResponsiveMask", System.getProperty("dle.compare.tsr.responsiveMask", "0.65")),
                    Map.entry("vulkan.tsrNeighborhoodClamp", System.getProperty("dle.compare.tsr.neighborhoodClamp", "0.88")),
                    Map.entry("vulkan.tsrReprojectionConfidence", System.getProperty("dle.compare.tsr.reprojectionConfidence", "0.85")),
                    Map.entry("vulkan.tsrSharpen", System.getProperty("dle.compare.tsr.sharpen", "0.14")),
                    Map.entry("vulkan.tsrAntiRinging", System.getProperty("dle.compare.tsr.antiRinging", "0.75")),
                    Map.entry("vulkan.tsrRenderScale", System.getProperty("dle.compare.tsr.renderScale", "0.60")),
                    Map.entry("vulkan.tuuaRenderScale", System.getProperty("dle.compare.tuua.renderScale", "0.72"))
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

    private static double estimateShimmerIndex(Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) {
            return 0.0;
        }
        double accum = 0.0;
        long samples = 0L;
        for (int y = 0; y < image.getHeight() - 1; y++) {
            for (int x = 0; x < image.getWidth() - 1; x++) {
                double luma = luma(image.getRGB(x, y));
                double lumaRight = luma(image.getRGB(x + 1, y));
                double lumaDown = luma(image.getRGB(x, y + 1));
                accum += Math.abs(luma - lumaRight) + Math.abs(luma - lumaDown);
                samples += 2L;
            }
        }
        return samples == 0L ? 0.0 : accum / samples;
    }

    private static double luma(int rgba) {
        double r = ((rgba >> 16) & 0xFF) / 255.0;
        double g = ((rgba >> 8) & 0xFF) / 255.0;
        double b = (rgba & 0xFF) / 255.0;
        return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
    }

    private static void writeModeMetadata(
            Path outputDir,
            String profileTag,
            QualityTier qualityTier,
            String vulkanMode,
            String acceptanceProfile,
            String aaMode,
            String aaPreset,
            double diffMetric,
            BackendSnapshot openGl,
            BackendSnapshot vulkan
    ) throws IOException {
        Properties metadata = new Properties();
        metadata.setProperty("compare.profileTag", profileTag);
        metadata.setProperty("compare.qualityTier", qualityTier.name());
        metadata.setProperty("compare.vulkan.mode", vulkanMode);
        metadata.setProperty("compare.aa.acceptanceProfile", acceptanceProfile);
        metadata.setProperty("compare.aa.mode", aaMode);
        metadata.setProperty("compare.aa.preset", aaPreset);
        metadata.setProperty("compare.diffMetric", Double.toString(diffMetric));
        metadata.setProperty("compare.opengl.shimmerIndex", Double.toString(openGl.shimmerIndex()));
        metadata.setProperty("compare.vulkan.shimmerIndex", Double.toString(vulkan.shimmerIndex()));
        metadata.setProperty("compare.opengl.taaHistoryRejectRate", Double.toString(openGl.taaHistoryRejectRate()));
        metadata.setProperty("compare.vulkan.taaHistoryRejectRate", Double.toString(vulkan.taaHistoryRejectRate()));
        metadata.setProperty("compare.opengl.taaConfidenceMean", Double.toString(openGl.taaConfidenceMean()));
        metadata.setProperty("compare.vulkan.taaConfidenceMean", Double.toString(vulkan.taaConfidenceMean()));
        metadata.setProperty("compare.opengl.taaConfidenceDropCount", Long.toString(openGl.taaConfidenceDropCount()));
        metadata.setProperty("compare.vulkan.taaConfidenceDropCount", Long.toString(vulkan.taaConfidenceDropCount()));
        metadata.setProperty("compare.opengl.taaRejectTrendWindow", Double.toString(openGl.taaRejectTrendWindow()));
        metadata.setProperty("compare.vulkan.taaRejectTrendWindow", Double.toString(vulkan.taaRejectTrendWindow()));
        metadata.setProperty("compare.opengl.taaConfidenceTrendWindow", Double.toString(openGl.taaConfidenceTrendWindow()));
        metadata.setProperty("compare.vulkan.taaConfidenceTrendWindow", Double.toString(vulkan.taaConfidenceTrendWindow()));
        metadata.setProperty("compare.opengl.taaConfidenceDropWindow", Long.toString(openGl.taaConfidenceDropWindow()));
        metadata.setProperty("compare.vulkan.taaConfidenceDropWindow", Long.toString(vulkan.taaConfidenceDropWindow()));
        try (var out = Files.newOutputStream(outputDir.resolve("compare-metadata.properties"))) {
            metadata.store(out, "DynamisLightEngine compare metadata");
        }
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
            double shimmerIndex,
            double taaHistoryRejectRate,
            double taaConfidenceMean,
            long taaConfidenceDropCount,
            double taaRejectTrendWindow,
            double taaConfidenceTrendWindow,
            long taaConfidenceDropWindow,
            int warningCount,
            java.util.List<String> warningCodes
    ) {
        BackendSnapshot withShimmerIndex(double value) {
            return new BackendSnapshot(
                    backendId,
                    drawCalls,
                    triangles,
                    visibleObjects,
                    cpuFrameMs,
                    gpuFrameMs,
                    value,
                    taaHistoryRejectRate,
                    taaConfidenceMean,
                    taaConfidenceDropCount,
                    taaRejectTrendWindow,
                    taaConfidenceTrendWindow,
                    taaConfidenceDropWindow,
                    warningCount,
                    warningCodes
            );
        }
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
