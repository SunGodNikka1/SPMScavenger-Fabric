package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;

import java.util.Optional;
import java.util.UUID;

/**
 * MI-14B — owns when a {@link MiningProject} starts and how it ends.
 *
 * <h2>What moved here, and why</h2>
 *
 * {@code ControlledDescentGoal} previously decided <em>whether</em> to mine, chose the heading,
 * created the project, executed it, judged the outcome, and completed the lifecycle. Six
 * responsibilities in an executor, and the only mode that could ever exist was the one that executor
 * implemented — a second mode had nowhere to be decided.
 *
 * <p>The split is now: <b>the director decides and owns lifecycle; executors execute.</b> An
 * executor that cannot find an assigned project does nothing. It can no longer invent work.
 *
 * <p>Deliberately <b>not</b> a {@code Goal}: it claims no flags and competes for nothing (D-MIW-001).
 * It is ticked by the existing flagless observer, so it adds no goal-arbitration surface. Choosing
 * <em>who may act</em> on a decision is MI-14C's job and is not attempted here.
 */
public final class MiningDirector {

    /** Sampled rim/surface pairs for heading choice, supplied by the caller. */
    @FunctionalInterface
    public interface TerrainSampler {
        int[] sampleAt(int x, int z);
    }

    private MiningDirector() {
    }

    /**
     * Whether a new project of this kind may begin.
     *
     * <p>An unresolved outcome is a claim on the next decision: every handoff reason blocks a fresh
     * controlled descent until it is consumed or expires (MI-14A-R1). Without that, the executor
     * that produced the outcome immediately out-competes the consumer that was supposed to act on
     * it — {@code ControlledDescentGoal} holds priority 3, {@code ExploringGoal} priority 8.
     */
    public static boolean mayStartControlledDescent(
            MiningProjectSavedData store, UUID mobId, NaturalDescentStatus status,
            boolean descentPressure) {
        if (store.projectOf(mobId).isPresent()) {
            return false;
        }
        if (!descentPressure) {
            return false;
        }
        if (store.pendingTransition(mobId)
                .filter(MiningTransition::blocksControlledDescentRestart)
                .isPresent()) {
            return false;
        }
        return NaturalDescentExhaustionPolicy.mayStartControlledDescent(status);
    }

    /**
     * Creates and assigns a controlled-descent project. The executor picks it up on its next
     * {@code canUse}; it never constructs one itself.
     */
    public static MiningProject startControlledDescent(
            ServerLevel level, Mob mob, MiningProjectSavedData store, Direction heading) {
        BlockPos origin = mob.blockPosition();
        MiningProject project =
                MiningProject.startControlledDescent(origin, heading, level.getGameTime());
        store.putProject(mob.getUUID(), project);
        store.putLease(
                mob.getUUID(),
                MiningExecutionLease.issued(
                        MiningProjectMode.CONTROLLED_DESCENT, level.getGameTime()));
        SpmScavenger.LOGGER.info(
                "[spmscavenger] director assigned CONTROLLED_DESCENT entity={} heading={} origin={}",
                mob.getId(), heading, origin);
        return project;
    }

    /**
     * Completes an assigned project and preserves its outcome atomically.
     *
     * <p>The executor reports the <em>fact</em> it observed; the director owns what that means for
     * persistence and for whatever acts next. Routing completion through one place is what stops a
     * terminal reason being emitted into a record that the same call deletes.
     */
    public static void completeProject(
            ServerLevel level, Mob mob, MiningProject project, MiningProjectEnd end, BlockPos at) {
        MiningTransition transition =
                MiningTransition.of(project, end, at, level.getGameTime());
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        store.completeProject(mob.getUUID(), end, transition);
        store.clearLease(mob.getUUID());
        SpmScavenger.LOGGER.info(
                "[spmscavenger] director completed mode={} entity={} reason={} at={} heading={}",
                project.mode(), mob.getId(), end, transition.at(), transition.heading());
    }

    /**
     * MI-14-R2 — complete with a real discovery, so the transition names a destination.
     *
     * <p>Without this the handoff carried {@code target = unresolved} and the staircase's own
     * heading, which is the difference between "something happened" and "go here".
     */
    public static void completeWithOpening(
            ServerLevel level, Mob mob, MiningProject project, CaveOpening opening, BlockPos at) {
        MiningTransition transition = new MiningTransition(
                project.mode(), MiningProjectEnd.CAVE_FOUND, at,
                opening.continuation(), opening.landing(), level.getGameTime());
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        store.completeProject(mob.getUUID(), MiningProjectEnd.CAVE_FOUND, transition);
        store.clearLease(mob.getUUID());
        SpmScavenger.LOGGER.info(
                "[spmscavenger] director completed mode={} entity={} reason=CAVE_FOUND kind={} "
                        + "at={} landing={} continuation={}",
                project.mode(), mob.getId(), opening.kind(), at,
                opening.landing(), opening.continuation());
    }

    /**
     * MI-14C1 — why controlled descent cannot execute right now, or {@link ExecutionBlocker#NONE}.
     *
     * <p>Lives here rather than in the executor because the executor must stop owning the
     * consequences of its own preconditions failing. It previously tested all of these
     * <em>before</em> looking up its assignment, so any of them failing after assignment left a
     * {@code RUNNING} project that blocked every future decision and could never time out.
     */
    public static ExecutionBlocker controlledDescentBlocker(
            ServerLevel level, Mob mob, ScavengerConfig cfg) {
        if (!cfg.enabled || !cfg.gatherResources || !cfg.exploring) {
            return ExecutionBlocker.FEATURE_DISABLED;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return ExecutionBlocker.WORLD_RULE_DISABLED;
        }
        if (mob.getTarget() != null) {
            return ExecutionBlocker.COMBAT_TARGET;
        }
        if (ToolTierPolicy.tierOfPick(
                        PlayerMobs.backpack(mob), mob.getMainHandItem(), mob.getOffhandItem())
                == ToolTier.NONE) {
            return ExecutionBlocker.CAPABILITY_MISSING;
        }
        return ExecutionBlocker.NONE;
    }

    /**
     * Evaluates the lease and performs its decision. Returns whether the executor may run.
     *
     * <p>Must be reachable even when the executor cannot be — that is the whole point. A blocked
     * assignment which nothing evaluates is exactly the zombie this repairs.
     */
    public static boolean authorizeExecution(
            ServerLevel level, Mob mob, MiningProjectSavedData store, MiningProject project,
            ExecutionBlocker blocker) {

        long now = level.getGameTime();
        MiningExecutionLease lease = store.leaseOf(mob.getUUID())
                .orElseGet(() -> {
                    // Assigned before leases existed, or persisted without one. Treat now as the
                    // assignment time rather than revoking work that may be perfectly healthy.
                    MiningExecutionLease issued = MiningExecutionLease.issued(project.mode(), now);
                    store.putLease(mob.getUUID(), issued);
                    return issued;
                });

        MiningExecutionLease withBlocker = lease.recordBlocker(blocker, now);
        ExecutionLeasePolicy.LeaseOutcome outcome = ExecutionLeasePolicy.evaluate(
                blocker,
                withBlocker.everStarted(),
                withBlocker.assignedAt(),
                withBlocker.blockedSince(),
                now);

        if (outcome.revoked()) {
            MiningProjectEnd reason = outcome.revokeReason();
            long blockedFor = withBlocker.blockedSince() >= 0L
                    ? now - withBlocker.blockedSince()
                    : 0L;
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] director revoked mode={} entity={} blocker={} reason={} "
                            + "blockedFor={} assignedAge={} everStarted={}",
                    project.mode(), mob.getId(), blocker, reason,
                    blockedFor, now - withBlocker.assignedAt(), withBlocker.everStarted());
            completeProject(level, mob, project, reason, mob.blockPosition());
            return false;
        }
        if (outcome.authorized()) {
            store.putLease(mob.getUUID(), withBlocker.resumed());
            return true;
        }
        store.putLease(mob.getUUID(), withBlocker.suspended());
        return false;
    }

    /**
     * Enforces the lease for whatever this mob is assigned, independent of any executor running.
     *
     * <p>Called from the flagless observer <b>before</b> its own preconditions, because those
     * preconditions are precisely the conditions under which an assignment gets stranded.
     */
    public static void enforceLease(
            ServerLevel level, Mob mob, MiningProjectSavedData store, ScavengerConfig cfg) {
        Optional<MiningProject> assigned =
                assignedProject(store, mob.getUUID(), MiningProjectMode.CONTROLLED_DESCENT);
        if (assigned.isEmpty()) {
            if (store.leaseOf(mob.getUUID()).isPresent()) {
                store.clearLease(mob.getUUID());
            }
            return;
        }
        authorizeExecution(
                level, mob, store, assigned.get(), controlledDescentBlocker(level, mob, cfg));
    }

    /** The executor reports that it has actually begun. Only it knows this. */
    public static void markExecutorStarted(ServerLevel level, Mob mob) {
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        store.leaseOf(mob.getUUID())
                .ifPresent(lease -> store.putLease(
                        mob.getUUID(), lease.started(level.getGameTime())));
    }

    /** The project this mob is assigned, if any. Executors ask; they do not create. */
    public static Optional<MiningProject> assignedProject(
            MiningProjectSavedData store, UUID mobId, MiningProjectMode mode) {
        return store.projectOf(mobId).filter(project -> project.mode() == mode);
    }
}
