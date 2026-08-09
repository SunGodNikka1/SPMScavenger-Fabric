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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherTargetPolicyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blockingIronOutranksWealthCoalAtSameDistance() {
        GatherIntentPolicy.GatherIntent intent = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON),
                Map.of(
                        GatherIntentPolicy.Resource.COAL,
                        new ResourceWealthPolicy.ResourceWealthContext(
                                ResourceWealthPolicy.ResourceCategory.COAL,
                                0,
                                0.75F,
                                1.0F)),
                ScavengerCrafting.Step.NOTHING);

        BlockState iron = Blocks.IRON_ORE.defaultBlockState();
        BlockState coal = Blocks.COAL_ORE.defaultBlockState();
        float cost = 1.0F;

        int ironPriority = GatherTargetPolicy.priority(
                intent, iron, DiscoveryMode.VISIBLE, cost);
        int coalPriority = GatherTargetPolicy.priority(
                intent, coal, DiscoveryMode.VISIBLE, cost);

        assertTrue(ironPriority > coalPriority);
    }

    @Test
    void nearerBlockingTargetWinsAmongEqualTiers() {
        GatherIntentPolicy.GatherIntent intent = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON),
                Map.of(),
                ScavengerCrafting.Step.NOTHING);
        BlockState iron = Blocks.IRON_ORE.defaultBlockState();

        int nearPriority = GatherTargetPolicy.priority(
                intent, iron, DiscoveryMode.VISIBLE, 0.5F);
        int farPriority = GatherTargetPolicy.priority(
                intent, iron, DiscoveryMode.VISIBLE, 3.0F);

        assertTrue(nearPriority > farPriority);
    }

    @Test
    void sortIndicesPrefersBlockingOreOverWealthOre() {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        BlockPos nearCoal = new BlockPos(1, 0, 0);
        BlockPos farIron = new BlockPos(8, 0, 0);
        exposeOre(level, nearCoal, Blocks.COAL_ORE);
        exposeOre(level, farIron, Blocks.IRON_ORE);

        GatherIntentPolicy.GatherIntent intent = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON),
                Map.of(
                        GatherIntentPolicy.Resource.COAL,
                        new ResourceWealthPolicy.ResourceWealthContext(
                                ResourceWealthPolicy.ResourceCategory.COAL,
                                0,
                                0.75F,
                                1.0F)),
                ScavengerCrafting.Step.NOTHING);

        BlockPos[] nearest = {nearCoal, farIron};
        double[] dists = {
            nearCoal.distSqr(BlockPos.ZERO), farIron.distSqr(BlockPos.ZERO)
        };

        int[] order = GatherTargetPolicy.sortIndicesByPriority(
                nearest, dists, 2, level, intent, null, 0L);

        assertEquals(1, order[0]);
        assertEquals(0, order[1]);
    }

    private static void exposeOre(
            GatherProtectionTest.MapBlockGetter level, BlockPos ore, net.minecraft.world.level.block.Block block) {
        level.set(ore, block.defaultBlockState());
        level.set(ore.above(), Blocks.AIR.defaultBlockState());
        level.set(ore.below(), Blocks.STONE.defaultBlockState());
        level.set(ore.north(), Blocks.STONE.defaultBlockState());
        level.set(ore.south(), Blocks.STONE.defaultBlockState());
        level.set(ore.east(), Blocks.STONE.defaultBlockState());
        level.set(ore.west(), Blocks.STONE.defaultBlockState());
    }
}
