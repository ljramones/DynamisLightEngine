package org.dynamislight.api;

/**
 * EngineException API type.
 */
public final class EngineException extends Exception {
    private final EngineErrorCode code;
    private final boolean recoverable;

    public EngineException(EngineErrorCode code, String message, boolean recoverable) {
        super(message);
        this.code = code;
        this.recoverable = recoverable;
    }

    public EngineErrorCode code() {
        return code;
    }

    public boolean recoverable() {
        return recoverable;
    }
}
