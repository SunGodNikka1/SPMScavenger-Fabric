package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherCandidatePolicyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void buriedIronOreIsNotPassOneCandidate() {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        BlockPos ore = new BlockPos(0, 64, 0);
        level.set(ore, Blocks.IRON_ORE.defaultBlockState());
        level.set(ore.above(), Blocks.STONE.defaultBlockState());
        level.set(ore.below(), Blocks.STONE.defaultBlockState());
        level.set(ore.north(), Blocks.STONE.defaultBlockState());
        level.set(ore.south(), Blocks.STONE.defaultBlockState());
        level.set(ore.east(), Blocks.STONE.defaultBlockState());
        level.set(ore.west(), Blocks.STONE.defaultBlockState());

        GatherIntentPolicy.GatherIntent intent = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON),
                com.noobk.spmscavenger.ScavengerCrafting.Step.NOTHING);
        Predicate<BlockState> alwaysTool = state -> true;

        assertFalse(GatherCandidatePolicy.isPassOneCandidate(
                level, ore, level.getBlockState(ore), intent, alwaysTool));
    }

    @Test
    void exposedIronOreIsPassOneCandidate() {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        BlockPos ore = new BlockPos(0, 64, 0);
        level.set(ore, Blocks.IRON_ORE.defaultBlockState());
        level.set(ore.above(), Blocks.AIR.defaultBlockState());
        level.set(ore.below(), Blocks.STONE.defaultBlockState());
        level.set(ore.north(), Blocks.STONE.defaultBlockState());
        level.set(ore.south(), Blocks.STONE.defaultBlockState());
        level.set(ore.east(), Blocks.STONE.defaultBlockState());
        level.set(ore.west(), Blocks.STONE.defaultBlockState());

        GatherIntentPolicy.GatherIntent intent = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON),
                com.noobk.spmscavenger.ScavengerCrafting.Step.NOTHING);
        Predicate<BlockState> alwaysTool = state -> true;

        assertTrue(GatherCandidatePolicy.isPassOneCandidate(
                level, ore, level.getBlockState(ore), intent, alwaysTool));
    }

    @Test
    void buriedOreCannotDisplaceExposedOreInNearestBuffer() {
        GatherProtectionTest.MapBlockGetter level = new GatherProtectionTest.MapBlockGetter();
        BlockPos origin = BlockPos.ZERO;
        BlockPos exposed = new BlockPos(5, 0, 0);
        level.set(origin, Blocks.STONE.defaultBlockState());

        for (int i = 1; i <= 24; i++) {
            BlockPos buried = new BlockPos(i, 0, 0);
            surroundBuriedIron(level, buried);
        }
        surroundExposedIron(level, exposed);

        GatherIntentPolicy.GatherIntent intent = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON),
                com.noobk.spmscavenger.ScavengerCrafting.Step.NOTHING);
        Predicate<BlockState> alwaysTool = state -> true;

        BlockPos[] nearest = new BlockPos[24];
        double[] dists = new double[24];
        int found = 0;
        int r = 10;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!GatherCandidatePolicy.isPassOneCandidate(
                            level, pos, state, intent, alwaysTool)) {
                        continue;
                    }
                    double dist = pos.distSqr(origin);
                    if (found == 24 && dist >= dists[found - 1]) {
                        continue;
                    }
                    int at = (found == 24) ? found - 1 : found++;
                    while (at > 0 && dists[at - 1] > dist) {
                        dists[at] = dists[at - 1];
                        nearest[at] = nearest[at - 1];
                        at--;
                    }
                    dists[at] = dist;
                    nearest[at] = pos.immutable();
                }
            }
        }

        assertEquals(1, found);
        assertEquals(exposed, nearest[0]);
    }

    private static void surroundBuriedIron(
            GatherProtectionTest.MapBlockGetter level, BlockPos ore) {
        level.set(ore, Blocks.IRON_ORE.defaultBlockState());
        for (BlockPos neighbour : new BlockPos[] {
            ore.above(), ore.below(), ore.north(), ore.south(), ore.east(), ore.west()
        }) {
            if (!level.getBlockState(neighbour).isAir()) {
                level.set(neighbour, Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void surroundExposedIron(
            GatherProtectionTest.MapBlockGetter level, BlockPos ore) {
        level.set(ore, Blocks.IRON_ORE.defaultBlockState());
        level.set(ore.above(), Blocks.AIR.defaultBlockState());
        level.set(ore.below(), Blocks.STONE.defaultBlockState());
        level.set(ore.north(), Blocks.STONE.defaultBlockState());
        level.set(ore.south(), Blocks.STONE.defaultBlockState());
        level.set(ore.east(), Blocks.STONE.defaultBlockState());
        level.set(ore.west(), Blocks.STONE.defaultBlockState());
    }
}
