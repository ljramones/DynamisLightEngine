package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

        VulkanEngineRuntime.LightingConfig config = VulkanEngineRuntimeLightingMapper.mapLighting(lights);

        assertEquals(2, config.localLightCount());
        assertEquals(32, config.localLightPosRange().length);
        assertEquals(32, config.localLightColorIntensity().length);
        assertTrue(config.shadowPointCastsShadows());
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

        VulkanEngineRuntime.LightingConfig config = VulkanEngineRuntimeLightingMapper.mapLighting(lights);

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

        VulkanEngineRuntime.ShadowRenderConfig low = VulkanEngineRuntimeLightingMapper.mapShadows(lights, org.dynamislight.api.config.QualityTier.LOW);
        VulkanEngineRuntime.ShadowRenderConfig ultra = VulkanEngineRuntimeLightingMapper.mapShadows(lights, org.dynamislight.api.config.QualityTier.ULTRA);

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
    }
}
