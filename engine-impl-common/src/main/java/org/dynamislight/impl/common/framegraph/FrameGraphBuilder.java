package org.dynamislight.impl.common.framegraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Topological frame graph builder with cycle and missing dependency checks.
 */
public final class FrameGraphBuilder {
    private final Map<String, FrameGraphPass> passes = new HashMap<>();

    public FrameGraphBuilder addPass(FrameGraphPass pass) {
        passes.put(pass.id(), pass);
        return this;
    }

    public FrameGraph build() {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> outgoing = new HashMap<>();

        for (FrameGraphPass pass : passes.values()) {
            indegree.put(pass.id(), 0);
            outgoing.put(pass.id(), new HashSet<>());
        }

        for (FrameGraphPass pass : passes.values()) {
            for (String dep : pass.dependsOn()) {
                if (!passes.containsKey(dep)) {
                    throw new IllegalArgumentException("Missing dependency pass: " + dep + " for " + pass.id());
                }
                indegree.put(pass.id(), indegree.get(pass.id()) + 1);
                outgoing.get(dep).add(pass.id());
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<FrameGraphPass> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            ordered.add(passes.get(id));
            for (String next : outgoing.get(id)) {
                int updated = indegree.get(next) - 1;
                indegree.put(next, updated);
                if (updated == 0) {
                    queue.add(next);
                }
            }
        }

        if (ordered.size() != passes.size()) {
            throw new IllegalStateException("Frame graph contains a dependency cycle");
        }

        validateHazards();
        return new FrameGraph(List.copyOf(ordered));
    }

    private void validateHazards() {
        List<FrameGraphPass> list = new ArrayList<>(passes.values());
        for (int i = 0; i < list.size(); i++) {
            FrameGraphPass a = list.get(i);
            for (int j = i + 1; j < list.size(); j++) {
                FrameGraphPass b = list.get(j);
                boolean writeWrite = intersects(a.writes(), b.writes());
                boolean readWrite = intersects(a.reads(), b.writes()) || intersects(b.reads(), a.writes());
                if ((writeWrite || readWrite) && !ordered(a.id(), b.id()) && !ordered(b.id(), a.id())) {
                    throw new IllegalStateException(
                            "Frame graph hazard without dependency between passes " + a.id() + " and " + b.id()
                    );
                }
            }
        }
    }

    private boolean ordered(String from, String to) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(to)) {
                return true;
            }
            FrameGraphPass pass = passes.get(current);
            if (pass == null) {
                continue;
            }
            queue.addAll(pass.dependsOn());
        }
        return false;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        for (String value : a) {
            if (b.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
