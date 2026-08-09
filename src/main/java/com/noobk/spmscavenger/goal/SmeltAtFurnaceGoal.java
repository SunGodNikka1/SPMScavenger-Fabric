package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.FurnaceJobSavedData;
import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.FurnaceStations;
import com.noobk.spmscavenger.FurnaceTransfers;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.StackFingerprint;
import com.noobk.spmscavenger.mining.MiningExecutionGuard;
import com.noobk.spmscavenger.mining.MiningGoalKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Walk to a usable furnace, insert a planned job, wait for vanilla cooking, extract the result
 * (D-FSM-001 goal half / FS-3). Charcoal and iron share this goal via {@link FurnacePolicy#demand}.
 */
public class SmeltAtFurnaceGoal extends Goal {

    private final Mob mob;
    private final double speed;

    private BlockPos furnacePos;
    private BlockPos tablePos;
    private FurnacePolicy.SmeltPlan plan;
    private int ticks;
    private int approachTicks;
    private int waitTicks;
    private boolean craftingFurnace;

    private static final int CRAFT_TICKS = 20;
    private static final int MAX_APPROACH_TICKS = 200;
    private static final double REACH_SQR = 6.0;
    private static final int WAIT_SLACK_TICKS = 100;

    public SmeltAtFurnaceGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.smeltEnabled || mob.getTarget() != null) {
            return false;
        }
        if (!MiningExecutionGuard.permits(mob, this, MiningGoalKind.SMELT_AT_FURNACE)) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel server)) {
            return false;
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return false;
        }

        // Resume an open ticket for this mob.
        FurnaceJobSavedData data = FurnaceJobSavedData.get(server);
        for (FurnaceJobSavedData.FurnaceJobTicket ticket : data.allTickets()) {
            if (ticket.claimantMob().equals(mob.getUUID())
                    && ticket.phase() == FurnaceJobSavedData.JobPhase.INSERTED) {
                furnacePos = ticket.furnacePos();
                return true;
            }
        }

        Optional<FurnacePolicy.SmeltPlan> planned =
                FurnacePolicy.plan(
                        server, backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg);
        if (planned.isEmpty()) {
            return false;
        }
        plan = planned.get();

        furnacePos = FurnaceStations.findUsable(server, mob.blockPosition(), mob.getUUID(), cfg);
        if (furnacePos != null) {
            return FurnaceStations.tryClaimWalk(furnacePos, mob.getUUID(), server.getGameTime());
        }
        if (cfg.placeFurnaces
                && (ScavengerCrafting.count(backpack, Items.FURNACE) > 0
                        || ScavengerCrafting.canMakeFurnace(backpack))) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null
                && approachTicks < MAX_APPROACH_TICKS
                && ScavengerConfig.get().smeltEnabled
                && MiningExecutionGuard.permits(mob, this, MiningGoalKind.SMELT_AT_FURNACE);
    }

    @Override
    public void start() {
        ticks = 0;
        approachTicks = 0;
        waitTicks = 0;
        craftingFurnace = false;
        tablePos = null;
    }

    @Override
    public void stop() {
        if (furnacePos != null) {
            FurnaceStations.releaseWalk(furnacePos, mob.getUUID());
        }
        furnacePos = null;
        tablePos = null;
        plan = null;
        ticks = 0;
        approachTicks = 0;
        waitTicks = 0;
        craftingFurnace = false;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel server)) {
            stop();
            return;
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            stop();
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        FurnaceJobSavedData data = FurnaceJobSavedData.get(server);

        Optional<FurnaceJobSavedData.FurnaceJobTicket> open = data.allTickets().stream()
                .filter(t -> t.claimantMob().equals(mob.getUUID())
                        && t.phase() == FurnaceJobSavedData.JobPhase.INSERTED)
                .findFirst();
        if (open.isPresent()) {
            furnacePos = open.get().furnacePos();
            tickWaitOrExtract(server, backpack, data, open.get());
            return;
        }

        if (furnacePos == null) {
            furnacePos = FurnaceStations.findUsable(server, mob.blockPosition(), mob.getUUID(), cfg);
        }
        if (furnacePos == null) {
            if (!ensureFurnaceItem(server, backpack, cfg)) {
                if (approachTicks >= MAX_APPROACH_TICKS) {
                    stop();
                }
                return;
            }
            BlockPos placed = FurnaceStations.tryPlaceFromBackpack(server, mob, backpack, cfg);
            if (placed == null) {
                approachTicks++;
                if (approachTicks >= MAX_APPROACH_TICKS) {
                    stop();
                }
                return;
            }
            approachTicks = 0;
            furnacePos = placed;
            FurnaceStations.tryClaimWalk(furnacePos, mob.getUUID(), server.getGameTime());
            mob.swing(InteractionHand.MAIN_HAND);
        }

        if (!walkTo(furnacePos)) {
            return;
        }

        if (plan == null) {
            plan = FurnacePolicy.plan(
                            server, backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg)
                    .orElse(null);
            if (plan == null) {
                stop();
                return;
            }
        }

        BlockEntity be = server.getBlockEntity(furnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            data.closeTicket(furnacePos);
            stop();
            return;
        }

        if (++ticks < CRAFT_TICKS) {
            return;
        }
        ticks = 0;

        if (!FurnaceTransfers.tryInsert(
                backpack, furnace, plan.input(), plan.fuelChosen(), plan.output())) {
            stop();
            return;
        }

        FurnaceJobSavedData.FurnaceJobTicket ticket = new FurnaceJobSavedData.FurnaceJobTicket(
                furnacePos.immutable(),
                mob.getUUID(),
                StackFingerprint.of(plan.input()),
                StackFingerprint.of(plan.fuelChosen()),
                StackFingerprint.of(plan.output()),
                1,
                server.getGameTime(),
                plan.recipeId(),
                FurnaceJobSavedData.JobPhase.INSERTED);
        data.putTicket(ticket);
        data.recordPlaced(furnacePos);
        mob.swing(InteractionHand.MAIN_HAND);
        waitTicks = 0;
    }

    private void tickWaitOrExtract(
            ServerLevel server,
            Container backpack,
            FurnaceJobSavedData data,
            FurnaceJobSavedData.FurnaceJobTicket ticket) {
        if (!walkTo(ticket.furnacePos())) {
            return;
        }
        BlockEntity be = server.getBlockEntity(ticket.furnacePos());
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            data.closeTicket(ticket.furnacePos());
            stop();
            return;
        }

        Optional<ItemStack> extracted = FurnaceTransfers.tryExtract(
                backpack, furnace, ticket.expectedOutput(), ticket.expectedOutput().count());
        if (extracted.isPresent()) {
            data.closeTicket(ticket.furnacePos());
            FurnaceStations.releaseWalk(ticket.furnacePos(), mob.getUUID());
            mob.swing(InteractionHand.MAIN_HAND);
            stop();
            return;
        }

        waitTicks++;
        int limit = Math.max(ticket.expectedOutput().count() * FurnacePolicy.VANILLA_SMELT_TICKS, 200)
                + WAIT_SLACK_TICKS;
        // Prefer plan cooking time when available.
        if (plan != null) {
            limit = plan.cookingTicks() + WAIT_SLACK_TICKS;
        }
        if (waitTicks > limit) {
            // Fail closed: leave world furnace state; close ticket without inventing stacks.
            data.closeTicket(ticket.furnacePos());
            stop();
        }
    }

    private boolean ensureFurnaceItem(ServerLevel server, Container backpack, ScavengerConfig cfg) {
        if (ScavengerCrafting.count(backpack, Items.FURNACE) > 0) {
            return true;
        }
        if (!cfg.placeFurnaces || !ScavengerCrafting.canMakeFurnace(backpack)) {
            return false;
        }
        if (!ensureAtCraftingTable(server, backpack, cfg)) {
            return false;
        }
        if (++ticks < CRAFT_TICKS) {
            return false;
        }
        ticks = 0;
        if (!ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_FURNACE)) {
            return false;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        craftingFurnace = false;
        return ScavengerCrafting.count(backpack, Items.FURNACE) > 0;
    }

    /**
     * Walk to a table and place one if needed — mirrors {@link CraftTorchesGoal} so smelting is not
     * stranded in the open when {@code placeCraftingTables} is on.
     */
    private boolean ensureAtCraftingTable(ServerLevel level, Container backpack, ScavengerConfig cfg) {
        if (tablePos == null || !level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
            tablePos = findTable(level);
        }
        if (tablePos == null) {
            if (!tryPlaceCraftingTable(level, backpack, cfg)) {
                return false;
            }
        }
        if (tablePos == null) {
            return false;
        }
        return walkTo(tablePos);
    }

    private boolean tryPlaceCraftingTable(ServerLevel level, Container backpack, ScavengerConfig cfg) {
        tablePos = findTable(level);
        if (tablePos != null) {
            return true;
        }
        if (!cfg.placeCraftingTables) {
            return false;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        if (ScavengerCrafting.count(backpack, Items.CRAFTING_TABLE) == 0) {
            if (!ScavengerCrafting.canMakeTable(backpack)) {
                return false;
            }
            if (++ticks < CRAFT_TICKS) {
                return false;
            }
            ticks = 0;
            if (!ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_TABLE)) {
                return false;
            }
            mob.swing(InteractionHand.MAIN_HAND);
        }
        BlockPos spot = freeSpotBeside(level);
        if (spot == null) {
            return false;
        }
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.is(Items.CRAFTING_TABLE)) {
                backpack.removeItem(i, 1);
                level.setBlock(spot, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
                mob.swing(InteractionHand.MAIN_HAND);
                tablePos = spot;
                return true;
            }
        }
        return false;
    }

    private BlockPos freeSpotBeside(Level level) {
        BlockPos origin = mob.blockPosition();
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos pos = origin.relative(dir);
            BlockState state = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());
            if (state.canBeReplaced() && below.isSolidRender(level, pos.below())) {
                return pos.immutable();
            }
        }
        return null;
    }

    private boolean walkTo(BlockPos pos) {
        double dist = mob.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (dist <= REACH_SQR) {
            approachTicks = 0;
            mob.getNavigation().stop();
            return true;
        }
        approachTicks++;
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
        }
        mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return false;
    }

    private BlockPos findTable(Level level) {
        BlockPos origin = mob.blockPosition();
        int r = (int) ScavengerConfig.get().craftingTableSearchRadius;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                        continue;
                    }
                    double dist = pos.distSqr(origin);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }
}
