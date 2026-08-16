package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * V2-A — staged inventory arithmetic, including the case {@code MerchantOffer#take} cannot express.
 */
class TradeTransactionTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SimpleContainer backpack(ItemStack... contents) {
        SimpleContainer container = new SimpleContainer(8);
        for (int i = 0; i < contents.length; i++) {
            container.setItem(i, contents[i]);
        }
        return container;
    }

    /**
     * The finding that motivated this class: a 20-wheat cost held as 16 + 4.
     *
     * <p>{@code take(a, b)} shrinks only the two stacks passed to it, so it can never pay this. An
     * implementation built on {@code take} compiles, passes every single-slot test, and silently
     * under-pays here.
     */
    @Test
    void mustHappen_costIsDebitedAcrossMultipleSlots() {
        SimpleContainer backpack = backpack(
                new ItemStack(Items.WHEAT, 16), new ItemStack(Items.WHEAT, 4));
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertTrue(TradeTransaction.debit(staged, new ItemStack(Items.WHEAT, 20)));
        assertEquals(0, TradeTransaction.countMatching(staged, new ItemStack(Items.WHEAT)));

        TradeTransaction.commit(backpack, staged);
        assertTrue(backpack.getItem(0).isEmpty());
        assertTrue(backpack.getItem(1).isEmpty());
    }

    @Test
    void mustHappen_partialSlotIsLeftWithTheRemainder() {
        SimpleContainer backpack = backpack(
                new ItemStack(Items.WHEAT, 16), new ItemStack(Items.WHEAT, 16));
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertTrue(TradeTransaction.debit(staged, new ItemStack(Items.WHEAT, 20)));
        assertEquals(12, TradeTransaction.countMatching(staged, new ItemStack(Items.WHEAT)));
    }

    /** A debit that cannot complete must not spend anything, even on the staged copy. */
    @Test
    void mustNotHappen_anUnaffordableDebitHalfSpends() {
        SimpleContainer backpack = backpack(
                new ItemStack(Items.WHEAT, 8), new ItemStack(Items.WHEAT, 5));
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertFalse(TradeTransaction.debit(staged, new ItemStack(Items.WHEAT, 20)));
        assertEquals(13, TradeTransaction.countMatching(staged, new ItemStack(Items.WHEAT)),
                "a failed debit must leave every staged slot untouched");
    }

    /** Staging is a copy: nothing reaches the real container until commit. */
    @Test
    void mustNotHappen_stagingMutatesTheRealContainer() {
        SimpleContainer backpack = backpack(new ItemStack(Items.WHEAT, 16));
        ItemStack[] staged = TradeTransaction.stage(backpack);

        TradeTransaction.debit(staged, new ItemStack(Items.WHEAT, 16));
        TradeTransaction.insert(staged, new ItemStack(Items.EMERALD, 3));

        assertEquals(16, backpack.getItem(0).getCount(), "still untouched before commit");
        assertTrue(backpack.getItem(1).isEmpty());
    }

    @Test
    void mustHappen_resultMergesIntoAPartialStackBeforeOpeningANewOne() {
        SimpleContainer backpack = backpack(new ItemStack(Items.EMERALD, 60));
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertTrue(TradeTransaction.insert(staged, new ItemStack(Items.EMERALD, 8)));
        assertEquals(64, staged[0].getCount(), "fills the partial stack to max first");
        assertEquals(4, staged[1].getCount(), "overflow opens exactly one new slot");
    }

    @Test
    void mustNotHappen_insertionExceedsMaxStackSize() {
        SimpleContainer backpack = backpack();
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertTrue(TradeTransaction.insert(staged, new ItemStack(Items.EMERALD, 70)));
        assertEquals(64, staged[0].getCount());
        assertEquals(6, staged[1].getCount());
    }

    /** A full backpack must refuse the output rather than void it. */
    @Test
    void mustNotHappen_outputIsVoidedWhenThereIsNoRoom() {
        SimpleContainer backpack = new SimpleContainer(2);
        backpack.setItem(0, new ItemStack(Items.STONE, 64));
        backpack.setItem(1, new ItemStack(Items.STONE, 64));
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertFalse(TradeTransaction.insert(staged, new ItemStack(Items.EMERALD, 1)));
    }

    @Test
    void mustHappen_anEmptyCostIsFree() {
        ItemStack[] staged = TradeTransaction.stage(backpack());
        assertTrue(TradeTransaction.debit(staged, ItemStack.EMPTY));
        assertTrue(TradeTransaction.debit(staged, null));
    }

    /** Different components are different items, so an enchanted book cannot pay for a plain one. */
    @Test
    void mustNotHappen_componentsAreIgnoredWhenMatching() {
        ItemStack enchanted = new ItemStack(Items.ENCHANTED_BOOK, 4);
        enchanted.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("special"));
        SimpleContainer backpack = backpack(enchanted);
        ItemStack[] staged = TradeTransaction.stage(backpack);

        assertFalse(TradeTransaction.debit(staged, new ItemStack(Items.ENCHANTED_BOOK, 4)),
                "a differently-componented stack must not satisfy the cost");
    }
}
