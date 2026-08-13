package com.noobk.spmscavenger.experience;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-GAO-024 — the <b>emitter</b> honours the foundational rule, not merely the memory downstream.
 *
 * <h2>Why a policy test was not enough</h2>
 *
 * {@code ExperienceOutcomePolicy.mayEmitLearning} being correct proves nothing about whether the
 * production emitter calls it. {@code ActivityEpisode} used the split checks
 * ({@code mayEmitPreferenceLearning} / {@code mayEmitFailureLearning}) with no cause awareness, and
 * {@code MobExperienceContext} forwards every emitted evidence to the external sink <em>after</em>
 * attempting internal application:
 *
 * <pre>
 * EXECUTION_FAILURE + ENVIRONMENT_BLOCKED + repetitions
 *   → ActivityEpisode emits EpisodeLearningEvidence   ← should not exist
 *   → OpinionMemoryService rejects it internally      ← safe
 *   → external sink still receives "learning evidence" ← semantic leak
 * </pre>
 *
 * These assert at the sink, which is the only place that distinguishes "never emitted" from
 * "emitted and later filtered".
 */
class LearningEvidenceEmissionTest {

    private final List<EpisodeLearningEvidence> emitted = new ArrayList<>();

    @AfterEach
    void reset() {
        OpinionExperienceRegistry.clearAll();
    }

    private MobExperienceContext contextRecordingEvidence(UUID mobId) {
        OpinionExperienceRegistry.setSinks(new OpinionExperienceSinks() {
            @Override
            public void onAffectPulse(AffectPulse pulse) {
                // affect is not under test here
            }

            @Override
            public void onLearningEvidence(EpisodeLearningEvidence evidence) {
                emitted.add(evidence);
            }
        });
        return OpinionExperienceRegistry.contextFor(mobId);
    }

    private static ExperienceEvent terminal(
            UUID episodeId, OutcomeClass outcome, ExperienceCause cause) {
        return new ExperienceEvent(
                ExperienceKind.PROJECT_END,
                100L,
                episodeId,
                outcome,
                cause,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                Optional.of(ActivityKind.CAVE_EXPLORATION),
                Optional.of(BlockPos.ZERO),
                Optional.empty());
    }

    @Test
    void mustNotHappen_aSuppressedCauseEmitsLearningEvidenceAtAll() {
        UUID mobId = UUID.randomUUID();
        MobExperienceContext context = contextRecordingEvidence(mobId);

        // Well past the repetition threshold, so only the cause can stop this.
        for (int i = 0; i < 10; i++) {
            context.registerExecutionFailure(ActivityKind.CAVE_EXPLORATION);
        }

        ActivityEpisode episode =
                context.openEpisode(Optional.of(ActivityKind.CAVE_EXPLORATION), 0L);
        episode.ingest(
                terminal(episode.episodeId(), OutcomeClass.EXECUTION_FAILURE,
                        ExperienceCause.ENVIRONMENT_BLOCKED),
                context.sinks(), context);

        assertTrue(emitted.isEmpty(),
                "a suppressed cause must never become evidence - relying on OpinionMemory to reject "
                        + "it downstream still leaks it to every external sink");
        assertTrue(episode.isClosed(), "and the episode still terminates, so RET-1b holds");
    }

    @Test
    void mustNotHappen_authorityOrPlayerOrderTeachesThroughRepetition() {
        for (ExperienceCause cause : new ExperienceCause[] {
                ExperienceCause.AUTHORITY_CANCEL, ExperienceCause.MINING_PLAYER_ORDER,
                ExperienceCause.SIMULATION_FRONTIER}) {
            emitted.clear();
            UUID mobId = UUID.randomUUID();
            MobExperienceContext context = contextRecordingEvidence(mobId);
            for (int i = 0; i < 10; i++) {
                context.registerExecutionFailure(ActivityKind.CAVE_EXPLORATION);
            }

            ActivityEpisode episode =
                    context.openEpisode(Optional.of(ActivityKind.CAVE_EXPLORATION), 0L);
            episode.ingest(
                    terminal(episode.episodeId(), OutcomeClass.EXECUTION_FAILURE, cause),
                    context.sinks(), context);

            assertTrue(emitted.isEmpty(), cause + " must not be laundered by repetition");
        }
    }

    @Test
    void mustHappen_genuineRepeatedFailureStillEmits() {
        UUID mobId = UUID.randomUUID();
        MobExperienceContext context = contextRecordingEvidence(mobId);
        for (int i = 0; i < 3; i++) {
            context.registerExecutionFailure(ActivityKind.CAVE_EXPLORATION);
        }

        ActivityEpisode episode =
                context.openEpisode(Optional.of(ActivityKind.CAVE_EXPLORATION), 0L);
        episode.ingest(
                terminal(episode.episodeId(), OutcomeClass.EXECUTION_FAILURE,
                        ExperienceCause.MINING_NO_PROGRESS),
                context.sinks(), context);

        assertEquals(1, emitted.size(),
                "repeatedly failing to make progress in a place the mob genuinely worked is real "
                        + "evidence - suppression must not become blanket silence");
        assertTrue(emitted.get(0).repetitionWeight() < 0f,
                "a failure teaches dislike, not liking");
    }

    @Test
    void mustHappen_aSuccessfulOutcomeStillEmits() {
        UUID mobId = UUID.randomUUID();
        MobExperienceContext context = contextRecordingEvidence(mobId);

        ActivityEpisode episode =
                context.openEpisode(Optional.of(ActivityKind.CAVE_EXPLORATION), 0L);
        episode.ingest(
                terminal(episode.episodeId(), OutcomeClass.VOLUNTARY_SUCCESS,
                        ExperienceCause.MINING_CAVE_FOUND),
                context.sinks(), context);

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0).repetitionWeight() > 0f);
    }

    /** Structural: the emitter must ask the foundational rule, not the split checks. */
    @Test
    void mustHappen_theEmitterCallsTheFoundationalRule() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/experience/ActivityEpisode.java"));

        assertTrue(source.contains("mayEmitLearning("),
                "eligibility is decided before evidence is constructed");
    }
}
