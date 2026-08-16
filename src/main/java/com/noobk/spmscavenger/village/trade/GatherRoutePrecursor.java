package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * V2-E-R5 — which gathered resource a demand's <b>existing</b> route actually goes looking for.
 *
 * <h2>Why the publisher needs this at all</h2>
 *
 * {@code GatherResourcesGoal}'s scan serves a combined intent — logs, coal, cobble, raw iron,
 * diamond — while {@link RouteExhaustionEvidence} means something much narrower: <i>this consumer's
 * route for this material found nothing</i>. Publishing on a bare {@code findTarget() == null} would
 * conflate the two in both directions:
 *
 * <pre>
 * scan wanted logs only, found nothing   -> says NOTHING about iron
 * scan wanted iron, found a log instead  -> says NOTHING about iron either
 * </pre>
 *
 * The first would authorize trade from a search that never looked; the second is the mutual-exclusion
 * error in reverse — unrelated P3 work is not evidence about the selected demand's route.
 *
 * <h2>The mapping is a route model, not a recipe lookup</h2>
 *
 * An iron <i>ingot</i> is never gathered. Its existing route is <b>mine raw iron, then smelt</b>, so
 * the resource a completed search must have covered is {@code RAW_IRON}. Charcoal is the same shape
 * one step over: gather logs, then smelt.
 *
 * <p>This is deliberately the same route model {@link ExistingRouteFeasibility} encodes, and the two
 * must stay in agreement — one says "the route is alive", the other says "the route was searched and
 * is empty", and they are only meaningful together. A material absent here has <b>no modelled gather
 * route</b>, and the honest consequence is that nothing may publish exhaustion for it: absence of a
 * model is not evidence of absence in the world.
 */
public final class GatherRoutePrecursor {

    private static final ResourceLocation IRON_INGOT = BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT);
    private static final ResourceLocation CHARCOAL = BuiltInRegistries.ITEM.getKey(Items.CHARCOAL);

    private GatherRoutePrecursor() {
    }

    /**
     * The resource a bounded gather scan must have covered before it may speak for this demand.
     *
     * @return empty when this material has no modelled gather route — publish nothing
     */
    public static Optional<GatherIntentPolicy.Resource> of(WorkDemandPolicy.MaterialDemand demand) {
        if (demand == null) {
            return Optional.empty();
        }
        if (IRON_INGOT.equals(demand.materialKey())) {
            return Optional.of(GatherIntentPolicy.Resource.RAW_IRON);
        }
        if (CHARCOAL.equals(demand.materialKey())) {
            return Optional.of(GatherIntentPolicy.Resource.LOGS);
        }
        return Optional.empty();
    }

    /**
     * May a completed, empty scan speak for this demand?
     *
     * <p>Both halves are required and neither implies the other: the scan must have been <b>asked</b>
     * for the precursor (otherwise it never looked), and the demand must have a modelled route
     * (otherwise there is nothing to say). A scan that wanted logs and found none is silent about
     * iron no matter how thoroughly it ran.
     */
    public static boolean scanCovers(
            WorkDemandPolicy.MaterialDemand demand, GatherIntentPolicy.GatherIntent intent) {
        if (intent == null) {
            return false;
        }
        return of(demand).map(intent::wants).orElse(false);
    }
}
