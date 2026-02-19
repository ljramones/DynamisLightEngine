package org.dynamislight.impl.vulkan.descriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderCapabilityContractV2;
import org.dynamislight.spi.render.RenderDescriptorRequirement;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;

/**
 * Phase C.2.1 descriptor layout composer from capability requirements.
 *
 * This class produces deterministic metadata plans. Vulkan layout object creation is a later cutover step.
 */
public final class VulkanDescriptorLayoutComposer {
    private VulkanDescriptorLayoutComposer() {
    }

    public static VulkanComposedDescriptorLayoutPlan composeForPass(
            String targetPassId,
            QualityTier tier,
            List<RenderFeatureCapabilityV2> capabilities
    ) {
        String passId = targetPassId == null ? "" : targetPassId.trim();
        QualityTier effectiveTier = tier == null ? QualityTier.MEDIUM : tier;
        List<RenderFeatureCapabilityV2> safeCapabilities = capabilities == null ? List.of() : List.copyOf(capabilities);

        record Key(int set, int binding) {
        }
        Map<Key, MutableBinding> bindingMap = new HashMap<>();

        for (RenderFeatureCapabilityV2 capability : safeCapabilities) {
            if (capability == null) {
                continue;
            }
            RenderCapabilityContractV2 contract = capability.contractV2(effectiveTier);
            for (RenderDescriptorRequirement requirement : contract.descriptorRequirements()) {
                if (!passId.equals(requirement.targetPassId())) {
                    continue;
                }
                Key key = new Key(requirement.setIndex(), requirement.bindingIndex());
                MutableBinding existing = bindingMap.get(key);
                if (existing == null) {
                    bindingMap.put(key, new MutableBinding(requirement, contract.featureId()));
                    continue;
                }
                if (existing.type != requirement.type() || existing.frequency != requirement.frequency()) {
                    throw new IllegalStateException(
                            "Descriptor collision at pass=" + passId
                                    + " set=" + requirement.setIndex()
                                    + " binding=" + requirement.bindingIndex()
                                    + " existingType=" + existing.type
                                    + " newType=" + requirement.type()
                                    + " existingFrequency=" + existing.frequency
                                    + " newFrequency=" + requirement.frequency()
                    );
                }
                existing.conditional = existing.conditional && requirement.conditional();
                existing.providers.add(contract.featureId());
            }
        }

        Map<Integer, List<VulkanComposedDescriptorBinding>> bySet = new HashMap<>();
        for (Map.Entry<Key, MutableBinding> entry : bindingMap.entrySet()) {
            Key key = entry.getKey();
            MutableBinding value = entry.getValue();
            bySet.computeIfAbsent(key.set, ignored -> new ArrayList<>())
                    .add(new VulkanComposedDescriptorBinding(
                            key.set,
                            key.binding,
                            value.type,
                            value.frequency,
                            value.conditional,
                            List.copyOf(value.providers)
                    ));
        }
        bySet.replaceAll((set, list) -> list.stream()
                .sorted(Comparator.comparingInt(VulkanComposedDescriptorBinding::bindingIndex))
                .toList());

        return new VulkanComposedDescriptorLayoutPlan(passId, bySet);
    }

    private static final class MutableBinding {
        private final org.dynamislight.spi.render.RenderDescriptorType type;
        private final org.dynamislight.spi.render.RenderBindingFrequency frequency;
        private boolean conditional;
        private final Set<String> providers = new LinkedHashSet<>();

        private MutableBinding(RenderDescriptorRequirement requirement, String featureId) {
            this.type = requirement.type();
            this.frequency = requirement.frequency();
            this.conditional = requirement.conditional();
            this.providers.add(featureId);
        }
    }
}

