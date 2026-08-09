package com.noobk.spmscavenger;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pure policy for which tool tier a scavenger owns, wants, and may craft toward.
 *
 * <p>Operates on a {@link Container} only — no entity, no level — so tier gates are unit-testable
 * without bootstrapping item tags.
 */
public final class ToolTierPolicy {

    public enum ToolKind {
        PICK,
        AXE
    }

    private ToolTierPolicy() {
    }

    public static ToolTier tierOfPick(Item item) {
        if (item == Items.WOODEN_PICKAXE) return ToolTier.WOOD;
        if (item == Items.STONE_PICKAXE) return ToolTier.STONE;
        if (item == Items.IRON_PICKAXE) return ToolTier.IRON;
        // Phase 1 (D-TTU-009): gold has wooden-level harvest restrictions — never rank as IRON.
        if (item == Items.GOLDEN_PICKAXE) return ToolTier.WOOD;
        if (item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE) return ToolTier.DIAMOND;
        return ToolTier.NONE;
    }

    public static ToolTier tierOfAxe(Item item) {
        if (item == Items.WOODEN_AXE) return ToolTier.WOOD;
        if (item == Items.STONE_AXE) return ToolTier.STONE;
        if (item == Items.IRON_AXE) return ToolTier.IRON;
        // Phase 1 (D-TTU-009): same ranking as pick — stone durability upgrade still pursued.
        if (item == Items.GOLDEN_AXE) return ToolTier.WOOD;
        if (item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE) return ToolTier.DIAMOND;
        return ToolTier.NONE;
    }

    public static ToolTier tierOfPick(Container backpack) {
        return tierOfPick(backpack, ItemStack.EMPTY);
    }

    public static ToolTier tierOfAxe(Container backpack) {
        return tierOfAxe(backpack, ItemStack.EMPTY);
    }

    public static ToolTier tierOfPick(Container backpack, ItemStack mainHand) {
        return bestTier(backpack, mainHand, ToolKind.PICK);
    }

    public static ToolTier tierOfAxe(Container backpack, ItemStack mainHand) {
        return bestTier(backpack, mainHand, ToolKind.AXE);
    }

    public static ToolTier targetPickTier(ScavengerConfig cfg) {
        return cfg.craftTools ? cfg.maxPickTier : ToolTier.NONE;
    }

    public static ToolTier targetAxeTier(ScavengerConfig cfg) {
        return cfg.craftTools ? cfg.maxAxeTier : ToolTier.NONE;
    }

    public static boolean needsPickUpgrade(Container backpack, ScavengerConfig cfg) {
        return needsPickUpgrade(backpack, ItemStack.EMPTY, cfg);
    }

    public static boolean needsAxeUpgrade(Container backpack, ScavengerConfig cfg) {
        return needsAxeUpgrade(backpack, ItemStack.EMPTY, cfg);
    }

    public static boolean needsPickUpgrade(Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        return targetPickTier(cfg).compareTo(tierOfPick(backpack, mainHand)) > 0;
    }

    public static boolean needsAxeUpgrade(Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        return targetAxeTier(cfg).compareTo(tierOfAxe(backpack, mainHand)) > 0;
    }

    public static boolean ownsAtLeast(Container backpack, ToolTier tier, ToolKind kind) {
        ToolTier owned = kind == ToolKind.PICK ? tierOfPick(backpack) : tierOfAxe(backpack);
        return owned.compareTo(tier) >= 0;
    }

    public static Item pickItem(ToolTier tier) {
        return switch (tier) {
            case WOOD -> Items.WOODEN_PICKAXE;
            case STONE -> Items.STONE_PICKAXE;
            case IRON -> Items.IRON_PICKAXE;
            case DIAMOND -> Items.DIAMOND_PICKAXE;
            case NONE -> Items.AIR;
        };
    }

    public static Item axeItem(ToolTier tier) {
        return switch (tier) {
            case WOOD -> Items.WOODEN_AXE;
            case STONE -> Items.STONE_AXE;
            case IRON -> Items.IRON_AXE;
            case DIAMOND -> Items.DIAMOND_AXE;
            case NONE -> Items.AIR;
        };
    }

    /** Replaced wooden tier when upgrading to stone — used for backpack disposal. */
    public static Item replacedItem(ToolTier craftedTier, ToolKind kind) {
        return switch (craftedTier) {
            case STONE -> kind == ToolKind.PICK ? Items.WOODEN_PICKAXE : Items.WOODEN_AXE;
            case IRON -> kind == ToolKind.PICK ? Items.STONE_PICKAXE : Items.STONE_AXE;
            case DIAMOND -> kind == ToolKind.PICK ? Items.IRON_PICKAXE : Items.IRON_AXE;
            default -> Items.AIR;
        };
    }

    /**
     * Whether the mob should still gather cobble for pending stone-tier crafts.
     *
     * <p>Stops once both target stone tools are owned (backpack + main hand) or cobble stock meets
     * the configured cap.
     */
    public static boolean cobbleBelowTarget(Container backpack, ScavengerConfig cfg) {
        return cobbleBelowTarget(backpack, ItemStack.EMPTY, cfg);
    }

    /**
     * Like {@link #cobbleBelowTarget(Container, ScavengerConfig)} but counts a drawn main-hand tool
     * toward ownership (D-TTU-011 / TT-1bR).
     */
    public static boolean cobbleBelowTarget(Container backpack, ItemStack mainHand, ScavengerConfig cfg) {
        if (!cfg.craftTools) {
            return false;
        }
        if (!needsPickUpgrade(backpack, mainHand, cfg) && !needsAxeUpgrade(backpack, mainHand, cfg)) {
            return false;
        }
        ToolTier pickTarget = targetPickTier(cfg);
        ToolTier axeTarget = targetAxeTier(cfg);
        if (pickTarget.compareTo(ToolTier.STONE) < 0 && axeTarget.compareTo(ToolTier.STONE) < 0) {
            return false;
        }
        return ScavengerCrafting.count(backpack, Items.COBBLESTONE) < cfg.cobbleStockTarget;
    }

    private static ToolTier bestTier(Container backpack, ItemStack mainHand, ToolKind kind) {
        ToolTier best = ToolTier.NONE;
        ToolTier handTier = tierFromStack(mainHand, kind);
        if (handTier.compareTo(best) > 0) {
            best = handTier;
        }
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ToolTier tier = tierFromStack(backpack.getItem(i), kind);
            if (tier.compareTo(best) > 0) {
                best = tier;
            }
        }
        return best;
    }

    private static ToolTier tierFromStack(ItemStack stack, ToolKind kind) {
        if (!isUsableTool(stack)) {
            return ToolTier.NONE;
        }
        return kind == ToolKind.PICK ? tierOfPick(stack.getItem()) : tierOfAxe(stack.getItem());
    }

    private static boolean isUsableTool(ItemStack stack) {
        return !stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage();
    }
}
