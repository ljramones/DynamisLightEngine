package org.dynamislight.demos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.dynamislight.api.scene.SceneDescriptor;

final class ThresholdReplayDemo implements DemoDefinition {
    private static final String DEFAULT_THRESHOLDS_FILE = "../engine-host-sample/src/test/resources/thresholds/vulkan-real.properties";

    @Override
    public String id() {
        return "threshold-replay";
    }

    @Override
    public String description() {
        return "Load threshold profile and emit replay verdict from CLI inputs.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        emitThresholdReplaySummary(request);
        return DemoScenes.sceneWithAa("taa", true, 0.80f, 1.0f);
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        String prefix = request.backendId().toLowerCase(java.util.Locale.ROOT);
        return Map.of(prefix + ".aaPreset", "stability");
    }

    private static void emitThresholdReplaySummary(DemoRequest request) {
        Path thresholdsPath = resolveThresholdPath(request.arg("thresholds-file", DEFAULT_THRESHOLDS_FILE));
        Properties thresholds = loadThresholdProperties(thresholdsPath);
        String key = request.arg("threshold-key", "threshold.post-process-high");
        float observedDiff = request.argFloat("observed-diff", -1.0f, -1.0f, 10.0f);

        long thresholdCount = thresholds.stringPropertyNames().stream()
                .filter(name -> name.startsWith("threshold."))
                .count();

        System.out.println("threshold-replay source=" + thresholdsPath + " loaded=" + thresholdCount);

        String rawThreshold = thresholds.getProperty(key);
        if (rawThreshold == null) {
            System.out.println("threshold-replay verdict=UNKNOWN reason=missing-key key=" + key);
            return;
        }

        float thresholdValue;
        try {
            thresholdValue = Float.parseFloat(rawThreshold);
        } catch (NumberFormatException ex) {
            System.out.println("threshold-replay verdict=UNKNOWN reason=invalid-threshold key=" + key + " value=" + rawThreshold);
            return;
        }

        if (observedDiff < 0.0f) {
            System.out.printf(java.util.Locale.ROOT, "threshold-replay verdict=READY key=%s threshold=%.6f observed=unset%n", key, thresholdValue);
            return;
        }

        String verdict = observedDiff <= thresholdValue ? "PASS" : "FAIL";
        System.out.printf(
                java.util.Locale.ROOT,
                "threshold-replay verdict=%s key=%s threshold=%.6f observed=%.6f margin=%.6f%n",
                verdict,
                key,
                thresholdValue,
                observedDiff,
                thresholdValue - observedDiff
        );
    }

    private static Properties loadThresholdProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(path)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ignored) {
            return new Properties();
        }
        return properties;
    }

    private static Path resolveThresholdPath(String rawPath) {
        Path path = Path.of(rawPath);
        if (Files.isRegularFile(path)) {
            return path;
        }
        if (!path.isAbsolute()) {
            Path fromModuleParent = Path.of("..").resolve(path).normalize();
            if (Files.isRegularFile(fromModuleParent)) {
                return fromModuleParent;
            }
        }
        return path;
    }
}
