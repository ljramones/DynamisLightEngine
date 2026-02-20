package org.dynamislight.impl.vulkan.capability;

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
 * GI Phase 1 v2 contract scaffold for composition planning.
 */
public final class VulkanGiCapabilityDescriptorV2 implements RenderFeatureCapabilityV2 {
    public static final RenderFeatureMode MODE_SSGI = new RenderFeatureMode("ssgi");
    public static final RenderFeatureMode MODE_PROBE_GRID = new RenderFeatureMode("probe_grid");
    public static final RenderFeatureMode MODE_RTGI_SINGLE = new RenderFeatureMode("rtgi_single");
    public static final RenderFeatureMode MODE_HYBRID_PROBE_SSGI_RT = new RenderFeatureMode("hybrid_probe_ssgi_rt");

    private static final List<RenderFeatureMode> SUPPORTED = List.of(
            MODE_SSGI,
            MODE_PROBE_GRID,
            MODE_RTGI_SINGLE,
            MODE_HYBRID_PROBE_SSGI_RT
    );

    private final RenderFeatureMode activeMode;

    public VulkanGiCapabilityDescriptorV2(RenderFeatureMode activeMode) {
        this.activeMode = sanitizeMode(activeMode);
    }

    public static VulkanGiCapabilityDescriptorV2 withMode(RenderFeatureMode activeMode) {
        return new VulkanGiCapabilityDescriptorV2(activeMode);
    }

    @Override
    public String featureId() {
        return "vulkan.gi";
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
        List<String> writes = writesFor(active);
        return List.of(new RenderPassDeclaration(
                "gi_resolve",
                RenderPassPhase.POST_MAIN,
                readsFor(active),
                writes,
                false,
                false,
                false,
                List.of()
        ));
    }

    @Override
    public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        int ordering = switch (active.id()) {
            case "probe_grid" -> 205;
            case "ssgi" -> 210;
            case "rtgi_single" -> 215;
            case "hybrid_probe_ssgi_rt" -> 220;
            default -> 210;
        };
        return List.of(new RenderShaderContribution(
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "gi_" + active.id(),
                descriptorRequirements(active),
                uniformRequirements(active),
                pushConstantRequirements(active),
                false,
                false,
                ordering,
                false
        ));
    }

    @Override
    public List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderShaderModuleBinding> bindings = new java.util.ArrayList<>(List.of(
                new RenderShaderModuleBinding("uGiSceneColor", descriptorByTargetSetBinding("post_composite", 0, 70)),
                new RenderShaderModuleBinding("uGiSceneDepth", descriptorByTargetSetBinding("post_composite", 0, 71)),
                new RenderShaderModuleBinding("uGiUniforms", descriptorByTargetSetBinding("post_composite", 0, 72))
        ));
        if ("ssgi".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            bindings.add(new RenderShaderModuleBinding("uGiSceneNormal", descriptorByTargetSetBinding("post_composite", 0, 74)));
        }
        if ("rtgi_single".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            bindings.add(new RenderShaderModuleBinding("uGiRtLane", descriptorByTargetSetBinding("post_composite", 0, 73)));
        }
        if ("probe_grid".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            bindings.add(new RenderShaderModuleBinding("uGiProbeGrid", descriptorByTargetSetBinding("post_composite", 0, 75)));
        }
        return List.of(new RenderShaderModuleDeclaration(
                "gi.post.resolve." + active.id(),
                featureId(),
                "post_composite",
                RenderShaderInjectionPoint.POST_RESOLVE,
                RenderShaderStage.FRAGMENT,
                "resolveGiIndirect",
                "vec4 resolveGiIndirect(vec4 baseColor, vec2 uv)",
                giModuleBody(active),
                bindings,
                uniformRequirements(active),
                List.of(),
                switch (active.id()) {
                    case "probe_grid" -> 205;
                    case "ssgi" -> 210;
                    case "rtgi_single" -> 215;
                    case "hybrid_probe_ssgi_rt" -> 220;
                    default -> 210;
                },
                false
        ));
    }

    @Override
    public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderDescriptorRequirement> requirements = new java.util.ArrayList<>(List.of(
                new RenderDescriptorRequirement("post_composite", 0, 70, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 71, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, false),
                new RenderDescriptorRequirement("post_composite", 0, 72, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false)
        ));
        if ("rtgi_single".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 73, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        if ("ssgi".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 74, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_PASS, true));
        }
        if ("probe_grid".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            requirements.add(new RenderDescriptorRequirement("post_composite", 0, 75, RenderDescriptorType.STORAGE_BUFFER, RenderBindingFrequency.PER_FRAME, true));
        }
        return List.copyOf(requirements);
    }

    @Override
    public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        return switch (active.id()) {
            case "ssgi" -> List.of(new RenderUniformRequirement("global_scene", "giSsgi", 0, 0));
            case "probe_grid" -> List.of(new RenderUniformRequirement("global_scene", "giProbeGrid", 0, 0));
            case "rtgi_single" -> List.of(new RenderUniformRequirement("global_scene", "giRt", 0, 0));
            case "hybrid_probe_ssgi_rt" -> List.of(
                    new RenderUniformRequirement("global_scene", "giProbeGrid", 0, 0),
                    new RenderUniformRequirement("global_scene", "giSsgi", 0, 0),
                    new RenderUniformRequirement("global_scene", "giRt", 0, 0)
            );
            default -> List.of();
        };
    }

    @Override
    public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
        return List.of();
    }

    @Override
    public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
        RenderFeatureMode active = sanitizeMode(mode);
        java.util.ArrayList<RenderResourceDeclaration> resources = new java.util.ArrayList<>(List.of(
                new RenderResourceDeclaration(
                        "gi_indirect",
                        RenderResourceType.ATTACHMENT,
                        RenderResourceLifecycle.TRANSIENT,
                        false,
                        List.of("swapchainRecreated", "renderScaleChanged")
                )
        ));
        if ("ssgi".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "gi_ssgi_buffer",
                    RenderResourceType.ATTACHMENT,
                    RenderResourceLifecycle.TRANSIENT,
                    false,
                    List.of("swapchainRecreated", "renderScaleChanged", "giModeChanged")
            ));
        }
        if ("probe_grid".equals(active.id()) || "hybrid_probe_ssgi_rt".equals(active.id())) {
            resources.add(new RenderResourceDeclaration(
                    "gi_probe_grid",
                    RenderResourceType.STORAGE_BUFFER,
                    RenderResourceLifecycle.PERSISTENT_PARTIAL_UPDATE,
                    false,
                    List.of("sceneProbesChanged", "giModeChanged")
            ));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
        return List.of(new RenderSchedulerDeclaration(
                "gi_phase1_scheduler",
                List.of(
                        new org.dynamislight.spi.render.RenderBudgetParameter("maxProbeUpdatesPerFrame", "16"),
                        new org.dynamislight.spi.render.RenderBudgetParameter("rtSamplesPerPixel", "1")
                ),
                false,
                true
        ));
    }

    @Override
    public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
        return new RenderTelemetryDeclaration(
                List.of(
                        "GI_CAPABILITY_PLAN_ACTIVE",
                        "GI_PHASE1_POLICY",
                        "GI_PROMOTION_POLICY_ACTIVE",
                        "GI_SSGI_POLICY_ACTIVE",
                        "GI_SSGI_ENVELOPE",
                        "GI_SSGI_ENVELOPE_BREACH",
                        "GI_SSGI_PROMOTION_READY",
                        "GI_PROBE_GRID_POLICY_ACTIVE",
                        "GI_PROBE_GRID_STREAMING_POLICY_ACTIVE",
                        "GI_PROBE_GRID_ENVELOPE",
                        "GI_PROBE_GRID_ENVELOPE_BREACH",
                        "GI_PROBE_GRID_STREAMING_ENVELOPE",
                        "GI_PROBE_GRID_STREAMING_ENVELOPE_BREACH",
                        "GI_PROBE_GRID_PROMOTION_READY",
                        "GI_RT_DETAIL_POLICY_ACTIVE",
                        "GI_RT_DETAIL_FALLBACK_CHAIN",
                        "GI_RT_DETAIL_ENVELOPE",
                        "GI_RT_DETAIL_ENVELOPE_BREACH",
                        "GI_RT_DETAIL_PROMOTION_READY",
                        "GI_HYBRID_COMPOSITION",
                        "GI_HYBRID_COMPOSITION_BREACH",
                        "GI_PROMOTION_READY",
                        "GI_PHASE2_PROMOTION_READY"
                ),
                List.of(
                        "giCapabilityDiagnostics",
                        "giPromotionDiagnostics"
                ),
                List.of(),
                List.of(
                        "gi.phase1.contract",
                        "gi.mode.policy"
                )
        );
    }

    private static RenderFeatureMode sanitizeMode(RenderFeatureMode mode) {
        if (mode == null || mode.id() == null) {
            return MODE_SSGI;
        }
        return SUPPORTED.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(mode.id()))
                .findFirst()
                .orElse(MODE_SSGI);
    }

    private static List<String> readsFor(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return switch (id) {
            case "ssgi" -> List.of("scene_color", "scene_depth", "scene_normal", "velocity");
            case "probe_grid" -> List.of("scene_color", "scene_depth", "scene_normal", "probe_grid");
            case "rtgi_single" -> List.of("scene_color", "scene_depth", "scene_normal", "rt_scene");
            case "hybrid_probe_ssgi_rt" -> List.of("scene_color", "scene_depth", "scene_normal", "probe_grid", "velocity", "rt_scene");
            default -> List.of("scene_color", "scene_depth");
        };
    }

    private static List<String> writesFor(RenderFeatureMode mode) {
        String id = sanitizeMode(mode).id();
        return switch (id) {
            case "ssgi", "hybrid_probe_ssgi_rt" -> List.of("gi_ssgi_buffer", "gi_indirect");
            default -> List.of("gi_indirect");
        };
    }

    private static String giModuleBody(RenderFeatureMode active) {
        return switch (sanitizeMode(active).id()) {
            case "probe_grid" -> """
                    float giHash12(vec2 p) {
                        vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                        p3 += dot(p3, p3.yzx + 33.33);
                        return fract((p3.x + p3.y) * p3.z);
                    }
                    vec4 resolveGiIndirect(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec3 c0 = texture(uSceneColor, uv).rgb;
                        vec3 c1 = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 c2 = texture(uSceneColor, clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 c3 = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 c4 = texture(uSceneColor, clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 probeInterp = (c0 * 0.40) + ((c1 + c2 + c3 + c4) * 0.15);
                        float probeBand = smoothstep(0.15, 0.85, giHash12(uv * 64.0));
                        float probeContribution = mix(0.05, 0.14, probeBand);
                        vec3 lifted = mix(baseColor.rgb, probeInterp, 0.18 + probeContribution);
                        return vec4(clamp(lifted, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "rtgi_single" -> """
                    vec4 resolveGiIndirect(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec2 ray = normalize((uv - vec2(0.5)) + vec2(0.0001));
                        vec3 rtAccum = vec3(0.0);
                        float rtWeight = 0.0;
                        for (int i = 0; i < 8; i++) {
                            float t = (float(i) + 1.0) / 8.0;
                            vec2 sampleUv = clamp(uv + ray * texel * (3.0 + t * 14.0), vec2(0.0), vec2(1.0));
                            vec3 c = textureLod(uSceneColor, sampleUv, 0.6 + t * 1.4).rgb;
                            float w = 1.0 - (t * 0.65);
                            rtAccum += c * w;
                            rtWeight += w;
                        }
                        vec3 rtColor = rtWeight > 0.0001 ? rtAccum / rtWeight : baseColor.rgb;
                        float centerWeight = 1.0 - clamp(length((uv - vec2(0.5)) * 1.6), 0.0, 1.0);
                        float rtContribution = mix(0.08, 0.18, centerWeight);
                        vec3 lifted = mix(baseColor.rgb, rtColor, 0.18 + rtContribution * 0.35);
                        return vec4(clamp(lifted, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            case "hybrid_probe_ssgi_rt" -> """
                    float giHash12(vec2 p) {
                        vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                        p3 += dot(p3, p3.yzx + 33.33);
                        return fract((p3.x + p3.y) * p3.z);
                    }
                    vec4 resolveGiIndirect(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec3 center = texture(uSceneColor, uv).rgb;
                        vec3 neighX = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 neighY = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 ssgiInterp = (center * 0.55) + ((neighX + neighY) * 0.225);
                        vec2 ray = normalize((uv - vec2(0.5)) + vec2(0.0001));
                        vec3 rtTap = textureLod(uSceneColor, clamp(uv + ray * texel * 9.0, vec2(0.0), vec2(1.0)), 1.2).rgb;
                        float probeBand = smoothstep(0.10, 0.90, giHash12(uv * 48.0));
                        float centerWeight = 1.0 - clamp(length((uv - vec2(0.5)) * 1.5), 0.0, 1.0);
                        float ssgiContribution = 0.07;
                        float probeContribution = mix(0.04, 0.10, probeBand);
                        float rtContribution = mix(0.03, 0.09, centerWeight);
                        float hybridContribution = ssgiContribution + probeContribution + rtContribution;
                        vec3 hybrid = mix(ssgiInterp, rtTap, 0.35 + rtContribution);
                        vec3 lifted = mix(baseColor.rgb, hybrid, 0.20 + hybridContribution * 0.45);
                        return vec4(clamp(lifted, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
            default -> """
                    vec4 resolveGiIndirect(vec4 baseColor, vec2 uv) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        vec3 c = texture(uSceneColor, uv).rgb;
                        vec3 cx = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 cy = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        float depth = texture(uVelocityColor, uv).b;
                        float horizon = clamp(1.0 - abs(uv.y - 0.5) * 2.0, 0.0, 1.0);
                        float depthAtten = clamp(1.0 - depth * 0.65, 0.15, 1.0);
                        float ssgiContribution = mix(0.05, 0.14, horizon) * depthAtten;
                        vec3 ssgiColor = (c * 0.6) + ((cx + cy) * 0.2);
                        vec3 lifted = mix(baseColor.rgb, ssgiColor, 0.16 + ssgiContribution * 0.50);
                        return vec4(clamp(lifted, vec3(0.0), vec3(1.0)), baseColor.a);
                    }
                    """;
        };
    }

    private static RenderDescriptorRequirement descriptorByTargetSetBinding(String targetPass, int set, int binding) {
        return new RenderDescriptorRequirement(
                targetPass,
                set,
                binding,
                switch (binding) {
                    case 72 -> RenderDescriptorType.UNIFORM_BUFFER;
                    case 73, 75 -> RenderDescriptorType.STORAGE_BUFFER;
                    default -> RenderDescriptorType.COMBINED_IMAGE_SAMPLER;
                },
                RenderBindingFrequency.PER_FRAME,
                false
        );
    }
}
