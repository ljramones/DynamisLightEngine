package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderDescriptorRequirement;
import org.dynamislight.spi.render.RenderDescriptorType;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;
import org.dynamislight.spi.render.RenderFeatureMode;
import org.dynamislight.spi.render.RenderPassDeclaration;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderPushConstantRequirement;
import org.dynamislight.spi.render.RenderResourceDeclaration;
import org.dynamislight.spi.render.RenderResourceLifecycle;
import org.dynamislight.spi.render.RenderResourceType;
import org.dynamislight.spi.render.RenderSchedulerDeclaration;
import org.dynamislight.spi.render.RenderShaderContribution;
import org.dynamislight.spi.render.RenderShaderInjectionPoint;
import org.dynamislight.spi.render.RenderShaderModuleBinding;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
import org.dynamislight.spi.render.RenderShaderStage;
import org.dynamislight.spi.render.RenderTelemetryDeclaration;
import org.dynamislight.spi.render.RenderUniformRequirement;

/**
 * RT cross-cut v2 contract descriptor for composition and CI validation.
 */
public final class VulkanRtCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_RT_AO_DENOISED = new RenderFeatureMode("rt_ao_denoised");
    public static final RenderFeatureMode MODE_RT_TRANSLUCENCY_CAUSTICS = new RenderFeatureMode("rt_translucency_caustics");
    public static final RenderFeatureMode MODE_BVH_MANAGEMENT = new RenderFeatureMode("bvh_management");
    public static final RenderFeatureMode MODE_DENOISER_FRAMEWORK = new RenderFeatureMode("denoiser_framework");
    public static final RenderFeatureMode MODE_RT_HYBRID_RASTER = new RenderFeatureMode("rt_hybrid_raster");
    public static final RenderFeatureMode MODE_QUALITY_TIERS = new RenderFeatureMode("rt_quality_tiers");
    public static final RenderFeatureMode MODE_INLINE_RAY_QUERY = new RenderFeatureMode("inline_ray_query");
    public static final RenderFeatureMode MODE_DEDICATED_RAYGEN = new RenderFeatureMode("dedicated_raygen");
    public static final RenderFeatureMode MODE_FULL_STACK = new RenderFeatureMode("rt_full_stack");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_RT_AO_DENOISED,
            MODE_RT_TRANSLUCENCY_CAUSTICS,
            MODE_BVH_MANAGEMENT,
            MODE_DENOISER_FRAMEWORK,
            MODE_RT_HYBRID_RASTER,
            MODE_QUALITY_TIERS,
            MODE_INLINE_RAY_QUERY,
            MODE_DEDICATED_RAYGEN,
            MODE_FULL_STACK
    );

    private final RenderFeatureMode activeMode;

    public VulkanRtCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanRtCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanRtCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.rt";
    }

    @Override
    public List<RenderFeatureMode> supportedModes() {
        return SUPPORTED;
    }

    @Override
    public RenderFeatureMode activeMode() {
        return activeMode;
    }

    @Override
    public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return List.of(new RenderPassDeclaration(
                "rt_crosscut_resolve",
                RenderPassPhase.POST_MAIN,
                readsFor(active),
                writesFor(active),
                false,
                false,
                false,
                List.of()
        ));
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return List.of(new RenderShaderContribution(
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "rt_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                false,
                false,
                orderingFor(active),
                false
        ));
    }

    @Override
    public List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        ArrayList<RenderShaderModuleBinding> bindings = new ArrayList<>(List.of(
                new RenderShaderModuleBinding("uRtSceneColor", descriptorByTargetSetBinding("post_composite", 0, 90)),
                new RenderShaderModuleBinding("uRtSceneDepth", descriptorByTargetSetBinding("post_composite", 0, 91)),
                new RenderShaderModuleBinding("uRtUniforms", descriptorByTargetSetBinding("post_composite", 0, 92))
        ));
        if (requiresAo(active) || requiresTranslucency(active) || requiresHybrid(active)) {
            bindings.add(new RenderShaderModuleBinding("uRtAoLane", descriptorByTargetSetBinding("post_composite", 0, 93)));
        }
        if (requiresTranslucency(active) || requiresHybrid(active)) {
            bindings.add(new RenderShaderModuleBinding("uRtTranslucencyLane", descriptorByTargetSetBinding("post_composite", 0, 94)));
        }
        if (requiresBvh(active) || requiresDedicatedRaygen(active) || requiresInlineRayQuery(active)) {
            bindings.add(new RenderShaderModuleBinding("uRtAcceleration", descriptorByTargetSetBinding("post_composite", 0, 95)));
        }
        if (requiresDenoiser(active) || requiresHybrid(active)) {
            bindings.add(new RenderShaderModuleBinding("uRtDenoiseHistory", descriptorByTargetSetBinding("post_composite", 0, 96)));
        }
        if (requiresQualityTiers(active)) {
            bindings.add(new RenderShaderModuleBinding("uRtTierControls", descriptorByTargetSetBinding("post_composite", 0, 97)));
        }
        return List.of(new RenderShaderModuleDeclaration(
                "rt.post.resolve." + active.id(),
                featureId(),
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "resolveRtCrossCut",
                "vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv)",
                rtModuleBody(active),
                bindings,
                uniformRequirements(active),
                List.of(),
                orderingFor(active),
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        ArrayList<RenderDescriptorRequirement> requirements = new ArrayList<>(List.of(
                new RenderDescriptorRequirement("post_composite", 0, 90, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 91, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 92, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false)
        ));
        if (requiresAo(active) || requiresTranslucency(active) || requiresHybrid(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 93, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        if (requiresTranslucency(active) || requiresHybrid(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 94, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        if (requiresBvh(active) || requiresDedicatedRaygen(active) || requiresInlineRayQuery(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 95, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        if (requiresDenoiser(active) || requiresHybrid(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 96, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, true));
        }
        if (requiresQualityTiers(active)) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 97, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        ArrayList<RenderUniformRequirement> requirements = new ArrayList<>();
        requirements.add(new RenderUniformRequirement("global_scene", "rtCrossCut", 0, 0));
        if (requiresAo(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "rtAo", 0, 0));
        }
        if (requiresTranslucency(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "rtTranslucency", 0, 0));
        }
        if (requiresDenoiser(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "rtDenoiser", 0, 0));
        }
        if (requiresQualityTiers(active)) {
            requirements.add(new RenderUniformRequirement("global_scene", "rtQualityTiers", 0, 0));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        ArrayList<RenderResourceDeclaration> resources = new ArrayList<>(List.of(
                new RenderResourceDeclaration(
                        "rt_crosscut_buffer",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("swapchainRecreated", "renderScaleChanged", "rtModeChanged")
                )
        ));
        if (requiresAo(active) || requiresHybrid(active)) {
            resources.add(new RenderResourceDeclaration(
                    "rt_ao_buffer",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.TRANSIENT,
                    true,
                    List.of("rtModeChanged", "renderScaleChanged")
            ));
        }
        if (requiresTranslucency(active) || requiresHybrid(active)) {
            resources.add(new RenderResourceDeclaration(
                    "rt_translucency_buffer",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.TRANSIENT,
                    true,
                    List.of("rtModeChanged", "renderScaleChanged")
            ));
        }
        if (requiresBvh(active) || requiresDedicatedRaygen(active) || requiresInlineRayQuery(active)) {
            resources.add(new RenderResourceDeclaration(
                    "rt_bvh",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    true,
                    List.of("sceneMeshChanged", "rtModeChanged")
            ));
        }
        if (requiresDenoiser(active) || requiresHybrid(active)) {
            resources.add(new RenderResourceDeclaration(
                    "rt_denoise_history",
                    RenderResourceType.ATTACHMENT,
                    RenderResourceLifecycle.CROSS_FRAME_TEMPORAL,
                    true,
                    List.of("swapchainRecreated", "renderScaleChanged", "rtModeChanged")
            ));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of(new RenderSchedulerDeclaration(
                "rt_crosscut_scheduler",
                List.of(
                        new org.dynamislight.spi.render.RenderBudgetParameter("rtAoRayCount", "8"),
                        new org.dynamislight.spi.render.RenderBudgetParameter("rtCausticsRayCount", "4"),
                        new org.dynamislight.spi.render.RenderBudgetParameter("rtBvhRefitPerFrame", "1")
                ),
                false,
                true
        ));
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "RT_CAPABILITY_MODE_ACTIVE",
                        "RT_CAPABILITY_POLICY_ACTIVE",
                        "RT_CAPABILITY_ENVELOPE",
                        "RT_CAPABILITY_ENVELOPE_BREACH",
                        "RT_CAPABILITY_PROMOTION_READY",
                        "RT_CROSSCUT_POLICY_ACTIVE",
                        "RT_CROSSCUT_ENVELOPE",
                        "RT_CROSSCUT_ENVELOPE_BREACH",
                        "RT_CROSSCUT_PROMOTION_READY"
                ),
                List.of(
                        "rtCapabilityDiagnostics",
                        "rtCapabilityPromotionDiagnostics",
                        "rtCrossCutDiagnostics"
                ),
                List.of(),
                List.of(
                        "rt.capability.contract",
                        "rt.crosscut.contract"
                )
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null) {
            return MODE_QUALITY_TIERS;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(mode.id()))
                .findFirst()
                .orElse(MODE_QUALITY_TIERS);
    }

    private static List<String> readsFor(RenderFeatureMode mode) {
        if (requiresTranslucency(mode) || requiresHybrid(mode)) {
            return List.of("scene_color", "scene_depth", "velocity", "rt_scene");
        }
        if (requiresAo(mode)) {
            return List.of("scene_color", "scene_depth", "scene_normal");
        }
        if (requiresDedicatedRaygen(mode) || requiresInlineRayQuery(mode)) {
            return List.of("scene_color", "scene_depth", "rt_scene");
        }
        return List.of("scene_color", "scene_depth");
    }

    private static List<String> writesFor(RenderFeatureMode mode) {
        if (requiresAo(mode) && requiresTranslucency(mode)) {
            return List.of("rt_ao_buffer", "rt_translucency_buffer", "rt_crosscut_buffer");
        }
        if (requiresAo(mode) || requiresHybrid(mode)) {
            return List.of("rt_ao_buffer", "rt_crosscut_buffer");
        }
        if (requiresTranslucency(mode)) {
            return List.of("rt_translucency_buffer", "rt_crosscut_buffer");
        }
        return List.of("rt_crosscut_buffer");
    }

    private static int orderingFor(RenderFeatureMode mode) {
        return switch (sanitizeMode(mode).id()) {
            case "rt_quality_tiers" -> 240;
            case "bvh_management" -> 242;
            case "denoiser_framework" -> 244;
            case "rt_hybrid_raster" -> 246;
            case "inline_ray_query" -> 248;
            case "dedicated_raygen" -> 250;
            case "rt_ao_denoised" -> 252;
            case "rt_translucency_caustics" -> 254;
            case "rt_full_stack" -> 256;
            default -> 240;
        };
    }

    private static boolean requiresAo(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "rt_ao_denoised".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresTranslucency(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "rt_translucency_caustics".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresBvh(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "bvh_management".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresDenoiser(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "denoiser_framework".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresHybrid(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "rt_hybrid_raster".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresQualityTiers(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "rt_quality_tiers".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresInlineRayQuery(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "inline_ray_query".equals(id) || "rt_full_stack".equals(id);
    }

    private static boolean requiresDedicatedRaygen(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return "dedicated_raygen".equals(id) || "rt_full_stack".equals(id);
    }

    private static String rtModuleBody(RenderFeatureMode mode) {
        return switch (sanitizeMode(mode).id()) {
            case "rt_ao_denoised" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        float d = texture(uSceneDepth, uv).r;
                        float dx = abs(texture(uSceneDepth, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).r - d);
                        float dy = abs(texture(uSceneDepth, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).r - d);
                        float ao = 1.0 - clamp((dx + dy) * 2.5, 0.0, 0.6);
                        vec3 shaded = baseColor.rgb * mix(0.80, 1.0, ao);
                        return vec4(shaded, baseColor.a);
                    }
                    """;
            case "rt_translucency_caustics" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec3 c0 = texture(uSceneColor, uv).rgb;
                        vec3 c1 = textureLod(uSceneColor, clamp(uv + vec2(texel.x, texel.y) * 5.0, vec2(0.0), vec2(1.0)), 1.1).rgb;
                        vec3 c2 = textureLod(uSceneColor, clamp(uv + vec2(-texel.x, texel.y) * 7.0, vec2(0.0), vec2(1.0)), 1.5).rgb;
                        vec3 caustics = (c0 * 0.5) + (c1 * 0.3) + (c2 * 0.2);
                        vec3 shaded = mix(baseColor.rgb, caustics, 0.20);
                        return vec4(clamp(shaded, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "rt_full_stack" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        float d = texture(uSceneDepth, uv).r;
                        float dx = abs(texture(uSceneDepth, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).r - d);
                        float dy = abs(texture(uSceneDepth, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).r - d);
                        float ao = 1.0 - clamp((dx + dy) * 2.5, 0.0, 0.6);
                        vec3 baseAo = baseColor.rgb * mix(0.80, 1.0, ao);
                        vec3 caustics = textureLod(uSceneColor, clamp(uv + vec2(texel.x, texel.y) * 6.0, vec2(0.0), vec2(1.0)), 1.2).rgb;
                        vec3 hybrid = mix(baseAo, caustics, 0.15);
                        return vec4(clamp(hybrid, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "bvh_management" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        float d0 = texture(uSceneDepth, uv).r;
                        float d1 = texture(uSceneDepth, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).r;
                        float d2 = texture(uSceneDepth, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).r;
                        float edge = clamp(abs(d1 - d0) + abs(d2 - d0), 0.0, 1.0);
                        float bvhConfidence = 1.0 - smoothstep(0.01, 0.18, edge);
                        vec3 stabilized = mix(baseColor.rgb * 0.94, baseColor.rgb, bvhConfidence);
                        return vec4(clamp(stabilized, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "denoiser_framework" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec3 center = texture(uSceneColor, uv).rgb;
                        vec3 h0 = texture(uHistoryColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 h1 = texture(uHistoryColor, clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 v0 = texture(uHistoryColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 v1 = texture(uHistoryColor, clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 neighborhood = (h0 + h1 + v0 + v1) * 0.25;
                        float denoiseStrength = clamp(pc.reflectionsB.w, 0.0, 1.0);
                        vec3 denoised = mix(center, neighborhood, 0.25 + denoiseStrength * 0.45);
                        vec3 temporal = mix(denoised, texture(uHistoryColor, uv).rgb, 0.22 + denoiseStrength * 0.25);
                        return vec4(clamp(mix(baseColor.rgb, temporal, 0.28), vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "rt_hybrid_raster" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec2 ray = normalize((uv - vec2(0.5)) + vec2(0.0001));
                        vec3 rtLike = textureLod(uSceneColor, clamp(uv + ray * texel * 10.0, vec2(0.0), vec2(1.0)), 1.0).rgb;
                        vec3 rasterLike = texture(uSceneColor, clamp(uv - ray * texel * 6.0, vec2(0.0), vec2(1.0))).rgb;
                        float hybridBias = clamp(0.40 + (1.0 - texture(uSceneDepth, uv).r) * 0.35, 0.0, 1.0);
                        vec3 hybrid = mix(rasterLike, rtLike, hybridBias);
                        return vec4(clamp(mix(baseColor.rgb, hybrid, 0.30), vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "rt_quality_tiers" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        float tierSignal = clamp(pc.ssao.w, 0.5, 1.0);
                        float qualityScale = smoothstep(0.5, 1.0, tierSignal);
                        vec3 history = texture(uHistoryColor, uv).rgb;
                        vec3 refined = mix(baseColor.rgb, history, 0.12 + qualityScale * 0.24);
                        return vec4(clamp(refined, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "inline_ray_query" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec2 dir = normalize(vec2(uv.x - 0.5, 0.5 - uv.y) + vec2(0.0001));
                        vec3 q0 = textureLod(uSceneColor, clamp(uv + dir * texel * 4.0, vec2(0.0), vec2(1.0)), 0.9).rgb;
                        vec3 q1 = textureLod(uSceneColor, clamp(uv + dir * texel * 9.0, vec2(0.0), vec2(1.0)), 1.3).rgb;
                        vec3 queryResult = mix(q0, q1, 0.45);
                        return vec4(clamp(mix(baseColor.rgb, queryResult, 0.22), vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "dedicated_raygen" -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec2 dirA = normalize(vec2(uv.x - 0.5, 0.5 - uv.y) + vec2(0.0001));
                        vec2 dirB = vec2(-dirA.y, dirA.x);
                        vec3 a0 = textureLod(uSceneColor, clamp(uv + dirA * texel * 7.0, vec2(0.0), vec2(1.0)), 1.0).rgb;
                        vec3 a1 = textureLod(uSceneColor, clamp(uv - dirA * texel * 6.0, vec2(0.0), vec2(1.0)), 1.2).rgb;
                        vec3 b0 = textureLod(uSceneColor, clamp(uv + dirB * texel * 8.0, vec2(0.0), vec2(1.0)), 1.4).rgb;
                        vec3 raygen = (a0 * 0.4) + (a1 * 0.3) + (b0 * 0.3);
                        vec3 denoised = mix(raygen, texture(uHistoryColor, uv).rgb, 0.25 + clamp(pc.reflectionsB.w, 0.0, 1.0) * 0.25);
                        return vec4(clamp(mix(baseColor.rgb, denoised, 0.35), vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            default -> """
                    vec4 resolveRtCrossCut(vec4 baseColor, vec2 uv) {
                        return baseColor;
                    }
                    """;
        };
    }

    private static RenderDescriptorRequirement descriptorByTargetSetBinding(String targetPass, int set, int binding) {
        RenderDescriptorType type = switch (binding) {
            case 92, 97 -> RenderDescriptorType.UNIFORM_BUFFER;
            case 93, 94, 95 -> RenderDescriptorType.STORAGE_BUFFER;
            default -> RenderDescriptorType.COMBINED_IMAGE_SAMPLER;
        };
        return new RenderDescriptorRequirement(targetPass, set, binding, type, RenderBindingFrequency.PER_FRAME, false);
    }
}
