package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;

/**
 * Presence / familiarity accumulation radius (D-VR-040). Distinct from
 * {@link VillageIdentityPolicy} (48) and {@link RaidAssociationPolicy} (96).
 */
public final class SettlementBoundsPolicy {

    private static final int PRESENCE_RADIUS_SQR =
            VillagePerception.VILLAGE_QUERY_RADIUS * VillagePerception.VILLAGE_QUERY_RADIUS;

    private SettlementBoundsPolicy() {
    }

    public static boolean within(BlockPos mobPos, BlockPos anchor) {
        return mobPos != null && anchor != null && mobPos.distSqr(anchor) <= PRESENCE_RADIUS_SQR;
    }
}
