package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GAO-0-B1 — the global switch disables new work, not existing lease settlement. */
final class ExplorationDisabledCadenceTest {

    @Test
    void disabledPassEnforcesLeaseBeforePreservingReadinessReset() {
        List<String> events = new ArrayList<>();

        boolean handled = ExplorationActivityGoal.handleDisabledCadence(
                false,
                () -> events.add("lease"),
                () -> events.add("readiness"));

        assertTrue(handled);
        assertEquals(List.of("lease", "readiness"), events);
    }

    @Test
    void disabledPassHasNoNewWorkCallbackSurface() {
        List<String> events = new ArrayList<>();

        ExplorationActivityGoal.handleDisabledCadence(
                false,
                () -> events.add("settle-existing"),
                () -> events.add("reset-readiness"));

        assertEquals(List.of("settle-existing", "reset-readiness"), events,
                "the disabled control-flow API must expose no assignment or handoff callback");
    }

    @Test
    void enabledPassLeavesExistingOrderingToTheNormalObserverPath() {
        List<String> events = new ArrayList<>();

        boolean handled = ExplorationActivityGoal.handleDisabledCadence(
                true,
                () -> events.add("lease"),
                () -> events.add("readiness"));

        assertFalse(handled);
        assertTrue(events.isEmpty(), "enabled behavior must continue through the unchanged path");
    }

    @Test
    void reenableDoesNotResurrectAuthorityCleanedByDisabledPass() {
        AtomicReference<String> authority = new AtomicReference<>("ACTIVE");

        assertTrue(ExplorationActivityGoal.handleDisabledCadence(
                false,
                () -> authority.set("CLEANED"),
                () -> { }));
        assertFalse(ExplorationActivityGoal.handleDisabledCadence(
                true,
                () -> authority.set("RESURRECTED"),
                () -> { }));

        assertEquals("CLEANED", authority.get());
    }

    @Test
    void cleanupOnlyObserverCannotAuthorizeWorkAfterReenable() {
        assertFalse(ExplorationActivityGoal.permitsNewMiningWork(false, false));
        assertFalse(ExplorationActivityGoal.permitsNewMiningWork(true, false));
        assertFalse(ExplorationActivityGoal.permitsNewMiningWork(false, true));
        assertTrue(ExplorationActivityGoal.permitsNewMiningWork(true, true));
    }
}
