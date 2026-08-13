package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.mining.MiningExecutionLease;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-GAO-024 — evidence before learning, and one classification of a mining terminal.
 *
 * <h2>The defect</h2>
 *
 * {@code MiningProjectEnd} was classified twice. The shared policy called {@code TOOL_FAILURE} a
 * {@code PROTECTED_INTERRUPT} with cause {@code ENVIRONMENT_BLOCKED} — explicitly not dislike under
 * D-GAO-023 — while {@code PlaceOpinionService} kept its own table charging it {@code −6f}. A
 * 117-cycle assign→{@code CAPABILITY_MISSING}→revoke loop drove one chunk toward the preference
 * floor for a mob that never broke a block.
 */
class MiningTerminalSemanticsTest {

    private static MiningExecutionLease unstarted() {
        return MiningExecutionLease.issued(MiningProjectMode.CONTROLLED_DESCENT, 0L);
    }

    private static MiningExecutionLease started() {
        return unstarted().started(10L);
    }

    // ---- evidence before learning ----

    @Test
    void mustNotHappen_aNeverStartedTerminalTeachesAnything() {
        MiningTerminalSemantics semantics =
                MiningTerminalSemantics.of(MiningProjectEnd.TOOL_FAILURE, unstarted());

        assertFalse(semantics.everStarted());
        assertTrue(semantics.isControlPlaneOnly());
        assertFalse(semantics.mayLearnPreference(),
                "the churn loop's exact shape: assigned, revoked for a missing pickaxe, never ran");
        assertEquals(0.0f, semantics.stress(),
                "and it is not stressful either - nothing happened to the mob");
    }

    @Test
    void mustNotHappen_anAbsentLeaseIsReadAsStarted() {
        MiningTerminalSemantics semantics =
                MiningTerminalSemantics.of(MiningProjectEnd.CAVE_FOUND, null);

        assertFalse(semantics.everStarted(),
                "a missing lease must fail closed - this is what a learning layer would see if it "
                        + "queried the lease after completeProject cleared it");
        assertFalse(semantics.mayLearnPreference());
    }

    /** everStarted is necessary, not sufficient. */
    @Test
    void mustNotHappen_executionAloneMakesEveryOutcomeLearnable() {
        for (MiningProjectEnd end : new MiningProjectEnd[] {
                MiningProjectEnd.PLAYER_ORDER, MiningProjectEnd.COMBAT,
                MiningProjectEnd.TOOL_FAILURE, MiningProjectEnd.LOW_FOOD}) {
            MiningTerminalSemantics semantics = MiningTerminalSemantics.of(end, started());

            assertTrue(semantics.everStarted());
            assertSame(OutcomeClass.PROTECTED_INTERRUPT, semantics.outcome(), end.toString());
            assertFalse(semantics.mayLearnPreference(),
                    end + ": the mob really was digging, but an authority or feasibility stop is "
                            + "still not evidence that the activity or the place is bad "
                            + "(D-GAO-023)");
        }
    }

    @Test
    void mustHappen_aRealOutcomeAfterRealExecutionIsLearnable() {
        assertTrue(MiningTerminalSemantics.of(MiningProjectEnd.CAVE_FOUND, started())
                        .mayLearnPreference(),
                "found a cave while actually mining - that is what learning is for");
        assertTrue(MiningTerminalSemantics.of(MiningProjectEnd.DEMAND_SATISFIED, started())
                .mayLearnPreference());
    }

    // ---- bookkeeping is not gated ----

    @Test
    void mustHappen_theExactTerminalCauseSurvivesEvenWhenUnlearnable() {
        MiningTerminalSemantics semantics =
                MiningTerminalSemantics.of(MiningProjectEnd.TOOL_FAILURE, unstarted());

        assertSame(MiningProjectEnd.TOOL_FAILURE, semantics.end(),
                "trace and debugging keep the precise reason - suppression is of learning, not of "
                        + "the record");
        assertSame(ExperienceCause.ENVIRONMENT_BLOCKED, semantics.cause());
    }

    // ---- one classification, not two ----

    @Test
    void mustHappen_placeLearningAgreesWithActivityLearningOnEveryTerminal() {
        for (MiningProjectEnd end : MiningProjectEnd.values()) {
            MiningTerminalSemantics semantics = MiningTerminalSemantics.of(end, started());
            float magnitude = com.noobk.spmscavenger.opinion.PlaceOpinionService
                    .preferenceMagnitude(end);

            if (!semantics.mayLearnPreference()) {
                continue;   // gated upstream; magnitude is never consulted
            }
            assertTrue(magnitude == 0f || ExperienceOutcomePolicy.mayEmitPreferenceLearning(
                            semantics.outcome()),
                    end + ": place learning must not move preference for an outcome the shared "
                            + "policy refuses - that fork is the defect");
        }
    }

    @Test
    void mustNotHappen_toolFailureCarriesAPlaceMagnitudeAtAll() {
        assertEquals(0f, com.noobk.spmscavenger.opinion.PlaceOpinionService
                        .preferenceMagnitude(MiningProjectEnd.TOOL_FAILURE),
                "a capability outcome is never dislike; it only ever reached the table through a "
                        + "terminal the executor had not begun");
        assertEquals(0f, com.noobk.spmscavenger.opinion.PlaceOpinionService
                .preferenceMagnitude(MiningProjectEnd.EXECUTION_UNAVAILABLE));
        assertEquals(0f, com.noobk.spmscavenger.opinion.PlaceOpinionService
                .preferenceMagnitude(MiningProjectEnd.LEASE_EXPIRED));
    }

    /** Structural: no consumer may re-derive the classification from the raw enum again. */
    @Test
    void mustHappen_thereIsOneOwnerOfTerminalClassification() throws Exception {
        String place = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/opinion/PlaceOpinionService.java"));

        assertFalse(place.contains("preferenceDelta(MiningProjectEnd"),
                "the independent classifier must be gone, not merely unused");
        assertTrue(place.contains("MiningTerminalSemantics"),
                "place learning consumes the shared semantics");
    }

    // ---- one foundational eligibility rule ----

    /**
     * The future trap D-GAO-024 exists to close: an {@code EXECUTION_FAILURE} with a protected cause
     * and enough repetitions would have been eligible for place learning and rejected by activity
     * learning — the same divergence, one terminal later.
     */
    @Test
    void mustHappen_placeAndActivityLearningAgreeOnEveryOutcomeAndCause() {
        for (OutcomeClass outcome : OutcomeClass.values()) {
            for (ExperienceCause cause : ExperienceCause.values()) {
                for (int failures : new int[] {0, 1, 2, 9}) {
                    boolean foundational =
                            ExperienceOutcomePolicy.mayEmitLearning(outcome, cause, failures);
                    if (!foundational) {
                        continue;
                    }
                    assertFalse(ExperienceOutcomePolicy.isSuppressedCause(cause),
                            outcome + "/" + cause + ": a suppressed cause must never be eligible, "
                                    + "whatever its outcome class or repetition count");
                }
            }
        }
    }

    @Test
    void mustNotHappen_aSuppressedCauseTeachesThroughTheFailureChannel() {
        for (ExperienceCause cause : new ExperienceCause[] {
                ExperienceCause.ENVIRONMENT_BLOCKED, ExperienceCause.AUTHORITY_CANCEL,
                ExperienceCause.MINING_PLAYER_ORDER, ExperienceCause.SIMULATION_FRONTIER}) {
            assertFalse(ExperienceOutcomePolicy.mayEmitLearning(
                            OutcomeClass.EXECUTION_FAILURE, cause, 99),
                    cause + ": repetition must not launder a cause that describes why the MOD "
                            + "stopped rather than what the WORLD is like");
        }
    }

    @Test
    void mustHappen_repeatedGenuineFailureIsStillLearnable() {
        assertFalse(ExperienceOutcomePolicy.mayEmitLearning(
                        OutcomeClass.EXECUTION_FAILURE, ExperienceCause.MINING_NO_PROGRESS, 1),
                "one stall is bad luck");
        assertTrue(ExperienceOutcomePolicy.mayEmitLearning(
                        OutcomeClass.EXECUTION_FAILURE, ExperienceCause.MINING_NO_PROGRESS, 2),
                "genuinely attempting an area and repeatedly failing is real evidence about it");
    }

    /**
     * Recorded, not changed: {@code physicalProgressObserved} is deliberately NOT required for
     * learning. A mob that starts, repeatedly attempts a route and never manages to break a block
     * has learned something true about that place; requiring a successful break would make it
     * impossible to learn that a genuinely attempted area is awful to work in.
     */
    @Test
    void documentsThatPhysicalProgressIsNotRequiredForLearning() {
        MiningTerminalSemantics attempted = new MiningTerminalSemantics(
                MiningProjectEnd.NO_PROGRESS, OutcomeClass.EXECUTION_FAILURE,
                ExperienceCause.MINING_NO_PROGRESS, true, false);

        assertTrue(attempted.mayLearnPreference(2),
                "everStarted establishes the project left the bookkeeping plane; terminal semantics "
                        + "then decide what the experience means");
    }
}
