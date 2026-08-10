package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate RET-1c — a controller must not create work its executor is guaranteed to refuse.
 *
 * <h2>Observed, not theorised</h2>
 *
 * A real session log showed, for one mob:
 *
 * <pre>
 * assigned CONTROLLED_DESCENT                              x117
 * revoked  blocker=CAPABILITY_MISSING  everStarted=false   x117
 * retired  non-resumable  reason=TOOL_FAILURE              x118
 * </pre>
 *
 * {@code everStarted=false} every cycle: the executor never ran. Admission asked only "is there
 * work to do" while the blocker asked "can I dig", so a mob with no pickaxe span forever. Each turn
 * allocated a project, a lease and a transition, marked saved data dirty, wrote three log lines and
 * emitted experience events — a churn loop that manufactures retention.
 *
 * <p>The correct state for a mob that wants diamonds and owns no pickaxe is <em>prerequisite: obtain
 * a pickaxe</em>, not <em>impossible mining job, retried forever</em>.
 */
class MiningAdmissionChurnTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("churn".getBytes());
    private static final BlockPos ORIGIN = new BlockPos(0, 40, 0);

    /** One director decision cycle: admit, and if admitted, run the executor's blocker verdict. */
    private static int runDecisionCycles(
            MiningProjectSavedData store, boolean hasCapability, int cycles) {
        int assignments = 0;
        for (int tick = 0; tick < cycles; tick++) {
            if (MiningDirector.mayStartControlledDescent(
                    store, MOB, NaturalDescentStatus.EXHAUSTED, true, hasCapability, tick)) {
                assignments++;
                MiningProject project =
                        MiningProject.startControlledDescent(ORIGIN, Direction.EAST, tick);
                store.putProject(MOB, project);
                store.putLease(MOB, MiningExecutionLease.issued(
                        MiningProjectMode.CONTROLLED_DESCENT, tick));

                // What the lease layer does next when the mandatory capability is absent.
                if (!hasCapability) {
                    ExecutionLeasePolicy.LeaseOutcome outcome = ExecutionLeasePolicy.evaluate(
                            ExecutionBlocker.CAPABILITY_MISSING, false, tick,
                            MiningExecutionLease.NOT_BLOCKED, tick);
                    assertTrue(outcome.revoked(), "a missing pickaxe is a HARD blocker");
                    store.completeProject(MOB, outcome.revokeReason(),
                            MiningTransition.of(project, outcome.revokeReason(), ORIGIN, tick));
                    store.clearProject(MOB);   // the observer retiring a non-resumable project
                    store.clearLease(MOB);
                }
            }
        }
        return assignments;
    }

    @Test
    void mustNotHappen_aPicklessMobSpinsTheDirector() {
        MiningProjectSavedData store = new MiningProjectSavedData();

        int assignments = runDecisionCycles(store, false, 10_000);

        assertEquals(0, assignments,
                "10,000 decision cycles with demand and no pickaxe must produce zero assignments - "
                        + "before RET-1c this was 10,000 assign/revoke/retire cycles, each "
                        + "allocating a project, a lease, a transition and an experience event");
        assertTrue(store.projectOf(MOB).isEmpty(), "no project created");
        assertTrue(store.pendingTransition(MOB).isEmpty(),
                "and no terminal transition, so nothing downstream is fed by the loop either");
        assertTrue(store.leaseOf(MOB).isEmpty());
    }

    /** Blocking impossibility must not become permanent suppression. */
    @Test
    void mustHappen_acquiringAPickaxeAdmitsExactlyOneDescent() {
        MiningProjectSavedData store = new MiningProjectSavedData();

        assertEquals(0, runDecisionCycles(store, false, 500), "pickless: nothing");

        // The mob crafts or loots a pickaxe; the next eligible cycle admits.
        assertTrue(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, 500),
                "capability restored, so mining resumes - the gate blocks impossibility, not mining");

        int assignments = runDecisionCycles(store, true, 500);
        assertEquals(1, assignments,
                "exactly one: the project it created then blocks every further admission");
        assertTrue(store.projectOf(MOB).isPresent());
        assertEquals(MiningProjectMode.CONTROLLED_DESCENT, store.projectOf(MOB).orElseThrow().mode());
    }

    @Test
    void mustHappen_pressureRemainsWhileCapabilityIsMissing() {
        MiningProjectSavedData store = new MiningProjectSavedData();

        // Descent pressure is still true throughout - the gate suppresses the *project*, not the
        // need. Prerequisite work (craft/gather a pickaxe) stays free to act on that need.
        assertFalse(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, false, 0));
        assertFalse(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, false, 100_000));
    }

    @Test
    void mustNotHappen_capabilityAloneAdmitsWithoutPressure() {
        MiningProjectSavedData store = new MiningProjectSavedData();

        assertFalse(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, false, true, 0),
                "owning a pickaxe is permission, not a reason - the new clause must not become an "
                        + "alternative trigger");
    }
}
