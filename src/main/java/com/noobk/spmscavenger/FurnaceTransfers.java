package com.noobk.spmscavenger;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Atomic backpack ↔ furnace transfers through {@link WorldlyContainer} faces (D-FSM-008 / D-FSM-009).
 *
 * <p>Insert and extract are separate transactions. Neither invents stacks on failure.
 */
public final class FurnaceTransfers {

    public static final Direction INPUT_FACE = Direction.UP;
    public static final Direction OUTPUT_FACE = Direction.DOWN;
    private static final Direction[] HORIZONTAL_FUEL_FACES = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private FurnaceTransfers() {
    }

    /**
     * Reserve backpack space, remove input+fuel from backpack, place through faces.
     * Rolls back both inventories on any failure.
     */
    public static boolean tryInsert(
            Container backpack,
            WorldlyContainer furnace,
            ItemStack input,
            ItemStack fuel,
            ItemStack expectedOutput) {
        if (input.isEmpty() || fuel.isEmpty() || expectedOutput.isEmpty()) {
            return false;
        }
        if (!ScavengerCrafting.canGive(backpack, expectedOutput)) {
            return false;
        }
        if (!isEmptyForNewJob(furnace)) {
            return false;
        }

        ItemStack[] backpackSnap = snapshot(backpack);
        ItemStack[] furnaceSnap = snapshot(furnace);

        ItemStack inputTaken = takeMatching(backpack, input);
        ItemStack fuelTaken = takeMatching(backpack, fuel);
        if (inputTaken.isEmpty() || fuelTaken.isEmpty()) {
            restore(backpack, backpackSnap);
            restore(furnace, furnaceSnap);
            return false;
        }

        if (!placeThroughFace(furnace, INPUT_FACE, inputTaken)) {
            restore(backpack, backpackSnap);
            restore(furnace, furnaceSnap);
            return false;
        }

        Optional<Direction> fuelFace = selectCompleteFuelFace(furnace, fuelTaken);
        if (fuelFace.isEmpty() || !placeThroughFace(furnace, fuelFace.get(), fuelTaken)) {
            restore(backpack, backpackSnap);
            restore(furnace, furnaceSnap);
            return false;
        }
        return true;
    }

    /**
     * Extract only output matching {@code expected} via the DOWN face, then give into the backpack.
     * Rolls back on failure. Does not touch non-matching stacks (D-FSM-004).
     */
    public static Optional<ItemStack> tryExtract(
            Container backpack, WorldlyContainer furnace, StackFingerprint expected, int maxCount) {
        if (maxCount <= 0) {
            return Optional.empty();
        }
        ItemStack[] backpackSnap = snapshot(backpack);
        ItemStack[] furnaceSnap = snapshot(furnace);

        ItemStack taken = ItemStack.EMPTY;
        for (int slot : furnace.getSlotsForFace(OUTPUT_FACE)) {
            ItemStack stack = furnace.getItem(slot);
            if (stack.isEmpty() || !expected.matchesItem(stack)) {
                continue;
            }
            if (!furnace.canTakeItemThroughFace(slot, stack, OUTPUT_FACE)) {
                continue;
            }
            int want = Math.min(maxCount - taken.getCount(), stack.getCount());
            if (want <= 0) {
                break;
            }
            ItemStack piece = furnace.removeItem(slot, want);
            if (taken.isEmpty()) {
                taken = piece;
            } else {
                taken.grow(piece.getCount());
            }
            if (taken.getCount() >= maxCount) {
                break;
            }
        }

        if (taken.isEmpty() || taken.getCount() > maxCount) {
            restore(backpack, backpackSnap);
            restore(furnace, furnaceSnap);
            return Optional.empty();
        }
        if (!expected.matchesItem(taken)) {
            restore(backpack, backpackSnap);
            restore(furnace, furnaceSnap);
            return Optional.empty();
        }
        ItemStack toGive = taken.copy();
        if (!ScavengerCrafting.canGive(backpack, toGive) || !ScavengerCrafting.give(backpack, toGive)) {
            restore(backpack, backpackSnap);
            restore(furnace, furnaceSnap);
            return Optional.empty();
        }
        return Optional.of(taken);
    }

    /** True when every slot is empty — required before starting a new scavenger job. */
    public static boolean isEmptyForNewJob(Container furnace) {
        return FurnaceStations.isContainerEmpty(furnace);
    }

    /**
     * Select one horizontal face that can accept the complete fuel stack without mutating the
     * furnace. This avoids splitting one transfer across multiple side contracts.
     */
    static Optional<Direction> selectCompleteFuelFace(WorldlyContainer furnace, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (Direction face : HORIZONTAL_FUEL_FACES) {
            if (canPlaceCompletelyThroughFace(furnace, face, stack)) {
                return Optional.of(face);
            }
        }
        return Optional.empty();
    }

    private static boolean canPlaceCompletelyThroughFace(
            WorldlyContainer furnace, Direction face, ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot : furnace.getSlotsForFace(face)) {
            if (!furnace.canPlaceItemThroughFace(slot, stack, face)) {
                continue;
            }
            ItemStack existing = furnace.getItem(slot);
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean placeThroughFace(WorldlyContainer furnace, Direction face, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int slot : furnace.getSlotsForFace(face)) {
            if (!furnace.canPlaceItemThroughFace(slot, stack, face)) {
                continue;
            }
            ItemStack existing = furnace.getItem(slot);
            if (existing.isEmpty()) {
                furnace.setItem(slot, stack.copy());
                stack.setCount(0);
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int room = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
                if (room > 0) {
                    existing.grow(room);
                    stack.shrink(room);
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    static ItemStack takeMatching(Container backpack, ItemStack want) {
        ItemStack taken = ItemStack.EMPTY;
        int left = want.getCount();
        for (int i = 0; i < backpack.getContainerSize() && left > 0; i++) {
            ItemStack slot = backpack.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, want)) {
                continue;
            }
            ItemStack piece = backpack.removeItem(i, left);
            if (taken.isEmpty()) {
                taken = piece;
            } else {
                taken.grow(piece.getCount());
            }
            left -= piece.getCount();
        }
        return left == 0 ? taken : ItemStack.EMPTY;
    }

    static ItemStack[] snapshot(Container c) {
        ItemStack[] snap = new ItemStack[c.getContainerSize()];
        for (int i = 0; i < snap.length; i++) {
            snap[i] = c.getItem(i).copy();
        }
        return snap;
    }

    static void restore(Container c, ItemStack[] snap) {
        for (int i = 0; i < snap.length; i++) {
            c.setItem(i, snap[i].copy());
        }
    }
}
