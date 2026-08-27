package com.noobk.spmscavenger.validation;

import java.util.Objects;

/** Pure overall Gate-0 timeout classification anchored to fixture session creation. */
final class V3Gate0TimeoutGate {

    static final long OVERALL_TIMEOUT_TICKS = 2400L;

    enum Verdict {
        WAITING,
        INCOMPLETE,
        FIXTURE_INCOMPLETE
    }

    record Result(Verdict verdict, long elapsedTicks, String reason) {
    }

    private V3Gate0TimeoutGate() {
    }

    static Result evaluate(
            long fixtureStartTick,
            long currentTick,
            V3Gate0Assessment.Result assessment) {
        Objects.requireNonNull(assessment, "assessment");
        long elapsed = Math.max(0L, currentTick - fixtureStartTick);
        if (elapsed < OVERALL_TIMEOUT_TICKS) {
            return new Result(Verdict.WAITING, elapsed,
                    "Gate0 waiting " + elapsed + "/" + OVERALL_TIMEOUT_TICKS + " ticks");
        }
        if (assessment.evidenceKind()
                == V3Gate0Assessment.EvidenceKind.DYNAMIC_HOME_OCCUPANCY) {
            return new Result(Verdict.FIXTURE_INCOMPLETE, elapsed,
                    "natural HOME claims remained incomplete through overall timeout");
        }
        return new Result(Verdict.INCOMPLETE, elapsed,
                "Gate0 evidence remained unreadable through overall timeout");
    }
}
