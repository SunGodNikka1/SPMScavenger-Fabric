package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.village.SettlementRelationshipService;
import com.noobk.spmscavenger.village.VillagePerceptionScheduler;
import com.noobk.spmscavenger.village.VillagePerceptionTuning;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * Flagless per-mob eligibility observer for V1-D village perception (D-VR-033).
 *
 * <p>Marks observation dirty on chunk transition or heartbeat, then enqueues a deduplicated scheduler
 * request. It never claims MOVE or LOOK and does not call {@link com.noobk.spmscavenger.village.VillagePerception}
 * directly — the server scheduler owns the POI query budget.
 *
 * <p>This subclasses {@link RandomLookAroundGoal} solely because SPM 0.86.0's objective readout
 * filters that host type as cosmetic/background noise. The inherited look lifecycle is disabled;
 * only this observer's perception tick executes.
 */
public final class VillagePerceptionObserver extends RandomLookAroundGoal {

    public static final int PRIORITY = 9;

    private final Mob mob;
    private final PhasedScanClock heartbeatClock;
    private final VillagePerceptionEnqueueDebounce enqueueDebounce;
    private boolean dirty;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private ResourceKey<Level> lastDimension;

    public VillagePerceptionObserver(Mob mob) {
        this(mob, new VillagePerceptionEnqueueDebounce(), new PhasedScanClock(
                mob.getId(),
                VillagePerceptionTuning.HEARTBEAT_TICKS,
                VillagePerceptionTuning.OBSERVER_GOAL_SALT));
    }

    VillagePerceptionObserver(Mob mob, VillagePerceptionEnqueueDebounce enqueueDebounce, PhasedScanClock heartbeatClock) {
        super(mob);
        this.mob = mob;
        this.enqueueDebounce = enqueueDebounce;
        this.heartbeatClock = heartbeatClock;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
        this.lastDimension = mob == null ? Level.OVERWORLD : mob.level().dimension();
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

    /** Do not inherit vanilla look behavior; only SPM's readout classification is used. */
    @Override
    public void start() {
    }

    @Override
    public void stop() {
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
            SettlementRelationshipService.onPresenceHeartbeat(
                    level, mob.getUUID(), mob.blockPosition(), gameTime);
        }
        enqueueIfDirty(gameTime, () -> VillagePerceptionScheduler.forServer(level.getServer())
                .requestObservation(level, mob.getUUID()));
    }

    /**
     * Enqueue path extracted for unit tests. Returns {@code true} when a request was admitted and the
     * dirty marker cleared.
     */
    boolean enqueueIfDirty(long gameTime, BooleanSupplier requestObservation) {
        if (!dirty) {
            return false;
        }
        if (enqueueDebounce.shouldBlock(gameTime, VillagePerceptionTuning.DEBOUNCE_TICKS)) {
            return false;
        }
        enqueueDebounce.recordEnqueue(gameTime);
        if (requestObservation.getAsBoolean()) {
            clearDirtyAfterEnqueue();
            return true;
        }
        return false;
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
