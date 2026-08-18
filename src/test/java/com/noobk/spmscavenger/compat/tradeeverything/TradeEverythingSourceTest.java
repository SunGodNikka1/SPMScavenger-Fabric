package com.noobk.spmscavenger.compat.tradeeverything;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.trade.OfferRef;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.TradeOpportunityQuery;
import com.noobk.spmscavenger.village.trade.TradeSourceKey;
import com.noobk.spmscavenger.village.trade.TradeSources;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * D-VR-077 step 6 — the optional source, proved without Trade Everything on the classpath.
 *
 * <h2>Why a fake bridge rather than the real one</h2>
 *
 * The dependency is {@code modCompileOnly}, so upstream classes are not on the JUnit runtime path —
 * and even if they were, mixins are not applied in a plain JUnit run, so {@code isSynthetic} would
 * throw. The interesting behaviour is not upstream's: it is Q1/Q2 strictness, {@code Requote}
 * identity, exact-object pass-through and fail-closed refusal, all of which are ours. The reflective
 * bridge gets the step-7 runtime witness; without this seam the source would arrive with runtime
 * evidence as its <i>first</i> evidence.
 *
 * <p>Entity-facing paths ({@code offers}, {@code revalidate}) need a live {@code Villager} and are
 * exercised at runtime; what is pinned here is everything reachable without one.
 */
class TradeEverythingSourceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The synthetic quote shape upstream produces: 22 oak_log -> 1 emerald, maxUses 999_999. */
    private static MerchantOffer quote(int logs, int emeralds) {
        return new MerchantOffer(new ItemCost(Items.OAK_LOG, logs), Optional.empty(),
                new ItemStack(Items.EMERALD, emeralds), 0, 999_999, 0, 0f);
    }

    private static OfferSnapshot snapshotOf(ItemStack inputKey, MerchantOffer offer) {
        return new OfferSnapshot(OfferRef.requote(inputKey), 0,
                offer.getCostA(), offer.getCostB(), offer.assemble(),
                offer.getUses(), offer.getMaxUses(), offer.getXp(),
                offer.getPriceMultiplier(), offer.getDemand(),
                offer.getSpecialPriceDiff(), offer.shouldRewardExp());
    }

    // ------------------------------------------------------------------ strictness

    /**
     * The rule that separates this source from vanilla.
     *
     * <p>Q1 and Q2 are independently generated, so there is no shared object identity to fall back
     * on. Every locked semantic field must therefore be compared — a difference means the pricing
     * inputs moved and the funding arithmetic computed on Q1 no longer holds.
     */
    @Test
    void mustHappen_everyLockedSemanticFieldIsCompared() {
        MerchantOffer q1 = quote(22, 1);
        OfferSnapshot planned = snapshotOf(new ItemStack(Items.OAK_LOG, 64), q1);

        assertTrue(planned.semanticallyMatches(quote(22, 1)), "an identical re-quote matches");

        assertFalse(planned.semanticallyMatches(quote(21, 1)), "cost moved");
        assertFalse(planned.semanticallyMatches(quote(22, 2)), "payout moved");
        assertFalse(planned.semanticallyMatches(new MerchantOffer(
                        new ItemCost(Items.OAK_LOG, 22), Optional.empty(),
                        new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f)),
                "lifetime moved - maxUses decided the whole B witness");
        assertFalse(planned.semanticallyMatches(new MerchantOffer(
                        new ItemCost(Items.OAK_LOG, 22), Optional.empty(),
                        new ItemStack(Items.EMERALD, 1), 0, 999_999, 5, 0f)),
                "xp moved");
        assertFalse(planned.semanticallyMatches(new MerchantOffer(
                        new ItemCost(Items.OAK_LOG, 22), Optional.empty(),
                        new ItemStack(Items.EMERALD, 1), 0, 999_999, 0, 0.05f)),
                "price multiplier moved");
        assertFalse(planned.semanticallyMatches(new MerchantOffer(
                        new ItemCost(Items.OAK_LOG, 22), Optional.empty(),
                        new ItemStack(Items.EMERALD, 1), 0, 999_999, 0, 0f, 4)),
                "demand moved");
    }

    /**
     * And the vanilla line stays where it was.
     *
     * <p>The same drift that strict comparison rejects must remain acceptable to
     * {@code matchesLive}, or step 5's parity claim quietly stops being true.
     */
    @Test
    void mustNotHappen_strictComparisonLeaksIntoVanillaMatchesLive() {
        MerchantOffer q1 = quote(22, 1);
        OfferSnapshot planned = snapshotOf(new ItemStack(Items.OAK_LOG, 64), q1);
        MerchantOffer semanticOnlyDrift = new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 22), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 999_999, 9, 0f, 6);

        assertFalse(planned.semanticallyMatches(semanticOnlyDrift), "strict rejects it");
        assertTrue(planned.matchesLive(semanticOnlyDrift),
                "and transaction equivalence still accepts it - the two questions differ on purpose");
    }

    /** Ordering is not identity, and the two ends of a walk assign different ordinals. */
    @Test
    void mustNotHappen_theRoundOrdinalAffectsStrictCorrespondence() {
        MerchantOffer q1 = quote(22, 1);
        OfferSnapshot planned = snapshotOf(new ItemStack(Items.OAK_LOG, 64), q1);

        assertTrue(planned.withRankOrdinal(7).semanticallyMatches(quote(22, 1)));
        assertTrue(planned.withRankOrdinal(0).semanticallyMatches(quote(22, 1)));
    }

    // ------------------------------------------------------------------ fail closed

    @Test
    void mustNotHappen_anUnavailableBridgeProducesOpportunities() {
        TradeEverythingTradeSource source =
                new TradeEverythingTradeSource(ReflectiveTradeEverythingBridge.unavailable());

        assertEquals(TradeSourceKey.TRADE_EVERYTHING, source.key());
        assertTrue(source.offers(null, TradeOpportunityQuery.none()).isEmpty());
        assertTrue(source.revalidate(null, null).isEmpty(),
                "no mod, no quote, no opportunity - and no exception either");
    }

    @Test
    void mustNotHappen_anUnregisteredSourceResolvesToVanilla() {
        TradeSources.clearOptionalSources();

        assertTrue(TradeSources.of(TradeSourceKey.TRADE_EVERYTHING).isEmpty(),
                "resolving to vanilla would send a Requote to the board resolver, which reports "
                        + "'gone' - a missing registration diagnosed as a market race");
        assertEquals(1, TradeSources.all().size());
    }

    @Test
    void mustHappen_registrationInstallsTheOptionalSourceAndOrdersVanillaFirst() {
        TradeSources.clearOptionalSources();
        TradeSources.registerTradeEverything(
                new TradeEverythingTradeSource(ReflectiveTradeEverythingBridge.unavailable()));
        try {
            assertEquals(2, TradeSources.all().size());
            assertEquals(TradeSourceKey.VANILLA, TradeSources.all().get(0).key(),
                    "vanilla stays first, so installing the optional source cannot reorder the "
                            + "candidates a round was already producing");
            assertEquals(TradeSourceKey.TRADE_EVERYTHING,
                    TradeSources.of(TradeSourceKey.TRADE_EVERYTHING).orElseThrow().key());
        } finally {
            TradeSources.clearOptionalSources();
        }
    }

    /** A source may only be registered under its own key. */
    @Test
    void mustNotHappen_aVanillaKeyedSourceIsInstalledAsOptional() {
        TradeSources.clearOptionalSources();
        TradeSources.registerTradeEverything(
                com.noobk.spmscavenger.village.trade.VanillaTradeSource.INSTANCE);

        assertTrue(TradeSources.of(TradeSourceKey.TRADE_EVERYTHING).isEmpty());
        assertEquals(1, TradeSources.all().size());
    }

    // ------------------------------------------------------------------ isolation

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    /**
     * The load-order rule, stated structurally.
     *
     * <p>A direct reference from an eagerly-loaded common class is a {@code NoClassDefFoundError} at
     * init for every user without the mod, and {@code isModLoaded} cannot prevent it because
     * resolution happens first.
     */
    @Test
    void mustNotHappen_commonTradeCodeNamesAnUpstreamClass() throws IOException {
        for (String common : java.util.List.of(
                "village/trade/TradeSources.java",
                "village/trade/VanillaTradeSource.java",
                "village/trade/TradeOpportunitySource.java",
                "village/trade/VillagerTradeAdapter.java",
                "village/trade/TradeFundingPlanner.java",
                "goal/TradeWithVillagerGoal.java")) {
            // The Trade Everything package specifically. Social Player Mobs also lives under
            // games.brennan, so a bare prefix would fire on a legitimate SPM reference and be
            // "fixed" by weakening the wrong assertion.
            assertFalse(source(common).contains("games.brennan.tradeeverything"),
                    common + " is on the common path and must not resolve an upstream class");
            assertFalse(source(common).contains("TradeEverythingTradeSource"),
                    common + " must not reference the optional source directly - it is registered "
                            + "from outside, never constructed here");
        }
    }

    /** Upstream is named in exactly one place, and only as a string. */
    @Test
    void mustHappen_upstreamIsReachedOnlyThroughTheReflectiveBridge() throws IOException {
        String bridge = source("compat/tradeeverything/ReflectiveTradeEverythingBridge.java");

        assertTrue(bridge.contains("\"games.brennan.tradeeverything.trade.RecipeValues\""));
        assertTrue(bridge.contains("\"games.brennan.tradeeverything.trade.OfferQuoter\""));
        assertTrue(bridge.contains("Class.forName"),
                "resolved at runtime, so absence is a handshake result rather than a crash");

        String sourceFile = source("compat/tradeeverything/TradeEverythingTradeSource.java");
        assertFalse(sourceFile.contains("games.brennan.tradeeverything"),
                "even the source itself goes through the bridge interface");
    }

    /** The runtime mod id, which is not the Modrinth slug the Gradle coordinate uses. */
    @Test
    void mustHappen_theRuntimeModIdMatchesThePinnedArtifact() {
        assertEquals("tradeeverything", TradeEverythingCompat.MOD_ID,
                "from the pinned jar's fabric.mod.json; the slug 'trade-everything' would have "
                        + "disabled compatibility permanently and silently");
    }

    /** No session, no menu, no fake player, no board insertion. */
    @Test
    void mustNotHappen_theSourceTouchesASessionOrTheBoard() throws IOException {
        String sourceFile = source("compat/tradeeverything/TradeEverythingTradeSource.java");

        for (String forbidden : java.util.List.of("setTradingPlayer", "MerchantMenu",
                "MerchantContainer", "FakePlayer", "ServerPlayer", "getOffers().add",
                "offers.set(", "new MerchantOffer(")) {
            assertFalse(sourceFile.contains(forbidden),
                    "P0-2 proved detached execution works; " + forbidden + " would undo that");
        }
        assertTrue(sourceFile.contains("return fresh;"),
                "Q2 itself is returned - rebuilding it strips the mixin-injected synthetic marker");
    }
}
