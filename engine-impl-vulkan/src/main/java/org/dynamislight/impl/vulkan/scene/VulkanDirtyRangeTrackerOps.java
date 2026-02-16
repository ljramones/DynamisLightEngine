package org.dynamislight.impl.vulkan.scene;

import java.util.Arrays;

public final class VulkanDirtyRangeTrackerOps {
    private VulkanDirtyRangeTrackerOps() {
    }

    public static AddResult addRange(
            int[] starts,
            int[] ends,
            int count,
            int start,
            int end,
            int hardCap
    ) {
        if (end < start) {
            return new AddResult(starts, ends, count, false);
        }
        if (count >= starts.length) {
            GrowthResult grown = grow(starts, ends, hardCap);
            if (!grown.grew()) {
                starts[0] = 0;
                ends[0] = Math.max(start, end);
                return new AddResult(starts, ends, 1, true);
            }
            starts = grown.starts();
            ends = grown.ends();
        }
        starts[count] = start;
        ends[count] = end;
        return new AddResult(starts, ends, count + 1, false);
    }

    public static int normalizeRanges(int[] starts, int[] ends, int count, int mergeGapObjects) {
        if (count <= 1) {
            return count;
        }
        for (int i = 1; i < count; i++) {
            int start = starts[i];
            int end = ends[i];
            int j = i - 1;
            while (j >= 0 && starts[j] > start) {
                starts[j + 1] = starts[j];
                ends[j + 1] = ends[j];
                j--;
            }
            starts[j + 1] = start;
            ends[j + 1] = end;
        }
        int write = 0;
        for (int read = 1; read < count; read++) {
            int currStart = starts[read];
            int currEnd = ends[read];
            int prevEnd = ends[write];
            if (currStart <= (prevEnd + 1 + mergeGapObjects)) {
                ends[write] = Math.max(prevEnd, currEnd);
            } else {
                write++;
                starts[write] = currStart;
                ends[write] = currEnd;
            }
        }
        return write + 1;
    }

    private static GrowthResult grow(int[] starts, int[] ends, int hardCap) {
        int current = starts.length;
        if (current >= hardCap) {
            return new GrowthResult(starts, ends, false);
        }
        int target = Math.min(hardCap, Math.max(current + 1, current * 2));
        return new GrowthResult(
                Arrays.copyOf(starts, target),
                Arrays.copyOf(ends, target),
                true
        );
    }

    public record AddResult(
            int[] starts,
            int[] ends,
            int count,
            boolean overflowed
    ) {
    }

    private record GrowthResult(
            int[] starts,
            int[] ends,
            boolean grew
    ) {
    }
}
