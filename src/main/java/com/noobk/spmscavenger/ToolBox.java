package com.noobk.spmscavenger;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finding and holding the right tool for a block.
 *
 * <h2>Why this exists at all</h2>
 *
 * A tool sitting in the backpack does nothing. {@code ItemStack#getDestroySpeed} and
 * {@code isCorrectToolForDrops} are asked of the stack the mob is <b>holding</b>, so v1.0–1.2 gated
 * coal ore on {@code getMainHandItem()} and a mob that owned a pickaxe still could not mine. The
 * scavenging loop dead-ended there: no pickaxe in hand meant no coal, no coal meant no torches, and
 * the mob chopped wood forever with nothing to show for it.
 *
 * <p>So the mob has to actually <em>draw</em> the tool. That is also the visible half — a PlayerMob
 * swapping to a pickaxe before it starts mining reads as intent in a way a backpack transaction
 * never does.
 *
 * <h2>Swapping is conservative</h2>
 *
 * Whatever was in the main hand goes back into the backpack, and the swap is <b>refused outright if
 * there is no room</b> rather than dropping the mob's weapon on the floor. Social Player Mobs owns
 * main-hand selection during combat — {@code equipBestWeaponForTarget} redraws from the same
 * backpack — so a mob interrupted mid-mine re-arms itself normally. The two systems agree because
 * they use the same storage; this one never destroys anything the other might want.
 */
public final class ToolBox {

    /** Below this a "tool" is no better than a fist and not worth a swap. */
    private static final float USEFUL_SPEED = 1.5F;

    private ToolBox() {
    }

    /** True if the mob can mine {@code state} properly with something it already owns. */
    public static boolean ownsToolFor(Mob mob, BlockState state) {
        return bestSpeed(mob, state) > USEFUL_SPEED;
    }

    /** The best destroy speed available from either hand or the backpack. */
    public static float bestSpeed(Mob mob, BlockState state) {
        float best = usefulSpeed(mob.getMainHandItem(), state);
        best = Math.max(best, usefulSpeed(mob.getOffhandItem(), state));
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack != null) {
            for (int i = 0; i < backpack.getContainerSize(); i++) {
                best = Math.max(best, usefulSpeed(backpack.getItem(i), state));
            }
        }
        return best;
    }

    /**
     * A stack's speed against a block, but only when it is the <em>correct</em> tool. A sword mines
     * cobwebs quickly and would otherwise look like a mining tool; requiring correctness also keeps
     * the mob from picking a weapon it is about to need.
     */
    private static float usefulSpeed(ItemStack stack, BlockState state) {
        if (stack.isEmpty()
                || (stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage())
                || !stack.isCorrectToolForDrops(state)) {
            return 1.0F;
        }
        return stack.getDestroySpeed(state);
    }

    /**
     * Puts the best owned tool for {@code state} in the main hand, returning true if the mob is now
     * holding something better than its fist.
     *
     * <p>A no-op when the held item is already the best option. An off-hand winner swaps hands
     * losslessly; a backpack winner still requires room for the prior main-hand item.
     */
    public static boolean equipFor(Mob mob, BlockState state) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return usefulSpeed(mob.getMainHandItem(), state) > USEFUL_SPEED;
        }
        float held = usefulSpeed(mob.getMainHandItem(), state);
        float offHand = usefulSpeed(mob.getOffhandItem(), state);

        int bestSlot = -1;
        float bestSpeed = Math.max(held, offHand);
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            float speed = usefulSpeed(backpack.getItem(i), state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) {
            if (offHand > held) {
                // A looted tool may be equipped in the off hand by the host mod. Swap hands
                // directly so mining does not depend on spare backpack capacity.
                ItemStack oldMain = mob.getMainHandItem().copy();
                ItemStack newMain = mob.getOffhandItem().copy();
                mob.setItemSlot(EquipmentSlot.MAINHAND, newMain);
                mob.setItemSlot(EquipmentSlot.OFFHAND, oldMain);
                return offHand > USEFUL_SPEED;
            }
            return held > USEFUL_SPEED; // already holding the best there is
        }

        ItemStack tool = backpack.getItem(bestSlot).copy();
        ItemStack heldStack = mob.getMainHandItem().copy();
        backpack.setItem(bestSlot, ItemStack.EMPTY);

        if (!heldStack.isEmpty() && !ScavengerCrafting.give(backpack, heldStack)) {
            backpack.setItem(bestSlot, tool);   // put it back; never drop the mob's weapon
            return held > USEFUL_SPEED;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, tool);
        return bestSpeed > USEFUL_SPEED;
    }
}
