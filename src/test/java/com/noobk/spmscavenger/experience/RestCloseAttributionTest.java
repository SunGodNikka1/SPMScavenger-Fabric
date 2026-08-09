package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.IntentLifecycle;
import com.noobk.spmscavenger.opinion.InvalidationCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RestCloseAttributionTest {

    @Test
    void timeoutIsSuccessfulCompletion() {
        RestCloseAttribution.Semantics semantics =
                RestCloseAttribution.forReason(RestCloseReason.TIMEOUT);
        assertEquals(IntentLifecycle.SUCCEEDED, semantics.directorLifecycle());
        assertEquals(InvalidationCause.NONE, semantics.directorCause());
        assertEquals(OutcomeClass.VOLUNTARY_SUCCESS, semantics.experienceOutcome());
        assertEquals(ExperienceCause.REST_SESSION_CLOSE, semantics.experienceCause());
    }

    @ParameterizedTest
    @EnumSource(
            value = RestCloseReason.class,
            names = {"COMBAT", "MANDATORY_WORK", "PLAYER_ORDER", "CHUNK_UNLOAD"})
    void protectedInterruptionsAreNotSuccess(RestCloseReason reason) {
        RestCloseAttribution.Semantics semantics = RestCloseAttribution.forReason(reason);
        assertNotEquals(IntentLifecycle.SUCCEEDED, semantics.directorLifecycle());
        assertNotEquals(OutcomeClass.VOLUNTARY_SUCCESS, semantics.experienceOutcome());
    }

    @Test
    void combatMapsToProtectedInterruptSemantics() {
        RestCloseAttribution.Semantics semantics =
                RestCloseAttribution.forReason(RestCloseReason.COMBAT);
        assertEquals(IntentLifecycle.INVALIDATED, semantics.directorLifecycle());
        assertEquals(InvalidationCause.COMBAT_TARGET, semantics.directorCause());
        assertEquals(OutcomeClass.PROTECTED_INTERRUPT, semantics.experienceOutcome());
        assertEquals(ExperienceCause.REST_COMBAT, semantics.experienceCause());
    }

    @Test
    void mandatoryWorkMapsToMandatoryAuthority() {
        RestCloseAttribution.Semantics semantics =
                RestCloseAttribution.forReason(RestCloseReason.MANDATORY_WORK);
        assertEquals(IntentLifecycle.INVALIDATED, semantics.directorLifecycle());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY, semantics.directorCause());
        assertEquals(OutcomeClass.PROTECTED_INTERRUPT, semantics.experienceOutcome());
        assertEquals(ExperienceCause.REST_MANDATORY_WORK, semantics.experienceCause());
    }

    @Test
    void playerOrderMapsToAuthorityCancel() {
        RestCloseAttribution.Semantics semantics =
                RestCloseAttribution.forReason(RestCloseReason.PLAYER_ORDER);
        assertEquals(IntentLifecycle.INVALIDATED, semantics.directorLifecycle());
        assertEquals(InvalidationCause.PLAYER_COMMAND, semantics.directorCause());
        assertEquals(OutcomeClass.AUTHORITY_CANCEL, semantics.experienceOutcome());
        assertEquals(ExperienceCause.AUTHORITY_CANCEL, semantics.experienceCause());
    }

    @ParameterizedTest
    @EnumSource(
            value = RestCloseReason.class,
            names = {"FIRE_INVALID", "VALIDITY_LOST", "CHUNK_UNLOAD"})
    void environmentAndUnloadAreNotSuccess(RestCloseReason reason) {
        RestCloseAttribution.Semantics semantics = RestCloseAttribution.forReason(reason);
        assertNotEquals(IntentLifecycle.SUCCEEDED, semantics.directorLifecycle());
        assertNotEquals(OutcomeClass.VOLUNTARY_SUCCESS, semantics.experienceOutcome());
    }

    @Test
    void leftRadiusIsNeutralAbandonNotFailure() {
        RestCloseAttribution.Semantics semantics =
                RestCloseAttribution.forReason(RestCloseReason.MOB_LEFT_RADIUS);
        assertEquals(IntentLifecycle.INTERRUPTED, semantics.directorLifecycle());
        assertEquals(OutcomeClass.VOLUNTARY_ABANDON, semantics.experienceOutcome());
        assertEquals(ExperienceCause.REST_LEFT_RADIUS, semantics.experienceCause());
        assertEquals(
                0.0f,
                ExperienceOutcomePolicy.preferenceSign(
                        semantics.experienceOutcome(), semantics.experienceCause()));
    }
}
