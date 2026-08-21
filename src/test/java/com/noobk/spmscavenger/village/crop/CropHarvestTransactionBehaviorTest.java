package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Task-55 R1-5 — behavioral transaction harness (shared commit kernel). */
class CropHarvestTransactionBehaviorTest {

    private static final BlockPos POS = new BlockPos(4, 64, 4);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void admissionDeniedPerformsNoDropRollOrReplacement() {
        RecordingWorld world = matureCarrotWorld();
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(9),
                POS,
                world.getBlockState(POS),
                false,
                metrics);
        assertEquals(CropHarvestTransaction.CommitOutcome.ABORT, result.outcome());
        assertEquals(0, metrics.dropRolls());
        assertEquals(0, metrics.replacements());
    }

    @Test
    void mobGriefingFalsePerformsNoDropRollOrReplacement() {
        RecordingWorld world = matureCarrotWorld();
        world.mobGriefing = false;
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(9),
                POS,
                world.getBlockState(POS),
                true,
                metrics);
        assertEquals(0, metrics.dropRolls());
        assertEquals(0, metrics.replacements());
    }

    @Test
    void staleCropPerformsNoDropRoll() {
        RecordingWorld world = matureCarrotWorld();
        BlockState stale = world.getBlockState(POS);
        world.set(POS, Blocks.AIR.defaultBlockState());
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(9),
                POS,
                stale,
                true,
                metrics);
        assertEquals(0, metrics.dropRolls());
    }

    @Test
    void wheatWithoutHeldSeedPerformsNoDropRoll() {
        RecordingWorld world = matureCropWorld(Blocks.WHEAT);
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(9),
                POS,
                world.getBlockState(POS),
                true,
                metrics);
        assertEquals(0, metrics.dropRolls());
    }

    @Test
    void acceptedTransactionRollsDropsExactlyOnce() {
        RecordingWorld world = matureCarrotWorld();
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(27),
                POS,
                world.getBlockState(POS),
                true,
                metrics);
        assertEquals(CropHarvestTransaction.CommitOutcome.SUCCESS, result.outcome());
        assertEquals(1, metrics.dropRolls());
        assertEquals(1, metrics.replacements());
    }

    @Test
    void replacementFalseRestoresEscrowAndGrantsNoLoot() {
        RecordingWorld world = matureCropWorld(Blocks.BEETROOTS);
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.BEETROOT_SEEDS, 1));
        world.replaceResult = false;
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                backpack,
                POS,
                world.getBlockState(POS),
                true,
                metrics);
        assertEquals(CropHarvestTransaction.CommitOutcome.ABORT, result.outcome());
        assertEquals(1, metrics.dropRolls());
        assertEquals(1, metrics.replacements());
        assertEquals(1, ContainerMerge.count(backpack, new ItemStack(Items.BEETROOT_SEEDS)));
        assertTrue(result.overflow().isEmpty());
    }

    @Test
    void successfulReplacementVerifiesExactAgeZero() {
        RecordingWorld world = matureCarrotWorld();
        CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(27),
                POS,
                world.getBlockState(POS),
                true,
                new CropHarvestTransaction.CommitMetrics());
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        assertEquals(0, carrots.getAge(world.getBlockState(POS)));
    }

    @Test
    void invariantMismatchGrantsNoStagedLoot() {
        RecordingWorld world = matureCarrotWorld();
        world.mismatchAfterReplace = true;
        SimpleContainer backpack = new SimpleContainer(27);
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                backpack,
                POS,
                world.getBlockState(POS),
                true,
                new CropHarvestTransaction.CommitMetrics());
        assertEquals(CropHarvestTransaction.CommitOutcome.INVARIANT_FAILURE, result.outcome());
        assertTrue(backpack.getItem(0).isEmpty());
    }

    @Test
    void partialInventoryFillConservesItems() {
        RecordingWorld world = matureCarrotWorld();
        world.fixedDrops = List.of(new ItemStack(Items.CARROT, 6));
        SimpleContainer backpack = new SimpleContainer(1);
        backpack.setItem(0, new ItemStack(Items.CARROT, 61));
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                backpack,
                POS,
                world.getBlockState(POS),
                true,
                new CropHarvestTransaction.CommitMetrics());
        assertEquals(CropHarvestTransaction.CommitOutcome.SUCCESS, result.outcome());
        assertEquals(2, result.overflow().get(0).getCount());
        assertEquals(64, backpack.getItem(0).getCount());
    }

    @Test
    void secondActorAfterAgeZeroPerformsNoRollOrMutation() {
        RecordingWorld world = matureCarrotWorld();
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState mature = world.getBlockState(POS);
        world.set(POS, carrots.getStateForAge(0));
        CropHarvestTransaction.CommitMetrics metrics = new CropHarvestTransaction.CommitMetrics();
        CropHarvestTransaction.commitKernel(
                world,
                stubHarvester(),
                new SimpleContainer(9),
                POS,
                mature,
                true,
                metrics);
        assertEquals(0, metrics.dropRolls());
        assertEquals(0, metrics.replacements());
        assertEquals(0, carrots.getAge(world.getBlockState(POS)));
    }

    private static RecordingWorld matureCarrotWorld() {
        return matureCropWorld(Blocks.CARROTS);
    }

    private static RecordingWorld matureCropWorld(Block cropBlock) {
        RecordingWorld world = new RecordingWorld();
        CropBlock crop = (CropBlock) cropBlock;
        BlockState mature = crop.getStateForAge(crop.getMaxAge());
        world.set(POS.below(), Blocks.FARMLAND.defaultBlockState());
        world.set(POS, mature);
        return world;
    }

    private static net.minecraft.world.entity.LivingEntity stubHarvester() {
        return null;
    }

    private static final class RecordingWorld implements CropHarvestTransaction.Operations {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        boolean mobGriefing = true;
        boolean loaded = true;
        boolean replaceResult = true;
        boolean mismatchAfterReplace;
        List<ItemStack> fixedDrops;
        boolean replaced;

        void set(BlockPos pos, BlockState state) {
            blocks.put(pos.immutable(), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (mismatchAfterReplace && replaced && pos.equals(POS)) {
                return Blocks.STONE.defaultBlockState();
            }
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return loaded;
        }

        @Override
        public boolean mobGriefing() {
            return mobGriefing;
        }

        @Override
        public List<ItemStack> rollDrops(
                BlockState state,
                BlockPos pos,
                net.minecraft.world.entity.LivingEntity harvester,
                ItemStack tool) {
            if (fixedDrops != null) {
                return copyStacks(fixedDrops);
            }
            return List.of(new ItemStack(Items.CARROT, 3));
        }

        @Override
        public boolean replaceBlock(BlockPos pos, BlockState state, int flags) {
            if (!replaceResult) {
                return false;
            }
            set(pos, state);
            replaced = true;
            return true;
        }

        private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : stacks) {
                copies.add(stack.copy());
            }
            return copies;
        }
    }
}
