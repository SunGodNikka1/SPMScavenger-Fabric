package com.noobk.spmscavenger.mining;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14A-T — the transition protocol contract.
 *
 * <p>This is the seam every later mining behaviour will be wired through, so the contract is locked
 * before MI-14B moves project ownership. The defect these guard against is specific and was live:
 * every handoff reason maps to {@link net.minecraft.world.entity.ai.goal.Goal} success, and
 * {@code MiningProject.shouldPersist()} keeps only RUNNING/INTERRUPTED/RETRY — so completing a
 * project <b>deleted</b> the record that carried its outcome.
 *
 * <p><b>Coverage boundary.</b> The rebase itself ({@code ExploringGoal.acceptCaveHandoff}) needs a
 * live mob, level and navigation, so it is not asserted here. What <em>is</em> asserted is the
 * admission rule that gates it — {@link MiningTransition#acceptableCaveHandoff} is the shipped code
 * path the goal calls, not a copy. Items marked RUNTIME below remain `UNVERIFIED`.
 */
class MiningTransitionContractTest {

    private static final UUID MOB_A = UUID.nameUUIDFromBytes("mob-a".getBytes());
    private static final UUID MOB_B = UUID.nameUUIDFromBytes("mob-b".getBytes());
    private static final int LIFETIME = 400;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MiningProject descentProject() {
        return MiningProject.startControlledDescent(
                new BlockPos(10, 60, -20), Direction.EAST, 0L);
    }

    private static MiningTransition transition(MiningProjectEnd end, long tick) {
        return MiningTransition.of(descentProject(), end, new BlockPos(12, 44, -20), tick);
    }

    // ---- 1. atomic terminal transition ----

    @Test
    void terminalCompletionDeletesTheProjectButPreservesExactlyOneTransition() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.putProject(MOB_A, descentProject());
        assertTrue(store.projectOf(MOB_A).isPresent(), "precondition: project is live");

        MiningTransition outcome = transition(MiningProjectEnd.CAVE_FOUND, 500L);
        store.completeProject(MOB_A, MiningProjectEnd.CAVE_FOUND, outcome);

        assertTrue(store.projectOf(MOB_A).isEmpty(),
                "a SUCCESS lifecycle is not persisted - the project must be gone");
        Optional<MiningTransition> pending = store.pendingTransition(MOB_A);
        assertTrue(pending.isPresent(), "the outcome must survive the deletion that produced it");

        MiningTransition kept = pending.get();
        assertEquals(MiningProjectMode.CONTROLLED_DESCENT, kept.fromMode());
        assertEquals(MiningProjectEnd.CAVE_FOUND, kept.reason());
        assertEquals(new BlockPos(12, 44, -20), kept.at());
        assertEquals(Direction.EAST, kept.heading());
        assertEquals(500L, kept.tick());
    }

    // ---- 2. NBT round-trip ----

    @Test
    void pendingTransitionsSurviveSaveAndLoadUnchanged() {
        MiningTransition original = transition(MiningProjectEnd.CAVE_FOUND, 1234L)
                .withTarget(new BlockPos(15, 43, -22));
        MiningTransition restored = MiningTransition.load(original.save());
        assertEquals(original, restored);
        assertTrue(restored.hasTarget());
    }

    @Test
    void savedDataRoundTripKeepsTransitionsPerMob() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB_A, transition(MiningProjectEnd.CAVE_FOUND, 10L));
        store.recordTransition(MOB_B, transition(MiningProjectEnd.HANDOFF_TUNNEL_SEARCH, 20L));

        MiningProjectSavedData reloaded =
                MiningProjectSavedData.load(store.save(new CompoundTag(), null), null);

        assertEquals(MiningProjectEnd.CAVE_FOUND,
                reloaded.pendingTransition(MOB_A).orElseThrow().reason());
        assertEquals(MiningProjectEnd.HANDOFF_TUNNEL_SEARCH,
                reloaded.pendingTransition(MOB_B).orElseThrow().reason());
    }

    // ---- 5. expiry ----

    @Test
    void anUnconsumableCaveHandoffExpiresRatherThanBecomingImmortal() {
        MiningTransition cave = transition(MiningProjectEnd.CAVE_FOUND, 1000L);
        assertTrue(MiningTransition.acceptableCaveHandoff(
                Optional.of(cave), 1000L + LIFETIME - 1, LIFETIME).isPresent());
        assertTrue(MiningTransition.acceptableCaveHandoff(
                        Optional.of(cave), 1000L + LIFETIME, LIFETIME).isEmpty(),
                "at the lifetime boundary the handoff is stale");
    }

    // ---- 6/7. restart guards ----

    @Test
    void tunnelAndBudgetOutcomesBlockAFreshControlledDescent() {
        assertTrue(transition(MiningProjectEnd.HANDOFF_TUNNEL_SEARCH, 0L)
                .blocksControlledDescentRestart());
        assertTrue(transition(MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED, 0L)
                .blocksControlledDescentRestart(),
                "otherwise: exhaust -> delete -> pressure unchanged -> exhaust again, forever");
    }

    // ---- 9. reason isolation ----

    @Test
    void unrelatedReasonsDoNotBehaveLikeHandoffs() {
        for (MiningProjectEnd end : new MiningProjectEnd[] {
                MiningProjectEnd.HAZARD, MiningProjectEnd.NO_PROGRESS,
                MiningProjectEnd.COMBAT, MiningProjectEnd.TOOL_FAILURE }) {
            MiningTransition other = transition(end, 0L);
            assertFalse(other.isCaveContinuation(), end + " must not rebase exploration");
            assertFalse(other.blocksControlledDescentRestart(), end + " must not block descent");
            assertTrue(MiningTransition.acceptableCaveHandoff(
                    Optional.of(other), 0L, LIFETIME).isEmpty());
        }
    }

    @Test
    void caveFoundDoesNotBlockDescentAndTunnelDoesNotRebaseExploration() {
        assertFalse(transition(MiningProjectEnd.CAVE_FOUND, 0L).blocksControlledDescentRestart());
        assertFalse(transition(MiningProjectEnd.HANDOFF_TUNNEL_SEARCH, 0L).isCaveContinuation());
    }

    // ---- 8. consumption exactly once ----

    @Test
    void aTransitionIsConsumedExactlyOnce() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB_A, transition(MiningProjectEnd.CAVE_FOUND, 0L));

        assertTrue(store.consumeTransition(MOB_A).isPresent());
        assertTrue(store.consumeTransition(MOB_A).isEmpty(),
                "a consumed handoff must not re-trigger a second rebase");
        assertTrue(store.pendingTransition(MOB_A).isEmpty());
    }

    @Test
    void peekingDoesNotConsume() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB_A, transition(MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED, 0L));
        // The restart guard peeks every canUse; if that consumed, the guard would disarm itself.
        assertTrue(store.pendingTransition(MOB_A).isPresent());
        assertTrue(store.pendingTransition(MOB_A).isPresent());
        assertTrue(store.consumeTransition(MOB_A).isPresent());
    }

    // ---- 4/12. failed rebase persistence ----

    @Test
    void aFailedRebaseLeavesTheTransitionPendingForALaterRetry() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB_A, transition(MiningProjectEnd.CAVE_FOUND, 0L));

        // Simulated failed attempt: the goal admits the handoff, cannot plan a route, and returns
        // without consuming. The contract is that admission and consumption are separate steps.
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(MiningTransition.acceptableCaveHandoff(
                            store.pendingTransition(MOB_A), 10L * attempt, LIFETIME).isPresent(),
                    "a failed plan must not discard the cave");
        }
        assertTrue(store.consumeTransition(MOB_A).isPresent(),
                "and a later successful rebase still finds it");
    }

    // ---- 10. per-mob isolation ----

    @Test
    void oneMobCannotConsumeAnotherMobsHandoff() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB_A, transition(MiningProjectEnd.CAVE_FOUND, 0L));

        assertTrue(store.pendingTransition(MOB_B).isEmpty());
        assertTrue(store.consumeTransition(MOB_B).isEmpty());
        assertTrue(store.pendingTransition(MOB_A).isPresent(),
                "consuming for the wrong mob must not disturb the owner's handoff");
    }

    @Test
    void recordingASecondOutcomeReplacesOnlyThatMobsPending() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB_A, transition(MiningProjectEnd.CAVE_FOUND, 0L));
        store.recordTransition(MOB_B, transition(MiningProjectEnd.HAZARD, 0L));
        store.recordTransition(MOB_A, transition(MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED, 5L));

        assertEquals(MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED,
                store.pendingTransition(MOB_A).orElseThrow().reason());
        assertEquals(MiningProjectEnd.HAZARD,
                store.pendingTransition(MOB_B).orElseThrow().reason());
    }

    // ---- 11. an active project outranks a stale pending outcome ----

    @Test
    void aPendingOutcomeDoesNotCancelAnAlreadyRunningProject() {
        // ControlledDescentGoal.canUse checks the resume branch *before* the restart guard, so a
        // leftover outcome can block a new descent but never interrupt one in progress.
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.putProject(MOB_A, descentProject());
        store.recordTransition(MOB_A, transition(MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED, 0L));

        assertTrue(store.projectOf(MOB_A).filter(MiningProject::isControlledDescent).isPresent(),
                "the running project is still resumable");
        assertTrue(store.pendingTransition(MOB_A).orElseThrow().blocksControlledDescentRestart(),
                "and the guard is still armed for the next start");
    }

    // ---- payload integrity ----

    @Test
    void theTransitionCarriesEnoughToActOnRatherThanJustAReason() {
        MiningTransition cave = transition(MiningProjectEnd.CAVE_FOUND, 77L);
        assertNotEquals(BlockPos.ZERO, cave.at(), "a handoff without a position is not actionable");
        assertEquals(Direction.EAST, cave.heading(), "continuation must keep the project's momentum");
        assertFalse(cave.hasTarget(), "no landing was resolved, and that is represented honestly");
        assertTrue(cave.withTarget(new BlockPos(1, 2, 3)).hasTarget());
    }
}
