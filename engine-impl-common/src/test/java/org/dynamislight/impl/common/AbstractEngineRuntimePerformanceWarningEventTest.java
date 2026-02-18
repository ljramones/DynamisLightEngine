package org.dynamislight.impl.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.event.PerformanceWarningEvent;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.junit.jupiter.api.Test;

class AbstractEngineRuntimePerformanceWarningEventTest {

    @Test
    void renderEmitsPerformanceWarningEventOnlyForEligibleWarnings() throws Exception {
        var runtime = new TestRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(), callbacks);

        var frame = runtime.render();

        assertEquals(2, frame.warnings().size());
        long performanceEvents = callbacks.events.stream().filter(PerformanceWarningEvent.class::isInstance).count();
        assertEquals(1L, performanceEvents);
        var event = callbacks.events.stream()
                .filter(PerformanceWarningEvent.class::isInstance)
                .map(PerformanceWarningEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("ELIGIBLE_WARNING", event.warningCode());
        assertEquals("eligible", event.message());
        runtime.shutdown();
    }

    @Test
    void renderSkipsPerformanceWarningEventWhenNoWarningsAreEligible() throws Exception {
        var runtime = new TestRuntime(false);
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(), callbacks);

        var frame = runtime.render();

        assertFalse(frame.warnings().isEmpty());
        assertTrue(callbacks.events.stream().noneMatch(PerformanceWarningEvent.class::isInstance));
        runtime.shutdown();
    }

    private static EngineConfig validConfig() {
        return new EngineConfig(
                "test",
                "test-app",
                1280,
                720,
                1.0f,
                true,
                60,
                QualityTier.MEDIUM,
                Path.of("."),
                Map.of()
        );
    }

    private static final class TestRuntime extends AbstractEngineRuntime {
        private final boolean emitEligible;

        private TestRuntime() {
            this(true);
        }

        private TestRuntime(boolean emitEligible) {
            super(
                    "test-backend",
                    new EngineCapabilities(Set.of("test"), false, false, false, false, 4096, 4096, Set.of(QualityTier.MEDIUM)),
                    1.0,
                    1.0
            );
            this.emitEligible = emitEligible;
        }

        @Override
        protected List<EngineWarning> frameWarnings() {
            return List.of(
                    new EngineWarning("ELIGIBLE_WARNING", "eligible"),
                    new EngineWarning("INELIGIBLE_WARNING", "ineligible")
            );
        }

        @Override
        protected boolean shouldEmitPerformanceWarningEvent(EngineWarning warning) {
            return emitEligible && warning != null && "ELIGIBLE_WARNING".equals(warning.code());
        }
    }

    private static final class RecordingCallbacks implements EngineHostCallbacks {
        private final List<EngineEvent> events = new ArrayList<>();

        @Override
        public void onEvent(EngineEvent event) {
            events.add(event);
        }

        @Override
        public void onLog(LogMessage message) {
        }

        @Override
        public void onError(EngineErrorReport error) {
        }
    }
}
