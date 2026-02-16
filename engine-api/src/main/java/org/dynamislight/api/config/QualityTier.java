package org.dynamislight.api.config;

/**
 * Represents various tiers for rendering quality.
 *
 * This enumeration is used to specify the desired level of rendering quality
 * in engine configurations such as {@code EngineConfig}. The rendering quality tier
 * influences factors such as graphical fidelity, performance, and resource usage.
 *
 * The following tiers are defined:
 * - LOW: Represents the lowest quality level, typically optimized for maximum performance
 *   on low-spec systems.
 * - MEDIUM: Represents a balanced quality level with moderate graphical fidelity
 *   and performance.
 * - HIGH: Represents a high-quality level with enhanced visual fidelity and
 *   resource usage.
 * - ULTRA: Represents the highest quality level, offering the best graphical fidelity
 *   but requiring significant system resources.
 */
public enum QualityTier {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA
}
