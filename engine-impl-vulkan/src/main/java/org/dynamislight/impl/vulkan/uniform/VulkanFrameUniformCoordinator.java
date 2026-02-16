package org.dynamislight.impl.vulkan.uniform;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

public final class VulkanFrameUniformCoordinator {
    private VulkanFrameUniformCoordinator() {
    }

    @FunctionalInterface
    public interface FailureFactory {
        EngineException failure(String operation, int result);
    }

    public static Result prepare(Inputs in) throws EngineException {
        int meshCount = Math.max(1, in.gpuMeshCount());
        int maxObservedDynamicObjects = Math.max(in.maxObservedDynamicObjects(), meshCount);
        if (meshCount > in.maxDynamicSceneObjects()) {
            throw new EngineException(
                    EngineErrorCode.RESOURCE_CREATION_FAILED,
                    "Scene mesh count " + meshCount + " exceeds dynamic uniform capacity " + in.maxDynamicSceneObjects(),
                    false
            );
        }
        int normalizedFrame = Math.floorMod(in.frameIdx(), in.framesInFlight());
        int frameBase = normalizedFrame * in.uniformFrameSpanBytes();
        boolean globalStale = in.frameGlobalRevisionApplied()[normalizedFrame] != in.globalStateRevision();
        boolean sceneStale = in.frameSceneRevisionApplied()[normalizedFrame] != in.sceneStateRevision();
        if (!globalStale && !sceneStale) {
            return Result.noop(maxObservedDynamicObjects);
        }

        long pendingGlobalUploadSrcOffset = -1L;
        long pendingGlobalUploadDstOffset = -1L;
        int pendingGlobalUploadByteCount = 0;

        long globalFrameBase = (long) normalizedFrame * in.globalUniformFrameSpanBytes();
        if (globalStale) {
            ByteBuffer globalMapped;
            if (in.sceneGlobalUniformStagingMappedAddress() != 0L) {
                globalMapped = memByteBuffer(in.sceneGlobalUniformStagingMappedAddress() + globalFrameBase, in.globalSceneUniformBytes());
            } else {
                globalMapped = memAlloc(in.globalSceneUniformBytes());
            }
            globalMapped.order(ByteOrder.nativeOrder());
            try {
                VulkanUniformWriters.writeGlobalSceneUniform(globalMapped, in.globalSceneUniformInput());
                if (in.sceneGlobalUniformStagingMappedAddress() == 0L) {
                    try (MemoryStack stack = stackPush()) {
                        PointerBuffer pData = stack.mallocPointer(1);
                        int mapResult = vkMapMemory(
                                in.device(),
                                in.sceneGlobalUniformStagingMemory(),
                                globalFrameBase,
                                in.globalSceneUniformBytes(),
                                0,
                                pData
                        );
                        if (mapResult != VK_SUCCESS) {
                            throw in.vkFailure().failure("vkMapMemory(globalStaging)", mapResult);
                        }
                        memCopy(memAddress(globalMapped), pData.get(0), in.globalSceneUniformBytes());
                        vkUnmapMemory(in.device(), in.sceneGlobalUniformStagingMemory());
                    }
                }
            } finally {
                if (in.sceneGlobalUniformStagingMappedAddress() == 0L) {
                    memFree(globalMapped);
                }
            }
            pendingGlobalUploadSrcOffset = globalFrameBase;
            pendingGlobalUploadDstOffset = globalFrameBase;
            pendingGlobalUploadByteCount = in.globalSceneUniformBytes();
        }

        int uploadRangeCount;
        int uploadCapacity = in.pendingUploadSrcOffsets().length;
        int[] uploadStarts = new int[uploadCapacity];
        int[] uploadEnds = new int[uploadCapacity];
        if (sceneStale && in.pendingSceneDirtyRangeCount() > 0) {
            uploadRangeCount = 0;
            for (int i = 0; i < in.pendingSceneDirtyRangeCount() && uploadRangeCount < uploadCapacity; i++) {
                int start = Math.max(0, Math.min(meshCount - 1, in.pendingSceneDirtyStarts()[i]));
                int end = Math.max(start, Math.min(meshCount - 1, in.pendingSceneDirtyEnds()[i]));
                uploadStarts[uploadRangeCount] = start;
                uploadEnds[uploadRangeCount] = end;
                uploadRangeCount++;
            }
            if (uploadRangeCount == 0) {
                uploadRangeCount = 1;
                uploadStarts[0] = 0;
                uploadEnds[0] = meshCount - 1;
            }
        } else {
            uploadRangeCount = 1;
            uploadStarts[0] = 0;
            uploadEnds[0] = meshCount - 1;
        }

        ByteBuffer mapped;
        if (in.objectUniformStagingMappedAddress() != 0L) {
            mapped = memByteBuffer(in.objectUniformStagingMappedAddress() + frameBase, in.uniformFrameSpanBytes());
        } else {
            mapped = memAlloc(in.uniformFrameSpanBytes());
        }
        mapped.order(ByteOrder.nativeOrder());
        try {
            for (int range = 0; range < uploadRangeCount; range++) {
                int rangeStart = uploadStarts[range];
                int rangeEnd = uploadEnds[range];
                for (int meshIndex = rangeStart; meshIndex <= rangeEnd; meshIndex++) {
                    VulkanUniformWriters.writeObjectUniform(
                            mapped,
                            meshIndex * in.uniformStrideBytes(),
                            in.objectUniformBytes(),
                            in.meshProvider().meshAt(meshIndex)
                    );
                }
            }
            if (in.objectUniformStagingMappedAddress() == 0L) {
                try (MemoryStack stack = stackPush()) {
                    PointerBuffer pData = stack.mallocPointer(1);
                    int mapResult = vkMapMemory(in.device(), in.objectUniformStagingMemory(), frameBase, in.uniformFrameSpanBytes(), 0, pData);
                    if (mapResult != VK_SUCCESS) {
                        throw in.vkFailure().failure("vkMapMemory(objectStaging)", mapResult);
                    }
                    for (int range = 0; range < uploadRangeCount; range++) {
                        int rangeStartByte = uploadStarts[range] * in.uniformStrideBytes();
                        int rangeByteCount = ((uploadEnds[range] - uploadStarts[range]) + 1) * in.uniformStrideBytes();
                        memCopy(memAddress(mapped) + rangeStartByte, pData.get(0) + rangeStartByte, rangeByteCount);
                    }
                    vkUnmapMemory(in.device(), in.objectUniformStagingMemory());
                }
            }
        } finally {
            if (in.objectUniformStagingMappedAddress() == 0L) {
                memFree(mapped);
            }
        }

        int pendingUploadRangeCount = uploadRangeCount;
        int pendingUploadObjectCount = 0;
        int pendingUploadStartObject = Integer.MAX_VALUE;
        int pendingUploadByteCount = 0;
        for (int range = 0; range < uploadRangeCount; range++) {
            int rangeStartByte = uploadStarts[range] * in.uniformStrideBytes();
            int rangeByteCount = ((uploadEnds[range] - uploadStarts[range]) + 1) * in.uniformStrideBytes();
            long srcOffset = (long) frameBase + rangeStartByte;
            in.pendingUploadSrcOffsets()[range] = srcOffset;
            in.pendingUploadDstOffsets()[range] = srcOffset;
            in.pendingUploadByteCounts()[range] = rangeByteCount;
            pendingUploadObjectCount += (uploadEnds[range] - uploadStarts[range]) + 1;
            pendingUploadStartObject = Math.min(pendingUploadStartObject, uploadStarts[range]);
            pendingUploadByteCount += rangeByteCount;
        }

        long pendingUploadSrcOffset;
        long pendingUploadDstOffset;
        if (pendingUploadRangeCount == 1) {
            pendingUploadSrcOffset = in.pendingUploadSrcOffsets()[0];
            pendingUploadDstOffset = in.pendingUploadDstOffsets()[0];
            pendingUploadByteCount = in.pendingUploadByteCounts()[0];
        } else {
            pendingUploadSrcOffset = -1L;
            pendingUploadDstOffset = -1L;
        }
        if (pendingUploadStartObject == Integer.MAX_VALUE) {
            pendingUploadStartObject = 0;
        }
        in.frameGlobalRevisionApplied()[normalizedFrame] = in.globalStateRevision();
        in.frameSceneRevisionApplied()[normalizedFrame] = in.sceneStateRevision();
        int pendingSceneDirtyRangeCount = in.pendingSceneDirtyRangeCount();
        if (pendingSceneDirtyRangeCount > 0 && allFramesApplied(in.frameSceneRevisionApplied(), in.sceneStateRevision())) {
            pendingSceneDirtyRangeCount = 0;
        }

        return new Result(
                false,
                maxObservedDynamicObjects,
                pendingGlobalUploadSrcOffset,
                pendingGlobalUploadDstOffset,
                pendingGlobalUploadByteCount,
                pendingUploadRangeCount,
                pendingUploadObjectCount,
                pendingUploadStartObject,
                pendingUploadByteCount,
                pendingUploadSrcOffset,
                pendingUploadDstOffset,
                pendingSceneDirtyRangeCount
        );
    }

    private static boolean allFramesApplied(long[] frameRevisions, long revision) {
        for (long frameRevision : frameRevisions) {
            if (frameRevision != revision) {
                return false;
            }
        }
        return true;
    }

    public interface MeshProvider {
        org.dynamislight.impl.vulkan.model.VulkanGpuMesh meshAt(int index);
    }

    public record Inputs(
            int frameIdx,
            int gpuMeshCount,
            int maxObservedDynamicObjects,
            int maxDynamicSceneObjects,
            int framesInFlight,
            int uniformFrameSpanBytes,
            int globalUniformFrameSpanBytes,
            int uniformStrideBytes,
            int objectUniformBytes,
            int globalSceneUniformBytes,
            VkDevice device,
            long objectUniformStagingMemory,
            long sceneGlobalUniformStagingMemory,
            long objectUniformStagingMappedAddress,
            long sceneGlobalUniformStagingMappedAddress,
            long globalStateRevision,
            long sceneStateRevision,
            long[] frameGlobalRevisionApplied,
            long[] frameSceneRevisionApplied,
            int pendingSceneDirtyRangeCount,
            int[] pendingSceneDirtyStarts,
            int[] pendingSceneDirtyEnds,
            long[] pendingUploadSrcOffsets,
            long[] pendingUploadDstOffsets,
            int[] pendingUploadByteCounts,
            MeshProvider meshProvider,
            VulkanUniformWriters.GlobalSceneUniformInput globalSceneUniformInput,
            FailureFactory vkFailure
    ) {
    }

    public record Result(
            boolean clearPendingOnly,
            int maxObservedDynamicObjects,
            long pendingGlobalUploadSrcOffset,
            long pendingGlobalUploadDstOffset,
            int pendingGlobalUploadByteCount,
            int pendingUploadRangeCount,
            int pendingUploadObjectCount,
            int pendingUploadStartObject,
            int pendingUploadByteCount,
            long pendingUploadSrcOffset,
            long pendingUploadDstOffset,
            int pendingSceneDirtyRangeCount
    ) {
        public static Result noop(int maxObservedDynamicObjects) {
            return new Result(true, maxObservedDynamicObjects, -1L, -1L, 0, 0, 0, 0, 0, -1L, -1L, 0);
        }
    }
}
