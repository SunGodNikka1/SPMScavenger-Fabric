package com.noobk.spmscavenger.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * <b>TEMPORARY V2-H PROOF SUPPORT — remove after VR-T2 is captured.</b>
 *
 * <p>Everything the harness observed at T0, held privately so the verdict can be a <b>conjunction of
 * evidence</b> rather than an inference from inventory.
 *
 * <h2>Oracle state is not a boundary violation</h2>
 *
 * "Nothing crosses the setup boundary" means nothing is <i>supplied to the AI</i>. The debugger is
 * allowed to remember what it saw — indeed it must, because a uses-delta or a component comparison is
 * meaningless without a baseline. The mob still rediscovers the villagers, reads the same live
 * boards, derives the same price and revalidates independently.
 *
 * <h2>Why inventory inference is not proof</h2>
 *
 * "sticks went down and emeralds went up" is a good <i>narrative</i> signal and a bad <i>oracle</i>:
 * it cannot distinguish the fixture's Fletcher from any other villager, cannot see a trade that
 * happened against a different offer, and would count a pickup as a sale. So the narrative may keep
 * inferring; PASS compares the <b>exact captured {@code MerchantOffer} uses</b> and the <b>exact
 * captured result stack</b>.
 *
 * @param toolsmithResult a defensive copy including components — the acquired tool must match this
 *     exactly, not merely be an iron pickaxe
 */
public record Vrt2Oracle(
        UUID mobId,
        UUID fletcherId,
        int fletcherOfferIndex,
        int fletcherBaselineUses,
        ItemStack fletcherCost,
        UUID toolsmithId,
        int toolsmithOfferIndex,
        int toolsmithBaselineUses,
        int price,
        ItemStack toolsmithResult,
        BlockPos settlementAnchor,
        int episodeBaseline,
        ResourceLocation t0Consumer,
        String t0RouteStatus) {

    /** Exactly four Fletcher sales, by construction of the fixed-deficit fixture. */
    public static final int EXPECTED_SELLS = 4;
    /** Exactly one Toolsmith purchase. */
    public static final int EXPECTED_BUYS = 1;
    /** Four sales plus one purchase must still teach one settlement relationship episode. */
    public static final int EXPECTED_EPISODES = 1;

    public Vrt2Oracle {
        fletcherCost = fletcherCost == null ? ItemStack.EMPTY : fletcherCost.copy();
        toolsmithResult = toolsmithResult == null ? ItemStack.EMPTY : toolsmithResult.copy();
    }

    /** Whether an acquired stack is the exact tool the Toolsmith quoted, components included. */
    public boolean matchesQuotedTool(ItemStack acquired) {
        return acquired != null
                && !acquired.isEmpty()
                && ItemStack.isSameItemSameComponents(acquired, toolsmithResult);
    }

    public String describe() {
        return "  mob            = " + mobId + "\n"
                + "  fletcher       = " + fletcherId + " offer#" + fletcherOfferIndex
                + " uses@T0=" + fletcherBaselineUses + "\n"
                + "  toolsmith      = " + toolsmithId + " offer#" + toolsmithOfferIndex
                + " uses@T0=" + toolsmithBaselineUses + " price=" + price + "\n"
                + "  quoted tool    = " + toolsmithResult.getCount() + "x "
                + toolsmithResult.getItem() + (toolsmithResult.isEnchanted() ? " (enchanted)" : "")
                + "\n"
                + "  T0 consumer    = " + t0Consumer + "\n"
                + "  T0 route       = " + t0RouteStatus + "\n"
                + "  episodes@T0    = " + episodeBaseline + "\n";
    }
}
