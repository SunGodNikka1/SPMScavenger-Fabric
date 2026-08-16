package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** V2-E — the greet interlock and the candidate-attempt round. */
class TradeInterlockAndRoundTest {

    private static final UUID GOD = UUID.randomUUID();
    private static final UUID OTHER_MOB = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();

    @BeforeEach
    void clean() {
        TradeSessionClaimWindow.shutdownServerState();
    }

    // ---------------------------------------------------------------- interlock

    /** The collision it exists for: my trade target, my greet. */
    @Test
    void mustHappen_theClaimSuppressesOnlyTheClaimedPairing() {
        TradeSessionClaimWindow.claim(GOD, BOB, 100L);

        assertTrue(TradeSessionClaimWindow.claims(GOD, BOB, 101L), "my greet of my trade target");
        assertFalse(TradeSessionClaimWindow.claims(GOD, ALICE, 101L),
                "I may still greet Alice while trading with Bob");
        assertFalse(TradeSessionClaimWindow.claims(OTHER_MOB, BOB, 101L),
                "another mob may still greet Bob - this is not a villager reservation");
    }

    /**
     * The 44D-R2 distinction, asserted rather than asserted-in-prose: with no claim, nothing is
     * suppressed. A global veto would fail this.
     */
    @Test
    void mustNotHappen_theInterlockSuppressesAnythingWithoutAClaim() {
        assertFalse(TradeSessionClaimWindow.claims(GOD, BOB, 1L));
        assertFalse(TradeSessionClaimWindow.claims(GOD, ALICE, 1L));
        assertFalse(TradeSessionClaimWindow.claims(null, BOB, 1L));
        assertFalse(TradeSessionClaimWindow.claims(GOD, null, 1L));
    }

    @Test
    void mustHappen_theClaimExpiresOnItsOwn() {
        TradeSessionClaimWindow.claim(GOD, BOB, 0L);

        assertTrue(TradeSessionClaimWindow.claims(GOD, BOB,
                TradeSessionClaimWindow.MAX_CLAIM_TICKS - 1));
        assertFalse(TradeSessionClaimWindow.claims(GOD, BOB,
                TradeSessionClaimWindow.MAX_CLAIM_TICKS),
                "a claim nobody released must not suppress greeting forever");
        assertEquals(0, TradeSessionClaimWindow.trackedClaimCount(), "and it is deleted, not merely expired");
    }

    /** Release must be safe to call blindly — `stop()` does exactly that. */
    @Test
    void mustHappen_releaseIsUnconditionalAndIdempotent() {
        TradeSessionClaimWindow.release(GOD);
        TradeSessionClaimWindow.claim(GOD, BOB, 0L);
        TradeSessionClaimWindow.release(GOD);
        TradeSessionClaimWindow.release(GOD);
        TradeSessionClaimWindow.release(null);

        assertFalse(TradeSessionClaimWindow.claims(GOD, BOB, 1L));
        assertEquals(0, TradeSessionClaimWindow.trackedClaimCount());
    }

    /** One claim per mob: re-targeting replaces, so Bob is freed the moment we switch to Alice. */
    @Test
    void mustNotHappen_aRetargetLeavesTheOldVillagerSuppressed() {
        TradeSessionClaimWindow.claim(GOD, BOB, 0L);
        TradeSessionClaimWindow.claim(GOD, ALICE, 5L);

        assertFalse(TradeSessionClaimWindow.claims(GOD, BOB, 6L));
        assertTrue(TradeSessionClaimWindow.claims(GOD, ALICE, 6L));
        assertEquals(1, TradeSessionClaimWindow.trackedClaimCount());
    }

    // ---------------------------------------------------------------- round

    /** The whole point: unreachable A must not be re-selected forever while B is right there. */
    @Test
    void mustHappen_anExhaustedCandidateIsDemotedAndTheNextIsTried() {
        TradeCandidateRound round = new TradeCandidateRound();
        round.begin(BOB);

        for (int i = 1; i < TradeCandidateRound.PATH_BUDGET_PER_CANDIDATE; i++) {
            assertFalse(round.recordPathFailure(), "still within budget for Bob");
            assertEquals(BOB, round.current());
        }
        assertTrue(round.recordPathFailure(), "budget spent");
        assertFalse(round.available(BOB), "Bob is demoted for this round");
        assertTrue(round.available(ALICE), "Alice is still eligible");
    }

    @Test
    void mustHappen_theRoundEndsOnlyWhenEveryCandidateIsExhausted() {
        TradeCandidateRound round = new TradeCandidateRound();

        round.begin(BOB);
        round.demoteCurrent();
        assertFalse(round.exhausted(2), "one of two tried");

        round.begin(ALICE);
        round.demoteCurrent();
        assertTrue(round.exhausted(2));
    }

    /** Exhaustion yields to a cooldown; a fresh round makes the demoted candidate eligible again. */
    @Test
    void mustHappen_aFreshRoundRestoresEveryCandidate() {
        TradeCandidateRound round = new TradeCandidateRound();
        round.begin(BOB);
        round.demoteCurrent();
        round.endRound(1_000L);

        assertTrue(round.coolingDown(1_000L));
        assertTrue(round.coolingDown(
                1_000L + TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS - 1));
        assertFalse(round.coolingDown(
                1_000L + TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS));

        assertTrue(round.available(BOB), "no permanent blacklist");
        assertEquals(0, round.attemptedCount());
    }

    @Test
    void mustHappen_beginIsIdempotentForTheCandidateInProgress() {
        TradeCandidateRound round = new TradeCandidateRound();
        round.begin(BOB);
        round.recordPathFailure();
        round.begin(BOB);
        // Would reset the budget and never demote if begin were not idempotent.
        round.recordPathFailure();
        assertTrue(round.recordPathFailure(), "three failures demote Bob, however often begin ran");
    }

    // ---------------------------------------------------------------- structural

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
     * Locked constraint 6, and the correction that mattered most: the interlock must run **before**
     * the target is published into the SOCIAL control plane, or Opinion forms a SOCIAL intent for an
     * executor we deliberately made unavailable.
     */
    @Test
    void mustHappen_theInterlockPrecedesSocialPublication() throws IOException {
        String seam = source(Path.of("mixin/FriendlyGreetAdmissionSeamMixin.java"));

        int interlock = seam.indexOf("TradeSessionClaimWindow.claims");
        int publish = seam.indexOf("SocialAdmissionSeam.recordObservation");
        int invoke = seam.indexOf("SocialAdmissionSeam.invokeOriginal");

        assertTrue(invoke > 0 && interlock > invoke,
                "the interlock needs SPM's own chosen target, so it follows invokeOriginal");
        assertTrue(publish > interlock,
                "and it must precede recordObservation - otherwise Opinion forms a SOCIAL intent "
                        + "for a target we are about to refuse");
    }

    /** Locked constraint 9: `stop()` is an unconditional cleanup boundary. */
    @Test
    void mustHappen_stopReleasesTheClaimUnconditionally() throws IOException {
        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));

        int stop = goal.indexOf("public void stop()");
        assertTrue(stop > 0);
        String body = goal.substring(stop, goal.indexOf("public void tick()", stop));
        assertTrue(body.contains("TradeSessionClaimWindow.release"),
                "combat, shelter and commands all arrive at stop(); a claim outliving its goal "
                        + "suppresses greeting for a villager nobody is trading with");
        assertFalse(body.contains("if ("), "release must be unconditional, not one of several exits");
    }

    /** Locked constraint 5: released on unload, death and server stop as well. */
    @Test
    void mustHappen_theClaimIsReleasedAcrossTheMobLifecycle() throws IOException {
        String bootstrap = source(Path.of("SpmScavenger.java"));
        int releases = bootstrap.split(
                java.util.regex.Pattern.quote("TradeSessionClaimWindow.release(mob.getUUID())"), -1)
                .length - 1;
        assertEquals(2, releases, "ENTITY_UNLOAD and AFTER_DEATH");
        assertTrue(bootstrap.contains("TradeSessionClaimWindow.shutdownServerState"),
                "and cleared with the server");
    }

    /** Locked constraint 2: the gate answers a question; it does not become a second director. */
    @Test
    void mustNotHappen_theGateGrowsIntoADirector() throws IOException {
        String gate = source(Path.of("village/trade/TradeDemandGate.java"));
        for (String forbidden : List.of(
                "private static final Map", "private final", "static Map",
                "Comparator", "sort(",
                "Container", "Level", "Villager", "performTrade")) {
            assertFalse(gate.contains(forbidden),
                    "TradeDemandGate must stay a thin seam, not " + forbidden);
        }
        assertTrue(gate.contains("TradeDemandRegistrar.decide"),
                "it defers the route decision rather than making one");
    }

    /** Locked constraint 7: the adapter refuses a busy or unavailable merchant before mutating. */
    @Test
    void mustHappen_theAdapterRefusesBusyAndUnavailableMerchantsBeforeMutating() throws IOException {
        String adapter = source(Path.of("village/trade/VillagerTradeAdapter.java"));

        int busy = adapter.indexOf("getTradingPlayer() != null");
        int asleep = adapter.indexOf("villager.isSleeping()");
        int execute = adapter.indexOf("return executeAgainst(");

        assertTrue(busy > 0 && busy < execute, "player-occupied merchant refused before execution");
        assertTrue(asleep > 0 && asleep < execute, "sleeping merchant refused before execution");
        assertTrue(adapter.contains("MERCHANT_BUSY") && adapter.contains("MERCHANT_UNAVAILABLE"),
                "and distinguishably, so the executor can demote rather than abandon the route");
    }

    /** Locked constraint 6 (executor half): offers are inspected per candidate, never swept. */
    @Test
    void mustNotHappen_theGoalSweepsOffersPassively() throws IOException {
        // R6: restated as the actual property. This was a whole-file index comparison, and it broke
        // the moment a SECOND, legitimate call site appeared earlier in the file - the execution
        // boundary re-inspecting the villager it is standing in front of. "First inspectOffers in
        // the file" was never the rule; "no offer list is touched for a villager that was not
        // already selected" is.
        String goal = source(Path.of("goal/TradeWithVillagerGoal.java"));
        final String CALL = "VillagerTradeAdapter.inspectOffers(";
        String discovery = goal.substring(goal.indexOf("private Optional<AuthorizedAttempt> authorizedCandidate("));
        discovery = discovery.substring(0, discovery.indexOf((char) 10 + "    }"));

        int filter = discovery.indexOf("VillagerTradeAdapter.available(villager)");
        int inspect = discovery.indexOf("VillagerTradeAdapter.inspectOffers");
        assertTrue(filter > 0 && filter < inspect,
                "candidates are filtered for availability before any offer list is touched - "
                        + "getOffers() lazily populates trades");

        int from = 0;
        int sites = 0;
        while ((from = goal.indexOf(CALL, from)) >= 0) {
            String argument = goal.substring(from + CALL.length());
            argument = argument.substring(0, argument.indexOf(')'));
            assertTrue(argument.equals("villager") || argument.equals("target"),
                    "offers may only be inspected for an already-selected candidate, not " + argument);
            sites++;
            from += CALL.length();
        }
        assertTrue(sites >= 1, "the discovery call site must still exist");
    }
}
