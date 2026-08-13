package com.noobk.spmscavenger.opinion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Task 44B/44C — an admission observation is not a social opportunity, and the entity SPM named is
 * only usable while it is still physically there.
 */
class SocialTargetLegalityTest {

    private static final double RANGE_SQR = 100.0D; // SPM's observed acquisition radius, 10.0

    /** combat, freshObservation, namedTarget, resolved, alive, sameLevel, dSqr, rSqr */
    private static SocialTargetValidity legal() {
        return SocialTargetLegality.check(false, true, true, true, true, true, 25.0D, RANGE_SQR);
    }

    @Test
    void mustHappen_aPhysicallyUsableNamedTargetIsValid() {
        assertEquals(SocialTargetValidity.VALID, legal());
        assertTrue(legal().usable());
    }

    /**
     * The headline rule. 98.4% of observed admissions named nobody, so a design that read "the host
     * looked" as "a target exists" would be wrong ~62 times in 63.
     */
    @Test
    void mustNotHappen_anAdmissionWithoutATargetBecomesAnOpportunity() {
        assertEquals(SocialTargetValidity.NO_OBSERVED_TARGET, SocialTargetLegality.check(
                        false, true, false, false, false, false, Double.NaN, RANGE_SQR),
                "the host looked and named nobody; that is not an opportunity");
    }

    /**
     * The identity SPM handed us describes a moment up to 40 ticks ago. Each of these is that same
     * entity, later — and each must reject on cheap live state alone, with no relationship query.
     */
    @Test
    void mustNotHappen_aNamedTargetStaysUsableAfterTheWorldMoves() {
        assertEquals(SocialTargetValidity.TARGET_GONE, SocialTargetLegality.check(
                false, true, true, false, false, false, Double.NaN, RANGE_SQR));
        assertEquals(SocialTargetValidity.TARGET_DEAD, SocialTargetLegality.check(
                false, true, true, true, false, true, 25.0D, RANGE_SQR));
        assertEquals(SocialTargetValidity.WRONG_LEVEL, SocialTargetLegality.check(
                false, true, true, true, true, false, 25.0D, RANGE_SQR));
        assertEquals(SocialTargetValidity.OUT_OF_RANGE, SocialTargetLegality.check(
                false, true, true, true, true, true, 100.01D, RANGE_SQR));
    }

    @Test
    void mustHappen_theHostsOwnGatesRejectFirst() {
        assertEquals(SocialTargetValidity.COMBAT_TARGET, SocialTargetLegality.check(
                        true, true, true, true, true, true, 25.0D, RANGE_SQR),
                "SPM returns false on a combat target before it ever searches");
        assertEquals(SocialTargetValidity.NO_ADMISSION_EVIDENCE, SocialTargetLegality.check(
                        false, false, true, true, true, true, 25.0D, RANGE_SQR),
                "without an observation the host is on cooldown, greeting, or being held");
    }

    /**
     * There must be no relationship term. Greet legality was proven by the host when its own search
     * returned this identity; re-asking would mean calling {@code reactionToward}, which writes a
     * per-tick memo cache and can change the host's own later answer (D-GAO-057).
     */
    @Test
    void mustNotHappen_aRelationshipQuestionSurvivesInThePredicate() {
        for (SocialTargetValidity validity : SocialTargetValidity.values()) {
            assertFalse(validity.name().contains("GREET") || validity.name().contains("REACTION"),
                    validity + " reintroduces a relationship question this addon must not ask");
        }
    }

    /** Exactly on the boundary is inside it — the host's own search uses an inclusive radius. */
    @Test
    void mustHappen_theRangeBoundIsInclusive() {
        assertEquals(SocialTargetValidity.VALID, SocialTargetLegality.check(
                false, true, true, true, true, true, RANGE_SQR, RANGE_SQR));
    }

    /**
     * An unmeasurable distance must fail closed. Written as {@code !(d <= r)} precisely so NaN
     * rejects; {@code d > r} would have returned false and let it through.
     */
    @Test
    void mustNotHappen_anUnmeasurableDistancePasses() {
        assertEquals(SocialTargetValidity.OUT_OF_RANGE, SocialTargetLegality.check(
                false, true, true, true, true, true, Double.NaN, RANGE_SQR));
    }

    /**
     * Last line of defence: an unusable <em>bound</em> must reject rather than admit everything.
     * With {@code rangeSqr = +Infinity} the comparison is true for every finite distance, so without
     * this guard the range term would accept the whole world.
     */
    @Test
    void mustNotHappen_anUnusableRangeBoundAdmitsEverything() {
        for (double badBound : new double[] {Double.POSITIVE_INFINITY, Double.NaN, 0.0D, -1.0D}) {
            assertEquals(SocialTargetValidity.OUT_OF_RANGE, SocialTargetLegality.check(
                            false, true, true, true, true, true, 25.0D, badBound),
                    "range bound " + badBound + " must reject, not admit");
        }
    }

    @Test
    void mustHappen_theRadiusInvariantIsFinitePositive() {
        assertTrue(SocialTargetLegality.isUsableRadius(10.0D));
        assertTrue(SocialTargetLegality.isUsableRadius(Double.MIN_VALUE));
        assertFalse(SocialTargetLegality.isUsableRadius(0.0D));
        assertFalse(SocialTargetLegality.isUsableRadius(-1.0D));
        assertFalse(SocialTargetLegality.isUsableRadius(Double.NaN));
        assertFalse(SocialTargetLegality.isUsableRadius(Double.POSITIVE_INFINITY));
        assertFalse(SocialTargetLegality.isUsableRadius(Double.NEGATIVE_INFINITY));
    }

    @Test
    void mustNotHappen_anyRejectionReportsItselfAsUsable() {
        for (SocialTargetValidity validity : SocialTargetValidity.values()) {
            assertEquals(validity == SocialTargetValidity.VALID, validity.usable(),
                    validity + " must only be usable when it is VALID");
        }
    }
}
