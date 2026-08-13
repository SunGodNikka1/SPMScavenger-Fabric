package com.noobk.spmscavenger.opinion;

/**
 * D-GAO-050 — per-activity continuation state for one director observation tick.
 *
 * <p>Deliberately shaped like {@link ActivityAdmissions} so the third activity extends both in the
 * same place, rather than continuation becoming an EXPLORE/REST special case bolted onto adoption.
 */
public record ActivityContinuations(
        ActivityContinuation explore,
        ActivityContinuation rest,
        ActivityContinuation social) {

    public ActivityContinuation forActivity(DiscretionaryActivity activity) {
        return switch (activity) {
            case EXPLORE -> explore;
            case REST -> rest;
            case SOCIAL -> social;
        };
    }

    public static ActivityContinuations none() {
        return new ActivityContinuations(
                ActivityContinuation.notRunning(),
                ActivityContinuation.notRunning(),
                ActivityContinuation.notRunning());
    }

    public static ActivityContinuations of(
            ActivityContinuation explore, ActivityContinuation rest) {
        return new ActivityContinuations(explore, rest, ActivityContinuation.notRunning());
    }

    public static ActivityContinuations of(
            ActivityContinuation explore,
            ActivityContinuation rest,
            ActivityContinuation social) {
        return new ActivityContinuations(explore, rest, social);
    }
}
