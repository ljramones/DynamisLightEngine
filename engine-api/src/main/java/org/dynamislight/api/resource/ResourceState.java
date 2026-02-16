package org.dynamislight.api.resource;

/**
 * Enum representing the load state of a resource in the system.
 *
 * This enumeration is used to track the current runtime state of a resource. It provides
 * insight into whether the resource is ready for use, has been successfully loaded, or
 * encountered a failure during loading.
 *
 * States:
 * - {@code UNLOADED}:
 *   Indicates that the resource has not been loaded into memory yet and is unavailable
 *   for use.
 *
 * - {@code LOADED}:
 *   Represents a successfully loaded resource that is ready for consumption within the
 *   system.
 *
 * - {@code FAILED}:
 *   Denotes a resource that failed to load due to errors, such as missing files or invalid
 *   data. Additional diagnostics may be required to resolve the issue.
 */
public enum ResourceState {
    UNLOADED,
    LOADED,
    FAILED
}
