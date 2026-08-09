package com.noobk.spmscavenger;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * Test double mirroring vanilla furnace face layout without hardcoded production access.
 * Slots: 0 input (UP), 1 fuel (SIDES), 2 result (DOWN).
 */
final class FakeFurnaceContainer implements WorldlyContainer {

    private static final int[] UP = {0};
    private static final int[] DOWN = {2};
    private static final int[] SIDES = {1};

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private final EnumSet<Direction> acceptedFuelFaces;

    FakeFurnaceContainer(Direction... acceptedFuelFaces) {
        this.acceptedFuelFaces = acceptedFuelFaces.length == 0
                ? EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
                : EnumSet.copyOf(Arrays.asList(acceptedFuelFaces));
    }

    static FakeFurnaceContainer rejectingAllFuelFaces() {
        FakeFurnaceContainer furnace = new FakeFurnaceContainer();
        furnace.acceptedFuelFaces.clear();
        return furnace;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return DOWN;
        }
        if (side == Direction.UP) {
            return UP;
        }
        return SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        if (index == 2) {
            return false;
        }
        if (index == 1) {
            return direction != null && acceptedFuelFaces.contains(direction);
        }
        return direction == Direction.UP || direction == null;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == 2 && direction == Direction.DOWN;
    }

    @Override
    public int getContainerSize() {
        return 3;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }
}
