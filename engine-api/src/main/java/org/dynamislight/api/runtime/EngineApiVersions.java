package org.dynamislight.api.runtime;

/**
 * Compatibility helpers for engine API versions.
 */
public final class EngineApiVersions {
    private EngineApiVersions() {
    }

    public static boolean isRuntimeCompatible(EngineApiVersion hostRequired, EngineApiVersion runtimeVersion) {
        if (hostRequired == null || runtimeVersion == null) {
            return false;
        }
        if (hostRequired.major() != runtimeVersion.major()) {
            return false;
        }
        if (runtimeVersion.minor() < hostRequired.minor()) {
            return false;
        }
        return true;
    }
}
