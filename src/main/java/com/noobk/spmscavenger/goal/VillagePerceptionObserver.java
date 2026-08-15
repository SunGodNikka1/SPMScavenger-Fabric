package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.village.VillagePerceptionScheduler;
import com.noobk.spmscavenger.village.VillagePerceptionTuning;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Flagless per-mob eligibility observer for V1-D village perception (D-VR-033).
 *
 * <p>Marks observation dirty on chunk transition or heartbeat, then enqueues a deduplicated scheduler
 * request. It never claims MOVE or LOOK and does not call {@link com.noobk.spmscavenger.village.VillagePerception}
 * directly — the server scheduler owns the POI query budget.
 */
public final class VillagePerceptionObserver extends Goal {

    public static final int PRIORITY = 9;

    private final Mob mob;
    private final PhasedScanClock heartbeatClock;
    private boolean dirty;
    private long lastEnqueueTick = Long.MIN_VALUE;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private ResourceKey<Level> lastDimension;

    public VillagePerceptionObserver(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
        this.heartbeatClock = new PhasedScanClock(
                mob.getId(),
                VillagePerceptionTuning.HEARTBEAT_TICKS,
                VillagePerceptionTuning.OBSERVER_GOAL_SALT);
        this.lastDimension = mob.level().dimension();
        markDirty();
    }

    @Override
    public boolean canUse() {
        return ScavengerConfig.get().enabled && mob.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            lastChunkX = Integer.MIN_VALUE;
            lastChunkZ = Integer.MIN_VALUE;
            markDirty();
        }
        int chunkX = mob.chunkPosition().x;
        int chunkZ = mob.chunkPosition().z;
        if (lastChunkX != chunkX || lastChunkZ != chunkZ) {
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
            markDirty();
        }
        long gameTime = level.getGameTime();
        if (heartbeatClock.claim(gameTime)) {
            markDirty();
        }
        if (!dirty) {
            return;
        }
        if (gameTime - lastEnqueueTick < VillagePerceptionTuning.DEBOUNCE_TICKS) {
            return;
        }
        lastEnqueueTick = gameTime;
        if (VillagePerceptionScheduler.forServer(level.getServer())
                .requestObservation(level, mob.getUUID())) {
            clearDirtyAfterEnqueue();
        }
    }

    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }

    void clearDirtyAfterEnqueue() {
        dirty = false;
    }
}
