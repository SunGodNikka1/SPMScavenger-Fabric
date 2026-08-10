package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.goal.ExploringGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14C2-R2 — admission and authority are different questions and need different clocks.
 *
 * <pre>
 * admission   400 ticks from DISCOVERY  — is this find still fresh enough to act on?
 * authority   N ticks from CLAIM        — how long may an accepted expedition stay alive?
 * </pre>
 *
 * <p>Before this repair both were anchored to discovery: {@code caveContinuation} stored
 * {@code claimedAt = now} and then computed {@code expiresAt = handoff.tick() + 400}. A handoff
 * claimed at discovery+399 received <b>one tick</b> of authority, so the 48-block continuation it
 * exists to protect immediately lost its protection — intent fell to {@code NONE}, exploring
 * reverted to ordinary, and priority-3 chores outranked it again. Loop B, through a subtler path.
 */
class CaveHandoffAuthorityClockTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("authority-clock".getBytes());
    private static final int ADMISSION = ExecutionIntentPolicy.CAVE_HANDOFF_LIFETIME_TICKS; // 400
    /** Read from its owner, not copied: a duplicated window is how the two clocks drift. */
    private static final int AUTHORITY = ExploringGoal.MAX_EXPEDITION_TICKS;

    private static final long DISCOVERY = 10_000L;
    /** Last tick the discovery is still admissible: {@code expired()} is {@code >=}, not {@code >}. */
    private static final long LATEST_CLAIM = DISCOVERY + ADMISSION - 1;

    private static MiningTransition handoff() {
        return new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT,
                MiningProjectEnd.CAVE_FOUND,
                new BlockPos(0, 40, 0),
                Direction.EAST,
                new BlockPos(6, 38, 0),
                DISCOVERY);
    }

    private static MiningProjectSavedData storeWithPendingHandoff() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB, handoff());
        return store;
    }

    /**
     * The boundary that matters. A claim on the last admissible tick keeps full authority into the
     * period when the *discovery* would have gone stale — because authority started at the claim.
     */
    @Test
    void mustHappen_aLateButValidClaimKeepsFullAuthorityAfterAdmissionCloses() {
        MiningProjectSavedData store = storeWithPendingHandoff();

        assertTrue(store.claimCaveContinuation(MOB, handoff(), LATEST_CLAIM, AUTHORITY),
                "discovery+399 is still inside the admission window");

        assertTrue(store.hasActiveCaveContinuation(MOB, DISCOVERY + ADMISSION),
                "the moment admission goes stale, an already-claimed continuation must survive - "
                        + "under the old discovery-anchored clock this had ~1 tick of authority");
        assertTrue(store.hasActiveCaveContinuation(MOB, LATEST_CLAIM + AUTHORITY - 1),
                "authority runs its full window from the claim");
    }

    /** The inverse, and the reason the two clocks must stay separate. */
    @Test
    void mustNotHappen_wideningAuthorityWidensAdmission() {
        MiningProjectSavedData store = storeWithPendingHandoff();
        long tooLate = DISCOVERY + ADMISSION;

        assertTrue(handoff().expired(tooLate, ADMISSION),
                "precondition: expired() is >=, so the window is strictly under 400 ticks");
        assertTrue(MiningTransition.acceptableCaveHandoff(
                        store.pendingTransition(MOB), tooLate, ADMISSION).isEmpty(),
                "a discovery nobody acted on in 400 ticks is no longer worth acting on");
        assertFalse(store.hasActiveCaveContinuation(MOB, tooLate),
                "never claimed, so no authority exists to inherit");
    }

    @Test
    void mustHappen_authorityStillExpires() {
        MiningProjectSavedData store = storeWithPendingHandoff();
        store.claimCaveContinuation(MOB, handoff(), DISCOVERY, AUTHORITY);

        assertTrue(store.hasActiveCaveContinuation(MOB, DISCOVERY + AUTHORITY - 1));
        assertFalse(store.hasActiveCaveContinuation(MOB, DISCOVERY + AUTHORITY + 1),
                "a mob that never arrives must release the claim - an unbounded commitment is the "
                        + "same deadlock the lease work removed");
    }

    /**
     * MI-14C2-M2 — the exact tick the previous revision got wrong.
     *
     * <p>Sharing the 2400 constant was not enough: the commitment asked
     * {@code now < claimedAt + 2400} while {@code ExploringGoal} asks
     * {@code now - startedTick > 2400}. At exactly {@code claim + 2400} the expedition was still
     * legally running and its authority had already lapsed — one tick in which intent falls to
     * {@code NONE}, exploring reverts to ordinary, and {@code mayStartControlledDescent} reopens.
     * A one-tick hole is still a false invariant, and one scheduler cycle is all a goal needs.
     */
    @Test
    void mustNotHappen_authorityLapsesOnTheTickTheExpeditionIsStillAlive() {
        MiningProjectSavedData store = storeWithPendingHandoff();
        store.claimCaveContinuation(MOB, handoff(), DISCOVERY, AUTHORITY);

        long lastLegalTick = DISCOVERY + AUTHORITY;
        assertFalse(ExploringGoal.expeditionExpired(DISCOVERY, lastLegalTick, AUTHORITY),
                "precondition: the expedition is still alive at exactly start + lifetime");
        assertTrue(store.hasActiveCaveContinuation(MOB, lastLegalTick),
                "so its authority must be too - the two boundaries are one definition now");
        assertFalse(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, lastLegalTick),
                "and no fresh staircase may slip through that tick");

        long firstStaleTick = DISCOVERY + AUTHORITY + 1;
        assertTrue(ExploringGoal.expeditionExpired(DISCOVERY, firstStaleTick, AUTHORITY));
        assertFalse(store.hasActiveCaveContinuation(MOB, firstStaleTick),
                "they must also end together, or authority outlives what it protects");
    }

    /**
     * Guards the repair itself: both sides must go on asking the same question. A future edit that
     * reintroduces a local comparison fails here rather than in a one-tick runtime window.
     */
    @Test
    void mustHappen_commitmentAndExpeditionShareOneLifetimeBoundary() {
        for (int window : new int[] {0, 1, 40, ADMISSION, AUTHORITY}) {
            MiningProjectSavedData store = storeWithPendingHandoff();
            assertTrue(store.claimCaveContinuation(MOB, handoff(), DISCOVERY, window));

            for (long age = 0; age <= window + 2L; age++) {
                long now = DISCOVERY + age;
                assertEquals(
                        !ExploringGoal.expeditionExpired(DISCOVERY, now, window),
                        store.hasActiveCaveContinuation(MOB, now),
                        "window=" + window + " age=" + age
                                + ": authority and expedition lifetime disagreed");
            }
        }
    }

    /** A pre-M2 save stored an absolute deadline; the window it meant must survive the upgrade. */
    @Test
    void mustHappen_legacyCommitmentsKeepTheirWindow() {
        MiningExecutionCommitment claimed =
                MiningExecutionCommitment.caveContinuation(handoff(), DISCOVERY, AUTHORITY);
        net.minecraft.nbt.CompoundTag legacy = claimed.save();
        legacy.remove("authorityTicks");
        legacy.putLong("expiresAt", DISCOVERY + AUTHORITY);

        assertEquals(AUTHORITY, MiningExecutionCommitment.load(legacy).authorityTicks(),
                "a world saved before M2 must not silently lose its authority window");
    }

    /** While the walk is protected, the director must not hand out a fresh staircase. */
    @Test
    void mustNotHappen_aFreshDescentIsAssignedDuringAProtectedWalk() {
        MiningProjectSavedData store = storeWithPendingHandoff();
        store.claimCaveContinuation(MOB, handoff(), LATEST_CLAIM, AUTHORITY);

        long afterAdmissionClosed = DISCOVERY + ADMISSION + 50;
        assertFalse(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, afterAdmissionClosed),
                "the transition is consumed and admission is stale, so the commitment is the only "
                        + "thing left stopping the mob digging a new hole beside the cave it found");

        long afterAuthorityLapsed = LATEST_CLAIM + AUTHORITY + 1;
        assertTrue(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, afterAuthorityLapsed),
                "once the walk is genuinely over, descent pressure may act again");
    }

    /** Intent must track the same clock, or the arbiter and the director disagree. */
    @Test
    void mustHappen_intentStaysCaveHandoffForTheWholeProtectedWalk() {
        MiningProjectSavedData store = storeWithPendingHandoff();
        store.claimCaveContinuation(MOB, handoff(), LATEST_CLAIM, AUTHORITY);

        assertEquals(ExecutionIntent.CAVE_HANDOFF,
                ExecutionIntentPolicy.derive(store, MOB, DISCOVERY + ADMISSION + 1),
                "consumed transition plus live commitment must still read as CAVE_HANDOFF");
        assertEquals(MiningGoalKind.EXPLORING_CAVE_HANDOFF,
                MiningGoalKind.classifyExploring(store, MOB, DISCOVERY + ADMISSION + 1),
                "and exploring must stay the designated consumer, not revert to ordinary");

        assertEquals(ExecutionIntent.NONE,
                ExecutionIntentPolicy.derive(store, MOB, LATEST_CLAIM + AUTHORITY + 1),
                "after the authority window the mob is exploring ordinarily again");
    }

    /**
     * Authority must not undercut the lifetime it protects. The continuation is an expedition, and
     * the normal path clears the commitment when that expedition completes or is abandoned — so the
     * window is a ceiling, and a ceiling below the floor would reintroduce the defect in miniature.
     */
    @Test
    void mustNotHappen_authorityExpiresBeforeTheExpeditionItProtects() {
        MiningProjectSavedData store = storeWithPendingHandoff();
        store.claimCaveContinuation(MOB, handoff(), LATEST_CLAIM, AUTHORITY);

        assertTrue(AUTHORITY >= ADMISSION,
                "an authority window shorter than admission would be strictly worse than no repair");
        assertTrue(store.hasActiveCaveContinuation(MOB, LATEST_CLAIM + ADMISSION),
                "a full admission window's worth of travel is the minimum this must survive");
    }
}
