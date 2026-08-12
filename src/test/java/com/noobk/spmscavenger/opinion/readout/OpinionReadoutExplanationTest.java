package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.goal.ShelterNightAuthority;
import com.noobk.spmscavenger.opinion.ActivityUtilityBreakdown;
import com.noobk.spmscavenger.opinion.DiscretionaryActivity;
import com.noobk.spmscavenger.opinion.OpinionDecisionTrace;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpinionReadoutExplanationTest {

    @AfterEach
    void reset() {
        OpinionFeatureGate.clearTestOverride();
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void shelterSummaryLabelsMandatoryHoldSeparatelyFromRest() {
        UUID mob = UUID.randomUUID();
        var context = OpinionExperienceRegistry.contextFor(mob);
        ShelterNightAuthority.Hold hold = new ShelterNightAuthority.Hold(
                mob, UUID.randomUUID(), BlockPos.ZERO, 1L, ShelterNightAuthority.Phase.APPROACHING);

        OpinionDecisionTrace trace = context.discretionaryDirector().trace();
        ActivityUtilityBreakdown explore = ActivityUtilityBreakdown.explore(
                1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, -1f);
        long decisionId = trace.beginDecision(
                10L, List.of(OpinionDecisionTrace.Candidate.eligible(explore)));
        trace.conclude(
                decisionId,
                OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY,
                null,
                true);

        List<String> summary = OpinionReadoutExplanation.buildSummary(
                context,
                Optional.of(hold),
                OpinionReadoutExplanation.latestDecision(context));

        assertTrue(summary.stream().anyMatch(line -> line.contains("mandatory")));
        assertTrue(summary.stream().anyMatch(line -> line.contains("SHELTER_HOLD")));
        assertTrue(summary.stream().anyMatch(line -> line.contains("Seeking night shelter")));
        assertFalse(summary.stream().anyMatch(line -> line.contains("discretionary REST")));
    }

    @Test
    void shelterSummaryRespectsSettledPhase() {
        UUID mob = UUID.randomUUID();
        var context = OpinionExperienceRegistry.contextFor(mob);
        ShelterNightAuthority.Hold hold = new ShelterNightAuthority.Hold(
                mob, UUID.randomUUID(), BlockPos.ZERO, 1L, ShelterNightAuthority.Phase.SETTLED);

        List<String> summary = OpinionReadoutExplanation.buildSummary(
                context, Optional.of(hold), Optional.empty());

        assertTrue(summary.stream().anyMatch(line -> line.contains("Holding night shelter")));
        assertFalse(summary.stream().anyMatch(line -> line.contains("Seeking night shelter")));
    }

    @Test
    void counterfactualStatusBelongsToEachDecisionNotCurrentShelter() {
        UUID mob = UUID.randomUUID();
        var context = OpinionExperienceRegistry.contextFor(mob);
        OpinionDecisionTrace trace = context.discretionaryDirector().trace();

        ActivityUtilityBreakdown explore = ActivityUtilityBreakdown.explore(
                1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, -1f);
        long exploreDecision = trace.beginDecision(
                100L, List.of(OpinionDecisionTrace.Candidate.eligible(explore)));
        trace.conclude(
                exploreDecision,
                OpinionDecisionTrace.DecisionDisposition.INTENT_ISSUED,
                DiscretionaryActivity.EXPLORE,
                true);

        ActivityUtilityBreakdown rest = ActivityUtilityBreakdown.rest(
                1f, 2f, 3f, 4f, 5f, 6f, 7f, -1f);
        long shelterDecision = trace.beginDecision(
                200L, List.of(OpinionDecisionTrace.Candidate.eligible(rest)));
        trace.conclude(
                shelterDecision,
                OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY,
                null,
                true);

        List<OpinionReadoutDecisionView> decisions = OpinionReadoutExplanation.recentDecisions(context);
        assertEquals(2, decisions.size());
        assertFalse(decisions.getFirst().counterfactualOnly(), "daytime EXPLORE stays causal");
        assertTrue(decisions.getLast().counterfactualOnly(), "shelter-blocked eval is non-causal");
    }
}
