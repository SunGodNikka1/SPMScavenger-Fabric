package com.noobk.spmscavenger.mining;

import net.minecraft.world.level.block.state.BlockState;

/**
 * How long a block takes to break. One owner for the physics, because it was inverted in two places.
 *
 * <h2>The defect this replaces</h2>
 *
 * Both deliberate-excavation executors computed {@code 20 / (hardness * toolSpeed)}. But
 * {@code BlockState#getDestroySpeed} returns <b>hardness</b>, so that expression is
 * {@code 1 / hardness} — inverted. Holding the tool constant it produced:
 *
 * <pre>
 * hardness  1  (stone-ish)    → slowest
 * hardness  3  (deepslate)    → faster
 * hardness 50  (obsidian)     → nearly instant
 * </pre>
 *
 * <p>The mob would have laboured over soft blocks and flicked through the hardest ones. The tool
 * term was right; only the hardness relationship was upside down.
 *
 * <p>The correct shape already existed in {@code GatherResourcesGoal}: {@code hardness * 30 /
 * toolSpeed}, mirroring vanilla's calculation without reaching into player-only mining state.
 * Duplicating a formula is how the two drifted; sharing it is the fix, and whether a given caller
 * wants a different minimum animation time stays a tuning decision it passes in.
 */
public final class MiningBreakTiming {

    /** Vanilla's per-hardness tick factor for a correct tool. */
    private static final float TICKS_PER_HARDNESS = 30.0F;

    private MiningBreakTiming() {
    }

    /** Hardness below zero means no tool can ever break it. Safety validators reject these first. */
    public static boolean isUnbreakable(float hardness) {
        return hardness < 0.0F;
    }

    /**
     * @param hardness {@code BlockState#getDestroySpeed} — higher is harder
     * @param toolSpeed multiplier from the best owned tool; non-positive is treated as bare hands
     * @param minimumTicks floor, so a swing is still visible on trivially soft blocks
     * @param maximumTicks ceiling, so nothing becomes an unbounded stall
     */
    public static int breakTicks(
            float hardness, float toolSpeed, int minimumTicks, int maximumTicks) {
        if (isUnbreakable(hardness)) {
            return maximumTicks;
        }
        float speed = toolSpeed <= 0.0F ? 1.0F : toolSpeed;
        int ticks = (int) Math.ceil((hardness * TICKS_PER_HARDNESS) / speed);
        return Math.max(Math.min(minimumTicks, maximumTicks), Math.min(maximumTicks, ticks));
    }

    /** Convenience for callers that already hold the state and a resolved tool speed. */
    public static int breakTicks(
            BlockState state, float hardness, float toolSpeed, int minimumTicks, int maximumTicks) {
        if (state != null && state.isAir()) {
            return minimumTicks;
        }
        return breakTicks(hardness, toolSpeed, minimumTicks, maximumTicks);
    }
}
