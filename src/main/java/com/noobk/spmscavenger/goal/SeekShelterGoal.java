package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.RestAnchorType;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At night, go somewhere with a roof — and if there is a bed, actually sleep in it.
 *
 * <h2>Ranked, not nearest-first</h2>
 *
 * Candidates pass a bounded SCR-2 pipeline: cheap physical filtering, spatially diverse
 * shortlisting, approximate interior classification, lexicographic semantic ranking, at most four
 * path probes, then a commitment-owned standing reservation.
 *
 * <h2>Sleeping is real, not a pose</h2>
 *
 * {@code LivingEntity#startSleeping} is <b>not</b> player-only — villagers use it, and it handles
 * {@code BedBlock.OCCUPIED} and the sleeping pose itself, with {@code stopSleeping} reversing both.
 * So this goal never hand-manages bed state; it only has to <em>check</em> occupancy before
 * committing and make sure two mobs do not walk toward the same bed at once, which occupancy alone
 * cannot prevent because it is not set until arrival.
 *
 * <p><b>Night-skip is not a risk.</b> Only {@code ServerPlayer} counts toward sleeping through the
 * night, so a village of sleeping PlayerMobs will not fast-forward the world.
 *
 * <h2>What it still refuses to do</h2>
 *
 * It never digs or builds a shelter. That would be more thorough and is what a real player does; it
 * is also how an addon starts leaving holes in someone's base. If nothing scores well enough the
 * goal does not run and the mob behaves exactly as Social Player Mobs ships it.
 */
public class SeekShelterGoal extends Goal {

    /**
     * Beds being walked toward, so two mobs do not set off for the same one. {@code OCCUPIED} is not
     * set until a mob actually lies down, so it cannot serve this purpose on its own. Entries are
     * retained across a temporary scheduler suspension, explicitly released when the commitment is
     * cancelled, and expired if the owner disappears or the bounded claim lifetime elapses.
     */
    private static final Map<BlockPos, Claim> CLAIMS = new ConcurrentHashMap<>();

    private record Claim(UUID mob, long expiresAtTick) {
    }

    /** A claim outlives roughly the longest sensible walk, then lapses. */
    private static final long CLAIM_TICKS = 600L;

    private final Mob mob;
    private final double speed;

    private ShelterCommitment commitment;
    private final PhasedScanClock scanClock;
    private final ShelterCandidateRejections candidateRejections = new ShelterCandidateRejections();
    private boolean executionRunning;
    private BlockPos rejectedDestination;
    private long rejectedUntilTick;

    private static final int SCAN_INTERVAL = 40;
    private static final int SCAN_PHASE_SALT = 11;
    /** startSleeping teleports the mob onto the bed, so only allow it from touching distance. */
    private static final double BED_REACH_SQR = 6.0;

    public SeekShelterGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.scanClock = new PhasedScanClock(mob.getId(), SCAN_INTERVAL, SCAN_PHASE_SALT);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!baseAuthorityAllows(cfg)) {
            cancelCommitment(true);
            return false;
        }
        Level level = mob.level();
        if (commitment != null) {
            if (!validCommitment(cfg, level) || commitment.approachBudgetExhausted(level.getGameTime())) {
                cancelCommitment(true);
                return false;
            }
            return true;
        }
        if (!scanClock.claim(level.getGameTime())) {
            return false;
        }
        // Under cover already and not chasing a bed? Then there is nothing worth doing, and no scan.
        boolean sheltered = !level.canSeeSky(mob.blockPosition());
        if (sheltered && !cfg.sleepInBeds) {
            return false;
        }
        return search(cfg);
    }

    @Override
    public boolean canContinueToUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!baseAuthorityAllows(cfg)
                || commitment == null
                || !validCommitment(cfg, mob.level())
                || commitment.approachBudgetExhausted(mob.level().getGameTime())) {
            cancelCommitment(true);
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        executionRunning = true;
        if (commitment != null) {
            commitment.activate();
            if (commitment.state() != ShelterCommitment.State.ARRIVED && !mob.isSleeping()) {
                requestFreshPath();
            }
        }
    }

    @Override
    public void stop() {
        executionRunning = false;
        if (commitment != null && !mob.isSleeping()) {
            commitment.suspend();
        }
        // Goal execution and the current Path really did stop. The commitment is deliberately not
        // destroyed here; the scheduler observer or explicit validity checks own cancellation.
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (commitment == null || mob.isSleeping()) {
            return; // asleep: nothing to do until canContinueToUse says otherwise
        }
        commitment.recordActiveApproachTick();
        if (commitment.approachBudgetExhausted(mob.level().getGameTime())) {
            abandonCurrentDestination();
            return;
        }

        BlockPos bedPos = commitment.bedPos().orElse(null);
        if (bedPos != null && mob.blockPosition().distSqr(bedPos) <= BED_REACH_SQR) {
            lieDown();
            return;
        }
        BlockPos standPos = commitment.destination();
        if (bedPos == null
                && ShelterSelectionPolicy.arrivedAtStandingSite(mob.blockPosition(), standPos)) {
            commitment.arrive();
            if (!commitment.restClaimOpened()) {
                RestSessionCoordinator.openShelterRecovery(
                        mob, standPos, RestAnchorType.SHELTER_STAND, mob.level().getGameTime());
                commitment.markRestClaimOpened();
            }
            mob.getNavigation().stop(); // arrived; wait out the night
            return;
        }
        if (mob.getNavigation().isDone()) {
            requestFreshPath();
        }
    }

    private void lieDown() {
        if (commitment == null || commitment.bedPos().isEmpty()) {
            return;
        }
        BlockPos bedPos = commitment.bedPos().orElseThrow();
        BlockState state = mob.level().getBlockState(bedPos);
        // Re-check on arrival: someone may have taken or broken the bed during the walk.
        if (state.is(BlockTags.BEDS)
                && !state.getValue(BedBlock.OCCUPIED)
                && ownsLiveClaim(commitment, mob.level().getGameTime())) {
            mob.getNavigation().stop();
            mob.startSleeping(bedPos);
            commitment.arrive();
            if (!commitment.restClaimOpened()) {
                RestSessionCoordinator.openShelterRecovery(
                        mob, bedPos, RestAnchorType.SHELTER_BED, mob.level().getGameTime());
                commitment.markRestClaimOpened();
            }
        } else {
            abandonCurrentDestination();
        }
    }

    /**
     * Dusk through dawn, rather than {@code Level#isNight()}.
     *
     * <p>{@code isNight()} only becomes true once it is <em>already</em> dark, which meant mobs set
     * off looking for cover at the moment the danger arrived and were routinely caught in the open
     * halfway there. Starting at dusk ({@value #DUSK} of the 24000-tick day, roughly when the sun
     * touches the horizon) gives them the walk home that a player takes.
     *
     * <p>Dimensions with a fixed time — the Nether and the End — have no night to shelter from, so
     * the goal never runs there rather than treating a permanent time-of-day as permanent night.
     */
    private static boolean shelterTime(Level level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        long time = level.getDayTime() % 24000L;
        return time >= DUSK && time < DAWN;
    }

    /** Sun setting. */
    private static final long DUSK = 11500L;
    /** Sun rising; mobs get up and the bed is released. */
    private static final long DAWN = 23000L;

    // ---- Searching --------------------------------------------------------

    /** Picks the best reachable semantic shelter in range, or leaves the goal unused. */
    private boolean search(ScavengerConfig cfg) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        int r = (int) cfg.shelterSearchRadius;
        long now = level.getGameTime();

        sweepExpiredClaims(now);
        candidateRejections.sweep(now);
        if (rejectedDestination != null && now > rejectedUntilTick) {
            rejectedDestination = null;
        }

        List<ShelterSelectionPolicy.RawCandidate> rawCandidates = new ArrayList<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (rejectedDestination != null && rejectedDestination.equals(pos)) {
                        continue;
                    }
                    if (candidateRejections.contains(pos)) {
                        continue;
                    }
                    if (level instanceof ServerLevel serverLevel
                            && !serverLevel.isPositionEntityTicking(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    double distance = Math.sqrt(pos.distSqr(origin));

                    boolean isBed = cfg.sleepInBeds && state.is(BlockTags.BEDS) && claimable(pos, state);
                    if (!isBed && (level.canSeeSky(pos) || !standable(level, pos) || !safeForMob(pos))) {
                        continue;
                    }
                    rawCandidates.add(new ShelterSelectionPolicy.RawCandidate(
                            pos,
                            isBed ? canonicalBedPos(pos, state) : null,
                            solidNeighbours(level, pos),
                            level.getBrightness(LightLayer.BLOCK, pos),
                            distance));
                }
            }
        }

        List<ShelterSelectionPolicy.RankedCandidate> ranked = new ArrayList<>();
        for (ShelterSelectionPolicy.RawCandidate raw :
                ShelterSelectionPolicy.diverseShortlist(rawCandidates, r)) {
            ShelterSelectionPolicy.Evidence evidence = interiorEvidence(level, raw.standPos());
            ranked.add(new ShelterSelectionPolicy.RankedCandidate(
                    raw,
                    ShelterSelectionPolicy.classify(raw.bed(), evidence),
                    evidence));
        }

        ShelterSelectionPolicy.PathProbeBudget pathProbeBudget =
                new ShelterSelectionPolicy.PathProbeBudget();
        for (ShelterSelectionPolicy.RankedCandidate candidate : ShelterSelectionPolicy.rank(ranked)) {
            ShelterSelectionPolicy.RawCandidate raw = candidate.raw();
            if (candidate.tier() == ShelterSelectionPolicy.Tier.EXPOSED
                    || occupied(raw.standPos())
                    || !ShelterReservationRegistry.available(
                            mob.getUUID(), level.dimension(), raw.standPos(),
                            ShelterReservationRegistry.DEFAULT_SPACING_RADIUS, now)) {
                continue;
            }
            if (!pathProbeBudget.tryAcquire()) {
                break;
            }
            Path path = mob.getNavigation().createPath(raw.standPos(), raw.bed() ? 1 : 0);
            if (path == null || !path.canReach() || !pathStaysEntityTicking(path)) {
                candidateRejections.reject(raw.standPos(), now);
                continue;
            }

            UUID commitmentId = UUID.randomUUID();
            if (!ShelterReservationRegistry.reserve(
                    mob.getUUID(), commitmentId, level.dimension(), raw.standPos(),
                    ShelterReservationRegistry.DEFAULT_SPACING_RADIUS, now)) {
                continue;
            }
            if (raw.bed() && !tryClaim(raw.bedPos(), mob.getUUID(), now)) {
                ShelterReservationRegistry.release(mob.getUUID(), commitmentId);
                continue;
            }
            commitment = new ShelterCommitment(
                    commitmentId,
                    raw.standPos(),
                    raw.bedPos(),
                    candidate.tier(),
                    mob.getUUID(),
                    now);
            return true;
        }
        return false;
    }

    /** Free bed, not already occupied and not being walked toward by another mob. */
    private boolean claimable(BlockPos pos, BlockState state) {
        if (state.getValue(BedBlock.OCCUPIED)) {
            return false;
        }
        BlockPos claimKey = canonicalBedPos(pos, state);
        Claim claim = CLAIMS.get(claimKey);
        // Gate RET-1d - logical expiry was not physical expiry. An expired bed claim read as free
        // but stayed in this static map, so every bed position ever considered was retained for the
        // life of the server. Conditional removal so a concurrent re-claim is not discarded.
        if (claim != null && mob.level().getGameTime() > claim.expiresAtTick()) {
            CLAIMS.remove(claimKey, claim);
            claim = null;
        }
        if (claim == null) {
            return true;
        }
        return claim.mob().equals(mob.getUUID()) || mob.level().getGameTime() > claim.expiresAtTick();
    }

    /** Both physical halves of one bed share a single claim key (the head block). */
    private static BlockPos canonicalBedPos(BlockPos pos, BlockState state) {
        return canonicalBedPos(pos, state.getValue(BedBlock.FACING), state.getValue(BedBlock.PART));
    }

    static BlockPos canonicalBedPos(BlockPos pos, Direction facing, BedPart part) {
        return (part == BedPart.FOOT ? pos.relative(facing) : pos).immutable();
    }

    private boolean ownsLiveClaim(ShelterCommitment current, long now) {
        if (current.bedPos().isEmpty()) {
            return true;
        }
        Claim claim = CLAIMS.get(current.bedPos().orElseThrow());
        return claim != null
                && claim.mob().equals(current.claimant())
                && now <= claim.expiresAtTick();
    }

    /** Gate RET-1d - release every bed claim when the server stops. */
    public static void shutdownServerState() {
        CLAIMS.clear();
        ShelterReservationRegistry.shutdownServerState();
    }

    /** Release claims when Minecraft removes the owning entity without a final Goal stop tick. */
    public static void onEntityUnload(UUID mobId) {
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().mob().equals(mobId));
        ShelterReservationRegistry.releaseOwner(mobId);
    }

    /** Death has the same ownership semantics as unload, but remains explicit for auditability. */
    public static void onDeath(UUID mobId) {
        onEntityUnload(mobId);
    }

    /** Entity unload, dimension transfer, and death invalidate intent as well as static ownership. */
    public void cancelForOwnerRemoval() {
        cancelCommitment(false);
    }

    /** Shared observer seam: approach is safety work; arrival/sleep is an actual rest condition. */
    public boolean isRestingAtShelter() {
        return mob.isSleeping()
                || (commitment != null && commitment.state() == ShelterCommitment.State.ARRIVED);
    }

    static int bedClaimCount() {
        return CLAIMS.size();
    }

    static void claimForTest(BlockPos pos, UUID mobId, long expiresAtTick) {
        CLAIMS.put(pos.immutable(), new Claim(mobId, expiresAtTick));
    }

    static boolean ownsClaimForTest(BlockPos pos, UUID mobId) {
        Claim claim = CLAIMS.get(pos);
        return claim != null && claim.mob().equals(mobId);
    }

    private static void sweepExpiredClaims(long now) {
        CLAIMS.entrySet().removeIf(entry -> now > entry.getValue().expiresAtTick());
    }

    private static synchronized boolean tryClaim(BlockPos pos, UUID claimant, long now) {
        BlockPos key = pos.immutable();
        Claim current = CLAIMS.get(key);
        if (current != null && now <= current.expiresAtTick() && !current.mob().equals(claimant)) {
            return false;
        }
        CLAIMS.put(key, new Claim(claimant, now + CLAIM_TICKS));
        return true;
    }

    private void release() {
        if (commitment == null) {
            return;
        }
        ShelterReservationRegistry.release(commitment.claimant(), commitment.commitmentId());
        if (commitment.bedPos().isPresent()) {
            BlockPos bedPos = commitment.bedPos().orElseThrow();
            Claim claim = CLAIMS.get(bedPos);
            // Only drop a claim this mob actually owns — another mob may have taken it after expiry.
            if (claim != null && claim.mob().equals(commitment.claimant())) {
                CLAIMS.remove(bedPos, claim);
            }
        }
    }

    /**
     * Reconcile a suspended commitment from the existing scheduler-wide observer. This performs no
     * second GoalSelector scan. Benign finite helpers leave the commitment suspended; mandatory or
     * unknown authority cancels it.
     */
    public void observeScheduler(ActivityObservationService.Observation observation) {
        if (commitment == null || executionRunning) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!baseAuthorityAllows(cfg)
                || !validCommitment(cfg, mob.level())
                || ShelterInterruptionPolicy.decide(observation.activeClasses())
                        == ShelterInterruptionPolicy.Decision.CANCEL) {
            cancelCommitment(true);
            return;
        }
        commitment.suspend();
    }

    private boolean baseAuthorityAllows(ScavengerConfig cfg) {
        return cfg.enabled
                && cfg.seekShelter
                && shelterTime(mob.level())
                && mob.getTarget() == null
                && mob.hurtTime <= 0
                && PlayerMobs.stayAnchorState(mob) == PlayerMobs.StayAnchorState.ABSENT;
    }

    private boolean validCommitment(ScavengerConfig cfg, Level level) {
        if (commitment == null) {
            return false;
        }
        BlockPos destination = commitment.destination();
        double maxDisplacement = Math.max(32.0, cfg.shelterSearchRadius * 2.0);
        if (mob.blockPosition().distSqr(destination) > maxDisplacement * maxDisplacement) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel
                && !serverLevel.isPositionEntityTicking(destination)) {
            return false;
        }
        if (!ShelterReservationRegistry.ownsAndRefresh(
                commitment.claimant(),
                commitment.commitmentId(),
                GlobalPos.of(level.dimension(), destination),
                level.getGameTime())) {
            return false;
        }
        if (commitment.bedPos().isPresent()) {
            BlockPos bedPos = commitment.bedPos().orElseThrow();
            BlockState state = level.getBlockState(bedPos);
            if (!state.is(BlockTags.BEDS)) {
                return false;
            }
            if (mob.isSleeping()) {
                return true;
            }
            return !state.getValue(BedBlock.OCCUPIED)
                    && ownsLiveClaim(commitment, level.getGameTime());
        }
        return !level.canSeeSky(destination) && standable(level, destination);
    }

    private void requestFreshPath() {
        if (commitment == null || commitment.state() == ShelterCommitment.State.ARRIVED) {
            return;
        }
        BlockPos destination = commitment.destination();
        boolean accepted = mob.getNavigation().moveTo(
                destination.getX() + 0.5,
                destination.getY(),
                destination.getZ() + 0.5,
                speed);
        if (!accepted) {
            commitment.recordPathFailure();
            if (commitment.approachBudgetExhausted(mob.level().getGameTime())) {
                abandonCurrentDestination();
            }
        }
    }

    private void abandonCurrentDestination() {
        if (commitment != null) {
            rejectedDestination = commitment.destination();
            rejectedUntilTick = mob.level().getGameTime() + CLAIM_TICKS;
        }
        cancelCommitment(true);
    }

    private void cancelCommitment(boolean deferRescan) {
        if (commitment == null) {
            return;
        }
        if (mob.isSleeping()) {
            mob.stopSleeping();
        }
        release();
        commitment = null;
        executionRunning = false;
        mob.getNavigation().stop();
        if (deferRescan) {
            scanClock.resetAfter(mob.level().getGameTime());
        }
    }

    /** Solid blocks among the four sides and the ceiling — a cheap proxy for enclosure. */
    private static int solidNeighbours(Level level, BlockPos pos) {
        int n = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) {
                continue; // the floor is required anyway; counting it would flatter open ground
            }
            BlockPos side = pos.relative(dir);
            if (level.getBlockState(side).isSolidRender(level, side)) {
                n++;
            }
        }
        return n;
    }

    /** Two blocks of headroom on a solid floor — enough for a player-shaped mob to stand. */
    private static boolean standable(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isSolidRender(level, below)) {
            return false;
        }
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }

    /** Reject navigation node types that are unsafe even when geometrically standable. */
    private boolean safeForMob(BlockPos pos) {
        PathType type = WalkNodeEvaluator.getPathTypeStatic(mob, pos);
        return switch (type) {
            case BLOCKED, FENCE, POWDER_SNOW, DANGER_POWDER_SNOW,
                    LAVA, WATER, WATER_BORDER,
                    DANGER_FIRE, DAMAGE_FIRE, DANGER_OTHER, DAMAGE_OTHER,
                    DAMAGE_CAUTIOUS, DANGER_TRAPDOOR, STICKY_HONEY -> false;
            default -> mob.getPathfindingMalus(type) >= 0.0F;
        };
    }

    /** Bounded approximation of room/cave enclosure for shortlisted candidates only. */
    private static ShelterSelectionPolicy.Evidence interiorEvidence(Level level, BlockPos pos) {
        int boundaries = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (boundaryWithin(level, pos, direction)) {
                boundaries++;
            }
        }
        int roofCoverage = level.canSeeSky(pos) ? 0 : 1;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.canSeeSky(pos.relative(direction))) {
                roofCoverage++;
            }
        }
        return new ShelterSelectionPolicy.Evidence(boundaries, roofCoverage, doorClearance(level, pos));
    }

    /** Manhattan clearance from a doorway, bounded so semantic evaluation stays cheap. */
    private static int doorClearance(Level level, BlockPos origin) {
        for (int distance = 0; distance <= ShelterSelectionPolicy.DOOR_PROBE_DISTANCE; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dzMagnitude = distance - Math.abs(dx);
                if (doorAt(level, origin.offset(dx, 0, dzMagnitude))
                        || (dzMagnitude != 0 && doorAt(level, origin.offset(dx, 0, -dzMagnitude)))) {
                    return distance;
                }
            }
        }
        return ShelterSelectionPolicy.DOOR_PROBE_DISTANCE + 1;
    }

    private static boolean doorAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DoorBlock
                || level.getBlockState(pos.above()).getBlock() instanceof DoorBlock
                || level.getBlockState(pos.below()).getBlock() instanceof DoorBlock;
    }

    private static boolean boundaryWithin(Level level, BlockPos origin, Direction direction) {
        for (int distance = 1; distance <= ShelterSelectionPolicy.BOUNDARY_PROBE_DISTANCE; distance++) {
            BlockPos lower = origin.relative(direction, distance);
            BlockState lowerState = level.getBlockState(lower);
            BlockPos upper = lower.above();
            BlockState upperState = level.getBlockState(upper);
            if (isBoundary(level, lower, lowerState) || isBoundary(level, upper, upperState)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoundary(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.isSolidRender(level, pos);
    }

    private boolean occupied(BlockPos pos) {
        AABB capacityArea = new AABB(pos).inflate(0.55, 0.25, 0.55);
        return !mob.level().getEntitiesOfClass(
                LivingEntity.class,
                capacityArea,
                entity -> entity != mob && entity.isAlive()).isEmpty();
    }

    private boolean pathStaysEntityTicking(Path path) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (!serverLevel.isPositionEntityTicking(path.getNode(i).asBlockPos())) {
                return false;
            }
        }
        return true;
    }
}
