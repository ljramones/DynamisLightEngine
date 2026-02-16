package org.dynamislight.api.event;

/**
 * DeviceLostEvent represents an event triggered when a device is lost or becomes unavailable.
 * It implements the EngineEvent interface and contains information about the associated backend.
 *
 * @param backendId the identifier of the backend where the device was lost.
 */
public record DeviceLostEvent(String backendId) implements EngineEvent {
}
