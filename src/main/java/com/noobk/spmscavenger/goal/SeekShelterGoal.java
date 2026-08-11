package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ShelterScore;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.RestAnchorType;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At night, go somewhere with a roof — and if there is a bed, actually sleep in it.
 *
 * <h2>Ranked, not nearest-first</h2>
 *
 * Candidates are scored by {@link ShelterScore}: a bed dominates, then enclosure, then light, with a
 * mild distance penalty. See that class for why each weight is what it is.
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
    private boolean executionRunning;
    private BlockPos rejectedDestination;
    private long rejectedUntilTick;

    private static final int SCAN_INTERVAL = 40;
    private static final int SCAN_PHASE_SALT = 11;
    private static final double ARRIVED_SQR = 4.0;
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
        if (bedPos == null && mob.blockPosition().distSqr(standPos) <= ARRIVED_SQR) {
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

    /** Picks the best-scoring candidate in range, or leaves the goal unused. */
    private boolean search(ScavengerConfig cfg) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        int r = (int) cfg.shelterSearchRadius;
        long now = level.getGameTime();

        sweepExpiredClaims(now);
        if (rejectedDestination != null && now > rejectedUntilTick) {
            rejectedDestination = null;
        }

        BlockPos bestStand = null;
        BlockPos bestBed = null;
        double bestScore = -Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (rejectedDestination != null && rejectedDestination.equals(pos)) {
                        continue;
                    }
                    if (level instanceof ServerLevel serverLevel
                            && !serverLevel.isPositionEntityTicking(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    double distance = Math.sqrt(pos.distSqr(origin));

                    boolean isBed = cfg.sleepInBeds && state.is(BlockTags.BEDS) && claimable(pos, state);
                    if (!isBed && (level.canSeeSky(pos) || !standable(level, pos))) {
                        continue;
                    }
                    double score = ShelterScore.score(
                            isBed,
                            solidNeighbours(level, pos),
                            level.getBrightness(LightLayer.BLOCK, pos),
                            distance,
                            cfg.torchLightLevel);

                    if (!isBed && score < ShelterScore.MIN_WORTHWHILE_SPOT) {
                        continue; // a bare overhang is not worth walking to
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestStand = pos.immutable();
                        bestBed = isBed ? canonicalBedPos(pos, state) : null;
                    }
                }
            }
        }

        if (bestStand == null) {
            return false;
        }
        commitment = new ShelterCommitment(bestStand, bestBed, mob.getUUID(), now);
        if (bestBed != null) {
            claim(bestBed, commitment.claimant(), now);
        }
        return true;
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
    }

    /** Release claims when Minecraft removes the owning entity without a final Goal stop tick. */
    public static void onEntityUnload(UUID mobId) {
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().mob().equals(mobId));
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

    private void claim(BlockPos pos, UUID claimant, long now) {
        CLAIMS.put(pos.immutable(), new Claim(claimant, now + CLAIM_TICKS));
    }

    private void release() {
        if (commitment == null || commitment.bedPos().isEmpty()) {
            return;
        }
        BlockPos bedPos = commitment.bedPos().orElseThrow();
        Claim claim = CLAIMS.get(bedPos);
        // Only drop a claim this mob actually owns — another mob may have taken it after expiry.
        if (claim != null && claim.mob().equals(commitment.claimant())) {
            CLAIMS.remove(bedPos);
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
}
