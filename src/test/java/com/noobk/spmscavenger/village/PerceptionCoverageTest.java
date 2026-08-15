package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * V1-R4 — {@link PerceptionCoverage} epistemic model and supersede ordering.
 */
class PerceptionCoverageTest {

    private static PerceptionCoverage full(int columns) {
        return new PerceptionCoverage(columns, columns);
    }

    private static PerceptionCoverage partial(int loaded, int total) {
        return new PerceptionCoverage(loaded, total);
    }

    @Test
    void mustHappen_crossMultiplyComparesWithoutFloat() {
        assertTrue(full(10).compareTo(partial(9, 10)) > 0);
        assertTrue(partial(9, 10).compareTo(full(10)) < 0);
        assertEquals(0, full(10).compareTo(full(16)));
    }

    @Test
    void mustHappen_equalFullCoverageNewerTickReplacesDespiteFewerPois() {
        ObservationQuality stored = ObservationQuality.withCoverage(20, 20, 20);
        ObservationQuality newer = ObservationQuality.withCoverage(16, 16, 16);
        assertTrue(newer.supersedes(stored, 200L, 100L),
                "100%/20 -> 100%/16: settlement shrank but observation opportunity was equal");
    }

    @Test
    void mustNotHappen_morePoisThroughWorseCoverageSupersedes() {
        ObservationQuality stored = ObservationQuality.withCoverage(10, 10, 10);
        ObservationQuality rimGlance = ObservationQuality.withCoverage(9, 20, 18);
        assertFalse(rimGlance.supersedes(stored, 200L, 100L),
                "45% window with more POIs must not beat 100% window");
    }

    @Test
    void mustHappen_supersedeIgnoresAdmittedCount() {
        ObservationQuality fullTwenty = ObservationQuality.withCoverage(10, 10, 20);
        ObservationQuality fullSixteen = ObservationQuality.withCoverage(10, 10, 16);
        assertTrue(fullSixteen.supersedes(fullTwenty, 300L, 100L));
    }

    @Test
    void mustHappen_optimisticMigrationFullIsNotDegradedByPartialGlance() {
        ObservationQuality legacy = ObservationQuality.fullCoverage(20);
        ObservationQuality partial = ObservationQuality.withCoverage(2, 10, 18);
        assertFalse(partial.supersedes(legacy, 200L, 100L));
    }

    @Test
    void mustHappen_chunkCircleIntersectionIsConservative() {
        assertTrue(PerceptionCoverage.intersectsHorizontalCircle(0, 0, 0, 0, 64L * 64L));
        assertFalse(PerceptionCoverage.intersectsHorizontalCircle(10, 10, 0, 0, 64L * 64L));
    }
}
