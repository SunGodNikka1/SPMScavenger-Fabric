package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.OutcomeClass;
import com.noobk.spmscavenger.experience.ExperienceEvent;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentOpinionLearningTest {

    @AfterEach
    void clearGate() {
        OpinionFeatureGate.clearTestOverride();
    }

    @Test
    void completedExpeditionDividesOneLearningDeltaAcrossLabels() {
        OpinionFeatureGate.setTestOverride(true);
        EnvironmentOpinionMemory single = new EnvironmentOpinionMemory();
        EnvironmentOpinionMemory correlated = new EnvironmentOpinionMemory();

        EnvironmentOpinionService.apply(single, completed(EnvironmentProfile.of(EnvironmentKind.FOREST)),
                PersonalityLearningResponse.NEUTRAL);
        EnvironmentOpinionService.apply(correlated,
                completed(EnvironmentProfile.of(EnvironmentKind.FOREST, EnvironmentKind.SNOWY)),
                PersonalityLearningResponse.NEUTRAL);

        float singleTotal = single.preference(EnvironmentKind.FOREST);
        float correlatedTotal = correlated.preference(EnvironmentKind.FOREST)
                + correlated.preference(EnvironmentKind.SNOWY);
        assertEquals(singleTotal, correlatedTotal, 0.001f);
        assertTrue(singleTotal > 0f);
    }

    @Test
    void genericFailuresAndInterruptionsTeachNothingAboutEnvironment() {
        OpinionFeatureGate.setTestOverride(true);
        EnvironmentOpinionMemory memory = new EnvironmentOpinionMemory();
        EnvironmentProfile snowy = EnvironmentProfile.of(EnvironmentKind.SNOWY);

        for (EpisodeLearningEvidence evidence : new EpisodeLearningEvidence[] {
                evidence(OutcomeClass.EXECUTION_FAILURE, ExperienceCause.MINING_NO_PROGRESS, snowy),
                evidence(OutcomeClass.SIMULATION_FRONTIER, ExperienceCause.SIMULATION_FRONTIER, snowy),
                evidence(OutcomeClass.AUTHORITY_CANCEL, ExperienceCause.AUTHORITY_CANCEL, snowy),
                evidence(OutcomeClass.PROTECTED_INTERRUPT, ExperienceCause.PROTECTED_INTERRUPT, snowy),
                evidence(OutcomeClass.VOLUNTARY_ABANDON, ExperienceCause.UNSPECIFIED, snowy)
        }) {
            EnvironmentOpinionService.apply(memory, evidence, PersonalityLearningResponse.NEUTRAL);
        }

        assertEquals(0f, memory.preference(EnvironmentKind.SNOWY), 0.001f,
                "powder-snow/path/scheduler failure must not become dislike of SNOWY");
    }

    @Test
    void opinionDisabledCannotCreateEnvironmentLearning() {
        OpinionFeatureGate.setTestOverride(false);
        EnvironmentOpinionMemory memory = new EnvironmentOpinionMemory();

        EnvironmentOpinionService.apply(memory, completed(EnvironmentProfile.of(EnvironmentKind.OCEAN)),
                PersonalityLearningResponse.NEUTRAL);

        assertEquals(0, memory.trackedEnvironmentCount());
    }

    @Test
    void memoryCardinalityCanNeverExceedTheFiniteTaxonomy() {
        EnvironmentOpinionMemory memory = new EnvironmentOpinionMemory();
        for (EnvironmentKind kind : EnvironmentKind.values()) {
            memory.recordOutcome(EnvironmentProfile.of(kind), 1f);
        }

        assertEquals(EnvironmentKind.values().length, memory.trackedEnvironmentCount());
    }

    @Test
    void rawEventFlowsThroughTheSingleEpisodePipelineIntoEnvironmentMemory() {
        OpinionFeatureGate.setTestOverride(true);
        MobExperienceContext context = new MobExperienceContext(UUID.randomUUID(), null);
        UUID episode = UUID.randomUUID();

        context.pipeline().accept(new ExperienceEvent(
                ExperienceKind.EXPEDITION_END,
                100L,
                episode,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                0f, 0f, 0.2f, 0f, 0f,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.of(BlockPos.ZERO),
                Optional.empty(),
                Optional.of(EnvironmentProfile.of(EnvironmentKind.FOREST))));

        assertTrue(context.environmentOpinionMemory().preference(EnvironmentKind.FOREST) > 0f);
        assertTrue(context.hasCompletedEpisode(episode), "terminal is compacted through the existing owner");
    }

    private static EpisodeLearningEvidence completed(EnvironmentProfile profile) {
        return evidence(OutcomeClass.VOLUNTARY_SUCCESS, ExperienceCause.EXPEDITION_COMPLETE, profile);
    }

    private static EpisodeLearningEvidence evidence(
            OutcomeClass outcome, ExperienceCause cause, EnvironmentProfile profile) {
        return new EpisodeLearningEvidence(
                UUID.randomUUID(), Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                ExperienceKind.EXPEDITION_END, outcome, cause, 1f, 100L, Optional.of(profile));
    }
}
