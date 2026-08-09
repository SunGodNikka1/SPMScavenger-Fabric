package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;

/**
 * MI-7B+C — tracks natural-descent search effort for one descent-pressure cycle.
 */
public final class NaturalDescentSearchState {

    private MiningBudget budget = MiningBudget.naturalDescentSearchDefaults();
    private MiningBudgetUsage usage = MiningBudgetUsage.EMPTY;
    private BlockPos anchor = BlockPos.ZERO;
    private int anchorY;
    private boolean active;

    public void beginSearch(BlockPos mobPos) {
        active = true;
        anchor = mobPos.immutable();
        anchorY = mobPos.getY();
        usage = MiningBudgetUsage.EMPTY;
        budget = MiningBudget.naturalDescentSearchDefaults();
    }

    public void reset() {
        active = false;
        usage = MiningBudgetUsage.EMPTY;
        anchor = BlockPos.ZERO;
        anchorY = 0;
    }

    public boolean isActive() {
        return active;
    }

    public MiningBudget budget() {
        return budget;
    }

    public MiningBudgetUsage usage() {
        return usage;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public void recordTick() {
        if (!active) {
            return;
        }
        usage = usage.withTick();
    }

    public void recordFailure() {
        if (!active) {
            return;
        }
        usage = usage.withFailedStep();
    }

    public void recordPosition(BlockPos mobPos) {
        if (!active) {
            return;
        }
        int dx = mobPos.getX() - anchor.getX();
        int dz = mobPos.getZ() - anchor.getZ();
        int horizontal = (int) Math.sqrt(dx * dx + dz * dz);
        int vertical = Math.max(0, anchorY - mobPos.getY());
        usage = usage.withProgress(horizontal, vertical);
    }
}
