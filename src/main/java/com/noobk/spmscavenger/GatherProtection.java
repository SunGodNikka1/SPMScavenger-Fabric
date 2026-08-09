package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a log or ore block is safe for a scavenger to break.
 *
 * <p>Minecraft exposes no record of who placed a block, so "is this a player's build?" can only be
 * approximated from shape and surroundings. The approximation must fail <b>towards refusing</b>:
 * a mob that declines a real tree is a mild inefficiency, a mob that eats a house is a catastrophe.
 *
 * <h2>What the first version got wrong</h2>
 *
 * It accepted a log when {@code hasLeavesNearby(radius 6) || isVerticalTrunk()}. Both halves leak
 * badly:
 *
 * <ul>
 *   <li><b>Leaves anywhere within 6 blocks</b> scanned a 13x13x13 cube — 2,197 positions — and
 *       accepted <em>any</em> leaf. A cabin at the edge of a forest, or with one overhanging branch,
 *       had every one of its logs whitelisted.</li>
 *   <li><b>"A log above or below"</b> is true of every stacked log wall ever built. It describes a
 *       column, and a wall is a column.</li>
 *   <li>The safety net treated {@code LOGS} and {@code LEAVES} as natural neighbours, so a log
 *       cabin looked entirely natural <em>to itself</em>.</li>
 * </ul>
 *
 * <p>So a log house next to trees passed all three tests. That is the failure that ate a user's
 * house.
 *
 * <h2>What replaces it</h2>
 *
 * A log is gatherable only when it is part of something shaped like a tree <b>and</b> standing away
 * from anything built:
 *
 * <ol>
 *   <li><b>Rooted.</b> The bottom of its column rests on natural growing ground — dirt, grass,
 *       podzol, sand, moss, mud, nylium. A wall sits on planks, stone or a foundation.</li>
 *   <li><b>Tall enough.</b> The column is at least {@value #MIN_TRUNK_HEIGHT} logs. Decorative
 *       single logs and short posts are refused.</li>
 *   <li><b>Crowned.</b> Leaves within two blocks of the <em>top</em> of that column — a canopy,
 *       not merely leaves somewhere in the postcode. A wall's top has a roof, not foliage.</li>
 *   <li><b>Not a wall run.</b> Three or more logs in a horizontal line still reads as a wall.</li>
 *   <li><b>Isolated.</b> No man-made block within {@value #STRUCTURE_RADIUS} blocks.</li>
 * </ol>
 *
 * Ore must additionally be exposed to air and away from anything built, so decorative ore in a
 * stone-brick wall is refused while a cave-wall vein is fair game.
 *
 * <h2>Unknown blocks count as man-made</h2>
 *
 * {@link #isNatural} is a whitelist. Anything it does not recognise — including every modded block —
 * counts as built, so an unfamiliar mod makes the scavenger <em>more</em> cautious rather than less.
 * That is the correct direction for the failure to point.
 */
public final class GatherProtection {

    /** Shortest column that can be a tree rather than decoration. */
    public static final int MIN_TRUNK_HEIGHT = 3;
    /** How far above the trunk top to look for canopy. */
    private static final int CANOPY_RADIUS = 2;
    /** No man-made block may be this close to a gatherable block. */
    public static final int STRUCTURE_RADIUS = 3;
    /** Horizontal log runs this long or longer are walls, not trunks. */
    private static final int MIN_HORIZONTAL_WALL_RUN = 3;
    /** Bounds the column walk so a pathological column cannot stall a tick. */
    private static final int MAX_COLUMN_WALK = 32;

    private GatherProtection() {
    }

    public static boolean isGatherableLog(Level level, BlockPos pos, ScavengerConfig cfg) {
        if (!cfg.protectPlayerBuilds) {
            return true;
        }
        if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
            return false;
        }
        // Cheapest discriminators first — most rejections never reach the wider scans.
        if (isHorizontalLogWall(level, pos)) {
            return false;
        }
        BlockPos base = columnEnd(level, pos, Direction.DOWN);
        if (!isGrowingGround(level.getBlockState(base.below()))) {
            return false;
        }
        BlockPos top = columnEnd(level, pos, Direction.UP);
        if (top.getY() - base.getY() + 1 < MIN_TRUNK_HEIGHT) {
            return false;
        }
        if (!hasCanopy(level, top)) {
            return false;
        }
        return !hasBuiltNearby(level, pos);
    }

    /**
     * Ore types the scavenger may mine at all (TT-2c). Iron joins coal under the <em>same</em>
     * exposure and built-nearby rules — a mob may take an exposed vein, never a wall someone built
     * out of ore blocks, and never one buried behind intact stone.
     */
    public static boolean isGatherableOreType(BlockState state) {
        return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)
                || state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
                || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE);
    }

    public static boolean isGatherableOre(Level level, BlockPos pos, ScavengerConfig cfg) {
        if (!cfg.protectPlayerBuilds) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (!isGatherableOreType(state)) {
            return false;
        }
        if (!GatherProtection.isExposedToAir(level, pos)) {
            return false;
        }
        return !hasBuiltNearby(level, pos);
    }

    /**
     * Whether an ore block has at least one air-adjacent face. Used in pass-one gather scanning
     * (MI-13a) so buried ore cannot displace legitimately exposed veins in the candidate buffer.
     */
    public static boolean isExposedToAir(BlockGetter level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a surface stone or cobble block is safe to mine for cobble stock.
     *
     * <p>Phase 1 refuses infested stone, buried blocks, horizontal wall runs, and anything near a
     * player build — the same fail-toward-refusing posture as logs and ore.
     */
    public static boolean isGatherableStone(Level level, BlockPos pos, ScavengerConfig cfg) {
        return isGatherableStone((BlockGetter) level, pos, cfg);
    }

    static boolean isGatherableStone(BlockGetter level, BlockPos pos, ScavengerConfig cfg) {
        if (!cfg.protectPlayerBuilds) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (isInfestedStone(state)) {
            return false;
        }
        if (!isStoneGatherBlock(state)) {
            return false;
        }
        if (isHorizontalStoneWall(level, pos)) {
            return false;
        }
        if (!GatherProtection.isExposedToAir(level, pos)) {
            return false;
        }
        return !hasBuiltNearby(level, pos);
    }

    // ---- Shape tests ------------------------------------------------------

    /** Walks the contiguous log column in {@code dir} and returns its last log. */
    private static BlockPos columnEnd(Level level, BlockPos from, Direction dir) {
        BlockPos cursor = from;
        for (int i = 0; i < MAX_COLUMN_WALK; i++) {
            BlockPos next = cursor.relative(dir);
            if (!level.getBlockState(next).is(BlockTags.LOGS)) {
                break;
            }
            cursor = next;
        }
        return cursor.immutable();
    }

    /**
     * Ground a tree could actually have grown from. A wall stands on a floor — planks, stone,
     * bricks — none of which appear here.
     */
    private static boolean isGrowingGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.NYLIUM)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL);
    }

    /** Leaves close to the top of the trunk — a crown, not a leaf somewhere in the neighbourhood. */
    private static boolean hasCanopy(Level level, BlockPos top) {
        for (int dx = -CANOPY_RADIUS; dx <= CANOPY_RADIUS; dx++) {
            for (int dz = -CANOPY_RADIUS; dz <= CANOPY_RADIUS; dz++) {
                for (int dy = -1; dy <= CANOPY_RADIUS; dy++) {
                    if (level.getBlockState(top.offset(dx, dy, dz)).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isHorizontalLogWall(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (horizontalRunLength(level, pos, dir) >= MIN_HORIZONTAL_WALL_RUN) {
                return true;
            }
        }
        return false;
    }

    private static int horizontalRunLength(Level level, BlockPos pos, Direction dir) {
        int count = 1;
        BlockPos cursor = pos.relative(dir);
        while (count < 8 && level.getBlockState(cursor).is(BlockTags.LOGS)) {
            count++;
            cursor = cursor.relative(dir);
        }
        cursor = pos.relative(dir.getOpposite());
        while (count < 8 && level.getBlockState(cursor).is(BlockTags.LOGS)) {
            count++;
            cursor = cursor.relative(dir.getOpposite());
        }
        return count;
    }

    private static boolean isHorizontalStoneWall(BlockGetter level, BlockPos pos) {
        if (!isStoneGatherBlock(level.getBlockState(pos))) {
            return false;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (horizontalStoneRunLength(level, pos, dir) >= MIN_HORIZONTAL_WALL_RUN) {
                return true;
            }
        }
        return false;
    }

    private static int horizontalStoneRunLength(BlockGetter level, BlockPos pos, Direction dir) {
        int count = 1;
        BlockPos cursor = pos.relative(dir);
        while (count < 8 && isStoneGatherBlock(level.getBlockState(cursor))) {
            count++;
            cursor = cursor.relative(dir);
        }
        cursor = pos.relative(dir.getOpposite());
        while (count < 8 && isStoneGatherBlock(level.getBlockState(cursor))) {
            count++;
            cursor = cursor.relative(dir.getOpposite());
        }
        return count;
    }

    private static boolean isStoneGatherBlock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE);
    }

    private static boolean isInfestedStone(BlockState state) {
        return state.is(Blocks.INFESTED_STONE)
                || state.is(Blocks.INFESTED_COBBLESTONE)
                || state.is(Blocks.INFESTED_DEEPSLATE)
                || state.is(Blocks.INFESTED_STONE_BRICKS)
                || state.is(Blocks.INFESTED_CHISELED_STONE_BRICKS)
                || state.is(Blocks.INFESTED_CRACKED_STONE_BRICKS)
                || state.is(Blocks.INFESTED_MOSSY_STONE_BRICKS);
    }

    // ---- Surroundings -----------------------------------------------------

    /** True when anything man-made stands within {@link #STRUCTURE_RADIUS}. */
    public static boolean hasBuiltNearby(Level level, BlockPos pos) {
        return hasBuiltNearby((BlockGetter) level, pos);
    }

    static boolean hasBuiltNearby(BlockGetter level, BlockPos pos) {
        int r = STRUCTURE_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!isNatural(level.getBlockState(pos.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The whitelist of blocks that occur without a player. <b>Anything absent counts as built</b>,
     * including modded blocks — the conservative direction.
     *
     * <p>Note what is deliberately <em>not</em> here: planks, stairs, slabs, doors, fences, glass,
     * wool, bricks, and every worked stone. Those are the blocks a house is made of, and the first
     * version's inverted "is this natural?" neighbour check let a log cabin vouch for itself.
     */
    public static boolean isNatural(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return true;
        }
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.NYLIUM)) {
            return true;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return true;
        }
        if (state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.CORAL_BLOCKS)
                || state.is(BlockTags.ICE)
                || state.is(BlockTags.SNOW)) {
            return true;
        }
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.WATER) || state.is(Blocks.LAVA)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            return true;
        }
        // Cave and surface decoration.
        if (state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.HANGING_ROOTS) || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COCOA) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.SCULK) || state.is(Blocks.SCULK_VEIN)) {
            return true;
        }
        // Ores generate naturally; a decorative ore wall is still caught by its other neighbours.
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES);
    }
}
