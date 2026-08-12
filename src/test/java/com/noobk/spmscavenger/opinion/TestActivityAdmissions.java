package com.noobk.spmscavenger.opinion;

/** Test helper for GAO-4R admission wiring. */
final class TestActivityAdmissions {

    private TestActivityAdmissions() {
    }

    static ActivityAdmissions bothReady() {
        return ActivityAdmissions.of(ActivityAdmission.ready(true), ActivityAdmission.ready(true));
    }

    static ActivityAdmissions exploreReady(boolean ready) {
        ActivityAdmission explore = ready
                ? ActivityAdmission.ready(true)
                : ActivityAdmission.blocked(
                        true, ActivityAdoptionBlocker.EXPLORE_NOT_READY, "test-not-ready");
        return ActivityAdmissions.of(explore, ActivityAdmission.ready(true));
    }

    static ActivityAdmissions restReady(boolean ready) {
        ActivityAdmission rest = ready
                ? ActivityAdmission.ready(true)
                : ActivityAdmission.blocked(
                        true, ActivityAdoptionBlocker.NO_CAMPFIRE_ITEM, "test-not-ready");
        return ActivityAdmissions.of(ActivityAdmission.ready(true), rest);
    }
}
