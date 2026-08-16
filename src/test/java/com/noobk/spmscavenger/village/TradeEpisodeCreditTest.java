package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * V2-G — trade familiarity, and the two things it must not become.
 *
 * <h2>Credit separation (`D-VR-057`)</h2>
 *
 * A shopping trip is not a friendship. Trade must never increment {@code socialEventCount}, or the
 * inspector and any future social threshold would read repeated commerce as social bonding.
 *
 * <h2>Visit normalization (`D-VR-063`)</h2>
 *
 * Ten offer uses in one visit teach <b>one</b> village relationship. The counter here is deliberately
 * dumb — it credits whatever it is handed, exactly once — because the once-per-visit rule lives at
 * the executor's episode boundary, where the visit actually exists. Putting it here instead would
 * mean the policy had to guess what a visit was.
 */
class TradeEpisodeCreditTest {

    private static SettlementRelationship fresh() {
        return SettlementRelationship.empty();
    }

    // ------------------------------------------------- credit separation

    @Test
    void mustNotHappen_tradeIncrementsTheSocialCounter() {
        SettlementRelationship relationship = fresh();

        relationship.recordTradeEpisode(100L);
        relationship.recordTradeEpisode(200L);

        assertEquals(2, relationship.tradeEpisodeCount());
        assertEquals(0, relationship.socialEventCount(),
                "a shopping trip is not a social event - D-VR-057 separates the credit");
    }

    @Test
    void mustNotHappen_aGreetIncrementsTheTradeCounter() {
        SettlementRelationship relationship = fresh();

        relationship.recordSocialEpisode(100L);

        assertEquals(1, relationship.socialEventCount());
        assertEquals(0, relationship.tradeEpisodeCount(), "the two totals are never shared");
    }

    /** Both feed familiarity — separation is of the counters, not of the relationship itself. */
    @Test
    void mustHappen_bothEpisodeKindsBuildFamiliarity() {
        SettlementRelationship traded = fresh();
        traded.recordTradeEpisode(100L);

        assertEquals(SettlementTuning.TRADE_FAMILIARITY_BUMP, traded.familiarityScore());
        assertEquals(100L, traded.lastVisitTick(), "a completed trade is a meaningful visit");
    }

    // ------------------------------------------------- persistence

    /**
     * The count survives a save/load round trip, and pre-V2-G saves load as zero.
     *
     * <p>No migration is needed precisely because absence and "never traded" are the same value —
     * the one case where reading a missing field as zero is honest rather than fabricated.
     */
    @Test
    void mustHappen_theTradeCountSurvivesSaveAndLoad() {
        SettlementRelationship relationship = fresh();
        relationship.recordTradeEpisode(50L);
        relationship.recordSocialEpisode(60L);

        SettlementRelationship reloaded = SettlementRelationship.load(relationship.save());

        assertEquals(1, reloaded.tradeEpisodeCount());
        assertEquals(1, reloaded.socialEventCount());
        assertEquals(relationship.familiarityScore(), reloaded.familiarityScore());
    }

    @Test
    void mustHappen_aPreV2gSaveLoadsAsNeverTraded() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("familiarity", 120);
        legacy.putLong("lastVisit", 400L);
        legacy.putInt("socialEvents", 3);

        SettlementRelationship loaded = SettlementRelationship.load(legacy);

        assertEquals(0, loaded.tradeEpisodeCount());
        assertEquals(3, loaded.socialEventCount(), "and the existing fields are untouched");
        assertEquals(120, loaded.familiarityScore());
    }

    /** Anchor merges sum both totals independently; neither leaks into the other. */
    @Test
    void mustHappen_anchorMergePreservesBothTotalsSeparately() {
        SettlementRelationship left = fresh();
        left.recordTradeEpisode(10L);
        left.recordTradeEpisode(20L);
        SettlementRelationship right = fresh();
        right.recordSocialEpisode(30L);

        left.mergeWith(right);

        assertEquals(2, left.tradeEpisodeCount());
        assertEquals(1, left.socialEventCount());
    }

    @Test
    void mustNotHappen_theTradeCounterExceedsItsCeiling() {
        SettlementRelationship left = new SettlementRelationship(
                0, 0L, 0, 0, 0L, 0L, SettlementTuning.MAX_TRADE_EPISODE_COUNT);
        SettlementRelationship right = new SettlementRelationship(
                0, 0L, 0, 0, 0L, 0L, SettlementTuning.MAX_TRADE_EPISODE_COUNT);

        left.mergeWith(right);
        left.recordTradeEpisode(1L);

        assertEquals(SettlementTuning.MAX_TRADE_EPISODE_COUNT, left.tradeEpisodeCount());
    }

    // ------------------------------------------------- the once-per-visit boundary

    private static String goalSource() throws IOException {
        return Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String methodOf(String source, String signature) {
        String body = source.substring(source.indexOf(signature));
        return body.substring(0, body.indexOf((char) 10 + "    }"));
    }

    /**
     * `D-VR-063` lives in the executor, and this is where it can actually be checked.
     *
     * <p>The anchor is captured on the <b>first</b> success only, and emission clears it. That pair
     * is the whole rule: without the null check a five-sale chain would re-resolve five times, and
     * without the clear the two teardown paths would credit twice.
     */
    @Test
    void mustHappen_theEpisodeIsBoundedToOnePerVisit() throws IOException {
        String goal = goalSource();

        assertTrue(methodOf(goal, "private void continueChain(")
                        .contains("if (tradeEpisodeAnchor == null)"),
                "the anchor is captured at the first success, not at every use in the chain");

        String emit = methodOf(goal, "private void emitTradeEpisode(");
        assertTrue(emit.contains("tradeEpisodeAnchor = null;"),
                "emission clears the anchor - that is what makes the two teardown paths idempotent");
        assertTrue(emit.indexOf("tradeEpisodeAnchor = null;")
                        < emit.indexOf("SettlementRelationshipService.onTradeEpisode("),
                "cleared before crediting, so a throwing credit cannot leave a re-creditable anchor");
        assertTrue(emit.contains("if (anchor == null)"),
                "a round that never transacted, or one outside any settlement, credits nothing");
    }

    /**
     * A failed or aborted round teaches nothing (`D-VR-063` rejects learning from path failure).
     *
     * <p>Enforced by construction rather than by a check: the anchor is only ever assigned in
     * {@code continueChain}, which is reached exclusively from a {@code TRADED} result.
     */
    @Test
    void mustNotHappen_anAbortedRoundCreditsAnEpisode() throws IOException {
        String goal = goalSource();

        assertFalse(methodOf(goal, "private void reselect(").contains("tradeEpisodeAnchor ="),
                "a demoted candidate is not a completed trade");
        assertEquals(1, goal.split("tradeEpisodeAnchor = SettlementRelationshipService", -1).length - 1,
                "exactly one assignment site, and it is behind a successful transaction");
    }

    /** Both teardown paths emit, so a combat interruption after a real trade still credits it. */
    @Test
    void mustHappen_anInterruptedVisitStillCreditsItsCompletedTrade() throws IOException {
        String goal = goalSource();

        assertTrue(methodOf(goal, "private void endRound(").contains("emitTradeEpisode(level)"));
        assertTrue(methodOf(goal, "public void stop() {").contains("emitTradeEpisode(level)"),
                "a visit that traded and was then preempted by combat still happened");
    }

    /** And the release still precedes it, so a claim can never outlive the goal. */
    @Test
    void mustNotHappen_theEpisodeEmitDelaysTheClaimRelease() throws IOException {
        String stop = methodOf(goalSource(), "public void stop() {");

        assertTrue(stop.indexOf("TradeSessionClaimWindow.release")
                        < stop.indexOf("emitTradeEpisode"),
                "nothing, including a throwing credit, may come between stop() and the release");
    }

    /** Trade credit must not be routed through the social path (`D-VR-057` rejected exactly that). */
    @Test
    void mustNotHappen_theExecutorCreditsTradeAsASocialEpisode() throws IOException {
        assertFalse(goalSource().contains("onSocialEpisode"),
                "crediting trade through the greet binding was explicitly rejected");
        assertNotEquals(-1, goalSource().indexOf("SettlementRelationshipService.onTradeEpisode("));
    }

    // ------------------------------------------------- once per chain, across preemption (R1)

    private static com.noobk.spmscavenger.village.trade.TradeChainPlan chain(long createdAt) {
        return com.noobk.spmscavenger.village.trade.TradeChainPlan.forDemand(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "spmscavenger", "iron_tool_frontier"),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "minecraft", "iron_ingot"),
                0, 2, createdAt);
    }

    /**
     * The R1 defect, as the exact sequence the User specified.
     *
     * <p>V2-G cleared the anchor at teardown, which bounds credit inside one uninterrupted visit.
     * But {@code TradeChainPlan} survives {@code stop()} on purpose — that is the hard lifetime
     * Option A exists to protect — so an interruption is not the end of the chain, and the resumed
     * chain would earn a second episode for the same bounded visit.
     */
    @Test
    void mustNotHappen_aResumedChainEarnsASecondEpisode() {
        com.noobk.spmscavenger.village.trade.TradeEpisodeLedger ledger =
                new com.noobk.spmscavenger.village.trade.TradeEpisodeLedger();
        com.noobk.spmscavenger.village.trade.TradeChainPlan funding = chain(1_000L);

        // SELL succeeds, then combat preempts and stop() credits immediately.
        assertTrue(ledger.consumeCreditFor(funding), "the first completed trade credits");

        // Combat ends; the SAME chain resumes and its BUY leg succeeds.
        assertFalse(ledger.consumeCreditFor(funding),
                "one bounded chain teaches one village relationship, interruption or not");
        assertFalse(ledger.consumeCreditFor(funding), "and no number of resumptions changes that");
    }

    /** A step transition is the same chain: {@code at()} mints a new record, not a new visit. */
    @Test
    void mustNotHappen_aStepTransitionCountsAsANewChain() {
        com.noobk.spmscavenger.village.trade.TradeEpisodeLedger ledger =
                new com.noobk.spmscavenger.village.trade.TradeEpisodeLedger();
        com.noobk.spmscavenger.village.trade.TradeChainPlan selling = chain(1_000L);

        assertTrue(ledger.consumeCreditFor(selling));
        assertFalse(ledger.consumeCreditFor(selling.at(
                        com.noobk.spmscavenger.village.trade.TradeChainPlan.Step.BUY_TARGET)),
                "SELL_TO_FUND -> BUY_TARGET is one chain advancing, not a second visit");
    }

    /**
     * A genuinely new chain is a new visit and earns its own episode — on identity alone.
     *
     * <p>R2 deleted the explicit reset. It made planning mutate learning state, and planning runs
     * between a completed transaction and its emission, so a pending episode could be credited after
     * its own history had been erased. {@code sameChainAs} already separates visits.
     */
    @Test
    void mustHappen_aNewChainEarnsItsOwnEpisode() {
        com.noobk.spmscavenger.village.trade.TradeEpisodeLedger ledger =
                new com.noobk.spmscavenger.village.trade.TradeEpisodeLedger();

        assertTrue(ledger.consumeCreditFor(chain(1_000L)));
        assertTrue(ledger.consumeCreditFor(chain(9_000L)),
                "a later visit for a later demand is a genuinely separate relationship episode");
    }

    /**
     * Chain identity itself, exercised directly.
     *
     * <p>The ledger tests above always call {@code onChainOpened()} between chains, so they never
     * reach the identity comparison — dropping {@code createdAtTick} from {@code sameChainAs} left
     * every one of them green. In production the reset does always fire, which makes the tick
     * defence-in-depth; but a defence nothing exercises is a defence nobody will notice losing.
     */
    @Test
    void mustNotHappen_twoChainsForOneConsumerAreTreatedAsOne() {
        com.noobk.spmscavenger.village.trade.TradeChainPlan first = chain(1_000L);
        com.noobk.spmscavenger.village.trade.TradeChainPlan later = chain(9_000L);

        assertFalse(first.sameChainAs(later),
                "same consumer and output, different visit - the creation tick is what separates "
                        + "a second visit from the same chain advancing");
        assertTrue(first.sameChainAs(first.at(
                        com.noobk.spmscavenger.village.trade.TradeChainPlan.Step.BUY_TARGET)),
                "and a step transition preserves createdAtTick, so it stays one chain");
        assertFalse(first.sameChainAs(null));
    }

    /** Without the reset, a later chain must still earn its own credit on identity alone. */
    @Test
    void mustHappen_identityAloneSeparatesTwoVisits() {
        com.noobk.spmscavenger.village.trade.TradeEpisodeLedger ledger =
                new com.noobk.spmscavenger.village.trade.TradeEpisodeLedger();

        assertTrue(ledger.consumeCreditFor(chain(1_000L)));
        assertTrue(ledger.consumeCreditFor(chain(9_000L)),
                "a genuinely later chain is a separate visit even if onChainOpened were missed");
    }

    /**
     * R3 — pending evidence with no chain owner is refused.
     *
     * <p>This test replaces a pre-R2 one that asserted the opposite. Then, the caller passed the
     * <b>live</b> chain and {@code null} meant "terminated between the trade and teardown", which was
     * a real episode. R2 made the caller pass the chain that <b>earned</b> the episode, captured in
     * the same guard as the anchor — so {@code null} now has no legitimate producer at all, and
     * crediting it would credit an episode nothing can account for.
     */
    @Test
    void mustNotHappen_evidenceWithoutAChainOwnerIsCredited() {
        com.noobk.spmscavenger.village.trade.TradeEpisodeLedger ledger =
                new com.noobk.spmscavenger.village.trade.TradeEpisodeLedger();

        assertFalse(ledger.consumeCreditFor(null),
                "a lost owner is not a terminated chain - fail closed");
        assertFalse(ledger.hasSpentCredit(),
                "and refusing must not consume the slot a real chain will need");
    }

    /** Credit is restored only by a new chain — never by teardown. */
    @Test
    void mustNotHappen_planningMutatesLearningState() throws IOException {
        String goal = goalSource();

        assertFalse(goal.contains("onChainOpened"),
                "R2: planning must not touch learning state at all - advanceChain runs between a "
                        + "completed transaction and its emission, so resetting there erased the "
                        + "history of an episode that had not yet been credited");
        assertTrue(methodOf(goal, "private void emitTradeEpisode(")
                        .contains("episodeLedger.consumeCreditFor(earnedBy)"),
                "and emission credits the chain that EARNED it, never the live field");
    }

    /** R2: both teardown paths release the greet claim before crediting, not just stop(). */
    @Test
    void mustNotHappen_eitherTeardownPathDelaysTheClaimRelease() throws IOException {
        String goal = goalSource();

        for (String path : new String[] {"public void stop() {", "private void endRound("}) {
            String body = methodOf(goal, path);
            assertTrue(body.indexOf("TradeSessionClaimWindow.release")
                            < body.indexOf("emitTradeEpisode"),
                    path + " must release the claim before crediting - a throwing credit would "
                            + "otherwise leak the greet interlock");
        }
    }

    // ------------------------------------------------- chain handoff (R2)

    /**
     * The R2 defect, as the User sequenced it.
     *
     * <p>Emission is not simultaneous with the transaction. {@code continueChain} records the anchor,
     * then replans — and replanning can terminate chain A and mint chain B <b>before</b> teardown
     * emits anything:
     *
     * <pre>
     * A credited, combat, A resumes, A's BUY succeeds   -&gt; pending anchor belongs to A
     * demand changes; advanceChain terminates A, mints B
     * B's trade admission fails
     * endRound emits the pending A episode ... against B
     * </pre>
     *
     * Two wrongs at once: A is credited a second time (its history had just been reset), and B is
     * marked spent without ever having traded.
     */
    @Test
    void mustNotHappen_aPendingEpisodeIsCreditedAgainstTheNextChain() {
        com.noobk.spmscavenger.village.trade.TradeEpisodeLedger ledger =
                new com.noobk.spmscavenger.village.trade.TradeEpisodeLedger();
        com.noobk.spmscavenger.village.trade.TradeChainPlan a = chain(1_000L);
        com.noobk.spmscavenger.village.trade.TradeChainPlan b = chain(5_000L);

        // A's first success, credited at the combat interruption.
        assertTrue(ledger.consumeCreditFor(a));

        // A resumes and succeeds again; the anchor and A's identity are captured together.
        // Replanning then hands over to B before teardown runs.
        assertFalse(ledger.consumeCreditFor(a),
                "the pending episode belongs to A, and A has already been credited");

        // B has NOT been marked spent by A's emission, so its own first success still earns one.
        assertTrue(ledger.consumeCreditFor(b),
                "B must still be able to earn exactly one episode when it actually trades");
        assertFalse(ledger.consumeCreditFor(b), "and only one");
    }

    /** The pending anchor and the pending chain are captured and cleared as a pair. */
    @Test
    void mustHappen_thePendingEpisodeCarriesItsOwnChainIdentity() throws IOException {
        String goal = goalSource();
        String capture = methodOf(goal, "private void continueChain(");

        assertTrue(capture.contains("tradeEpisodeChain = chain;"),
                "the chain that earned the episode is recorded at the moment of success");
        assertTrue(capture.indexOf("tradeEpisodeAnchor =") < capture.indexOf("tradeEpisodeChain ="),
                "and inside the same first-success guard as the anchor");

        String emit = methodOf(goal, "private void emitTradeEpisode(");
        assertTrue(emit.contains("tradeEpisodeChain = null;"),
                "both pending fields are cleared before the service is called");
        assertTrue(emit.indexOf("tradeEpisodeChain = null;")
                        < emit.indexOf("SettlementRelationshipService.onTradeEpisode("),
                "cleared first, so no path can re-emit a pending episode");
    }
}
