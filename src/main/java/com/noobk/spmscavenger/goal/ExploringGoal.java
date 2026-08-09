package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.CaveContextPolicy;
import com.noobk.spmscavenger.CaveLandingResolver;
import com.noobk.spmscavenger.CaveOpportunityPolicy;
import com.noobk.spmscavenger.CaveOpportunitySelection;
import com.noobk.spmscavenger.DescentHeadingPolicy;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.mining.MiningExecutionGuard;
import com.noobk.spmscavenger.mining.MiningGoalKind;
import com.noobk.spmscavenger.mining.MiningTransition;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.mining.NaturalDescentExhaustionPolicy;
import com.noobk.spmscavenger.mining.NaturalDescentSearchState;
import com.noobk.spmscavenger.mining.NaturalDescentStatus;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Low-priority, generic spatial exploration. It knows nothing about resources, structures or POIs:
 * its only job is to move into less-recently-used active territory and let SPM's higher-priority
 * goals react to whatever the mob encounters there.
 *
 * <h2>State boundary</h2>
 *
 * {@link ExpeditionState} owns the durable intent (heading, immutable waypoint centres, index,
 * failures and timestamps). {@link NavigationState} owns the disposable Minecraft {@link Path}.
 * {@link #stop()} always drops navigation state and deliberately preserves expedition state, so an
 * interruption recalculates a fresh path to the same remaining waypoint.
 */
public final class ExploringGoal extends Goal {

    private static final int MIN_STAGES = 2;
    private static final int MAX_STAGES = 4;
    private static final double MAX_EXPEDITION_DISTANCE = 150.0;
    private static final int ROUTE_CANDIDATES = 8;
    private static final int LANDING_RADIUS = 4;
    /** Total path probes one planning call may spend, shared across every hop length it tries. */
    private static final int LANDING_PROBES_PER_PLAN = 20;
    /** Per hop length, so a crowded first rung cannot starve the shorter fallbacks behind it. */
    private static final int LANDING_PROBES_PER_HOP = 8;
    /** Shortest hop worth asking for; below this the mob is better off wandering for a moment. */
    private static final double MIN_PATH_STEP = 6.0;
    /** A cave handoff older than this is stale — the mob or the world has moved on. */
    private static final int CAVE_HANDOFF_LIFETIME_TICKS = 400;
    /** Cave continuation is short and local; the opening is right there. */
    private static final double CAVE_HANDOFF_ROUTE_BLOCKS = 48.0;
    /** A landing this far above or below the mob is a roof or a cliff top, not the next step. */
    private static final int MAX_LANDING_ELEVATION = 16;
    private static final int MAX_WAYPOINT_FAILURES = 3;
    private static final int MAX_EXPEDITION_FAILURES = 6;
    private static final int REPLAN_DELAY_TICKS = 20;
    /**
     * Maximum lifetime of an expedition. Public because MI-14C2-R2 bounds cave-continuation
     * authority by it: the commitment protecting a continuation must not expire while the
     * expedition it protects is still legally running, and duplicating the number into the
     * mining package would let the two drift apart silently.
     */
    public static final int MAX_EXPEDITION_TICKS = 2400;
    private static final int STALL_TICKS = 100;
    private static final int NAVIGATION_DONE_GRACE_TICKS = 20;
    private static final int COOLDOWN_TICKS = 600;
    private static final int REGION_SIZE_CHUNKS = 4;
    private static final int REGION_MEMORY_LIMIT = 10;
    private static final int HEADING_SECTORS = 12;
    private static final int HEADING_MEMORY_LIMIT = 6;
    private static final double ARRIVAL_DISTANCE_SQR = 2.25;
    private static final double PROGRESS_EPSILON_SQR = 0.25;
    private static final double FORWARD_SKIP_MARGIN = 8.0;
    private static final double LATERAL_REJOIN_DISTANCE = 32.0;
    private static final double REJOIN_ADVANCE = 24.0;

    private final PathfinderMob mob;
    private final ExplorationReadiness readiness;
    private final ArrayDeque<Long> recentVisitedRegions = new ArrayDeque<>();
    private final ArrayDeque<Long> recentExpeditionDestinations = new ArrayDeque<>();
    private final ArrayDeque<Integer> recentCompletedHeadings = new ArrayDeque<>();

    private ExpeditionState expedition;
    private NavigationState navigationState;
    private long retryAfterTick;
    private final NaturalDescentSearchState descentSearch = new NaturalDescentSearchState();

    public ExploringGoal(PathfinderMob mob, ExplorationReadiness readiness) {
        this.mob = mob;
        this.readiness = readiness;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.exploring || mob.getTarget() != null || mob.isPassenger()
                || mob.isSleeping() || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }

        long now = level.getGameTime();
        if (!MiningExecutionGuard.permits(
                mob, this, MiningGoalKind.classifyExploring(
                        MiningProjectSavedData.get(level), mob.getUUID(), now))) {
            return false;
        }
        if (!readiness.hasDescentPressure()) {
            descentSearch.reset();
        }
        if (yieldToStayAnchor(level, now)) {
            return false;
        }

        // MI-14A: a cave the mob just dug into outranks whatever it was doing before. Checked ahead
        // of the retry window and of readiness, because the stale expedition is exactly what would
        // otherwise win: stop() deliberately preserves it, so without this the mob breaks into a
        // cave, the project is deleted, and it resumes walking to a surface waypoint chosen minutes
        // earlier.
        if (acceptCaveHandoff(level, now)) {
            return true;
        }

        // A failed plan releases MOVE for this window instead of standing on it, so the mob keeps
        // moving under local wandering while the route re-resolves.
        if (now < retryAfterTick) {
            return false;
        }

        if (expedition == null) {
            if (!readiness.eligible(now, cfg.exploreLocalTripsThreshold, cfg.exploreIdleTicks)) {
                return false;
            }
            expedition = createExpedition(level, cfg);
            if (expedition == null) {
                readiness.consume(now + COOLDOWN_TICKS);
                return false;
            }
            readiness.consume(now + COOLDOWN_TICKS);
        } else if (now - expedition.startedTick > MAX_EXPEDITION_TICKS) {
            abandon(EndReason.STALE, now);
            return false;
        }

        rebaseAfterInterruption(level);
        if (expedition == null) {
            return false;
        }

        PlanResult result = planCurrentStage(level, now);
        if (result == PlanResult.READY) {
            // Only recruit once the leader has a route it can actually walk, so nobody is invited
            // on a journey that fails to start.
            if (!expedition.companionsInvited) {
                expedition.companionsInvited = true;
                inviteCompanions(level, cfg, now);
            }
            return true;
        }
        handlePlanFailure(result, now);
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (expedition == null) {
            return false;
        }
        if (mob.level() instanceof ServerLevel level
                && yieldToStayAnchor(level, level.getGameTime())) {
            return false;
        }
        if (mob.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            if (!MiningExecutionGuard.permits(
                    mob, this, MiningGoalKind.classifyExploring(
                            MiningProjectSavedData.get(level), mob.getUUID(), now))) {
                return false;
            }
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        return cfg.enabled && cfg.exploring && mob.getTarget() == null && !mob.isPassenger()
                && navigationState != null;
    }

    @Override
    public void start() {
        if (navigationState != null) {
            mob.getNavigation().moveTo(navigationState.path, exploreSpeed());
        }
    }

    @Override
    public void stop() {
        // The path belongs to this activation only. The heading and remaining waypoint list do not.
        mob.getNavigation().stop();
        navigationState = null;
        if (expedition != null && mob.level() instanceof ServerLevel level) {
            expedition.lastInterruptedTick = level.getGameTime();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || expedition == null) {
            return;
        }
        long now = level.getGameTime();
        if (readiness.hasDescentPressure() && descentSearch.isActive()) {
            descentSearch.recordTick();
            descentSearch.recordPosition(mob.blockPosition());
        }
        if (now - expedition.startedTick > MAX_EXPEDITION_TICKS) {
            abandon(EndReason.STALE, now);
            return;
        }

        if (navigationState == null) {
            return;
        }

        if (!travelFrontierStillSafe(level, navigationState.path, expedition.headingX, expedition.headingZ)) {
            abandon(EndReason.SIMULATION_FRONTIER, now);
            return;
        }

        double distanceSqr = mob.distanceToSqr(
                navigationState.target.getX() + 0.5,
                navigationState.target.getY(),
                navigationState.target.getZ() + 0.5);
        if (distanceSqr <= ARRIVAL_DISTANCE_SQR) {
            if (navigationState.intermediate) {
                advanceHop(level, now);
            } else {
                finishCurrentStage(level, now);
            }
            return;
        }

        if (ExplorationPolicy.madeProgress(
                navigationState.lastDistanceSqr, distanceSqr, PROGRESS_EPSILON_SQR)) {
            navigationState.lastDistanceSqr = distanceSqr;
            navigationState.lastProgressTick = now;
            navigationState.navigationDoneSinceTick = -1;
            expedition.lastProgressTick = now;
        } else {
            boolean navigationDone = mob.getNavigation().isDone();
            if (navigationDone && navigationState.navigationDoneSinceTick < 0) {
                navigationState.navigationDoneSinceTick = now;
            } else if (!navigationDone) {
                navigationState.navigationDoneSinceTick = -1;
            }
            long navigationDoneTicks = navigationState.navigationDoneSinceTick < 0
                    ? 0 : now - navigationState.navigationDoneSinceTick;
            if (ExplorationPolicy.navigationFailed(
                    navigationDone,
                    navigationDoneTicks,
                    now - navigationState.lastProgressTick,
                    NAVIGATION_DONE_GRACE_TICKS,
                    STALL_TICKS)) {
                handlePlanFailure(PlanResult.PATH_FAILURE, now);
            }
        }
    }

    /**
     * Asks nearby PlayerMobs this mob feels positively about to walk the same way.
     *
     * <h2>Why this is not built on SPM's friend following</h2>
     *
     * SPM already ships {@code FollowLovedOneGoal} at priority 2, and it does support mob-to-mob
     * company: {@code PlayerMobEntity.findFollowTarget()} scans every {@code LivingEntity} within
     * 64 blocks and accepts anything it feels <b>7.0 or more</b> toward. The reason two mobs are
     * almost never seen travelling together is not that goal - it is that the bond can hardly ever
     * reach 7.0 off a Dungeon Train. The one accrual event named for travel,
     * {@code FeelingLedger.travel}, routes to {@code FeelingRecord.afterCarriageAdvance}, which
     * only pays its +0.2 when the <em>carriage index changes</em>. In an ordinary world that index
     * is always {@code NO_CARRIAGE}, so it never changes, and shared journeys never count.
     *
     * <p>So this deliberately does not reimplement following, and equally deliberately does not
     * write to SPM's ledger to force the bond upward. It reads the same feeling SPM reads and uses
     * a far lower bar - merely <em>above neutral</em> - to decide who sets off together. Company on
     * the road is this mod's own decision to make; who counts as a real friend stays SPM's.
     */
    private void inviteCompanions(ServerLevel level, ScavengerConfig cfg, long now) {
        if (!cfg.exploreCompanions || expedition == null || expedition.waypoints.isEmpty()) {
            return;
        }
        int slots = Math.min(4, cfg.exploreCompanionMax);
        if (slots <= 0) {
            return;
        }
        double radius = Mth.clamp(cfg.exploreCompanionRadius, 4.0, 24.0);
        double routeBudget = expedition.waypoints.get(expedition.waypoints.size() - 1).forwardDistance;

        int taken = 0;
        for (Mob other : level.getEntitiesOfClass(
                Mob.class, mob.getBoundingBox().inflate(radius), this::travelsWith)) {
            if (taken >= slots) {
                break;
            }
            ExploringGoal companion = exploringGoalOf(other);
            if (companion == null || companion == this) {
                continue;
            }
            if (companion.acceptCompanionInvitation(
                    expedition.headingX, expedition.headingZ, routeBudget,
                    ExplorationPolicy.companionLateralOffset(taken), now)) {
                taken++;
            }
        }
        if (taken > 0) {
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] exploration departed entity={} companions={} stages={}",
                    mob.getId(), taken, expedition.waypoints.size());
        }
    }

    /** Mutual positive regard, read through SPM's own public accessor. Fails closed. */
    private boolean travelsWith(Mob other) {
        if (other == mob || !other.isAlive() || other.getTarget() != null
                || !PlayerMobs.isPlayerMob(other)
                || PlayerMobs.stayAnchorState(other) != PlayerMobs.StayAnchorState.ABSENT) {
            return false;
        }
        return ExplorationPolicy.travelsTogether(
                PlayerMobs.feelingToward(mob, other),
                PlayerMobs.feelingToward(other, mob),
                PlayerMobs.neutralFeeling());
    }

    private static ExploringGoal exploringGoalOf(Mob other) {
        GoalSelector selector = ((MobGoalSelectorAccessor) other).spmscavenger$getGoalSelector();
        if (selector == null) {
            return null;
        }
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ExploringGoal goal) {
                return goal;
            }
        }
        return null;
    }

    /**
     * Take up a companion's invitation, bypassing the ordinary readiness thresholds: a mob goes
     * because someone it likes is going, not because it has run out of things to do. Everything
     * else - the cooldown, combat, sleeping, the simulation boundary - still applies, so an
     * invitation can be declined and being asked is never a way around a safety condition.
     */
    private boolean acceptCompanionInvitation(
            double headingX, double headingZ, double routeBudget, double lateralBias, long now) {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.exploring || !cfg.exploreCompanions
                || expedition != null || now < retryAfterTick
                || mob.getTarget() != null || mob.isPassenger() || mob.isSleeping()
                || PlayerMobs.stayAnchorState(mob) != PlayerMobs.StayAnchorState.ABSENT
                || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        ExpeditionState invited = createExpedition(
                level, cfg, headingX, headingZ, routeBudget, lateralBias);
        if (invited == null) {
            return false;
        }
        expedition = invited;
        expedition.companionsInvited = true; // a guest does not recruit its own party
        readiness.consume(now + COOLDOWN_TICKS);
        return true;
    }

    /**
     * MI-14A — consume a pending {@code CAVE_FOUND} transition and rebase onto the opening.
     *
     * <p>Deliberately destructive: the previous expedition, its waypoints and its navigation are
     * discarded rather than resumed. That is the point of the handoff — the world changed under the
     * old plan, because the mob mined its way into somewhere better.
     *
     * @return true when a cave-continuation expedition was installed and planning should proceed
     */
    private boolean acceptCaveHandoff(ServerLevel level, long now) {
        ScavengerConfig cfg = ScavengerConfig.get();
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        Optional<MiningTransition> pending = MiningTransition.acceptableCaveHandoff(
                store.pendingTransition(mob.getUUID()), now, CAVE_HANDOFF_LIFETIME_TICKS);
        if (pending.isEmpty()) {
            return false;
        }
        MiningTransition handoff = pending.get();

        ExpeditionState rebased = createExpedition(
                level, cfg,
                handoff.heading().getStepX(), handoff.heading().getStepZ(),
                CAVE_HANDOFF_ROUTE_BLOCKS, 0.0);
        if (rebased == null) {
            // Leave it pending: failing to plan is not a reason to forget the cave.
            return false;
        }

        // MI-14C2-R2: the continuation is an expedition, so its own lifetime bounds the
        // authority protecting it. Passing it from here keeps the constant with its owner instead
        // of duplicating a number into the mining package that could drift out of step.
        if (!store.claimCaveContinuation(
                mob.getUUID(), handoff, now, MAX_EXPEDITION_TICKS)) {
            return false;
        }
        mob.getNavigation().stop();
        navigationState = null;
        expedition = rebased;
        expedition.caveHandoffContinuation = true;
        expedition.companionsInvited = true; // a handoff is not a recruiting opportunity
        readiness.consume(now + COOLDOWN_TICKS);
        SpmScavenger.LOGGER.info(
                "[spmscavenger] cave handoff accepted entity={} at={} heading={}",
                mob.getId(), handoff.at(), handoff.heading());
        return true;
    }

    private ExpeditionState createExpedition(ServerLevel level, ScavengerConfig cfg) {
        return createExpedition(level, cfg, Double.NaN, Double.NaN, MAX_EXPEDITION_DISTANCE, 0.0);
    }

    /**
     * Builds a route.
     *
     * <p>An invited companion passes a forced heading, the leader's route length and a sideways
     * bias, so it walks its <em>own</em> route beside the leader rather than trailing it. That is
     * the difference between company and a conga line, and it is also why this does not duplicate
     * SPM's {@code FollowLovedOneGoal}: nobody is following anybody, they simply left together.
     * A forced heading skips the heading and region history, because the mob is going where its
     * companion is going, not where it would have chosen.
     */
    private ExpeditionState createExpedition(
            ServerLevel level, ScavengerConfig cfg, double forcedHeadingX, double forcedHeadingZ,
            double routeBudget, double lateralBias) {
        RandomSource random = mob.getRandom();
        boolean forced = !Double.isNaN(forcedHeadingX) && !Double.isNaN(forcedHeadingZ);
        double maximumDistance = Math.max(MIN_STAGES * 16.0, Math.min(MAX_EXPEDITION_DISTANCE, routeBudget));
        double minimumStage = Mth.clamp(cfg.exploreMinStageDistance, 16.0, 64.0);
        double maximumStage = Mth.clamp(cfg.exploreMaxStageDistance, minimumStage, 80.0);

        // One cache for this whole call: the eight candidate routes overlap heavily, so a chunk
        // is inspected at most once no matter how many routes cross it. Discarded on return.
        ChunkInterest interest = forced ? null : new ChunkInterest(level);

        RouteCandidate best = null;
        if (!forced && readiness.hasDescentPressure()) {
            best = buildDescentRoute(
                    level, interest, maximumDistance, minimumStage, maximumStage, random);
        }

        if (best == null) {
        int attempts = forced ? 1 : ROUTE_CANDIDATES;
        for (int candidateIndex = 0; candidateIndex < attempts; candidateIndex++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double headingX = forced ? forcedHeadingX : Math.cos(angle);
            double headingZ = forced ? forcedHeadingZ : Math.sin(angle);
            int stageCount = MIN_STAGES + random.nextInt(MAX_STAGES - MIN_STAGES + 1);
            List<IntendedWaypoint> waypoints = new ArrayList<>(stageCount);
            double forward = 0.0;
            boolean insideSimulation = true;

            for (int stage = 0; stage < stageCount; stage++) {
                double stageDistance = minimumStage
                        + random.nextDouble() * (maximumStage - minimumStage);
                if (forward + stageDistance > maximumDistance) {
                    if (waypoints.size() >= MIN_STAGES) {
                        break;
                    }
                    stageDistance = maximumDistance - forward;
                }
                forward += stageDistance;
                double lateralLimit = Math.min(12.0, stageDistance * 0.30);
                double lateral = lateralBias + (random.nextDouble() * 2.0 - 1.0) * lateralLimit;
                int x = Mth.floor(ExplorationPolicy.projectedX(
                        mob.getX(), headingX, headingZ, forward, lateral));
                int z = Mth.floor(ExplorationPolicy.projectedZ(
                        mob.getZ(), headingX, headingZ, forward, lateral));
                IntendedWaypoint waypoint = new IntendedWaypoint(x, z, forward);
                if (!chunkGuardTicking(level, new ChunkPos(x >> 4, z >> 4), mob.blockPosition().getY())) {
                    insideSimulation = waypoints.size() >= MIN_STAGES;
                    break;
                }
                waypoints.add(waypoint);
            }

            if (!insideSimulation || waypoints.size() < MIN_STAGES) {
                continue;
            }

            int sector = ExplorationPolicy.headingSector(headingX, headingZ, HEADING_SECTORS);
            int score = 0;
            if (!forced) {
                // Novelty stays the main term. Interest is a bonus that breaks ties and biases the
                // choice; it is never a penalty, because most of a world worth exploring - caves,
                // forests, ridges, mineshafts, the bulk of a village - is made of ordinary blocks
                // and registers nothing at all.
                int accumulatedInterest = 0;
                score = random.nextInt(20)
                        - (recentCompletedHeadings.contains(sector) ? 35 : 0);
                for (IntendedWaypoint waypoint : waypoints) {
                    long region = regionKey(waypoint);
                    score -= recentVisitedRegions.contains(region) ? 20 : 0;
                    score -= recentExpeditionDestinations.contains(region) ? 100 : 0;
                    accumulatedInterest += interest.at(waypoint.x, waypoint.z);
                }
                score += ExplorationInterest.routeScore(accumulatedInterest);
            }
            RouteCandidate candidate = new RouteCandidate(headingX, headingZ, sector, waypoints, score);
            if (best == null || candidate.score > best.score) {
                best = candidate;
            }
        }
        }

        if (best == null) {
            return null;
        }
        long now = level.getGameTime();
        // Captured at creation: the journey keeps its identity even after the pressure clears.
        ExplorationIntent intent = readiness.hasDescentPressure()
                ? ExplorationIntent.DESCENT
                : ExplorationIntent.NORMAL;
        if (intent == ExplorationIntent.DESCENT) {
            descentSearch.beginSearch(mob.blockPosition());
        }
        return new ExpeditionState(
                intent, mob.getX(), mob.getZ(), best.headingX, best.headingZ,
                best.headingSector, List.copyOf(best.waypoints), now);
    }

    /**
     * MI-5H — terrain-scored macro heading for descent expeditions instead of novelty roulette.
     */
    private RouteCandidate buildDescentRoute(
            ServerLevel level,
            ChunkInterest interest,
            double maximumDistance,
            double minimumStage,
            double maximumStage,
            RandomSource random) {
        int mobY = mob.blockPosition().getY();
        DescentHeadingPolicy.Heading chosen = DescentHeadingPolicy.chooseBest(
                mob.getX(),
                mob.getZ(),
                mobY,
                (x, z) -> new int[] {
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                    sampleLocalRim(level, x, z)
                },
                0,
                recentCompletedHeadings::contains,
                HEADING_SECTORS,
                random);
        double headingX = chosen.x();
        double headingZ = chosen.z();
        int midInterest = 0;
        if (interest != null) {
            int midX = DescentHeadingPolicy.projectedBlock(
                    mob.getX(), headingX, DescentHeadingPolicy.SAMPLE_DISTANCES[1]);
            int midZ = DescentHeadingPolicy.projectedBlock(
                    mob.getZ(), headingZ, DescentHeadingPolicy.SAMPLE_DISTANCES[1]);
            midInterest = interest.at(midX, midZ);
        }
        int stageCount = MIN_STAGES + random.nextInt(MAX_STAGES - MIN_STAGES + 1);
        List<IntendedWaypoint> waypoints = new ArrayList<>(stageCount);
        double forward = 0.0;
        for (int stage = 0; stage < stageCount; stage++) {
            double stageDistance = minimumStage
                    + random.nextDouble() * (maximumStage - minimumStage);
            if (forward + stageDistance > maximumDistance) {
                if (waypoints.size() >= MIN_STAGES) {
                    break;
                }
                stageDistance = maximumDistance - forward;
            }
            forward += stageDistance;
            int x = DescentHeadingPolicy.projectedBlock(mob.getX(), headingX, (int) forward);
            int z = DescentHeadingPolicy.projectedBlock(mob.getZ(), headingZ, (int) forward);
            if (!chunkGuardTicking(level, new ChunkPos(x >> 4, z >> 4), mobY)) {
                if (waypoints.size() >= MIN_STAGES) {
                    break;
                }
                return null;
            }
            waypoints.add(new IntendedWaypoint(x, z, forward));
        }
        if (waypoints.size() < MIN_STAGES) {
            return null;
        }
        int sector = chosen.sector(HEADING_SECTORS);
        int score = 1_000 + ExplorationInterest.routeScore(midInterest);
        return new RouteCandidate(headingX, headingZ, sector, waypoints, score);
    }

    /** Preserve the original heading and route; only skip or insert a temporary rejoin when needed. */
    private void rebaseAfterInterruption(ServerLevel level) {
        if (expedition == null || expedition.lastInterruptedTick < 0 || expedition.rejoin != null) {
            return;
        }

        double mobForward = ExplorationPolicy.forwardProgress(
                expedition.originX, expedition.originZ, expedition.headingX, expedition.headingZ,
                mob.getX(), mob.getZ());
        double mobLateral = ExplorationPolicy.lateralDistance(
                expedition.originX, expedition.originZ, expedition.headingX, expedition.headingZ,
                mob.getX(), mob.getZ());

        while (expedition.waypointIndex < expedition.waypoints.size()) {
            IntendedWaypoint current = expedition.waypoints.get(expedition.waypointIndex);
            ExplorationPolicy.ResumeAction action = ExplorationPolicy.resumeAction(
                    mobForward, current.forwardDistance, mobLateral,
                    FORWARD_SKIP_MARGIN, LATERAL_REJOIN_DISTANCE);
            if (action != ExplorationPolicy.ResumeAction.SKIP_CURRENT) {
                if (action == ExplorationPolicy.ResumeAction.REJOIN_HEADING) {
                    double forward = Math.max(mobForward + REJOIN_ADVANCE, current.forwardDistance);
                    int x = Mth.floor(expedition.originX + expedition.headingX * forward);
                    int z = Mth.floor(expedition.originZ + expedition.headingZ * forward);
                    expedition.rejoin = new IntendedWaypoint(x, z, forward);
                }
                break;
            }
            // An interruption carried the mob beyond this stage. Do not regenerate or mark it visited.
            expedition.waypointIndex++;
            expedition.resetWaypointResolution();
        }

        expedition.lastInterruptedTick = -1;
        if (expedition.waypointIndex >= expedition.waypoints.size()) {
            completeExpedition(level.getGameTime(), mob.blockPosition());
        }
    }

    /**
     * Resolves the next <em>path</em>, which is not the same thing as the next waypoint.
     *
     * <p>A stage centre is 24-48 blocks away, but vanilla A* stops expanding at
     * {@code FOLLOW_RANGE} blocks from the start - 32 for a PlayerMob - so a request that long
     * cannot return a reachable path at all. The route is therefore walked in hops: the longest
     * one the pathfinder can honour, halved again on failure down to {@link #MIN_PATH_STEP}. A hop
     * is an intermediate target; only the waypoint itself completes a stage.
     */
    private PlanResult planCurrentStage(ServerLevel level, long now) {
        if (expedition == null) {
            return PlanResult.PATH_FAILURE;
        }
        expedition.lastPlanHadReachableLanding = false;
        expedition.lastPlanHadBlockedOpportunity = false;
        IntendedWaypoint intended = expedition.rejoin != null
                ? expedition.rejoin
                : expedition.waypoints.get(expedition.waypointIndex);

        if (!chunkGuardTicking(level, new ChunkPos(intended.x >> 4, intended.z >> 4), mob.blockPosition().getY())) {
            return PlanResult.SIMULATION_FRONTIER;
        }

        double targetX = intended.x + 0.5;
        double targetZ = intended.z + 0.5;
        double dx = targetX - mob.getX();
        double dz = targetZ - mob.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        boolean crossedSimulationFrontier = false;
        int probes = 0;
        double step = ExplorationPolicy.maxPathStep(mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        while (true) {
            boolean direct = distance <= step;
            int aimX = intended.x;
            int aimZ = intended.z;
            boolean aimTicking = true;
            if (!direct) {
                aimX = Mth.floor(ExplorationPolicy.stepCoordinate(mob.getX(), targetX, distance, step));
                aimZ = Mth.floor(ExplorationPolicy.stepCoordinate(mob.getZ(), targetZ, distance, step));
                aimTicking = chunkGuardTicking(
                        level, new ChunkPos(aimX >> 4, aimZ >> 4), mob.blockPosition().getY());
                crossedSimulationFrontier |= !aimTicking;
            }

            if (aimTicking) {
                int rungProbes = 0;
                for (BlockPos candidate
                        : landingCandidates(level, aimX, aimZ, direct, expedition.attemptedLandings, now)) {
                    if (probes >= LANDING_PROBES_PER_PLAN || rungProbes >= LANDING_PROBES_PER_HOP) {
                        break;
                    }
                    probes++;
                    rungProbes++;
                    Path path = mob.getNavigation().createPath(candidate, 0);
                    if (path == null || !path.canReach()) {
                        expedition.attemptedLandings.add(candidate.asLong());
                        continue;
                    }
                    if (!corridorTicking(level, path)) {
                        crossedSimulationFrontier = true;
                        continue;
                    }
                    double distanceSqr = mob.distanceToSqr(
                            candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                    if (direct) {
                        expedition.resolvedX = candidate.getX();
                        expedition.resolvedY = candidate.getY();
                        expedition.resolvedZ = candidate.getZ();
                        expedition.hasResolvedTarget = true;
                    }
                    navigationState = new NavigationState(path, candidate, distanceSqr, now, !direct);
                    expedition.lastProgressTick = now;
                    expedition.lastPlanHadReachableLanding = true;
                    return PlanResult.READY;
                }
            }

            if (direct || probes >= LANDING_PROBES_PER_PLAN || step * 0.5 < MIN_PATH_STEP) {
                break;
            }
            step *= 0.5;
        }
        if (probes > 0) {
            expedition.lastPlanHadBlockedOpportunity = true;
        }
        return crossedSimulationFrontier ? PlanResult.SIMULATION_FRONTIER : PlanResult.PATH_FAILURE;
    }

    /**
     * An intermediate hop landed. The mob is closer to the same waypoint on the same heading, so
     * only the disposable path is replaced - and real ground covered earns back the failure budget.
     */
    private void advanceHop(ServerLevel level, long now) {
        navigationState = null;
        expedition.hops++;
        expedition.perWaypointFailures = 0;
        expedition.expeditionFailures = 0;
        expedition.attemptedLandings.clear();
        expedition.lastProgressTick = now;

        PlanResult result = planCurrentStage(level, now);
        if (result == PlanResult.READY) {
            mob.getNavigation().moveTo(navigationState.path, exploreSpeed());
        } else {
            handlePlanFailure(result, now);
        }
    }

    /**
     * Standable positions around an aim point, cheapest first.
     *
     * <p>Heightmap tops remain the surface/roof fallback. When the mob is already cave/ravine-like
     * (MI-6B rim), MI-6A also probes a bounded 3D volume for real walkable floors so sorting can
     * prefer staying underground (MI-6D modes).
     */
    private List<BlockPos> landingCandidates(
            ServerLevel level, int centreX, int centreZ, boolean allowResolvedTarget,
            Set<Long> alreadyAttempted, long now) {
        List<BlockPos> result = new ArrayList<>();
        if (allowResolvedTarget && expedition.hasResolvedTarget) {
            BlockPos resolved = new BlockPos(
                    expedition.resolvedX, expedition.resolvedY, expedition.resolvedZ);
            if (!alreadyAttempted.contains(resolved.asLong()) && safeStand(level, resolved)) {
                result.add(resolved);
            }
        }

        int mobY = mob.blockPosition().getY();
        int columnSurface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                mob.blockPosition().getX(),
                mob.blockPosition().getZ());
        int localRim = sampleLocalRim(level, mob.blockPosition().getX(), mob.blockPosition().getZ());
        boolean caveOrRavine = CaveContextPolicy.isCaveOrRavineLike(mobY, columnSurface, localRim);
        boolean descending = expedition != null
                && expedition.intent == ExplorationIntent.DESCENT
                && !WorkDemandPolicy.isDiamondLocalGatherEligible(mobY);
        CaveContextPolicy.LandingMode mode =
                CaveContextPolicy.resolveLandingMode(descending, caveOrRavine);

        List<BlockPos> ring = new ArrayList<>();
        if (caveOrRavine) {
            for (BlockPos caveFloor : CaveLandingResolver.collectStandable(
                    centreX, centreZ, mobY, pos -> safeStand(level, pos))) {
                if (!alreadyAttempted.contains(caveFloor.asLong())) {
                    ring.add(caveFloor);
                }
            }
        }

        for (int radius = 0; radius <= LANDING_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = centreX + dx;
                    int z = centreZ + dz;
                    if (!level.isPositionEntityTicking(new BlockPos(x, mobY, z))) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (Math.abs(y - mobY) > MAX_LANDING_ELEVATION) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    if (!alreadyAttempted.contains(position.asLong()) && safeStand(level, position)) {
                        ring.add(position);
                    }
                }
            }
        }

        int terrainRef = Math.max(columnSurface, localRim);
        Map<Long, Integer> preferenceKeys = CaveOpportunitySelection.preferenceKeyMap();
        ring.sort(Comparator.comparingInt(position -> {
            int landingTerrain = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    position.getX(),
                    position.getZ());
            int ref = mode == CaveContextPolicy.LandingMode.NORMAL
                    ? landingTerrain
                    : Math.max(landingTerrain, terrainRef);
            int key = CaveContextPolicy.landingPreferenceKey(mode, position.getY(), mobY, ref);
            preferenceKeys.put(position.asLong(), key);
            return key;
        }));
        result.addAll(ring);

        if (expedition != null && (caveOrRavine || descending) && result.size() >= 2) {
            CaveOpportunitySelection.CommitmentResult committed = CaveOpportunitySelection.commitBestScored(
                    result,
                    preferenceKeys,
                    expedition.caveCommitment,
                    id -> {
                        if (!preferenceKeys.containsKey(id)) {
                            return false;
                        }
                        return safeStand(level, BlockPos.of(id));
                    },
                    now);
            expedition.caveCommitment = committed.commitment();
            return committed.candidates();
        }
        return result;
    }

    /** MI-6F — whether a short-lived cave branch commitment is still active. */
    public boolean hasActiveCaveCommitment(ServerLevel level, long now) {
        if (expedition == null || expedition.caveCommitment == null) {
            return false;
        }
        BlockPos committed = BlockPos.of(expedition.caveCommitment.id());
        boolean stillValid = safeStand(level, committed);
        return CaveOpportunityPolicy.holds(
                expedition.caveCommitment,
                stillValid,
                now,
                CaveOpportunityPolicy.COMMIT_TICKS);
    }

    /**
     * MI-7C — current natural-descent exhaustion evidence. Does not start controlled descent.
     */
    public NaturalDescentStatus naturalDescentStatus(ServerLevel level, long now) {
        if (!readiness.hasDescentPressure()) {
            return NaturalDescentStatus.SEARCHING;
        }
        boolean activeCave = hasActiveCaveCommitment(level, now);
        boolean reachable = expedition != null && expedition.lastPlanHadReachableLanding;
        boolean blocked = expedition != null && expedition.lastPlanHadBlockedOpportunity;
        boolean searchActive = expedition != null && expedition.intent == ExplorationIntent.DESCENT;
        return NaturalDescentExhaustionPolicy.evaluate(
                descentSearch.budget(),
                descentSearch.usage(),
                activeCave,
                reachable,
                blocked,
                searchActive || descentSearch.isActive());
    }

    NaturalDescentSearchState descentSearchState() {
        return descentSearch;
    }

    private static int sampleLocalRim(ServerLevel level, int originX, int originZ) {
        int[] ox = CaveContextPolicy.rimSampleOffsetsX();
        int[] oz = CaveContextPolicy.rimSampleOffsetsZ();
        int[] samples = new int[ox.length];
        for (int i = 0; i < ox.length; i++) {
            samples[i] = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, originX + ox[i], originZ + oz[i]);
        }
        return CaveContextPolicy.localRimHeight(samples);
    }

    private boolean safeStand(ServerLevel level, BlockPos position) {
        if (!level.isPositionEntityTicking(position)
                || !level.getFluidState(position).isEmpty()
                || !level.getFluidState(position.below()).isEmpty()
                || !level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                || !level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
                || !level.getBlockState(position.below()).isFaceSturdy(
                        level, position.below(), Direction.UP)) {
            return false;
        }
        double dx = position.getX() + 0.5 - mob.getX();
        double dy = position.getY() - mob.getY();
        double dz = position.getZ() + 0.5 - mob.getZ();
        return level.noCollision(mob, mob.getBoundingBox().move(dx, dy, dz));
    }

    /** Full, chunk-deduplicated 3x3 guard validation. Called only when a path is created. */
    private static boolean corridorTicking(ServerLevel level, Path path) {
        Set<ChunkPos> corridorChunks = new HashSet<>();
        for (int index = 0; index < path.getNodeCount(); index++) {
            corridorChunks.add(new ChunkPos(path.getNode(index).asBlockPos()));
        }
        int y = path.getTarget().getY();
        for (ChunkPos chunk : corridorChunks) {
            if (!chunkGuardTicking(level, chunk, y)) {
                return false;
            }
        }
        return true;
    }

    private static boolean chunkGuardTicking(ServerLevel level, ChunkPos centre, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = centre.x + dx;
                int chunkZ = centre.z + dz;
                BlockPos representative = new BlockPos((chunkX << 4) + 8, y, (chunkZ << 4) + 8);
                if (!level.isPositionEntityTicking(representative)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Cheap travel-time frontier check; full corridor validation belongs to planning/replanning. */
    private boolean travelFrontierStillSafe(
            ServerLevel level, Path path, double headingX, double headingZ) {
        BlockPos current = mob.blockPosition();
        BlockPos next = path.isDone() ? path.getTarget() : path.getNextNodePos();
        if (!level.isPositionEntityTicking(current) || !level.isPositionEntityTicking(next)) {
            return false;
        }
        ChunkPos nextChunk = new ChunkPos(next);
        int guardX = nextChunk.x + (int) Math.signum(headingX);
        int guardZ = nextChunk.z + (int) Math.signum(headingZ);
        BlockPos forwardGuard = new BlockPos((guardX << 4) + 8, next.getY(), (guardZ << 4) + 8);
        return level.isPositionEntityTicking(forwardGuard);
    }

    private void finishCurrentStage(ServerLevel level, long now) {
        if (expedition.rejoin != null) {
            expedition.rejoin = null;
            expedition.resetWaypointResolution();
        } else {
            IntendedWaypoint reached = expedition.waypoints.get(expedition.waypointIndex);
            remember(recentVisitedRegions, regionKey(reached), REGION_MEMORY_LIMIT);
            expedition.waypointIndex++;
            expedition.perWaypointFailures = 0;
            expedition.expeditionFailures = 0;
            expedition.resetWaypointResolution();
        }
        navigationState = null;

        if (expedition.waypointIndex >= expedition.waypoints.size()) {
            completeExpedition(now, mob.blockPosition());
            return;
        }

        PlanResult result = planCurrentStage(level, now);
        if (result == PlanResult.READY) {
            mob.getNavigation().moveTo(navigationState.path, exploreSpeed());
        } else {
            handlePlanFailure(result, now);
        }
    }

    private void handlePlanFailure(PlanResult result, long now) {
        if (navigationState != null && expedition != null) {
            expedition.attemptedLandings.add(navigationState.target.asLong());
        }
        navigationState = null;
        mob.getNavigation().stop();
        if (result != PlanResult.SIMULATION_FRONTIER) {
            expedition.perWaypointFailures++;
            expedition.expeditionFailures++;
            expedition.resetResolvedTargetOnly();
            if (expedition.intent == ExplorationIntent.DESCENT && descentSearch.isActive()) {
                descentSearch.recordFailure();
            }
        }
        ExplorationPolicy.FailureAction action = ExplorationPolicy.failureAction(
                result == PlanResult.SIMULATION_FRONTIER,
                expedition.perWaypointFailures,
                expedition.expeditionFailures,
                MAX_WAYPOINT_FAILURES,
                MAX_EXPEDITION_FAILURES,
                expedition.rejoin != null,
                expedition.waypointIndex + 1 < expedition.waypoints.size());
        switch (action) {
            case ABANDON_SIMULATION_FRONTIER -> {
                abandon(EndReason.SIMULATION_FRONTIER, now);
                return;
            }
            case ABANDON_PATH -> {
                abandon(EndReason.PATH_FAILURE, now);
                return;
            }
            case DROP_REJOIN -> expedition.rejoin = null;
            case SKIP_WAYPOINT -> expedition.waypointIndex++;
            case RETRY_WAYPOINT -> {
                // Preserve the same intended centre; only the exact landing is retried.
            }
        }
        if (action == ExplorationPolicy.FailureAction.DROP_REJOIN
                || action == ExplorationPolicy.FailureAction.SKIP_WAYPOINT) {
            expedition.perWaypointFailures = 0;
            expedition.resetWaypointResolution();
        }
        retryAfterTick = now + REPLAN_DELAY_TICKS;
    }

    private void completeExpedition(long now, BlockPos actualEnd) {
        if (expedition == null) {
            return;
        }
        if (mob.level() instanceof ServerLevel level) {
            clearCaveContinuationCommitment(level);
        }
        remember(recentExpeditionDestinations,
                ExplorationPolicy.regionKey(actualEnd.getX(), actualEnd.getZ(), REGION_SIZE_CHUNKS),
                REGION_MEMORY_LIMIT);
        remember(recentCompletedHeadings, expedition.headingSector, HEADING_MEMORY_LIMIT);
        SpmScavenger.LOGGER.info(
                "[spmscavenger] exploration completed entity={} intent={} stages={} hops={} endpoint={}",
                mob.getId(), expedition.intent, expedition.waypoints.size(), expedition.hops,
                actualEnd);
        expedition = null;
        navigationState = null;
        readiness.consume(now + COOLDOWN_TICKS);
    }

    private void clearCaveContinuationCommitment(ServerLevel level) {
        if (expedition != null && expedition.caveHandoffContinuation) {
            MiningProjectSavedData.get(level).clearCommitment(mob.getUUID());
        }
    }

    private void abandon(EndReason reason, long now) {
        // A simulation frontier is not evidence that the heading or unseen destination was bad.
        if (expedition != null) {
            if (mob.level() instanceof ServerLevel level) {
                clearCaveContinuationCommitment(level);
            }
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] exploration ended entity={} intent={} reason={} waypoint={}/{} "
                            + "hops={} waypointFailures={} expeditionFailures={}",
                    mob.getId(), expedition.intent, reason, expedition.waypointIndex + 1,
                    expedition.waypoints.size(), expedition.hops, expedition.perWaypointFailures,
                    expedition.expeditionFailures);
        }
        expedition = null;
        navigationState = null;
        mob.getNavigation().stop();
        readiness.consume(now + (reason == EndReason.SIMULATION_FRONTIER
                ? COOLDOWN_TICKS / 2 : COOLDOWN_TICKS));
    }

    /**
     * A stay-near anchor is a persistent player order, not a transient interruption. Keeping the
     * outward expedition would make StayNearGoal return the mob and this goal immediately send it
     * back out forever. Unknown integration state also fails closed and disables exploration.
     */
    private boolean yieldToStayAnchor(ServerLevel level, long now) {
        PlayerMobs.StayAnchorState state = PlayerMobs.stayAnchorState(mob);
        if (ExplorationPolicy.allowsExpedition(state)) {
            return false;
        }
        if (expedition != null) {
            abandon(state == PlayerMobs.StayAnchorState.PRESENT
                    ? EndReason.STAY_ANCHOR
                    : EndReason.STAY_ANCHOR_STATE_UNAVAILABLE, now);
        }
        return true;
    }

    private double exploreSpeed() {
        return Mth.clamp(ScavengerConfig.get().exploreSpeed, 0.5, 1.3);
    }

    private static long regionKey(IntendedWaypoint waypoint) {
        return ExplorationPolicy.regionKey(waypoint.x, waypoint.z, REGION_SIZE_CHUNKS);
    }

    private static <T> void remember(ArrayDeque<T> history, T value, int limit) {
        history.remove(value);
        history.addLast(value);
        while (history.size() > limit) {
            history.removeFirst();
        }
    }

    private enum PlanResult {
        READY,
        PATH_FAILURE,
        SIMULATION_FRONTIER
    }

    private enum EndReason {
        PATH_FAILURE,
        SIMULATION_FRONTIER,
        STALE,
        STAY_ANCHOR,
        STAY_ANCHOR_STATE_UNAVAILABLE
    }

    private record IntendedWaypoint(int x, int z, double forwardDistance) {
    }

    private record RouteCandidate(
            double headingX,
            double headingZ,
            int headingSector,
            List<IntendedWaypoint> waypoints,
            int score) {
    }

    /**
     * Why this expedition exists. {@code DESCENT} journeys stay recognisable for their whole life,
     * so they can be logged and terminated for a named reason instead of silently dissolving when
     * the pressure that started them goes away (MI-5 defect 2).
     */
    enum ExplorationIntent { NORMAL, DESCENT }

    /** Intent state only. Deliberately contains no Path or path-node index. */
    private static final class ExpeditionState {
        final ExplorationIntent intent;
        final double originX;
        final double originZ;
        final double headingX;
        final double headingZ;
        final int headingSector;
        final List<IntendedWaypoint> waypoints;
        final long startedTick;
        final Set<Long> attemptedLandings = new HashSet<>();
        int waypointIndex;
        int perWaypointFailures;
        int expeditionFailures;
        int hops;
        boolean companionsInvited;
        long lastProgressTick;
        long lastInterruptedTick = -1;
        IntendedWaypoint rejoin;
        boolean hasResolvedTarget;
        int resolvedX;
        int resolvedY;
        int resolvedZ;

        CaveOpportunityPolicy.CaveOpportunity caveCommitment;
        boolean lastPlanHadReachableLanding;
        boolean lastPlanHadBlockedOpportunity;
        boolean caveHandoffContinuation;

        ExpeditionState(
                ExplorationIntent intent,
                double originX,
                double originZ,
                double headingX,
                double headingZ,
                int headingSector,
                List<IntendedWaypoint> waypoints,
                long startedTick) {
            this.intent = intent;
            this.originX = originX;
            this.originZ = originZ;
            this.headingX = headingX;
            this.headingZ = headingZ;
            this.headingSector = headingSector;
            this.waypoints = waypoints;
            this.startedTick = startedTick;
            this.lastProgressTick = startedTick;
        }

        void resetResolvedTargetOnly() {
            hasResolvedTarget = false;
        }

        void resetWaypointResolution() {
            hasResolvedTarget = false;
            attemptedLandings.clear();
        }
    }

    /** Disposable activation state. Never survives {@link #stop()}. */
    private static final class NavigationState {
        final Path path;
        final BlockPos target;
        /** True when this path ends at a hop along the way rather than at the waypoint itself. */
        final boolean intermediate;
        double lastDistanceSqr;
        long lastProgressTick;
        long navigationDoneSinceTick = -1;

        NavigationState(
                Path path, BlockPos target, double lastDistanceSqr, long lastProgressTick,
                boolean intermediate) {
            this.path = path;
            this.target = target;
            this.lastDistanceSqr = lastDistanceSqr;
            this.lastProgressTick = lastProgressTick;
            this.intermediate = intermediate;
        }
    }
}
