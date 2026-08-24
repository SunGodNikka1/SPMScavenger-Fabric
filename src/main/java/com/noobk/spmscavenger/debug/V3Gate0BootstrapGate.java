package com.noobk.spmscavenger.debug;

import java.util.Objects;

/** Task-59-only temporal gate in front of the pure Gate-0 facts assessment. */
final class V3Gate0BootstrapGate {

    static final long MINIMUM_BOOTSTRAP_TICKS = 120L;

    enum Verdict {
        WAITING_BOOTSTRAP,
        PASS,
        FIXTURE_FAILURE,
        INCOMPLETE
    }

    record Result(Verdict verdict, long elapsedTicks, String reason) {
    }

    private V3Gate0BootstrapGate() {
    }

    static Result evaluate(
            long bootstrapStartTick,
            long currentTick,
            V3Gate0Assessment.Result assessment) {
        Objects.requireNonNull(assessment, "assessment");
        long elapsed = Math.max(0L, currentTick - bootstrapStartTick);
        if (elapsed < MINIMUM_BOOTSTRAP_TICKS) {
            return new Result(
                    Verdict.WAITING_BOOTSTRAP,
                    elapsed,
                    "natural settlement bootstrap " + elapsed + "/"
                            + MINIMUM_BOOTSTRAP_TICKS + " ticks");
        }
        return new Result(switch (assessment.verdict()) {
            case PASS -> Verdict.PASS;
            case FIXTURE_FAILURE -> Verdict.FIXTURE_FAILURE;
            case INCOMPLETE -> Verdict.INCOMPLETE;
        }, elapsed, assessment.reason());
    }
}
