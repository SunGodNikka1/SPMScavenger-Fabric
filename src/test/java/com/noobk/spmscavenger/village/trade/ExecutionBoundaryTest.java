package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * V2-E-R6 — <b>planning permission does not authorize execution.</b>
 *
 * <h2>The gap this closes</h2>
 *
 * Selection computes reserves, disposable units, the deficit and the authorized quote. Then the mob
 * <i>walks</i>, for hundreds of ticks, during which crafting, smelting, eating and pickup all change
 * the inventory those numbers described. R5 rechecked merchant availability, consumer identity, the
 * live offer, affordability and output capacity at the transaction — <b>everything except whether the
 * sale was still permitted</b>:
 *
 * <pre>
 * selection   64 sticks, 3 reserved, 61 disposable, 32-stick sale -&gt; authorized
 * walk        crafting consumes 30 sticks
 * execution   34 sticks, 3 reserved, 31 disposable
 *             canAfford(32) is TRUE  -&gt; the adapter debits 32 and eats the reserve
 * </pre>
 *
 * <p>Affordability and permission answer different questions, and only one of them was being asked
 * at the boundary where the items actually move.
 */
class ExecutionBoundaryTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, CONSUMER);
    }

    private static OfferSnapshot buyIron() {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 2), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
    }

    private static OfferSnapshot sellSticks() {
        return OfferSnapshot.of(1, new MerchantOffer(
                new ItemCost(Items.STICK, 32), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
    }

    private static TradeFundingPlanner.FundingTarget derive(SimpleContainer backpack) {
        ScavengerConfig cfg = new ScavengerConfig();
        return TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buyIron(), sellSticks()), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY,
                m -> SellReserveModel.reservedUnits(m, backpack, cfg));
    }

    /**
     * The mutation the User named: <b>still affordable, no longer permitted</b>.
     *
     * <p>This is the exact shape that makes the defect invisible to every other guard. The stack is
     * physically there, the merchant is willing, the offer is unchanged — and the sale would spend
     * material the craft chain has already claimed. Nothing but a re-derivation of permission can
     * tell the difference.
     */
    @Test
    void mustNotHappen_aSaleThatBecameUnauthorizedDuringTheWalkStillExecutes() {
        SimpleContainer atSelection = new SimpleContainer(9);
        atSelection.setItem(0, new ItemStack(Items.STICK, 64));

        TradeFundingPlanner.FundingTarget planned = derive(atSelection);
        assertNotNull(planned.sellLeg(), "64 sticks less a 3-stick claim covers a 32-stick sale");
        assertTrue(planned.sellLeg().covers(sellSticks()));

        // ... the walk. Crafting consumes 30 sticks.
        SimpleContainer atExecution = new SimpleContainer(9);
        atExecution.setItem(0, new ItemStack(Items.STICK, 34));

        assertTrue(VillagerTradeAdapter.canAfford(atExecution, sellSticks()),
                "34 sticks can physically pay a 32-stick cost - affordability says yes");

        TradeFundingPlanner.FundingTarget now = derive(atExecution);
        assertNull(now.sellLeg(),
                "31 disposable against a 32-stick sale: paying would spend the craft reserve");
    }

    /** The same walk with a smaller consumption leaves the sale legal — this is not a blanket no. */
    @Test
    void mustHappen_aStillAuthorizedSaleSurvivesTheWalk() {
        SimpleContainer atExecution = new SimpleContainer(9);
        atExecution.setItem(0, new ItemStack(Items.STICK, 40));

        TradeFundingPlanner.FundingTarget now = derive(atExecution);
        assertNotNull(now.sellLeg(), "37 disposable covers a 32-stick sale");
        assertTrue(now.sellLeg().covers(sellSticks()));
    }

    /**
     * A different authorized quote is not permission to execute <b>this</b> one.
     *
     * <p>{@code covers} is the guard: re-deriving and finding <i>some</i> legal sale would otherwise
     * wave through the attempted offer, which is the wrong-offer substitution arriving at the last
     * possible moment.
     */
    @Test
    void mustNotHappen_anotherAuthorizedQuoteWavesThroughTheAttemptedOne() {
        OfferSnapshot cheaperSale = OfferSnapshot.of(1, new MerchantOffer(
                new ItemCost(Items.STICK, 10), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 20));
        ScavengerConfig cfg = new ScavengerConfig();

        TradeFundingPlanner.FundingTarget now = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buyIron(), cheaperSale), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY,
                m -> SellReserveModel.reservedUnits(m, backpack, cfg));

        assertNotNull(now.sellLeg(), "the 10-stick sale is authorized");
        assertFalse(now.sellLeg().covers(sellSticks()),
                "but it says nothing about the 32-stick quote the mob was about to attempt");
    }

    /** Funding that completed during the walk is also a reason not to sell. */
    @Test
    void mustNotHappen_aFundedPurchaseStillSells() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));
        backpack.setItem(1, new ItemStack(Items.EMERALD, 2));

        TradeFundingPlanner.FundingTarget now = derive(backpack);
        assertTrue(now.funded(), "emeralds arrived from somewhere else during the walk");
        assertNull(now.sellLeg(), "there is nothing left to fund");
    }

    /** And the guard is actually wired into the transaction, before the items move. */
    @Test
    void mustHappen_theExecutorReauthorizesBeforeTrading() throws IOException {
        String goal = Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        String attempt = goal.substring(goal.indexOf("private void attemptTransaction("));
        attempt = attempt.substring(0, attempt.indexOf((char) 10 + "    }"));

        int guard = attempt.indexOf("stillAuthorized(");
        int trade = attempt.indexOf("VillagerTradeAdapter.performTrade(");
        assertTrue(guard > 0, "the funding SELL must be re-authorized at the boundary");
        assertTrue(guard < trade, "and before the transaction, not after it");
    }

    /**
     * The successful seller keeps its place with a <b>fresh</b> budget.
     *
     * <p>{@code begin()} is idempotent for the candidate already in progress, so re-selecting the
     * same villager after a successful trade returned early and it inherited the approach ticks and
     * path failures its previous attempt had spent — the one merchant proven to trade with us was the
     * one arriving part-exhausted.
     */
    @Test
    void mustNotHappen_theSuccessfulSellerInheritsASpentBudget() {
        TradeCandidateRound round = new TradeCandidateRound();
        java.util.UUID farmer = java.util.UUID.nameUUIDFromBytes("farmer".getBytes());

        round.begin(farmer);
        for (int i = 0; i < TradeCandidateRound.APPROACH_TICK_BUDGET_PER_CANDIDATE - 1; i++) {
            assertFalse(round.recordApproachTick());
        }

        round.completeCurrentSuccessfully();
        round.begin(farmer);

        assertTrue(round.available(farmer), "success is not a demotion");
        assertEquals(0, round.approachTicks(), "and the next approach starts from zero");
        assertFalse(round.recordApproachTick(), "one tick into a fresh budget, not over it");
    }

    /** Demotion still means demotion: an unreachable villager does not get infinite retries. */
    @Test
    void mustHappen_demotionStillRemovesACandidate() {
        TradeCandidateRound round = new TradeCandidateRound();
        java.util.UUID asleep = java.util.UUID.nameUUIDFromBytes("asleep".getBytes());

        round.begin(asleep);
        round.demoteCurrent();

        assertFalse(round.available(asleep),
                "completeCurrentSuccessfully must not have blurred the two");
    }

    /** Sanity: the reserve the whole test rests on is the craft chain's, not a magic number. */
    @Test
    void mustHappen_theStickReserveComesFromTheCraftChain() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 34));

        assertEquals(3, SellReserveModel
                .reservedUnits(new ItemStack(Items.STICK), backpack, new ScavengerConfig())
                .orElseThrow());
        assertEquals(34, ScavengerCrafting.count(backpack, Items.STICK));
    }
}
