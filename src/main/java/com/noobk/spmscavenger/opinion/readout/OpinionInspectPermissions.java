package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * GAO-8B Task 42B — server-side inspect permission and target validation (PD-GAO-14, D-GAO-040).
 */
public final class OpinionInspectPermissions {

    public static final double MAX_INSPECT_DISTANCE_SQR = 16.0 * 16.0;

    private OpinionInspectPermissions() {
    }

    public static boolean mayRequest(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    public static OpinionInspectRejectReason validateTarget(ServerPlayer player, Entity entity) {
        if (!PlayerMobs.available()) {
            return OpinionInspectRejectReason.SPM_UNAVAILABLE;
        }
        if (entity == null || !entity.isAlive()) {
            return entity == null
                    ? OpinionInspectRejectReason.ENTITY_NOT_FOUND
                    : OpinionInspectRejectReason.NOT_ALIVE;
        }
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            return OpinionInspectRejectReason.NOT_PLAYER_MOB;
        }
        if (entity.level() != player.level()) {
            return OpinionInspectRejectReason.WRONG_DIMENSION;
        }
        Vec3 eye = player.getEyePosition();
        if (eye.distanceToSqr(entity.position()) > MAX_INSPECT_DISTANCE_SQR) {
            return OpinionInspectRejectReason.OUT_OF_RANGE;
        }
        return OpinionInspectRejectReason.NONE;
    }
}
