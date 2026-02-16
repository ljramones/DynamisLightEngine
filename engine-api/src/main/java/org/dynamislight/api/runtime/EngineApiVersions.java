package org.dynamislight.api.runtime;

/**
 * Utility class for calculating and verifying compatibility between engine API versions.
 *
 * This class provides methods to determine if a runtime engine version meets the
 * API requirements of a host, following rules based on major and minor version numbers.
 */
public final class EngineApiVersions {
    private EngineApiVersions() {
    }

    /**
     * Determines whether the runtime engine version is compatible with the host's required API version.
     *
     * Compatibility is checked based on the following rules:
     * - The major version of both the host requirement and runtime version must match.
     * - The minor version of the runtime must be greater than or equal to the host's required minor version.
     *
     * @param hostRequired the API version required by the host; must not be null
     * @param runtimeVersion the API version provided by the runtime; must not be null
     * @return {@code true} if the runtime version is compatible with the host's required version, {@code false} otherwise
     */
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
