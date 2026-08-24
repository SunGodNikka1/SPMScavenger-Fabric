package com.noobk.spmscavenger.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Task-59-only spatial interpretation; it never steers or constrains the subject. */
final class V3CampaignSpatialPolicy {

    static final double SCENARIO_CORE_RADIUS = 32.0;
    // Core 32 + production ExploringGoal route cap 150 = 182; round to a 12-chunk radius.
    static final double OBSERVATION_ENVELOPE_RADIUS = 192.0;
    // One additional core width before geometry-dependent evidence becomes uninterpretable.
    static final double ESCAPE_BOUNDARY_RADIUS = 224.0;

    enum Zone {
        SCENARIO_CORE,
        OBSERVATION_ENVELOPE,
        ESCAPE_MARGIN,
        ESCAPED
    }

    record Result(Zone zone, double horizontalDistance) {
    }

    private V3CampaignSpatialPolicy() {
    }

    static Result classify(BlockPos origin, Vec3 position) {
        double dx = position.x - origin.getX();
        double dz = position.z - origin.getZ();
        double distanceSquared = dx * dx + dz * dz;
        double coreSquared = SCENARIO_CORE_RADIUS * SCENARIO_CORE_RADIUS;
        double envelopeSquared = OBSERVATION_ENVELOPE_RADIUS * OBSERVATION_ENVELOPE_RADIUS;
        double escapeSquared = ESCAPE_BOUNDARY_RADIUS * ESCAPE_BOUNDARY_RADIUS;
        Zone zone;
        if (distanceSquared <= coreSquared) {
            zone = Zone.SCENARIO_CORE;
        } else if (distanceSquared <= envelopeSquared) {
            zone = Zone.OBSERVATION_ENVELOPE;
        } else if (distanceSquared <= escapeSquared) {
            zone = Zone.ESCAPE_MARGIN;
        } else {
            zone = Zone.ESCAPED;
        }
        return new Result(zone, Math.sqrt(distanceSquared));
    }

    static boolean spatiallyUninterpretable(V3CampaignScenario scenario, Zone zone) {
        return zone == Zone.ESCAPED
                && scenario != V3CampaignScenario.MANDATORY_BLOCKS_VILLAGE_WORK;
    }
}
