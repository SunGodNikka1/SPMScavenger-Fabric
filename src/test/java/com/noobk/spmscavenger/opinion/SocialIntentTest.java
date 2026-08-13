package com.noobk.spmscavenger.opinion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task 44B — the intent is identity-bound and honest about how old its evidence was. */
class SocialIntentTest {

    private static final UUID TARGET = UUID.randomUUID();

    @Test
    void mustHappen_theIntentNamesOneEntityAndKnowsItsEvidenceAge() {
        SocialIntent intent = new SocialIntent(TARGET, 1_040L, 1_010L, 10.0D);
        assertTrue(intent.targets(TARGET));
        assertFalse(intent.targets(UUID.randomUUID()), "an intent must not match another entity");
        assertEquals(30L, intent.evidenceAgeTicks());
    }

    /**
     * The radius belongs to SPM and is external evidence, so every unusable shape must be rejected
     * at the boundary — not only the two a bare positivity check happens to catch.
     */
    @Test
    void mustNotHappen_anIntentCarriesAnUnusableRadius() {
        for (double bad : new double[] {
                0.0D, -10.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SocialIntent(TARGET, 100L, 100L, bad),
                    "radius " + bad + " must be rejected");
        }
    }

    /**
     * The two that slip past {@code range <= 0}, called out on their own because they are the reason
     * this guard exists. {@code +Infinity} squares to {@code +Infinity} and would put every entity
     * in the world inside the acquisition radius — a silent inversion of fail-closed, arriving as
     * "it greeted someone impossibly far away" rather than as an error.
     */
    @Test
    void mustNotHappen_nanOrInfinityPassAPositivityCheck() {
        assertFalse(Double.NaN <= 0.0D, "NaN survives a bare positivity guard");
        assertFalse(Double.POSITIVE_INFINITY <= 0.0D, "+Infinity survives it too");
        assertFalse(SocialTargetLegality.isUsableRadius(Double.NaN));
        assertFalse(SocialTargetLegality.isUsableRadius(Double.POSITIVE_INFINITY));
        assertTrue(SocialTargetLegality.isUsableRadius(10.0D), "SPM's observed radius is usable");
    }

    /** Evidence cannot come from the future; that would be a clock or ordering defect, not data. */
    @Test
    void mustNotHappen_evidencePostdatesTheIntentFormedFromIt() {
        assertThrows(IllegalArgumentException.class,
                () -> new SocialIntent(TARGET, 100L, 101L, 10.0D));
    }

    @Test
    void mustNotHappen_anIntentWithoutATarget() {
        assertThrows(NullPointerException.class,
                () -> new SocialIntent(null, 100L, 100L, 10.0D));
    }
}
