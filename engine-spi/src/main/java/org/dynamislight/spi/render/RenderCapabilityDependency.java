package org.dynamislight.spi.render;

/**
 * Declares a dependency on another capability or produced resource.
 *
 * @param capabilityId target capability ID
 * @param resourceName target resource name
 * @param required whether dependency is required for activation
 */
public record RenderCapabilityDependency(
        String capabilityId,
        String resourceName,
        boolean required
) {
    public RenderCapabilityDependency {
        capabilityId = capabilityId == null ? "" : capabilityId.trim();
        resourceName = resourceName == null ? "" : resourceName.trim();
    }
}
