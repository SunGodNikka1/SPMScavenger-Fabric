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
    /**
     * The one mob whose events are recorded.
     *
     * <h2>Why attribution cannot be optional</h2>
     *
     * Every hook was global static state with no notion of <i>whose</i> event it was, so in a world
     * with several autonomous PlayerMobs the recorder could interleave
     *
     * <pre>
     * mob A: GATHER PUBLISHED     mob A: GATHER YIELDING
     * mob B: ROUTE INFEASIBLE     mob B: PLAN #1
     * </pre>
     *
     * and present it as one causal stream. The step-7A run was almost certainly single-subject — the
     * exact 320→298 ledger says so — but a proof harness whose causality rests on "probably only one
     * mob was doing this" is not a proof harness. Events from any other mob are now dropped rather
     * than attributed.
     *
     * <p>{@code null} means unbound: nothing records, so a forgotten bind fails closed rather than
     * silently recording everything again.
     */
    private static volatile java.util.UUID subject;
    private static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());
    private static int selections;
    private static int teSelections;
    private static int revalidations;
    private static int transactions;
    private static int episodes;
    private static int dropped;
    private static int logsBefore;
    private static String lastGateOutcome = "";
    private static String lastRouteOutcome = "";
    /** Board size at first sight, per villager. Bounded by the fixture's two merchants. */
    private static final java.util.Map<java.util.UUID, Integer> BOARD_SIZE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private TradeRuntimeObserver() {
    }

    /** Bind first, then record. An unbound observer stays silent. */
    public static void watch(java.util.UUID mobId) {
        subject = mobId;
    }

    public static java.util.UUID subject() {
        return subject;
    }

    public static void setRecording(boolean on) {
        recording = on;
        if (!on) {
            subject = null;
        }
    }

    /** The single gate every hook passes through: recording, bound, and the right mob. */
    private static boolean records(java.util.UUID mobId) {
        return recording && subject != null && subject.equals(mobId);
    }

    public static boolean recording() {
        return recording;
    }

    public static void reset() {
        EVENTS.clear();
        selections = 0;
        teSelections = 0;
        revalidations = 0;
        transactions = 0;
        BOARD_SIZE.clear();
        lastGateOutcome = "";
        lastRouteOutcome = "";
        episodes = 0;
        dropped = 0;
    }

    public static List<String> events() {
        return List.copyOf(EVENTS);
    }

    public static String summary() {
        return "plans=" + selections + " (TE " + teSelections + ")  revals=" + revalidations
                + "  trades=" + transactions + "  episodes=" + episodes
                + (dropped > 0 ? "  dropped=" + dropped : "");
    }

    private static void add(String line) {
        if (EVENTS.size() >= MAX_EVENTS) {
            dropped++;
            return;
        }
        EVENTS.add(line);
    }

    // ------------------------------------------------------------------ hooks

    /**
     * Compact by design.
     *
     * <p>Run #1 printed a full board dump and a {@code Requote[...]} toString on every line, which is
     * unreadable in Minecraft chat — the readout existed but could not be read. Each event is now one
     * short line, and the invariants that were being dumped in full are carried as <b>flags</b>:
     * {@code tp!} appears only if a trading player was present, {@code board!} only if the villager's
     * real board changed size. Silence is the passing case.
     */
    public static void selected(java.util.UUID mobId, Object sourceKey, Villager villager,
            Object ref, ItemStack costA, ItemStack result) {
        if (!records(mobId)) {
            return;
        }
        selections++;
        if (isTradeEverything(sourceKey)) {
            teSelections++;
        }
        add("PLAN #" + selections + " " + shortSource(sourceKey) + " " + profession(villager)
                + "  Q1: " + describe(costA) + " -> " + describe(result));
    }

    public static void revalidated(java.util.UUID mobId, Object sourceKey, Villager villager,
            Object ref, Optional<MerchantOffer> resolved) {
        if (!records(mobId)) {
            return;
        }
        revalidations++;
        add("REVAL #" + revalidations + resolved
                .map(o -> "  Q2: " + describe(o.getCostA()) + " -> " + describe(o.assemble()) + "  OK")
                .orElse("  Q2 MISMATCH/GONE -> REJECTED")
                + flags(villager));
    }

    public static void transacted(java.util.UUID mobId, Object sourceKey, Villager villager,
            Object result, Container backpack, int emeraldsBefore, int pickaxesBefore) {
        if (!records(mobId)) {
            return;
        }
        transactions++;
        add("TRADE #" + transactions + " " + result
                + "  logs " + logsBefore + "->" + count(backpack, Items.OAK_LOG)
                + "  em " + emeraldsBefore + "->" + count(backpack, Items.EMERALD)
                + "  pick " + pickaxesBefore + "->" + count(backpack, Items.IRON_PICKAXE)
                + flags(villager));
    }

    /** Called by the goal just before a transaction, so the log delta is exact rather than sampled. */
    public static void aboutToTrade(java.util.UUID mobId, Container backpack) {
        // Subject-bound too: a global snapshot taken from another mob's backpack would make the
        // next TRADE line's log delta describe an inventory nobody traded from.
        if (!records(mobId)) {
            return;
        }
        logsBefore = count(backpack, Items.OAK_LOG);
    }

    public static void episode(java.util.UUID mobId) {
        if (!records(mobId)) {
            return;
        }
        episodes++;
        add("EPISODE #" + episodes);
    }

    /**
     * Step-7B diagnostic — <b>why the gather route did or did not publish exhaustion.</b>
     *
     * <p>The readout could say {@code plans=0 NO_CANDIDATES} and nothing about the reason. For iron,
     * {@code ExistingRouteFeasibility} deliberately answers UNKNOWN while raw iron is still wanted,
     * and only {@code RouteExhaustionEvidence} converts that into INFEASIBLE. Gather publishes it
     * solely when the sweep was full, {@code findTarget} came back null, the failure was
     * {@code NO_CANDIDATES_IN_RADIUS}, and the scan actually covered RAW_IRON — four conditions, and
     * a silent no from any one of them looks identical from outside.
     *
     * <p>Deduplicated: this is evaluated every time gather stops, and an unchanged answer repeated
     * two hundred times would bury everything else.
     */
    public static void gatherExhaustionGate(java.util.UUID mobId, String outcome) {
        if (!records(mobId) || outcome.equals(lastGateOutcome)) {
            return;
        }
        lastGateOutcome = outcome;
        add("GATHER  " + outcome);
    }

    /** Step-7B diagnostic — the tri-state trade is actually reading, and when it changes. */
    public static void routeFeasibility(java.util.UUID mobId, Object materialKey,
            boolean mayDisplace) {
        if (!records(mobId)) {
            return;
        }
        String line = "ROUTE   " + shortId(materialKey)
                + (mayDisplace ? "  INFEASIBLE -> trade may displace"
                        : "  UNKNOWN/FEASIBLE -> gather keeps ownership");
        if (line.equals(lastRouteOutcome)) {
            return;
        }
        lastRouteOutcome = line;
        add(line);
    }

    private static String shortId(Object key) {
        String text = String.valueOf(key);
        return text.startsWith("minecraft:") ? text.substring("minecraft:".length()) : text;
    }

    /** The fixture's own marker, so mutation timing is visible in the same stream. */
    public static void note(String line) {
        if (!recording) {
            return;
        }
        add(line);
    }

    public static int tradeEverythingSelections() {
        return teSelections;
    }

    /**
     * Only the failures are printed. A clean run says nothing, which is what makes a dirty one
     * impossible to miss in a wall of chat.
     */
    private static String flags(Villager villager) {
        if (villager == null) {
            return "";
        }
        StringBuilder flags = new StringBuilder();
        if (villager.getTradingPlayer() != null) {
            flags.append("  tp!");
        }
        int size = villager.getOffers().size();
        Integer baseline = BOARD_SIZE.putIfAbsent(villager.getUUID(), size);
        if (baseline != null && baseline != size) {
            flags.append("  board!").append(baseline).append("->").append(size);
        }
        return flags.toString();
    }

    private static boolean isTradeEverything(Object sourceKey) {
        return sourceKey != null && "TRADE_EVERYTHING".equals(sourceKey.toString());
    }

    private static String shortSource(Object sourceKey) {
        return isTradeEverything(sourceKey) ? "TE" : "VAN";
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
     * Full board text, for the on-demand report only.
     *
     * <p>Deliberately not {@code SyntheticOfferFactory.isSynthetic}: this class stays free of Trade
     * Everything, and a row appearing at all is what the claim is about — which the per-event
     * {@code board!} flag already catches by size.
     */
    public static String board(Villager villager) {
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
        // No "x", no namespace: every character costs readable width in a chat line.
        return stack == null || stack.isEmpty() ? "-"
                : stack.getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).getPath();
    }
}
