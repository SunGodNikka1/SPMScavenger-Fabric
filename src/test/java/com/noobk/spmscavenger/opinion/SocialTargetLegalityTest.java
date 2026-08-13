package com.noobk.spmscavenger.opinion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Task 44B — the rule the runtime data forced: an admission pulse is not a social target.
 */
class SocialTargetLegalityTest {

    private static final double RANGE_SQR = 100.0D; // SPM's observed acquisition radius, 10.0

    private static SocialTargetValidity legal() {
        return SocialTargetLegality.check(true, false, true, true, true, true, 25.0D, RANGE_SQR, true);
    }

    @Test
    void mustHappen_aFullyLegalTargetIsUsable() {
        assertEquals(SocialTargetValidity.VALID, legal());
        assertTrue(legal().usable());
    }

    /**
     * The headline rule. 98.4% of observed pulses carried no eligible target, so a design that
     * treated "pulse exists" as "target available" would be wrong ~62 times out of 63.
     */
    @Test
    void mustNotHappen_admissionEvidenceAloneAuthorizesASocialTarget() {
        assertEquals(SocialTargetValidity.TARGET_GONE,
                SocialTargetLegality.check(
                        true, false, true, false, false, false, 0.0D, RANGE_SQR, false),
                "a fresh pulse with nobody resolvable must not be usable");
    }

    /**
     * Even a pulse that did find somebody is a statement about a moment up to 40 ticks ago. Each of
     * these is that same target, later.
     */
    @Test
    void mustNotHappen_aOnceValidTargetStaysValidAfterTheWorldMoves() {
        assertEquals(SocialTargetValidity.TARGET_DEAD, SocialTargetLegality.check(
                true, false, true, true, false, true, 25.0D, RANGE_SQR, true));
        assertEquals(SocialTargetValidity.WRONG_LEVEL, SocialTargetLegality.check(
                true, false, true, true, true, false, 25.0D, RANGE_SQR, true));
        assertEquals(SocialTargetValidity.OUT_OF_RANGE, SocialTargetLegality.check(
                true, false, true, true, true, true, 100.01D, RANGE_SQR, true));
        assertEquals(SocialTargetValidity.NOT_GREET_REACTION, SocialTargetLegality.check(
                true, false, true, true, true, true, 25.0D, RANGE_SQR, false),
                "turning hostile must invalidate a target selected while friendly");
    }

    @Test
    void mustHappen_theHostsOwnGatesRejectBeforeAnyTargetQuestion() {
        assertEquals(SocialTargetValidity.SPM_UNAVAILABLE, SocialTargetLegality.check(
                false, false, true, true, true, true, 25.0D, RANGE_SQR, true),
                "an unreadable relationship is not a friendship");
        assertEquals(SocialTargetValidity.COMBAT_TARGET, SocialTargetLegality.check(
                true, true, true, true, true, true, 25.0D, RANGE_SQR, true),
                "SPM returns false on a combat target before it ever searches");
        assertEquals(SocialTargetValidity.NO_ADMISSION_EVIDENCE, SocialTargetLegality.check(
                true, false, false, true, true, true, 25.0D, RANGE_SQR, true),
                "without a pulse the host is on cooldown, greeting, or being held");
    }

    /** Exactly on the boundary is inside it — the host's own search uses an inclusive radius. */
    @Test
    void mustHappen_theRangeBoundIsInclusive() {
        assertEquals(SocialTargetValidity.VALID, SocialTargetLegality.check(
                true, false, true, true, true, true, RANGE_SQR, RANGE_SQR, true));
    }

    /**
     * An unmeasurable distance must fail closed. Written as {@code !(d <= r)} precisely so NaN
     * rejects; {@code d > r} would have returned false and let it through.
     */
    @Test
    void mustNotHappen_anUnmeasurableDistancePasses() {
        assertEquals(SocialTargetValidity.OUT_OF_RANGE, SocialTargetLegality.check(
                true, false, true, true, true, true, Double.NaN, RANGE_SQR, true));
    }

    @Test
    void mustNotHappen_anyRejectionReportsItselfAsUsable() {
        for (SocialTargetValidity validity : SocialTargetValidity.values()) {
            assertEquals(validity == SocialTargetValidity.VALID, validity.usable(),
                    validity + " must only be usable when it is VALID");
        }
        assertFalse(SocialTargetValidity.NO_ADMISSION_EVIDENCE.usable());
    }
}
