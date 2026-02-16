package org.dynamislight.api.runtime;

/**
 * Represents the version of the engine API, consisting of major, minor, and patch components.
 *
 * <ul>
 *   <li><b>Major:</b> Indicates breaking changes or significant updates to the API that are not
 *       backward-compatible.</li>
 *   <li><b>Minor:</b> Represents new features or extensions to the API that are backward-compatible
 *       with the same major version.</li>
 *   <li><b>Patch:</b> Specifies bug fixes or small changes that do not affect the overall
 *       compatibility of the API.</li>
 * </ul>
 *
 * This type is used to capture and compare the API version requirements and capabilities
 * between different components of the engine (e.g., runtime and host).
 */
public record EngineApiVersion(int major, int minor, int patch) {
}
