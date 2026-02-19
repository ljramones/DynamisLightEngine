package org.dynamislight.impl.vulkan.graph;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.dynamislight.spi.render.RenderResourceType;

/**
 * Per-frame table that maps logical graph resources to concrete Vulkan handles and tracked layouts.
 */
public final class VulkanResourceBindingTable {
    private final Map<String, VulkanResourceBinding> bindings = new LinkedHashMap<>();

    public VulkanResourceBindingTable bind(
            String name,
            long vkImageHandle,
            int format,
            int aspectMask,
            int currentLayout
    ) {
        return bindImage(name, RenderResourceType.ATTACHMENT, vkImageHandle, format, aspectMask, currentLayout);
    }

    public VulkanResourceBindingTable bindImage(
            String name,
            RenderResourceType resourceType,
            long vkImageHandle,
            int format,
            int aspectMask,
            int currentLayout
    ) {
        String normalized = normalize(name);
        if (!normalized.isBlank()) {
            bindings.put(normalized, new VulkanImageResourceBinding(
                    normalized,
                    resourceType == null ? RenderResourceType.ATTACHMENT : resourceType,
                    vkImageHandle,
                    format,
                    aspectMask,
                    currentLayout
            ));
        }
        return this;
    }

    public VulkanResourceBindingTable bind(String name, long vkBufferHandle, long size) {
        return bindBuffer(name, RenderResourceType.STORAGE_BUFFER, vkBufferHandle, size);
    }

    public VulkanResourceBindingTable bindBuffer(
            String name,
            RenderResourceType resourceType,
            long vkBufferHandle,
            long size
    ) {
        String normalized = normalize(name);
        if (!normalized.isBlank()) {
            bindings.put(normalized, new VulkanBufferResourceBinding(
                    normalized,
                    resourceType == null ? RenderResourceType.STORAGE_BUFFER : resourceType,
                    vkBufferHandle,
                    size
            ));
        }
        return this;
    }

    public VulkanResourceBinding resolve(String name) {
        return bindings.get(normalize(name));
    }

    public VulkanImageResourceBinding resolveImage(String name) {
        VulkanResourceBinding binding = resolve(name);
        if (binding == null) {
            throw new IllegalStateException("No resource binding found for image '" + normalize(name) + "'");
        }
        if (binding instanceof VulkanImageResourceBinding image) {
            return image;
        }
        throw new IllegalStateException("Resource '" + normalize(name) + "' is not an image binding");
    }

    public VulkanBufferResourceBinding resolveBuffer(String name) {
        VulkanResourceBinding binding = resolve(name);
        if (binding == null) {
            throw new IllegalStateException("No resource binding found for buffer '" + normalize(name) + "'");
        }
        if (binding instanceof VulkanBufferResourceBinding buffer) {
            return buffer;
        }
        throw new IllegalStateException("Resource '" + normalize(name) + "' is not a buffer binding");
    }

    public void updateLayout(String name, int newLayout) {
        resolveImage(name).updateLayout(newLayout);
    }

    public Set<String> unboundResources(VulkanRenderGraphPlan graphPlan) {
        Set<String> resources = requiredResources(graphPlan);
        Set<String> missing = new LinkedHashSet<>();
        for (String resource : resources) {
            if (!bindings.containsKey(resource)) {
                missing.add(resource);
            }
        }
        return missing;
    }

    public Set<String> invalidBindings(VulkanRenderGraphPlan graphPlan) {
        Set<String> invalid = new LinkedHashSet<>();
        for (String resource : requiredResources(graphPlan)) {
            VulkanResourceBinding binding = bindings.get(resource);
            if (binding == null) {
                continue;
            }
            if (binding instanceof VulkanImageResourceBinding image && !image.isValid()) {
                invalid.add(resource);
            }
            if (binding instanceof VulkanBufferResourceBinding buffer && !buffer.isValid()) {
                invalid.add(resource);
            }
        }
        return invalid;
    }

    private static Set<String> requiredResources(VulkanRenderGraphPlan graphPlan) {
        Set<String> resources = new LinkedHashSet<>();
        if (graphPlan == null) {
            return resources;
        }
        for (VulkanRenderGraphNode node : graphPlan.orderedNodes()) {
            if (node == null) {
                continue;
            }
            for (String read : node.reads()) {
                String normalized = normalize(read);
                if (!normalized.isBlank()) {
                    resources.add(normalized);
                }
            }
            for (String write : node.writes()) {
                String normalized = normalize(write);
                if (!normalized.isBlank()) {
                    resources.add(normalized);
                }
            }
        }
        for (VulkanImportedResource imported : graphPlan.importedResources()) {
            if (imported == null) {
                continue;
            }
            String normalized = normalize(imported.resourceName());
            if (!normalized.isBlank()) {
                resources.add(normalized);
            }
        }
        return resources;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim();
    }
}
