package com.noobk.spmscavenger.village.compost;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * COMMIT primitive — vanilla {@link ComposterBlock#insertItem} with single backpack debit owner.
 */
public final class CompostTransaction {

    public enum CommitOutcome {
        ABORT,
        COMMITTED
    }

    public record CommitResult(CommitOutcome outcome, int levelBefore, int levelAfter) {}

    private CompostTransaction() {}

    public static CommitResult commit(
            ServerLevel level,
            Mob mob,
            Container backpack,
            int slot,
            BlockPos composterPos) {
        if (level == null || mob == null || backpack == null || composterPos == null) {
            return abort();
        }
        if (slot < 0 || slot >= backpack.getContainerSize()) {
            return abort();
        }
        ItemStack slotStack = backpack.getItem(slot);
        if (slotStack.isEmpty() || !CompostMechanicalEligibility.isCompostable(slotStack)) {
            return abort();
        }
        BlockState state = level.getBlockState(composterPos);
        if (!CompostMechanicalEligibility.canAcceptInput(state)) {
            return abort();
        }
        int levelBefore = state.getValue(ComposterBlock.LEVEL);
        ItemStack insertion = slotStack.copyWithCount(1);
        int countBefore = insertion.getCount();
        BlockState next = ComposterBlock.insertItem(mob, state, level, insertion, composterPos);
        if (insertion.getCount() >= countBefore) {
            return abort();
        }
        slotStack.shrink(1);
        if (slotStack.isEmpty()) {
            backpack.setItem(slot, ItemStack.EMPTY);
        }
        int levelAfter = next.getValue(ComposterBlock.LEVEL);
        return new CommitResult(CommitOutcome.COMMITTED, levelBefore, levelAfter);
    }

    private static CommitResult abort() {
        return new CommitResult(CommitOutcome.ABORT, -1, -1);
    }
}
