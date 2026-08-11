package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentProfileTest {

    @Test
    void classifierKeepsCorrelatedSignalsAsOneFiniteMultiLabelProfile() {
        EnvironmentProfile profile = EnvironmentClassifier.fromSignals(true, false, true, false, false);

        assertEquals(Set.of(EnvironmentKind.FOREST, EnvironmentKind.SNOWY), profile.kinds());
        assertEquals(2, profile.size());
    }

    @Test
    void unknownOrUntaggedEnvironmentIsNeutral() {
        EnvironmentProfile profile = EnvironmentClassifier.fromSignals(false, false, false, false, false);

        assertTrue(profile.isEmpty());
        assertFalse(profile.contains(EnvironmentKind.FOREST));
    }

    @Test
    void netherAndEndRemainIndependentTargetNativeLabels() {
        assertEquals(
                Set.of(EnvironmentKind.NETHER),
                EnvironmentClassifier.fromSignals(false, false, false, true, false).kinds());
        assertEquals(
                Set.of(EnvironmentKind.END),
                EnvironmentClassifier.fromSignals(false, false, false, false, true).kinds());
    }
}
