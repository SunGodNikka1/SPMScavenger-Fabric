package com.noobk.spmscavenger.validation;

/** Pure bounded lifecycle decision for threaded fixture-light readiness. */
final class V4FixtureLightingGate {

    enum Verdict {
        WAITING,
        PASS,
        TIMEOUT
    }

    private V4FixtureLightingGate() {
    }

    static Verdict evaluate(
            boolean lightingVerified, long now, long lightingWaitDeadline) {
        if (lightingVerified) {
            return Verdict.PASS;
        }
        return now >= lightingWaitDeadline ? Verdict.TIMEOUT : Verdict.WAITING;
    }
}
