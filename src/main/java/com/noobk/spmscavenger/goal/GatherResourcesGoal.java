package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.CaveContextPolicy;
import com.noobk.spmscavenger.DiscoveryMode;
import com.noobk.spmscavenger.DiscoveryPolicy;
import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.GatherCandidatePolicy;
import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.GatherProtection;
import com.noobk.spmscavenger.GatherTargetPolicy;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ToolBox;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.mining.ExposureOpportunity;
import com.noobk.spmscavenger.mining.ExposureOpportunityPolicy;
import com.noobk.spmscavenger.mining.MiningProject;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MiningBreakTiming;
import com.noobk.spmscavenger.mining.MiningExecutionGuard;
import com.noobk.spmscavenger.mining.MiningGoalKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Breaks nearby wood and coal so the mob has something to make torches out of.
 *
 * <h2>This is the only goal in the mod that changes the world destructively</h2>
 *
 * It is therefore gated three ways, all of which must pass: the feature switch, the vanilla
 * {@code mobGriefing} game rule, and a stock target — once the mob has enough torches it stops
 * entirely. Without that last one a scavenger would strip a forest, because "collect wood" has no
 * natural end condition the way "light this spot" does.
 *
 * <h2>Breaking is paced, not instant</h2>
 *
 * Break time comes from the block's own {@code getDestroySpeed} and the tool the mob happens to be
 * holding, so a mob with an axe fells a tree noticeably faster than one punching it — and stone-class
 * blocks it has no pickaxe for are skipped rather than mined bare-handed forever.
 *
 * <h2>Why it keeps the drops instead of leaving them</h2>
 *
 * The obvious design — break the block and let Social Player Mobs' {@code CollectFloorItemsGoal}
 * collect the drops — <b>silently fails for coal</b>. SPM's {@code ItemPickupPolicy} wants ammo,
 * consumables, block items and a curated valuables set of diamond/emerald/ingot tier; plain
 * {@code minecraft:coal} is none of those, so mined coal would lie on the ground forever and the
 * torch chain would never start. A user could add {@code extraPickupItems=minecraft:coal} to
 * {@code playermob.properties} — but a mod that only works after an undocumented config edit is a
 * mod that does not work.
 *
 * <p>So this goal takes what the chain needs straight out of the drops and lets the rest fall, which
 * is <b>exactly</b> the shape of SPM's own {@code HarvestCropsGoal}: compute {@code Block.getDrops},
 * keep what it wants, drop the remainder, then destroy the block without dropping again. Following
 * the host mod's own precedent, rather than inventing a second scanning-and-pickup system, is what
 * keeps this inside Gate SPM-2.
 */
public class GatherResourcesGoal extends Goal {

    private final Mob mob;
    private final double speed;

    private BlockPos target;
    private int breakTicks;
    private int breakTotal;

    private final PhasedScanClock scanClock;

    /** D-MIW-TS2 - restricts {@link #findTarget} to a physically justified boundary. */
    private java.util.function.Predicate<BlockPos> scanScope;

    /** D-MIW-TS2 - this acquisition was approved cooperatively, not by the global rule. */
    private boolean cooperativeSession;
    private int approachTicks;
    /** Logs already taken from the approved trunk. Zero means this is not a felling session yet. */
    private int felledLogs;
    /** Reachable standing position selected by pathfinding, rather than the solid target block. */
    private BlockPos approachPos;
    /** Precomputed path to {@link #approachPos}; may be partial when foliage blocks the final cells. */
    private Path approachPath;
    /** Whole tree/base identity used for failure backoff. */
    private BlockPos failureKey;
    /** Non-null only for a tree target. Leaf recovery is forbidden for ore targets. */
    private BlockPos treeBase;
    private double lastApproachDistanceSqr;
    private int stalledTicks;
    private int leavesCleared;
    /** Small, bounded per-mob backoff so one inaccessible tree cannot monopolise every scan. */
    private final Map<BlockPos, Long> targetBackoff = new LinkedHashMap<>();
    /** One inventory/config evaluation reused throughout the current bounded target scan. */
    private GatherIntentPolicy.GatherIntent activeIntent;
    /** Last pass-one/pass-two outcome when {@link #findTarget} found nothing — diagnostics (MI-13a). */
    private GatherCandidatePolicy.ScanFailureReason lastScanFailure =
            GatherCandidatePolicy.ScanFailureReason.NONE;
    /** Most recent break position for {@link DiscoveryMode#NEWLY_EXPOSED} vein follow (MI-13). */
    private DiscoveryPolicy.HarvestReveal lastHarvest;

    private static final int SCAN_INTERVAL = 60;
    private static final int SCAN_PHASE_SALT = 61;
    /** Match craft/smelt goals — partial tree paths need longer than ~7s. */
    private static final int MAX_APPROACH_TICKS = 200;
    /** How long to ignore a whole tree/ore after failing to reach it. */
    private static final int UNREACHABLE_COOLDOWN_TICKS = 200;
    private static final double REACH_SQR = 6.0;
    /** Never let one block take longer than this, whatever the maths says. */
    private static final int MAX_BREAK_TICKS = 200;
    /** Gather keeps a longer visible minimum than deliberate excavation. */
    private static final int MIN_BREAK_TICKS = 10;
    /** How many nearest candidates get the expensive build-protection check. */
    private static final int MAX_CANDIDATES = 24;
    /** Pathfinding is substantially more expensive than a block-state check; keep each scan bounded. */
    private static final int MAX_PATH_PROBES = 3;
    private static final int MAX_BACKED_OFF_TARGETS = 8;
    /** A leaf is never touched until one full second has passed without meaningful progress. */
    private static final int LEAF_STALL_TICKS = 20;
    /** One tree approach may remove no more than three directly obstructing leaves. */
    private static final int MAX_LEAVES_CLEARED = 3;
    private static final double LEAF_TREE_RADIUS_SQR = 25.0;
    private static final double PROGRESS_EPSILON_SQR = 0.25;
    /** Most logs one approved tree may yield — a tall jungle trunk, not a forest. */
    private static final int MAX_FELL_LOGS = 12;
    /** Reach while working up a trunk the mob is already standing at the foot of. */
    private static final double FELL_REACH_SQR = 30.0;

    GatherCandidatePolicy.ScanFailureReason lastScanFailureReason() {
        return lastScanFailure;
    }

    public GatherResourcesGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.scanClock = new PhasedScanClock(mob.getId(), SCAN_INTERVAL, SCAN_PHASE_SALT);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.gatherResources) {
            return false;
        }
        if (!mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        if (mob.getTarget() != null) {
            return false;
        }
        if (!MiningExecutionGuard.permits(mob, this, MiningGoalKind.GATHER_RESOURCES)) {
            return false;
        }
        // D-MIW-TS2 - cooperative admission runs BEFORE the global scheduling rule.
        //
        // wantsMore() ends in shouldGather() = hasDemand && readyCraftStep == NOTHING. A mob can
        // genuinely want the diamond the tunnel just exposed while its global admission says
        // "craft something first" - and under TUNNEL_SEARCH, CraftTorchesGoal yields. Tunnel
        // exposes, gather declines, crafter is yielded, nobody consumes the exposure.
        //
        // This bypasses that scheduling rule, never demand: the probe still asks the same intent
        // and candidate policies, only over a physically justified boundary.
        if (tryCooperativeAdmission(cfg)) {
            return true;
        }
        cooperativeSession = false;
        // M3 - the global scheduling rule runs AFTER the cooperative branch. Placing it first
        // meant the bypass sat behind the very gate it exists to bypass: a ready craft step made
        // shouldGather() false and canUse returned before the exposure was ever considered.
        if (!wantsMore(cfg)) {
            return false;
        }
        long now = mob.level().getGameTime();
        if (!scanClock.claim(now)) {
            return false;
        }
        GatherTarget selected = findTarget(cfg);
        if (selected == null) {
            scanClock.resetAfter(now);
            return false;
        }
        target = selected.blockPos();
        approachPos = selected.approachPos();
        approachPath = selected.path();
        failureKey = selected.failureKey();
        treeBase = selected.treeBase();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        boolean hardConditionsPass = target != null
                && mob.getTarget() == null
                && approachTicks < MAX_APPROACH_TICKS
                && cfg.enabled
                && cfg.gatherResources
                && mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && MiningExecutionGuard.permits(mob, this, MiningGoalKind.GATHER_RESOURCES);

        // Crafting availability is a soft acquisition boundary: it may stop the mob from starting
        // another tree, but it must not cancel a trunk that was already approved and opened. Doing
        // so after log one leaves the rest floating, and build protection then correctly refuses to
        // reacquire that now-unrooted trunk.
        // D-MIW-TS2 - an approved cooperative acquisition is not re-litigated against the global
        // scheduling rule. Otherwise the same deadlock returns one tick later: gather starts on the
        // exposed diamond, then stops before reaching it because a craft step became ready.
        if (cooperativeSession) {
            return hardConditionsPass && cooperativeSessionLive();
        }
        boolean acquisitionStillNeeded = hardConditionsPass
                && felledLogs == 0
                && wantsMore(cfg);
        return FellingPolicy.mayContinueGoal(
                hardConditionsPass, felledLogs > 0, acquisitionStillNeeded);
    }

    @Override
    public void start() {
        approachTicks = 0;
        breakTicks = 0;
        breakTotal = 0;
        felledLogs = 0;
        stalledTicks = 0;
        leavesCleared = 0;
        lastApproachDistanceSqr = approachDistanceSqr();
        if (target != null && mob.blockPosition().distSqr(target) > REACH_SQR) {
            moveAlongApproach();
        }
    }

    @Override
    public void stop() {
        if (target != null && approachTicks >= MAX_APPROACH_TICKS) {
            backOff(failureKey != null ? failureKey : target);
        }
        target = null;
        approachPos = null;
        approachPath = null;
        failureKey = null;
        treeBase = null;
        breakTicks = 0;
        breakTotal = 0;
        approachTicks = 0;
        felledLogs = 0;
        stalledTicks = 0;
        leavesCleared = 0;
        lastApproachDistanceSqr = Double.POSITIVE_INFINITY;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        Level level = mob.level();
        BlockState state = level.getBlockState(target);

        // Cheap validation only. The full build-protection check is several hundred block lookups;
        // running it here cost 32% of server tick time in a Spark profile (measured, v1.6.0). It now
        // runs once, on arrival, below.
        if (!isCandidate(level, target, state)) {
            stop();
            return;
        }
        mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        double reach = felledLogs > 0 ? FELL_REACH_SQR : REACH_SQR;
        if (mob.blockPosition().distSqr(target) > reach) {
            approachTicks++;
            if (felledLogs == 0) {
                trackApproachProgress();
                if (stalledTicks >= LEAF_STALL_TICKS && clearOneBlockingLeaf()) {
                    stalledTicks = 0;
                    recomputeApproachPath();
                } else if (mob.getNavigation().isDone() && approachTicks % LEAF_STALL_TICKS == 0) {
                    recomputeApproachPath();
                }
            } else if (mob.getNavigation().isDone()) {
                // Preserve the established vertical-trunk behaviour after the base was approved.
                mob.getNavigation().moveTo(
                        target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
            }
            return;
        }
        mob.getNavigation().stop();

        if (breakTotal == 0) {
            // Arrived. Validate properly once — the world may have changed during the walk — then
            // commit to this tree. Subsequent logs up the same trunk skip the check: the tree was
            // approved as a whole, and once its base is gone the "rooted on soil" test would fail
            // for every log above it.
            if (felledLogs == 0 && !isWanted(state, target)) {
                stop();
                return;
            }
            ToolBox.equipFor(mob, state);   // draw the right tool before the first swing
            breakTotal = breakTicksFor(state);
        }
        if (++breakTicks % 5 == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (breakTicks >= breakTotal) {
            harvest(state);
            if (state.is(BlockTags.LOGS)) {
                felledLogs++;
            }
            if (!continueFelling(level, state)) {
                stop();
            }
        }
    }

    /**
     * Moves up to the next log of the same trunk, so a mob fells a tree instead of taking one block
     * and standing there — which is what a player does, and what v1.0–1.6 conspicuously did not.
     *
     * <p>No protection re-check: this trunk was approved before the first swing, and re-testing it
     * mid-fell would both fail (the base is now air) and reintroduce the per-tick cost.
     *
     * @return true when a new target was taken and the goal should keep running
     */
    private boolean continueFelling(Level level, BlockState harvestedState) {
        BlockPos above = target.above();
        boolean nextIsLog = level.getBlockState(above).is(BlockTags.LOGS);
        if (!FellingPolicy.mayTakeNextLog(
                harvestedState.is(BlockTags.LOGS), nextIsLog, felledLogs, MAX_FELL_LOGS)) {
            return false;
        }
        target = above;
        approachPos = mob.blockPosition().immutable();
        approachPath = null;
        breakTicks = 0;
        breakTotal = 0;
        approachTicks = 0;
        return true;
    }

    /**
     * Keeps what the torch chain needs, drops the rest, then removes the block without dropping it a
     * second time. Mirrors {@code HarvestCropsGoal#harvest} in Social Player Mobs.
     */
    private void harvest(BlockState state) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Container backpack = PlayerMobs.backpack(mob);
        List<ItemStack> drops = Block.getDrops(
                state, serverLevel, target, /* blockEntity */ null, mob, mob.getMainHandItem());

        for (ItemStack drop : drops) {
            if (backpack != null && wantedDrop(drop) && ScavengerCrafting.give(backpack, drop)) {
                continue; // give() consumed the stack
            }
            mob.spawnAtLocation(drop);   // backpack full, or nothing the chain can use
        }
        serverLevel.destroyBlock(target, /* dropBlock */ false, mob);
        lastHarvest = new DiscoveryPolicy.HarvestReveal(target.immutable(), serverLevel.getGameTime());
    }

    /** What the torch and tool chains consume. Cobble is kept when stone upgrades are pending. */
    private boolean wantedDrop(ItemStack stack) {
        GatherIntentPolicy.GatherIntent intent = currentIntent();
        if (stack.is(Items.COBBLESTONE)
                && intent.wants(GatherIntentPolicy.Resource.COBBLESTONE)) {
            return true;
        }
        // TT-2c actual-drop retention: keep what the block really dropped, not an assumed item.
        // Block.getDrops already ran with the live main-hand, so this is raw_iron under an ordinary
        // pick and the ore block itself under Silk Touch - both smelt to an iron ingot.
        if (intent.wants(GatherIntentPolicy.Resource.DIAMOND) && stack.is(Items.DIAMOND)) {
            return true;
        }
        if (intent.wants(GatherIntentPolicy.Resource.RAW_IRON)
                && (stack.is(Items.RAW_IRON)
                        || stack.is(Items.IRON_ORE)
                        || stack.is(Items.DEEPSLATE_IRON_ORE))) {
            return true;
        }
        return (intent.wants(GatherIntentPolicy.Resource.COAL)
                        && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)))
                || (intent.wants(GatherIntentPolicy.Resource.LOGS) && stack.is(ItemTags.LOGS));
    }

    /**
     * Whether there is any point gathering right now.
     *
     * <p>Two independent reasons to want wood: torches, and <b>tools</b>. v1.3 only counted the
     * first, so a mob that had scavenged eight torches from the world would never gather again — and
     * therefore never build a workbench or a pickaxe, no matter how much it needed one. Tools are
     * the thing that unlocks coal, so gating them behind a torch count had it exactly backwards.
     */
    private boolean wantsMore(ScavengerConfig cfg) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return false;
        }
        // Yield only when smelt can actually run — demand alone is insufficient (two logs, one
        // surplus → charcoal demand but empty plan → gather must keep chopping fuel).
        if (mob.level() instanceof ServerLevel server
                && FurnacePolicy.shouldYieldGatherToSmelt(
                        server, backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg)) {
            return false;
        }
        activeIntent = GatherIntentPolicy.evaluate(
                backpack,
                mob.getMainHandItem(),
                mob.getOffhandItem(),
                cfg,
                mob.blockPosition().getY());
        return activeIntent.shouldGather();
    }

    /**
     * How long this block should take, from its own hardness and the held tool. Mirrors the shape of
     * vanilla's calculation without reaching into player-only mining state.
     */
    private int breakTicksFor(BlockState state) {
        // Single owner for the physics. This formula was correct while the two excavation
        // executors had it inverted, which is exactly why keeping a private copy is a latent
        // defect: the next tuning pass would change the shared policy and silently leave gather
        // on the old numbers. The 10-tick floor is gather's own animation choice and stays here.
        return MiningBreakTiming.breakTicks(
                state,
                state.getDestroySpeed(mob.level(), target),
                ToolBox.bestSpeed(mob, state),
                MIN_BREAK_TICKS,
                MAX_BREAK_TICKS);
    }

    private boolean isCandidate(Level level, BlockPos pos, BlockState state) {
        return isCandidate(level, pos, state, 0.0F);
    }

    private boolean isCandidate(Level level, BlockPos pos, BlockState state, float acquisitionCost) {
        return GatherCandidatePolicy.isPassOneCandidate(
                level,
                pos,
                state,
                currentIntent(),
                toolState -> ToolBox.ownsToolFor(mob, toolState),
                acquisitionCost);
    }

    /**
     * Whether a diamond-tool consumer needs diamonds, and the mob is somewhere diamond can exist.
     * Above the generation ceiling this is always false, so the gather scan still switches off.
     */
    private GatherIntentPolicy.GatherIntent currentIntent() {
        Container backpack = PlayerMobs.backpack(mob);
        if (activeIntent == null && backpack != null) {
            activeIntent = GatherIntentPolicy.evaluate(
                    backpack,
                    mob.getMainHandItem(),
                    mob.getOffhandItem(),
                    ScavengerConfig.get(),
                    mob.blockPosition().getY());
        }
        return activeIntent;
    }

    private boolean isWanted(BlockState state, BlockPos pos) {
        Level level = mob.level();
        ScavengerConfig cfg = ScavengerConfig.get();
        if (state.is(BlockTags.LOGS)) {
            return GatherProtection.isGatherableLog(level, pos, cfg);
        }
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            if (!ToolBox.ownsToolFor(mob, state)) {
                return false;
            }
            return GatherProtection.isGatherableOre(level, pos, cfg);
        }
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            if (!currentIntent().wants(GatherIntentPolicy.Resource.RAW_IRON)
                    || !ToolBox.ownsToolFor(mob, state)) {
                return false;
            }
            return GatherProtection.isGatherableOre(level, pos, cfg);
        }
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            if (!currentIntent().wants(GatherIntentPolicy.Resource.DIAMOND)
                    || !ToolBox.ownsToolFor(mob, state)) {
                return false;
            }
            return GatherProtection.isGatherableOre(level, pos, cfg);
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            if (!ToolBox.ownsToolFor(mob, state)) {
                return false;
            }
            return GatherProtection.isGatherableStone(level, pos, cfg);
        }
        return false;
    }

    /**
     * Nearest gatherable block, found in two passes.
     *
     * <p><b>Why two passes.</b> The build-protection check walks a log's column, inspects its canopy
     * and scans a 7x7x7 box around it — several hundred lookups. Running that inside the search loop
     * meant paying it for <em>every log in radius</em>: at radius 20 the scan visits ~15,000
     * positions, and in a forest hundreds of those are logs, so a single scan could cost millions of
     * block lookups. Per mob. Every {@value #SCAN_INTERVAL} ticks.
     *
     * <p>So pass one collects candidates with a <b>cheap</b> test only (right block, right tool) and
     * keeps just the {@value #MAX_CANDIDATES} nearest. Pass two runs the expensive protection on
     * those, in distance order, and stops at the first that passes. Bounded work, same answer.
     */
    private GatherTarget findTarget(ScavengerConfig cfg) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        int r = (int) cfg.gatherSearchRadius;

        BlockPos[] nearest = new BlockPos[MAX_CANDIDATES];
        double[] dists = new double[MAX_CANDIDATES];
        int found = 0;

        lastScanFailure = GatherCandidatePolicy.ScanFailureReason.NONE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    // D-MIW-TS2: a cooperative probe may only look where the excavation actually
                    // revealed something. Without this, "cooperative work" would fund an unrelated
                    // resource excursion while pausing the tunnel's lease for it.
                    if (scanScope != null && !scanScope.test(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    double dist = pos.distSqr(origin);
                    // MI-4R: normalized discovery cost. Path/dig/danger costs remain later RFC work.
                    float acquisitionCost = (float) (Math.sqrt(dist) / 8.0D);
                    if (!isCandidate(level, pos, state, acquisitionCost)) {
                        continue;
                    }
                    // Initial tree acquisition is base-only. Selecting internal/canopy logs is
                    // what made the mob stare into foliage or cycle through unreachable members
                    // of the same tree. Once a base is approved, continueFelling owns the column.
                    if (state.is(BlockTags.LOGS)
                            && !GatherApproachPolicy.isInitialTreeLog(
                                    true, level.getBlockState(pos.below()).is(BlockTags.LOGS))) {
                        continue;
                    }
                    if (found == MAX_CANDIDATES && dist >= dists[found - 1]) {
                        continue;
                    }
                    // Sorted insert into a fixed buffer — no allocation, no full sort.
                    int at = (found == MAX_CANDIDATES) ? found - 1 : found++;
                    while (at > 0 && dists[at - 1] > dist) {
                        dists[at] = dists[at - 1];
                        nearest[at] = nearest[at - 1];
                        at--;
                    }
                    dists[at] = dist;
                    nearest[at] = pos.immutable();
                }
            }
        }

        if (found == 0) {
            lastScanFailure = GatherCandidatePolicy.ScanFailureReason.NO_CANDIDATES_IN_RADIUS;
            return null;
        }

        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        int[] rimSamples = new int[CaveContextPolicy.rimSampleOffsetsX().length];
        int[] ox = CaveContextPolicy.rimSampleOffsetsX();
        int[] oz = CaveContextPolicy.rimSampleOffsetsZ();
        for (int i = 0; i < ox.length; i++) {
            rimSamples[i] = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    origin.getX() + ox[i],
                    origin.getZ() + oz[i]);
        }
        int mobRim = CaveContextPolicy.localRimHeight(rimSamples);
        boolean mobCaveOrRavine =
                CaveContextPolicy.isCaveOrRavineLike(origin.getY(), surfaceY, mobRim);

        boolean[] caveOpportunity = new boolean[found];
        for (int i = 0; i < found; i++) {
            BlockPos pos = nearest[i];
            int columnSurface = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            boolean candidateColumn =
                    CaveContextPolicy.isCaveLike(pos.getY(), columnSurface);
            boolean underMobRim = CaveContextPolicy.isCaveLike(pos.getY(), mobRim);
            caveOpportunity[i] = CaveContextPolicy.caveOpportunity(
                    mobCaveOrRavine, candidateColumn, underMobRim);
        }

        int[] probeOrder = GatherTargetPolicy.sortIndicesByPriority(
                nearest,
                dists,
                found,
                level,
                currentIntent(),
                lastHarvest,
                level.getGameTime(),
                caveOpportunity);

        GatherTarget partialFallback = null;
        int pathProbes = 0;
        boolean sawPassOneCandidate = false;
        for (int orderIndex = 0; orderIndex < found && pathProbes < MAX_PATH_PROBES; orderIndex++) {
            int i = probeOrder[orderIndex];
            BlockPos pos = nearest[i];
            BlockState state = level.getBlockState(pos);
            float acquisitionCost = (float) (Math.sqrt(dists[i]) / 8.0D);
            if (GatherTargetPolicy.priority(
                            currentIntent(),
                            state,
                            DiscoveryPolicy.classify(
                                    level, pos, state, lastHarvest, level.getGameTime()),
                            acquisitionCost,
                            caveOpportunity[i])
                    == Integer.MIN_VALUE) {
                continue;
            }
            BlockPos candidateFailureKey = state.is(BlockTags.LOGS)
                    ? treeFailureKey(level, pos)
                    : pos;
            if (isBackedOff(candidateFailureKey)) {
                continue;
            }
            sawPassOneCandidate = true;
            if (!isWanted(state, pos)) {
                continue;
            }

            BlockPos selectedTreeBase = state.is(BlockTags.LOGS) ? pos : null;
            if (origin.distSqr(pos) <= REACH_SQR) {
                return new GatherTarget(
                        pos, origin.immutable(), candidateFailureKey, selectedTreeBase, null);
            }

            List<BlockPos> approaches = findStandingPositions(
                    level, pos, selectedTreeBase != null && cfg.clearLeafObstructions);
            if (approaches.isEmpty()) {
                backOff(candidateFailureKey);
                continue;
            }
            pathProbes++;
            Path path = mob.getNavigation().createPath(approaches.stream(), 0);
            if (path == null || path.getTarget() == null) {
                backOff(candidateFailureKey);
                continue;
            }
            GatherTarget candidate = new GatherTarget(
                    pos,
                    path.getTarget().immutable(),
                    candidateFailureKey,
                    selectedTreeBase,
                    path);
            if (path.canReach()) {
                return candidate;
            }
            if (partialFallback == null) {
                partialFallback = candidate;
            }
        }
        if (partialFallback == null && sawPassOneCandidate) {
            lastScanFailure = GatherCandidatePolicy.ScanFailureReason.CANDIDATES_ALL_REJECTED_PROTECTION;
        }
        return partialFallback;
    }

    /**
     * Canonicalises adjacent bottom logs (notably 2x2 jungle trunks) to one failure identity. The
     * one-block neighbourhood is deliberate: it groups a multi-column base without turning a grove
     * of nearby but separate trees into one unbounded connected-component scan.
     */
    private BlockPos treeFailureKey(Level level, BlockPos base) {
        BlockPos key = base;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos neighbour = base.offset(dx, 0, dz);
                if (!level.getBlockState(neighbour).is(BlockTags.LOGS)
                        || level.getBlockState(neighbour.below()).is(BlockTags.LOGS)) {
                    continue;
                }
                if (neighbour.getX() < key.getX()
                        || (neighbour.getX() == key.getX() && neighbour.getZ() < key.getZ())) {
                    key = neighbour;
                }
            }
        }
        return key.immutable();
    }

    /** Candidate feet positions close enough to work the block, with solid floor and two clear cells. */
    private List<BlockPos> findStandingPositions(
            Level level, BlockPos block, boolean allowLeafRecovery) {
        List<BlockPos> result = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos feet = block.offset(dx, dy, dz);
                    if (feet.equals(block) || feet.distSqr(block) > REACH_SQR) {
                        continue;
                    }
                    BlockState feetState = level.getBlockState(feet);
                    BlockState headState = level.getBlockState(feet.above());
                    boolean feetAllowed = GatherApproachPolicy.isApproachCellAllowed(
                            feetState.getCollisionShape(level, feet).isEmpty(),
                            feetState.is(BlockTags.LEAVES),
                            allowLeafRecovery);
                    boolean headAllowed = GatherApproachPolicy.isApproachCellAllowed(
                            headState.getCollisionShape(level, feet.above()).isEmpty(),
                            headState.is(BlockTags.LEAVES),
                            allowLeafRecovery);
                    if (feetAllowed
                            && headAllowed
                            && level.getBlockState(feet.below())
                                    .isFaceSturdy(level, feet.below(), Direction.UP)) {
                        result.add(feet.immutable());
                    }
                }
            }
        }
        return result;
    }

    private void trackApproachProgress() {
        double distance = approachDistanceSqr();
        if (GatherApproachPolicy.madeProgress(
                lastApproachDistanceSqr, distance, PROGRESS_EPSILON_SQR)) {
            stalledTicks = 0;
        } else {
            stalledTicks++;
        }
        lastApproachDistanceSqr = distance;
    }

    private double approachDistanceSqr() {
        return approachPos == null
                ? Double.POSITIVE_INFINITY
                : mob.blockPosition().distSqr(approachPos);
    }

    private void moveAlongApproach() {
        if (approachPath != null) {
            mob.getNavigation().moveTo(approachPath, speed);
        } else if (approachPos != null) {
            mob.getNavigation().moveTo(
                    approachPos.getX() + 0.5,
                    approachPos.getY(),
                    approachPos.getZ() + 0.5,
                    speed);
        }
    }

    private void recomputeApproachPath() {
        if (approachPos == null) {
            return;
        }
        Path replacement = mob.getNavigation().createPath(approachPos, 0);
        if (replacement != null) {
            approachPath = replacement;
            moveAlongApproach();
        }
        lastApproachDistanceSqr = approachDistanceSqr();
    }

    /**
     * Removes one leaf directly between the mob and its selected standing position, and nothing
     * else. This is recovery after a measured stall, not a general-purpose foliage-clearing goal.
     */
    private boolean clearOneBlockingLeaf() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!(mob.level() instanceof ServerLevel serverLevel)
                || target == null
                || approachPos == null
                || treeBase == null
                || mob.getTarget() != null) {
            return false;
        }

        Direction direction = horizontalDirectionToward(approachPos);
        BlockPos forward = mob.blockPosition().relative(direction);
        BlockPos[] cells = {forward, forward.above(), forward.above(2)};
        for (BlockPos cell : cells) {
            BlockState state = serverLevel.getBlockState(cell);
            boolean buildProtectionAllows = !cfg.protectPlayerBuilds
                    || !GatherProtection.hasBuiltNearby(serverLevel, cell);
            if (!GatherApproachPolicy.mayClearLeaf(
                    true,
                    cfg.clearLeafObstructions,
                    serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING),
                    state.is(BlockTags.LEAVES),
                    cell.distSqr(treeBase) <= LEAF_TREE_RADIUS_SQR,
                    buildProtectionAllows,
                    leavesCleared,
                    MAX_LEAVES_CLEARED,
                    stalledTicks,
                    LEAF_STALL_TICKS)) {
                continue;
            }
            mob.swing(InteractionHand.MAIN_HAND);
            if (serverLevel.destroyBlock(cell, true, mob)) {
                leavesCleared++;
                return true;
            }
        }
        return false;
    }

    private Direction horizontalDirectionToward(BlockPos destination) {
        BlockPos from = mob.blockPosition();
        int dx = destination.getX() - from.getX();
        int dz = destination.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return mob.getDirection();
    }

    private boolean isBackedOff(BlockPos key) {
        long now = mob.level().getGameTime();
        targetBackoff.entrySet().removeIf(entry -> entry.getValue() <= now);
        Long expires = targetBackoff.get(key);
        return expires != null && expires > now;
    }

    private void backOff(BlockPos key) {
        if (key == null) {
            return;
        }
        targetBackoff.remove(key);
        while (targetBackoff.size() >= MAX_BACKED_OFF_TARGETS) {
            BlockPos oldest = targetBackoff.keySet().iterator().next();
            targetBackoff.remove(oldest);
        }
        targetBackoff.put(
                key.immutable(), mob.level().getGameTime() + UNREACHABLE_COOLDOWN_TICKS);
    }

    /**
     * D-MIW-TS2 — the cooperative gather lifecycle.
     *
     * <pre>
     * OFFERED    one exposure-local probe over the cells the cut opened
     *   nothing  → offer already consumed by the take; tunnel resumes
     *   target   → ACQUIRING
     * ACQUIRING  continuation probes ONLY the frontier gather's own lastHarvest created
     *   target   → keep the session, refresh its idle clock
     *   nothing  → clear the session; tunnel resumes
     * </pre>
     *
     * <p>Three scopes, one target-selection implementation: normal gather scans the world, an
     * offered probe scans the excavation boundary, and a continuation scans the vein frontier. No
     * second gather system, and no path by which cooperative status funds an unrelated errand.
     */
    private boolean tryCooperativeAdmission(ScavengerConfig cfg) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        MiningProject project = store.projectOf(mob.getUUID())
                .filter(candidate -> candidate.mode() == MiningProjectMode.TUNNEL_SEARCH)
                .filter(MiningProject::isActive)
                .orElse(null);
        if (project == null) {
            cooperativeSession = false;
            return false;
        }
        long now = level.getGameTime();
        ExposureOpportunity exposure = store.exposureOf(mob.getUUID()).orElse(null);

        if (ExposureOpportunityPolicy.holdsCooperativeSession(exposure, project, now)) {
            return continueCooperativeVein(cfg, store, project, now);
        }
        if (!ExposureOpportunityPolicy.offersProbe(exposure, project, now)) {
            cooperativeSession = false;
            return false;
        }
        ExposureOpportunity taken = store.takeExposureProbe(mob.getUUID(), project, now)
                .orElse(null);
        if (taken == null) {
            return false;
        }
        // The probe is spent whether or not it finds anything; a fruitless look releases the tunnel
        // immediately rather than holding it while we decide.
        GatherTarget selected = scopedTarget(cfg, pos ->
                ExposureOpportunityPolicy.isExposureLocal(taken, pos));
        if (selected == null) {
            cooperativeSession = false;
            return false;
        }
        if (!store.beginCooperativeAcquisition(mob.getUUID(), project, taken, now)) {
            return false;
        }
        adopt(selected);
        cooperativeSession = true;
        return true;
    }

    /** Continuation looks only at the physical frontier gather itself created. */
    private boolean continueCooperativeVein(
            ScavengerConfig cfg, MiningProjectSavedData store, MiningProject project, long now) {
        DiscoveryPolicy.HarvestReveal reveal = lastHarvest;
        GatherTarget selected = reveal == null || reveal.pos() == null
                ? null
                : scopedTarget(cfg, pos -> pos.distManhattan(reveal.pos()) <= 1);
        if (selected == null) {
            // The vein is finished. Release the tunnel rather than drifting into a broad scan under
            // cooperative cover.
            store.clearExposure(mob.getUUID());
            cooperativeSession = false;
            return false;
        }
        store.noteCooperativeAcquisition(mob.getUUID(), project, now);
        adopt(selected);
        cooperativeSession = true;
        return true;
    }

    /** Existing selection, existing policies, restricted to a physically justified scope. */
    private GatherTarget scopedTarget(
            ScavengerConfig cfg, java.util.function.Predicate<BlockPos> scope) {
        // Demand still governs what counts as a candidate; only the global "craft first" ordering
        // is bypassed, so activeIntent must be populated without wantsMore().
        activeIntent = GatherIntentPolicy.evaluate(
                PlayerMobs.backpack(mob),
                mob.getMainHandItem(),
                mob.getOffhandItem(),
                cfg,
                mob.blockPosition().getY());
        scanScope = scope;
        try {
            return findTarget(cfg);
        } finally {
            scanScope = null;
        }
    }

    private boolean cooperativeSessionLive() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        return store.projectOf(mob.getUUID())
                .filter(project -> project.mode() == MiningProjectMode.TUNNEL_SEARCH)
                .filter(project -> ExposureOpportunityPolicy.holdsCooperativeSession(
                        store.exposureOf(mob.getUUID()).orElse(null), project, level.getGameTime()))
                .isPresent();
    }

    private void adopt(GatherTarget selected) {
        target = selected.blockPos();
        approachPos = selected.approachPos();
        approachPath = selected.path();
        failureKey = selected.failureKey();
        treeBase = selected.treeBase();
    }

    private record GatherTarget(
            BlockPos blockPos,
            BlockPos approachPos,
            BlockPos failureKey,
            BlockPos treeBase,
            Path path) {
    }
}
