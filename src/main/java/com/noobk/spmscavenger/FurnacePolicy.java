package com.noobk.spmscavenger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure smelting decisions for scavenger furnace jobs (D-FSM-001 / D-FSM-006).
 *
 * <p>Demand, log reserves, and fuel choice operate on a {@link Container} only. Recipe identity and
 * cooking time come from an injected {@link RecipeLookup} so unit tests do not need a live
 * {@link ServerLevel}; production code uses {@link #liveRecipes(ServerLevel)}.
 */
public final class FurnacePolicy {

    /** Vanilla charcoal / ore cook time; used only by test stubs, never as a production table. */
    public static final int VANILLA_SMELT_TICKS = 200;

    public enum SmeltDemand {
        NONE,
        CHARCOAL,
        IRON
    }

    /**
     * One planned furnace job. Stacks are copies sized for a single insert batch — callers must not
     * mutate shared backpack references.
     */
    public record SmeltPlan(
            ResourceLocation recipeId,
            ItemStack input,
            ItemStack output,
            int cookingTicks,
            int batchSize,
            ItemStack fuelChosen,
            int fuelBurnTicks) {
    }

    /** Resolved live (or test-stub) smelting recipe for one input item. */
    public record ResolvedSmeltingRecipe(
            ResourceLocation id, ItemStack input, ItemStack output, int cookingTicks) {
    }

    @FunctionalInterface
    public interface RecipeLookup {
        Optional<ResolvedSmeltingRecipe> find(ItemStack input);
    }

    @FunctionalInterface
    public interface FuelLookup {
        /** Burn duration in ticks, or {@code 0} if the stack is not furnace fuel. */
        int burnTicks(ItemStack stack);
    }

    private FurnacePolicy() {
    }

    /** Executor mapping for the one demand selected by {@link WorkDemandPolicy}. */
    public static SmeltDemand demand(Container backpack, ScavengerConfig cfg) {
        return demand(backpack, ItemStack.EMPTY, cfg);
    }

    public static SmeltDemand demand(Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        return demand(backpack, mainHand, ItemStack.EMPTY, cfg);
    }

    public static SmeltDemand demand(
            Container backpack, ItemStack mainHand, ItemStack offHand, ScavengerConfig cfg) {
        return WorkDemandPolicy.select(backpack, mainHand, offHand, cfg)
                .map(d -> d.payload().materialKey().equals(BuiltInRegistries.ITEM.getKey(Items.CHARCOAL))
                        ? SmeltDemand.CHARCOAL
                        : SmeltDemand.IRON)
                .orElse(SmeltDemand.NONE);
    }

    /**
     * Logs that must remain for planks/sticks/table/campfire. Charcoal may only consume surplus
     * above this floor (D-FSM-003).
     */
    public static int logReserveForCraftChain(Container backpack, ScavengerConfig cfg) {
        int planks = countPlanks(backpack);
        int sticks = ScavengerCrafting.count(backpack, Items.STICK);
        int reserve = 0;

        if (torchChainNeedsMoreFuel(backpack, cfg)) {
            // Need at least one stick for the next torch craft once charcoal exists.
            if (sticks < 1 && planks < ScavengerCrafting.PLANKS_PER_STICK_CRAFT) {
                reserve = Math.max(reserve, 1);
            }
        }

        if (cfg.craftTools && ScavengerCrafting.count(backpack, Items.CRAFTING_TABLE) == 0) {
            if (planks < ScavengerCrafting.PLANKS_PER_TABLE) {
                int plankDeficit = ScavengerCrafting.PLANKS_PER_TABLE - planks;
                int logsForTable = (plankDeficit + ScavengerCrafting.PLANKS_PER_LOG - 1)
                        / ScavengerCrafting.PLANKS_PER_LOG;
                reserve = Math.max(reserve, logsForTable);
            }
        }

        if (cfg.campfire
                && ScavengerCrafting.count(backpack, Items.CAMPFIRE) == 0
                && ScavengerCrafting.count(backpack, Items.COAL)
                                + ScavengerCrafting.count(backpack, Items.CHARCOAL)
                        >= 1) {
            // Campfire still wants three logs once fuel exists; keep that floor.
            reserve = Math.max(reserve, ScavengerCrafting.LOGS_PER_CAMPFIRE);
        }

        return reserve;
    }

    public static int surplusLogs(Container backpack, ScavengerConfig cfg) {
        return Math.max(0, countLogs(backpack) - logReserveForCraftChain(backpack, cfg));
    }

    /**
     * Build a single-batch smelt plan for {@code demand}, or empty when ingredients/fuel/reserves
     * make the job impossible.
     */
    public static Optional<SmeltPlan> plan(
            Container backpack,
            ScavengerConfig cfg,
            SmeltDemand demand,
            RecipeLookup recipes,
            FuelLookup fuels) {
        if (demand == SmeltDemand.NONE) {
            return Optional.empty();
        }
        Optional<ItemStack> inputOpt = selectInput(backpack, cfg, demand);
        if (inputOpt.isEmpty()) {
            return Optional.empty();
        }
        ItemStack input = inputOpt.get();
        Optional<ResolvedSmeltingRecipe> recipeOpt = recipes.find(input);
        if (recipeOpt.isEmpty()) {
            return Optional.empty();
        }
        ResolvedSmeltingRecipe recipe = recipeOpt.get();
        int cookingTicks = Math.max(1, recipe.cookingTicks());
        int batchSize = 1;
        int fuelNeeded = cookingTicks * batchSize;

        Optional<ItemStack> fuelOpt = chooseFuel(backpack, cfg, demand, input, fuelNeeded, fuels);
        if (fuelOpt.isEmpty()) {
            return Optional.empty();
        }
        ItemStack fuel = fuelOpt.get();
        int burn = fuels.burnTicks(fuel);
        if (burn < fuelNeeded) {
            return Optional.empty();
        }

        return Optional.of(new SmeltPlan(
                recipe.id(),
                input.copyWithCount(1),
                recipe.output().copy(),
                cookingTicks,
                batchSize,
                fuel.copyWithCount(1),
                burn));
    }

    public static Optional<SmeltPlan> plan(
            Container backpack, ScavengerConfig cfg, RecipeLookup recipes, FuelLookup fuels) {
        return plan(backpack, ItemStack.EMPTY, cfg, recipes, fuels);
    }

    public static Optional<SmeltPlan> plan(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg,
            RecipeLookup recipes, FuelLookup fuels) {
        return plan(backpack, mainHand, ItemStack.EMPTY, cfg, recipes, fuels);
    }

    public static Optional<SmeltPlan> plan(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg,
            RecipeLookup recipes,
            FuelLookup fuels) {
        return plan(backpack, cfg, demand(backpack, mainHand, offHand, cfg), recipes, fuels);
    }

    /** Production entry: live {@link RecipeManager} + furnace fuel map. */
    public static Optional<SmeltPlan> plan(ServerLevel level, Container backpack, ScavengerConfig cfg) {
        return plan(level, backpack, ItemStack.EMPTY, cfg);
    }

    public static Optional<SmeltPlan> plan(
            ServerLevel level, Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        return plan(level, backpack, mainHand, ItemStack.EMPTY, cfg);
    }

    public static Optional<SmeltPlan> plan(
            ServerLevel level,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg) {
        return plan(backpack, mainHand, offHand, cfg, liveRecipes(level), liveFuels());
    }

    /**
     * Gather should yield only when a smelt job is executable right now — not merely when
     * {@link #demand} is flagged. Otherwise a mob with two reserved logs stops chopping while it
     * still lacks fuel for the charcoal job and every goal goes idle.
     */
    public static boolean shouldYieldGatherToSmelt(
            ServerLevel level, Container backpack, ScavengerConfig cfg) {
        return shouldYieldGatherToSmelt(level, backpack, ItemStack.EMPTY, cfg);
    }

    public static boolean shouldYieldGatherToSmelt(
            ServerLevel level, Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        return shouldYieldGatherToSmelt(level, backpack, mainHand, ItemStack.EMPTY, cfg);
    }

    public static boolean shouldYieldGatherToSmelt(
            ServerLevel level,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg) {
        if (!cfg.smeltEnabled || demand(backpack, mainHand, offHand, cfg) == SmeltDemand.NONE) {
            return false;
        }
        return plan(level, backpack, mainHand, offHand, cfg).isPresent();
    }

    public static RecipeLookup liveRecipes(ServerLevel level) {
        return input -> {
            if (input.isEmpty()) {
                return Optional.empty();
            }
            SingleRecipeInput recipeInput = new SingleRecipeInput(input);
            Optional<RecipeHolder<SmeltingRecipe>> holder = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, recipeInput, level);
            if (holder.isEmpty()) {
                return Optional.empty();
            }
            RecipeHolder<SmeltingRecipe> recipeHolder = holder.get();
            SmeltingRecipe recipe = recipeHolder.value();
            ItemStack assembled = recipe.assemble(recipeInput, level.registryAccess());
            if (assembled.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSmeltingRecipe(
                    recipeHolder.id(),
                    input.copyWithCount(1),
                    assembled.copy(),
                    recipe.getCookingTime()));
        };
    }

    public static FuelLookup liveFuels() {
        return stack -> {
            if (stack.isEmpty() || !AbstractFurnaceBlockEntity.isFuel(stack)) {
                return 0;
            }
            Map<Item, Integer> map = AbstractFurnaceBlockEntity.getFuel();
            return map.getOrDefault(stack.getItem(), 0);
        };
    }

    /**
     * Smallest burn-time fuel that covers {@code fuelNeededTicks}, skipping craft-chain reserves
     * (D-FSM-006). Prefers non-log fuels before surplus logs.
     */
    public static Optional<ItemStack> chooseFuel(
            Container backpack,
            ScavengerConfig cfg,
            SmeltDemand demand,
            ItemStack reservedInput,
            int fuelNeededTicks,
            FuelLookup fuels) {
        int logReserve = logReserveForCraftChain(backpack, cfg);
        // Charcoal job consumes one surplus log as input — that log is not also available as fuel.
        int logsCommittedAsInput = 0;
        if (demand == SmeltDemand.CHARCOAL && isLog(reservedInput)) {
            logsCommittedAsInput = 1;
        }

        List<ItemStack> candidates = new ArrayList<>();
        int logsSeen = 0;
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int burn = fuels.burnTicks(stack);
            if (burn < fuelNeededTicks) {
                continue;
            }
            if (isLog(stack)) {
                int available = stack.getCount();
                // Walk log stacks in order; apply global reserve + input commitment.
                int previously = logsSeen;
                logsSeen += available;
                int usable = Math.max(0, logsSeen - logReserve - logsCommittedAsInput) - Math.max(0, previously - logReserve - logsCommittedAsInput);
                if (usable <= 0) {
                    continue;
                }
            }
            // Do not burn the last coal/charcoal when charcoal demand exists to unlock torches —
            // charcoal demand implies those counts are already zero, so this is belt-and-braces.
            if (demand == SmeltDemand.CHARCOAL
                    && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL))) {
                continue;
            }
            candidates.add(stack);
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingInt((ItemStack s) -> isLog(s) ? 1 : 0)
                        .thenComparingInt(fuels::burnTicks))
                .map(s -> s.copyWithCount(1))
                .findFirst();
    }

    static boolean needsCharcoal(Container backpack, ScavengerConfig cfg) {
        if (!torchChainNeedsMoreFuel(backpack, cfg)) {
            return false;
        }
        if (ScavengerCrafting.count(backpack, Items.COAL) > 0
                || ScavengerCrafting.count(backpack, Items.CHARCOAL) > 0) {
            return false;
        }
        return surplusLogs(backpack, cfg) >= 1;
    }

    private static boolean torchChainNeedsMoreFuel(Container backpack, ScavengerConfig cfg) {
        if (!cfg.placeTorches) {
            return false;
        }
        return ScavengerCrafting.count(backpack, Items.TORCH) < cfg.torchStockTarget;
    }

    private static Optional<ItemStack> selectInput(
            Container backpack, ScavengerConfig cfg, SmeltDemand demand) {
        return switch (demand) {
            case CHARCOAL -> findSurplusLog(backpack, cfg);
            case IRON -> findIronInput(backpack);
            case NONE -> Optional.empty();
        };
    }

    private static Optional<ItemStack> findSurplusLog(Container backpack, ScavengerConfig cfg) {
        int reserve = logReserveForCraftChain(backpack, cfg);
        int seen = 0;
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (!isLog(stack)) {
                continue;
            }
            int before = seen;
            seen += stack.getCount();
            int usable = Math.max(0, seen - reserve) - Math.max(0, before - reserve);
            if (usable > 0) {
                return Optional.of(stack.copyWithCount(1));
            }
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> findIronInput(Container backpack) {
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.RAW_IRON)
                    || stack.is(Items.IRON_ORE)
                    || stack.is(Items.DEEPSLATE_IRON_ORE)) {
                return Optional.of(stack.copyWithCount(1));
            }
        }
        return Optional.empty();
    }

    private static int countLogs(Container backpack) {
        int n = 0;
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (isLog(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static int countPlanks(Container backpack) {
        int n = 0;
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (isPlank(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /**
     * Log detection for reserves/fuel. Prefers {@link ItemTags#LOGS}; when tags are unbound
     * (Bootstrap unit tests without datapacks), falls back to vanilla item id suffixes.
     */
    static boolean isLog(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.LOGS)) {
            return true;
        }
        return matchesVanillaPathSuffix(stack, "_log", "_wood");
    }

    static boolean isPlank(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.PLANKS)) {
            return true;
        }
        return matchesVanillaPathSuffix(stack, "_planks");
    }

    private static boolean matchesVanillaPathSuffix(ItemStack stack, String... suffixes) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
