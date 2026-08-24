package com.noobk.spmscavenger.debug;

/** Pure classification for opening a settlement-dependent V3 runtime evidence window. */
final class V3RowPrecondition {

    enum Verdict {
        READY,
        WAITING_DAYTIME,
        FIXTURE_INCOMPLETE
    }

    record Result(Verdict verdict, String reason) {
    }

    private V3RowPrecondition() {
    }

    static Result evaluate(boolean daytime, boolean shelterHold) {
        if (!shelterHold) {
            return new Result(Verdict.READY, "no SHELTER_HOLD activity");
        }
        if (!daytime) {
            return new Result(Verdict.WAITING_DAYTIME,
                    "SHELTER_HOLD active before daytime transition");
        }
        return new Result(Verdict.FIXTURE_INCOMPLETE,
                "SHELTER_HOLD remained active after daytime transition");
    }
}
