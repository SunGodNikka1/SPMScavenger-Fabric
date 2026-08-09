package com.noobk.spmscavenger;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Inventory crafting policy for the scavenger's torch and tool progression.
 *
 * <p>Pure and static, operating on a {@link Container} — no entity, no level, no server — so the
 * whole chain is reachable from a unit test. This mirrors how Social Player Mobs writes its own
 * policy classes, and it is deliberate: the interesting decision ("can I make a torch yet?") should
 * not be buried inside a goal's tick method.
 *
 * <h2>Why these three and no more</h2>
 *
 * <pre>
 *   1 log    -> 4 planks
 *   2 planks -> 4 sticks
 *   1 coal + 1 stick -> 4 torches
 * </pre>
 *
 * All three are 2x2 recipes, so a real player could craft them in the inventory grid without a
 * crafting table. That is the line this class will not cross: a mob conjuring a table-only recipe
 * out of its backpack is a different mod. Charcoal is accepted alongside coal because a scavenger
 * that has burned wood should not be blocked on ore it never found.
 */
public final class ScavengerCrafting {

    public static final int PLANKS_PER_LOG = 4;
    public static final int STICKS_PER_CRAFT = 4;
    public static final int PLANKS_PER_STICK_CRAFT = 2;
    public static final int TORCHES_PER_CRAFT = 4;

    private ScavengerCrafting() {
    }

    /** What the mob should do next, or {@link Step#NOTHING}. */
    public enum Step {
        NOTHING,
        LOGS_TO_PLANKS,
        PLANKS_TO_STICKS,
        MAKE_TORCHES,
        MAKE_TABLE,
        MAKE_PICKAXE,
        MAKE_AXE,
        MAKE_STONE_PICKAXE,
        MAKE_STONE_AXE,
        MAKE_IRON_PICKAXE,
        MAKE_IRON_AXE,
        MAKE_DIAMOND_PICKAXE,
        MAKE_DIAMOND_AXE,
        MAKE_CAMPFIRE,
        MAKE_FURNACE
    }

    /** Recipes that need a 3x3 grid, so the mob must stand at a crafting table. */
    public static boolean needsTable(Step step) {
        return step == Step.MAKE_PICKAXE
                || step == Step.MAKE_AXE
                || step == Step.MAKE_STONE_PICKAXE
                || step == Step.MAKE_STONE_AXE
                || step == Step.MAKE_IRON_PICKAXE
                || step == Step.MAKE_IRON_AXE
                || step == Step.MAKE_DIAMOND_PICKAXE
                || step == Step.MAKE_DIAMOND_AXE
                || step == Step.MAKE_CAMPFIRE
                || step == Step.MAKE_FURNACE;
    }

    public static final int PLANKS_PER_TABLE = 4;
    public static final int PLANKS_PER_TOOL = 3;
    public static final int COBBLE_PER_TOOL = 3;
    public static final int DIAMOND_PER_TOOL = 3;
    public static final int IRON_PER_TOOL = 3;
    public static final int STICKS_PER_TOOL = 2;
    public static final int LOGS_PER_CAMPFIRE = 3;
    public static final int STICKS_PER_CAMPFIRE = 3;
    public static final int COBBLE_PER_FURNACE = 8;

    /** Typed ingredient key; future consumers can use an exact item or a data-pack item tag. */
    public sealed interface IngredientKey permits ExactIngredient, TaggedIngredient {
        boolean matches(ItemStack stack);
    }

    public record ExactIngredient(Item item) implements IngredientKey {
        @Override public boolean matches(ItemStack stack) { return stack.is(item); }
    }

    public record TaggedIngredient(TagKey<Item> tag) implements IngredientKey {
        @Override public boolean matches(ItemStack stack) { return stack.is(tag); }
    }

    public record IngredientRequirement(IngredientKey key, int count) {
        public IngredientRequirement {
            if (count <= 0) throw new IllegalArgumentException("ingredient count must be positive");
        }
    }

    /** One source of truth shared by consumer demand and atomic craft application. */
    public record ConsumerRecipeSpec(
            ResourceLocation consumerKey,
            Step step,
            Item output,
            List<IngredientRequirement> ingredients,
            Item replacedItem) {
        public int requiredCount(Item item) {
            return ingredients.stream()
                    .filter(r -> r.key() instanceof ExactIngredient exact && exact.item() == item)
                    .mapToInt(IngredientRequirement::count)
                    .sum();
        }
    }

    private static IngredientRequirement exact(Item item, int count) {
        return new IngredientRequirement(new ExactIngredient(item), count);
    }

    public static final ConsumerRecipeSpec IRON_PICKAXE_RECIPE = new ConsumerRecipeSpec(
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade"),
            Step.MAKE_IRON_PICKAXE,
            Items.IRON_PICKAXE,
            List.of(exact(Items.IRON_INGOT, IRON_PER_TOOL), exact(Items.STICK, STICKS_PER_TOOL)),
            Items.STONE_PICKAXE);
    public static final ConsumerRecipeSpec IRON_AXE_RECIPE = new ConsumerRecipeSpec(
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_axe_upgrade"),
            Step.MAKE_IRON_AXE,
            Items.IRON_AXE,
            List.of(exact(Items.IRON_INGOT, IRON_PER_TOOL), exact(Items.STICK, STICKS_PER_TOOL)),
            Items.STONE_AXE);

    public static final ConsumerRecipeSpec DIAMOND_PICKAXE_RECIPE = new ConsumerRecipeSpec(
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "diamond_pickaxe_upgrade"),
            Step.MAKE_DIAMOND_PICKAXE,
            Items.DIAMOND_PICKAXE,
            List.of(exact(Items.DIAMOND, DIAMOND_PER_TOOL), exact(Items.STICK, STICKS_PER_TOOL)),
            Items.IRON_PICKAXE);
    public static final ConsumerRecipeSpec DIAMOND_AXE_RECIPE = new ConsumerRecipeSpec(
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "diamond_axe_upgrade"),
            Step.MAKE_DIAMOND_AXE,
            Items.DIAMOND_AXE,
            List.of(exact(Items.DIAMOND, DIAMOND_PER_TOOL), exact(Items.STICK, STICKS_PER_TOOL)),
            Items.IRON_AXE);

    /**
     * Single-frontier consumer: pickaxe first, then axe. Mirrors
     * {@link #activeIronToolRecipe}; diamond needs no smelt step, so the consumer pulls the ore
     * drop directly.
     */
    public static Optional<ConsumerRecipeSpec> activeDiamondToolRecipe(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        if (!cfg.craftTools) return Optional.empty();
        ToolTier pick = ToolTierPolicy.tierOfPick(backpack, mainHand);
        if (ToolTierPolicy.targetPickTier(cfg).compareTo(ToolTier.DIAMOND) >= 0
                && pick.compareTo(ToolTier.IRON) >= 0
                && pick.compareTo(ToolTier.DIAMOND) < 0) {
            return Optional.of(DIAMOND_PICKAXE_RECIPE);
        }
        ToolTier axe = ToolTierPolicy.tierOfAxe(backpack, mainHand);
        if (ToolTierPolicy.targetAxeTier(cfg).compareTo(ToolTier.DIAMOND) >= 0
                && axe.compareTo(ToolTier.IRON) >= 0
                && axe.compareTo(ToolTier.DIAMOND) < 0) {
            return Optional.of(DIAMOND_AXE_RECIPE);
        }
        return Optional.empty();
    }

    /** Single-frontier consumer: pickaxe first, then axe. */
    public static Optional<ConsumerRecipeSpec> activeIronToolRecipe(
            Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        if (!cfg.craftTools) return Optional.empty();
        ToolTier pick = ToolTierPolicy.tierOfPick(backpack, mainHand);
        if (ToolTierPolicy.targetPickTier(cfg).compareTo(ToolTier.IRON) >= 0
                && pick.compareTo(ToolTier.STONE) >= 0
                && pick.compareTo(ToolTier.IRON) < 0) {
            return Optional.of(IRON_PICKAXE_RECIPE);
        }
        ToolTier axe = ToolTierPolicy.tierOfAxe(backpack, mainHand);
        if (ToolTierPolicy.targetAxeTier(cfg).compareTo(ToolTier.IRON) >= 0
                && axe.compareTo(ToolTier.STONE) >= 0
                && axe.compareTo(ToolTier.IRON) < 0) {
            return Optional.of(IRON_AXE_RECIPE);
        }
        return Optional.empty();
    }

    public static int countMatching(Container container, IngredientKey key) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (key.matches(stack)) count += stack.getCount();
        }
        return count;
    }

    public static Step nextStep(Container backpack, ScavengerConfig cfg) {
        return nextStep(backpack, cfg, ItemStack.EMPTY);
    }

    public static Step nextStep(Container backpack, ScavengerConfig cfg, ItemStack mainHand) {
        return nextStep(
                backpack,
                ToolTierPolicy.needsPickUpgrade(backpack, mainHand, cfg),
                ToolTierPolicy.needsAxeUpgrade(backpack, mainHand, cfg),
                cfg,
                mainHand);
    }

    public static Step nextStep(Container backpack, boolean needPickaxe, boolean needAxe) {
        return nextStep(backpack, needPickaxe, needAxe, ScavengerConfig.get(), ItemStack.EMPTY);
    }

    /**
     * The next thing worth doing, in priority order.
     *
     * <p>Order matters more than the recipes do. Torches come first because they are the point;
     * <b>tools come before stockpiling</b>, because a mob with no pickaxe can never reach coal and
     * the whole chain stalls — that stall is exactly the v1.0–1.2 bug where mobs chopped wood
     * forever and produced nothing. Sticks are only whittled when something is actually waiting for
     * them, so a mob does not stand there carving indefinitely.
     */
    private static Step nextStep(
            Container backpack, boolean needPickaxe, boolean needAxe, ScavengerConfig cfg, ItemStack mainHand) {
        int fuel = count(backpack, Items.COAL) + count(backpack, Items.CHARCOAL);
        int sticks = count(backpack, Items.STICK);
        int planks = countTag(backpack, true);
        int logs = countTag(backpack, false);
        int cobble = count(backpack, Items.COBBLESTONE);

        if (fuel > 0 && sticks > 0) {
            return Step.MAKE_TORCHES;
        }
        if (needPickaxe) {
            Step step = towardPickUpgrade(backpack, mainHand, planks, sticks, logs, cobble, cfg);
            if (step != Step.NOTHING) {
                return step;
            }
        }
        if (needAxe) {
            Step step = towardAxeUpgrade(backpack, mainHand, planks, sticks, logs, cobble, cfg);
            if (step != Step.NOTHING) {
                return step;
            }
        }
        if (fuel > 0 && sticks == 0) {
            if (planks >= PLANKS_PER_STICK_CRAFT) {
                return Step.PLANKS_TO_STICKS;
            }
            if (logs > 0) {
                return Step.LOGS_TO_PLANKS;
            }
        }
        return Step.NOTHING;
    }

    /** Backwards-compatible overload: no tools wanted. */
    public static Step nextStep(Container backpack) {
        return nextStep(backpack, false, false);
    }

    /** Either the wooden tool itself, or the one intermediate craft still missing for it. */
    private static Step towardTool(int planks, int sticks, int logs, Step tool) {
        if (planks >= PLANKS_PER_TOOL && sticks >= STICKS_PER_TOOL) {
            return tool;
        }
        if (sticks < STICKS_PER_TOOL && planks >= PLANKS_PER_STICK_CRAFT + PLANKS_PER_TOOL) {
            return Step.PLANKS_TO_STICKS;
        }
        if (logs > 0) {
            return Step.LOGS_TO_PLANKS;
        }
        if (sticks < STICKS_PER_TOOL && planks >= PLANKS_PER_STICK_CRAFT) {
            return Step.PLANKS_TO_STICKS;
        }
        return Step.NOTHING;
    }

    private static Step towardPickUpgrade(
            Container backpack,
            ItemStack mainHand,
            int planks,
            int sticks,
            int logs,
            int cobble,
            ScavengerConfig cfg) {
        ToolTier owned = ToolTierPolicy.tierOfPick(backpack, mainHand);
        ToolTier target = ToolTierPolicy.targetPickTier(cfg);
        if (owned.compareTo(target) >= 0) {
            return Step.NOTHING;
        }
        if (owned.compareTo(ToolTier.WOOD) < 0 && target.compareTo(ToolTier.WOOD) >= 0) {
            return towardTool(planks, sticks, logs, Step.MAKE_PICKAXE);
        }
        if (owned.compareTo(ToolTier.STONE) < 0 && target.compareTo(ToolTier.STONE) >= 0) {
            return towardStoneTool(sticks, planks, logs, cobble, Step.MAKE_STONE_PICKAXE);
        }
        if (owned.compareTo(ToolTier.IRON) < 0 && target.compareTo(ToolTier.IRON) >= 0) {
            return towardConsumerTool(backpack, sticks, planks, logs, IRON_PICKAXE_RECIPE);
        }
        if (owned.compareTo(ToolTier.DIAMOND) < 0 && target.compareTo(ToolTier.DIAMOND) >= 0) {
            return towardConsumerTool(backpack, sticks, planks, logs, DIAMOND_PICKAXE_RECIPE);
        }
        return Step.NOTHING;
    }

    private static Step towardAxeUpgrade(
            Container backpack,
            ItemStack mainHand,
            int planks,
            int sticks,
            int logs,
            int cobble,
            ScavengerConfig cfg) {
        ToolTier owned = ToolTierPolicy.tierOfAxe(backpack, mainHand);
        ToolTier target = ToolTierPolicy.targetAxeTier(cfg);
        if (owned.compareTo(target) >= 0) {
            return Step.NOTHING;
        }
        if (owned.compareTo(ToolTier.WOOD) < 0 && target.compareTo(ToolTier.WOOD) >= 0) {
            return towardTool(planks, sticks, logs, Step.MAKE_AXE);
        }
        if (owned.compareTo(ToolTier.STONE) < 0 && target.compareTo(ToolTier.STONE) >= 0) {
            return towardStoneTool(sticks, planks, logs, cobble, Step.MAKE_STONE_AXE);
        }
        if (owned.compareTo(ToolTier.IRON) < 0 && target.compareTo(ToolTier.IRON) >= 0) {
            return towardConsumerTool(backpack, sticks, planks, logs, IRON_AXE_RECIPE);
        }
        if (owned.compareTo(ToolTier.DIAMOND) < 0 && target.compareTo(ToolTier.DIAMOND) >= 0) {
            return towardConsumerTool(backpack, sticks, planks, logs, DIAMOND_AXE_RECIPE);
        }
        return Step.NOTHING;
    }

    /**
     * Route toward any {@link ConsumerRecipeSpec}. Previously hardcoded {@code IRON_INGOT}, which
     * made the signature's promise of generality untrue and would have silently checked iron stock
     * for a diamond recipe. It now satisfies whatever the spec actually declares, so a new tier
     * needs a spec and nothing else here.
     */
    private static Step towardConsumerTool(
            Container backpack, int sticks, int planks, int logs, ConsumerRecipeSpec spec) {
        boolean haveAll = spec.ingredients().stream()
                .allMatch(r -> countMatching(backpack, r.key()) >= r.count());
        if (haveAll) {
            return spec.step();
        }
        if (sticks < spec.requiredCount(Items.STICK) && planks >= PLANKS_PER_STICK_CRAFT) {
            return Step.PLANKS_TO_STICKS;
        }
        if (sticks < spec.requiredCount(Items.STICK) && logs > 0) {
            return Step.LOGS_TO_PLANKS;
        }
        return Step.NOTHING;
    }

    private static Step towardStoneTool(int sticks, int planks, int logs, int cobble, Step tool) {
        if (cobble >= COBBLE_PER_TOOL && sticks >= STICKS_PER_TOOL) {
            return tool;
        }
        if (sticks < STICKS_PER_TOOL && planks >= PLANKS_PER_STICK_CRAFT) {
            return Step.PLANKS_TO_STICKS;
        }
        if (sticks < STICKS_PER_TOOL && logs > 0) {
            return Step.LOGS_TO_PLANKS;
        }
        return Step.NOTHING;
    }

    /** Whether the mob can make a table right now — checked separately, since it needs no table. */
    public static boolean canMakeTable(Container backpack) {
        return countTag(backpack, true) >= PLANKS_PER_TABLE;
    }

    /**
     * Whether a campfire is craftable right now: 3 logs + 3 sticks + 1 fuel.
     *
     * <p>Kept out of {@link #nextStep} on purpose. A campfire competes with torches for the same
     * coal, and torches are the mod's actual job — so the campfire goal asks this only once the mob
     * is already stocked, rather than the crafting chain quietly spending fuel on scenery.
     */
    public static boolean canMakeCampfire(Container backpack) {
        return countTag(backpack, false) >= LOGS_PER_CAMPFIRE
                && count(backpack, Items.STICK) >= STICKS_PER_CAMPFIRE
                && count(backpack, Items.COAL) + count(backpack, Items.CHARCOAL) >= 1;
    }

    /** 8 cobble → furnace (3×3 recipe; requires a crafting table in the goal). */
    public static boolean canMakeFurnace(Container backpack) {
        return count(backpack, Items.COBBLESTONE) >= COBBLE_PER_FURNACE;
    }

    /** Applies one step. Returns false when the inputs turned out not to be there after all. */
    public static boolean apply(Container backpack, Step step) {
        return apply(backpack, step, null);
    }

    /**
     * Like {@link #apply(Container, Step)} but drops replaced lower-tier tools at {@code dropAt}'s feet.
     *
     * <p>TT-0R: mutates a snapshot first, then atomically copies the result back. Ingredient
     * consumption, output insertion, and wooden-tool extraction either all commit or none do —
     * so a full backpack can still craft when the recipe itself frees a slot.
     */
    public static boolean apply(Container backpack, Step step, Mob dropAt) {
        ItemStack mainHand = dropAt == null ? ItemStack.EMPTY : dropAt.getMainHandItem();
        return apply(backpack, step, mainHand,
                dropped -> { if (dropAt != null) dropAt.spawnAtLocation(dropped); });
    }

    /** Testable transaction boundary including a replaced tool held in the main hand. */
    static boolean apply(
            Container backpack, Step step, ItemStack mainHand, Consumer<ItemStack> replacedSink) {
        SimpleContainer trial = copyOf(backpack);
        ItemStack[] extracted = {ItemStack.EMPTY};
        if (!applyMutating(trial, step, extracted)) {
            return false;
        }
        ConsumerRecipeSpec spec = recipeForStep(step).orElse(null);
        boolean replaceMainHand = extracted[0].isEmpty()
                && spec != null
                && !mainHand.isEmpty()
                && mainHand.is(spec.replacedItem());
        copyInto(backpack, trial);
        if (replaceMainHand) {
            ItemStack removed = mainHand.copyWithCount(1);
            mainHand.shrink(1);
            replacedSink.accept(removed);
        } else if (!extracted[0].isEmpty()) {
            replacedSink.accept(extracted[0]);
        }
        return true;
    }

    /** Mutates {@code backpack} in place. On failure the caller must discard the container. */
    private static boolean applyMutating(Container backpack, Step step, ItemStack[] extractedOut) {
        return switch (step) {
            case LOGS_TO_PLANKS -> {
                int slot = firstTagSlot(backpack, false);
                if (slot < 0) {
                    yield false;
                }
                ItemStack log = backpack.getItem(slot);
                Item planks = PlankMap.plankFor(log.getItem());
                ItemStack output = new ItemStack(planks, PLANKS_PER_LOG);
                backpack.removeItem(slot, 1);
                yield give(backpack, output);
            }
            case PLANKS_TO_STICKS -> {
                if (countTag(backpack, true) < PLANKS_PER_STICK_CRAFT) {
                    yield false;
                }
                ItemStack output = new ItemStack(Items.STICK, STICKS_PER_CRAFT);
                if (!take(backpack, true, PLANKS_PER_STICK_CRAFT)) {
                    yield false;
                }
                yield give(backpack, output);
            }
            case MAKE_TORCHES -> {
                if (count(backpack, Items.COAL) + count(backpack, Items.CHARCOAL) < 1
                        || count(backpack, Items.STICK) < 1) {
                    yield false;
                }
                ItemStack output = new ItemStack(Items.TORCH, TORCHES_PER_CRAFT);
                if (!takeItem(backpack, Items.COAL, 1) && !takeItem(backpack, Items.CHARCOAL, 1)) {
                    yield false;
                }
                if (!takeItem(backpack, Items.STICK, 1)) {
                    yield false;
                }
                yield give(backpack, output);
            }
            case MAKE_TABLE -> {
                if (countTag(backpack, true) < PLANKS_PER_TABLE) {
                    yield false;
                }
                ItemStack output = new ItemStack(Items.CRAFTING_TABLE, 1);
                if (!take(backpack, true, PLANKS_PER_TABLE)) {
                    yield false;
                }
                yield give(backpack, output);
            }
            case MAKE_PICKAXE -> makeWoodenTool(backpack, Items.WOODEN_PICKAXE);
            case MAKE_AXE -> makeWoodenTool(backpack, Items.WOODEN_AXE);
            case MAKE_STONE_PICKAXE -> makeStoneTool(
                    backpack, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE, extractedOut);
            case MAKE_STONE_AXE -> makeStoneTool(
                    backpack, Items.STONE_AXE, Items.WOODEN_AXE, extractedOut);
            case MAKE_DIAMOND_PICKAXE, MAKE_DIAMOND_AXE -> makeConsumerTool(
                    backpack, recipeForStep(step).orElseThrow(), extractedOut);
            case MAKE_IRON_PICKAXE, MAKE_IRON_AXE -> makeConsumerTool(
                    backpack, recipeForStep(step).orElseThrow(), extractedOut);
            case MAKE_CAMPFIRE -> {
                if (countTag(backpack, false) < LOGS_PER_CAMPFIRE
                        || count(backpack, Items.STICK) < STICKS_PER_CAMPFIRE
                        || count(backpack, Items.COAL) + count(backpack, Items.CHARCOAL) < 1) {
                    yield false;
                }
                ItemStack output = new ItemStack(Items.CAMPFIRE, 1);
                if (!take(backpack, false, LOGS_PER_CAMPFIRE)) {
                    yield false;
                }
                if (!takeItem(backpack, Items.STICK, STICKS_PER_CAMPFIRE)) {
                    yield false;
                }
                if (!takeItem(backpack, Items.COAL, 1) && !takeItem(backpack, Items.CHARCOAL, 1)) {
                    yield false;
                }
                yield give(backpack, output);
            }
            case MAKE_FURNACE -> {
                if (count(backpack, Items.COBBLESTONE) < COBBLE_PER_FURNACE) {
                    yield false;
                }
                ItemStack output = new ItemStack(Items.FURNACE, 1);
                if (!takeItem(backpack, Items.COBBLESTONE, COBBLE_PER_FURNACE)) {
                    yield false;
                }
                yield give(backpack, output);
            }
            case NOTHING -> false;
        };
    }

    /** 3 planks + 2 sticks. Capacity is proven on the TT-0R trial snapshot. */
    private static boolean makeWoodenTool(Container backpack, Item tool) {
        if (countTag(backpack, true) < PLANKS_PER_TOOL
                || count(backpack, Items.STICK) < STICKS_PER_TOOL) {
            return false;
        }
        ItemStack output = new ItemStack(tool, 1);
        if (!take(backpack, true, PLANKS_PER_TOOL)) {
            return false;
        }
        if (!takeItem(backpack, Items.STICK, STICKS_PER_TOOL)) {
            return false;
        }
        return give(backpack, output);
    }

    /**
     * 3 cobble + 2 sticks. Extracts the replaced wooden tool before inserting the stone tool so a
     * full mid-upgrade pack can free the output slot without a world spawn during simulation.
     */
    private static boolean makeStoneTool(
            Container backpack, Item tool, Item replaced, ItemStack[] extractedOut) {
        if (count(backpack, Items.COBBLESTONE) < COBBLE_PER_TOOL
                || count(backpack, Items.STICK) < STICKS_PER_TOOL) {
            return false;
        }
        ItemStack output = new ItemStack(tool, 1);
        if (!takeItem(backpack, Items.COBBLESTONE, COBBLE_PER_TOOL)) {
            return false;
        }
        if (!takeItem(backpack, Items.STICK, STICKS_PER_TOOL)) {
            return false;
        }
        extractedOut[0] = extractReplacedTool(backpack, replaced);
        return give(backpack, output);
    }

    private static Optional<ConsumerRecipeSpec> recipeForStep(Step step) {
        if (step == Step.MAKE_DIAMOND_PICKAXE) return Optional.of(DIAMOND_PICKAXE_RECIPE);
        if (step == Step.MAKE_DIAMOND_AXE) return Optional.of(DIAMOND_AXE_RECIPE);
        if (step == Step.MAKE_IRON_PICKAXE) return Optional.of(IRON_PICKAXE_RECIPE);
        if (step == Step.MAKE_IRON_AXE) return Optional.of(IRON_AXE_RECIPE);
        return Optional.empty();
    }

    private static boolean makeConsumerTool(
            Container backpack, ConsumerRecipeSpec spec, ItemStack[] extractedOut) {
        for (IngredientRequirement requirement : spec.ingredients()) {
            if (countMatching(backpack, requirement.key()) < requirement.count()) return false;
        }
        for (IngredientRequirement requirement : spec.ingredients()) {
            if (!takeMatching(backpack, requirement.key(), requirement.count())) return false;
        }
        extractedOut[0] = extractReplacedTool(backpack, spec.replacedItem());
        return give(backpack, new ItemStack(spec.output()));
    }

    /** Removes one replaced tool from the pack; caller spawns it only after a successful commit. */
    private static ItemStack extractReplacedTool(Container backpack, Item replaced) {
        if (replaced == null || replaced == Items.AIR) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.is(replaced)) {
                return backpack.removeItem(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static SimpleContainer copyOf(Container source) {
        SimpleContainer copy = new SimpleContainer(source.getContainerSize());
        for (int i = 0; i < source.getContainerSize(); i++) {
            copy.setItem(i, source.getItem(i).copy());
        }
        return copy;
    }

    private static void copyInto(Container dest, Container source) {
        int size = Math.min(dest.getContainerSize(), source.getContainerSize());
        for (int i = 0; i < size; i++) {
            dest.setItem(i, source.getItem(i).copy());
        }
    }

    public static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    /** Counts planks when {@code planks}, otherwise logs. */
    private static int countTag(Container c, boolean planks) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (planks ? s.is(ItemTags.PLANKS) : s.is(ItemTags.LOGS)) n += s.getCount();
        }
        return n;
    }

    private static int firstTagSlot(Container c, boolean planks) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (planks ? s.is(ItemTags.PLANKS) : s.is(ItemTags.LOGS)) return i;
        }
        return -1;
    }

    private static boolean take(Container c, boolean planks, int amount) {
        if (countTag(c, planks) < amount) return false;
        int left = amount;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack s = c.getItem(i);
            if (planks ? s.is(ItemTags.PLANKS) : s.is(ItemTags.LOGS)) {
                left -= c.removeItem(i, left).getCount();
            }
        }
        return left == 0;
    }

    private static boolean takeItem(Container c, Item item, int amount) {
        if (count(c, item) < amount) return false;
        int left = amount;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            if (c.getItem(i).is(item)) left -= c.removeItem(i, left).getCount();
        }
        return left == 0;
    }

    private static boolean takeMatching(Container c, IngredientKey key, int amount) {
        if (countMatching(c, key) < amount) return false;
        int left = amount;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            if (key.matches(c.getItem(i))) left -= c.removeItem(i, left).getCount();
        }
        return left == 0;
    }

    /** Whether {@link #give} could accept the full stack without mutating the container. */
    static boolean canGive(Container c, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int remaining = stack.getCount();
        for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
            ItemStack slot = c.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                remaining -= Math.min(slot.getMaxStackSize() - slot.getCount(), remaining);
            }
        }
        for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
            if (c.getItem(i).isEmpty()) {
                remaining -= Math.min(remaining, stack.getMaxStackSize());
            }
        }
        return remaining <= 0;
    }

    /** Merges into existing stacks, then empty slots. Returns false if it would not all fit. */
    public static boolean give(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = c.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int room = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
                if (room > 0) {
                    slot.grow(room);
                    stack.shrink(room);
                }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack.isEmpty();
    }
}
