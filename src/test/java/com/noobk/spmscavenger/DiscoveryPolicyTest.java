package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryPolicyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposedIronOreIsVisible() {
        GatherProtectionTest.MapBlockGetter level = surroundExposedIron(new BlockPos(0, 64, 0));
        BlockPos ore = new BlockPos(0, 64, 0);

        assertEquals(
                DiscoveryMode.VISIBLE,
                DiscoveryPolicy.classify(
                        level, ore, level.getBlockState(ore), null, 0L));
    }

    @Test
    void buriedIronOreIsUndiscovered() {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        BlockPos ore = new BlockPos(0, 64, 0);
        surroundBuriedIron(level, ore);

        assertEquals(
                DiscoveryMode.UNDISCOVERED,
                DiscoveryPolicy.classify(
                        level, ore, level.getBlockState(ore), null, 0L));
        assertFalse(
                GatherTargetPolicy.isLegitimateTarget(
                        DiscoveryPolicy.classify(
                                level, ore, level.getBlockState(ore), null, 0L)));
    }

    @Test
    void adjacentOreAfterRecentBreakIsNewlyExposed() {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        BlockPos broken = new BlockPos(0, 64, 0);
        BlockPos neighbour = broken.east();
        surroundBuriedIron(level, neighbour);
        level.set(broken, Blocks.AIR.defaultBlockState());

        DiscoveryPolicy.HarvestReveal reveal =
                new DiscoveryPolicy.HarvestReveal(broken, 100L);

        assertEquals(
                DiscoveryMode.NEWLY_EXPOSED,
                DiscoveryPolicy.classify(
                        level, neighbour, level.getBlockState(neighbour), reveal, 110L));
    }

    @Test
    void staleHarvestRevealFallsBackToVisibleWhenExposed() {
        GatherProtectionTest.MapBlockGetter level =
                surroundExposedIron(new BlockPos(0, 64, 0));
        BlockPos ore = new BlockPos(0, 64, 0);
        DiscoveryPolicy.HarvestReveal reveal =
                new DiscoveryPolicy.HarvestReveal(ore.below(), 10L);

        assertEquals(
                DiscoveryMode.VISIBLE,
                DiscoveryPolicy.classify(
                        level, ore, level.getBlockState(ore), reveal, 100L));
    }

    private static GatherProtectionTest.MapBlockGetter surroundExposedIron(BlockPos ore) {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        level.set(ore, Blocks.IRON_ORE.defaultBlockState());
        level.set(ore.above(), Blocks.AIR.defaultBlockState());
        level.set(ore.below(), Blocks.STONE.defaultBlockState());
        level.set(ore.north(), Blocks.STONE.defaultBlockState());
        level.set(ore.south(), Blocks.STONE.defaultBlockState());
        level.set(ore.east(), Blocks.STONE.defaultBlockState());
        level.set(ore.west(), Blocks.STONE.defaultBlockState());
        return level;
    }

    private static void surroundBuriedIron(
            GatherProtectionTest.MapBlockGetter level, BlockPos ore) {
        level.set(ore, Blocks.IRON_ORE.defaultBlockState());
        for (BlockPos neighbour : new BlockPos[] {
            ore.above(), ore.below(), ore.north(), ore.south(), ore.east(), ore.west()
        }) {
            level.set(neighbour, Blocks.STONE.defaultBlockState());
        }
    }
}
