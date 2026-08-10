package com.noobk.spmscavenger.experience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpisodeBoundaryPolicyTest {

    @Test
    void restOpenIsNotTerminal() {
        ExperienceEvent open = restEvent(ExperienceCause.REST_SESSION_OPEN, OutcomeClass.VOLUNTARY_SUCCESS);
        assertFalse(EpisodeBoundaryPolicy.isTerminal(open));
    }

    @Test
    void restCloseIsTerminal() {
        ExperienceEvent close = restEvent(ExperienceCause.REST_SESSION_CLOSE, OutcomeClass.VOLUNTARY_SUCCESS);
        assertTrue(EpisodeBoundaryPolicy.isTerminal(close));
    }

    @Test
    void expeditionUnlockIsNotTerminal() {
        ExperienceEvent unlock = new ExperienceEvent(
                ExperienceKind.EXPEDITION_UNLOCKED,
                10L,
                java.util.UUID.randomUUID(),
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_UNLOCKED,
                0f,
                0f,
                0f,
                0f,
                0f,
                java.util.Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                java.util.Optional.empty(),
                java.util.Optional.empty());
        assertFalse(EpisodeBoundaryPolicy.isTerminal(unlock));
    }

    @Test
    void expeditionEndIsTerminal() {
        ExperienceEvent end = new ExperienceEvent(
                ExperienceKind.EXPEDITION_END,
                20L,
                java.util.UUID.randomUUID(),
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                0f,
                0f,
                0.2f,
                0f,
                0f,
                java.util.Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                java.util.Optional.empty(),
                java.util.Optional.empty());
        assertTrue(EpisodeBoundaryPolicy.isTerminal(end));
    }

    private static ExperienceEvent restEvent(ExperienceCause cause, OutcomeClass outcome) {
        return new ExperienceEvent(
                ExperienceKind.REST_SESSION,
                1L,
                java.util.UUID.randomUUID(),
                outcome,
                cause,
                0f,
                0f,
                0f,
                0f,
                0f,
                java.util.Optional.of(ActivityKind.REST),
                java.util.Optional.empty(),
                java.util.Optional.empty());
    }
}
