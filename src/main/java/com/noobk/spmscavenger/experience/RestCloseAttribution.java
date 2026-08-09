package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.IntentLifecycle;
import com.noobk.spmscavenger.opinion.InvalidationCause;

/**
 * GAO-4 — authoritative REST claim close semantics for Director termination and experience
 * attribution (D-GAO-023). Authority/combat/mandatory interruption must not read as voluntary REST
 * success or dislike.
 */
public final class RestCloseAttribution {

    /**
     * @param satisfactionDelta terminal satisfaction pulse for affect routing only; learning sign is
     *                          controlled separately by {@link OutcomeClass} + {@link ExperienceCause}
     */
    public record Semantics(
            IntentLifecycle directorLifecycle,
            InvalidationCause directorCause,
            OutcomeClass experienceOutcome,
            ExperienceCause experienceCause,
            float satisfactionDelta) {}

    private RestCloseAttribution() {}

    public static Semantics forReason(RestCloseReason reason) {
        return switch (reason) {
            case TIMEOUT -> new Semantics(
                    IntentLifecycle.SUCCEEDED,
                    InvalidationCause.NONE,
                    OutcomeClass.VOLUNTARY_SUCCESS,
                    ExperienceCause.REST_SESSION_CLOSE,
                    0.1f);
            case COMBAT -> new Semantics(
                    IntentLifecycle.INVALIDATED,
                    InvalidationCause.COMBAT_TARGET,
                    OutcomeClass.PROTECTED_INTERRUPT,
                    ExperienceCause.REST_COMBAT,
                    0.0f);
            case MANDATORY_WORK -> new Semantics(
                    IntentLifecycle.INVALIDATED,
                    InvalidationCause.MANDATORY_AUTHORITY,
                    OutcomeClass.PROTECTED_INTERRUPT,
                    ExperienceCause.REST_MANDATORY_WORK,
                    0.0f);
            case PLAYER_ORDER -> new Semantics(
                    IntentLifecycle.INVALIDATED,
                    InvalidationCause.PLAYER_COMMAND,
                    OutcomeClass.AUTHORITY_CANCEL,
                    ExperienceCause.AUTHORITY_CANCEL,
                    0.0f);
            case CHUNK_UNLOAD -> new Semantics(
                    IntentLifecycle.INVALIDATED,
                    InvalidationCause.UNLOAD_FREEZE,
                    OutcomeClass.PROTECTED_INTERRUPT,
                    ExperienceCause.PROTECTED_INTERRUPT,
                    0.0f);
            case FIRE_INVALID, VALIDITY_LOST -> new Semantics(
                    IntentLifecycle.INTERRUPTED,
                    InvalidationCause.NONE,
                    OutcomeClass.ENVIRONMENT_UNAVAILABLE,
                    ExperienceCause.ENVIRONMENT_BLOCKED,
                    0.0f);
            case MOB_LEFT_RADIUS -> new Semantics(
                    IntentLifecycle.INTERRUPTED,
                    InvalidationCause.NONE,
                    OutcomeClass.VOLUNTARY_ABANDON,
                    ExperienceCause.REST_LEFT_RADIUS,
                    0.0f);
        };
    }
}
