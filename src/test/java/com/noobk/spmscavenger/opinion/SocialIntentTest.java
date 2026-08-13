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

    /** The radius belongs to SPM. A zero or negative one means we invented it, so reject loudly. */
    @Test
    void mustNotHappen_anIntentCarriesARadiusThisAddonChose() {
        assertThrows(IllegalArgumentException.class,
                () -> new SocialIntent(TARGET, 100L, 100L, 0.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialIntent(TARGET, 100L, 100L, -10.0D));
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
