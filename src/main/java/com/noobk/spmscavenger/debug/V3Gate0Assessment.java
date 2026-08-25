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

    enum EvidenceKind {
        SATISFIED,
        UNREADABLE,
        STRUCTURAL_IMPOSSIBILITY,
        DYNAMIC_HOME_OCCUPANCY
    }

    record Result(Verdict verdict, EvidenceKind evidenceKind, String reason) {
    }

    private V3Gate0Assessment() {
    }

    static Result evaluate(boolean settlementObserved, Optional<VillageWorkFacts> facts) {
        if (!settlementObserved) {
            return new Result(
                    Verdict.INCOMPLETE, EvidenceKind.UNREADABLE,
                    "no remembered current settlement");
        }
        if (facts == null || facts.isEmpty()) {
            return new Result(
                    Verdict.INCOMPLETE, EvidenceKind.UNREADABLE, "no population facts");
        }
        VillageWorkFacts value = facts.get();
        if (!value.isReadable()) {
            return new Result(Verdict.INCOMPLETE, EvidenceKind.UNREADABLE,
                    "facts are " + value.completeness() + "/" + value.freshness());
        }
        if (value.adultVillagerCount() < 2) {
            return new Result(
                    Verdict.FIXTURE_FAILURE,
                    EvidenceKind.STRUCTURAL_IMPOSSIBILITY,
                    "adultVillagerCount=" + value.adultVillagerCount() + " < 2");
        }
        if (value.totalUsableHomeCapacity() < 3) {
            return new Result(
                    Verdict.FIXTURE_FAILURE,
                    EvidenceKind.STRUCTURAL_IMPOSSIBILITY,
                    "totalUsableHomeCapacity=" + value.totalUsableHomeCapacity() + " < 3");
        }
        if (value.claimedHomeCount() < 2) {
            return new Result(
                    Verdict.INCOMPLETE,
                    EvidenceKind.DYNAMIC_HOME_OCCUPANCY,
                    "claimedHomeCount=" + value.claimedHomeCount()
                            + " < 2; natural HOME acquisition still pending");
        }
        if (value.currentFreeHomeCapacity() < 1) {
            return new Result(
                    Verdict.FIXTURE_FAILURE,
                    EvidenceKind.STRUCTURAL_IMPOSSIBILITY,
                    "currentFreeHomeCapacity=" + value.currentFreeHomeCapacity() + " < 1");
        }
        return new Result(
                Verdict.PASS,
                EvidenceKind.SATISFIED,
                "settlement and population thresholds readable");
    }
}
