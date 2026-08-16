package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * V2-E-R1 — can gather / smelt / craft satisfy this demand <b>right now</b>?
 *
 * <h2>The hole this closes</h2>
 *
 * {@code TradeWithVillagerGoal} passed {@code RouteEvidence.of(false, …)} — a hardcoded "the
 * existing route is infeasible" — to {@link TradeDemandRegistrar}. The first branch of
 * {@code decide} returns {@code EXISTING_WORK} whenever that fact is true, so production told the
 * policy the one thing that disables its central guard, every call. V2-C's invariant
 * <i>feasible work + attractive trade → EXISTING_WORK</i> was fully tested and **never reachable in
 * the game**: policy correct, caller lying, unit tests green.
 *
 * <h2>Why it fails toward EXISTING_WORK</h2>
 *
 * The two wrong answers are not symmetric.
 *
 * <ul>
 *   <li>Wrongly <b>feasible</b> → trade is skipped; the mob keeps doing work that may be slower.
 *       Visible, recoverable, costs a purchase.</li>
 *   <li>Wrongly <b>infeasible</b> → trade displaces working progression, which is precisely the
 *       failure V2-C's gate 3/7 exists to prevent.</li>
 * </ul>
 *
 * So anything this class cannot positively rule out counts as feasible.
 *
 * <h2>What it actually covers, and what it does not</h2>
 *
 * {@code EXISTING_WORK} is deliberately an opaque bucket in V2-C, and this producer does not pretend
 * to be a complete model of it:
 *
 * <ul>
 *   <li><b>Covered:</b> a live smelt plan whose output is the demanded material; holding the
 *       precursor that would become it; an open local gather deficit pulling that precursor.</li>
 *   <li><b>Not covered:</b> reachability of a furnace or of ore, crafting routes with no smelt step,
 *       and materials outside the iron/charcoal chains this mod currently progresses. Those all fall
 *       to the conservative default above rather than being silently treated as infeasible.</li>
 * </ul>
 *
 * Stated plainly because a future material added to `WorkDemandPolicy` without a branch here will
 * read as feasible and quietly disable trading for it — a visible bug, not a corrupting one, but one
 * whose cause should be findable.
 */
public final class ExistingRouteFeasibility {

    private static final ResourceLocation IRON_INGOT =
            BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.IRON_INGOT);
    private static final ResourceLocation CHARCOAL =
            BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.CHARCOAL);

    private ExistingRouteFeasibility() {
    }

    /**
     * @return {@code true} when gather/smelt/craft can make progress on this demand now, or when
     *     this producer cannot tell
     */
    public static boolean canSatisfy(
            net.minecraft.server.level.ServerLevel level,
            WorkDemandPolicy.MaterialDemand demand,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg) {
        if (level == null || demand == null || backpack == null || cfg == null) {
            return true; // cannot tell -> do not displace work
        }

        // A live smelt plan producing exactly this material is the existing route, running.
        java.util.Optional<FurnacePolicy.SmeltPlan> plan =
                FurnacePolicy.plan(level, backpack, mainHand, offHand, cfg);
        if (plan.isPresent() && producesDemanded(plan.get(), demand)) {
            return true;
        }

        // Iron: holding raw iron, or still pulling it through the gather chain, means the existing
        // route has somewhere to go without a merchant.
        if (IRON_INGOT.equals(demand.materialKey())) {
            if (WorkDemandPolicy.rawIronDeficit(backpack, mainHand, offHand, cfg) <= 0) {
                // No outstanding raw-iron need means the chain is already satisfied upstream:
                // either enough raw iron is held to smelt, or no iron consumer is active.
                return true;
            }
            return false;
        }

        // Charcoal: the torch chain is smelt-driven, so an absent plan is a genuine dead end.
        if (CHARCOAL.equals(demand.materialKey())) {
            return false;
        }

        // Anything else is outside this producer's knowledge. Conservative default.
        return true;
    }

    private static boolean producesDemanded(
            FurnacePolicy.SmeltPlan plan, WorkDemandPolicy.MaterialDemand demand) {
        ItemStack output = plan.output();
        return !output.isEmpty()
                && demand.materialKey().equals(BuiltInRegistries.ITEM.getKey(output.getItem()));
    }
}
