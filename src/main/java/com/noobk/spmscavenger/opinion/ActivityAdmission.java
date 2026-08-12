package com.noobk.spmscavenger.opinion;

/**
 * GAO-4R — executor installed vs adoptable at decision time.
 */
public record ActivityAdmission(
        boolean executorPresent,
        boolean adoptionReady,
        ActivityAdoptionBlocker blocker,
        String detail) {

    public ActivityAdmission {
        blocker = blocker == null ? ActivityAdoptionBlocker.READY : blocker;
        detail = detail == null ? "" : detail;
    }

    public static ActivityAdmission ready(boolean executorPresent) {
        return new ActivityAdmission(executorPresent, true, ActivityAdoptionBlocker.READY, "");
    }

    public static ActivityAdmission blocked(
            boolean executorPresent, ActivityAdoptionBlocker blocker, String detail) {
        return new ActivityAdmission(executorPresent, false, blocker, detail);
    }

    public static ActivityAdmission executorAbsent() {
        return new ActivityAdmission(false, false, ActivityAdoptionBlocker.EXECUTOR_DISABLED, "");
    }

    /** Trace / inspector detail for suppressed candidates. */
    public String suppressionDetail() {
        if (adoptionReady || blocker == ActivityAdoptionBlocker.READY) {
            return "";
        }
        return detail.isBlank() ? blocker.name() : blocker.name() + " — " + detail;
    }
}
