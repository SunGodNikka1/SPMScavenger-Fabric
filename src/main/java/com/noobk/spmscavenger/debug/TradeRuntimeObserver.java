package com.noobk.spmscavenger.debug;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <b>TEMPORARY STEP-7A RUNTIME SUPPORT — remove with the TE3 probe.</b>
 *
 * <h2>It records; it never decides</h2>
 *
 * Every method returns {@code void} and every one begins by checking {@link #recording}. There is no
 * return value the goal can branch on, no exception it can throw into a decision, and no state it
 * writes that production reads. That is deliberate and is the whole basis of the step-7A claim: the
 * mob's route must be produced by {@code TradeWithVillagerGoal}, not by a fixture nudging it.
 *
 * <p>Disabled by default, so an ordinary session pays one volatile read per decision point.
 *
 * <h2>Why hooks rather than an external tick watcher</h2>
 *
 * Which <i>source</i> produced the selected candidate, what the planned {@code OfferRef} was, and
 * whether Q2 matched Q1 are not observable from outside the goal. A tick watcher could see a mob
 * walking and emeralds appearing and would have to <i>infer</i> the route — which is exactly the
 * kind of inference this whole workstream has been removing.
 *
 * <h2>Bounded</h2>
 *
 * The log is capped at {@link #MAX_EVENTS} and cleared by {@code reset}. A debug recorder that grows
 * without limit for a whole session is the RET-1 shape this repository has shipped before.
 */
public final class TradeRuntimeObserver {

    private static final int MAX_EVENTS = 400;

    private static volatile boolean recording;
    private static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());
    private static int selections;
    private static int transactions;
    private static int episodes;
    private static int dropped;

    private TradeRuntimeObserver() {
    }

    public static void setRecording(boolean on) {
        recording = on;
    }

    public static boolean recording() {
        return recording;
    }

    public static void reset() {
        EVENTS.clear();
        selections = 0;
        transactions = 0;
        episodes = 0;
        dropped = 0;
    }

    public static List<String> events() {
        return List.copyOf(EVENTS);
    }

    public static String summary() {
        return "selections=" + selections + "  transactions=" + transactions
                + "  episodes=" + episodes + (dropped > 0 ? "  dropped=" + dropped : "");
    }

    private static void add(String line) {
        if (EVENTS.size() >= MAX_EVENTS) {
            dropped++;
            return;
        }
        EVENTS.add(line);
    }

    // ------------------------------------------------------------------ hooks

    /** A candidate was accepted for an attempt. Which source produced it is the headline fact. */
    public static void selected(Object sourceKey, Villager villager, Object ref, ItemStack costA,
            ItemStack result) {
        if (!recording) {
            return;
        }
        selections++;
        add("SELECT  source=" + sourceKey + "  villager=" + profession(villager)
                + "  ref=" + ref + "  quote=" + describe(costA) + " -> " + describe(result));
    }

    /**
     * The execution boundary re-resolved the planned offer.
     *
     * <p>For a re-quoting source this is Q2, and the board fingerprint recorded beside it is what
     * shows no synthetic row was inserted while the quote was produced.
     */
    public static void revalidated(Object sourceKey, Villager villager, Object ref,
            Optional<MerchantOffer> resolved) {
        if (!recording) {
            return;
        }
        add("REQUOTE source=" + sourceKey + "  villager=" + profession(villager)
                + "  ref=" + ref
                + "  Q2=" + resolved.map(o -> describe(o.getCostA()) + " -> " + describe(o.assemble()))
                        .orElse("<none: Q1/Q2 mismatch or gone>")
                + "  tradingPlayer=" + (villager == null ? "?" : villager.getTradingPlayer())
                + "  board=" + board(villager));
    }

    /** The transaction outcome, with the backpack either side of it. */
    public static void transacted(Object sourceKey, Villager villager, Object result,
            Container backpack, int emeraldsBefore, int pickaxesBefore) {
        if (!recording) {
            return;
        }
        transactions++;
        add("TRADE   source=" + sourceKey + "  villager=" + profession(villager)
                + "  result=" + result
                + "  emeralds " + emeraldsBefore + " -> " + count(backpack, Items.EMERALD)
                + "  iron_pickaxe " + pickaxesBefore + " -> " + count(backpack, Items.IRON_PICKAXE)
                + "  tradingPlayer=" + (villager == null ? "?" : villager.getTradingPlayer())
                + "  board=" + board(villager));
    }

    public static void episode() {
        if (!recording) {
            return;
        }
        episodes++;
        add("EPISODE recorded");
    }

    // ------------------------------------------------------------------ helpers

    public static int count(Container container, net.minecraft.world.item.Item item) {
        if (container == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Board size and contents, so an inserted row is visible without naming an upstream type.
     *
     * <p>Deliberately not {@code SyntheticOfferFactory.isSynthetic}: this class stays free of Trade
     * Everything, and a row appearing at all is what the claim is about.
     */
    private static String board(Villager villager) {
        if (villager == null) {
            return "?";
        }
        MerchantOffers offers = villager.getOffers();
        StringBuilder out = new StringBuilder().append(offers.size()).append('[');
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            out.append(i == 0 ? "" : " | ").append(describe(offer.getCostA())).append("->")
                    .append(describe(offer.assemble()));
        }
        return out.append(']').toString();
    }

    private static String profession(Villager villager) {
        return villager == null ? "?"
                : net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                        .getKey(villager.getVillagerData().getProfession()).getPath();
    }

    private static String describe(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "-"
                : stack.getCount() + "x " + net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).getPath();
    }
}
