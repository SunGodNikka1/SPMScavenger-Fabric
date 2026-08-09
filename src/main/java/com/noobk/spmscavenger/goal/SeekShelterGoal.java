package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ShelterScore;
import com.noobk.spmscavenger.experience.RestAnchorType;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

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
     * released on {@link #stop()} and expire on their own if a mob dies mid-journey.
     */
    private static final Map<BlockPos, Claim> CLAIMS = new ConcurrentHashMap<>();

    private record Claim(UUID mob, long expiresAtTick) {
    }

    /** A claim outlives roughly the longest sensible walk, then lapses. */
    private static final long CLAIM_TICKS = 600L;

    private final Mob mob;
    private final double speed;

    private BlockPos standPos;
    private BlockPos bedPos;
    private final PhasedScanClock scanClock;
    private int approachTicks;
    private boolean restClaimOpened;

    private static final int SCAN_INTERVAL = 40;
    private static final int SCAN_PHASE_SALT = 11;
    private static final int MAX_APPROACH_TICKS = 400;
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
        if (!cfg.enabled || !cfg.seekShelter) {
            return false;
        }
        Level level = mob.level();
        if (!scanClock.claim(level.getGameTime())) {
            return false;
        }
        if (!shelterTime(level) || mob.getTarget() != null) {
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
        if (!ScavengerConfig.get().seekShelter || !shelterTime(mob.level())) {
            return false;
        }
        if (mob.getTarget() != null || mob.hurtTime > 0) {
            return false; // woken by a fight, or by being hit while asleep
        }
        return standPos != null && (mob.isSleeping() || approachTicks < MAX_APPROACH_TICKS);
    }

    @Override
    public void start() {
        approachTicks = 0;
        restClaimOpened = false;
        if (standPos != null) {
            mob.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        }
    }

    @Override
    public void stop() {
        if (mob.isSleeping()) {
            mob.stopSleeping();   // also clears BedBlock.OCCUPIED
        }
        release();
        standPos = null;
        bedPos = null;
        approachTicks = 0;
        restClaimOpened = false;
        scanClock.resetAfter(mob.level().getGameTime());
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (standPos == null || mob.isSleeping()) {
            return; // asleep: nothing to do until canContinueToUse says otherwise
        }
        approachTicks++;

        if (bedPos != null && mob.blockPosition().distSqr(bedPos) <= BED_REACH_SQR) {
            lieDown();
            return;
        }
        if (bedPos == null && mob.blockPosition().distSqr(standPos) <= ARRIVED_SQR) {
            if (!restClaimOpened) {
                RestSessionCoordinator.openShelterRecovery(
                        mob, standPos, RestAnchorType.SHELTER_STAND, mob.level().getGameTime());
                restClaimOpened = true;
            }
            mob.getNavigation().stop(); // arrived; wait out the night
            return;
        }
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        }
    }

    private void lieDown() {
        BlockState state = mob.level().getBlockState(bedPos);
        // Re-check on arrival: someone may have taken or broken the bed during the walk.
        if (state.is(BlockTags.BEDS) && !state.getValue(BedBlock.OCCUPIED)) {
            mob.getNavigation().stop();
            mob.startSleeping(bedPos);
            if (!restClaimOpened) {
                RestSessionCoordinator.openShelterRecovery(
                        mob, bedPos, RestAnchorType.SHELTER_BED, mob.level().getGameTime());
                restClaimOpened = true;
            }
        } else {
            release();
            bedPos = null;
            standPos = null;
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

        BlockPos bestStand = null;
        BlockPos bestBed = null;
        double bestScore = -Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
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
                        bestBed = isBed ? pos.immutable() : null;
                    }
                }
            }
        }

        if (bestStand == null) {
            return false;
        }
        standPos = bestStand;
        bedPos = bestBed;
        if (bedPos != null) {
            claim(bedPos);
        }
        return true;
    }

    /** Free bed, not already occupied and not being walked toward by another mob. */
    private boolean claimable(BlockPos pos, BlockState state) {
        if (state.getValue(BedBlock.OCCUPIED)) {
            return false;
        }
        Claim claim = CLAIMS.get(pos);
        if (claim == null) {
            return true;
        }
        return claim.mob().equals(mob.getUUID()) || mob.level().getGameTime() > claim.expiresAtTick();
    }

    private void claim(BlockPos pos) {
        CLAIMS.put(pos.immutable(), new Claim(mob.getUUID(), mob.level().getGameTime() + CLAIM_TICKS));
    }

    private void release() {
        if (bedPos == null) {
            return;
        }
        Claim claim = CLAIMS.get(bedPos);
        // Only drop a claim this mob actually owns — another mob may have taken it after expiry.
        if (claim != null && claim.mob().equals(mob.getUUID())) {
            CLAIMS.remove(bedPos);
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
