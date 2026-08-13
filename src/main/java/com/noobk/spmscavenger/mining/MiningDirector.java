package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceEmitters;
import com.noobk.spmscavenger.experience.MiningTerminalSemantics;
import com.noobk.spmscavenger.experience.ExperienceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.EnumSet;

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
            boolean descentPressure, boolean hasMiningCapability, long now) {
        // Gate RET-1c - never create a project for an executor whose mandatory capability is
        // already known to be absent.
        //
        // Admission used to ask only "is there work to do", while the executor's blocker asked
        // "can I dig". A mob with no pickaxe therefore produced: assign -> CAPABILITY_MISSING ->
        // revoke -> retire -> assign, every observer pass, forever. Each cycle allocated a project,
        // a lease and a transition, marked saved data dirty, wrote three log lines and emitted
        // experience events - a churn loop that manufactures the retention RET-1a hunts for.
        //
        // The correct state for a mob that wants diamonds and owns no pickaxe is "prerequisite:
        // obtain a pickaxe", not "impossible mining job, retried forever".
        if (!hasMiningCapability) {
            return false;
        }
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
        if (store.hasActiveCaveContinuation(mobId, now)) {
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
        // D-GAO-024 - capture execution evidence BEFORE lifecycle cleanup. everStarted lives on the
        // lease and clearLease is two lines away; a learning layer that queried it afterwards would
        // read an absent record and treat every terminal as never-started.
        MiningTerminalSemantics semantics = MiningTerminalSemantics.of(
                end, store.leaseOf(mob.getUUID()).orElse(null));
        store.completeProject(mob.getUUID(), end, transition);
        store.clearLease(mob.getUUID());
        // D-GAO-024 follow-up: report the evidence, not a verdict. The final learning decision
        // depends on the execution-failure count, which the pipeline increments *after* this line -
        // so logging mayLearnPreference() here printed learnable=false for terminals that then went
        // on to teach. A truthful log of inputs beats a confident log of the wrong conclusion.
        SpmScavenger.LOGGER.info(
                "[spmscavenger] director completed mode={} entity={} reason={} at={} heading={} "
                        + "everStarted={} outcome={} cause={}",
                project.mode(), mob.getId(), end, transition.at(), transition.heading(),
                semantics.everStarted(), semantics.outcome(), semantics.cause());
        ExperienceEmitters.miningTerminal(mob, project, semantics, at, level.getGameTime());
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
    /**
     * Step 2.5 — blockers shared by every deliberate excavation mode.
     *
     * <p>Renamed from {@code controlledDescentBlocker}: every clause here (feature switch,
     * {@code mobGriefing}, combat, host order, pickaxe) governs any mode that deliberately breaks
     * blocks. Leaving the shared lifecycle API named after one mode is how the third mode ends up
     * bolted around it instead of through it. Mode-specific physical checks stay in executors.
     */
    public static ExecutionBlocker miningExecutionBlocker(
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
        PlayerMobs.StayAnchorState stayAnchor = PlayerMobs.stayAnchorState(mob);
        if (stayAnchor == PlayerMobs.StayAnchorState.PRESENT) {
            return ExecutionBlocker.PLAYER_ORDER;
        }
        if (stayAnchor == PlayerMobs.StayAnchorState.UNAVAILABLE) {
            // Fail closed: an unreadable host order must not be silently overridden by mining.
            return ExecutionBlocker.FEATURE_DISABLED;
        }
        if (ToolTierPolicy.tierOfPick(
                        PlayerMobs.backpack(mob), mob.getMainHandItem(), mob.getOffhandItem())
                == ToolTier.NONE) {
            return ExecutionBlocker.CAPABILITY_MISSING;
        }
        return ExecutionBlocker.NONE;
    }

    /**
     * MI-14C2 — base preconditions plus scheduler contention when an actionable intent exists but a
     * chore that should yield still owns {@code MOVE}.
     */
    public static ExecutionBlocker resolveMiningExecutionBlocker(
            ServerLevel level,
            Mob mob,
            ScavengerConfig cfg,
            MiningProjectSavedData store,
            @Nullable Goal callingGoal) {
        ExecutionBlocker blocker = miningExecutionBlocker(level, mob, cfg);
        if (!blocker.permitsExecution()) {
            return blocker;
        }
        return SchedulerConflictPolicy.resolveBlocker(
                mob,
                callingGoal,
                store,
                level.getGameTime(),
                EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
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
        ExecutionLeasePolicy.LeaseOutcome outcome =
                ExecutionLeasePolicy.evaluate(blocker, withBlocker, now);

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
        // Step 2.5 — serve whatever executable project is assigned, not one hard-coded mode.
        // Asking only for CONTROLLED_DESCENT meant a running TUNNEL_SEARCH project looked orphaned:
        // the observer deleted its lease, the executor recreated one, and every guarantee the lease
        // provides - start window, progress watchdog, cooperative pause accounting - stopped being
        // persistent for the second mode.
        Optional<MiningProject> stored = store.projectOf(mob.getUUID());
        // M5 - retire a stored-but-not-running project.
        //
        // completeProject keeps RUNNING/INTERRUPTED/RETRY, so a NO_PROGRESS or LEASE_EXPIRED
        // revocation left the record in place. mayStartControlledDescent and claimTunnelSearch both
        // refuse while projectOf is present, and this observer skipped it because it is not active -
        // so one progress-lease revocation permanently ended all mining for that mob. Loop A
        // returning through the persistence rule instead of the lease.
        //
        // Retiring rather than resuming is the honest option while no resumption path exists.
        if (stored.isPresent() && !stored.get().isActive()) {
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] director retired non-resumable project mode={} entity={} "
                            + "reason={}",
                    stored.get().mode(), mob.getId(), stored.get().endReason());
            store.clearProject(mob.getUUID());
            store.clearLease(mob.getUUID());
            store.clearExposure(mob.getUUID());
            return;
        }
        Optional<MiningProject> assigned = stored
                .filter(MiningProject::isActive)
                .filter(project -> ExecutionIntentPolicy.intentOf(project.mode()).isPresent());
        if (assigned.isEmpty()) {
            if (store.leaseOf(mob.getUUID()).isPresent()) {
                store.clearLease(mob.getUUID());
            }
            return;
        }
        MiningProject project = assigned.get();
        // A lease issued for another mode cannot authorize this one. Reissue rather than honour it.
        store.leaseOf(mob.getUUID())
                .filter(lease -> lease.mode() != project.mode())
                .ifPresent(stale -> store.putLease(
                        mob.getUUID(),
                        MiningExecutionLease.issued(project.mode(), level.getGameTime())));
        authorizeExecution(
                level,
                mob,
                store,
                project,
                resolveMiningExecutionBlocker(level, mob, cfg, store, null));
    }

    /** The executor reports that it has actually begun. Only it knows this. */
    public static void markExecutorStarted(ServerLevel level, Mob mob) {
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        store.leaseOf(mob.getUUID())
                .ifPresent(lease -> store.putLease(
                        mob.getUUID(), lease.started(level.getGameTime())));
    }

    /**
     * MI-14C3 — records observable executor progress, never goal liveness or replanning.
     *
     * <p>Callers are deliberately narrow: successful planned block removal, completed stair step,
     * or terminal handoff. Keeping this out of {@code tick()} is what lets the lease detect a live
     * but physically stuck goal.
     */
    public static void markExecutionProgress(ServerLevel level, Mob mob) {
        markExecutionProgress(
                level, mob, ExperienceKind.BLOCK_BROKEN, ExperienceCause.MINING_BLOCK_REMOVED);
    }

    public static void markExecutionProgress(
            ServerLevel level,
            Mob mob,
            ExperienceKind kind,
            ExperienceCause cause) {
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        store.leaseOf(mob.getUUID())
                .filter(MiningExecutionLease::everStarted)
                .ifPresent(lease -> store.putLease(
                        mob.getUUID(), lease.markProgress(level.getGameTime())));
        store.projectOf(mob.getUUID()).ifPresent(project ->
                ExperienceEmitters.miningProgress(mob, project, kind, cause, level.getGameTime()));
    }

    /** MI-14C1-R2 — whether an executor may persist interruption state after {@code stop()}. */
    public static boolean shouldPersistExecutorCheckpoint(
            MiningProjectSavedData store, UUID mobId, MiningProject localCopy) {
        return store.projectOf(mobId)
                .filter(MiningProject::isActive)
                .filter(stored -> stored.matchesSession(localCopy))
                .isPresent();
    }
    /**
     * Step 2.5 — atomically claim a pending {@code HANDOFF_TUNNEL_SEARCH} as a real project.
     *
     * <p>The cave-handoff lesson applied to the tunnel: consuming the transition, creating the
     * project and issuing the lease are one operation, so no interleaving can leave a consumed
     * handoff with no project or a project with no lease.
     *
     * <p><b>Demand is revalidated here, not trusted from the handoff.</b> The descent observed
     * diamond demand when it emitted the transition; by the time the tunnel gets scheduler access
     * the mob may have obtained diamonds elsewhere. A satisfied requirement retires the handoff
     * instead of starting deliberate excavation on fossilised intent.
     *
     * @return the created project, or empty when nothing was claimed
     */
    public static Optional<MiningProject> claimTunnelSearch(
            ServerLevel level, Mob mob, MiningProjectSavedData store, ScavengerConfig cfg) {

        Optional<MiningTransition> pending = store.pendingTransition(mob.getUUID())
                .filter(transition ->
                        transition.reason() == MiningProjectEnd.HANDOFF_TUNNEL_SEARCH);
        if (pending.isEmpty() || store.projectOf(mob.getUUID()).isPresent()) {
            return Optional.empty();
        }
        MiningTransition handoff = pending.get();

        if (!tunnelDemandStillLive(mob, cfg)) {
            store.consumeTransition(mob.getUUID());
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] director retired stale HANDOFF_TUNNEL_SEARCH entity={} "
                            + "reason=demand_satisfied",
                    mob.getId());
            return Optional.empty();
        }

        MiningProject project = MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH,
                mob.blockPosition(),
                handoff.heading(),
                MiningBudget.controlledDescentDefaults(),
                level.getGameTime());
        Optional<MiningProject> claimed =
                store.claimTunnelSearchHandoff(mob.getUUID(), handoff, project, level.getGameTime());
        claimed.ifPresent(started -> SpmScavenger.LOGGER.info(
                "[spmscavenger] director claimed TUNNEL_SEARCH entity={} heading={} origin={}",
                mob.getId(), started.heading(), started.origin()));
        return claimed;
    }

    /** Current demand, not the demand that existed when the handoff was emitted. */
    private static boolean tunnelDemandStillLive(Mob mob, ScavengerConfig cfg) {
        if (!WorkDemandPolicy.isDiamondLocalGatherEligible(mob.blockPosition().getY())) {
            return false;
        }
        return WorkDemandPolicy.diamondProgressionDemand(
                        PlayerMobs.backpack(mob),
                        mob.getMainHandItem(),
                        mob.getOffhandItem(),
                        cfg)
                > 0;
    }

    /** The project this mob is assigned, if any. Executors ask; they do not create. */
    public static Optional<MiningProject> assignedProject(
            MiningProjectSavedData store, UUID mobId, MiningProjectMode mode) {
        // M5 - only a RUNNING project is an assignment. Nothing in the codebase resumes an
        // INTERRUPTED/RETRY project (MiningProjectEnd.resumable has zero consumers), so adopting
        // one made an executor start, plan, and stop again every tick against a dead record.
        return store.projectOf(mobId)
                .filter(MiningProject::isActive)
                .filter(project -> project.mode() == mode);
    }
}
