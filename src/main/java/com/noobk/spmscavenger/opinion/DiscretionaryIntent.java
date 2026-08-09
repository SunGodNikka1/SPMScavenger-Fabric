package com.noobk.spmscavenger.opinion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-4 — adoption-anchored discretionary authority record.
 */
public final class DiscretionaryIntent {

    private final UUID intentId;
    private final DiscretionaryActivity activity;
    private IntentLifecycle lifecycle;
    private final float selectedUtility;
    private final float runnerUpUtility;
    private final long scoredAtTick;
    private long issuedAtTick;
    private long adoptedAtTick;
    private long commitmentUntilTick;
    private InvalidationCause invalidationCause;

    public DiscretionaryIntent(
            UUID intentId,
            DiscretionaryActivity activity,
            IntentLifecycle lifecycle,
            float selectedUtility,
            float runnerUpUtility,
            long scoredAtTick,
            long issuedAtTick) {
        this.intentId = Objects.requireNonNull(intentId, "intentId");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.selectedUtility = selectedUtility;
        this.runnerUpUtility = runnerUpUtility;
        this.scoredAtTick = scoredAtTick;
        this.issuedAtTick = issuedAtTick;
        this.invalidationCause = InvalidationCause.NONE;
    }

    public static DiscretionaryIntent pending(
            DiscretionaryActivity activity,
            float selectedUtility,
            float runnerUpUtility,
            long gameTime) {
        return new DiscretionaryIntent(
                UUID.randomUUID(),
                activity,
                IntentLifecycle.PENDING,
                selectedUtility,
                runnerUpUtility,
                gameTime,
                gameTime);
    }

    public UUID intentId() {
        return intentId;
    }

    public DiscretionaryActivity activity() {
        return activity;
    }

    public IntentLifecycle lifecycle() {
        return lifecycle;
    }

    public float selectedUtility() {
        return selectedUtility;
    }

    public float runnerUpUtility() {
        return runnerUpUtility;
    }

    public long scoredAtTick() {
        return scoredAtTick;
    }

    public long issuedAtTick() {
        return issuedAtTick;
    }

    public long adoptedAtTick() {
        return adoptedAtTick;
    }

    public long commitmentUntilTick() {
        return commitmentUntilTick;
    }

    public InvalidationCause invalidationCause() {
        return invalidationCause;
    }

    public boolean isActive() {
        return lifecycle.isActive();
    }

    void markAdopted(long gameTime) {
        lifecycle = IntentLifecycle.ADOPTED;
        adoptedAtTick = gameTime == 0L ? 1L : gameTime;
        commitmentUntilTick =
                adoptedAtTick + DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS;
    }

    void markRunning() {
        if (lifecycle == IntentLifecycle.ADOPTED || lifecycle == IntentLifecycle.PENDING) {
            lifecycle = IntentLifecycle.RUNNING;
        }
    }

    void markTerminal(IntentLifecycle terminal, InvalidationCause cause) {
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException("not terminal: " + terminal);
        }
        lifecycle = terminal;
        invalidationCause = cause == null ? InvalidationCause.NONE : cause;
    }

    boolean isExpiredPending(long gameTime) {
        return lifecycle == IntentLifecycle.PENDING
                && gameTime - issuedAtTick > DiscretionaryDirectorConstants.PENDING_INTENT_TTL_TICKS;
    }

    boolean isWithinCommitment(long gameTime) {
        return lifecycle.isActive()
                && adoptedAtTick > 0L
                && gameTime < commitmentUntilTick;
    }

    Optional<DiscretionaryActivity> activityIfActive() {
        return isActive() ? Optional.of(activity) : Optional.empty();
    }
}
