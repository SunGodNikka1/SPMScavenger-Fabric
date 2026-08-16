package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * V2-H0 / `D-VR-075` — the same appetite, expressed for a different acquisition route.
 *
 * <h2>The abstraction that stopped one layer short</h2>
 *
 * `D-VR-015` always intended parallel acquisition candidates for one {@code ConsumerRecipeSpec}. The
 * implementation instead treated the route-specific <b>ingredient</b> demand as if it were the
 * consumer, so trade could only ever look for the thing the mining route needed:
 *
 * <pre>
 * consumer  spmscavenger:iron_pickaxe_upgrade
 *
 * EXISTING_WORK   raw iron -&gt; smelt -&gt; iron_ingot x3 -&gt; craft -&gt; iron_pickaxe
 * TRADE           emeralds -&gt; toolsmith            -&gt;          iron_pickaxe
 * </pre>
 *
 * A vanilla supply probe over {@link net.minecraft.world.entity.npc.VillagerTrades} (283 of 286
 * listings) found that vanilla sells the finished {@code iron_pickaxe} but <b>never</b>
 * {@code iron_ingot}, and no {@code charcoal} or {@code coal} at all. So with the ingredient demand
 * as the only trade target, the registrar could never choose {@code TRADE} in an uncontaminated
 * vanilla world — correct machinery serving an empty market.
 *
 * <h2>No new appetite is invented</h2>
 *
 * The projection carries the <b>same {@code consumerKey}</b>. It never creates a desire; it restates
 * one the consumer already has in the units the market actually trades in. It exists only while that
 * consumer's recipe is live, and disappears with it.
 *
 * <h2>Direct material first — and it wins when it can act</h2>
 *
 * The original path is evaluated first and wins whenever it is <b>actionable</b>: already funded, or
 * carrying a SELL leg that fully closes its deficit. A direct quote the mob can never complete falls
 * through to an actionable finished-output purchase instead — R1/R2 corrected an earlier "direct
 * always wins" rule that let an unfundable ingredient quote suppress a reachable tool purchase.
 * Finished-output projection remains a <b>fallback</b>, not a replacement, which is why nothing here
 * knows what a Toolsmith is.
 *
 * <h2>What must NOT be projected</h2>
 *
 * {@link ExistingRouteFeasibility} and {@link RouteExhaustionEvidence} stay bound to the <b>source
 * material</b> demand. Their logic and their evidence describe the gather/smelt route for raw iron;
 * asking them whether crafting a finished pickaxe is infeasible would be a category error, and would
 * silently reinterpret every exhaustion record already published.
 */
public final class TradePurchaseProjection {

    private TradePurchaseProjection() {
    }

    /**
     * The finished output this consumer would accept instead of its ingredients.
     *
     * @param source the live route-specific demand, unchanged and still owned by
     *     {@code WorkDemandPolicy}
     * @param spec the consumer's recipe, resolved from the same inventory the demand came from
     * @return empty when there is no live consumer, when the recipe belongs to a different consumer,
     *     or when the source demand already names the output
     */
    public static Optional<WorkDemandPolicy.MaterialDemand> ontoOutput(
            WorkDemandPolicy.MaterialDemand source, ScavengerCrafting.ConsumerRecipeSpec spec) {
        if (source == null || spec == null || spec.output() == null) {
            return Optional.empty();
        }
        // Same appetite or nothing: a projection that changed consumer would be a fabricated desire.
        if (!spec.consumerKey().equals(source.consumerKey())) {
            return Optional.empty();
        }
        var outputKey = BuiltInRegistries.ITEM.getKey(spec.output());
        if (outputKey.equals(source.materialKey())) {
            // Already the output; projecting would produce the same demand twice.
            return Optional.empty();
        }
        // Deficit 1: one finished tool satisfies the consumer outright, whatever its ingredient
        // count was. Carrying the ingredient deficit here would ask for three pickaxes.
        return Optional.of(new WorkDemandPolicy.MaterialDemand(outputKey, 1, source.consumerKey()));
    }

    /**
     * Is {@code desiredOutput} something this consumer may currently be buying?
     *
     * <p>Two representations of one appetite (`D-VR-075`): the source material itself, and the
     * finished recipe output. Ownership tests that knew only the first killed every projected chain
     * on the next {@code liveDemand()} tick — {@code iron_pickaxe != iron_ingot} — while later
     * discovery quietly rebuilt one, so the mod looked functional while the hard lifetime, the
     * relationship credit and chain identity were all being reset underneath.
     */
    public static boolean isPurchaseTargetFor(
            WorkDemandPolicy.MaterialDemand source,
            ScavengerCrafting.ConsumerRecipeSpec spec,
            net.minecraft.resources.ResourceLocation desiredOutput) {
        if (source == null || desiredOutput == null) {
            return false;
        }
        if (desiredOutput.equals(source.materialKey())) {
            return true;
        }
        return ontoOutput(source, spec)
                .map(projected -> desiredOutput.equals(projected.materialKey()))
                .orElse(false);
    }

    /**
     * Does this chain still belong to the live consumer?
     *
     * <p>Same consumer <b>and</b> a currently valid purchase target. A recipe that stops being live
     * withdraws the projection in the same tick, so a projected chain becomes ownerless exactly when
     * its consumer stops wanting the finished tool — which is the lifetime {@code D-VR-075} claims
     * projection shares.
     */
    public static boolean stillOwns(
            TradeChainPlan chain,
            WorkDemandPolicy.MaterialDemand source,
            ScavengerCrafting.ConsumerRecipeSpec spec) {
        return chain != null
                && source != null
                && chain.consumerKey().equals(source.consumerKey())
                && isPurchaseTargetFor(source, spec, chain.desiredOutput());
    }

    /**
     * The active recipe owning this demand, if any.
     *
     * <p>Consults the existing frontier accessors rather than a table of its own, so a consumer that
     * stops being active stops being projectable in the same tick — no separate lifetime to leak.
     */
    public static Optional<ScavengerCrafting.ConsumerRecipeSpec> activeSpecFor(
            WorkDemandPolicy.MaterialDemand source,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg) {
        if (source == null || backpack == null || cfg == null) {
            return Optional.empty();
        }
        for (Optional<ScavengerCrafting.ConsumerRecipeSpec> candidate : java.util.List.of(
                ScavengerCrafting.activeIronToolRecipe(backpack, mainHand, offHand, cfg),
                ScavengerCrafting.activeDiamondToolRecipe(backpack, mainHand, offHand, cfg))) {
            if (candidate.isPresent()
                    && candidate.get().consumerKey().equals(source.consumerKey())) {
                return candidate;
            }
        }
        return Optional.empty();
    }
}
