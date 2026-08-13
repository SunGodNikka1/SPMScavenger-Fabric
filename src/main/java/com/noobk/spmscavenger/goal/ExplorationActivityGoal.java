package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.opinion.SocialIntent;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.DescentPressurePolicy;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import com.noobk.spmscavenger.opinion.AffectiveStateService;
import com.noobk.spmscavenger.opinion.ActivityAdmission;
import com.noobk.spmscavenger.opinion.ActivityAdmissions;
import com.noobk.spmscavenger.opinion.ActivityContinuation;
import com.noobk.spmscavenger.opinion.ActivityContinuations;
import com.noobk.spmscavenger.opinion.DiscretionaryActivityDirector;
import com.noobk.spmscavenger.opinion.DiscretionaryActivity;
import com.noobk.spmscavenger.opinion.DiscretionaryAvailability;
import com.noobk.spmscavenger.opinion.ExploreAdmission;
import com.noobk.spmscavenger.opinion.ExploreReadinessThresholds;
import com.noobk.spmscavenger.opinion.PassiveExpressionService;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.GameRules;
import net.minecraft.server.level.ServerLevel;
import com.noobk.spmscavenger.mining.NaturalDescentStatus;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MiningDirector;
import com.noobk.spmscavenger.mining.ExecutionBlocker;
import com.noobk.spmscavenger.mining.ExecutionIntent;
import com.noobk.spmscavenger.mining.SchedulerConflictPolicy;
import com.noobk.spmscavenger.DescentHeadingPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * A staggered, flagless observer that distinguishes real SPM work from harmless idling. Unknown
 * running goals are deliberately treated as work, which makes this fail safely with future SPM or
 * third-party goals rather than starting an expedition over them.
 *
 * <p>This subclasses {@link RandomLookAroundGoal} solely because SPM 0.86.0's
 * {@code ObjectiveReadout#isNoise} defines that vanilla type as its public observable contract for
 * cosmetic/background goals. The observer does not call the superclass look behaviour and clears
 * its flags. Keeping it outside the visible objective stack prevents the bookkeeping label
 * "Exploration activity" from appearing beside the mob's real action.
 */
public final class ExplorationActivityGoal extends RandomLookAroundGoal {

    private static final int OBSERVE_INTERVAL = 10;

    private final PathfinderMob mob;
    private final GoalSelector selector;
    private final ExplorationReadiness readiness;
    private final boolean allowNewMiningWork;
    @Nullable
    private final SeekShelterGoal shelterGoal;
    @Nullable
    private final CampfireGoal campfireGoal;

    public ExplorationActivityGoal(
            PathfinderMob mob, GoalSelector selector, ExplorationReadiness readiness) {
        this(mob, selector, readiness, true, null, null);
    }

    public ExplorationActivityGoal(
            PathfinderMob mob,
            GoalSelector selector,
            ExplorationReadiness readiness,
            boolean allowNewMiningWork) {
        this(mob, selector, readiness, allowNewMiningWork, null, null);
    }

    public ExplorationActivityGoal(
            PathfinderMob mob,
            GoalSelector selector,
            ExplorationReadiness readiness,
            boolean allowNewMiningWork,
            @Nullable SeekShelterGoal shelterGoal) {
        this(mob, selector, readiness, allowNewMiningWork, shelterGoal, null);
    }

    public ExplorationActivityGoal(
            PathfinderMob mob,
            GoalSelector selector,
            ExplorationReadiness readiness,
            boolean allowNewMiningWork,
            @Nullable SeekShelterGoal shelterGoal,
            @Nullable CampfireGoal campfireGoal) {
        super(mob);
        this.mob = mob;
        this.selector = selector;
        this.readiness = readiness;
        this.allowNewMiningWork = allowNewMiningWork;
        this.shelterGoal = shelterGoal;
        this.campfireGoal = campfireGoal;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    /** Do not inherit any cosmetic look lifecycle; only the host readout classification is used. */
    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        if (Math.floorMod(mob.tickCount + mob.getId(), OBSERVE_INTERVAL) != 0) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        if (handleDisabledCadence(
                cfg.enabled,
                () -> directorTick(false),
                readiness::recordMeaningfulWork)) {
            return;
        }

        MiningProjectSavedData observationStore = mob.level() instanceof ServerLevel level
                ? MiningProjectSavedData.get(level)
                : null;
        ActivityObservationService.Observation observation = ActivityObservationService.observe(
                selector, mob, observationStore, mob.level().getGameTime());
        if (shelterGoal != null) {
            shelterGoal.observeScheduler(observation);
        }

        if (observation.meaningfulWorkForExpedition()) {
            readiness.recordMeaningfulWork();
        } else if (!observation.exploring()) {
            readiness.recordIdleTicks(OBSERVE_INTERVAL);
        }

        RestSessionCoordinator.validate(mob, observation, mob.level().getGameTime());
        AffectiveStateService.observe(mob, observation, OBSERVE_INTERVAL);
        PassiveExpressionService.observe(mob, observation);
        DiscretionaryAvailability availability =
                new DiscretionaryAvailability(cfg.exploring, cfg.campfire, PlayerMobs.available());
        long now = mob.level().getGameTime();
        int idleTicks = ExploreReadinessThresholds.idleTicks(cfg, mob.getUUID());
        int tripThreshold = cfg.exploreLocalTripsThreshold;
        ActivityAdmission exploreAdmission = ExploreAdmission.inspect(
                availability.hasExecutor(DiscretionaryActivity.EXPLORE),
                readiness,
                now,
                tripThreshold,
                idleTicks);
        ActivityAdmission restAdmission = campfireGoal == null
                ? ActivityAdmission.executorAbsent()
                : campfireGoal.inspectAdmission(now);
        SocialIntent freshSocialOpportunity = com.noobk.spmscavenger.opinion.SocialTargetResolver
                .resolve(mob, now)
                .orElse(null);
        SocialIntent boundSocialSubject =
                com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry
                        .runningSubject(mob.getUUID())
                        .orElse(null);
        SocialIntent socialOpportunity =
                boundSocialSubject != null ? boundSocialSubject : freshSocialOpportunity;
        ActivityAdmission socialAdmission = socialOpportunity == null
                ? ActivityAdmission.blocked(
                        PlayerMobs.available(),
                        com.noobk.spmscavenger.opinion.ActivityAdoptionBlocker.NO_SOCIAL_TARGET,
                        "SPM named no live greet target")
                : ActivityAdmission.ready(PlayerMobs.available());
        ActivityAdmissions admissions =
                ActivityAdmissions.of(exploreAdmission, restAdmission, socialAdmission);
        // D-GAO-050 - real continuation snapshots, never derived from adoptionReady. Adoption and
        // continuation stay independently observable, so a running incumbent with a closed adoption
        // window is representable as adoptable=false + continuable=true and keeps competing on its
        // actual utility rather than being deleted from the comparison.
        ExploringGoal exploringGoal = ControlledDescentGoal.exploringGoalOf(mob);
        ActivityContinuations continuations = ActivityContinuations.of(
                exploringGoal == null
                        ? ActivityContinuation.notRunning()
                        : exploringGoal.inspectContinuation(now),
                campfireGoal == null
                        ? ActivityContinuation.notRunning()
                        : campfireGoal.inspectContinuation(now),
                boundSocialSubject == null
                        ? ActivityContinuation.notRunning()
                        : ActivityContinuation.valid());

        DiscretionaryActivityDirector.tick(
                mob.getUUID(),
                now,
                observation,
                availability,
                mob.getTarget() != null,
                admissions,
                continuations,
                socialOpportunity);

        boolean mayCreateWork = permitsNewMiningWork(cfg.enabled, allowNewMiningWork);
        if (mayCreateWork) {
            updateDescentPressure();
        }
        directorTick(mayCreateWork);
    }

    /**
     * GAO-0-B1 — disabled activity observation still owns mining lease cleanup.
     *
     * <p>The cleanup callback deliberately has no assignment callback alongside it. This keeps the
     * disabled path structurally limited to existing-authority settlement followed by the legacy
     * readiness reset. Returning {@code true} tells the caller to stop before activity observation,
     * descent pressure, handoff claims, or assignment.
     */
    static boolean handleDisabledCadence(
            boolean enabled, Runnable enforceExistingLease, Runnable recordMeaningfulWork) {
        if (enabled) {
            return false;
        }
        enforceExistingLease.run();
        recordMeaningfulWork.run();
        return true;
    }

    static boolean permitsNewMiningWork(boolean enabled, boolean installedWithExecutorStack) {
        return enabled && installedWithExecutorStack;
    }

    /**
     * MI-14B — the director runs on this flagless observer's cadence.
     *
     * <p>It claims no goal flags and competes for nothing, so hosting it here adds no arbitration
     * surface. The decision it makes is recorded as an assigned {@link MiningProject};
     * {@code ControlledDescentGoal} picks that up on its next {@code canUse} and can no longer
     * create one itself.
     */
    private void directorTick(boolean allowNewWork) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        // MI-14C1: enforce the lease FIRST. Every early return below is a condition under which an
        // assignment gets stranded, so evaluating leases after them reproduces the deadlock inside
        // the director itself: combat or mobGriefing would stop the one component able to revoke.
        MiningDirector.enforceLease(level, mob, store, cfg);

        // GAO-0-B1: global disable reaches lease settlement, but this pass is cleanup-only. Keeping
        // this guard ahead of every assignment/handoff branch makes the negative authority explicit.
        if (!allowNewWork) {
            return;
        }
        if (!cfg.enabled || !cfg.gatherResources || !cfg.exploring) {
            return;
        }
        if (mob.getTarget() != null
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        // Persistent player authority prevents assignment even while StayNearGoal is not actively
        // pathing. UNAVAILABLE fails closed so an SPM API rename cannot silently bypass the order.
        if (PlayerMobs.stayAnchorState(mob) != PlayerMobs.StayAnchorState.ABSENT) {
            return;
        }
        // M1 - claim a pending HANDOFF_TUNNEL_SEARCH before considering a new descent.
        // Without this call the transition blocks mayStartControlledDescent forever and nothing
        // consumes it: the mob finishes its staircase at the diamond band and simply stops. An
        // implemented, unit-tested claim API with no caller is still a dead leaf.
        if (MiningDirector.claimTunnelSearch(level, mob, store, cfg).isPresent()) {
            return;
        }

        ExploringGoal exploring = ControlledDescentGoal.exploringGoalOf(mob);
        if (exploring == null) {
            return;
        }
        long now = level.getGameTime();
        NaturalDescentStatus status = exploring.naturalDescentStatus(level, now);
        // RET-1c: the same capability the executor's blocker rejects on, asked before assigning.
        boolean hasMiningCapability = ToolTierPolicy.tierOfPick(
                        PlayerMobs.backpack(mob), mob.getMainHandItem(), mob.getOffhandItem())
                != ToolTier.NONE;
        if (!MiningDirector.mayStartControlledDescent(
                store, mob.getUUID(), status, readiness.hasDescentPressure(),
                hasMiningCapability, now)) {
            return;
        }
        ExecutionBlocker admissionBlocker = SchedulerConflictPolicy.resolveBlocker(
                mob,
                null,
                store,
                now,
                EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK),
                ExecutionIntent.CONTROLLED_DESCENT);
        if (SchedulerConflictPolicy.preventsAssignment(admissionBlocker)) {
            return;
        }
        DescentHeadingPolicy.Heading heading = DescentHeadingPolicy.chooseBest(
                mob.getX(),
                mob.getZ(),
                mob.blockPosition().getY(),
                (x, z) -> new int[] {
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                    ControlledDescentGoal.sampleLocalRim(level, x, z)
                },
                0,
                sector -> false,
                12,
                mob.getRandom());
        MiningDirector.startControlledDescent(level, mob, store, heading.direction());
    }

    /** MI-5 / D-MIW-031: progression diamond need above the band unlocks explore sooner. */
    private void updateDescentPressure() {
        ScavengerConfig cfg = ScavengerConfig.get();
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null || !cfg.enabled || !cfg.craftTools || !cfg.exploring) {
            readiness.clearDescentPressure();
            return;
        }
        int progression = WorkDemandPolicy.diamondProgressionDemand(
                backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg);
        boolean local = WorkDemandPolicy.isDiamondLocalGatherEligible(mob.blockPosition().getY());
        if (DescentPressurePolicy.wantsDescentExplore(progression, local, false)) {
            readiness.recordDescentPressure();
        } else {
            readiness.clearDescentPressure();
        }
    }
}
