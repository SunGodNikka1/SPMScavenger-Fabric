package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.OutcomeClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpinionLearningPolicyTest {

    private static final UUID EPISODE = UUID.randomUUID();

    @Test
    void acceptsVoluntarySuccess() {
        assertTrue(OpinionLearningPolicy.accepts(evidence(
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.UNSPECIFIED,
                1.0f)));
    }

    @Test
    void rejectsSimulationFrontier() {
        assertFalse(OpinionLearningPolicy.accepts(evidence(
                OutcomeClass.SIMULATION_FRONTIER,
                ExperienceCause.SIMULATION_FRONTIER,
                0f)));
    }

    @Test
    void rejectsPlayerOrderCause() {
        assertFalse(OpinionLearningPolicy.accepts(evidence(
                OutcomeClass.VOLUNTARY_ABANDON,
                ExperienceCause.MINING_PLAYER_ORDER,
                -0.5f)));
    }

    @Test
    void rejectsProtectedInterrupt() {
        assertFalse(OpinionLearningPolicy.accepts(evidence(
                OutcomeClass.PROTECTED_INTERRUPT,
                ExperienceCause.PROTECTED_INTERRUPT,
                0f)));
    }

    private static EpisodeLearningEvidence evidence(
            OutcomeClass outcome, ExperienceCause cause, float weight) {
        return new EpisodeLearningEvidence(
                EPISODE,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                ExperienceKind.EXPEDITION_UNLOCKED,
                outcome,
                cause,
                weight,
                1L);
    }
}
