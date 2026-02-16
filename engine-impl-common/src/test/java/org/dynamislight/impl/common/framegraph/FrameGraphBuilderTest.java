package org.dynamislight.impl.common.framegraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FrameGraphBuilderTest {
    @Test
    void missingDependencyThrows() {
        FrameGraphBuilder builder = new FrameGraphBuilder()
                .addPass(pass("geometry", Set.of("missing"), () -> { }));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void dependencyCycleThrows() {
        FrameGraphBuilder builder = new FrameGraphBuilder()
                .addPass(pass("a", Set.of("b"), () -> { }))
                .addPass(pass("b", Set.of("a"), () -> { }));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void executesInTopologicalOrder() {
        List<String> order = new ArrayList<>();
        FrameGraph graph = new FrameGraphBuilder()
                .addPass(pass("clear", Set.of(), () -> order.add("clear")))
                .addPass(pass("geometry", Set.of("clear"), () -> order.add("geometry")))
                .addPass(pass("fog", Set.of("geometry"), () -> order.add("fog")))
                .build();

        new FrameGraphExecutor().execute(graph);

        assertEquals(List.of("clear", "geometry", "fog"), order);
    }

    @Test
    void readWriteHazardWithoutDependencyThrows() {
        FrameGraphBuilder builder = new FrameGraphBuilder()
                .addPass(pass("writer", Set.of(), Set.of(), Set.of("color"), () -> { }))
                .addPass(pass("reader", Set.of(), Set.of("color"), Set.of(), () -> { }));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void writeWriteHazardWithoutDependencyThrows() {
        FrameGraphBuilder builder = new FrameGraphBuilder()
                .addPass(pass("a", Set.of(), Set.of(), Set.of("depth"), () -> { }))
                .addPass(pass("b", Set.of(), Set.of(), Set.of("depth"), () -> { }));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void hazardWithDependencyIsAllowed() {
        FrameGraph graph = new FrameGraphBuilder()
                .addPass(pass("writer", Set.of(), Set.of(), Set.of("color"), () -> { }))
                .addPass(pass("reader", Set.of("writer"), Set.of("color"), Set.of(), () -> { }))
                .build();

        assertEquals(2, graph.orderedPasses().size());
    }

    private static FrameGraphPass pass(String id, Set<String> deps, Runnable run) {
        return pass(id, deps, Set.of(), Set.of(), run);
    }

    private static FrameGraphPass pass(String id, Set<String> deps, Set<String> reads, Set<String> writes, Runnable run) {
        return new FrameGraphPass() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> dependsOn() {
                return deps;
            }

            @Override
            public Set<String> reads() {
                return reads;
            }

            @Override
            public Set<String> writes() {
                return writes;
            }

            @Override
            public void execute() {
                run.run();
            }
        };
    }
}
