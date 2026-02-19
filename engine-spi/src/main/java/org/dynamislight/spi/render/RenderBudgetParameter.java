package org.dynamislight.spi.render;

/**
 * Scheduler budget parameter metadata.
 *
 * @param name stable parameter name
 * @param description concise meaning
 */
public record RenderBudgetParameter(String name, String description) {
    public RenderBudgetParameter {
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
    }
}
