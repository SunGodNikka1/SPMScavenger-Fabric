package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 43 / GAO-4R1 — adoption is not continuation, and yield is generic.
 *
 * <h2>The falsification case</h2>
 *
 * <pre>
 * EXPLORE  installed=true  adoptable=false  blocker=SCAN_COOLDOWN
 *          running=true    continuable=true
 * → the incumbent participates using its REAL utility
 * </pre>
 *
 * Before this task an adoption failure deleted the incumbent from candidate scoring, so a
 * challenger only had to beat nothing. The mob abandoned a live expedition to a rival that would
 * have lost a fair comparison.
 */
class Task43ContinuationRetentionTest {

    private static ActivityAdmissions exploreOnCooldown() {
        return ActivityAdmissions.of(
                ActivityAdmission.blocked(true, ActivityAdoptionBlocker.SCAN_COOLDOWN, "adoption cooldown"),
                ActivityAdmission.ready(true));
    }

    // ---- D-GAO-050: the two questions are independent ----

    @Test
    void mustHappen_adoptionAndContinuationAreSeparatelyObservable() {
        ActivityAdmission admission = ActivityAdmission.blocked(
                true, ActivityAdoptionBlocker.SCAN_COOLDOWN, "adoption cooldown");
        ActivityContinuation continuation = ActivityContinuation.valid();

        assertTrue(admission.executorPresent(), "installed");
        assertFalse(admission.adoptionReady(), "not adoptable");
        assertTrue(continuation.continuable(), "but the running execution is fine");

        assertNotEquals(admission.adoptionReady(), continuation.continuable(),
                "the healthy runtime state Task 43 exists to represent");
    }

    @Test
    void mustNotHappen_continuationIsManufacturedFromAdoption() {
        // A blocked adoption says nothing about continuation, and vice versa. If continuation were
        // derived from adoptionReady these could never disagree, and the retention branch would be
        // unreachable - which is exactly the state the framework shipped in.
        ActivityContinuations running = ActivityContinuations.of(
                ActivityContinuation.valid(), ActivityContinuation.notRunning());

        assertTrue(running.forActivity(DiscretionaryActivity.EXPLORE).continuable());
        assertFalse(running.forActivity(DiscretionaryActivity.REST).continuable());
        assertSame(ActivityContinuation.ContinuationBlocker.NOT_RUNNING,
                running.forActivity(DiscretionaryActivity.REST).blocker(),
                "nothing running is not the same as running-and-broken");
    }

    @Test
    void mustHappen_continuationBlockersAreDistinctFromAdoptionBlockers() {
        // Reusing ActivityAdoptionBlocker would have re-merged the two questions in the type system.
        for (ActivityContinuation.ContinuationBlocker blocker
                : ActivityContinuation.ContinuationBlocker.values()) {
            assertNotEquals("SCAN_COOLDOWN", blocker.name(),
                    "continuation cannot fail for an adoption reason");
        }
        assertSame(ActivityContinuation.ContinuationBlocker.CLAIM_LAPSED,
                ActivityContinuation.invalid(
                                ActivityContinuation.ContinuationBlocker.CLAIM_LAPSED, "gone")
                        .blocker());
    }

    @Test
    void mustHappen_anInvalidContinuationCannotHideBehindAdoption() {
        ActivityContinuation ended = ActivityContinuation.invalid(
                ActivityContinuation.ContinuationBlocker.EXECUTION_ENDED, "expedition expired");

        assertFalse(ended.continuable(),
                "retention is not immunity - an incumbent whose execution ended is not retained "
                        + "merely because it is the incumbent");
        assertFalse(ended.blockedDetail().isBlank(), "and the reason is inspectable");
    }

    // ---- D-GAO-051: one generic, identity-bound contract ----

    @Test
    void mustHappen_yieldIsGenericAcrossAnyIncumbentAndChallenger() {
        DiscretionaryIntent incumbent = DiscretionaryIntent.pending(
                1L, DiscretionaryActivity.EXPLORE, 50f, 10f, 100L);
        YieldRequest request =
                YieldRequest.of(incumbent, DiscretionaryActivity.REST, 1L, 100L);

        assertSame(DiscretionaryActivity.EXPLORE, request.incumbentActivity());
        assertSame(DiscretionaryActivity.REST, request.challengerActivity());
        assertTrue(request.isFor(DiscretionaryActivity.EXPLORE));
        assertFalse(request.isFor(DiscretionaryActivity.REST),
                "the request is about the incumbent, not the challenger");
    }

    @Test
    void mustNotHappen_aStaleRequestYieldsAReplacementIntent() {
        DiscretionaryIntent original = DiscretionaryIntent.pending(
                1L, DiscretionaryActivity.EXPLORE, 50f, 10f, 100L);
        YieldRequest request =
                YieldRequest.of(original, DiscretionaryActivity.REST, 1L, 100L);

        DiscretionaryIntent replacement = DiscretionaryIntent.pending(
                2L, DiscretionaryActivity.EXPLORE, 55f, 10f, 120L);

        assertTrue(request.appliesTo(original, 120L), "still valid for the execution it named");
        assertFalse(request.appliesTo(replacement, 120L),
                "same activity, same mob, different execution - a boolean could not tell these "
                        + "apart, which is why identity binding replaced it");
    }

    @Test
    void mustNotHappen_anUnansweredRequestLingersForever() {
        DiscretionaryIntent incumbent = DiscretionaryIntent.pending(
                1L, DiscretionaryActivity.REST, 50f, 10f, 100L);
        YieldRequest request =
                YieldRequest.of(incumbent, DiscretionaryActivity.EXPLORE, 1L, 100L);

        assertFalse(request.expired(100L + YieldRequest.LIFETIME_TICKS - 1));
        assertTrue(request.expired(100L + YieldRequest.LIFETIME_TICKS),
                "an unanswered request is declined, not held open");
        assertFalse(request.appliesTo(incumbent, 100L + YieldRequest.LIFETIME_TICKS),
                "and an expired request cannot yield even the execution it named");
    }

    @Test
    void mustNotHappen_yieldBecomesMovementAuthority() {
        DiscretionaryIntent incumbent = DiscretionaryIntent.pending(
                1L, DiscretionaryActivity.EXPLORE, 50f, 10f, 100L);
        YieldRequest request =
                YieldRequest.of(incumbent, DiscretionaryActivity.REST, 1L, 100L);

        // The contract carries identity and intent only. Nothing here stops, moves or re-targets an
        // executor - it communicates a decision, and the executor still owns reaching a safe finite
        // yield point and reporting lifecycle truth.
        assertEquals(1L, request.originDecisionId());
        assertEquals(100L, request.requestedAt());
        assertTrue(request.expiresAt() > request.requestedAt());
    }

    // ---- the shape production must be able to represent ----

    @Test
    void mustHappen_theFalsificationCaseIsRepresentable() {
        ActivityAdmissions admissions = exploreOnCooldown();
        ActivityContinuations continuations = ActivityContinuations.of(
                ActivityContinuation.valid(), ActivityContinuation.notRunning());

        ActivityAdmission explore = admissions.forActivity(DiscretionaryActivity.EXPLORE);
        ActivityContinuation running = continuations.forActivity(DiscretionaryActivity.EXPLORE);

        assertTrue(explore.executorPresent());
        assertFalse(explore.adoptionReady());
        assertSame(ActivityAdoptionBlocker.SCAN_COOLDOWN, explore.blocker());
        assertTrue(running.continuable(),
                "EXPLORE installed, not adoptable, running and continuable - the incumbent must "
                        + "participate with its real utility rather than -Infinity");
    }

    /** Production must supply real snapshots; defaulting to none() is how the defect stayed live. */
    @Test
    void mustHappen_productionSuppliesRealContinuations() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExplorationActivityGoal.java"));

        assertTrue(source.contains("inspectContinuation("),
                "the observer must ask the executors, not default to ActivityContinuations.none()");
        assertTrue(source.contains("ActivityContinuations.of("),
                "and pass them into the director tick");
        assertFalse(source.contains("ActivityContinuations.none()"),
                "no production path may fall back to 'nothing is running'");
    }
}
