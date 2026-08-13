package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpinionDecisionTraceTest {

    @Test
    void preservesEveryUtilityComponentWithoutParsingStrings() {
        OpinionDecisionTrace trace = new OpinionDecisionTrace();
        ActivityUtilityBreakdown breakdown = new ActivityUtilityBreakdown(
                DiscretionaryActivity.EXPLORE,
                1f,
                2f,
                3f,
                4f,
                5f,
                55f,
                6f,
                7f,
                8f,
                -9f,
                27f);

        long decisionId = trace.beginDecision(
                10L, List.of(OpinionDecisionTrace.Candidate.eligible(breakdown)));
        trace.conclude(
                decisionId,
                OpinionDecisionTrace.DecisionDisposition.BELOW_ACTIVATION_THRESHOLD,
                DiscretionaryActivity.EXPLORE,
                true);

        ActivityUtilityBreakdown stored = trace.snapshot().getFirst().candidates().getFirst().breakdown();
        assertEquals(breakdown, stored);
        assertEquals(1f, stored.baseUsefulness());
        assertEquals(3f, stored.boredomFit());
        assertEquals(4f, stored.stressFit());
        assertEquals(5f, stored.noveltyFit());
        // GAO-10: the SOCIAL subject term is a real component and must survive the trace intact,
        // like every other. A component that silently reads back as 0 would make an explanation
        // that is complete-looking and wrong.
        assertEquals(55f, stored.subjectFit());
        assertEquals(6f, stored.recentReward());
        assertEquals(8f, stored.failurePressure());
        assertEquals(-9f, stored.cost());
    }

    @Test
    void decisionIdExistsBeforeAndRemainsDistinctFromIntentId() {
        OpinionDecisionTrace trace = new OpinionDecisionTrace();
        long decisionId = trace.beginDecision(10L, List.of());
        UUID intentId = UUID.randomUUID();

        trace.attachIntent(decisionId, intentId);
        trace.transition(
                decisionId,
                11L,
                OpinionDecisionTrace.Stage.INTENT,
                intentId,
                DiscretionaryActivity.REST,
                IntentLifecycle.PENDING,
                InvalidationCause.NONE,
                "issued");

        OpinionDecisionTrace.Decision stored = trace.snapshot().getFirst();
        assertTrue(decisionId > 0L);
        assertEquals(intentId, stored.intentId());
        assertNotEquals(Long.toString(decisionId), intentId.toString());
    }

    @Test
    void evictionProtectsLiveOriginThenRemovesItAtomicallyAfterTerminal() {
        OpinionDecisionTrace trace = new OpinionDecisionTrace();
        long firstId = trace.beginDecision(0L, List.of());
        UUID firstIntent = UUID.randomUUID();
        trace.attachIntent(firstId, firstIntent);
        trace.transition(
                firstId,
                1L,
                OpinionDecisionTrace.Stage.INTENT,
                firstIntent,
                DiscretionaryActivity.EXPLORE,
                IntentLifecycle.PENDING,
                InvalidationCause.NONE,
                "issued");
        trace.transition(
                firstId,
                2L,
                OpinionDecisionTrace.Stage.ADOPT,
                firstIntent,
                DiscretionaryActivity.EXPLORE,
                IntentLifecycle.ADOPTED,
                InvalidationCause.NONE,
                "adopted");

        for (int i = 1; i <= DiscretionaryDirectorConstants.TRACE_DECISION_CAPACITY; i++) {
            long id = trace.beginDecision(i * 10L, List.of());
            trace.conclude(
                    id,
                    OpinionDecisionTrace.DecisionDisposition.NO_CANDIDATES,
                    null,
                    true);
        }

        assertEquals(DiscretionaryDirectorConstants.TRACE_DECISION_CAPACITY, trace.snapshot().size());
        assertTrue(trace.snapshot().stream().anyMatch(decision -> decision.decisionId() == firstId));
        assertTrue(trace.snapshot().stream()
                .flatMap(decision -> decision.transitions().stream())
                .anyMatch(transition -> firstIntent.equals(transition.intentId())));

        trace.transition(
                firstId,
                500L,
                OpinionDecisionTrace.Stage.TERMINAL,
                firstIntent,
                DiscretionaryActivity.EXPLORE,
                IntentLifecycle.SUCCEEDED,
                InvalidationCause.NONE,
                "complete");
        long replacement = trace.beginDecision(510L, List.of());
        trace.conclude(
                replacement,
                OpinionDecisionTrace.DecisionDisposition.NO_CANDIDATES,
                null,
                true);

        assertEquals(DiscretionaryDirectorConstants.TRACE_DECISION_CAPACITY, trace.snapshot().size());
        assertFalse(trace.snapshot().stream().anyMatch(decision -> decision.decisionId() == firstId));
        assertFalse(trace.snapshot().stream()
                .flatMap(decision -> decision.transitions().stream())
                .anyMatch(transition -> firstIntent.equals(transition.intentId())));
    }
}
