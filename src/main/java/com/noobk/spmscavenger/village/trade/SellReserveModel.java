package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.OptionalInt;

/**
 * V2-E-R4 — how many units of a material this mod's own systems have already claimed.
 *
 * <h2>The defect this replaces</h2>
 *
 * Production passed {@code material -> 0}: every material, unconditionally, fully spare. That is the
 * session's recurring shape one more time — <b>a correct permission policy whose caller fabricates
 * permissive evidence is a permission policy that is not running.</b> {@code SellExpendabilityPolicy}
 * was subtracting reserves from a reserve that was always zero, so its arithmetic was real and its
 * answer was always "yes".
 *
 * <h2>Absence of a model is not a reserve of zero</h2>
 *
 * The two are constantly confused and they are opposites. "Nothing claims this" is a <b>finding</b>;
 * "I have no idea what claims this" is <b>ignorance</b>, and ignorance must not authorize spending —
 * the same rule {@code ExistingRouteStatus.UNKNOWN} enforces one layer up. So this returns an
 * {@link OptionalInt}: empty means <i>unmodelled</i>, and the caller must refuse the material rather
 * than read the empty as zero.
 *
 * <p>The consequence is deliberate and worth stating plainly: <b>today the only authorized funding
 * stock is craft-chain surplus</b> — logs, planks and sticks beyond what the active chain claims.
 * Wheat, coal, iron and everything else are refused, not because they are precious, but because this
 * mod cannot currently say what wants them. Widening that set means writing the missing reserve
 * model for each material, never relaxing the default.
 *
 * <h2>Conservative where the claim is conditional</h2>
 *
 * Sticks are claimed by tool crafting (2) and campfire crafting (3) at different times. Rather than
 * predict which chain will run, the largest live claim is reserved. Over-reserving refuses a legal
 * sale; under-reserving sells a material the mob is about to need and stalls its own progression.
 * Those costs are not symmetric.
 */
public final class SellReserveModel {

    private SellReserveModel() {
    }

    /**
     * Units of {@code material} already spoken for.
     *
     * @return empty when no system in this mod models this material's claims — <b>refuse</b>, never
     *     substitute zero
     */
    public static OptionalInt reservedUnits(
            ItemStack material, Container backpack, ScavengerConfig cfg) {
        if (material == null || material.isEmpty() || backpack == null || cfg == null) {
            return OptionalInt.empty();
        }

        // FurnacePolicy's own predicate, not a second one. It is the module that produces the log
        // reserve, and a divergent notion of "is a log" between producer and consumer would be this
        // repair's own defect class reintroduced one layer down.
        if (FurnacePolicy.isLog(material)) {
            // The exact number FurnacePolicy itself withholds from smelting, reused rather than
            // re-derived, so a log the torch chain has claimed cannot be spare merely because a
            // villager will pay for it.
            return OptionalInt.of(FurnacePolicy.logReserveForCraftChain(backpack, cfg));
        }

        if (FurnacePolicy.isPlank(material)) {
            return OptionalInt.of(ScavengerCrafting.PLANKS_PER_TABLE
                    + ScavengerCrafting.PLANKS_PER_TOOL
                    + ScavengerCrafting.PLANKS_PER_STICK_CRAFT);
        }

        if (material.is(Items.STICK)) {
            return OptionalInt.of(Math.max(
                    ScavengerCrafting.STICKS_PER_TOOL, ScavengerCrafting.STICKS_PER_CAMPFIRE));
        }

        // Unmodelled. Coal, iron, food and every modded item land here: each is plausibly claimed by
        // smelting, eating, tool crafting or another mod, and none of those claims are quantified
        // here yet.
        return OptionalInt.empty();
    }

    /** Whether this material may be considered funding stock at all. */
    public static boolean modelled(ItemStack material, Container backpack, ScavengerConfig cfg) {
        return reservedUnits(material, backpack, cfg).isPresent();
    }
}
