package com.noobk.spmscavenger.experience;

/**
 * GAO-0c — overland expedition terminal semantics for episode close events.
 */
public final class ExpeditionEndAttribution {

    public record Semantics(OutcomeClass outcome, ExperienceCause cause, float satisfactionDelta) {}

    private ExpeditionEndAttribution() {}

    public static Semantics completed() {
        return new Semantics(
                OutcomeClass.VOLUNTARY_SUCCESS, ExperienceCause.EXPEDITION_COMPLETE, 0.2f);
    }

    public static Semantics simulationFrontier() {
        return new Semantics(
                OutcomeClass.SIMULATION_FRONTIER, ExperienceCause.SIMULATION_FRONTIER, 0.0f);
    }

    public static Semantics pathFailure() {
        return new Semantics(
                OutcomeClass.EXECUTION_FAILURE, ExperienceCause.MINING_NO_PROGRESS, 0.0f);
    }

    public static Semantics authorityInterrupt() {
        return new Semantics(
                OutcomeClass.AUTHORITY_CANCEL, ExperienceCause.AUTHORITY_CANCEL, 0.0f);
    }

    public static Semantics staleAbandon() {
        return new Semantics(OutcomeClass.VOLUNTARY_ABANDON, ExperienceCause.UNSPECIFIED, 0.0f);
    }
}
