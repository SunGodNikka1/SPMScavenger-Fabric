package com.noobk.spmscavenger.mining;

/**
 * MI-14C2/R1 — how a running required-flag holder relates to arbitration and lease availability.
 */
public enum MoveHolderClassification {
    /** Scavenger chore that must yield under an actionable mining intent. */
    PARTICIPATING_YIELD,
    /** Safety/recovery — non-preemptible and a condition-bound lease pause. */
    PROTECTED_SAFETY_RECOVERY,
    /** Explicit/persistent player authority — non-preemptible and incompatible with mining. */
    PROTECTED_PLAYER_ORDER,
    /** Combat goal — non-preemptible and governed by the existing bounded combat blocker. */
    PROTECTED_COMBAT,
    /** Eating — non-preemptible and governed by the bounded low-food blocker. */
    PROTECTED_LOW_FOOD,
    /** Short host reflex — non-preemptible but bounded rather than condition-bound. */
    PROTECTED_FINITE,
    /** Non-critical host movement (e.g. social follow catch-up) — observable contention. */
    ORDINARY_HOST_WORK,
    /** Unclassified {@code MOVE} owner — must not silently permit healthy execution. */
    UNKNOWN_MOVE_HOLDER,
    /**
     * Cooperative Resource Handoff — a participating goal that holds the executor's flags while
     * doing work the <em>active project wants done</em>.
     *
     * <p>Distinct from {@link #PARTICIPATING_YIELD} (a chore competing with mining) and from
     * {@link #NOT_MOVE_HOLDER} (nothing in the way). Tunnel Search exposes ore so that
     * {@code GatherResourcesGoal} can take it; while gather runs, tunnelling is not blocked, not
     * failing, and not contending — it is being served.
     */
    COOPERATIVE_PROJECT_WORK,
    /** Does not intersect the designated executor's required flags. */
    NOT_MOVE_HOLDER
}
