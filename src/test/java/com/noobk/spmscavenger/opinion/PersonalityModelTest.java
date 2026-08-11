package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.OutcomeClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalityModelTest {

    private static final UUID MOB_A =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MOB_B =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID EPISODE =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void optionA_isDeterministicBoundedAndHostAnchored() {
        PersonalityModel low = PersonalityFactory.fromIdentity(MOB_A, 0, 0);
        PersonalityModel repeat = PersonalityFactory.fromIdentity(MOB_A, 0, 0);
        PersonalityModel high = PersonalityFactory.fromIdentity(MOB_A, 10, 10);
        PersonalityModel other = PersonalityFactory.fromIdentity(MOB_B, 0, 0);
        PersonalityModel fallback = PersonalityFactory.fromIdentity(MOB_A, null, null);
        PersonalityModel fallbackRepeat = PersonalityFactory.fromIdentity(MOB_A, null, null);

        assertEquals(low, repeat, "same identity and host anchors must be stable");
        assertNotEquals(low, other, "different UUIDs must retain deterministic individuality");
        assertEquals(fallback, fallbackRepeat,
                "missing host accessors must fall back deterministically without invented anchors");
        assertTrue(high.sociability() > low.sociability(), "SPM friendliness anchors sociability");
        assertTrue(high.riskTolerance() > low.riskTolerance(), "SPM fight/flight anchors risk tolerance");
        assertTrue(high.adventurousness() > low.adventurousness(),
                "adventurousness coherently includes risk tolerance");
        assertBounded(high);
        assertBounded(low);
        assertThrows(IllegalArgumentException.class,
                () -> new PersonalityModel(Float.NaN, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f));
    }

    @Test
    void neutralResponse_isExactPreGao7Parity() {
        EpisodeLearningEvidence positive = terminal(
                ActivityKind.OVERLAND_EXPLORATION,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                1.0f);
        OpinionMemory legacy = new OpinionMemory();
        OpinionMemory neutral = new OpinionMemory();

        legacy.apply(positive, 600L);
        neutral.apply(positive, 600L, PersonalityLearningResponse.NEUTRAL);

        assertEquals(
                legacy.memoryOf(ActivityKind.OVERLAND_EXPLORATION).captureSnapshot(),
                neutral.memoryOf(ActivityKind.OVERLAND_EXPLORATION).captureSnapshot(),
                "NEUTRAL must reproduce the exact pre-GAO-7 mutation");
        assertEquals(
                PersonalityLearningResponse.NEUTRAL,
                PersonalityModel.NEUTRAL.learningResponse(positive));
    }

    @Test
    void personalityScalesButNeverCreatesInvertsOrChangesEligibility() {
        PersonalityModel highAdventure = new PersonalityModel(1f, 0.5f, 1f, 0.5f, 0.5f, 1f);
        PersonalityModel lowAdventure = new PersonalityModel(0f, 0.5f, 0f, 0.5f, 0.5f, 0f);
        EpisodeLearningEvidence positive = terminal(
                ActivityKind.OVERLAND_EXPLORATION,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                1.0f);
        EpisodeLearningEvidence negative = terminal(
                ActivityKind.TUNNEL_SEARCH,
                OutcomeClass.EXECUTION_FAILURE,
                ExperienceCause.MINING_NO_PROGRESS,
                -1.0f);
        EpisodeLearningEvidence zero = terminal(
                ActivityKind.OVERLAND_EXPLORATION,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                0.0f);
        EpisodeLearningEvidence blocked = terminal(
                ActivityKind.OVERLAND_EXPLORATION,
                OutcomeClass.AUTHORITY_CANCEL,
                ExperienceCause.AUTHORITY_CANCEL,
                1.0f);

        OpinionMemory high = apply(highAdventure, positive);
        OpinionMemory low = apply(lowAdventure, positive);
        assertTrue(high.preference(ActivityKind.OVERLAND_EXPLORATION)
                > low.preference(ActivityKind.OVERLAND_EXPLORATION));
        assertTrue(high.memoryOf(ActivityKind.OVERLAND_EXPLORATION).recentReward()
                > low.memoryOf(ActivityKind.OVERLAND_EXPLORATION).recentReward());
        assertTrue(high.preference(ActivityKind.OVERLAND_EXPLORATION) > 0f);
        assertTrue(low.preference(ActivityKind.OVERLAND_EXPLORATION) > 0f);

        OpinionMemory failure = apply(
                new PersonalityModel(0.5f, 0.5f, 0.5f, 0f, 0.5f, 0.5f), negative);
        assertTrue(failure.preference(ActivityKind.TUNNEL_SEARCH) < 0f,
                "positive multipliers must preserve a negative baseline sign");

        OpinionMemory zeroMemory = apply(highAdventure, zero);
        assertEquals(0f, zeroMemory.preference(ActivityKind.OVERLAND_EXPLORATION));
        assertEquals(0f,
                zeroMemory.memoryOf(ActivityKind.OVERLAND_EXPLORATION).recentReward());

        OpinionMemory blockedMemory = apply(highAdventure, blocked);
        assertEquals(0, blockedMemory.trackedActivityCount(),
                "personality must never make rejected evidence eligible");
    }

    @Test
    void objectiveEpisodeFactsDoNotDependOnPersonality() {
        EpisodeLearningEvidence evidence = terminal(
                ActivityKind.CAVE_EXPLORATION,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                1.0f);
        OpinionMemory high = apply(
                new PersonalityModel(1f, 0.5f, 1f, 0.5f, 0.5f, 1f), evidence);
        OpinionMemory low = apply(
                new PersonalityModel(0f, 0.5f, 0f, 0.5f, 0.5f, 0f), evidence);
        ActivityOpinionMemory highFacts = high.memoryOf(ActivityKind.CAVE_EXPLORATION);
        ActivityOpinionMemory lowFacts = low.memoryOf(ActivityKind.CAVE_EXPLORATION);

        assertEquals(highFacts.repetition(), lowFacts.repetition());
        assertEquals(highFacts.recentDuration(), lowFacts.recentDuration());
        assertEquals(highFacts.lastPerformed(), lowFacts.lastPerformed());
        assertEquals(highFacts.recentFailures(), lowFacts.recentFailures());
        assertNotEquals(highFacts.preference(), lowFacts.preference());
    }

    @Test
    void persistenceAndRiskOnlyBoundNegativeInterpretation() {
        EpisodeLearningEvidence stuck = terminal(
                ActivityKind.TUNNEL_SEARCH,
                OutcomeClass.EXECUTION_FAILURE,
                ExperienceCause.MINING_NO_PROGRESS,
                -1.0f);
        EpisodeLearningEvidence hazard = terminal(
                ActivityKind.CONTROLLED_DESCENT,
                OutcomeClass.EXECUTION_FAILURE,
                ExperienceCause.MINING_HAZARD,
                -1.0f);
        PersonalityModel resilient = new PersonalityModel(0.5f, 0.5f, 1f, 1f, 0.5f, 0.5f);
        PersonalityModel sensitive = new PersonalityModel(0.5f, 0.5f, 0f, 0f, 0.5f, 0.5f);

        float resilientStuck = apply(resilient, stuck).preference(ActivityKind.TUNNEL_SEARCH);
        float sensitiveStuck = apply(sensitive, stuck).preference(ActivityKind.TUNNEL_SEARCH);
        float resilientHazard = apply(resilient, hazard).preference(ActivityKind.CONTROLLED_DESCENT);
        float sensitiveHazard = apply(sensitive, hazard).preference(ActivityKind.CONTROLLED_DESCENT);

        assertTrue(resilientStuck > sensitiveStuck,
                "persistent mobs may learn a weaker dislike, but both remain negative");
        assertTrue(resilientHazard > sensitiveHazard,
                "risk-tolerant mobs may learn a weaker hazard dislike, but both remain negative");
        assertTrue(resilientStuck < 0f && sensitiveStuck < 0f);
        assertTrue(resilientHazard < 0f && sensitiveHazard < 0f);
    }

    @Test
    void responseBoundsAreEnforced() {
        PersonalityLearningResponse response = new PersonalityLearningResponse(0f, 2f, 1f);
        assertEquals(PersonalityLearningResponse.MIN_MULTIPLIER, response.preferenceMultiplier());
        assertEquals(PersonalityLearningResponse.MAX_MULTIPLIER, response.rewardMultiplier());
        assertThrows(IllegalArgumentException.class,
                () -> new PersonalityLearningResponse(Float.POSITIVE_INFINITY, 1f, 1f));
    }

    private static OpinionMemory apply(
            PersonalityModel personality, EpisodeLearningEvidence evidence) {
        OpinionMemory memory = new OpinionMemory();
        memory.apply(evidence, 600L, personality.learningResponse(evidence));
        return memory;
    }

    private static EpisodeLearningEvidence terminal(
            ActivityKind activity,
            OutcomeClass outcome,
            ExperienceCause cause,
            float weight) {
        return new EpisodeLearningEvidence(
                EPISODE,
                Optional.of(activity),
                ExperienceKind.EXPEDITION_END,
                outcome,
                cause,
                weight,
                1_000L);
    }

    private static void assertBounded(PersonalityModel model) {
        for (float value : new float[] {
                model.curiosity(), model.sociability(), model.riskTolerance(),
                model.persistence(), model.materialism(), model.adventurousness()
        }) {
            assertTrue(value >= 0f && value <= 1f);
        }
    }
}
