package org.dynamislight.api.event;

/**
 * DeviceLostEvent API type.
 */
public record DeviceLostEvent(String backendId) implements EngineEvent {
}
