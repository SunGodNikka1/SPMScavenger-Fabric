package com.noobk.spmscavenger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One bounded arbitration point for work that currently needs furnace output (D-FSM-013).
 * Payloads are derived facts, never stored state and never schedulers of their own.
 */
public final class WorkDemandPolicy {

    public enum WorkType { SMELT_BATCH }
    public enum DemandClass { SURVIVAL, PROGRESSION }

    public record MaterialDemand(
            ResourceLocation materialKey, int derivedDeficit, ResourceLocation consumerKey) {
        public MaterialDemand {
            if (derivedDeficit <= 0) throw new IllegalArgumentException("deficit must be positive");
        }
    }

    public record WorkDemand(
            WorkType workType,
            DemandClass demandClass,
            int derivedUtility,
            String reason,
            MaterialDemand payload) {
    }

    private WorkDemandPolicy() {
    }

    /** Select exactly one live demand. Stable ordering is class, utility, then reason. */
    public static Optional<WorkDemand> select(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        List<WorkDemand> candidates = new java.util.ArrayList<>(2);
        charcoalDemand(backpack, cfg).ifPresent(candidates::add);
        ironToolDemand(backpack, mainHand, cfg).ifPresent(candidates::add);
        return candidates.stream().max(Comparator
                .comparingInt((WorkDemand d) -> classWeight(d.demandClass()))
                .thenComparingInt(WorkDemand::derivedUtility)
                .thenComparing(WorkDemand::reason, Comparator.reverseOrder()));
    }

    private static Optional<WorkDemand> charcoalDemand(Container backpack, ScavengerConfig cfg) {
        if (!FurnacePolicy.needsCharcoal(backpack, cfg)) return Optional.empty();
        int deficit = Math.max(1, cfg.torchStockTarget - ScavengerCrafting.count(backpack, Items.TORCH));
        return Optional.of(new WorkDemand(
                WorkType.SMELT_BATCH,
                DemandClass.SURVIVAL,
                100,
                "torch_fuel",
                new MaterialDemand(
                        BuiltInRegistries.ITEM.getKey(Items.CHARCOAL),
                        deficit,
                        ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain"))));
    }

    /**
     * Highest Y at which diamond can generate in vanilla worldgen. Above this a mob has no diamond
     * prospect at all, so it must not carry diamond demand.
     */
    public static final int DIAMOND_GENERATION_CEILING_Y = 16;

    /**
     * How many diamonds the mob still needs for its active diamond-tool consumer.
     *
     * <p>Consumer-pulled like {@link #rawIronDeficit}, with one extra gate: <b>plausibility</b>.
     * Diamond generates below Y≈16, this addon has no descent behaviour, and the ore gate requires
     * air exposure — so a surface mob can never satisfy diamond demand. Carrying it anyway would
     * make {@code wantsMore()} permanently true and destroy the "nothing to gather" resting state,
     * leaving every mob scanning forever for a block that cannot be in range (D-TTU-024).
     *
     * <p>Gating on the mob's own altitude keeps Phase 3 honest: a mob that wanders into a ravine or
     * deep cave opportunistically mines diamond, and a mob on the surface simply does not want any.
     * <b>Seeking</b> diamond is deliberately out of scope — it belongs to a future Mining
     * Intelligence RFC, not here.
     */
    public static int diamondDeficit(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg, int mobBlockY) {
        if (mobBlockY > DIAMOND_GENERATION_CEILING_Y) {
            return 0;
        }
        Optional<ScavengerCrafting.ConsumerRecipeSpec> specOpt =
                ScavengerCrafting.activeDiamondToolRecipe(backpack, mainHand, cfg);
        if (specOpt.isEmpty()) {
            return 0;
        }
        ScavengerCrafting.ConsumerRecipeSpec spec = specOpt.get();
        int required = spec.requiredCount(Items.DIAMOND);
        return Math.max(0, required - ScavengerCrafting.count(backpack, Items.DIAMOND));
    }

    /**
     * How much raw iron the mob still needs to mine to satisfy the active iron-tool consumer.
     *
     * <p>Derived from the same {@code ConsumerRecipeSpec} that drives {@link #ironToolDemand}, so
     * ore gathering is <b>pulled by a consumer</b> rather than pushed by "ore exists". Raw iron
     * already carried counts against the deficit: a mob holding enough to smelt does not keep
     * mining. Returns {@code 0} when no iron tool is currently wanted, which is what keeps TT-2c
     * dormant while the iron tier is unreachable.
     */
    public static int rawIronDeficit(Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        Optional<WorkDemand> demand = ironToolDemand(backpack, mainHand, cfg);
        if (demand.isEmpty()) {
            return 0;
        }
        int ingotDeficit = demand.get().payload().derivedDeficit();
        int rawHeld = ScavengerCrafting.count(backpack, Items.RAW_IRON);
        return Math.max(0, ingotDeficit - rawHeld);
    }

    private static Optional<WorkDemand> ironToolDemand(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        Optional<ScavengerCrafting.ConsumerRecipeSpec> specOpt =
                ScavengerCrafting.activeIronToolRecipe(backpack, mainHand, cfg);
        if (specOpt.isEmpty()) return Optional.empty();
        ScavengerCrafting.ConsumerRecipeSpec spec = specOpt.get();
        int required = spec.requiredCount(Items.IRON_INGOT);
        int deficit = Math.max(0, required - ScavengerCrafting.count(backpack, Items.IRON_INGOT));
        if (deficit == 0) return Optional.empty();
        return Optional.of(new WorkDemand(
                WorkType.SMELT_BATCH,
                DemandClass.PROGRESSION,
                100,
                "iron_tool_frontier",
                new MaterialDemand(
                        BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), deficit, spec.consumerKey())));
    }

    private static int classWeight(DemandClass demandClass) {
        return demandClass == DemandClass.SURVIVAL ? 2 : 1;
    }
}
