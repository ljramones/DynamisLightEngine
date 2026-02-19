package org.dynamislight.spi.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.dynamislight.api.config.QualityTier;

/**
 * Cross-capability validator for v2 capability contracts.
 */
public final class RenderCapabilityContractV2Validator {
    private RenderCapabilityContractV2Validator() {
    }

    public static List<RenderCapabilityValidationIssue> validate(
            List<RenderFeatureCapabilityV2> capabilities,
            QualityTier tier
    ) {
        List<RenderFeatureCapabilityV2> safeCapabilities = capabilities == null ? List.of() : capabilities.stream()
                .filter(Objects::nonNull)
                .toList();
        QualityTier safeTier = tier == null ? QualityTier.HIGH : tier;

        List<RenderCapabilityContractV2> contracts = safeCapabilities.stream()
                .map(capability -> capability.contractV2(safeTier))
                .toList();

        List<RenderCapabilityValidationIssue> issues = new ArrayList<>();
        validateDescriptorBindingCollisions(contracts, issues);
        validateResourceNameCollisions(contracts, issues);
        validatePushConstantConflicts(contracts, issues);
        validateRequiredFeatureScopes(contracts, issues);
        validateShaderInjectionConflicts(contracts, issues);
        return List.copyOf(issues);
    }

    public static boolean hasErrors(List<RenderCapabilityValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return false;
        }
        return issues.stream().anyMatch(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR);
    }

    private static void validateDescriptorBindingCollisions(
            List<RenderCapabilityContractV2> contracts,
            List<RenderCapabilityValidationIssue> issues
    ) {
        record DescriptorOwner(String featureId, String targetPassId, RenderDescriptorType type) {
        }
        Map<String, DescriptorOwner> byKey = new LinkedHashMap<>();

        for (RenderCapabilityContractV2 contract : contracts) {
            for (RenderDescriptorRequirement req : contract.descriptorRequirements()) {
                String pass = normalized(req.targetPassId());
                String key = pass + "|set=" + req.setIndex() + "|binding=" + req.bindingIndex();
                DescriptorOwner owner = byKey.get(key);
                if (owner == null) {
                    byKey.put(key, new DescriptorOwner(contract.featureId(), pass, req.type()));
                    continue;
                }
                if (owner.featureId().equals(contract.featureId())) {
                    continue;
                }
                issues.add(new RenderCapabilityValidationIssue(
                        "DESCRIPTOR_BINDING_COLLISION",
                        "Descriptor collision at " + key + " between '" + owner.featureId() + "' and '" + contract.featureId() + "'",
                        RenderCapabilityValidationIssue.Severity.ERROR,
                        owner.featureId(),
                        contract.featureId(),
                        "targetPass=" + pass
                                + ", set=" + req.setIndex()
                                + ", binding=" + req.bindingIndex()
                                + ", existingType=" + owner.type()
                                + ", incomingType=" + req.type()
                ));
            }
        }
    }

    private static void validateResourceNameCollisions(
            List<RenderCapabilityContractV2> contracts,
            List<RenderCapabilityValidationIssue> issues
    ) {
        Map<String, String> ownerByResource = new LinkedHashMap<>();
        for (RenderCapabilityContractV2 contract : contracts) {
            for (RenderResourceDeclaration resource : contract.ownedResources()) {
                String name = normalized(resource.resourceName());
                if (name.isBlank()) {
                    continue;
                }
                String owner = ownerByResource.get(name);
                if (owner == null) {
                    ownerByResource.put(name, contract.featureId());
                    continue;
                }
                if (owner.equals(contract.featureId())) {
                    continue;
                }
                issues.add(new RenderCapabilityValidationIssue(
                        "RESOURCE_NAME_COLLISION",
                        "Owned resource name collision for '" + name + "' between '" + owner + "' and '" + contract.featureId() + "'",
                        RenderCapabilityValidationIssue.Severity.ERROR,
                        owner,
                        contract.featureId(),
                        "resourceName=" + name
                ));
            }
        }
    }

    private static void validatePushConstantConflicts(
            List<RenderCapabilityContractV2> contracts,
            List<RenderCapabilityValidationIssue> issues
    ) {
        record PushRange(String featureId, String targetPassId, RenderShaderStage stage, int start, int end) {
        }
        Map<String, List<PushRange>> rangesByKey = new LinkedHashMap<>();

        for (RenderCapabilityContractV2 contract : contracts) {
            for (RenderPushConstantRequirement push : contract.pushConstantRequirements()) {
                int start = push.byteOffset();
                int end = push.byteOffset() + push.byteSize();
                if (end <= start) {
                    continue;
                }
                String pass = normalized(push.targetPassId());
                for (RenderShaderStage stage : push.stages()) {
                    if (stage == null) {
                        continue;
                    }
                    String key = pass + "|stage=" + stage.name();
                    List<PushRange> ranges = rangesByKey.computeIfAbsent(key, __ -> new ArrayList<>());
                    for (PushRange existing : ranges) {
                        if (existing.featureId().equals(contract.featureId())) {
                            continue;
                        }
                        if (overlaps(start, end, existing.start(), existing.end())) {
                            issues.add(new RenderCapabilityValidationIssue(
                                    "PUSH_CONSTANT_LAYOUT_CONFLICT",
                                    "Push-constant range overlap on " + key + " between '" + existing.featureId() + "' and '" + contract.featureId() + "'",
                                    RenderCapabilityValidationIssue.Severity.ERROR,
                                    existing.featureId(),
                                    contract.featureId(),
                                    "targetPass=" + pass
                                            + ", stage=" + stage
                                            + ", existingRange=[" + existing.start() + "," + existing.end() + ")"
                                            + ", incomingRange=[" + start + "," + end + ")"
                            ));
                        }
                    }
                    ranges.add(new PushRange(contract.featureId(), pass, stage, start, end));
                }
            }
        }
    }

    private static void validateRequiredFeatureScopes(
            List<RenderCapabilityContractV2> contracts,
            List<RenderCapabilityValidationIssue> issues
    ) {
        Set<String> presentFeatures = new LinkedHashSet<>();
        for (RenderCapabilityContractV2 contract : contracts) {
            presentFeatures.add(contract.featureId());
        }

        for (RenderCapabilityContractV2 contract : contracts) {
            for (RenderPassDeclaration pass : contract.passes()) {
                for (String requiredFeature : pass.requiredFeatureScopes()) {
                    String required = normalized(requiredFeature);
                    if (required.isBlank() || presentFeatures.contains(required)) {
                        continue;
                    }
                    issues.add(new RenderCapabilityValidationIssue(
                            "UNRESOLVED_FEATURE_SCOPE_DEPENDENCY",
                            "Pass '" + pass.passId() + "' in feature '" + contract.featureId() + "' requires missing feature scope '" + required + "'",
                            RenderCapabilityValidationIssue.Severity.ERROR,
                            contract.featureId(),
                            required,
                            "passId=" + pass.passId() + ", requiredFeatureScope=" + required
                    ));
                }
            }
        }
    }

    private static void validateShaderInjectionConflicts(
            List<RenderCapabilityContractV2> contracts,
            List<RenderCapabilityValidationIssue> issues
    ) {
        record ShaderOwner(String featureId, int ordering, String implementationKey) {
        }
        Map<String, List<ShaderOwner>> byKey = new LinkedHashMap<>();

        for (RenderCapabilityContractV2 contract : contracts) {
            for (RenderShaderContribution contribution : contract.shaderContributions()) {
                String key = normalized(contribution.targetPassId())
                        + "|injection=" + contribution.injectionPoint().name();
                List<ShaderOwner> owners = byKey.computeIfAbsent(key, __ -> new ArrayList<>());
                for (ShaderOwner existing : owners) {
                    if (existing.featureId().equals(contract.featureId())) {
                        continue;
                    }
                    boolean unspecified = existing.ordering() < 0 || contribution.ordering() < 0;
                    boolean sameOrder = existing.ordering() >= 0
                            && contribution.ordering() >= 0
                            && existing.ordering() == contribution.ordering();
                    if (unspecified || sameOrder) {
                        String reason = unspecified
                                ? "missing explicit ordering declaration"
                                : "duplicate explicit ordering value";
                        issues.add(new RenderCapabilityValidationIssue(
                                "SHADER_INJECTION_POINT_CONFLICT",
                                "Shader injection conflict at " + key + " between '"
                                        + existing.featureId() + "' and '" + contract.featureId() + "' (" + reason + ")",
                                RenderCapabilityValidationIssue.Severity.WARNING,
                                existing.featureId(),
                                contract.featureId(),
                                "targetPass=" + normalized(contribution.targetPassId())
                                        + ", injectionPoint=" + contribution.injectionPoint()
                                        + ", existingOrdering=" + existing.ordering()
                                        + ", incomingOrdering=" + contribution.ordering()
                                        + ", existingImpl=" + existing.implementationKey()
                                        + ", incomingImpl=" + contribution.implementationKey()
                        ));
                    }
                }
                owners.add(new ShaderOwner(
                        contract.featureId(),
                        contribution.ordering(),
                        contribution.implementationKey()
                ));
            }
        }
    }

    private static boolean overlaps(int aStart, int aEnd, int bStart, int bEnd) {
        return aStart < bEnd && bStart < aEnd;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
