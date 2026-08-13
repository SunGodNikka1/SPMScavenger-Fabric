package com.noobk.spmscavenger.opinion;

/**
 * D-GAO-050 — whether an <b>already adopted</b> execution remains valid.
 *
 * <h2>Adoption is not continuation</h2>
 *
 * {@link ActivityAdmission} answers <em>may this activity begin a new execution now</em>.
 * This answers <em>may the execution already running keep running</em>. They are different
 * questions and they fail for different reasons: an adoption cooldown, a spent budget or a
 * readiness window says nothing about whether the expedition currently under way is still healthy.
 *
 * <p>Conflating them produced a concrete defect: a running EXPLORE incumbent whose *adoption*
 * cooldown was active was suppressed out of candidate scoring entirely, so a challenger only had to
 * beat nothing rather than beat the incumbent's real utility. The mob abandoned a live expedition to
 * a rival that would have lost a fair comparison.
 *
 * <p>Inspection is <b>bounded and non-mutating</b>: this is asked every observation tick, and an
 * inspector that advanced state or scanned the world would make the question expensive and change
 * the answer by asking it.
 *
 * @param continuable whether the running execution may continue
 * @param blocker why it may not, when it may not
 * @param detail inspector text; never load-bearing
 */
public record ActivityContinuation(
        boolean continuable, ContinuationBlocker blocker, String detail) {

    /** Why an adopted execution can no longer continue. Distinct from adoption blockers. */
    public enum ContinuationBlocker {
        /** Continuing is fine. */
        VALID,
        /** No execution of this activity is running, so there is nothing to continue. */
        NOT_RUNNING,
        /** The executor is gone — config off, goal removed, entity state changed. */
        EXECUTOR_ABSENT,
        /** The claim or session the execution depended on has lapsed. */
        CLAIM_LAPSED,
        /** The executor reported it has finished or given up. */
        EXECUTION_ENDED
    }

    public ActivityContinuation {
        blocker = blocker == null ? ContinuationBlocker.VALID : blocker;
        detail = detail == null ? "" : detail;
    }

    public static ActivityContinuation valid() {
        return new ActivityContinuation(true, ContinuationBlocker.VALID, "");
    }

    public static ActivityContinuation invalid(ContinuationBlocker blocker, String detail) {
        return new ActivityContinuation(false, blocker, detail);
    }

    public static ActivityContinuation notRunning() {
        return new ActivityContinuation(false, ContinuationBlocker.NOT_RUNNING, "");
    }

    public String blockedDetail() {
        if (continuable) {
            return "";
        }
        return detail.isBlank() ? blocker.name() : blocker.name() + " — " + detail;
    }
}
