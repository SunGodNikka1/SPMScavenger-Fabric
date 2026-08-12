package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.opinion.ActivityAdmission;

/** GAO-4R — bounded admission row for inspector wire/UI. */
public record ActivityAdmissionView(
        boolean executorPresent,
        boolean adoptionReady,
        String blocker,
        String detail) {

    private static final ActivityAdmissionView EMPTY = new ActivityAdmissionView(false, false, "", "");

    public ActivityAdmissionView {
        blocker = blocker == null ? "" : blocker;
        detail = detail == null ? "" : detail;
    }

    public static ActivityAdmissionView empty() {
        return EMPTY;
    }

    public static ActivityAdmissionView from(ActivityAdmission admission) {
        if (admission == null) {
            return empty();
        }
        return new ActivityAdmissionView(
                admission.executorPresent(),
                admission.adoptionReady(),
                admission.blocker().name(),
                admission.detail());
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }
}
