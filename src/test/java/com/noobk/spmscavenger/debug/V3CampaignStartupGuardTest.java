package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class V3CampaignStartupGuardTest {

    @Test
    void uncheckedFixtureFailureBecomesDiagnosableAndReleasesHarnessResources() {
        HarnessState harness = new HarnessState();

        V3CampaignStartupGuard.Outcome outcome = V3CampaignStartupGuard.execute(() -> {
            throw new IllegalStateException("fixture executor blew up");
        }, harness::accept);

        assertFalse(outcome.succeeded(), "unchecked fixture failure must not escape generically");
        assertEquals("FIXTURE_FAILURE", harness.state);
        assertTrue(harness.status.stream()
                .anyMatch(line -> line.contains("IllegalStateException: fixture executor blew up")));
        assertTrue(harness.report.stream()
                .anyMatch(line -> line.contains("IllegalStateException: fixture executor blew up")));
        assertTrue(harness.resourcesReleased.get());
    }

    @Test
    void fatalVmFailuresAreNotContained() {
        assertThrows(OutOfMemoryError.class, () -> V3CampaignStartupGuard.execute(() -> {
            throw new OutOfMemoryError("fatal control");
        }));
    }

    private static final class HarnessState {
        private String state = "PREPARING";
        private final List<String> status = new ArrayList<>();
        private final List<String> report = new ArrayList<>();
        private final AtomicBoolean resourcesReleased = new AtomicBoolean();

        private void accept(V3CampaignStartupGuard.Outcome outcome) {
            if (outcome.succeeded()) {
                return;
            }
            state = "FIXTURE_FAILURE";
            status.add(outcome.failureSummary());
            report.add(outcome.failureSummary());
            resourcesReleased.set(true);
        }
    }
}
