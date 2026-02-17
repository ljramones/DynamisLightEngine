package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanEngineRuntimeLightingMapperTest {
    @Test
    void mapLightingPacksMultiplePointAndSpotLights() {
        List<LightDesc> lights = List.of(
                new LightDesc("sun", new Vec3(0f, 10f, 0f), new Vec3(1f, 1f, 1f), 1.0f, 200f, true, null, LightType.DIRECTIONAL, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointA", new Vec3(1f, 2f, 3f), new Vec3(0.9f, 0.1f, 0.1f), 3.5f, 12f, false, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotB", new Vec3(4f, 3f, 2f), new Vec3(0.1f, 0.9f, 0.1f), 2.0f, 18f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.LightingConfig config = VulkanEngineRuntimeLightingMapper.mapLighting(lights, org.dynamislight.api.config.QualityTier.HIGH, 0);

        assertEquals(2, config.localLightCount());
        assertEquals(32, config.localLightPosRange().length);
        assertEquals(32, config.localLightColorIntensity().length);
        boolean hasLayerAssignment = config.localLightOuterTypeShadow()[3] > 0.5f
                || config.localLightOuterTypeShadow()[7] > 0.5f;
        assertTrue(hasLayerAssignment);
        assertFalse(config.shadowPointCastsShadows());
        assertTrue(config.shadowPointIsSpot());
        assertEquals(18f, config.shadowPointRange(), 0.0001f);
    }

    @Test
    void mapLightingClampsLocalLightCountToRuntimeLimit() {
        List<LightDesc> lights = new ArrayList<>();
        lights.add(new LightDesc("sun", new Vec3(0f, 10f, 0f), new Vec3(1f, 1f, 1f), 1.0f, 200f, true, null, LightType.DIRECTIONAL, new Vec3(0f, -1f, 0f), 15f, 30f));
        for (int i = 0; i < 20; i++) {
            lights.add(new LightDesc(
                    "p" + i,
                    new Vec3(i, 1f, 2f),
                    new Vec3(1f, 0.8f, 0.2f),
                    1.0f + i,
                    8f + i,
                    (i % 4) == 0,
                    null,
                    (i % 2) == 0 ? LightType.POINT : LightType.SPOT,
                    new Vec3(0f, -1f, 0f),
                    20f,
                    30f
            ));
        }

        VulkanEngineRuntime.LightingConfig config = VulkanEngineRuntimeLightingMapper.mapLighting(lights, org.dynamislight.api.config.QualityTier.HIGH, 0);

        assertEquals(VulkanContext.MAX_LOCAL_LIGHTS, config.localLightCount());
        for (int i = config.localLightCount(); i < VulkanContext.MAX_LOCAL_LIGHTS; i++) {
            int offset = i * 4;
            assertEquals(0f, config.localLightPosRange()[offset + 3], 0.0001f);
            assertEquals(0f, config.localLightColorIntensity()[offset + 3], 0.0001f);
        }
    }

    @Test
    void mapShadowsAppliesTierBudgetAndPrimarySelectionPolicy() {
        List<LightDesc> lights = List.of(
                new LightDesc("sun", new Vec3(0f, 10f, 0f), new Vec3(1f, 1f, 1f), 1.2f, 220f, true, null, LightType.DIRECTIONAL, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointA", new Vec3(2f, 1f, 1f), new Vec3(1f, 0.8f, 0.6f), 2.0f, 12f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotB", new Vec3(-2f, 3f, 2f), new Vec3(0.7f, 0.9f, 1f), 2.4f, 18f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 18f, 32f)
        );

        VulkanEngineRuntime.ShadowRenderConfig low = VulkanEngineRuntimeLightingMapper.mapShadows(lights, org.dynamislight.api.config.QualityTier.LOW, "pcf", false, "off", 0, 0);
        VulkanEngineRuntime.ShadowRenderConfig ultra = VulkanEngineRuntimeLightingMapper.mapShadows(lights, org.dynamislight.api.config.QualityTier.ULTRA, "evsm", true, "optional", 0, 0);

        assertTrue(low.enabled());
        assertEquals("sun", low.primaryShadowLightId());
        assertEquals("directional", low.primaryShadowType());
        assertEquals(1, low.maxShadowedLocalLights());
        assertEquals(1, low.selectedLocalShadowLights());
        assertEquals(4, ultra.maxShadowedLocalLights());
        assertEquals(2, ultra.selectedLocalShadowLights());
        assertTrue(ultra.atlasCapacityTiles() > 0);
        assertTrue(ultra.atlasAllocatedTiles() >= 0);
        assertTrue(ultra.atlasUtilization() >= 0.0f);
        assertTrue(ultra.atlasUtilization() <= 1.0f);
        assertTrue(ultra.atlasMemoryBytesD16() > 0L);
        assertTrue(ultra.atlasMemoryBytesD32() > ultra.atlasMemoryBytesD16());
        assertTrue(ultra.shadowUpdateBytesEstimate() >= 0L);
        assertTrue(ultra.normalBiasScale() >= 1.0f);
        assertTrue(ultra.slopeBiasScale() >= 1.0f);
        assertEquals("evsm", ultra.filterPath());
        assertEquals("evsm", ultra.runtimeFilterPath());
        assertFalse(ultra.momentFilterEstimateOnly());
        assertTrue(ultra.momentPipelineRequested());
        assertFalse(ultra.momentPipelineActive());
        assertTrue(ultra.contactShadowsRequested());
        assertEquals("optional", ultra.rtShadowMode());
        assertTrue(ultra.shadowMomentAtlasBytesEstimate() > 0L);

        VulkanEngineRuntime.ShadowRenderConfig pcss = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcss", false, "off", 0, 0
        );
        assertEquals("pcss", pcss.filterPath());
        assertEquals("pcss", pcss.runtimeFilterPath());
        assertFalse(pcss.momentFilterEstimateOnly());
        assertFalse(pcss.momentPipelineRequested());
        assertFalse(pcss.momentPipelineActive());
    }

    @Test
    void mapShadowsCapsPointCubemapConcurrencyByTierLayerBudget() {
        List<LightDesc> lights = List.of(
                new LightDesc("pointA", new Vec3(1f, 1f, 1f), new Vec3(1f, 0.9f, 0.8f), 3.0f, 20f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointB", new Vec3(2f, 1f, 1f), new Vec3(0.8f, 0.9f, 1f), 2.5f, 18f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointC", new Vec3(3f, 1f, 1f), new Vec3(0.9f, 0.8f, 1f), 2.2f, 16f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.ShadowRenderConfig high = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcf", false, "off", 0, 0
        );
        VulkanEngineRuntime.ShadowRenderConfig ultra = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.ULTRA, "pcf", false, "off", 0, 0
        );

        assertEquals("point", high.primaryShadowType());
        assertEquals(6, high.cascadeCount());
        assertEquals(1, high.renderedPointShadowCubemaps());
        assertEquals(12, ultra.cascadeCount());
        assertEquals(2, ultra.renderedPointShadowCubemaps());
    }

    @Test
    void mapShadowsRespectsShadowSchedulerOverrides() {
        List<LightDesc> lights = List.of(
                new LightDesc("pointA", new Vec3(1f, 1f, 1f), new Vec3(1f, 0.9f, 0.8f), 3.0f, 20f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointB", new Vec3(2f, 1f, 1f), new Vec3(0.8f, 0.9f, 1f), 2.5f, 18f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.ShadowRenderConfig highDefault = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcf", false, "off", 0, 0
        );
        VulkanEngineRuntime.ShadowRenderConfig highOverridden = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcf", false, "off", 12, 12
        );
        VulkanEngineRuntime.ShadowRenderConfig highFacesCapped = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcf", false, "off", 12, 6
        );

        assertEquals(6, highDefault.cascadeCount());
        assertEquals(1, highDefault.renderedPointShadowCubemaps());
        assertEquals(12, highOverridden.cascadeCount());
        assertEquals(2, highOverridden.renderedPointShadowCubemaps());
        assertEquals(6, highFacesCapped.cascadeCount());
        assertEquals(1, highFacesCapped.renderedPointShadowCubemaps());
    }

    @Test
    void mapShadowsCadenceCanThrottleDistantUpdates() {
        List<LightDesc> lights = List.of(
                new LightDesc("pointA", new Vec3(1f, 1f, 1f), new Vec3(1f, 0.9f, 0.8f), 3.0f, 20f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointB", new Vec3(2f, 1f, 1f), new Vec3(0.8f, 0.9f, 1f), 2.5f, 18f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotC", new Vec3(3f, 2f, 1f), new Vec3(0.9f, 0.8f, 1f), 2.2f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.ShadowRenderConfig cadenceOff = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.ULTRA, "pcf", false, "off",
                0, 12, 12, false, 1, 2, 4, 1L, java.util.Map.of()
        );
        VulkanEngineRuntime.ShadowRenderConfig cadenceOn = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.ULTRA, "pcf", false, "off",
                0, 12, 12, true, 4, 4, 8, 1L, java.util.Map.of()
        );

        assertTrue(cadenceOff.renderedLocalShadowLights() >= cadenceOn.renderedLocalShadowLights());
        assertTrue(cadenceOn.deferredShadowLightCount() >= 1);
        assertFalse(cadenceOn.deferredShadowLightIdsCsv().isBlank());
    }

    @Test
    void mapShadowsRespectsMaxShadowedLocalLightsOverride() {
        List<LightDesc> lights = List.of(
                new LightDesc("spot1", new Vec3(0f, 2f, 0f), new Vec3(1f, 1f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spot2", new Vec3(1f, 2f, 0f), new Vec3(1f, 1f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spot3", new Vec3(2f, 2f, 0f), new Vec3(1f, 1f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spot4", new Vec3(3f, 2f, 0f), new Vec3(1f, 1f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spot5", new Vec3(4f, 2f, 0f), new Vec3(1f, 1f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spot6", new Vec3(5f, 2f, 0f), new Vec3(1f, 1f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.ShadowRenderConfig highDefault = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcf", false, "off", 0, 0, 0
        );
        VulkanEngineRuntime.ShadowRenderConfig highOverride = VulkanEngineRuntimeLightingMapper.mapShadows(
                lights, org.dynamislight.api.config.QualityTier.HIGH, "pcf", false, "off", 6, 24, 24
        );

        assertEquals(3, highDefault.maxShadowedLocalLights());
        assertEquals(6, highOverride.maxShadowedLocalLights());
        assertTrue(highOverride.selectedLocalShadowLights() >= highDefault.selectedLocalShadowLights());
    }

    @Test
    void mapLightingReusesShadowLayerAssignmentsAcrossStableFrames() {
        List<LightDesc> lights = List.of(
                new LightDesc("spotA", new Vec3(0f, 2f, 0f), new Vec3(1f, 0.9f, 0.8f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotB", new Vec3(1f, 2f, 0f), new Vec3(0.8f, 0.9f, 1f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotC", new Vec3(2f, 2f, 0f), new Vec3(0.9f, 1f, 0.8f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.LightingConfig first = VulkanEngineRuntimeLightingMapper.mapLighting(
                lights, org.dynamislight.api.config.QualityTier.ULTRA,
                4, 12, false, 1, 2, 4, 1L, Map.of(), Map.of()
        );
        VulkanEngineRuntime.LightingConfig second = VulkanEngineRuntimeLightingMapper.mapLighting(
                lights, org.dynamislight.api.config.QualityTier.ULTRA,
                4, 12, false, 1, 2, 4, 2L, Map.of(), first.shadowLayerAssignments()
        );

        assertEquals(first.shadowLayerAssignments(), second.shadowLayerAssignments());
        assertTrue(second.shadowAllocatorReusedAssignments() >= first.shadowLayerAssignments().size());
        assertEquals(0, second.shadowAllocatorEvictions());
    }

    @Test
    void mapLightingEvictsOutOfBudgetAssignmentAndReassignsLayer() {
        List<LightDesc> lights = List.of(
                new LightDesc("spotA", new Vec3(0f, 2f, 0f), new Vec3(1f, 0.9f, 0.8f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        VulkanEngineRuntime.LightingConfig config = VulkanEngineRuntimeLightingMapper.mapLighting(
                lights, org.dynamislight.api.config.QualityTier.HIGH,
                1, 4, false, 1, 2, 4, 1L, Map.of(), Map.of("spotA", 8)
        );

        assertEquals(1, config.shadowAllocatorAssignedLights());
        assertEquals(0, config.shadowAllocatorReusedAssignments());
        assertEquals(1, config.shadowAllocatorEvictions());
        assertEquals(1, config.shadowLayerAssignments().get("spotA"));
    }

    @Test
    void mapLightingReusesAssignmentsWhenLightOrderChanges() {
        List<LightDesc> ordered = List.of(
                new LightDesc("spotA", new Vec3(0f, 2f, 0f), new Vec3(1f, 0.9f, 0.8f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotB", new Vec3(1f, 2f, 0f), new Vec3(0.8f, 0.9f, 1f), 1.7f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );
        List<LightDesc> reordered = List.of(ordered.get(1), ordered.get(0));

        VulkanEngineRuntime.LightingConfig first = VulkanEngineRuntimeLightingMapper.mapLighting(
                ordered, org.dynamislight.api.config.QualityTier.HIGH,
                3, 8, false, 1, 2, 4, 1L, Map.of(), Map.of()
        );
        VulkanEngineRuntime.LightingConfig second = VulkanEngineRuntimeLightingMapper.mapLighting(
                reordered, org.dynamislight.api.config.QualityTier.HIGH,
                3, 8, false, 1, 2, 4, 2L, Map.of(), first.shadowLayerAssignments()
        );

        assertEquals(first.shadowLayerAssignments(), second.shadowLayerAssignments());
        assertTrue(second.shadowAllocatorReusedAssignments() >= 2);
        assertEquals(0, second.shadowAllocatorEvictions());
    }

    @Test
    void mapShadowsCadenceRotatesRenderedAndDeferredSetsAcrossTicks() {
        List<LightDesc> lights = List.of(
                new LightDesc("spotA", new Vec3(0f, 2f, 0f), new Vec3(1f, 0.9f, 0.8f), 2.0f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotB", new Vec3(1f, 2f, 0f), new Vec3(0.8f, 0.9f, 1f), 1.9f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("spotC", new Vec3(2f, 2f, 0f), new Vec3(0.9f, 1f, 0.8f), 1.8f, 16f, true, null, LightType.SPOT, new Vec3(0f, -1f, 0f), 15f, 30f),
                new LightDesc("pointD", new Vec3(3f, 2f, 0f), new Vec3(0.9f, 0.8f, 1f), 1.7f, 16f, true, null, LightType.POINT, new Vec3(0f, -1f, 0f), 15f, 30f)
        );

        Map<String, Long> lastRenderedTicks = new HashMap<>();
        Set<String> renderedSets = new HashSet<>();
        Set<String> deferredSets = new HashSet<>();
        for (long tick = 1; tick <= 8; tick++) {
            VulkanEngineRuntime.ShadowRenderConfig cfg = VulkanEngineRuntimeLightingMapper.mapShadows(
                    lights, org.dynamislight.api.config.QualityTier.ULTRA, "pcf", false, "off",
                    3, 8, 8, true, 1, 2, 4, tick, lastRenderedTicks
            );
            renderedSets.add(cfg.renderedShadowLightIdsCsv());
            deferredSets.add(cfg.deferredShadowLightIdsCsv());
            if (!cfg.renderedShadowLightIdsCsv().isBlank()) {
                for (String id : cfg.renderedShadowLightIdsCsv().split(",")) {
                    String normalized = id == null ? "" : id.trim();
                    if (!normalized.isEmpty()) {
                        lastRenderedTicks.put(normalized, tick);
                    }
                }
            }
        }

        assertTrue(renderedSets.size() > 1);
        assertTrue(deferredSets.size() > 1);
    }
}
