package com.noobk.spmscavenger.goal;

/** Pure limits for movement-first environmental escape and its last-resort block removal. */
final class EnvironmentalEscapePolicy {

    private EnvironmentalEscapePolicy() {
    }

    /**
     * Whether an escape incident survives a moment of reading "not trapped".
     *
     * <p>The trapped predicates are instantaneous and extremely twitchy: `isInWall()` tests a box
     * **one micrometre tall** at eye height, and `isInPowderSnow` clears the moment the mob's feet
     * rise. Any movement - a jump, a shove, being pushed while fleeing - flickers them false for a
     * tick. Without hysteresis, that single tick wipes the incident and restarts the grace timer, so
     * a mob can be stuck indefinitely and never accumulate the few consecutive ticks that would let
     * it break out. The incident therefore ends only after a *sustained* clear.
     */
    static boolean incidentSurvivesClear(boolean incidentActive, int clearStreak, int clearTicks) {
        return incidentActive && clearStreak < clearTicks;
    }

    static boolean mayBreakEntrappingBlock(
            boolean enabled,
            boolean mobGriefing,
            boolean intersectsMob,
            boolean hasBlockEntity,
            boolean deniedByTag,
            boolean naturalOrExplicitlyAllowed,
            float destroySpeed,
            float maximumDestroySpeed,
            int blocksAlreadyBroken,
            int maximumBlocks,
            int trappedTicks,
            int movementGraceTicks) {
        return enabled
                && mobGriefing
                && intersectsMob
                && !hasBlockEntity
                && !deniedByTag
                && naturalOrExplicitlyAllowed
                && destroySpeed >= 0.0F
                && destroySpeed <= maximumDestroySpeed
                && blocksAlreadyBroken < maximumBlocks
                && trappedTicks >= movementGraceTicks;
    }
}
