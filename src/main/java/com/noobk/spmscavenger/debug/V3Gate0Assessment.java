package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.village.work.VillageWorkFacts;

import java.util.Optional;

/** Pure Task-59 Gate-0 classification over already-observed production facts. */
final class V3Gate0Assessment {

    enum Verdict {
        PASS,
        FIXTURE_FAILURE,
        INCOMPLETE
    }

    record Result(Verdict verdict, String reason) {
    }

    private V3Gate0Assessment() {
    }

    static Result evaluate(boolean settlementObserved, Optional<VillageWorkFacts> facts) {
        if (!settlementObserved) {
            return new Result(Verdict.INCOMPLETE, "no remembered current settlement");
        }
        if (facts == null || facts.isEmpty()) {
            return new Result(Verdict.INCOMPLETE, "no population facts");
        }
        VillageWorkFacts value = facts.get();
        if (!value.isReadable()) {
            return new Result(Verdict.INCOMPLETE,
                    "facts are " + value.completeness() + "/" + value.freshness());
        }
        if (value.adultVillagerCount() < 2) {
            return new Result(Verdict.FIXTURE_FAILURE, "adultVillagerCount < 2");
        }
        if (value.claimedHomeCount() < 2) {
            return new Result(Verdict.FIXTURE_FAILURE, "claimedHomeCount < 2");
        }
        if (value.currentFreeHomeCapacity() < 1) {
            return new Result(Verdict.FIXTURE_FAILURE, "currentFreeHomeCapacity < 1");
        }
        return new Result(Verdict.PASS, "settlement and population thresholds readable");
    }
}
