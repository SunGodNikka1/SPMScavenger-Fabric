package com.noobk.spmscavenger.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class V3CampaignSpatialPolicyTest {

    private static final BlockPos ORIGIN = BlockPos.ZERO;

    @Test
    void coreEnvelopeMarginAndEscapeAreDistinctHorizontalZones() {
        assertEquals(V3CampaignSpatialPolicy.Zone.SCENARIO_CORE, zone(32.0));
        assertEquals(V3CampaignSpatialPolicy.Zone.OBSERVATION_ENVELOPE, zone(32.01));
        assertEquals(V3CampaignSpatialPolicy.Zone.OBSERVATION_ENVELOPE, zone(192.0));
        assertEquals(V3CampaignSpatialPolicy.Zone.ESCAPE_MARGIN, zone(192.01));
        assertEquals(V3CampaignSpatialPolicy.Zone.ESCAPE_MARGIN, zone(224.0));
        assertEquals(V3CampaignSpatialPolicy.Zone.ESCAPED, zone(224.01));
    }

    @Test
    void t3jNeverTurnsSpatialDepartureIntoPrematureIncomplete() {
        assertFalse(V3CampaignSpatialPolicy.spatiallyUninterpretable(
                V3CampaignScenario.MANDATORY_BLOCKS_VILLAGE_WORK,
                V3CampaignSpatialPolicy.Zone.ESCAPED));
        assertTrue(V3CampaignSpatialPolicy.spatiallyUninterpretable(
                V3CampaignScenario.CROP_MANAGED_SINGLE,
                V3CampaignSpatialPolicy.Zone.ESCAPED));
    }

    @Test
    void envelopeIsDerivedFromPinnedExplorationRouteGeometry() throws IOException {
        String exploring = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        assertTrue(exploring.contains("MAX_EXPEDITION_DISTANCE = 150.0"));
        assertEquals(192.0, V3CampaignSpatialPolicy.OBSERVATION_ENVELOPE_RADIUS);
        assertEquals(224.0, V3CampaignSpatialPolicy.ESCAPE_BOUNDARY_RADIUS);
    }

    private static V3CampaignSpatialPolicy.Zone zone(double x) {
        return V3CampaignSpatialPolicy.classify(ORIGIN, new Vec3(x, 300.0, 0.0)).zone();
    }
}
