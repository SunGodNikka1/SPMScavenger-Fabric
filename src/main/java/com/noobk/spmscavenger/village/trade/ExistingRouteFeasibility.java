package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * V2-E-R2 — can gather / smelt / craft satisfy this demand, and <b>do we actually know?</b>
 *
 * <h2>Two failures, one class</h2>
 *
 * R1 replaced a hardcoded lie with a producer that derived the wrong fact. For iron it returned
 * <i>infeasible</i> whenever {@code rawIronDeficit > 0} — but {@code GatherIntentPolicy} adds
 * {@code RAW_IRON} to its gather set on exactly that condition. The two read the same number and drew
 * opposite conclusions:
 *
 * <pre>
 * rawIronDeficit &gt; 0
 *   GatherIntentPolicy      -> "the gather route wants raw iron"   (route ALIVE)
 *   ExistingRouteFeasibility -> "the existing route is infeasible"  (route DEAD)  &lt;- wrong
 * </pre>
 *
 * A missing precursor is what gathering is <em>for</em>. It never proves gathering is impossible, and
 * the class had already written the correct rule — <i>anything that cannot be positively ruled out
 * counts as feasible</i> — while its iron branch violated it.
 *
 * <h2>Unknown is not infeasible</h2>
 *
 * So the answer is tri-state, and <b>only positive evidence produces {@link ExistingRouteStatus#INFEASIBLE}</b>:
 *
 * <pre>
 * FEASIBLE    the route demonstrably has somewhere to go now
 * INFEASIBLE  positively proven dead - evidence, never absence
 * UNKNOWN     we cannot tell        -> treated exactly like FEASIBLE by the caller
 * </pre>
 *
 * {@link #tradeMayDisplace} is the only consumer, and it admits trade on {@code INFEASIBLE} alone.
 *
 * <h2>What can currently prove INFEASIBLE, and what cannot</h2>
 *
 * One signal, and it is deliberately narrow: <b>the mob cannot mine the precursor at all</b>. Iron
 * ore needs a stone-tier pickaxe; a mob whose best pick is wood or nothing has a gather route that is
 * dead as a matter of game rules, not of circumstance. That is evidence.
 *
 * <p>Everything else is {@code UNKNOWN}: ore may be twenty blocks away or absent, a furnace may be
 * reachable or not, a craft path may exist that this class does not model. <b>Absence of a plan is
 * not proof of impossibility</b>, and this class will not pretend otherwise.
 *
 * <p><b>Consequence, stated plainly: INFEASIBLE currently has no reachable producer, so TRADE does
 * not fire at all.</b> The tool-tier guard below is semantically right and mutually exclusive with
 * the only demand that reaches it ({@code activeIronToolRecipe} already requires a stone-tier pick),
 * so it can never fire for iron. That is a real gap, not a pessimistic default: The right producer of {@code INFEASIBLE} is the gather/smelt route after its own
 * bounded search fails — evidence it already has and this class does not. Reimplementing target
 * discovery here to manufacture the answer would rebuild gather inside the trade goal, which is worse
 * than a quiet feature. {@link #reportRouteExhausted} is the seam for that evidence when a work goal
 * is ready to publish it; it has <b>no production callers yet</b>, and that is a known gap rather
 * than an oversight.
 */
public final class ExistingRouteFeasibility {

    /** What we know about the gather/smelt/craft route for one demand. */
    public enum ExistingRouteStatus {
        FEASIBLE,
        INFEASIBLE,
        UNKNOWN;

        /** {@code UNKNOWN} behaves as {@code FEASIBLE}: trade never displaces on ignorance. */
        public boolean permitsTradeDisplacement() {
            return this == INFEASIBLE;
        }
    }

    private static final ResourceLocation IRON_INGOT = BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT);
    private static final ResourceLocation CHARCOAL = BuiltInRegistries.ITEM.getKey(Items.CHARCOAL);

    private ExistingRouteFeasibility() {
    }

    /** The one question the executor asks. Trade may proceed only on proven infeasibility. */
    public static boolean tradeMayDisplace(
            ServerLevel level, java.util.UUID mobId, WorkDemandPolicy.MaterialDemand demand,
            Container backpack, ItemStack mainHand, ItemStack offHand, ScavengerConfig cfg) {
        return status(level, mobId, demand, backpack, mainHand, offHand, cfg)
                .permitsTradeDisplacement();
    }

    public static ExistingRouteStatus status(
            ServerLevel level, WorkDemandPolicy.MaterialDemand demand, Container backpack,
            ItemStack mainHand, ItemStack offHand, ScavengerConfig cfg) {
        return status(level, null, demand, backpack, mainHand, offHand, cfg);
    }

    /**
     * @param mobId the owner, so published route-exhaustion evidence can be consulted; {@code null}
     *     skips that source entirely
     */
    public static ExistingRouteStatus status(
            ServerLevel level, java.util.UUID mobId, WorkDemandPolicy.MaterialDemand demand,
            Container backpack, ItemStack mainHand, ItemStack offHand, ScavengerConfig cfg) {
        if (level == null || demand == null || backpack == null || cfg == null) {
            return ExistingRouteStatus.UNKNOWN;
        }

        // A live smelt plan producing exactly this material is the route, running.
        if (FurnacePolicy.plan(level, backpack, mainHand, offHand, cfg)
                .filter(plan -> producesDemanded(plan, demand))
                .isPresent()) {
            RouteExhaustionEvidence.clear(mobId);
            return ExistingRouteStatus.FEASIBLE;
        }

        ExistingRouteStatus gather = gatherStatus(demand, backpack, mainHand, offHand, cfg);
        if (gather == ExistingRouteStatus.FEASIBLE) {
            // R4: work progress / target rediscovery physically invalidates the memory of an empty
            // search. Without this the entry survives its full lifetime and keeps being re-consulted
            // after the world has already answered - and `reconcile` would silently rely on
            // precedence to hide a record that is now known to be false.
            RouteExhaustionEvidence.clear(mobId);
            return ExistingRouteStatus.FEASIBLE;
        }
        return reconcile(
                gather, RouteExhaustionEvidence.exhaustedFor(mobId, demand, level.getGameTime()));
    }

    /**
     * Present beats past.
     *
     * <p>A live gather route outranks any memory of a failed search, so "failed once" never becomes
     * temporary trade ownership — which is what preserves V2-C's convergence. Exhaustion evidence is
     * consulted only when nothing positive can be said.
     *
     * <p>Extracted so the precedence itself is testable: a control that inverts it must fail
     * something, and while this logic lived inline behind a {@code ServerLevel} it could not.
     */
    static ExistingRouteStatus reconcile(ExistingRouteStatus gather, boolean routeExhausted) {
        if (gather == ExistingRouteStatus.FEASIBLE) {
            return ExistingRouteStatus.FEASIBLE;
        }
        if (gather == ExistingRouteStatus.INFEASIBLE) {
            return ExistingRouteStatus.INFEASIBLE;
        }
        return routeExhausted ? ExistingRouteStatus.INFEASIBLE : ExistingRouteStatus.UNKNOWN;
    }

    /**
     * The level-free half: everything decidable from inventory and game rules.
     *
     * <p>Split out so the iron and charcoal branches can be tested <b>behaviourally</b>. The R1 test
     * only asserted that the final fall-through was conservative, which proved nothing about the
     * explicit branches — and the iron branch was the one that was wrong.
     */
    static ExistingRouteStatus gatherStatus(
            WorkDemandPolicy.MaterialDemand demand, Container backpack,
            ItemStack mainHand, ItemStack offHand, ScavengerConfig cfg) {
        if (demand == null || backpack == null || cfg == null) {
            return ExistingRouteStatus.UNKNOWN;
        }
        if (IRON_INGOT.equals(demand.materialKey())) {
            // R4: the one positive producer this branch was missing. Holding the smelt INPUT is
            // present-tense evidence that the route has somewhere to go; the capable pickaxe below
            // is not, and conflating the two is what made exhaustion unusable.
            if (ScavengerCrafting.count(backpack, Items.RAW_IRON) > 0) {
                return ExistingRouteStatus.FEASIBLE;
            }
            // Wanting raw iron is the gather route being ALIVE, not dead - the R1 inversion.
            boolean gatherWantsRawIron =
                    WorkDemandPolicy.rawIronDeficit(backpack, mainHand, offHand, cfg) > 0;
            if (gatherWantsRawIron) {
                // ...unless the mob positively cannot mine it. Iron ore requires stone tier, so a
                // wooden or bare-handed mob would have a route dead by game rule - evidence rather
                // than absence.
                //
                // UNREACHABLE FOR IRON TODAY, and deliberately kept anyway. `activeIronToolRecipe`
                // only produces an iron demand when the pick is already >= STONE, so this branch and
                // the demand that reaches it are mutually exclusive. It is retained because it is the
                // semantically correct guard and a future consumer with no tier prerequisite would
                // need it - but it must not be mistaken for a live producer of INFEASIBLE.
                ToolTier pick = ToolTierPolicy.tierOfPick(backpack, mainHand, offHand);
                // R4: a capable pickaxe proves CAPABILITY, not current route feasibility. Treating
                // it as positive evidence made it outrank a completed-and-empty gather search, so
                // exhaustion evidence could never authorize anything: "I own a pick" beat "I looked
                // and there is no iron". Capability + an outstanding need is UNKNOWN, which is
                // exactly the state a finished search is entitled to resolve.
                return pick.compareTo(ToolTier.STONE) >= 0
                        ? ExistingRouteStatus.UNKNOWN
                        : ExistingRouteStatus.INFEASIBLE;
            }
            // No outstanding raw-iron need and no smelt plan: the chain is satisfied upstream or has
            // no active consumer. Either way we cannot prove the route is dead.
            return ExistingRouteStatus.UNKNOWN;
        }

        if (CHARCOAL.equals(demand.materialKey())) {
            // needsCharcoal() requires surplus logs, and gathering can still go and get logs.
            // An absent smelt plan proves nothing about the broader route.
            return ExistingRouteStatus.UNKNOWN;
        }

        return ExistingRouteStatus.UNKNOWN;
    }

    /**
     * @deprecated R2's seam did nothing — it returned a value that died at the call site while
     *     {@link #status} never consulted it. Publish through
     *     {@link RouteExhaustionEvidence#publish} instead, which binds the evidence to a mob, a
     *     consumer, a material and an expiry.
     */
    @Deprecated
    public static ExistingRouteStatus reportRouteExhausted() {
        return ExistingRouteStatus.INFEASIBLE;
    }

    private static boolean producesDemanded(
            FurnacePolicy.SmeltPlan plan, WorkDemandPolicy.MaterialDemand demand) {
        ItemStack output = plan.output();
        return !output.isEmpty()
                && demand.materialKey().equals(BuiltInRegistries.ITEM.getKey(output.getItem()));
    }
}
