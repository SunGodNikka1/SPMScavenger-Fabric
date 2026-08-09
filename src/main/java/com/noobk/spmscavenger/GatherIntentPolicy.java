package com.noobk.spmscavenger;

import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Pure, immutable snapshot of why the gather executor should scan right now (MI-1).
 *
 * <p>This policy owns no navigation, block discovery, or persistence. It consolidates existing
 * consumer demand so {@code GatherResourcesGoal} does not independently reinterpret the same
 * backpack several times while scanning a block volume.
 */
public final class GatherIntentPolicy {

    public enum Resource { LOGS, COAL, COBBLESTONE, RAW_IRON, DIAMOND }

    public record GatherIntent(
            EnumSet<Resource> resources, ScavengerCrafting.Step readyCraftStep) {
        public GatherIntent {
            resources = resources.clone();
        }

        @Override
        public EnumSet<Resource> resources() {
            return resources.clone();
        }

        public boolean wants(Resource resource) {
            return resources.contains(resource);
        }

        public boolean hasDemand() {
            return !resources.isEmpty();
        }

        /** Crafting is the cheaper next action when an existing recipe can commit immediately. */
        public boolean shouldGather() {
            return hasDemand() && readyCraftStep == ScavengerCrafting.Step.NOTHING;
        }
    }

    private GatherIntentPolicy() {
    }

    public static GatherIntent evaluate(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg, int mobBlockY) {
        EnumSet<Resource> resources = EnumSet.noneOf(Resource.class);

        boolean wantsTorches =
                ScavengerCrafting.count(backpack, Items.TORCH) < cfg.torchStockTarget;
        boolean wantsPickUpgrade =
                cfg.craftTools && ToolTierPolicy.needsPickUpgrade(backpack, mainHand, cfg);
        boolean wantsAxeUpgrade =
                cfg.craftTools && ToolTierPolicy.needsAxeUpgrade(backpack, mainHand, cfg);

        if (wantsTorches || wantsPickUpgrade || wantsAxeUpgrade) {
            resources.add(Resource.LOGS);
        }
        if (wantsTorches) {
            resources.add(Resource.COAL);
        }
        if (cfg.craftTools && ToolTierPolicy.cobbleBelowTarget(backpack, mainHand, cfg)) {
            resources.add(Resource.COBBLESTONE);
        }
        if (cfg.craftTools && WorkDemandPolicy.rawIronDeficit(backpack, mainHand, cfg) > 0) {
            resources.add(Resource.RAW_IRON);
        }
        if (cfg.craftTools
                && WorkDemandPolicy.diamondDeficit(backpack, mainHand, cfg, mobBlockY) > 0) {
            resources.add(Resource.DIAMOND);
        }

        // MI-4: wealth is strictly additive. Consumer deficits above have already decided what the
        // mob *needs*; this only adds what it would *like*. At greed=0 or wealthLevel=0 every wealth
        // term is zero and this loop adds nothing, which is the exact-consumer parity guarantee.
        for (Resource resource : Resource.values()) {
            if (!resources.contains(resource) && wealthWants(backpack, resource, cfg)) {
                resources.add(resource);
            }
        }

        return new GatherIntent(resources, ScavengerCrafting.nextStep(backpack, cfg, mainHand));
    }

    /**
     * Whether holding one more of this resource is worth a detour purely for wealth.
     *
     * <p>Uses {@link ResourceWealthPolicy#evaluateWealth} with a zero acquisition cost — this answers
     * "would the mob want this at all", not "is that particular block worth walking to". Distance is
     * the candidate scorer's job, and keeping it out of here stops wealth from silently becoming a
     * second targeting system (Gate SPM-2).
     */
    private static boolean wealthWants(
            Container backpack, Resource resource, ScavengerConfig cfg) {
        float greed = (float) Mth.clamp(cfg.greed, 0.0, 1.0);
        float wealthLevel = (float) Math.max(0.0, cfg.wealthLevel);
        if (greed <= 0.0F || wealthLevel <= 0.0F) {
            return false;
        }
        ResourceWealthPolicy.ResourceCategory category = categoryOf(resource);
        if (category == null) {
            return false;
        }
        int held = ScavengerCrafting.count(backpack, stockItem(resource));
        ResourceWealthPolicy.WealthUtility utility = ResourceWealthPolicy.evaluateWealth(
                new ResourceWealthPolicy.ResourceWealthContext(category, held, greed, wealthLevel),
                0.0F);
        return utility.netUtility() > 0.0F;
    }

    /** Gather resources map onto wealth categories; the two enums are deliberately separate. */
    private static ResourceWealthPolicy.ResourceCategory categoryOf(Resource resource) {
        return switch (resource) {
            case LOGS -> ResourceWealthPolicy.ResourceCategory.LOGS;
            case COAL -> ResourceWealthPolicy.ResourceCategory.COAL;
            case COBBLESTONE -> ResourceWealthPolicy.ResourceCategory.COBBLESTONE;
            case RAW_IRON -> ResourceWealthPolicy.ResourceCategory.IRON;
            case DIAMOND -> ResourceWealthPolicy.ResourceCategory.DIAMOND;
        };
    }

    /** The stack a mob actually accumulates for each gather resource. */
    private static Item stockItem(Resource resource) {
        return switch (resource) {
            case LOGS -> Items.OAK_LOG;
            case COAL -> Items.COAL;
            case COBBLESTONE -> Items.COBBLESTONE;
            case RAW_IRON -> Items.RAW_IRON;
            case DIAMOND -> Items.DIAMOND;
        };
    }
}
