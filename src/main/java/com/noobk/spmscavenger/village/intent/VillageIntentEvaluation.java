package com.noobk.spmscavenger.village.intent;

/** Separates semantic existence from present scheduler admissibility. */
public record VillageIntentEvaluation(
        boolean intentStillExists,
        boolean currentlyAdmissible,
        Cause cause) {

    public enum Cause {
        ACTIVE,
        INTERRUPTED,
        NO_INTENT,
        DEMAND_GONE,
        DEMAND_CHANGED,
        ROUTE_JUSTIFICATION_LOST,
        DESTINATION_INVALID
    }

    static VillageIntentEvaluation active() {
        return new VillageIntentEvaluation(true, true, Cause.ACTIVE);
    }

    static VillageIntentEvaluation interrupted() {
        return new VillageIntentEvaluation(true, false, Cause.INTERRUPTED);
    }

    static VillageIntentEvaluation closed(Cause cause) {
        return new VillageIntentEvaluation(false, false, cause);
    }
}
