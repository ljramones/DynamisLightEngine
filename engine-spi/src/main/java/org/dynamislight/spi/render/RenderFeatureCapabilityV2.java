package org.dynamislight.spi.render;

import java.util.List;
import org.dynamislight.api.config.QualityTier;

/**
 * v2 capability contract surface extracted from mature feature domains.
 */
public interface RenderFeatureCapabilityV2 extends RenderPassContributor, RenderShaderContributor, RenderShaderModuleContributor {
    String featureId();

    List<RenderFeatureMode> supportedModes();

    RenderFeatureMode activeMode();

    List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode);

    List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode);

    List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode);

    List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode);

    List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode);

    RenderTelemetryDeclaration telemetry(RenderFeatureMode mode);

    default RenderCapabilityContractV2 contractV2(QualityTier tier) {
        RenderFeatureMode mode = activeMode();
        return new RenderCapabilityContractV2(
                featureId(),
                mode,
                declarePasses(tier, mode),
                shaderContributions(mode),
                descriptorRequirements(mode),
                uniformRequirements(mode),
                pushConstantRequirements(mode),
                ownedResources(mode),
                schedulers(mode),
                telemetry(mode)
        );
    }
}
