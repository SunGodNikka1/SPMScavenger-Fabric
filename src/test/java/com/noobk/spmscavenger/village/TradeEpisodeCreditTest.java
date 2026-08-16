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
}
