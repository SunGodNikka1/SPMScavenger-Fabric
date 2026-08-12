package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared campfire feasibility helpers for {@link CampfireGoal} and {@link RestExecutorAdmission}.
 */
final class RestCampfireFeasibility {

    static final int SCAN_INTERVAL = 100;
    static final int SCAN_PHASE_SALT = 37;
    static final int SEARCH_RADIUS = 16;
    static final double ARRIVED_SQR = 4.0;

    private RestCampfireFeasibility() {
    }

    static boolean carriesCampfire(Mob mob) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return false;
        }
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.is(Items.CAMPFIRE)) {
                return true;
            }
        }
        return false;
    }

    static BlockPos findCampfire(Level level, BlockPos origin) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
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

    static BlockPos spotBeside(Level level, BlockPos centre) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos pos = centre.offset(dx, 0, dz);
                BlockState below = level.getBlockState(pos.below());
                if (level.getBlockState(pos).isAir()
                        && level.getBlockState(pos.above()).isAir()
                        && below.isSolidRender(level, pos.below())) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    static boolean canPlaceCampfire(Level level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    static boolean featureEnabled() {
        ScavengerConfig cfg = ScavengerConfig.get();
        return cfg.enabled && cfg.campfire;
    }
}
