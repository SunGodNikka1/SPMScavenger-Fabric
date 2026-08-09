package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.GatherProtection;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Recovers a PlayerMob from powder snow or a solid block intersecting its body.
 *
 * <p>Movement is always attempted first. World mutation is a delayed, bounded fallback and only
 * touches the actual intersecting obstruction. Fire is deliberately excluded: Social Player Mobs'
 * own {@code FireBucketGoal} owns that lifecycle and must be allowed to run.
 */
public final class EnvironmentalEscapeGoal extends Goal {

    private static final TagKey<net.minecraft.world.level.block.Block> BREAKABLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SpmScavenger.MOD_ID, "environmental_escape_breakable"));
    private static final TagKey<net.minecraft.world.level.block.Block> NEVER_BREAK = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SpmScavenger.MOD_ID, "environmental_escape_never_break"));
    private static final int REPATH_INTERVAL = 20;
    /** Sustained clear required before an incident is considered over. See the policy javadoc. */
    private static final int INCIDENT_CLEAR_TICKS = 40;
    private static final int MAX_PATH_ATTEMPTS = 12;
    private static final int[][] HORIZONTAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final PathfinderMob mob;
    private int trappedTicks;
    private int blocksBroken;
    private boolean incidentActive;
    private boolean breakLimitLogged;
    private BlockPos miningPos;
    private int miningTicks;
    private int requiredMiningTicks;
    private int clearStreak;
    private int equippedToolSlot = -1;
    private ItemStack parkedMainHand;
    private int nextMiningTick;

    public EnvironmentalEscapeGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.environmentalEscape || mob.isOnFire() || !mob.isAlive()
                || mob.isPassenger()) {
            return false;
        }
        if (isTrapped()) {
            clearStreak = 0;
            return true;
        }
        // A single "not trapped" reading is almost always the mob bobbing, not the mob escaping.
        // Keep the incident - and its grace timer - alive until the clear is sustained.
        if (EnvironmentalEscapePolicy.incidentSurvivesClear(
                incidentActive, clearStreak++, INCIDENT_CLEAR_TICKS)) {
            return false;
        }
        clearIncident();
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        return cfg.enabled && cfg.environmentalEscape && mob.isAlive() && !mob.isPassenger()
                && !mob.isOnFire() && isTrapped();
    }

    @Override
    public void start() {
        if (!incidentActive) {
            incidentActive = true;
            trappedTicks = 0;
            blocksBroken = 0;
            breakLimitLogged = false;
            nextMiningTick = 0;
        }
        planEscapePath();
    }

    @Override
    public void tick() {
        trappedTicks++;
        if (isTrapped()) {
            clearStreak = 0;
        }

        if (miningPos != null) {
            tickMining();
            return;
        }

        ScavengerConfig cfg = ScavengerConfig.get();
        int graceTicks = mob.isInWall() ? 0 : Math.max(0, cfg.environmentalEscapeGraceTicks);

        // Jumping belongs to the movement-first window only. Past that the remedy is breaking the
        // block, and jumping actively prevents it: it lifts the mob clear for a tick, the trapped
        // predicate flickers, and in powder snow the mob simply sinks back in having reset nothing
        // but its own progress.
        if (trappedTicks < graceTicks) {
            mob.getJumpControl().jump();
        }

        if (trappedTicks % REPATH_INTERVAL == 1) {
            planEscapePath();
        }

        if (trappedTicks < graceTicks || trappedTicks < nextMiningTick) {
            return;
        }

        BlockPos obstruction = findBreakableObstruction(cfg);
        if (obstruction != null) {
            beginMining(obstruction);
        } else if (blocksBroken >= Math.max(0, cfg.environmentalEscapeMaxBlocks)
                && !breakLimitLogged) {
            breakLimitLogged = true;
            SpmScavenger.LOGGER.debug(
                    "[spmscavenger] environmental escape reached its {}-block incident limit for {}",
                    cfg.environmentalEscapeMaxBlocks, mob.getUUID());
        }
    }

    @Override
    public void stop() {
        cancelMining();
        mob.getNavigation().stop();
        // Deliberately does not clear the incident. Being interrupted - by a flicker, by combat, by
        // fleeing - is not evidence of escape, and wiping the grace timer here is what let a mob
        // stay stuck forever while looking busy. canUse() ends the incident once the clear lasts.
    }

    private boolean isTrapped() {
        return mob.isInPowderSnow || mob.isInWall();
    }

    /** Searches only a small local cube, and only when starting or re-planning. */
    private void planEscapePath() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        int radius = Math.max(1, Math.min(8, cfg.environmentalEscapeSearchRadius));
        BlockPos origin = mob.blockPosition();
        List<BlockPos> candidates = new ArrayList<>(radius * HORIZONTAL_DIRECTIONS.length * 4);
        for (int distance = 1; distance <= radius; distance++) {
            for (int[] direction : HORIZONTAL_DIRECTIONS) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos candidate = origin.offset(
                            direction[0] * distance, dy, direction[1] * distance);
                    if (isSafeStandPosition(level, candidate)) {
                        candidates.add(candidate.immutable());
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        int attempts = 0;
        for (BlockPos candidate : candidates) {
            if (attempts++ >= MAX_PATH_ATTEMPTS) {
                break;
            }
            Path path = mob.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                mob.getNavigation().moveTo(path,
                        Math.max(0.5, Math.min(1.5, cfg.environmentalEscapeSpeed)));
                return;
            }
        }
    }

    private boolean isSafeStandPosition(ServerLevel level, BlockPos feet) {
        if (!level.isPositionEntityTicking(feet)) {
            return false;
        }
        BlockPos head = feet.above();
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState floorState = level.getBlockState(feet.below());
        // Same predicate the obstruction search uses. Deriving "bad block" a second time here is how
        // an escape route ends at a block the mob then has to escape from - and the narrower copy
        // only knew about vanilla powder snow, so a modded suffocating block read as a safe landing.
        if (isActualObstruction(level, feet, feetState)
                || isActualObstruction(level, head, headState)
                || !feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()
                || !floorState.isFaceSturdy(level, feet.below(), Direction.UP)) {
            return false;
        }
        AABB moved = mob.getBoundingBox().move(
                feet.getX() + 0.5 - mob.getX(), feet.getY() - mob.getY(),
                feet.getZ() + 0.5 - mob.getZ());
        return level.noCollision(mob, moved);
    }

    private BlockPos findBreakableObstruction(ScavengerConfig cfg) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB bounds = mob.getBoundingBox().deflate(1.0E-4);
        return BlockPos.betweenClosedStream(bounds)
                .map(BlockPos::immutable)
                .filter(pos -> isActualObstruction(level, pos))
                .filter(pos -> mayBreak(level, pos, cfg))
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(mob.getEyePosition())))
                .orElse(null);
    }

    private boolean isActualObstruction(ServerLevel level, BlockPos pos) {
        return isActualObstruction(level, pos, level.getBlockState(pos));
    }

    /** Overload for callers that already hold the state, so a landing check does not re-read it. */
    private boolean isActualObstruction(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(Blocks.POWDER_SNOW) || state.isSuffocating(level, pos);
    }

    private boolean mayBreak(ServerLevel level, BlockPos pos, ScavengerConfig cfg) {
        BlockState state = level.getBlockState(pos);
        boolean explicitlyAllowed = state.is(BREAKABLE);
        boolean natural = GatherProtection.isNatural(state);
        float speed = state.getDestroySpeed(level, pos);
        return EnvironmentalEscapePolicy.mayBreakEntrappingBlock(
                cfg.environmentalEscapeBreakBlocks,
                level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING),
                mob.getBoundingBox().intersects(new AABB(pos)),
                state.hasBlockEntity(),
                state.is(NEVER_BREAK),
                natural || explicitlyAllowed,
                speed,
                (float) Math.max(0.0, cfg.environmentalEscapeMaxHardness),
                blocksBroken,
                Math.max(0, cfg.environmentalEscapeMaxBlocks),
                trappedTicks,
                mob.isInWall() ? 0 : Math.max(0, cfg.environmentalEscapeGraceTicks));
    }

    private void beginMining(BlockPos obstruction) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        mob.getNavigation().stop();
        miningPos = obstruction.immutable();
        miningTicks = 0;
        ToolChoice choice = chooseBestTool(level.getBlockState(obstruction), obstruction);
        requiredMiningTicks = choice.requiredTicks();
        equipTemporarily(choice);
    }

    private void tickMining() {
        if (!(mob.level() instanceof ServerLevel level) || miningPos == null) {
            cancelMining();
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!isActualObstruction(level, miningPos) || !mayBreak(level, miningPos, cfg)) {
            cancelMining();
            return;
        }

        mob.getLookControl().setLookAt(
                miningPos.getX() + 0.5, miningPos.getY() + 0.5, miningPos.getZ() + 0.5,
                30.0F, 30.0F);
        if (miningTicks % 5 == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        miningTicks++;
        level.destroyBlockProgress(mob.getId(), miningPos,
                MiningPolicy.crackStage(miningTicks, requiredMiningTicks));
        if (miningTicks < requiredMiningTicks) {
            return;
        }

        BlockPos brokenPos = miningPos;
        BlockState state = level.getBlockState(brokenPos);
        BlockEntity blockEntity = level.getBlockEntity(brokenPos);
        ItemStack usedTool = mob.getMainHandItem();
        boolean harvested = canHarvest(state, usedTool);
        if (level.destroyBlock(brokenPos, false, mob)) {
            // Vanilla gates drops and experience on the correct tool in ServerPlayerGameMode, not in
            // the loot table, so dropping unconditionally would pay cobblestone for a bare-handed
            // stone break. Durability is still spent either way, as it is for a player.
            if (harvested) {
                Block.dropResources(state, level, brokenPos, blockEntity, mob, usedTool);
                state.spawnAfterBreak(level, brokenPos, usedTool, true);
            }
            if (!usedTool.isEmpty()) {
                usedTool.hurtAndBreak(1, mob, EquipmentSlot.MAINHAND);
            }
            blocksBroken++;
        }
        level.destroyBlockProgress(mob.getId(), brokenPos, -1);
        restorePreviousEquipment();
        clearMiningState();
        nextMiningTick = trappedTicks
                + Math.max(1, cfg.environmentalEscapeBreakIntervalTicks);
        planEscapePath();
    }

    private ToolChoice chooseBestTool(BlockState state, BlockPos pos) {
        ItemStack main = mob.getMainHandItem();
        ToolChoice best = new ToolChoice(-1, main, requiredTicks(state, pos, main));
        if (!(mob instanceof InventoryCarrier carrier)) {
            return best;
        }
        SimpleContainer inventory = carrier.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty()) {
                continue;
            }
            int ticks = requiredTicks(state, pos, candidate);
            if (ticks < best.requiredTicks()) {
                best = new ToolChoice(slot, candidate, ticks);
            }
        }
        return best;
    }

    private int requiredTicks(BlockState state, BlockPos pos, ItemStack stack) {
        float hardness = state.getDestroySpeed(mob.level(), pos);
        float speed = stack.isEmpty() ? 1.0F : stack.getDestroySpeed(state);
        return MiningPolicy.requiredTicks(hardness, speed, canHarvest(state, stack));
    }

    /** One harvest answer, shared by the break timing and the drop decision. */
    private static boolean canHarvest(BlockState state, ItemStack stack) {
        return MiningPolicy.dropsAllowed(
                state.requiresCorrectToolForDrops(),
                !stack.isEmpty() && stack.isCorrectToolForDrops(state));
    }

    private void equipTemporarily(ToolChoice choice) {
        if (choice.slot() < 0 || !(mob instanceof InventoryCarrier carrier)) {
            equippedToolSlot = -1;
            parkedMainHand = null;
            return;
        }
        SimpleContainer inventory = carrier.getInventory();
        ItemStack selected = inventory.removeItemNoUpdate(choice.slot());
        parkedMainHand = mob.getMainHandItem();
        inventory.setItem(choice.slot(), parkedMainHand);
        mob.setItemSlot(EquipmentSlot.MAINHAND, selected);
        equippedToolSlot = choice.slot();
        inventory.setChanged();
    }

    private void restorePreviousEquipment() {
        if (equippedToolSlot < 0 || !(mob instanceof InventoryCarrier carrier)) {
            equippedToolSlot = -1;
            parkedMainHand = null;
            return;
        }
        SimpleContainer inventory = carrier.getInventory();
        ItemStack parked = inventory.getItem(equippedToolSlot);
        if (parked == parkedMainHand) {
            ItemStack usedTool = mob.getMainHandItem();
            inventory.setItem(equippedToolSlot, usedTool);
            mob.setItemSlot(EquipmentSlot.MAINHAND, parked);
            inventory.setChanged();
        } else {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] backpack slot {} changed during environmental mining for {}; "
                            + "leaving current equipment untouched to avoid item loss",
                    equippedToolSlot, mob.getUUID());
        }
        equippedToolSlot = -1;
        parkedMainHand = null;
    }

    private void cancelMining() {
        if (miningPos != null && mob.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(mob.getId(), miningPos, -1);
        }
        restorePreviousEquipment();
        clearMiningState();
    }

    private void clearMiningState() {
        miningPos = null;
        miningTicks = 0;
        requiredMiningTicks = 0;
    }

    private void clearIncident() {
        incidentActive = false;
        clearStreak = 0;
        trappedTicks = 0;
        blocksBroken = 0;
        breakLimitLogged = false;
        nextMiningTick = 0;
    }

    private record ToolChoice(int slot, ItemStack stack, int requiredTicks) {
    }
}
