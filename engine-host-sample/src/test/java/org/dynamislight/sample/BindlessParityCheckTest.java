package org.dynamislight.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class BindlessParityCheckTest {
    private static final Pattern PARITY_PATTERN = Pattern.compile(
            ".*\\[BINDLESS_PARITY\\].*drawCount=(\\d+).*streamHash=([0-9]+).*"
    );
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(90);

    @Test
    @EnabledIfSystemProperty(named = "dle.bindless.parity.tests", matches = "true")
    void bindlessParityMatchesLegacyAndHasNoStaleHandleLogs() throws Exception {
        RunResult legacy = runSample(false);
        RunResult bindless = runSample(true);

        assertFalse(
                legacy.parityFrames().isEmpty(),
                "Legacy run emitted no [BINDLESS_PARITY] lines:\n" + legacy.fullOutput()
        );
        assertFalse(
                bindless.parityFrames().isEmpty(),
                "Bindless run emitted no [BINDLESS_PARITY] lines:\n" + bindless.fullOutput()
        );
        assertEquals(
                legacy.parityFrames().size(),
                bindless.parityFrames().size(),
                "Parity frame count mismatch:\nlegacy=" + legacy.parityFrames() + "\nbindless=" + bindless.parityFrames()
        );
        assertEquals(
                legacy.parityFrames(),
                bindless.parityFrames(),
                "Parity mismatch:\nlegacy=" + legacy.parityFrames() + "\nbindless=" + bindless.parityFrames()
        );

        assertFalse(
                bindless.fullOutput().contains("[BINDLESS_HEAP] stale_handle"),
                "Bindless run reported stale heap handle(s):\n" + bindless.fullOutput()
        );
    }

    private static RunResult runSample(boolean bindlessEnabled) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-cp");
        command.add(runtimeClasspath());
        command.add("-Dvk.bindless.enabled=" + bindlessEnabled);
        command.add("-Ddle.vulkan.mockContext=true");
        command.add("org.dynamislight.sample.SampleHostApp");
        command.add("vulkan");
        command.add("--frames=10");
        command.add("--mesh=meshes/box.glb");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoRoot().toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        Instant deadline = Instant.now().plus(PROCESS_TIMEOUT);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
                if (Instant.now().isAfter(deadline)) {
                    process.destroyForcibly();
                    throw new AssertionError("Timed out waiting for SampleHostApp output");
                }
            }
        }

        boolean exited = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(exited, "SampleHostApp did not exit in time");
        int exitCode = process.exitValue();
        assertEquals(0, exitCode, "SampleHostApp failed with exit code " + exitCode + ":\n" + output);

        return new RunResult(parseParityFrames(output.toString()), output.toString());
    }

    private static List<String> parseParityFrames(String output) {
        List<String> frames = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Matcher matcher = PARITY_PATTERN.matcher(line);
            if (matcher.matches()) {
                frames.add(matcher.group(1) + ":" + matcher.group(2));
            }
        }
        return frames;
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String runtimeClasspath() {
        Path root = repoRoot();
        List<String> entries = new ArrayList<>();
        entries.add(root.resolve("engine-host-sample/target/classes").toString());
        entries.add(root.resolve("engine-impl-vulkan/target/classes").toString());
        entries.add(root.resolve("engine-impl-opengl/target/classes").toString());
        entries.add(root.resolve("engine-impl-common/target/classes").toString());
        entries.add(root.resolve("engine-spi/target/classes").toString());
        entries.add(root.resolve("engine-api/target/classes").toString());
        Arrays.stream(System.getProperty("java.class.path", "").split(Pattern.quote(File.pathSeparator)))
                .filter(entry -> !entry.isBlank())
                .filter(entry -> !isDynamicLightEngineBinary(entry))
                .forEach(entries::add);
        appendIfExists(entries, Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "vectrix", "vectrix", "1.10.9", "vectrix-1.10.9.jar"));
        appendIfExists(entries, Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "animis", "animis-runtime", "1.0.0", "animis-runtime-1.0.0.jar"));
        appendIfExists(entries, Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "meshforge", "meshforge", "1.1.0", "meshforge-1.1.0.jar"));
        appendIfExists(entries, Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "meshforge", "meshforge-loader", "1.1.0", "meshforge-loader-1.1.0.jar"));
        appendIfExists(entries, Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "dynamiscollision", "dynamiscollision", "1.0.0", "dynamiscollision-1.0.0.jar"));
        return String.join(File.pathSeparator, entries);
    }

    private static void appendIfExists(List<String> entries, Path path) {
        if (Files.isRegularFile(path)) {
            entries.add(path.toString());
        }
    }

    private static boolean isDynamicLightEngineBinary(String entry) {
        String normalized = entry.replace('\\', '/');
        if (normalized.contains("/org/dynamislight/engine-")) {
            return true;
        }
        return normalized.endsWith("/engine-api/target/classes")
                || normalized.endsWith("/engine-api/target/test-classes")
                || normalized.endsWith("/engine-spi/target/classes")
                || normalized.endsWith("/engine-spi/target/test-classes")
                || normalized.endsWith("/engine-impl-common/target/classes")
                || normalized.endsWith("/engine-impl-common/target/test-classes")
                || normalized.endsWith("/engine-impl-opengl/target/classes")
                || normalized.endsWith("/engine-impl-opengl/target/test-classes")
                || normalized.endsWith("/engine-impl-vulkan/target/classes")
                || normalized.endsWith("/engine-impl-vulkan/target/test-classes")
                || normalized.endsWith("/engine-host-sample/target/classes")
                || normalized.endsWith("/engine-host-sample/target/test-classes");
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("assets")) && Files.isDirectory(cwd.resolve("engine-host-sample"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("assets")) && Files.isDirectory(parent.resolve("engine-host-sample"))) {
            return parent;
        }
        return cwd;
    }

    private record RunResult(List<String> parityFrames, String fullOutput) {
    }
}
