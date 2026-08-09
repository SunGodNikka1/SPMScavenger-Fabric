package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ToolTierPolicy;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Crafting: torches, and the tools that make torches possible.
 *
 * <h2>Why it places a crafting table</h2>
 *
 * v1.0 drew a line at 2x2 recipes — anything a player could make in their inventory grid, nothing
 * more. Tools are 3x3, so that line stopped the chain dead: no pickaxe meant no coal, no coal meant
 * no torches, and the mob gathered wood forever with nothing to show for it.
 *
 * <p><b>Placing a table is exactly how a real player crosses that line</b>, so the rule is now "craft
 * the way a player does" rather than "craft only what fits in a pocket". The table is crafted 2x2
 * from four planks, set down on the ground, walked to, and used. It is left standing afterwards —
 * that is the point. A workbench appearing in a clearing is the first visible evidence that these
 * mobs are doing something.
 *
 * <h2>What the mob looks like doing this</h2>
 *
 * <ol>
 *   <li>Stops where it is and works, unless the step needs a table.</li>
 *   <li>If it needs one: looks for a table within range, walks to it, and crafts there.</li>
 *   <li>If there is none and it has four planks, it makes one, places it in a clear spot beside
 *       itself, then walks up to it.</li>
 *   <li>Each craft takes about a second and swings the mob's arm, so the sequence reads as work
 *       rather than an inventory flicker.</li>
 * </ol>
 */
public class CraftTorchesGoal extends Goal {

    private final Mob mob;
    private final double speed;

    private ScavengerCrafting.Step step = ScavengerCrafting.Step.NOTHING;
    private BlockPos tablePos;
    private int ticks;
    private int approachTicks;

    private static final int CRAFT_TICKS = 20;
    /** Longer than other goals — village tables and mob-placed benches can be a fair walk away. */
    private static final int MAX_APPROACH_TICKS = 200;
    private static final double TABLE_REACH_SQR = 6.0;

    public CraftTorchesGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.gatherResources || mob.getTarget() != null) {
            return false;
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return false;
        }
        step = chooseStep(cfg, backpack);
        return step != ScavengerCrafting.Step.NOTHING;
    }

    @Override
    public boolean canContinueToUse() {
        return step != ScavengerCrafting.Step.NOTHING
                && mob.getTarget() == null
                && approachTicks < MAX_APPROACH_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        approachTicks = 0;
    }

    @Override
    public void stop() {
        step = ScavengerCrafting.Step.NOTHING;
        tablePos = null;
        ticks = 0;
        approachTicks = 0;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            stop();
            return;
        }
        if (ScavengerCrafting.needsTable(step)) {
            if (!atTable()) {
                return; // walking there, or making one
            }
        }
        if (++ticks < CRAFT_TICKS) {
            return;
        }
        ticks = 0;

        // Re-read rather than trusting the step chosen earlier: the backpack may have changed.
        ScavengerCrafting.Step now = chooseStep(ScavengerConfig.get(), backpack);
        if (now == ScavengerCrafting.Step.NOTHING || !ScavengerCrafting.apply(backpack, now, mob)) {
            stop();
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        step = chooseStep(ScavengerConfig.get(), backpack);
    }

    /**
     * Torches and tools if the mob still needs them, with an explicit stop once it has enough
     * torches — without that, "make torches" has no end condition.
     */
    private ScavengerCrafting.Step chooseStep(ScavengerConfig cfg, Container backpack) {
        boolean stocked = ScavengerCrafting.count(backpack, Items.TORCH) >= cfg.torchStockTarget;
        boolean needPickaxe = ToolTierPolicy.needsPickUpgrade(backpack, mob.getMainHandItem(), cfg);
        boolean needAxe = ToolTierPolicy.needsAxeUpgrade(backpack, mob.getMainHandItem(), cfg);

        if (stocked && !needPickaxe && !needAxe) {
            // Fully equipped: spend the surplus on a campfire to sit around, never before.
            if (cfg.campfire
                    && ScavengerCrafting.count(backpack, Items.CAMPFIRE) == 0
                    && ScavengerCrafting.canMakeCampfire(backpack)) {
                return ScavengerCrafting.Step.MAKE_CAMPFIRE;
            }
            return ScavengerCrafting.Step.NOTHING;
        }
        return ScavengerCrafting.nextStep(backpack, cfg, mob.getMainHandItem());
    }

    // ---- Crafting table ---------------------------------------------------

    /** True once the mob is standing at a usable table. Otherwise it is walking to or making one. */
    private boolean atTable() {
        Level level = mob.level();
        if (tablePos == null || !level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
            tablePos = findTable(level);
        }
        if (tablePos == null) {
            placeTable(level);
            return false;
        }
        if (mob.blockPosition().distSqr(tablePos) <= TABLE_REACH_SQR) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(
                    tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);
            return true;
        }
        approachTicks++;
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(
                    tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, speed);
        }
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

    /**
     * Crafts a table if needed and sets it down beside the mob.
     *
     * <p>Placement is deliberately timid: an empty, replaceable block on solid ground directly
     * adjacent to the mob, and only with {@code mobGriefing} on. It will not carve out a space, and
     * it never replaces anything that is already there.
     */
    private void placeTable(Level level) {
        // Re-scan before spending planks or pulling a table from the pack — another mob (or the
        // player) may have placed one since the last tick, or the first scan was from too close.
        BlockPos existing = findTable(level);
        if (existing != null) {
            tablePos = existing;
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.placeCraftingTables) {
            stop();
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            stop();
            return;
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            stop();
            return;
        }
        if (ScavengerCrafting.count(backpack, Items.CRAFTING_TABLE) == 0) {
            if (!ScavengerCrafting.canMakeTable(backpack)) {
                stop();   // not enough planks yet; gathering will fix that
                return;
            }
            if (++ticks < CRAFT_TICKS) {
                return;
            }
            ticks = 0;
            if (!ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_TABLE)) {
                stop();
            }
            mob.swing(InteractionHand.MAIN_HAND);
            return;
        }
        BlockPos spot = freeSpotBeside(level);
        if (spot == null) {
            stop();   // nowhere sensible; try again elsewhere
            return;
        }
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.is(Items.CRAFTING_TABLE)) {
                backpack.removeItem(i, 1);
                level.setBlock(spot, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
                mob.swing(InteractionHand.MAIN_HAND);
                tablePos = spot;
                return;
            }
        }
    }

    /** An adjacent air block on solid ground — nothing is ever overwritten. */
    private BlockPos freeSpotBeside(Level level) {
        BlockPos origin = mob.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos pos = origin.offset(dx, 0, dz);
                BlockState below = level.getBlockState(pos.below());
                if (level.getBlockState(pos).isAir() && below.isSolidRender(level, pos.below())) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }
}
