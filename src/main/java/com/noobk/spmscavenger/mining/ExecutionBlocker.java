package com.noobk.spmscavenger.mining;

import org.jetbrains.annotations.Nullable;

/**
 * MI-14C1 — why an assigned {@link MiningProject} is not executing right now.
 *
 * <h2>Why the reason matters, not just the fact</h2>
 *
 * A plain time-to-live would delete an assignment without asking why it stalled, which is wrong in
 * both directions: a mob fighting a zombie for twenty seconds should keep its staircase, and a mob
 * that has lost its pickaxe should release it immediately so the systems that can restore a pickaxe
 * are not competing with a project nobody can run.
 *
 * <p>The deadlock this exists to break: {@code canUse} tested config, combat, {@code mobGriefing}
 * and tool capability <em>before</em> looking up the assignment, so any of them failing after
 * assignment returned {@code false} forever while the project stayed {@code RUNNING} — and
 * {@code mayStartControlledDescent} refuses a new assignment whenever one is present.
 */
public enum ExecutionBlocker {

    /** Nothing is in the way; the executor may run. */
    NONE(BlockerClass.NONE, null),

    /** Fighting or being hunted. Bounded, and worth keeping the project across. */
    COMBAT_TARGET(BlockerClass.TEMPORARY, MiningProjectEnd.COMBAT),

    /** Survival interrupt placeholder — director classification deferred; not wired in MI-14C2. */
    LOW_FOOD(BlockerClass.TEMPORARY, MiningProjectEnd.LOW_FOOD),

    /** A bounded host reflex (social reaction, door operation, or combat fallback). */
    HOST_INTERRUPT(BlockerClass.TEMPORARY, MiningProjectEnd.LEASE_EXPIRED),

    /**
     * Observable safety/recovery work owns required scheduler flags. Mining may neither preempt it
     * nor infer failure from its duration; the owning safety system decides when recovery ends.
     */
    SAFETY_RECOVERY(BlockerClass.PROTECTED_PAUSE, null),

    /** An explicit or persistent player command outranks autonomous mining. */
    PLAYER_ORDER(BlockerClass.HARD, MiningProjectEnd.PLAYER_ORDER),

    /** The feature was switched off. Not the mob's problem to wait out. */
    FEATURE_DISABLED(BlockerClass.HARD, MiningProjectEnd.EXECUTION_UNAVAILABLE),

    /** {@code mobGriefing} is off, so no dig can legally happen at all. */
    WORLD_RULE_DISABLED(BlockerClass.HARD, MiningProjectEnd.EXECUTION_UNAVAILABLE),

    /**
     * The capability the mode requires is gone — for controlled descent, any usable pickaxe.
     * Released rather than suspended so tool acquisition can proceed unobstructed.
     */
    CAPABILITY_MISSING(BlockerClass.HARD, MiningProjectEnd.TOOL_FAILURE),

    /** Admissible, but another non-critical goal owns {@code MOVE}. Arbitration is MI-14C2. */
    CONTENTION(BlockerClass.CONTENTION, MiningProjectEnd.LEASE_EXPIRED);

    /** How the control plane should respond, independent of the specific cause. */
    public enum BlockerClass {
        NONE,
        TEMPORARY,
        PROTECTED_PAUSE,
        HARD,
        CONTENTION
    }

    private final BlockerClass blockerClass;
    @Nullable
    private final MiningProjectEnd revocationReason;

    ExecutionBlocker(BlockerClass blockerClass, @Nullable MiningProjectEnd revocationReason) {
        this.blockerClass = blockerClass;
        this.revocationReason = revocationReason;
    }

    public BlockerClass blockerClass() {
        return blockerClass;
    }

    /** The end reason recorded if this blocker ends up revoking the assignment. */
    @Nullable
    public MiningProjectEnd revocationReason() {
        return revocationReason;
    }

    public boolean permitsExecution() {
        return this == NONE;
    }
}
