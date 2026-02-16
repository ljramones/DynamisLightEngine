package org.dynamislight.api.runtime;

/**
 * Opaque frame handle returned by {@link EngineRuntime#render()}.
 *
 * <p>Ownership remains with the runtime. The handle is valid until the next
 * render call unless {@code persistent} is {@code true}.</p>
 */
public record FrameHandle(long id, boolean persistent) {
}
