package org.dynamislight.api.resource;

/**
 * Represents a unique identifier for a resource within the system.
 *
 * This record encapsulates a string-based value that serves as an immutable
 * identifier for resources managed during runtime. It enforces validation to ensure
 * that the identifier is non-null and non-blank, preventing invalid identifiers
 * from being created.
 *
 * Constructor Requirements:
 * - The provided value must not be null or consist solely of whitespace.
 * - An {@link IllegalArgumentException} is thrown if the validation fails.
 *
 * Purpose:
 * - The {@code ResourceId} is utilized as a fundamental entity in resource
 *   tracking and management, ensuring that each resource in the system has
 *   a globally unique identifier.
 */
public record ResourceId(String value) {
    /**
     * Constructs a new {@code ResourceId} instance.
     *
     * This constructor validates that the provided value is neither null nor blank.
     * It ensures that the {@code ResourceId} instance is instantiated with a valid,
     * non-empty string representation.
     *
     * @param value The unique string identifier for the resource.
     *              This identifier must be non-null and cannot be a blank string.
     * @throws IllegalArgumentException If the provided value is null or blank.
     */
    public ResourceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ResourceId value is required");
        }
    }
}
