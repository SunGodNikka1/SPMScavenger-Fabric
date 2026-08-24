package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class V3PostOpenContaminationPolicyTest {

    @Test
    void idleOuterEnvelopePresenceIsTelemetryOnly() {
        assertEquals(V3PostOpenContaminationPolicy.Disposition.TELEMETRY,
                decide(false, 180.0, false, false));
    }

    @Test
    void coreProximityTargetingAndRowInteractionAreCausal() {
        assertEquals(V3PostOpenContaminationPolicy.Disposition.EXTERNAL_INTERFERENCE,
                decide(true, 180.0, false, false));
        assertEquals(V3PostOpenContaminationPolicy.Disposition.EXTERNAL_INTERFERENCE,
                decide(false, 16.0, false, false));
        assertEquals(V3PostOpenContaminationPolicy.Disposition.EXTERNAL_INTERFERENCE,
                decide(false, 180.0, true, false));
        assertEquals(V3PostOpenContaminationPolicy.Disposition.EXTERNAL_INTERFERENCE,
                decide(false, 180.0, false, true));
    }

    private static V3PostOpenContaminationPolicy.Disposition decide(
            boolean inCore,
            double subjectDistance,
            boolean targetingRelationship,
            boolean rowSpecificInteraction) {
        return V3PostOpenContaminationPolicy.evaluate(
                inCore, subjectDistance, targetingRelationship, rowSpecificInteraction).disposition();
    }
}
