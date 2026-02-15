package org.dynamislight.impl.opengl;

import java.util.Set;
import org.dynamislight.api.EngineCapabilities;
import org.dynamislight.api.EngineConfig;
import org.dynamislight.api.EngineErrorCode;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.QualityTier;
import org.dynamislight.impl.common.AbstractEngineRuntime;

public final class OpenGlEngineRuntime extends AbstractEngineRuntime {
    private final OpenGlContext context = new OpenGlContext();
    private boolean mockContext;

    public OpenGlEngineRuntime() {
        super(
                "OpenGL",
                new EngineCapabilities(
                        Set.of("opengl"),
                        true,
                        false,
                        false,
                        false,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH)
                ),
                16.6,
                8.3
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        String mock = config.backendOptions().getOrDefault("opengl.mockContext", "false");
        mockContext = Boolean.parseBoolean(mock);
        if (Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced OpenGL init failure", false);
        }
        if (mockContext) {
            return;
        }
        context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), config.vsyncEnabled());
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        if (mockContext) {
            return null;
        }
        OpenGlContext.OpenGlFrameMetrics frame = context.renderFrame();
        return renderMetrics(
                frame.cpuFrameMs(),
                frame.gpuFrameMs(),
                frame.drawCalls(),
                frame.triangles(),
                frame.visibleObjects(),
                frame.gpuMemoryBytes()
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        if (!mockContext) {
            context.resize(widthPx, heightPx);
        }
    }

    @Override
    protected void onShutdown() {
        if (!mockContext) {
            context.shutdown();
        }
    }
}
