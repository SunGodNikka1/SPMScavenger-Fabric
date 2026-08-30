package com.noobk.spmscavenger.validation;

/** Pure lifecycle gate between instantaneous fixture attachment and V4-G bootstrap. */
final class V4StartupStabilityGate {

    enum Verdict { WAITING, PASS, FIXTURE_FAILURE }

    record Assessment(Verdict verdict, String reason) {
    }

    private V4StartupStabilityGate() {
    }

    static Assessment evaluate(
            long creationTick,
            long currentTick,
            long deadlineTick,
            int subjectTickCountAtCreation,
            int currentSubjectTickCount,
            boolean subjectAttachedAndAlive,
            boolean traderAttachedAndAlive,
            boolean helperAttachedAndAlive) {
        if (!subjectAttachedAndAlive) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "subject not attached/alive before startup stability");
        }
        if (!traderAttachedAndAlive) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "trader not attached/alive before startup stability");
        }
        if (!helperAttachedAndAlive) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "helper not attached/alive before startup stability");
        }
        boolean laterServerTick = currentTick > creationTick;
        boolean subjectActuallyTicked = currentSubjectTickCount > subjectTickCountAtCreation;
        if (laterServerTick && subjectActuallyTicked) {
            return new Assessment(Verdict.PASS,
                    "first normal subject/server tick survived");
        }
        if (currentTick >= deadlineTick) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "startup stability deadline elapsed without a normal subject tick");
        }
        return new Assessment(Verdict.WAITING,
                "waiting for first normal subject/server tick");
    }
}
