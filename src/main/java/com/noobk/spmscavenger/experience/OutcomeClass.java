package com.noobk.spmscavenger.experience;

/**
 * GAO-0b — whether an event may affect long-term learning and how interrupts are classified.
 *
 * <p>Owned as schema vocabulary in GAO-0b; episode-learning semantics are implemented in GAO-0c.
 */
public enum OutcomeClass {
    VOLUNTARY_SUCCESS,
    VOLUNTARY_ABANDON,
    EXECUTION_FAILURE,
    ENVIRONMENT_UNAVAILABLE,
    SIMULATION_FRONTIER,
    PROTECTED_INTERRUPT,
    AUTHORITY_CANCEL
}
