package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * <b>TEMPORARY V2-TE P0-1 PROBE SUPPORT — remove with the probe.</b>
 *
 * <p>Tests the comparator, not the runtime. P0-1's runtime half is inherently manual — it needs a
 * human to open a merchant — so the part that can be pinned statically is <b>whether the comparator
 * would actually notice a difference</b>. A parity check that returns "identical" for two offers
 * that differ is worse than no check: it would licence Scavenger to plan against a quote the player
 * is never shown, and report success while doing it.
 *
 * <p>Every test below is therefore a negative control by construction: each mutates exactly one
 * field of an otherwise identical pair and requires the comparator to name that field.
 */
class Te3ParityDiffTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The TE synthetic quote the B witness produced: 22 oak_log -> 1 emerald, maxUses 999_999. */
    private static MerchantOffer quote(ItemStack costA, ItemStack result, int maxUses) {
        return new MerchantOffer(new ItemCost(costA.getItem(), costA.getCount()),
                Optional.empty(), result, 0, maxUses, 0, 0f);
    }

    private static MerchantOffer baseline() {
        return quote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1), 999_999);
    }

    @Test
    void mustHappen_twoIdenticalQuotesCompareEqual() {
        assertEquals(List.of(), Te3ProbeCommand.diffOffers(baseline(), baseline()),
                "the comparator must not manufacture a divergence between identical offers");
    }

    /**
     * The headline trap. Both offers "say emerald"; one pays twice as much.
     */
    @Test
    void mustNotHappen_aResultCountDifferenceIsReportedAsParity() {
        List<String> diff = Te3ProbeCommand.diffOffers(baseline(),
                quote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 2), 999_999));

        assertTrue(diff.stream().anyMatch(line -> line.startsWith("result:")),
                "1 emerald and 2 emerald are not the same offer: " + diff);
    }

    @Test
    void mustNotHappen_aCostCountDifferenceIsReportedAsParity() {
        List<String> diff = Te3ProbeCommand.diffOffers(baseline(),
                quote(new ItemStack(Items.OAK_LOG, 21), new ItemStack(Items.EMERALD, 1), 999_999));

        assertTrue(diff.stream().anyMatch(line -> line.startsWith("costA:")),
                "22 logs and 21 logs are not the same price: " + diff);
    }

    /**
     * R12's lesson, promoted into the parity comparator.
     *
     * <p>{@code maxUses} decided the entire B witness — {@code affordableUses} is
     * {@code min(inventoryUses, maxUses - uses)}, so a quote that looks identical in cost and result
     * funds 12 purchases instead of 17. A comparator blind to lifetime would have called that pair
     * equal.
     */
    @Test
    void mustNotHappen_aLifetimeDifferenceIsReportedAsParity() {
        List<String> diff = Te3ProbeCommand.diffOffers(baseline(),
                quote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1), 12));

        assertTrue(diff.stream().anyMatch(line -> line.startsWith("maxUses:")),
                "999_999 uses and 12 uses are not the same offer: " + diff);
    }

    /**
     * Components, not just item and count.
     *
     * <p>The toolsmith's pickaxe is an {@code EnchantedItemForEmeralds} result. If the direct path
     * ever produced the plain item where TE produced the enchanted one — or either produced a
     * damaged stack — an item-and-count comparison would call them identical while the mob planned
     * around the wrong tool.
     */
    @Test
    void mustNotHappen_aComponentDifferenceIsReportedAsParity() {
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(37);

        List<String> diff = Te3ProbeCommand.diffOffers(
                quote(new ItemStack(Items.EMERALD, 8), new ItemStack(Items.IRON_PICKAXE), 12),
                quote(new ItemStack(Items.EMERALD, 8), damaged, 12));

        assertTrue(diff.stream().anyMatch(line -> line.startsWith("result:")),
                "same item, same count, different components is not parity: " + diff);
    }

    /** Price modifiers are part of what the player is charged, so they are part of the claim. */
    @Test
    void mustNotHappen_aPriceMultiplierDifferenceIsReportedAsParity() {
        MerchantOffer multiplied = new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 22), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 999_999, 0, 0.05f);

        List<String> diff = Te3ProbeCommand.diffOffers(baseline(), multiplied);

        assertTrue(diff.stream().anyMatch(line -> line.startsWith("priceMultiplier:")),
                "a different price multiplier is a different offer: " + diff);
    }

    /** A second cost slot is a different trade, even when costA and the result match exactly. */
    @Test
    void mustNotHappen_anAddedSecondCostIsReportedAsParity() {
        MerchantOffer twoCost = new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 22),
                Optional.of(new ItemCost(Items.STICK, 4)),
                new ItemStack(Items.EMERALD, 1), 0, 999_999, 0, 0f);

        List<String> diff = Te3ProbeCommand.diffOffers(baseline(), twoCost);

        assertTrue(diff.stream().anyMatch(line -> line.startsWith("costB:")),
                "an added second cost must be named: " + diff);
    }
}
