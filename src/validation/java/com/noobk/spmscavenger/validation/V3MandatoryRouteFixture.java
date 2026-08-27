package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Fixture-only inventory frontier for the two Task-59 mandatory-ownership rows. */
final class V3MandatoryRouteFixture {

    record Result(boolean prepared, String reason) {
    }

    private V3MandatoryRouteFixture() {
    }

    static Result prepare(V3CampaignScenario scenario, Mob subject) {
        if (!scenario.requiresMandatoryRoute()) {
            return new Result(true, "not required by " + scenario.id());
        }
        Container backpack = PlayerMobs.backpack(subject);
        if (backpack == null || backpack.getContainerSize() < 4) {
            return new Result(false, "PlayerMob backpack unavailable or smaller than four slots");
        }

        // Declared pre-window fixture input only. Production policies remain the sole owners of
        // demand, admission, claim publication, target selection, and execution.
        backpack.clearContent();
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.TORCH, 8));
        // Isolate the pick frontier even when the configured axe target is DIAMOND.
        backpack.setItem(3, new ItemStack(Items.DIAMOND_AXE));
        subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        subject.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        return new Result(true,
                "stone_pickaxe + sticks=2 + torches=8 + diamond_axe; iron/raw_iron absent");
    }
}
