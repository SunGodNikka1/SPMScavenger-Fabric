package com.noobk.spmscavenger.opinion;

import java.util.Objects;
import java.util.UUID;

/**
 * Task 44B — a social interaction Opinion wants, bound to one specific entity.
 *
 * <h2>Identity, not a reference</h2>
 *
 * The target is a {@link UUID}. Holding the {@code LivingEntity} would keep a possibly-unloaded
 * entity alive in a field and — worse — would let stale state read as current: the object still
 * answers {@code getX()} after it stops being part of the world. An id forces every consumer to
 * re-resolve against the live level, which is exactly the discipline this intent exists to enforce
 * (and the same identity-binding D-GAO-051 established for yields).
 *
 * <h2>This is a request, not a permission</h2>
 *
 * A {@code SocialIntent} records that a legal target existed <em>at</em> {@link #formedAtTick},
 * witnessed off an admission pulse observed at {@link #admissionObservedAtTick}. It confers no
 * authority to greet. Before anything physical happens the target must pass
 * {@link SocialTargetLegality} again against live state — because in the intervening ticks the
 * target can move out of range, die, unload, change dimension, or simply stop being someone SPM
 * would greet.
 *
 * <p>Nothing here starts, binds to, or substitutes into {@code FriendlyGreetGoal}. Until an executor
 * demonstrably accepts <em>this exact intent</em>, an SPM-initiated greeting remains SPM's own
 * autonomous reflex and must never be credited to Opinion (D-GAO-058).
 */
public record SocialIntent(
        UUID targetId,
        long formedAtTick,
        long admissionObservedAtTick,
        double hostAcquisitionRange) {

    public SocialIntent {
        Objects.requireNonNull(targetId, "targetId");
        if (hostAcquisitionRange <= 0.0D) {
            throw new IllegalArgumentException(
                    "hostAcquisitionRange must be positive; it is SPM's own radius read from the "
                            + "admission pulse, never a value this addon chooses");
        }
        if (admissionObservedAtTick > formedAtTick) {
            throw new IllegalArgumentException(
                    "admission evidence cannot postdate the intent formed from it");
        }
    }

    /**
     * How stale the evidence was when this intent was formed. Useful for explanation and for
     * deciding later whether {@code PULSE_LIFETIME_TICKS} is too generous — a question deliberately
     * left open until a real SOCIAL candidate exists to produce stale-positive decisions worth
     * measuring.
     */
    public long evidenceAgeTicks() {
        return formedAtTick - admissionObservedAtTick;
    }

    public boolean targets(UUID candidate) {
        return targetId.equals(candidate);
    }
}
