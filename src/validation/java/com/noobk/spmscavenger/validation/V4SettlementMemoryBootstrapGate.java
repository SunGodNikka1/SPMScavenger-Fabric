package com.noobk.spmscavenger.validation;

/** Pure validation-side ordering gate; it reads facts and grants no production authority. */
final class V4SettlementMemoryBootstrapGate {

    enum Verdict { WAITING, READY, FIXTURE_FAILURE }

    record Assessment(Verdict verdict, String reason) { }

    private V4SettlementMemoryBootstrapGate() { }

    static Assessment evaluate(
            int rememberedVillageCount,
            boolean homePresent,
            boolean expectedDemandPresent,
            boolean anyDemandPresent,
            boolean traderWithinLocalRadius) {
        if (expectedDemandPresent || anyDemandPresent) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "validation neutral inventory produced a pre-warmup demand");
        }
        if (homePresent) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "HOME became present before warm-up");
        }
        if (rememberedVillageCount > 1) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "expected exactly one remembered settlement");
        }
        if (rememberedVillageCount == 0) {
            return new Assessment(Verdict.WAITING,
                    "waiting for ordinary production settlement perception");
        }
        if (!traderWithinLocalRadius) {
            return new Assessment(Verdict.FIXTURE_FAILURE,
                    "remembered settlement anchor is outside local trader radius");
        }
        return new Assessment(Verdict.READY, "settlement memory ready");
    }
}
