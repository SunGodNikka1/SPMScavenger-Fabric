package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/**
 * Vanilla local wandering with one extra signal: only a naturally completed trip that actually
 * displaced the mob counts toward exploration readiness. An interruption never counts as a trip.
 */
public final class TrackedLocalWanderGoal extends WaterAvoidingRandomStrollGoal {

    private static final double MINIMUM_COMPLETED_DISTANCE = 4.0;

    private final PathfinderMob mob;
    private final ExplorationReadiness readiness;
    private double startX;
    private double startZ;
    private boolean completedNaturally;

    public TrackedLocalWanderGoal(
            PathfinderMob mob, double speedModifier, ExplorationReadiness readiness) {
        super(mob, speedModifier);
        this.mob = mob;
        this.readiness = readiness;
    }

    @Override
    public void start() {
        startX = mob.getX();
        startZ = mob.getZ();
        completedNaturally = false;
        ScavengerConfig cfg = ScavengerConfig.get();
        double speed = cfg.enabled
                ? Math.max(0.5, Math.min(1.2, cfg.localWanderSpeed))
                : 0.6;
        mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speed);
    }

    @Override
    public boolean canContinueToUse() {
        boolean continuing = super.canContinueToUse();
        if (!continuing && mob.getNavigation().isDone()) {
            double dx = mob.getX() - startX;
            double dz = mob.getZ() - startZ;
            completedNaturally = ExplorationPolicy.meaningfulLocalTrip(
                    dx * dx + dz * dz, MINIMUM_COMPLETED_DISTANCE);
        }
        return continuing;
    }

    @Override
    public void stop() {
        if (completedNaturally) {
            readiness.recordSuccessfulLocalTrip();
        }
        completedNaturally = false;
        super.stop();
    }
}
