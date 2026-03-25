package org.dynamisengine.light.impl.opengl;

import org.dynamisengine.debug.api.draw.DebugDrawCommand;
import org.dynamisengine.debug.api.draw.DebugDrawConsumer;

import java.util.List;

/**
 * Adapter that bridges the {@link DebugDrawConsumer} SPI to the OpenGL engine runtime.
 *
 * <p>This keeps the debug integration contract separate from the runtime type itself,
 * matching the pattern used for telemetry adapters elsewhere in the engine.
 */
public final class OpenGlDebugDrawConsumer implements DebugDrawConsumer {

    private final OpenGlEngineRuntime runtime;

    public OpenGlDebugDrawConsumer(OpenGlEngineRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void renderDebugDraw(List<DebugDrawCommand> commands) {
        runtime.submitDebugDrawCommands(commands);
    }

    @Override
    public boolean isDebugDrawEnabled() {
        return runtime.isDebugDrawEnabled();
    }

    @Override
    public void setDebugDrawEnabled(boolean enabled) {
        runtime.setDebugDrawEnabled(enabled);
    }
}
