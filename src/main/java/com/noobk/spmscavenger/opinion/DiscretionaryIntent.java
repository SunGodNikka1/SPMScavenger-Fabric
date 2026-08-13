package com.noobk.spmscavenger.opinion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-4 — adoption-anchored discretionary authority record.
 */
public final class DiscretionaryIntent {

    private final UUID intentId;
    private final long decisionId;
    private final DiscretionaryActivity activity;
    private final SocialIntent socialSubject;
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
            long decisionId,
            DiscretionaryActivity activity,
            SocialIntent socialSubject,
            IntentLifecycle lifecycle,
            float selectedUtility,
            float runnerUpUtility,
            long scoredAtTick,
            long issuedAtTick) {
        this.intentId = Objects.requireNonNull(intentId, "intentId");
        if (decisionId <= 0L) {
            throw new IllegalArgumentException("decisionId must be positive");
        }
        this.decisionId = decisionId;
        this.activity = Objects.requireNonNull(activity, "activity");
        this.socialSubject = socialSubject;
        // The subject is part of the decision, not a lookup performed later. Without this the
        // pending intent would say only "SOCIAL", and an executor could pair it with whoever the
        // newest observation happens to name - decision #91 chose Bob, Alice gets greeted, and the
        // causal chain silently changes subject underneath the record that authorised it.
        if ((activity == DiscretionaryActivity.SOCIAL) != (socialSubject != null)) {
            throw new IllegalArgumentException(
                    "SOCIAL requires exactly one bound subject and no other activity may carry one; "
                            + "activity=" + activity + " subject=" + socialSubject);
        }
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.selectedUtility = selectedUtility;
        this.runnerUpUtility = runnerUpUtility;
        this.scoredAtTick = scoredAtTick;
        this.issuedAtTick = issuedAtTick;
        this.invalidationCause = InvalidationCause.NONE;
    }

    public static DiscretionaryIntent pending(
            long decisionId,
            DiscretionaryActivity activity,
            float selectedUtility,
            float runnerUpUtility,
            long gameTime) {
        return pending(decisionId, activity, null, selectedUtility, runnerUpUtility, gameTime);
    }

    /**
     * GAO-10 — the SOCIAL form. The exact {@link SocialIntent} that participated in the winning
     * score becomes immutable data belonging to this decision.
     *
     * <p>Deliberately not a separate per-mob "current social target" store: a second mutable holder
     * would have its own lifecycle, and the two would drift precisely when it mattered.
     */
    public static DiscretionaryIntent pending(
            long decisionId,
            DiscretionaryActivity activity,
            SocialIntent socialSubject,
            float selectedUtility,
            float runnerUpUtility,
            long gameTime) {
        return new DiscretionaryIntent(
                UUID.randomUUID(),
                decisionId,
                activity,
                socialSubject,
                IntentLifecycle.PENDING,
                selectedUtility,
                runnerUpUtility,
                gameTime,
                gameTime);
    }

    public UUID intentId() {
        return intentId;
    }

    public long decisionId() {
        return decisionId;
    }

    public DiscretionaryActivity activity() {
        return activity;
    }

    /** The subject this decision chose, present only for SOCIAL. Never re-derived, never replaced. */
    public SocialIntent socialSubject() {
        return socialSubject;
    }

    /** Whether this intent is bound to {@code targetId} — the equality 44D adoption will require. */
    public boolean boundTo(UUID targetId) {
        return socialSubject != null && socialSubject.targets(targetId);
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
