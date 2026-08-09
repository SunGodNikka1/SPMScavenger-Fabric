package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentalEscapePolicyTest {

    @Test
    void movementGetsItsFullGracePeriodBeforeAnyBlockDamage() {
        assertFalse(mayBreak(true, true, true, false, false, true, 0.5F, 0, 29));
        assertTrue(mayBreak(true, true, true, false, false, true, 0.5F, 0, 30));
    }

    @Test
    void incidentBlockLimitIsExact() {
        assertTrue(mayBreak(true, true, true, false, false, true, 0.5F, 2, 30));
        assertFalse(mayBreak(true, true, true, false, false, true, 0.5F, 3, 30));
    }

    @Test
    void protectionAndWorldSafetyGatesCannotBeBypassed() {
        assertFalse(mayBreak(false, true, true, false, false, true, 0.5F, 0, 30));
        assertFalse(mayBreak(true, false, true, false, false, true, 0.5F, 0, 30));
        assertFalse(mayBreak(true, true, false, false, false, true, 0.5F, 0, 30));
        assertFalse(mayBreak(true, true, true, true, false, true, 0.5F, 0, 30));
        assertFalse(mayBreak(true, true, true, false, true, true, 0.5F, 0, 30));
        assertFalse(mayBreak(true, true, true, false, false, false, 0.5F, 0, 30));
        assertFalse(mayBreak(true, true, true, false, false, true, -1.0F, 0, 30));
        assertFalse(mayBreak(true, true, true, false, false, true, 3.0F, 0, 30));
    }

    private static boolean mayBreak(
            boolean enabled,
            boolean griefing,
            boolean intersects,
            boolean blockEntity,
            boolean denied,
            boolean natural,
            float hardness,
            int broken,
            int ticks) {
        return EnvironmentalEscapePolicy.mayBreakEntrappingBlock(
                enabled, griefing, intersects, blockEntity, denied, natural,
                hardness, 2.0F, broken, 3, ticks, 30);
    }

    @Test
    void aMomentaryClearDoesNotEndAnEscapeIncident() {
        // The trapped predicates flicker on any vertical movement, so one false reading must not
        // wipe the grace timer - that is what left mobs stuck in powder snow indefinitely.
        assertTrue(EnvironmentalEscapePolicy.incidentSurvivesClear(true, 0, 40));
        assertTrue(EnvironmentalEscapePolicy.incidentSurvivesClear(true, 39, 40));
    }

    @Test
    void aSustainedClearDoesEndIt() {
        assertFalse(EnvironmentalEscapePolicy.incidentSurvivesClear(true, 40, 40));
        assertFalse(EnvironmentalEscapePolicy.incidentSurvivesClear(true, 999, 40));
    }

    @Test
    void nothingSurvivesWhenNoIncidentIsOpen() {
        assertFalse(EnvironmentalEscapePolicy.incidentSurvivesClear(false, 0, 40));
    }
}
