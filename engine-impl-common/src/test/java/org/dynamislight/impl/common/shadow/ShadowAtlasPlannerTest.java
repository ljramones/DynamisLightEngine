package org.dynamislight.impl.common.shadow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShadowAtlasPlannerTest {
    @Test
    void plansDescendingSizeWithoutOverlap() {
        ShadowAtlasPlanner.PlanResult plan = ShadowAtlasPlanner.plan(
                1024,
                List.of(
                        new ShadowAtlasPlanner.Request("spotA", 512, 10),
                        new ShadowAtlasPlanner.Request("spotB", 256, 10),
                        new ShadowAtlasPlanner.Request("spotC", 256, 10),
                        new ShadowAtlasPlanner.Request("spotD", 128, 10)
                ),
                Map.of()
        );

        assertEquals(1024, plan.atlasSizePx());
        assertEquals(4, plan.allocations().size());
        assertTrue(plan.utilization() > 0.0f);
        assertTrue(plan.evictedIds().isEmpty());

        // Quick overlap check.
        for (int i = 0; i < plan.allocations().size(); i++) {
            ShadowAtlasPlanner.Allocation a = plan.allocations().get(i);
            for (int j = i + 1; j < plan.allocations().size(); j++) {
                ShadowAtlasPlanner.Allocation b = plan.allocations().get(j);
                boolean overlap = a.xPx() < b.xPx() + b.tileSizePx()
                        && a.xPx() + a.tileSizePx() > b.xPx()
                        && a.yPx() < b.yPx() + b.tileSizePx()
                        && a.yPx() + a.tileSizePx() > b.yPx();
                assertFalse(overlap, "shadow atlas allocations must not overlap");
            }
        }
    }

    @Test
    void retainsMatchingExistingPlacements() {
        Map<String, ShadowAtlasPlanner.ExistingAllocation> existing = new HashMap<>();
        existing.put("spotA", new ShadowAtlasPlanner.ExistingAllocation("spotA", 0, 0, 512, 100));
        existing.put("spotB", new ShadowAtlasPlanner.ExistingAllocation("spotB", 512, 0, 512, 90));

        ShadowAtlasPlanner.PlanResult plan = ShadowAtlasPlanner.plan(
                1024,
                List.of(
                        new ShadowAtlasPlanner.Request("spotA", 512, 101),
                        new ShadowAtlasPlanner.Request("spotB", 512, 101)
                ),
                existing
        );

        assertEquals(2, plan.allocations().size());
        assertTrue(plan.allocations().stream().anyMatch(a -> a.id().equals("spotA") && a.xPx() == 0 && a.yPx() == 0));
        assertTrue(plan.allocations().stream().anyMatch(a -> a.id().equals("spotB") && a.xPx() == 512 && a.yPx() == 0));
    }

    @Test
    void evictsLeastRecentlyVisibleWhenUnderPressure() {
        Map<String, ShadowAtlasPlanner.ExistingAllocation> existing = new HashMap<>();
        existing.put("oldA", new ShadowAtlasPlanner.ExistingAllocation("oldA", 0, 0, 512, 1));
        existing.put("oldB", new ShadowAtlasPlanner.ExistingAllocation("oldB", 512, 0, 512, 2));
        existing.put("oldC", new ShadowAtlasPlanner.ExistingAllocation("oldC", 0, 512, 512, 3));
        existing.put("oldD", new ShadowAtlasPlanner.ExistingAllocation("oldD", 512, 512, 512, 4));

        ShadowAtlasPlanner.PlanResult plan = ShadowAtlasPlanner.plan(
                1024,
                List.of(new ShadowAtlasPlanner.Request("newSpot", 512, 10)),
                existing
        );

        assertEquals(1, plan.allocations().size());
        assertEquals("newSpot", plan.allocations().getFirst().id());
        assertFalse(plan.evictedIds().isEmpty());
        assertEquals("oldA", plan.evictedIds().getFirst());
    }
}
