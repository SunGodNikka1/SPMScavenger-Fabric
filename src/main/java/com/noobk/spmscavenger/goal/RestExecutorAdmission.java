package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.opinion.ActivityAdmission;
import com.noobk.spmscavenger.opinion.ActivityAdoptionBlocker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * GAO-4R — pure REST adoption feasibility for Opinion decision-time scoring.
 *
 * <p>Does not call {@link CampfireGoal#canUse()} and does not mutate scan clocks. Shares policy
 * with {@link CampfireGoal} through {@link RestCampfireFeasibility}.
 */
public final class RestExecutorAdmission {

    private RestExecutorAdmission() {
    }

    /**
     * Fast admission probe used by the Opinion observer.
     *
     * @param cachedFirePos optional in-flight fire position from a running {@link CampfireGoal}
     * @param cachedIdlePos optional in-flight idle position from a running {@link CampfireGoal}
     */
    public static ActivityAdmission inspect(
            Mob mob,
            long gameTime,
            PhasedScanClock scanClock,
            @Nullable BlockPos cachedFirePos,
            @Nullable BlockPos cachedIdlePos) {
        if (!RestCampfireFeasibility.featureEnabled()) {
            return ActivityAdmission.executorAbsent();
        }
        if (!ShelterActivityEnvelope.permitsVoluntaryDisplacement(mob)) {
            return ActivityAdmission.blocked(
                    true, ActivityAdoptionBlocker.SHELTER_AUTHORITY, "");
        }
        if (mob.getTarget() != null) {
            return ActivityAdmission.blocked(true, ActivityAdoptionBlocker.COMBAT, "");
        }

        Level level = mob.level();
        if (cachedFirePos != null && cachedIdlePos != null) {
            if (mob.blockPosition().distSqr(cachedIdlePos) > RestCampfireFeasibility.ARRIVED_SQR) {
                return ActivityAdmission.ready(true);
            }
        }

        if (RestCampfireFeasibility.carriesCampfire(mob)) {
            if (!RestCampfireFeasibility.canPlaceCampfire(level)) {
                return ActivityAdmission.blocked(
                        true, ActivityAdoptionBlocker.MOB_GRIEFING_DISABLED, "");
            }
            BlockPos placement = RestCampfireFeasibility.spotBeside(level, mob.blockPosition());
            if (placement == null) {
                return ActivityAdmission.blocked(
                        true, ActivityAdoptionBlocker.NO_VALID_REST_POSITION, "placement");
            }
            return ActivityAdmission.ready(true);
        }

        if (!scanClock.isDue(gameTime)) {
            long remaining = scanClock.ticksUntilDue(gameTime);
            return ActivityAdmission.blocked(
                    true,
                    ActivityAdoptionBlocker.SCAN_COOLDOWN,
                    remaining + " ticks until scan");
        }

        BlockPos firePos = RestCampfireFeasibility.findCampfire(level, mob.blockPosition());
        if (firePos == null) {
            return ActivityAdmission.blocked(
                    true, ActivityAdoptionBlocker.NO_CAMPFIRE_ITEM, "no nearby campfire or carried item");
        }
        BlockPos idlePos = RestCampfireFeasibility.spotBeside(level, firePos);
        if (idlePos == null) {
            return ActivityAdmission.blocked(
                    true, ActivityAdoptionBlocker.NO_VALID_REST_POSITION, "beside existing fire");
        }
        if (mob.blockPosition().distSqr(idlePos) <= RestCampfireFeasibility.ARRIVED_SQR) {
            return ActivityAdmission.blocked(
                    true, ActivityAdoptionBlocker.NO_CAMPFIRE_AVAILABLE, "already at fire");
        }
        return ActivityAdmission.ready(true);
    }
}
