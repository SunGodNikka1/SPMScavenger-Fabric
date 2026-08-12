package com.noobk.spmscavenger.opinion;

/**
 * GAO-4R — per-activity adoption state for one director observation tick.
 */
public record ActivityAdmissions(ActivityAdmission explore, ActivityAdmission rest) {

    public ActivityAdmission forActivity(DiscretionaryActivity activity) {
        return switch (activity) {
            case EXPLORE -> explore;
            case REST -> rest;
        };
    }

    public static ActivityAdmissions unavailable() {
        return new ActivityAdmissions(
                ActivityAdmission.executorAbsent(), ActivityAdmission.executorAbsent());
    }

    public static ActivityAdmissions of(ActivityAdmission explore, ActivityAdmission rest) {
        return new ActivityAdmissions(explore, rest);
    }
}
