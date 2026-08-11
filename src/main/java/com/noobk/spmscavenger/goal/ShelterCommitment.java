package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent intent for one shelter trip. Navigation paths are deliberately absent: Minecraft may
 * discard them whenever a higher-priority Goal takes MOVE, while this bounded commitment survives
 * a benign interruption and creates a fresh path on resume.
 */
final class ShelterCommitment {

    static final int MAX_ACTIVE_APPROACH_TICKS = 400;
    static final long MAX_PRE_ARRIVAL_TICKS = 600L;
    static final int MAX_PATH_FAILURES = 3;

    enum State {
        PENDING,
        ACTIVE,
        SUSPENDED,
        ARRIVED
    }

    private final UUID commitmentId;
    private final BlockPos destination;
    private final Optional<BlockPos> bedPos;
    private final ShelterSelectionPolicy.Tier shelterTier;
    private final UUID claimant;
    private final long startedAt;
    private int activeApproachTicks;
    private int pathFailureCount;
    private int resumeAttempts;
    private State state;
    private boolean restClaimOpened;

    ShelterCommitment(BlockPos destination, BlockPos bedPos, UUID claimant, long startedAt) {
        this(UUID.randomUUID(), destination, bedPos, ShelterSelectionPolicy.Tier.PORCH_OVERHANG,
                claimant, startedAt);
    }

    ShelterCommitment(
            UUID commitmentId,
            BlockPos destination,
            BlockPos bedPos,
            ShelterSelectionPolicy.Tier shelterTier,
            UUID claimant,
            long startedAt) {
        this.commitmentId = Objects.requireNonNull(commitmentId, "commitmentId");
        this.destination = Objects.requireNonNull(destination, "destination").immutable();
        this.bedPos = Optional.ofNullable(bedPos).map(BlockPos::immutable);
        this.shelterTier = Objects.requireNonNull(shelterTier, "shelterTier");
        this.claimant = Objects.requireNonNull(claimant, "claimant");
        this.startedAt = startedAt;
        this.state = State.PENDING;
    }

    UUID commitmentId() {
        return commitmentId;
    }

    BlockPos destination() {
        return destination;
    }

    Optional<BlockPos> bedPos() {
        return bedPos;
    }

    ShelterSelectionPolicy.Tier shelterTier() {
        return shelterTier;
    }

    UUID claimant() {
        return claimant;
    }

    long startedAt() {
        return startedAt;
    }

    int activeApproachTicks() {
        return activeApproachTicks;
    }

    int pathFailureCount() {
        return pathFailureCount;
    }

    int resumeAttempts() {
        return resumeAttempts;
    }

    State state() {
        return state;
    }

    boolean restClaimOpened() {
        return restClaimOpened;
    }

    void activate() {
        if (state == State.SUSPENDED) {
            resumeAttempts++;
        }
        if (state != State.ARRIVED) {
            state = State.ACTIVE;
        }
    }

    void suspend() {
        if (state != State.ARRIVED) {
            state = State.SUSPENDED;
        }
    }

    void recordActiveApproachTick() {
        if (state == State.ACTIVE) {
            activeApproachTicks++;
        }
    }

    void recordPathFailure() {
        pathFailureCount++;
    }

    void arrive() {
        state = State.ARRIVED;
    }

    void markRestClaimOpened() {
        restClaimOpened = true;
    }

    boolean approachBudgetExhausted(long now) {
        return state != State.ARRIVED
                && (activeApproachTicks >= MAX_ACTIVE_APPROACH_TICKS
                        || now - startedAt >= MAX_PRE_ARRIVAL_TICKS
                        || pathFailureCount >= MAX_PATH_FAILURES);
    }
}
