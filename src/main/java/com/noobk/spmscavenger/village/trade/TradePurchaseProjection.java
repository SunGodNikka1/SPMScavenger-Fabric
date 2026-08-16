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
 * <h2>Direct material first, always</h2>
 *
 * The original path is preserved and tried first: if a datapack or another mod ever sells
 * {@code iron_ingot}, that purchase wins and no projection happens. Finished-output projection is a
 * <b>fallback</b>, not a replacement, which is why nothing here knows what a Toolsmith is.
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
