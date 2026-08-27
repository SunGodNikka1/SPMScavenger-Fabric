package com.noobk.spmscavenger.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class V3ContaminationScanGateTest {

    @Test
    void periodicScanThrottlesButFinalBoundaryNeverDoes() {
        assertFalse(V3ContaminationScanGate.shouldScan(
                105L, 100L, V3ContaminationScanGate.Mode.PERIODIC));
        assertTrue(V3ContaminationScanGate.shouldScan(
                105L, 100L, V3ContaminationScanGate.Mode.FORCED_BOUNDARY));
    }
}
