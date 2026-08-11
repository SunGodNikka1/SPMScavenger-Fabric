package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerCrafting;

/** Pure presentation mapping for the recipe currently owned by {@link CraftTorchesGoal}. */
public final class CraftingReadout {

    private static final String OBJECTIVE = "Crafting";

    private CraftingReadout() {
    }

    /**
     * Returns the complete SPM objective line for one immutable crafting decision.
     *
     * <p>{@link ScavengerCrafting.Step#NOTHING} deliberately has no em-dash suffix. A running
     * crafting goal should not normally expose that transient state, but the fallback keeps the
     * optional compatibility hook non-empty if the host samples during shutdown.
     */
    public static String forStep(ScavengerCrafting.Step step) {
        String recipe = switch (step) {
            case NOTHING -> null;
            case LOGS_TO_PLANKS -> "planks";
            case PLANKS_TO_STICKS -> "sticks";
            case MAKE_TORCHES -> "torches";
            case MAKE_TABLE -> "crafting table";
            case MAKE_PICKAXE -> "wooden pickaxe";
            case MAKE_AXE -> "wooden axe";
            case MAKE_STONE_PICKAXE -> "stone pickaxe";
            case MAKE_STONE_AXE -> "stone axe";
            case MAKE_IRON_PICKAXE -> "iron pickaxe";
            case MAKE_IRON_AXE -> "iron axe";
            case MAKE_DIAMOND_PICKAXE -> "diamond pickaxe";
            case MAKE_DIAMOND_AXE -> "diamond axe";
            case MAKE_CAMPFIRE -> "campfire";
            case MAKE_FURNACE -> "furnace";
        };
        return recipe == null ? OBJECTIVE : OBJECTIVE + " — " + recipe;
    }
}
