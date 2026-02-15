package org.dynamislight.spi.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineCapabilities;
import org.dynamislight.api.EngineConfig;
import org.dynamislight.api.EngineErrorCode;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.EngineFrameResult;
import org.dynamislight.api.EngineHostCallbacks;
import org.dynamislight.api.EngineInput;
import org.dynamislight.api.EngineRuntime;
import org.dynamislight.api.EngineStats;
import org.dynamislight.api.FrameHandle;
import org.dynamislight.api.QualityTier;
import org.dynamislight.api.SceneDescriptor;
import org.dynamislight.spi.EngineBackendInfo;
import org.dynamislight.spi.EngineBackendProvider;
import org.junit.jupiter.api.Test;

class BackendRegistryTest {
    @Test
    void resolvesProviderById() throws Exception {
        BackendRegistry registry = new BackendRegistry(List.of(
                provider("opengl", 1, 0),
                provider("vulkan", 1, 0)
        ));

        EngineBackendProvider resolved = registry.resolve("vulkan", new EngineApiVersion(1, 0, 0));

        assertEquals("vulkan", resolved.backendId());
    }

    @Test
    void throwsBackendNotFoundWhenMissing() {
        BackendRegistry registry = new BackendRegistry(List.of(provider("opengl", 1, 0)));

        EngineException ex = assertThrows(EngineException.class,
                () -> registry.resolve("metal", new EngineApiVersion(1, 0, 0)));

        assertEquals(EngineErrorCode.BACKEND_NOT_FOUND, ex.code());
    }

    @Test
    void throwsInternalErrorOnDuplicateBackendIds() {
        BackendRegistry registry = new BackendRegistry(List.of(
                provider("opengl", 1, 0),
                provider("opengl", 1, 0)
        ));

        EngineException ex = assertThrows(EngineException.class,
                () -> registry.resolve("opengl", new EngineApiVersion(1, 0, 0)));

        assertEquals(EngineErrorCode.INTERNAL_ERROR, ex.code());
    }

    @Test
    void throwsInvalidArgumentWhenApiVersionIncompatible() {
        BackendRegistry registry = new BackendRegistry(List.of(provider("opengl", 1, 0)));

        EngineException ex = assertThrows(EngineException.class,
                () -> registry.resolve("opengl", new EngineApiVersion(1, 2, 0)));

        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    private static EngineBackendProvider provider(String id, int major, int minor) {
        return new EngineBackendProvider() {
            @Override
            public String backendId() {
                return id;
            }

            @Override
            public EngineApiVersion supportedApiVersion() {
                return new EngineApiVersion(major, minor, 0);
            }

            @Override
            public EngineBackendInfo info() {
                return new EngineBackendInfo(id, id.toUpperCase(), "test", "test provider");
            }

            @Override
            public EngineRuntime createRuntime() {
                return new EngineRuntime() {
                    @Override
                    public EngineApiVersion apiVersion() {
                        return new EngineApiVersion(1, 0, 0);
                    }

                    @Override
                    public void initialize(EngineConfig config, EngineHostCallbacks host) {
                    }

                    @Override
                    public void loadScene(SceneDescriptor scene) {
                    }

                    @Override
                    public EngineFrameResult update(double dtSeconds, EngineInput input) {
                        return new EngineFrameResult(0, 0, 0, new FrameHandle(0, false), List.of());
                    }

                    @Override
                    public EngineFrameResult render() {
                        return new EngineFrameResult(0, 0, 0, new FrameHandle(0, false), List.of());
                    }

                    @Override
                    public void resize(int widthPx, int heightPx, float dpiScale) {
                    }

                    @Override
                    public EngineStats getStats() {
                        return new EngineStats(0, 0, 0, 0, 0, 0, 0);
                    }

                    @Override
                    public EngineCapabilities getCapabilities() {
                        return new EngineCapabilities(Set.of(id), false, false, false, false, 1, 1, Set.of(QualityTier.LOW));
                    }

                    @Override
                    public void shutdown() {
                    }
                };
            }
        };
    }
}
