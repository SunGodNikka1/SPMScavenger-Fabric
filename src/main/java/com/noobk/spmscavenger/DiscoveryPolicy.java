package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classifies how a gather scan candidate was discovered (MI-13).
 */
public final class DiscoveryPolicy {

    /** Ticks after a break during which adjacent ore counts as {@link DiscoveryMode#NEWLY_EXPOSED}. */
    public static final int NEWLY_EXPOSED_WINDOW_TICKS = 40;

    public record HarvestReveal(BlockPos pos, long gameTime) {
        public boolean isRecent(long now) {
            return pos != null && now - gameTime <= NEWLY_EXPOSED_WINDOW_TICKS;
        }
    }

    private DiscoveryPolicy() {
    }

    public static DiscoveryMode classify(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            HarvestReveal reveal,
            long currentGameTime) {
        if (GatherProtection.isGatherableOreType(state)) {
            return classifyOre(level, pos, reveal, currentGameTime);
        }
        if (state.is(BlockTags.LOGS)
                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)) {
            return DiscoveryMode.VISIBLE;
        }
        return DiscoveryMode.UNDISCOVERED;
    }

    private static DiscoveryMode classifyOre(
            BlockGetter level, BlockPos pos, HarvestReveal reveal, long currentGameTime) {
        if (reveal != null && reveal.isRecent(currentGameTime) && isAdjacent(pos, reveal.pos())) {
            return DiscoveryMode.NEWLY_EXPOSED;
        }
        if (GatherProtection.isExposedToAir(level, pos)) {
            return DiscoveryMode.VISIBLE;
        }
        return DiscoveryMode.UNDISCOVERED;
    }

    private static boolean isAdjacent(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1
                && Math.abs(a.getY() - b.getY()) <= 1
                && Math.abs(a.getZ() - b.getZ()) <= 1
                && !a.equals(b);
    }
}
