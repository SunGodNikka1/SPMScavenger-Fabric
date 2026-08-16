package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * V2-E-R1 — the integration defects: the caller lying to the policy, and the unbounded attempt.
 *
 * <p>These are structural because the fault was never in a policy — every policy unit test was green
 * while production bypassed the guard entirely. What has to be asserted is the <b>wiring</b>.
 */
class ExistingRouteWiringTest {

    private static String source(Path relative) throws IOException {
        String raw = Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
        StringBuilder out = new StringBuilder(raw.length());
        boolean inBlock = false;
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlock = true;
                }
                continue;
            }
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * P0. `RouteEvidence.of(false, …)` told V2-C the existing route was always infeasible, which is
     * the exact fact that disables its central guard — so *feasible work + attractive trade →
     * EXISTING_WORK* was tested and unreachable.
     */
    @Test
    void mustNotHappen_theGoalHardcodesExistingRouteFeasibility() throws IOException {
        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));

        assertFalse(goal.contains("RouteEvidence.of(false"),
                "hardcoding infeasible disables V2-C's guard on every call");
        assertFalse(goal.contains("RouteEvidence.of(true"),
                "hardcoding feasible would disable trading entirely - also a lie");
        assertTrue(goal.contains("existingRouteInfeasible(level"),
                "the fact must be produced, not invented");
        assertTrue(goal.contains("ExistingRouteFeasibility.tradeMayDisplace"),
                "and produced by the dedicated authority");
    }

    /**
     * The producer's default must be "feasible". The two wrong answers are not symmetric: wrongly
     * feasible skips a trade, wrongly infeasible lets trade displace working progression.
     */
    @Test
    void mustHappen_theProducerFailsTowardExistingWork() throws IOException {
        String producer = source(Path.of("village/trade/ExistingRouteFeasibility.java"));

        // Scoped to the DECISION path. The whole-file version failed on reportRouteExhausted(),
        // which legitimately returns INFEASIBLE and is not part of the fall-through.
        int decisionStart = producer.indexOf("static ExistingRouteStatus gatherStatus(");
        int decisionEnd = producer.indexOf("public static ExistingRouteStatus reportRouteExhausted");
        assertTrue(decisionStart > 0 && decisionEnd > decisionStart);
        String decision = producer.substring(decisionStart, decisionEnd);

        int lastUnknown = decision.lastIndexOf("return ExistingRouteStatus.UNKNOWN;");
        int lastInfeasible = decision.lastIndexOf("return ExistingRouteStatus.INFEASIBLE;");
        assertTrue(lastUnknown > lastInfeasible,
                "the final fall-through must be UNKNOWN - anything unrecognised keeps work eligible");
        assertTrue(producer.contains("this == INFEASIBLE"),
                "only positively proven infeasibility may permit displacement");
    }

    /** P1. Route ownership must be revalidated during the walk, or V2-C convergence never happens. */
    @Test
    void mustHappen_continuationRevalidatesRouteOwnership() throws IOException {
        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));

        int continueStart = goal.indexOf("public boolean canContinueToUse()");
        int continueEnd = goal.indexOf("public void start()", continueStart);
        assertTrue(continueStart > 0 && continueEnd > continueStart);
        String body = goal.substring(continueStart, continueEnd);

        assertTrue(body.contains("existingRouteInfeasible"),
                "work becoming feasible again must return ownership to EXISTING_WORK mid-walk");
        assertTrue(body.contains("sameAttemptConsumer"),
                "and it must be the SAME consumer, not merely some demand");
        assertTrue(body.contains("liveDemand"), "and the demand must still exist");
        assertFalse(body.contains("inspectOffers"),
                "continuation stays cheap - exact offer checks belong at the transaction boundary");
    }

    /** P1. Offhand is part of tool ownership; dropping it manufactures demand nobody has. */
    @Test
    void mustHappen_liveDemandIncludesTheOffhand() throws IOException {
        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));

        // Anchored on the `select` call itself. The first version asserted a bare substring that
        // also occurs in existingRouteFeasible, so dropping the offhand from `select` left it
        // passing - a test matching an incidental string rather than the property.
        int select = goal.indexOf(".select(backpack");
        assertTrue(select > 0, "the demand selection exists");
        String call = goal.substring(
                select, goal.indexOf(")", goal.indexOf("ScavengerConfig.get()", select)));
        assertTrue(call.contains("getOffhandItem"),
                "WorkDemandPolicy.select must receive the offhand, not an implicit EMPTY - tool "
                        + "ownership spans backpack + main hand + offhand");
    }

    /**
     * P1. The offer's real index is retained rather than re-derived. Matching an offer back by item
     * identity is ambiguous whenever a villager sells the same pair at two counts.
     */
    @Test
    void mustNotHappen_theRealOfferIndexIsReDerivedByItemMatching() throws IOException {
        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));

        assertFalse(goal.contains("candidate.result().getItem() == flat.result().getItem()"),
                "identity was ours to keep; matching it back is ambiguous");
        assertFalse(goal.contains("private Candidate resolve("), "the reverse lookup is gone");
        assertTrue(goal.contains("new Candidate(villager, offer,"),
                "the candidate keeps the villager's own offer, real index and all");
    }

    /**
     * P1. The attempt must be bounded in ticks, not only in navigation refusals.
     *
     * <p>An accepted path that stalls consumes no failure budget, so without this the attempt can
     * outlive the claim's hard expiry — at which point the priority-1 greet sees the target again
     * and preempts the goal the interlock was protecting.
     */
    @Test
    void mustHappen_theApproachIsBoundedInTicks() throws IOException {
        String round = source(Path.of("village/trade/TradeCandidateRound.java"));
        assertTrue(round.contains("APPROACH_TICK_BUDGET_PER_CANDIDATE"));
        assertTrue(round.contains("recordApproachTick"));

        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));
        int approach = goal.indexOf("private void approach(");
        int cooldown = goal.indexOf("repathCooldown--", approach);
        int tickBudget = goal.indexOf("recordApproachTick", approach);
        assertTrue(tickBudget > 0 && tickBudget < cooldown,
                "the tick budget is consumed before the repath cooldown can skip the tick");
    }

    /** The attempt must end before its own backstop, or the claim expires under a live attempt. */
    @Test
    void mustHappen_theApproachBudgetEndsBeforeTheClaimExpires() {
        assertTrue(TradeCandidateRound.APPROACH_TICK_BUDGET_PER_CANDIDATE
                        < TradeSessionClaimWindow.MAX_CLAIM_TICKS,
                "otherwise the interlock lapses while the mob is still walking to that villager, "
                        + "and the P1 greet can preempt the goal the claim was protecting");
    }

    @Test
    void mustHappen_theApproachBudgetDemotesAndResets() {
        TradeCandidateRound round = new TradeCandidateRound();
        java.util.UUID bob = java.util.UUID.randomUUID();
        round.begin(bob);

        for (int i = 1; i < TradeCandidateRound.APPROACH_TICK_BUDGET_PER_CANDIDATE; i++) {
            assertFalse(round.recordApproachTick());
        }
        assertTrue(round.recordApproachTick(), "budget spent -> demoted");
        assertFalse(round.available(bob));
        assertEquals(0, round.approachTicks(), "and the counter resets with the candidate");
    }

    /** A fresh candidate gets a full budget; a stalled predecessor must not shorten it. */
    @Test
    void mustNotHappen_theApproachBudgetLeaksBetweenCandidates() {
        TradeCandidateRound round = new TradeCandidateRound();
        round.begin(java.util.UUID.randomUUID());
        for (int i = 0; i < 50; i++) {
            round.recordApproachTick();
        }
        round.begin(java.util.UUID.randomUUID());
        assertEquals(0, round.approachTicks());
    }

    /** Locked constraint 2 restated after the amendment: the gate is still not a director. */
    @Test
    void mustNotHappen_theFeasibilityProducerGrowsIntoAPlanner() throws IOException {
        String producer = source(Path.of("village/trade/ExistingRouteFeasibility.java"));
        for (String forbidden : List.of("private static final Map", "private final",
                "VillagerTradeAdapter", "performTrade", "getNavigation", "Villager")) {
            assertFalse(producer.contains(forbidden),
                    "the producer answers one question: " + forbidden);
        }
    }
}
