package com.noobk.spmscavenger.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.village.trade.TradeOpportunityQuery;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * D-VR-077 step 5 — <b>what a market source may be asked to quote.</b>
 *
 * <h2>Modelled is not disposable</h2>
 *
 * The tempting one-liner is {@code SellReserveModel.modelled(stack)}. It is wrong, and wrong in the
 * permissive direction: knowing what claims a material is not the same as having any of it spare.
 * A query built that way would authorize asking the market about a stack whose every unit is
 * already spoken for — permission granted by category rather than by quantity.
 *
 * <p>So the builder runs the reserve model <i>and then</i> {@code SellExpendabilityPolicy}, and only
 * a positive surplus makes a kind eligible.
 *
 * <h2>Eligible to ask is still not permission to spend</h2>
 *
 * Everything here decides is whether a kind may be <i>quoted</i>. The exact quantity, for the exact
 * quote, for the exact external consumer, is decided later by {@code authorizeFunding} and carried
 * in {@code SellFundingLeg}. Merging the two would be the same category error one layer up.
 */
class AuthorizedSellQueryTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TradeOpportunityQuery queryFor(SimpleContainer backpack) {
        return TradeWithVillagerGoal.authorizedSellQuery(
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, new ScavengerConfig());
    }

    private static boolean authorizes(TradeOpportunityQuery query, net.minecraft.world.item.Item item) {
        return query.authorizedSellInputs().stream().anyMatch(stack -> stack.is(item));
    }

    @Test
    void mustHappen_aModelledKindWithSurplusIsQuotable() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));

        TradeOpportunityQuery query = queryFor(backpack);

        assertTrue(authorizes(query, Items.OAK_LOG), "64 logs, at most a handful reserved");
        assertEquals(1, query.authorizedSellInputs().get(0).getCount(),
                "canonicalized - the query says which kind, never how many");
    }

    /**
     * Ignorance does not authorize asking.
     *
     * <p>{@code SellReserveModel} returns empty for anything it cannot account for. That is a
     * finding about <i>us</i>, not about the material, and a quote we could never legally act on is
     * a quote not worth paying for.
     */
    @Test
    void mustNotHappen_anUnmodelledKindReachesTheQuery() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 64));
        backpack.setItem(1, new ItemStack(Items.DIAMOND, 8));
        backpack.setItem(2, new ItemStack(Items.IRON_INGOT, 12));

        TradeOpportunityQuery query = queryFor(backpack);

        assertTrue(query.isEmpty(),
                "wheat, diamonds and iron are unmodelled - refuse, never substitute a zero reserve");
    }

    /**
     * The case {@code modelled()} alone would have got wrong.
     *
     * <p>Sticks are modelled; the craft chain reserves three. Holding exactly three means the
     * reserve model answers confidently and the surplus is nil, so the market must not be asked.
     */
    @Test
    void mustNotHappen_aModelledKindWithNoSurplusReachesTheQuery() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 3));

        assertTrue(queryFor(backpack).isEmpty(),
                "every stick is claimed; knowing that is not permission to sell them");
    }

    @Test
    void mustHappen_theSameKindAboveItsReserveBecomesQuotable() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 10));

        assertTrue(authorizes(queryFor(backpack), Items.STICK),
                "seven spare sticks - the reserve is subtracted, not the kind refused");
    }

    /** The held-item veto travels with the disposability rule, not around it. */
    @Test
    void mustNotHappen_aHeldMaterialIsQuotable() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));

        TradeOpportunityQuery inHand = TradeWithVillagerGoal.authorizedSellQuery(
                backpack, new ItemStack(Items.OAK_LOG), ItemStack.EMPTY, new ScavengerConfig());

        assertFalse(authorizes(inHand, Items.OAK_LOG),
                "the same veto that stops a held item becoming furnace fuel stops it becoming stock");
    }

    /** Several kinds, mixed legality, one bounded query. */
    @Test
    void mustHappen_onlyTheDisposableKindsSurvive() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        backpack.setItem(1, new ItemStack(Items.STICK, 3));
        backpack.setItem(2, new ItemStack(Items.WHEAT, 64));
        backpack.setItem(3, new ItemStack(Items.OAK_PLANKS, 64));

        TradeOpportunityQuery query = queryFor(backpack);

        assertTrue(authorizes(query, Items.OAK_LOG));
        assertTrue(authorizes(query, Items.OAK_PLANKS));
        assertFalse(authorizes(query, Items.STICK), "fully reserved");
        assertFalse(authorizes(query, Items.WHEAT), "unmodelled");
        assertEquals(2, query.authorizedSellInputs().size());
    }
}
