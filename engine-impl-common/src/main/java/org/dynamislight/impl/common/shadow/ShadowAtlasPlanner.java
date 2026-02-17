package org.dynamislight.impl.common.shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans power-of-two shadow atlas placement with descending-size packing.
 * Optional prior allocations are used to keep stable placement and to evict
 * least-recently-visible entries when space pressure occurs.
 */
public final class ShadowAtlasPlanner {
    private ShadowAtlasPlanner() {
    }

    public static PlanResult plan(
            int atlasSizePx,
            List<Request> requests,
            Map<String, ExistingAllocation> existingAllocations
    ) {
        int atlasSize = clampPowerOfTwo(atlasSizePx);
        if (requests == null || requests.isEmpty()) {
            return new PlanResult(atlasSize, List.of(), List.of(), 0f);
        }
        List<Request> sortedRequests = new ArrayList<>();
        for (Request request : requests) {
            if (request == null || request.id() == null || request.id().isBlank()) {
                continue;
            }
            int tileSize = clampTileSize(request.tileSizePx(), atlasSize);
            sortedRequests.add(new Request(request.id(), tileSize, request.lastVisibleFrame()));
        }
        if (sortedRequests.isEmpty()) {
            return new PlanResult(atlasSize, List.of(), List.of(), 0f);
        }
        sortedRequests.sort(Comparator
                .comparingInt(Request::tileSizePx).reversed()
                .thenComparing(Request::id));

        Map<String, ExistingAllocation> retained = normalizeExisting(atlasSize, existingAllocations);
        List<Allocation> placements = new ArrayList<>();
        List<String> evicted = new ArrayList<>();
        boolean[][] occupancy = new boolean[atlasSize][atlasSize];
        for (ExistingAllocation existing : retained.values()) {
            if (fitsAndFree(occupancy, existing.xPx(), existing.yPx(), existing.tileSizePx(), atlasSize)) {
                mark(occupancy, existing.xPx(), existing.yPx(), existing.tileSizePx(), true);
            }
        }

        // Keep existing placements for currently requested lights first.
        for (Request request : sortedRequests) {
            ExistingAllocation prior = retained.get(request.id());
            if (prior == null || prior.tileSizePx() != request.tileSizePx()) {
                continue;
            }
            placements.add(new Allocation(request.id(), prior.xPx(), prior.yPx(), prior.tileSizePx()));
        }

        for (Request request : sortedRequests) {
            if (containsAllocation(placements, request.id())) {
                continue;
            }
            Placement candidate = findPlacement(occupancy, atlasSize, request.tileSizePx());
            if (candidate == null) {
                // Evict least-recently-visible retained entries not currently requested.
                List<ExistingAllocation> evictionCandidates = retained.values().stream()
                        .filter(existing -> !containsRequest(sortedRequests, existing.id()))
                        .sorted(Comparator
                                .comparingInt(ExistingAllocation::lastVisibleFrame)
                                .thenComparing(ExistingAllocation::id))
                        .toList();
                boolean freed = false;
                for (ExistingAllocation stale : evictionCandidates) {
                    if (withinBounds(stale.xPx(), stale.yPx(), stale.tileSizePx(), atlasSize)) {
                        mark(occupancy, stale.xPx(), stale.yPx(), stale.tileSizePx(), false);
                    }
                    evicted.add(stale.id());
                    candidate = findPlacement(occupancy, atlasSize, request.tileSizePx());
                    if (candidate != null) {
                        freed = true;
                        break;
                    }
                }
                if (!freed && candidate == null) {
                    continue;
                }
            }
            mark(occupancy, candidate.x(), candidate.y(), request.tileSizePx(), true);
            placements.add(new Allocation(request.id(), candidate.x(), candidate.y(), request.tileSizePx()));
        }

        int usedPixels = 0;
        for (Allocation allocation : placements) {
            usedPixels += allocation.tileSizePx() * allocation.tileSizePx();
        }
        float utilization = Math.min(1.0f, (float) usedPixels / (float) (atlasSize * atlasSize));
        placements.sort(Comparator
                .comparingInt(Allocation::tileSizePx).reversed()
                .thenComparing(Allocation::id));
        return new PlanResult(atlasSize, placements, evicted.stream().distinct().toList(), utilization);
    }

    private static Map<String, ExistingAllocation> normalizeExisting(
            int atlasSize,
            Map<String, ExistingAllocation> existingAllocations
    ) {
        Map<String, ExistingAllocation> normalized = new HashMap<>();
        if (existingAllocations == null || existingAllocations.isEmpty()) {
            return normalized;
        }
        for (ExistingAllocation allocation : existingAllocations.values()) {
            if (allocation == null || allocation.id() == null || allocation.id().isBlank()) {
                continue;
            }
            int tileSize = clampTileSize(allocation.tileSizePx(), atlasSize);
            int x = Math.max(0, Math.min(atlasSize - tileSize, allocation.xPx()));
            int y = Math.max(0, Math.min(atlasSize - tileSize, allocation.yPx()));
            normalized.put(allocation.id(), new ExistingAllocation(allocation.id(), x, y, tileSize, allocation.lastVisibleFrame()));
        }
        return normalized;
    }

    private static Placement findPlacement(boolean[][] occupancy, int atlasSize, int tileSize) {
        for (int y = 0; y <= atlasSize - tileSize; y += tileSize) {
            for (int x = 0; x <= atlasSize - tileSize; x += tileSize) {
                if (fitsAndFree(occupancy, x, y, tileSize, atlasSize)) {
                    return new Placement(x, y);
                }
            }
        }
        return null;
    }

    private static boolean fitsAndFree(boolean[][] occupancy, int x, int y, int size, int atlasSize) {
        if (!withinBounds(x, y, size, atlasSize)) {
            return false;
        }
        for (int row = y; row < y + size; row++) {
            for (int col = x; col < x + size; col++) {
                if (occupancy[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean withinBounds(int x, int y, int size, int atlasSize) {
        return !(x < 0 || y < 0 || size <= 0 || x + size > atlasSize || y + size > atlasSize);
    }

    private static void mark(boolean[][] occupancy, int x, int y, int size, boolean value) {
        for (int row = y; row < y + size; row++) {
            for (int col = x; col < x + size; col++) {
                occupancy[row][col] = value;
            }
        }
    }

    private static boolean containsAllocation(List<Allocation> placements, String id) {
        for (Allocation allocation : placements) {
            if (allocation.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRequest(List<Request> requests, String id) {
        for (Request request : requests) {
            if (request.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static int clampPowerOfTwo(int value) {
        int clamped = Math.max(256, Math.min(4096, value));
        int p2 = 1;
        while (p2 < clamped && p2 < 4096) {
            p2 <<= 1;
        }
        return Math.max(256, Math.min(4096, p2));
    }

    private static int clampTileSize(int value, int atlasSize) {
        int clamped = Math.max(64, Math.min(atlasSize, value));
        int p2 = 1;
        while (p2 < clamped && p2 < atlasSize) {
            p2 <<= 1;
        }
        return Math.max(64, Math.min(atlasSize, p2));
    }

    public record Request(String id, int tileSizePx, int lastVisibleFrame) {
    }

    public record ExistingAllocation(String id, int xPx, int yPx, int tileSizePx, int lastVisibleFrame) {
    }

    public record Allocation(String id, int xPx, int yPx, int tileSizePx) {
    }

    public record PlanResult(
            int atlasSizePx,
            List<Allocation> allocations,
            List<String> evictedIds,
            float utilization
    ) {
    }

    private record Placement(int x, int y) {
    }
}
