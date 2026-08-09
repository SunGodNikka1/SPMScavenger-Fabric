package com.noobk.spmscavenger.mining;

import net.minecraft.SharedConstants;
import com.noobk.spmscavenger.goal.ExploringGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14B — the admission rule that decides whether a mining project may begin.
 *
 * <p>The responsibility this locks down used to live inside {@code ControlledDescentGoal}, which
 * decided whether to mine, chose the heading, created the project, executed it, judged the outcome
 * and completed the lifecycle. The executor now asks one question — <em>am I assigned work?</em> —
 * so every reason a project may or may not start is testable here without a world.
 */
class MiningDirectorTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("director-mob".getBytes());

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MiningProject descentProject() {
        return MiningProject.startControlledDescent(
                new BlockPos(0, 60, 0), Direction.NORTH, 0L);
    }

    private static MiningTransition transition(MiningProjectEnd end) {
        return MiningTransition.of(descentProject(), end, new BlockPos(0, 50, 0), 0L);
    }

    @Test
    void mustHappen_exhaustedNaturalDescentUnderPressureAuthorisesAProject() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        assertTrue(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, 0L));
    }

    @Test
    void mustNotHappen_projectStartsWithoutDescentPressure() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        assertFalse(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, false, 0L));
    }

    @Test
    void mustNotHappen_projectStartsWhileNaturalDescentRemains() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        for (NaturalDescentStatus status : new NaturalDescentStatus[] {
                NaturalDescentStatus.SEARCHING,
                NaturalDescentStatus.AVAILABLE,
                NaturalDescentStatus.TEMPORARILY_BLOCKED }) {
            assertFalse(MiningDirector.mayStartControlledDescent(store, MOB, status, true, 0L),
                    status + " must not authorise digging - natural descent is still an option");
        }
    }

    @Test
    void mustNotHappen_aSecondProjectWhileOneIsAssigned() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.putProject(MOB, descentProject());
        assertFalse(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, 0L),
                "one assignment at a time");
    }

    @Test
    void mustNotHappen_aFreshDescentStarvesAnUnconsumedHandoff() {
        // MI-14A-R1 through the director: every handoff reason holds the lock, including
        // CAVE_FOUND, whose consumer sits at a lower priority and would otherwise never run.
        for (MiningProjectEnd end : new MiningProjectEnd[] {
                MiningProjectEnd.CAVE_FOUND,
                MiningProjectEnd.HANDOFF_TUNNEL_SEARCH,
                MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED }) {
            MiningProjectSavedData store = new MiningProjectSavedData();
            store.recordTransition(MOB, transition(end));
            assertFalse(MiningDirector.mayStartControlledDescent(
                            store, MOB, NaturalDescentStatus.EXHAUSTED, true, 0L),
                    end + " must hold the descent lock until consumed or expired");
        }
    }

    @Test
    void mustHappen_unrelatedOutcomesDoNotHoldTheLock() {
        for (MiningProjectEnd end : new MiningProjectEnd[] {
                MiningProjectEnd.HAZARD, MiningProjectEnd.NO_PROGRESS,
                MiningProjectEnd.COMBAT, MiningProjectEnd.TOOL_FAILURE }) {
            MiningProjectSavedData store = new MiningProjectSavedData();
            store.recordTransition(MOB, transition(end));
            assertTrue(MiningDirector.mayStartControlledDescent(
                            store, MOB, NaturalDescentStatus.EXHAUSTED, true, 0L),
                    end + " is not a handoff and must not block the next attempt");
        }
    }

    @Test
    void mustHappen_consumingTheHandoffReleasesTheLock() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB, transition(MiningProjectEnd.CAVE_FOUND));
        assertFalse(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, 0L));

        store.consumeTransition(MOB);
        assertTrue(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, 0L),
                "once the rebase has taken the cave, digging again is legitimate");
    }

    @Test
    void mustNotHappen_claimedCaveContinuationHoldsLockUntilCleared() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningTransition handoff = transition(MiningProjectEnd.CAVE_FOUND);
        store.recordTransition(MOB, handoff);
        assertTrue(store.claimCaveContinuation(MOB, handoff, 0L, ExploringGoal.MAX_EXPEDITION_TICKS));
        assertFalse(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, 10L));
        store.clearCommitment(MOB);
        assertTrue(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, 11L));
    }

    @Test
    void mustHappen_assignedProjectIsFoundOnlyForItsOwnMode() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.putProject(MOB, descentProject());
        assertTrue(MiningDirector.assignedProject(
                store, MOB, MiningProjectMode.CONTROLLED_DESCENT).isPresent());
        assertTrue(MiningDirector.assignedProject(
                        store, MOB, MiningProjectMode.CAVE_EXPLORATION).isEmpty(),
                "an executor must not pick up work assigned to a different mode");
    }

    @Test
    void mustNotHappen_oneMobsAssignmentAuthorisesAnother() {
        UUID other = UUID.nameUUIDFromBytes("other-mob".getBytes());
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.putProject(MOB, descentProject());
        assertTrue(MiningDirector.assignedProject(
                store, other, MiningProjectMode.CONTROLLED_DESCENT).isEmpty());
        assertTrue(MiningDirector.mayStartControlledDescent(
                        store, other, NaturalDescentStatus.EXHAUSTED, true, 0L),
                "another mob's project neither blocks nor authorises this one");
    }
}
