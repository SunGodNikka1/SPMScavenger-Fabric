package com.noobk.spmscavenger.opinion;

/**
 * GAO-4R — per-activity adoption state for one director observation tick.
 */
public record ActivityAdmissions(
        ActivityAdmission explore, ActivityAdmission rest, ActivityAdmission social) {

    public ActivityAdmission forActivity(DiscretionaryActivity activity) {
        return switch (activity) {
            case EXPLORE -> explore;
            case REST -> rest;
            case SOCIAL -> social;
        };
    }

    public static ActivityAdmissions unavailable() {
        return new ActivityAdmissions(
                ActivityAdmission.executorAbsent(),
                ActivityAdmission.executorAbsent(),
                ActivityAdmission.executorAbsent());
    }

    /**
     * GAO-10 — SOCIAL has no executor until 44D, so it is admitted as {@code executorAbsent}.
     *
     * <p>That is the fail-safe end of a real trade-off. Marking it adoption-ready now would let
     * SOCIAL win a decision and become the incumbent, and an incumbent that can never physically
     * start would block EXPLORE and REST behind it — the Task 43R shape, visible in game as mobs
     * standing still. Suppressed-but-scored costs nothing and still shows the wanting: the decision
     * trace records SOCIAL's real utility alongside its cause.
     */
    public static ActivityAdmissions of(ActivityAdmission explore, ActivityAdmission rest) {
        return new ActivityAdmissions(explore, rest, ActivityAdmission.executorAbsent());
    }

    public static ActivityAdmissions of(
            ActivityAdmission explore, ActivityAdmission rest, ActivityAdmission social) {
        return new ActivityAdmissions(explore, rest, social);
    }
}
