package com.noobk.spmscavenger.village.crop;

import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Atomic mature→age-0 crop harvest transaction (task-55). No {@code destroyBlock}, no
 * {@code MandatoryOwnership} publisher.
 */
public final class CropHarvestTransaction {

    public enum CommitOutcome {
        SUCCESS,
        ABORT,
        INVARIANT_FAILURE
    }

    public record CommitResult(CommitOutcome outcome, List<ItemStack> overflow) {
        public static CommitResult abort() {
            return new CommitResult(CommitOutcome.ABORT, List.of());
        }

        public static CommitResult invariantFailure() {
            return new CommitResult(CommitOutcome.INVARIANT_FAILURE, List.of());
        }

        public static CommitResult success(List<ItemStack> overflow) {
            return new CommitResult(CommitOutcome.SUCCESS, overflow);
        }
    }

    /** Test-visible commit counters (task-55 R1-5). */
    public static final class CommitMetrics {
        private int dropRolls;
        private int replacements;

        public int dropRolls() {
            return dropRolls;
        }

        public int replacements() {
            return replacements;
        }
    }

    interface Operations {
        BlockState getBlockState(BlockPos pos);

        boolean isLoaded(BlockPos pos);

        boolean mobGriefing();

        List<ItemStack> rollDrops(
                BlockState state,
                BlockPos pos,
                LivingEntity harvester,
                ItemStack tool);

        boolean replaceBlock(BlockPos pos, BlockState state, int flags);
    }

    private record PlantingUnit(ItemStack stack, boolean fromInventory) {
    }

    private CropHarvestTransaction() {
    }

  /**
     * COMMIT path — caller must have finished WINDUP. Revalidates every deterministic precondition
     * immediately before the single drop roll.
     */
    public static CommitResult commit(
            ServerLevel level,
            LivingEntity harvester,
            Container backpack,
            BlockPos pos,
            BlockState expectedMature,
            boolean admissionPermits) {
        return commitKernel(
                new ServerLevelOperations(level),
                harvester,
                backpack,
                pos,
                expectedMature,
                admissionPermits,
                null);
    }

    static CommitResult commitKernel(
            Operations world,
            LivingEntity harvester,
            Container backpack,
            BlockPos pos,
            BlockState expectedMature,
            boolean admissionPermits,
            @Nullable CommitMetrics metrics) {
        if (!admissionPermits) {
            return CommitResult.abort();
        }
        if (!world.mobGriefing()) {
            return CommitResult.abort();
        }
        if (pos == null || !world.isLoaded(pos)) {
            return CommitResult.abort();
        }
        BlockState current = world.getBlockState(pos);
        if (!current.equals(expectedMature)
                || !CropReplantSemantics.isMature(current)
                || !CropReplantSemantics.supportedCrop(current)) {
            return CommitResult.abort();
        }
        if (!(world.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) {
            return CommitResult.abort();
        }
        if (!HarvestCandidatePolicy.deterministicReplantFeasible(current, backpack)) {
            return CommitResult.abort();
        }

        if (metrics != null) {
            metrics.dropRolls++;
        }
        List<ItemStack> stagedDrops = world.rollDrops(
                current,
                pos,
                harvester,
                harvester == null ? ItemStack.EMPTY : harvester.getMainHandItem());

        PlantingUnit unit = choosePlantingUnit(current, stagedDrops, backpack);
        if (unit == null) {
            return CommitResult.abort();
        }

        ItemStack escrow = ItemStack.EMPTY;
        if (unit.fromInventory()) {
            Item template = unit.stack().getItem();
            int removed = ContainerMerge.remove(backpack, new ItemStack(template), 1);
            if (removed != 1) {
                return CommitResult.abort();
            }
            escrow = unit.stack().copy();
        }

        BlockState ageZero = CropReplantSemantics.ageZero(current);
        if (metrics != null) {
            metrics.replacements++;
        }
        boolean setOk = world.replaceBlock(pos, ageZero, Block.UPDATE_ALL);
        if (!setOk) {
            restoreEscrow(backpack, escrow);
            return CommitResult.abort();
        }

        BlockState after = world.getBlockState(pos);
        if (!after.equals(ageZero)) {
            restoreEscrow(backpack, escrow);
            SpmScavenger.LOGGER.error(
                    "[spmscavenger] Crop harvest invariant failure at {}: expected {} but found {}",
                    pos, ageZero, after);
            return CommitResult.invariantFailure();
        }

        consumePlantingUnit(unit, stagedDrops);
        List<ItemStack> overflow = bankDrops(harvester, backpack, current, stagedDrops);
        return CommitResult.success(overflow);
    }

    private static PlantingUnit choosePlantingUnit(
            BlockState cropState,
            List<ItemStack> stagedDrops,
            Container backpack) {
        for (ItemStack drop : stagedDrops) {
            if (CropReplantSemantics.isReplantMaterial(cropState, drop) && !drop.isEmpty()) {
                return new PlantingUnit(drop.copyWithCount(1), false);
            }
        }
        Item planting = CropReplantSemantics.plantingItem(cropState);
        if (ContainerMerge.count(backpack, new ItemStack(planting)) >= 1) {
            return new PlantingUnit(new ItemStack(planting), true);
        }
        return null;
    }

    private static void consumePlantingUnit(PlantingUnit unit, List<ItemStack> stagedDrops) {
        if (unit.fromInventory()) {
            return;
        }
        ItemStack template = unit.stack();
        for (ItemStack drop : stagedDrops) {
            if (ItemStack.isSameItemSameComponents(drop, template) && drop.getCount() > 0) {
                drop.shrink(1);
                return;
            }
        }
    }

    private static List<ItemStack> bankDrops(
            LivingEntity harvester,
            Container backpack,
            BlockState cropState,
            List<ItemStack> stagedDrops) {
        List<ItemStack> overflow = new ArrayList<>();
        List<ItemStack> replantSurplus = new ArrayList<>();
        List<ItemStack> foodOutput = new ArrayList<>();
        for (ItemStack drop : stagedDrops) {
            if (drop.isEmpty()) {
                continue;
            }
            if (CropReplantSemantics.isReplantMaterial(cropState, drop)) {
                replantSurplus.add(drop.copy());
            } else if (CropReplantSemantics.isFoodOutput(cropState, drop)) {
                foodOutput.add(drop.copy());
            } else {
                foodOutput.add(drop.copy());
            }
        }
        for (ItemStack stack : replantSurplus) {
            overflow.addAll(deposit(backpack, stack));
        }
        for (ItemStack stack : foodOutput) {
            overflow.addAll(deposit(backpack, stack));
        }
        for (ItemStack stack : overflow) {
            if (harvester instanceof net.minecraft.world.entity.Mob mob) {
                mob.spawnAtLocation(stack);
            }
        }
        return overflow;
    }

    private static List<ItemStack> deposit(Container backpack, ItemStack stack) {
        if (backpack == null) {
            return List.of(stack.copy());
        }
        ItemStack remaining = ContainerMerge.insert(backpack, stack);
        if (remaining.isEmpty()) {
            return List.of();
        }
        return List.of(remaining);
    }

    private static void restoreEscrow(Container backpack, ItemStack escrow) {
        if (backpack == null || escrow.isEmpty()) {
            return;
        }
        ItemStack remaining = ContainerMerge.insert(backpack, escrow);
        if (!remaining.isEmpty()) {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] Crop harvest escrow restore left {} uninserted",
                    remaining);
        }
    }

    private static final class ServerLevelOperations implements Operations {
        private final ServerLevel level;

        private ServerLevelOperations(ServerLevel level) {
            this.level = level;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return level.getBlockState(pos);
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return level.isLoaded(pos);
        }

        @Override
        public boolean mobGriefing() {
            return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        }

        @Override
        public List<ItemStack> rollDrops(
                BlockState state,
                BlockPos pos,
                LivingEntity harvester,
                ItemStack tool) {
            return Block.getDrops(state, level, pos, null, harvester, tool);
        }

        @Override
        public boolean replaceBlock(BlockPos pos, BlockState state, int flags) {
            return level.setBlock(pos, state, flags);
        }
    }
}
