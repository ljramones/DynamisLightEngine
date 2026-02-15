package org.dynamislight.api;

/**
 * DeviceLostEvent API type.
 */
public record DeviceLostEvent(String backendId) implements EngineEvent {
}
