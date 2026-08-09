package com.noobk.spmscavenger.mining;

/**
 * MI-14C2-R2 — how a running {@code MOVE} holder relates to mining execution authority.
 */
public enum MoveHolderClassification {
    /** Scavenger chore that must yield under an actionable mining intent. */
    PARTICIPATING_YIELD,
    /** Combat, survival, recovery, or explicit command — mining must not forcibly preempt. */
    PROTECTED_INTERRUPT,
    /** Non-critical host movement (e.g. social follow catch-up) — observable contention. */
    ORDINARY_HOST_WORK,
    /** Unclassified {@code MOVE} owner — must not silently permit healthy execution. */
    UNKNOWN_MOVE_HOLDER,
    /** Not a running {@code MOVE} holder relevant to contention. */
    NOT_MOVE_HOLDER
}
