package com.noobk.spmscavenger.opinion;

/**
 * Task 43 item 8 — why a candidate was in, or out of, the comparison at decision time.
 *
 * <h2>Why copied primitives</h2>
 *
 * Holding live {@link ActivityAdmission} / {@link ActivityContinuation} references would make the
 * trace re-read state that has since moved on, so an inspector would answer "why was EXPLORE kept?"
 * with whatever is true now. This is a snapshot: enums, booleans and strings captured at the moment
 * the decision was taken.
 *
 * <h2>Why it earns its place</h2>
 *
 * {@code RUNNING_INTENT_RETAINED} says what happened, not why it was legal. The Task-43 case is
 * exactly the one that needs explaining — an activity that is <em>not adoptable</em> nevertheless
 * competing — and without this the reviewer has to reconstruct it from admission state that no
 * longer holds:
 *
 * <pre>
 * EXPLORE  utility=31.2  executor=yes
 *          adoption: ready=no  blocker=SCAN_COOLDOWN
 *          incumbent=yes  continuation: valid=yes
 *          → ELIGIBLE, retainedByContinuation=yes
 * </pre>
 *
 * <p>Generic by construction: a third activity supplies its own admission and continuation
 * snapshots and needs no new fields.
 *
 * @param executorPresent whether an executor was installed at all
 * @param adoptionReady whether a <em>new</em> execution could have begun
 * @param adoptionBlocker why not, when not
 * @param runningIncumbent whether this activity was the running incumbent
 * @param continuable whether the running execution could continue
 * @param continuationBlocker why not, when not
 * @param retainedByContinuation whether it stayed in the comparison *because* it was a continuable
 *     incumbent despite failing adoption — the D-GAO-050 path, made visible
 */
public record ExecutionEvidence(
        boolean executorPresent,
        boolean adoptionReady,
        ActivityAdoptionBlocker adoptionBlocker,
        String adoptionDetail,
        boolean runningIncumbent,
        boolean continuable,
        ActivityContinuation.ContinuationBlocker continuationBlocker,
        String continuationDetail,
        boolean retainedByContinuation) {

    public ExecutionEvidence {
        adoptionBlocker = adoptionBlocker == null ? ActivityAdoptionBlocker.READY : adoptionBlocker;
        continuationBlocker = continuationBlocker == null
                ? ActivityContinuation.ContinuationBlocker.NOT_RUNNING
                : continuationBlocker;
        adoptionDetail = adoptionDetail == null ? "" : adoptionDetail;
        continuationDetail = continuationDetail == null ? "" : continuationDetail;
    }

    public static ExecutionEvidence of(
            ActivityAdmission admission,
            ActivityContinuation continuation,
            boolean runningIncumbent,
            boolean retainedByContinuation) {
        ActivityAdmission a = admission == null ? ActivityAdmission.executorAbsent() : admission;
        ActivityContinuation c =
                continuation == null ? ActivityContinuation.notRunning() : continuation;
        return new ExecutionEvidence(
                a.executorPresent(),
                a.adoptionReady(),
                a.blocker(),
                a.detail(),
                runningIncumbent,
                c.continuable(),
                c.blocker(),
                c.detail(),
                retainedByContinuation);
    }

    /** The state Task 43 exists to make legal and legible. */
    public boolean isRetainedDespiteBlockedAdoption() {
        return retainedByContinuation && !adoptionReady && continuable;
    }
}
