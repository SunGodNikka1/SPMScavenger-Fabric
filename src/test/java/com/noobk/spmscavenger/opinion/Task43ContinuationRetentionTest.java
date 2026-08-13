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

    // ---- continuation inspection must not create state by observing ----

    /**
     * D-GAO-050 — an inspector that allocated would make the observer a mutator: asking whether a
     * rest session continues would bring a context into existence for a mob that has none.
     * {@code contextFor(...)} rehydrates or creates; {@code hasLiveRestClaim(...)} does not.
     */
    @Test
    void mustNotHappen_continuationInspectionAllocatesAContext() {
        com.noobk.spmscavenger.experience.OpinionExperienceRegistry.clearAll();
        UUID unseen = UUID.randomUUID();

        boolean live = com.noobk.spmscavenger.experience.OpinionExperienceRegistry
                .hasLiveRestClaim(unseen);

        assertFalse(live, "a mob with no context holds no claim");
        assertEquals(0, com.noobk.spmscavenger.experience.OpinionExperienceRegistry.contextCount(),
                "and asking must not have created one - otherwise every observation tick for every "
                        + "mob in the world allocates, which is RET-1a by another route");
    }

    @Test
    void mustHappen_theRestInspectorUsesTheNonAllocatingQuery() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/CampfireGoal.java"));
        int inspect = source.indexOf("inspectContinuation");
        String body = source.substring(inspect, source.indexOf("inspectAdmission", inspect));
        // Strip comments: the body explains why contextFor is forbidden, and a naive text match
        // trips on the explanation rather than on a call.
        StringBuilder code = new StringBuilder();
        for (String line : body.lines().toList()) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                code.append(line).append(System.lineSeparator());
            }
        }
        body = code.toString();

        assertFalse(body.contains("contextFor("),
                "the continuation inspector must not use the allocating/rehydrating path");
        assertTrue(body.contains("hasLiveRestClaim("),
                "it uses the explicitly non-allocating registry query");
    }

    // ---- D-GAO-051: the executor does not name its successor ----

    @Test
    void mustNotHappen_anExecutorNamesTheChallenger() throws Exception {
        for (String goal : new String[] {"ExploringGoal", "CampfireGoal"}) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/noobk/spmscavenger/goal/" + goal + ".java"));

            assertFalse(source.contains("YieldedForRest") || source.contains("YieldedForExplore"),
                    goal + " still uses a pairwise acknowledgement - one method per ordered pair is "
                            + "the explosion D-GAO-051 exists to prevent, and it forces each "
                            + "executor to know the whole activity set");
            assertTrue(source.contains("onDiscretionaryYielded("),
                    goal + " reports only its own identity and activity");
        }
    }

    // ---- yield causal ownership ----

    /**
     * The origin of a switch is the decision that <b>chose the challenger</b>, not the decision that
     * created the incumbent. Recording the latter would attach the switch to the wrong historical
     * cause — a trace built on it would confidently point at the moment EXPLORE started as the
     * reason REST took over, possibly dozens of decisions earlier.
     */
    @Test
    void mustHappen_theYieldOriginIsTheDecisionThatChoseTheChallenger() {
        DiscretionaryIntent incumbent = DiscretionaryIntent.pending(
                20L, DiscretionaryActivity.EXPLORE, 29f, 10f, 100L);

        YieldRequest request =
                YieldRequest.of(incumbent, DiscretionaryActivity.REST, 87L, 900L);

        assertEquals(87L, request.originDecisionId(),
                "decision #87 compared REST 42 against EXPLORE 29 and chose to switch");
        assertNotEquals(incumbent.decisionId(), request.originDecisionId(),
                "#20 merely created the incumbent - it caused nothing about this switch");
    }

    /** Every way a request ends is a named outcome, captured when it happens. */
    @Test
    void mustHappen_everyYieldTerminationHasADistinctReason() {
        java.util.Set<String> reasons = new java.util.HashSet<>();
        for (DiscretionaryDirectorState.YieldOutcome outcome
                : DiscretionaryDirectorState.YieldOutcome.values()) {
            reasons.add(outcome.name());
        }

        assertTrue(reasons.containsAll(java.util.List.of(
                        "ACKNOWLEDGED", "EXPIRED", "STALE_INCUMBENT",
                        "MANDATORY_INVALIDATION", "SUPERSEDED")),
                "five paths removed a request; each needs its own reason or the trace cannot tell "
                        + "'nobody answered' from 'combat ended it'");
    }

    @Test
    void mustNotHappen_mandatoryAuthorityLeavesARequestHanging() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryDirectorState.java"));
        int at = source.indexOf("private void invalidateAll(");
        String body = source.substring(at, at + 700);

        assertTrue(body.contains("MANDATORY_INVALIDATION"),
                "mandatory authority must end the negotiation at the moment it happens - otherwise "
                        + "a later read reports STALE, which is true but names the wrong cause");
    }

    /** One seam, so a future trace has one call site rather than five that drift. */
    @Test
    void mustHappen_allYieldTerminationGoesThroughOneSeam() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryDirectorState.java"));
        StringBuilder code = new StringBuilder();
        for (String line : source.lines().toList()) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                code.append(line).append(System.lineSeparator());
            }
        }
        long directClears = code.toString().lines()
                .filter(line -> line.contains("yieldRequest = null"))
                .count();

        assertTrue(directClears <= 2,
                "expected the seam plus the reset path only, found " + directClears
                        + " direct clears - each one is a place a causal event can be forgotten");
    }

    // ---- a yield is a transaction, not a per-tick flag ----

    /**
     * Repeated qualifying decisions must not restart the clock. The director observes every 10
     * ticks; raising a fresh request each time turned a bounded 200-tick contract into an immortal
     * sliding timeout, and moved the causal origin to whichever scoring pass ran last.
     */
    @Test
    void mustNotHappen_repeatedDecisionsRefreshTheYieldClock() {
        DiscretionaryIntent incumbent = DiscretionaryIntent.pending(
                20L, DiscretionaryActivity.EXPLORE, 29f, 10f, 100L);

        YieldRequest first = YieldRequest.of(incumbent, DiscretionaryActivity.REST, 87L, 900L);
        // What a naive refresh at the next observation pass would produce.
        YieldRequest refreshed = YieldRequest.of(incumbent, DiscretionaryActivity.REST, 88L, 910L);

        assertNotEquals(first.expiresAt(), refreshed.expiresAt(),
                "documents the drift: a refresh moves the deadline");
        assertNotEquals(first.originDecisionId(), refreshed.originDecisionId(),
                "and moves the cause");
        assertEquals(900L + YieldRequest.LIFETIME_TICKS, first.expiresAt(),
                "the transaction the director must keep instead: one start, one deadline");
    }



    @Test
    void mustHappen_anExpiredTransactionEndsBeforeANewOneBegins() {
        DiscretionaryIntent incumbent = DiscretionaryIntent.pending(
                20L, DiscretionaryActivity.EXPLORE, 29f, 10f, 100L);
        YieldRequest request = YieldRequest.of(incumbent, DiscretionaryActivity.REST, 87L, 900L);

        long past = 900L + YieldRequest.LIFETIME_TICKS;
        assertTrue(request.expired(past));
        assertFalse(request.appliesTo(incumbent, past),
                "an expired request cannot be silently reused - it is recorded EXPIRED, and a new "
                        + "decision must independently choose to start a fresh bounded transaction");
    }
}
