package com.noobk.spmscavenger;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Which planks a log crafts into.
 *
 * <p>Vanilla exposes no log-to-planks lookup — the relationship exists only inside recipe JSON, and
 * resolving it through the recipe manager needs a server and a {@code CraftingInput} for a mob that
 * is only ever making a 2x2. A table is the honest way to express it.
 *
 * <p>Stripped logs, wood and hyphae are mapped too, because a scavenger picking up whatever it finds
 * has no reason to know the difference. Anything unmapped — a modded log, most likely — falls back
 * to oak rather than failing: making the wrong plank is a much smaller sin than a mob that stares at
 * a log it cannot use. Modded planks still work as *inputs* to sticks, since that side matches on
 * {@code #minecraft:planks} rather than this table.
 */
public final class PlankMap {

    private static final Map<Item, Item> LOG_TO_PLANKS = Map.ofEntries(
            Map.entry(Items.OAK_LOG, Items.OAK_PLANKS),
            Map.entry(Items.STRIPPED_OAK_LOG, Items.OAK_PLANKS),
            Map.entry(Items.OAK_WOOD, Items.OAK_PLANKS),
            Map.entry(Items.STRIPPED_OAK_WOOD, Items.OAK_PLANKS),
            Map.entry(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS),
            Map.entry(Items.STRIPPED_SPRUCE_LOG, Items.SPRUCE_PLANKS),
            Map.entry(Items.SPRUCE_WOOD, Items.SPRUCE_PLANKS),
            Map.entry(Items.STRIPPED_SPRUCE_WOOD, Items.SPRUCE_PLANKS),
            Map.entry(Items.BIRCH_LOG, Items.BIRCH_PLANKS),
            Map.entry(Items.STRIPPED_BIRCH_LOG, Items.BIRCH_PLANKS),
            Map.entry(Items.BIRCH_WOOD, Items.BIRCH_PLANKS),
            Map.entry(Items.STRIPPED_BIRCH_WOOD, Items.BIRCH_PLANKS),
            Map.entry(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS),
            Map.entry(Items.STRIPPED_JUNGLE_LOG, Items.JUNGLE_PLANKS),
            Map.entry(Items.JUNGLE_WOOD, Items.JUNGLE_PLANKS),
            Map.entry(Items.STRIPPED_JUNGLE_WOOD, Items.JUNGLE_PLANKS),
            Map.entry(Items.ACACIA_LOG, Items.ACACIA_PLANKS),
            Map.entry(Items.STRIPPED_ACACIA_LOG, Items.ACACIA_PLANKS),
            Map.entry(Items.ACACIA_WOOD, Items.ACACIA_PLANKS),
            Map.entry(Items.STRIPPED_ACACIA_WOOD, Items.ACACIA_PLANKS),
            Map.entry(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS),
            Map.entry(Items.STRIPPED_DARK_OAK_LOG, Items.DARK_OAK_PLANKS),
            Map.entry(Items.DARK_OAK_WOOD, Items.DARK_OAK_PLANKS),
            Map.entry(Items.STRIPPED_DARK_OAK_WOOD, Items.DARK_OAK_PLANKS),
            Map.entry(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS),
            Map.entry(Items.STRIPPED_MANGROVE_LOG, Items.MANGROVE_PLANKS),
            Map.entry(Items.MANGROVE_WOOD, Items.MANGROVE_PLANKS),
            Map.entry(Items.STRIPPED_MANGROVE_WOOD, Items.MANGROVE_PLANKS),
            Map.entry(Items.CHERRY_LOG, Items.CHERRY_PLANKS),
            Map.entry(Items.STRIPPED_CHERRY_LOG, Items.CHERRY_PLANKS),
            Map.entry(Items.CHERRY_WOOD, Items.CHERRY_PLANKS),
            Map.entry(Items.STRIPPED_CHERRY_WOOD, Items.CHERRY_PLANKS),
            Map.entry(Items.CRIMSON_STEM, Items.CRIMSON_PLANKS),
            Map.entry(Items.STRIPPED_CRIMSON_STEM, Items.CRIMSON_PLANKS),
            Map.entry(Items.CRIMSON_HYPHAE, Items.CRIMSON_PLANKS),
            Map.entry(Items.STRIPPED_CRIMSON_HYPHAE, Items.CRIMSON_PLANKS),
            Map.entry(Items.WARPED_STEM, Items.WARPED_PLANKS),
            Map.entry(Items.STRIPPED_WARPED_STEM, Items.WARPED_PLANKS),
            Map.entry(Items.WARPED_HYPHAE, Items.WARPED_PLANKS),
            Map.entry(Items.STRIPPED_WARPED_HYPHAE, Items.WARPED_PLANKS));

    private PlankMap() {
    }

    public static Item plankFor(Item log) {
        return LOG_TO_PLANKS.getOrDefault(log, Items.OAK_PLANKS);
    }
}
