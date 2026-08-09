package com.noobk.spmscavenger.goal;

/** Pure approximation of vanilla survival block-destruction timing. */
final class MiningPolicy {

    private MiningPolicy() {
    }

    static int requiredTicks(float hardness, float toolSpeed, boolean canHarvest) {
        if (hardness < 0.0F) {
            return Integer.MAX_VALUE;
        }
        if (hardness == 0.0F) {
            return 1;
        }
        float progressPerTick = Math.max(0.0F, toolSpeed) / hardness / (canHarvest ? 30.0F : 100.0F);
        if (progressPerTick <= 0.0F) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil(1.0F / progressPerTick));
    }

    /**
     * Whether breaking this block yields its drops.
     *
     * <p>Vanilla enforces this in {@code ServerPlayerGameMode.destroyBlock} via
     * {@code hasCorrectToolForDrops}, <b>not</b> in the loot table — so a caller that drops
     * unconditionally hands out cobblestone for a bare-handed stone break. The same answer also
     * decides the 30x vs 100x divisor in {@link #requiredTicks}, so both callers share it.
     */
    static boolean dropsAllowed(boolean requiresCorrectTool, boolean toolIsCorrect) {
        return !requiresCorrectTool || toolIsCorrect;
    }

    static int crackStage(int elapsedTicks, int requiredTicks) {
        if (requiredTicks <= 0) {
            return 9;
        }
        return Math.max(0, Math.min(9, (elapsedTicks * 10) / requiredTicks));
    }
}
