package com.noobk.spmscavenger.validation;

/** Pure cadence rule for periodic scans versus the mandatory window-boundary scan. */
final class V3ContaminationScanGate {

    static final long PERIODIC_INTERVAL_TICKS = 20L;

    enum Mode {
        PERIODIC,
        FORCED_BOUNDARY
    }

    private V3ContaminationScanGate() {
    }

    static boolean shouldScan(long now, long lastScanTick, Mode mode) {
        return mode == Mode.FORCED_BOUNDARY
                || lastScanTick < 0L
                || now - lastScanTick >= PERIODIC_INTERVAL_TICKS;
    }
}
