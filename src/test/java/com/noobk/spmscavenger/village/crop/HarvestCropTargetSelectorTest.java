package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Task-55 R1-1 — reachability-aware bounded crop target selection. */
class HarvestCropTargetSelectorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nearestInaccessibleFartherReachableSelectsFarther() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState mature = carrots.getStateForAge(carrots.getMaxAge());
        BlockPos nearest = new BlockPos(3, 64, 0);
        BlockPos farther = new BlockPos(6, 64, 0);
        BlockPos approachFar = new BlockPos(5, 64, 0);

        MapCropWorld world = new MapCropWorld();
        world.putFarmlandAndCrop(nearest, mature);
        world.putFarmlandAndCrop(farther, mature);
        world.putSolid(approachFar.below());
        world.putSolid(nearest.west().below());

        ManagedCropDomainContext domain = alwaysManaged();
        HarvestTargetBackoff backoff = new HarvestTargetBackoff();
        AtomicInteger probes = new AtomicInteger();

        HarvestCropTargetSelector.SelectionResult result = HarvestCropTargetSelector.selectAt(
                new BlockPos(0, 64, 0),
                null,
                world,
                new SimpleContainer(9),
                domain,
                backoff,
                0L,
                (mob, approaches) -> {
                    probes.incrementAndGet();
                    if (approaches.contains(approachFar)) {
                        return stubPath(approachFar);
                    }
                    return null;
                });

        assertNotNull(result.target());
        assertEquals(farther, result.target().cropPos());
        assertEquals(2, probes.get());
    }

    @Test
    void allInaccessibleYieldsNoTargetAndBacksOff() {
        CropBlock potatoes = (CropBlock) Blocks.POTATOES;
        BlockState mature = potatoes.getStateForAge(potatoes.getMaxAge());
        BlockPos crop = new BlockPos(4, 64, 0);
        MapCropWorld world = new MapCropWorld();
        world.putFarmlandAndCrop(crop, mature);
        world.putSolid(crop.below().west());

        HarvestTargetBackoff backoff = new HarvestTargetBackoff();
        HarvestCropTargetSelector.SelectionResult result = HarvestCropTargetSelector.selectAt(
                new BlockPos(0, 64, 0),
                null,
                world,
                new SimpleContainer(9),
                alwaysManaged(),
                backoff,
                10L,
                (mob, approaches) -> null);

        assertNull(result.target());
        assertTrue(result.pathProbesUsed() > 0);
        assertTrue(backoff.isActive(crop, 11L));
    }

    @Test
    void backoffSkipsCropUntilExpiry() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState mature = carrots.getStateForAge(carrots.getMaxAge());
        BlockPos crop = new BlockPos(2, 64, 0);
        MapCropWorld world = new MapCropWorld();
        world.putFarmlandAndCrop(crop, mature);

        HarvestTargetBackoff backoff = new HarvestTargetBackoff();
        backoff.recordFailure(crop, 100L);
        HarvestCropTargetSelector.SelectionResult during = HarvestCropTargetSelector.selectAt(
                new BlockPos(0, 64, 0),
                null,
                world,
                new SimpleContainer(9),
                alwaysManaged(),
                backoff,
                150L,
                (mob, approaches) -> stubPath(crop.west()));
        assertNull(during.target());

        world.putSolid(crop.west().below());
        HarvestCropTargetSelector.SelectionResult after = HarvestCropTargetSelector.selectAt(
                new BlockPos(0, 64, 0),
                null,
                world,
                new SimpleContainer(9),
                alwaysManaged(),
                backoff,
                301L,
                (mob, approaches) -> stubPath(crop.west()));
        assertNotNull(after.target());
    }

    @Test
    void pathProbesAreHardBounded() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState mature = carrots.getStateForAge(carrots.getMaxAge());
        MapCropWorld world = new MapCropWorld();
        for (int i = 0; i < 10; i++) {
            BlockPos crop = new BlockPos(5, 64, i * 3);
            world.putFarmlandAndCrop(crop, mature);
            world.putSolid(crop.west().below());
        }

        AtomicInteger probes = new AtomicInteger();
        HarvestCropTargetSelector.SelectionResult result = HarvestCropTargetSelector.selectAt(
                new BlockPos(0, 64, 0),
                null,
                world,
                new SimpleContainer(9),
                alwaysManaged(),
                new HarvestTargetBackoff(),
                0L,
                (mob, approaches) -> {
                    probes.incrementAndGet();
                    return null;
                });

        assertNull(result.target());
        assertEquals(HarvestCropTargetSelector.MAX_PATH_PROBES, probes.get());
        assertEquals(HarvestCropTargetSelector.MAX_PATH_PROBES, result.pathProbesUsed());
    }

    @Test
    void unprobedCandidatesRemainEligibleOnNextScan() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState mature = carrots.getStateForAge(carrots.getMaxAge());
        BlockPos first = new BlockPos(5, 64, 0);
        BlockPos second = new BlockPos(5, 64, 3);
        BlockPos third = new BlockPos(5, 64, 6);
        BlockPos fourth = new BlockPos(5, 64, 9);

        MapCropWorld world = new MapCropWorld();
        for (BlockPos crop : List.of(first, second, third, fourth)) {
            world.putFarmlandAndCrop(crop, mature);
            world.putSolid(crop.west().below());
        }

        HarvestTargetBackoff backoff = new HarvestTargetBackoff();
        long now = 100L;
        AtomicInteger probes = new AtomicInteger();

        HarvestCropTargetSelector.SelectionResult scanOne = HarvestCropTargetSelector.selectAt(
                new BlockPos(0, 64, 0),
                null,
                world,
                new SimpleContainer(9),
                alwaysManaged(),
                backoff,
                now,
                (mob, approaches) -> {
                    probes.incrementAndGet();
                    return null;
                });

        assertNull(scanOne.target());
        assertEquals(HarvestCropTargetSelector.MAX_PATH_PROBES, scanOne.pathProbesUsed());
        assertTrue(backoff.isActive(first, now + 1));
        assertTrue(backoff.isActive(second, now + 1));
        assertTrue(backoff.isActive(third, now + 1));
        assertFalse(backoff.isActive(fourth, now + 1));

        HarvestCropTargetSelector.SelectionResult scanTwo = HarvestCropTargetSelector.selectAt(
                new BlockPos(4, 64, 9),
                null,
                world,
                new SimpleContainer(9),
                alwaysManaged(),
                backoff,
                now + 1,
                (mob, approaches) -> null);

        assertNotNull(scanTwo.target());
        assertEquals(fourth, scanTwo.target().cropPos());
    }

    private static ManagedCropDomainContext alwaysManaged() {
        return ManagedCropDomainContext.forTests(true, List.of(new BlockPos(0, 64, 0)));
    }

    private static Path stubPath(BlockPos target) {
        Node node = new Node(target.getX(), target.getY(), target.getZ());
        return new Path(List.of(node), target, true);
    }

    private static final class MapCropWorld implements CropWorldView {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void put(BlockPos pos, BlockState state) {
            blocks.put(pos.immutable(), state);
        }

        void putSolid(BlockPos pos) {
            put(pos, Blocks.STONE.defaultBlockState());
        }

        void putFarmlandAndCrop(BlockPos crop, BlockState cropState) {
            put(crop.below(), Blocks.FARMLAND.defaultBlockState());
            put(crop, cropState);
            putSolid(crop.below().below());
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return true;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
