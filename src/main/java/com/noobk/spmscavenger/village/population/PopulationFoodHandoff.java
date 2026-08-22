package com.noobk.spmscavenger.village.population;

import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Dedicated population-food toss primitive (G0-5) — mechanics only, no greet semantics.
 */
public final class PopulationFoodHandoff {

    public enum CommitOutcome {
        ABORT,
        COMMITTED
    }

    public record CommitResult(
            CommitOutcome outcome,
            int villagerFoodValue,
            int recipientFoodPointsBefore) {}

    private PopulationFoodHandoff() {}

    public static CommitResult commitKernel(
            ServerLevel level,
            Mob mob,
            Villager villager,
            Container backpack,
            Item item,
            int count,
            boolean mobGriefing) {
        if (level == null || mob == null || villager == null || backpack == null || item == null || count <= 0) {
            return abort();
        }
        if (!mobGriefing) {
            return abort();
        }
        int before = VillagerFoodInventory.inventoryFoodPoints(villager);
        ItemStack template = new ItemStack(item);
        int removed = ContainerMerge.remove(backpack, template, count);
        if (removed <= 0) {
            return abort();
        }
        int foodValue = removed * VillagerFoodInventory.foodPointsPerItem(item);
        ItemStack tossed = new ItemStack(item, removed);
        spawnToward(level, mob, villager, tossed);
        return new CommitResult(CommitOutcome.COMMITTED, foodValue, before);
    }

    public static boolean observeDeliveryAck(Villager villager, int foodPointsBefore, int committedFoodValue) {
        if (villager == null || committedFoodValue <= 0) {
            return false;
        }
        return VillagerFoodInventory.inventoryFoodPoints(villager) >= foodPointsBefore + committedFoodValue;
    }

    private static CommitResult abort() {
        return new CommitResult(CommitOutcome.ABORT, 0, 0);
    }

    private static void spawnToward(ServerLevel level, Mob mob, Villager villager, ItemStack stack) {
        double fromX = mob.getX();
        double fromY = mob.getY() + mob.getEyeHeight() * 0.5;
        double fromZ = mob.getZ();
        ItemEntity thrown = new ItemEntity(level, fromX, fromY, fromZ, stack);
        Vec3 delta = villager.position()
                .subtract(fromX, fromY, fromZ)
                .normalize()
                .scale(0.3)
                .add(0.0, 0.2, 0.0);
        thrown.setDeltaMovement(delta);
        thrown.setPickUpDelay(PopulationFoodTuning.TOSS_PICK_UP_DELAY);
        thrown.setThrower(mob);
        level.addFreshEntity(thrown);
    }
}
