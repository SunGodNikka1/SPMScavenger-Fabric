package com.noobk.spmscavenger.debug;

/** Causal post-open classification; the subject's observation envelope is telemetry only. */
final class V3PostOpenContaminationPolicy {

    static final double SUBJECT_PROXIMITY_RADIUS = 16.0;

    enum Disposition {
        TELEMETRY,
        EXTERNAL_INTERFERENCE
    }

    record Result(Disposition disposition, String reason) {
    }

    private V3PostOpenContaminationPolicy() {
    }

    static Result evaluate(
            boolean inScenarioCore,
            double distanceFromSubject,
            boolean targetingRelationship,
            boolean rowSpecificInteraction) {
        if (inScenarioCore) {
            return new Result(Disposition.EXTERNAL_INTERFERENCE, "entered scenario core");
        }
        if (distanceFromSubject <= SUBJECT_PROXIMITY_RADIUS) {
            return new Result(Disposition.EXTERNAL_INTERFERENCE, "entered subject proximity");
        }
        if (targetingRelationship) {
            return new Result(Disposition.EXTERNAL_INTERFERENCE, "targeting relationship observed");
        }
        if (rowSpecificInteraction) {
            return new Result(Disposition.EXTERNAL_INTERFERENCE, "row-specific interaction observed");
        }
        return new Result(Disposition.TELEMETRY, "outer-envelope presence only");
    }
}
