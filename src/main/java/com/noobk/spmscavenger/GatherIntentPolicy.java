package com.noobk.spmscavenger;

import net.minecraft.util.Mth;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure, immutable snapshot of why the gather executor should scan right now (MI-1).
 *
 * <p>This policy owns no navigation, block discovery, or persistence. It consolidates existing
 * consumer demand so {@code GatherResourcesGoal} does not independently reinterpret the same
 * backpack several times while scanning a block volume.
 */
public final class GatherIntentPolicy {

    /** Normalized cost required before wealth alone may start a bounded world scan. */
    private static final float SCAN_ACTIVATION_COST = 0.25F;

    public enum Resource { LOGS, COAL, COBBLESTONE, RAW_IRON, DIAMOND }

    public record GatherIntent(
            EnumSet<Resource> requiredResources,
            Map<Resource, ResourceWealthPolicy.ResourceWealthContext> wealthContexts,
            ScavengerCrafting.Step readyCraftStep) {
        public GatherIntent {
            requiredResources = requiredResources.clone();
            wealthContexts = Map.copyOf(wealthContexts);
        }

        /** Compatibility constructor for consumer-only callers and focused policy tests. */
        public GatherIntent(EnumSet<Resource> resources, ScavengerCrafting.Step readyCraftStep) {
            this(resources, Map.of(), readyCraftStep);
        }

        public EnumSet<Resource> resources() {
            EnumSet<Resource> resources = requiredResources.clone();
            resources.addAll(wealthContexts.keySet());
            return resources;
        }

        public boolean wants(Resource resource) {
            return requiredResources.contains(resource) || wealthContexts.containsKey(resource);
        }

        /** Candidate-aware admission: needs are unconditional; wealth must pay its actual cost. */
        public boolean wants(Resource resource, float acquisitionCost) {
            if (requiredResources.contains(resource)) {
                return true;
            }
            ResourceWealthPolicy.ResourceWealthContext context = wealthContexts.get(resource);
            return context != null
                    && ResourceWealthPolicy.evaluateWealth(context, acquisitionCost).netUtility() > 0.0F;
        }

        public boolean hasDemand() {
            if (!requiredResources.isEmpty()) {
                return true;
            }
            return wealthContexts.values().stream().anyMatch(context ->
                    ResourceWealthPolicy.evaluateWealth(context, SCAN_ACTIVATION_COST)
                                    .netUtility()
                            > 0.0F);
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
        return evaluate(backpack, mainHand, cfg, mobBlockY, stack -> stack.is(ItemTags.LOGS));
    }

    static GatherIntent evaluate(
            Container backpack,
            ItemStack mainHand,
            ScavengerConfig cfg,
            int mobBlockY,
            Predicate<ItemStack> isLog) {
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

        Map<Resource, ResourceWealthPolicy.ResourceWealthContext> wealthContexts =
                new java.util.EnumMap<>(Resource.class);
        float greed = (float) Mth.clamp(cfg.greed, 0.0, 1.0);
        float wealthLevel = (float) Math.max(0.0, cfg.wealthLevel);
        if (greed > 0.0F && wealthLevel > 0.0F) {
            for (Resource resource : Resource.values()) {
                // Do not advertise ore that cannot plausibly generate at the mob's current height.
                if (resource == Resource.DIAMOND
                        && mobBlockY > WorkDemandPolicy.DIAMOND_GENERATION_CEILING_Y) {
                    continue;
                }
                wealthContexts.put(resource, new ResourceWealthPolicy.ResourceWealthContext(
                        categoryOf(resource), countResource(backpack, resource, isLog), greed, wealthLevel));
            }
        }

        return new GatherIntent(
                resources, wealthContexts, ScavengerCrafting.nextStep(backpack, cfg, mainHand));
    }

    /**
     * Whether holding one more of this resource is worth a detour purely for wealth.
     *
     * <p>Uses {@link ResourceWealthPolicy#evaluateWealth} with a zero acquisition cost — this answers
     * "would the mob want this at all", not "is that particular block worth walking to". Distance is
     * the candidate scorer's job, and keeping it out of here stops wealth from silently becoming a
     * second targeting system (Gate SPM-2).
     */
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

    static int countResource(
            Container backpack, Resource resource, Predicate<ItemStack> isLog) {
        int count = 0;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack stack = backpack.getItem(slot);
            boolean matches = switch (resource) {
                case LOGS -> isLog.test(stack);
                case COAL -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
                case COBBLESTONE -> stack.is(Items.COBBLESTONE);
                case RAW_IRON -> stack.is(Items.RAW_IRON)
                        || stack.is(Items.IRON_ORE)
                        || stack.is(Items.DEEPSLATE_IRON_ORE);
                case DIAMOND -> stack.is(Items.DIAMOND);
            };
            if (matches) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
