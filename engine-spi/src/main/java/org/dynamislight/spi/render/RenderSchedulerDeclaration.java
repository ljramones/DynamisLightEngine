package org.dynamislight.spi.render;

import java.util.List;

/**
 * Scheduler declaration for capability-owned work selection.
 *
 * @param schedulerId stable scheduler identifier
 * @param budgetParameters exposed budget controls
 * @param affectsPassCount true when scheduler changes pass count
 * @param affectsPassActivation true when scheduler toggles pass activation
 */
public record RenderSchedulerDeclaration(
        String schedulerId,
        List<RenderBudgetParameter> budgetParameters,
        boolean affectsPassCount,
        boolean affectsPassActivation
) {
    public RenderSchedulerDeclaration {
        schedulerId = schedulerId == null ? "" : schedulerId.trim();
        budgetParameters = budgetParameters == null ? List.of() : List.copyOf(budgetParameters);
    }
}
