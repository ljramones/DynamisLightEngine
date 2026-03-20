package org.dynamisengine.light.impl.common.mesh;

/**
 * Thrown when mesh loading fails.
 */
public final class MeshLoadException extends Exception {

    public MeshLoadException(String message) {
        super(message);
    }

    public MeshLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
