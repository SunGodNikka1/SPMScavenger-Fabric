package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.SellReserveModel;
import com.noobk.spmscavenger.village.trade.TradeFundingPlanner;
import com.noobk.spmscavenger.village.trade.TradePurchaseProjection;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
import games.brennan.tradeeverything.trade.OfferQuoter;
import games.brennan.tradeeverything.trade.RecipeValues;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * <b>TEMPORARY V2-TE P0-3 PROBE SUPPORT — remove with the probe.</b>
 *
 * <p>{@code /spmscavenger debug te3 index|quote|scan}. The <b>only</b> thing here that Trade
 * Everything touches is {@link RecipeValues#ensureIndexed} and {@link OfferQuoter#quote} — no
 * pricing is reimplemented, because an oracle that recreated TE's valuation would share the
 * assumptions of the thing it is measuring.
 *
 * <h2>Why {@code index} is a separate subcommand</h2>
 *
 * P0-1: {@code quote} has <b>zero</b> references to {@code MinecraftServer} or
 * {@code ensureIndexed} (confirmed from bytecode), so it runs happily against an empty index and
 * returns offers priced by the <i>fallback</i> economy. `scan` therefore refuses until the index has
 * been built in this session — a probe that measured the wrong economy would be worse than no probe.
 *
 * <h2>This answers reachability, not desirability</h2>
 *
 * It classifies what the market <i>could</i> offer against what Scavenger currently wants. It
 * implements no autonomous behaviour and never transacts.
 */
public final class Te3ProbeCommand {

    private static final double RADIUS = 24.0D;
    private static boolean indexed;
    private static long coldIndexNanos = -1L;
    private static long warmIndexNanos = -1L;

    private Te3ProbeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger").then(Commands.literal("debug")
                .then(Commands.literal("te3").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("index").executes(c -> index(c.getSource())))
                        .then(Commands.literal("p02").executes(c -> p02(c.getSource())))
                        .then(Commands.literal("mutate")
                                .then(Commands.literal("arm").executes(c -> {
                                    mutationArmed = true;
                                    mutationApplied = false;
                                    c.getSource().sendSuccess(() -> Component.literal(
                                            "[TE3] market mutation ARMED - it fires on the mob's "
                                                    + "FIRST Trade Everything plan, while it walks"),
                                            false);
                                    return 1;
                                }))
                                .then(Commands.literal("now")
                                        .executes(c -> applyMutation(c.getSource()))))
                        .then(Commands.literal("watch")
                                .then(Commands.literal("on").executes(c -> {
                                    TradeRuntimeObserver.reset();
                                    TradeRuntimeObserver.setRecording(true);
                                    c.getSource().sendSuccess(() -> Component.literal(
                                            "[TE3] observer RECORDING (passive - it reports "
                                                    + "decisions, it cannot make them)"), false);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(c -> {
                                    mutationArmed = false;
                                    TradeRuntimeObserver.setRecording(false);
                                    c.getSource().sendSuccess(() -> Component.literal(
                                            "[TE3] observer stopped"), false);
                                    return 1;
                                }))
                                .then(Commands.literal("report").executes(c -> {
                                    Mob watched = nearestScavenger(c.getSource());
                                    Container pack = watched == null
                                            ? null : PlayerMobs.backpack(watched);
                                    StringBuilder out = new StringBuilder(
                                            "[TE3] step-7A autonomous readout" + (char) 10);
                                    out.append("  ").append(TradeRuntimeObserver.summary())
                                            .append((char) 10);
                                    for (String line : TradeRuntimeObserver.events()) {
                                        out.append("  ").append(line).append((char) 10);
                                    }
                                    out.append("  NOW: emeralds=")
                                            .append(TradeRuntimeObserver.count(pack, Items.EMERALD))
                                            .append("  iron_pickaxe(pack)=")
                                            .append(TradeRuntimeObserver.count(
                                                    pack, Items.IRON_PICKAXE))
                                            .append("  mainHand=")
                                            .append(watched == null ? "?"
                                                    : describe(watched.getMainHandItem()))
                                            .append("  logs=")
                                            .append(TradeRuntimeObserver.count(pack, Items.OAK_LOG))
                                            .append((char) 10);
                                    c.getSource().sendSuccess(
                                            () -> Component.literal(out.toString()), false);
                                    return 1;
                                })))
                        .then(Commands.literal("fixture")
                                .executes(c -> fixture(c.getSource())))
                        .then(Commands.literal("parity")
                                .then(Commands.literal("snapshot")
                                        .executes(c -> paritySnapshot(c.getSource())))
                                .then(Commands.literal("arm")
                                        .executes(c -> parityArm(c.getSource())))
                                .then(Commands.literal("live")
                                        .executes(c -> parityLive(c.getSource())))
                                .then(Commands.literal("closed")
                                        .executes(c -> parityClosed(c.getSource()))))
                        .then(Commands.literal("seed")
                                .then(Commands.argument("scenario",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(c -> seed(c.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                        .getString(c, "scenario")))))
                        .then(Commands.literal("scan")
                                .executes(c -> scan(c.getSource(), null))
                                .then(Commands.argument("expectDemand",
                                                com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(c -> scan(c.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                        .getString(c, "expectDemand")))))
                        .then(Commands.literal("reset").executes(c -> {
                            indexed = false;
                            coldIndexNanos = warmIndexNanos = -1L;
                            paritySubject = null;
                            parityDirect = null;
                            parityBoard = null;
                            parityInput = ItemStack.EMPTY;
                            parityArmed = false;
                            parityCaptured = null;
                            parityCapturedBoard = null;
                            paritySessionSeen = false;
                            c.getSource().sendSuccess(() -> Component.literal(
                                    "[TE3] probe state reset. NOTE: TE memoizes its index on "
                                            + "(RecipeManager, config) identity and this does not "
                                            + "clear it - only the FIRST `index` after a fresh "
                                            + "launch is a genuine cold measurement."), false);
                            return 1;
                        })))));
    }

    /**
     * Seed the fixture backpack from Java, because the datapack cannot.
     *
     * <p><b>Run #1 finding:</b> {@code /item replace entity ... inventory.N} addresses vanilla
     * entity slots, which are <i>not</i> the {@code InventoryCarrier} backpack every Scavenger goal
     * reads. The scenarios appeared to seed inventory, the backpack stayed empty, and the probe
     * reported {@code A=B=C=D=E=0} from zero quote attempts.
     *
     * <p>So inventory ownership moves here — the same reason VR-T2's setup seeded from Java. The
     * datapack still owns villagers, professions, positions and progression.
     */
    private static int seed(CommandSourceStack source, String scenario) {
        Mob mob = nearestScavenger(source);
        Container backpack = mob == null ? null : PlayerMobs.backpack(mob);
        if (backpack == null) {
            source.sendFailure(Component.literal("[TE3] no te3-tagged PlayerMob with a backpack"));
            return 0;
        }
        backpack.clearContent();
        switch (scenario) {
            case "iron" -> {
                mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.STONE_PICKAXE));
                mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                        new ItemStack(Items.IRON_AXE));
                backpack.setItem(0, new ItemStack(Items.OAK_LOG, 48));
                backpack.setItem(1, new ItemStack(Items.STICK, 64));
                backpack.setItem(2, new ItemStack(Items.OAK_PLANKS, 32));
                backpack.setItem(3, new ItemStack(Items.TORCH, 16));
            }
            case "torch" -> {
                mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.IRON_PICKAXE));
                mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                        new ItemStack(Items.IRON_AXE));
                backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));
                backpack.setItem(1, new ItemStack(Items.OAK_LOG, 64));
            }
            case "funding" -> {
                if (backpack.getContainerSize() < FUNDING_SLOTS) {
                    source.sendFailure(Component.literal(
                            "[TE3] backpack has " + backpack.getContainerSize() + " slots, the "
                                    + "funding fixture needs " + FUNDING_SLOTS));
                    return 0;
                }
                mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.STONE_PICKAXE));
                mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                        new ItemStack(Items.IRON_AXE));
                seedFundingInventory(backpack);
            }
            case "protected" -> {
                mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.STONE_PICKAXE));
                backpack.setItem(0, new ItemStack(Items.DIAMOND, 8));
                backpack.setItem(1, new ItemStack(Items.IRON_INGOT, 12));
                backpack.setItem(2, new ItemStack(Items.WHEAT, 64));
                backpack.setItem(3, new ItemStack(Items.STICK, 2));
            }
            case "autonomous" -> {
                // Step 7A. The mob must EARN its emeralds, so none are seeded and no pickaxe is
                // given. Stone pickaxe + iron axe is the state that naturally raises the
                // iron_pickaxe_upgrade demand; torches keep the SURVIVAL charcoal demand off, since
                // it outranks PROGRESSION and would quietly turn this into a torch scenario.
                //
                // Six log stacks, torches, and ONE FREE SLOT. The free slot is not spare capacity -
                // it is where the first TE emerald lands, and without it the opening sale fails
                // NO_ROOM (the R12 lesson: value is not capacity).
                if (backpack.getContainerSize() < FUNDING_SLOTS) {
                    source.sendFailure(Component.literal("[TE3] backpack too small"));
                    return 0;
                }
                mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.STONE_PICKAXE));
                mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                        new ItemStack(Items.IRON_AXE));
                // FIVE log stacks, not six. Step-7A run #1 seeded six and the mob converted a
                // log to planks, filling its last slot - after which the first TE emerald had
                // nowhere to land. Value is not capacity, and the capacity has to survive the
                // mob's OWN crafting, not just the trade.
                for (int slot = 0; slot < 5; slot++) {
                    backpack.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
                }
                backpack.setItem(5, new ItemStack(Items.TORCH, 16));
                // Three sticks: the iron-pickaxe recipe wants two, so towardConsumerTool finds the
                // stick requirement already met and returns NOTHING instead of starting a craft
                // chain the mob has no room to finish. Reserved by SellReserveModel, so they are
                // never sold - they exist to keep the mob out of the crafting loop entirely.
                backpack.setItem(6, new ItemStack(Items.STICK, 3));
                // Slot 7 stays empty. It is where the first TE emerald lands.
            }
            case "detached" -> {
                // P0-2 needs one sellable stack and free slots for the emerald. Nothing else: the
                // witness is about the call path, not about demand selection.
                backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));
            }
            default -> {
                source.sendFailure(Component.literal(
                        "[TE3] unknown scenario - use iron | torch | protected | funding | detached | autonomous"));
                return 0;
            }
        }
        int filled = 0;
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            if (!backpack.getItem(i).isEmpty()) {
                filled++;
            }
        }
        final int count = filled;
        source.sendSuccess(() -> Component.literal(
                "[TE3] seeded '" + scenario + "' - " + count + " backpack stacks"), false);
        return 1;
    }

    static final int FUNDING_SLOTS = 8;

    /**
     * R12 — the capacity-safe B-witness inventory, as a <b>pure</b> function of the container so its
     * capacity safety is unit-testable without a world.
     *
     * <h2>Why not 484 logs and no emeralds</h2>
     *
     * The census arithmetic said "&ge;484 logs covers the worst-case price", and it does — as
     * <i>value</i>. It is wrong as <i>inventory</i>. The backpack has {@value #FUNDING_SLOTS} slots;
     * filling every one with logs leaves the first {@code 22 oak_log -> 1 emerald} transaction with
     * nowhere to put its emerald, and {@code VillagerTradeAdapter} debits a staged inventory and
     * requires the result to insert before it commits. The route would fail {@code NO_ROOM} on step
     * one of seventeen — a fixture that disproves nothing.
     *
     * <p>So the fixture keeps 6 log stacks (384 logs) and spends the 7th slot on a <b>5-emerald
     * stack the TE payout can merge into</b>, and the 8th on the torches that keep the SURVIVAL
     * charcoal demand off (SURVIVAL outranks PROGRESSION). Purchasing power is unchanged at 22:
     * {@code floor(383/22) = 17} TE uses plus the 5 already held.
     *
     * <p>The final BUY then empties the emerald stack, which is what frees the slot the iron pickaxe
     * arrives in. Capacity is not merely tolerated here; it is what makes the route close.
     */
    static void seedFundingInventory(Container backpack) {
        backpack.clearContent();
        for (int slot = 0; slot < 6; slot++) {
            backpack.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        }
        backpack.setItem(6, new ItemStack(Items.EMERALD, 5));
        backpack.setItem(7, new ItemStack(Items.TORCH, 16));
    }

    /**
     * Raw capacity facts, reported and never classified on.
     *
     * <p>{@code classify} has no insertion model and must not grow one: {@code TradeFundingPlanner}
     * is the production transaction contract, and a second one written for a probe would be a
     * private oracle agreeing with itself. Execution capacity stays <b>P0-2</b>. What this does is
     * report the two facts that decide whether a seeded fixture can physically run, so a
     * {@code B_FUNDING} result is never quietly read as "and it would execute".
     */
    static boolean hasEmeraldMergeRoom(Container backpack) {
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack stack = backpack.getItem(slot);
            if (stack.is(Items.EMERALD) && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    static int freeSlots(Container backpack) {
        int free = 0;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            if (backpack.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** Cold vs repeated {@code ensureIndexed}. TE memoizes on (RecipeManager, config) identity. */
    private static int index(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            RecipeValues.ensureIndexed(source.getServer());
            long cold = System.nanoTime() - t0;

            long t1 = System.nanoTime();
            RecipeValues.ensureIndexed(source.getServer());
            long warm = System.nanoTime() - t1;

            if (coldIndexNanos < 0) {
                coldIndexNanos = cold;
            }
            warmIndexNanos = warm;
            indexed = true;
            source.sendSuccess(() -> Component.literal(String.join("\n",
                    "[TE3] ensureIndexed cold      = " + ms(cold),
                    "[TE3] ensureIndexed repeated  = " + ms(warm) + "  (memoized)",
                    "[TE3] index ready - `scan` is now permitted")), false);
            return 1;
        } catch (NoClassDefFoundError missing) {
            source.sendFailure(Component.literal(
                    "[TE3] Trade Everything is not on the runtime classpath - install "
                            + "tradeeverything-fabric-0.3.0.jar in this instance"));
            return 0;
        }
    }

    private static String ms(long nanos) {
        return String.format("%.3f ms", nanos / 1_000_000.0D);
    }

    // ------------------------------------------------------ P0-2 detached synthetic execution

    /**
     * P0-2 — execute <b>exactly one</b> detached synthetic SELL and prove the whole call path.
     *
     * <h2>What "detached" means here</h2>
     *
     * The offer is produced by {@code OfferQuoter.quote} and handed straight to
     * {@code VillagerTradeAdapter.executeResolved}. It is never inserted into
     * {@code villager.getOffers()}, no {@code MerchantMenu} or {@code MerchantContainer} is
     * constructed, {@code setTradingPlayer} is never called, and no {@code Player} — real or fake —
     * exists anywhere in the path.
     *
     * <h2>Why runtime and not source</h2>
     *
     * The source review said TE's {@code afterTrade} injection asks only whether the offer argument
     * is synthetic and then resets that offer's uses, without searching the board. That is a reading
     * of bytecode. Whether the mixin is actually applied, whether the marker survives our call path,
     * and whether the hook fires for an offer the villager has never heard of are runtime facts. The
     * decisive one is {@code uses}: {@code notifyTrade} increments it, TE's hook resets it, so
     * <b>uses == 0 after the trade is the only direct evidence the hook fired detached</b>.
     *
     * <p>The quoted object is passed through by reference on purpose. TE marks synthetic offers with
     * a mixin-injected instance field, so rebuilding it from its own field values would silently
     * strip the marker — pinned below as an explicit negative control rather than left as advice.
     */
    private static int p02(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        net.minecraft.world.entity.npc.Villager villager = nearestFixtureVillager(source);
        Mob mob = nearestScavenger(source);
        Container backpack = mob == null ? null : PlayerMobs.backpack(mob);
        if (villager == null || backpack == null) {
            source.sendFailure(Component.literal(
                    "[TE3] need one te3-tagged Villager and one te3 PlayerMob with a backpack - "
                            + "run /function te3:scenario/p02_detached"));
            return 0;
        }

        List<String> checks = new ArrayList<>();
        MerchantOffers board = villager.getOffers();

        // ---- BEFORE
        boolean playerBefore = villager.getTradingPlayer() == null;
        boolean syntheticBefore = noSyntheticRow(board);
        List<String> boardBefore = fingerprint(board);
        int professionXpBefore = villager.getVillagerXp();

        RecipeValues.ensureIndexed(source.getServer());
        ItemStack input = backpack.getItem(0);
        if (input.isEmpty()) {
            source.sendFailure(Component.literal("[TE3] backpack slot 0 is empty - seed first"));
            return 0;
        }
        Optional<MerchantOffer> quoted = OfferQuoter.quote(villager, input, board);
        if (quoted.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[TE3] no quote for " + describe(input) + " - is the seller conditioned?"));
            return 0;
        }
        // The object TE produced. Never reconstructed, never copied before notify.
        MerchantOffer quote = quoted.get();
        boolean marked = games.brennan.tradeeverything.trade.SyntheticOfferFactory
                .isSynthetic(quote);

        // ---- DURING (quoting must not have installed anything)
        boolean playerDuring = villager.getTradingPlayer() == null;
        boolean syntheticDuring = noSyntheticRow(villager.getOffers());

        int costItemBefore = ScavengerCrafting.count(backpack, quote.getCostA().getItem());
        int resultItemBefore = ScavengerCrafting.count(backpack, quote.getResult().getItem());
        int usesBefore = quote.getUses();
        int orbsBefore = countOrbs(level, villager);
        boolean rewardExp = quote.shouldRewardExp();

        if (!marked) {
            source.sendFailure(Component.literal(
                    "[TE3] REFUSED - the fresh quote is NOT marked synthetic. Executing it would "
                            + "not exercise TE's afterTrade hook, so the run would prove nothing."));
            return 0;
        }

        // ---- THE ONE TRANSACTION
        int[] notified = new int[1];
        VillagerTradeAdapter.TradeResult result = VillagerTradeAdapter.executeResolved(
                backpack, quote, offer -> {
                    notified[0]++;
                    villager.notifyTrade(offer);
                });

        // ---- AFTER
        int usesAfter = quote.getUses();
        int costItemAfter = ScavengerCrafting.count(backpack, quote.getCostA().getItem());
        int resultItemAfter = ScavengerCrafting.count(backpack, quote.getResult().getItem());
        boolean playerAfter = villager.getTradingPlayer() == null;
        boolean syntheticAfter = noSyntheticRow(villager.getOffers());
        List<String> boardDrift = diffBoards(boardBefore, fingerprint(villager.getOffers()));
        int professionXpAfter = villager.getVillagerXp();
        int orbsAfter = countOrbs(level, villager);

        // ---- marker hazard, pinned as a control rather than as advice. Run AFTER the transaction
        // so it cannot influence it.
        MerchantOffer reconstructed = new MerchantOffer(quote.getItemCostA(), quote.getItemCostB(),
                quote.getResult().copy(), quote.getUses(), quote.getMaxUses(), quote.getXp(),
                quote.getPriceMultiplier());
        boolean reconstructedMarked = games.brennan.tradeeverything.trade.SyntheticOfferFactory
                .isSynthetic(reconstructed);
        boolean copiedMarked = games.brennan.tradeeverything.trade.SyntheticOfferFactory
                .isSynthetic(quote.copy());

        int expectedCost = quote.getCostA().getCount();
        int expectedResult = quote.getResult().getCount();

        check(checks, "no tradingPlayer BEFORE", playerBefore);
        check(checks, "no tradingPlayer DURING", playerDuring);
        check(checks, "no tradingPlayer AFTER", playerAfter);
        check(checks, "no synthetic row on board BEFORE", syntheticBefore);
        check(checks, "no synthetic row on board DURING", syntheticDuring);
        check(checks, "no synthetic row on board AFTER", syntheticAfter);
        check(checks, "fresh detached quote is marked synthetic", marked);
        check(checks, "adapter returned TRADED (got " + result + ")",
                result == VillagerTradeAdapter.TradeResult.TRADED);
        check(checks, "notifyTrade fired exactly once (" + notified[0] + ")", notified[0] == 1);
        check(checks, "payment removed exactly once: " + describe(quote.getCostA()) + "  "
                        + costItemBefore + " -> " + costItemAfter,
                costItemBefore - costItemAfter == expectedCost);
        check(checks, "result inserted exactly once: " + describe(quote.getResult()) + "  "
                        + resultItemBefore + " -> " + resultItemAfter,
                resultItemAfter - resultItemBefore == expectedResult);
        check(checks, "uses before = " + usesBefore, usesBefore == 0);
        check(checks, "uses after  = " + usesAfter
                        + "  <- TE afterTrade fired DETACHED (notifyTrade increments, TE resets)",
                usesAfter == 0);
        check(checks, "villager profession XP unchanged (" + professionXpBefore + " -> "
                        + professionXpAfter + ", synthetic xp=" + quote.getXp() + ")",
                professionXpBefore == professionXpAfter);
        check(checks, "real board unchanged and ordered"
                        + (boardDrift.isEmpty() ? "" : "  " + boardDrift), boardDrift.isEmpty());
        check(checks, "NEGATIVE CONTROL new MerchantOffer(...) LOSES the marker",
                !reconstructedMarked);
        check(checks, "NEGATIVE CONTROL MerchantOffer.copy() PRESERVES the marker", copiedMarked);

        StringBuilder out = new StringBuilder("[TE3] P0-2 detached synthetic execution witness");
        out.append((char) 10);
        out.append("  quote   = ").append(describeOffer(quote)).append((char) 10);
        for (String line : checks) {
            out.append("  ").append(line).append((char) 10);
        }
        // Reported as observation, not asserted: whether an orb is visible to getEntitiesOfClass on
        // the same tick it was spawned is a scheduling detail, so a zero delta here is not by itself
        // proof that no orb was created. shouldRewardExp() is the fact that decides the behaviour.
        out.append("  OBSERVED shouldRewardExp() = ").append(rewardExp)
                .append("   XP orbs near villager ").append(orbsBefore).append(" -> ")
                .append(orbsAfter).append((char) 10);
        boolean pass = checks.stream().allMatch(line -> line.startsWith("PASS"));
        out.append("  VERDICT: ").append(pass
                        ? "P0-2 WITNESS PASS - one detached synthetic trade executed with no board "
                                + "insertion, no session and no Player."
                        : "P0-2 WITNESS FAIL - see the FAIL lines above.")
                .append((char) 10);
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return pass ? 1 : 0;
    }

    private static void check(List<String> into, String what, boolean ok) {
        into.add((ok ? "PASS  " : "FAIL  ") + what);
    }

    private static boolean noSyntheticRow(MerchantOffers board) {
        for (MerchantOffer offer : board) {
            if (games.brennan.tradeeverything.trade.SyntheticOfferFactory.isSynthetic(offer)) {
                return false;
            }
        }
        return true;
    }

    private static int countOrbs(ServerLevel level, net.minecraft.world.entity.Entity around) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                new AABB(around.blockPosition()).inflate(8.0D)).size();
    }

    // ------------------------------------------------------------------ P0-1 exact quote parity

    /**
     * P0-1 — does the direct quote Scavenger would call equal the offer Trade Everything itself
     * materializes during a real merchant session?
     *
     * <h2>What the two paths actually are</h2>
     *
     * <pre>
     * DIRECT   RecipeValues.ensureIndexed(server)
     *          OfferQuoter.quote(villager, input, villager.getOffers())      // no synthetic row
     *
     * TE       AbstractVillagerTradingMixin#onSetTradingPlayer inserts a PLACEHOLDER at index 0
     *          MerchantContainerMixin#repriceInner then does
     *              offers.set(0, OfferQuoter.quoteOrPlaceholder(villager, input, offers))
     *                                                                       // WITH the synthetic row
     * </pre>
     *
     * <p>So the boards handed to the pricer are <b>not</b> the same list: TE's carries its own
     * synthetic row at index 0. {@code DefaultBuyItemSelector.select} and
     * {@code TradePricer.payoutValueSixteenths} both skip synthetic offers, so the pricing inputs
     * <i>ought</i> to be identical — and "ought" is exactly what this probe exists to replace. If
     * the two ever diverge, every Scavenger decision made from a direct quote is a decision about a
     * trade the player would never be shown.
     *
     * <h2>Known, expected divergence</h2>
     *
     * For an input that does not quote, TE substitutes {@code SyntheticOfferFactory.placeholder}
     * while the direct path returns {@code Optional.empty()}. That is a UI affordance, not a price,
     * so {@code live} refuses placeholders rather than reporting them as a parity failure.
     *
     * <h2>State</h2>
     *
     * One snapshot slot, overwritten on each {@code snapshot} and cleared by {@code reset}. Bounded
     * by construction (RET-1a): it cannot grow, and it dies with the probe.
     */
    private static java.util.UUID paritySubject;
    private static ItemStack parityInput = ItemStack.EMPTY;
    private static MerchantOffer parityDirect;
    private static List<String> parityBoard;

    private static int paritySnapshot(CommandSourceStack source) {
        net.minecraft.world.entity.npc.Villager villager = nearestFixtureVillager(source);
        if (villager == null) {
            source.sendFailure(Component.literal("[TE3] no te3-tagged Villager in range"));
            return 0;
        }
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null || player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.literal(
                    "[TE3] hold the EXACT stack you will put in the trade slot - the parity claim "
                            + "is per-input, and a different stack proves nothing"));
            return 0;
        }
        MerchantOffers board = villager.getOffers();
        // R1 - stated correctly. A synthetic row present RIGHT NOW proves a session is or was open.
        // Its ABSENCE proves nothing of the kind: TE removes the row on setTradingPlayer(null), so a
        // completed earlier session leaves the board looking exactly like a virgin one. This check
        // is therefore NECESSARY AND NOT SUFFICIENT, and "fresh process" remains a PROCEDURAL
        // precondition that this probe does not verify. The only session evidence it owns is
        // paritySessionSeen, tracked from `arm` onward - see observe().
        for (MerchantOffer existing : board) {
            if (games.brennan.tradeeverything.trade.SyntheticOfferFactory.isSynthetic(existing)) {
                source.sendFailure(Component.literal(
                        "[TE3] refused - this villager carries a synthetic row NOW, so a session is "
                                + "or was open. Close it and restart the process. NOTE: the absence "
                                + "of this row does not prove no session has occurred - TE removes "
                                + "it on close - so a fresh process stays your responsibility."));
                return 0;
            }
        }
        RecipeValues.ensureIndexed(source.getServer());
        ItemStack input = player.getMainHandItem().copy();
        Optional<MerchantOffer> quoted = OfferQuoter.quote(villager, input, board);
        if (quoted.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[TE3] the direct path returned no quote for " + describe(input)
                            + " - TE would show a placeholder here, which is a UI affordance and "
                            + "not a price. Pick an input that actually quotes."));
            return 0;
        }
        paritySubject = villager.getUUID();
        parityInput = input;
        parityDirect = quoted.get();
        parityBoard = fingerprint(board);
        parityArmed = true;
        parityArmRemaining = ARM_WINDOW_TICKS;
        parityCaptured = null;
        parityCapturedBoard = null;
        paritySessionSeen = false;
        final String subject = id(villager) + " " + paritySubject;
        source.sendSuccess(() -> Component.literal(String.join(String.valueOf((char) 10),
                "[TE3] P0-1 direct snapshot taken BEFORE any session",
                "  subject = " + subject,
                "  input   = " + describe(parityInput),
                "  direct  = " + describeOffer(parityDirect),
                "  board   = " + parityBoard.size() + " non-synthetic offers fingerprinted",
                "  observer ARMED for " + (ARM_WINDOW_TICKS / 20) + "s - no command is needed",
                "  while the merchant screen is open.",
                "  Open THIS villager, place that exact stack in the trade slot, close the screen,",
                "  then run: /spmscavenger debug te3 parity live")), false);
        return 1;
    }

    private static int parityLive(CommandSourceStack source) {
        if (parityDirect == null) {
            source.sendFailure(Component.literal("[TE3] no snapshot - run `parity snapshot` first"));
            return 0;
        }
        if (parityCaptured == null) {
            source.sendFailure(Component.literal(String.join(String.valueOf((char) 10),
                    "[TE3] the observer captured nothing.",
                    "  armed            = " + parityArmed,
                    "  session observed = " + paritySessionSeen
                            + "  (a synthetic row was seen on the subject since arming)",
                    paritySessionSeen
                            ? "  A session happened but no PRICED quote for the snapshotted input "
                                    + "appeared. Place that exact stack in the trade slot so "
                                    + "repriceInner replaces the placeholder."
                            : "  No session was observed at all. Re-arm and open the pinned "
                                    + "villager: /spmscavenger debug te3 parity arm")));
            return 0;
        }
        List<String> boardDrift = diffBoards(parityBoard, parityCapturedBoard);
        List<String> fields = diffOffers(parityDirect, parityCaptured);
        StringBuilder out = new StringBuilder("[TE3] P0-1 exact quote parity");
        out.append((char) 10);
        out.append("  input   = ").append(describe(parityInput)).append((char) 10);
        out.append("  direct  = ").append(describeOffer(parityDirect)).append((char) 10);
        out.append("  TE live = ").append(describeOffer(parityCaptured)).append((char) 10);
        if (!boardDrift.isEmpty()) {
            // Ordered and multiplicity-sensitive. The two paths must have priced against the SAME
            // real board, or field equality is a coincidence rather than a result.
            out.append("  BOARD DRIFT between snapshot and session:").append((char) 10);
            for (String line : boardDrift) {
                out.append("    ").append(line).append((char) 10);
            }
        }
        if (fields.isEmpty() && boardDrift.isEmpty()) {
            out.append("  VERDICT: EXACT PARITY - every compared field identical, on an unchanged "
                    + "real board. The direct path is what the player would be shown.")
                    .append((char) 10);
        } else {
            out.append("  VERDICT: DIVERGENT").append((char) 10);
            for (String field : fields) {
                out.append("    ").append(field).append((char) 10);
            }
        }
        out.append("  now close the merchant if it is still open, then: ")
                .append("/spmscavenger debug te3 parity closed").append((char) 10);
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return fields.isEmpty() && boardDrift.isEmpty() ? 1 : 0;
    }

    private static int parityClosed(CommandSourceStack source) {
        net.minecraft.world.entity.npc.Villager villager = subjectVillager(source);
        if (villager == null) {
            source.sendFailure(Component.literal("[TE3] snapshotted villager not in range"));
            return 0;
        }
        List<String> leftovers = new ArrayList<>();
        for (MerchantOffer offer : villager.getOffers()) {
            if (games.brennan.tradeeverything.trade.SyntheticOfferFactory.isSynthetic(offer)) {
                leftovers.add(describeOffer(offer));
            }
        }
        final boolean clean = leftovers.isEmpty();
        List<String> boardNow = fingerprint(villager.getOffers());
        final List<String> restoreDrift = parityBoard == null
                ? List.of("<no snapshot>") : diffBoards(parityBoard, boardNow);
        final boolean sameBoard = restoreDrift.isEmpty();
        final String remaining = leftovers.size() + (clean ? "" : "  " + leftovers);
        source.sendSuccess(() -> Component.literal(String.join(String.valueOf((char) 10),
                "[TE3] P0-1 session cleanup",
                "  synthetic rows remaining     = " + remaining,
                "  non-synthetic board restored = " + sameBoard
                        + (sameBoard ? "" : "  " + restoreDrift),
                "  VERDICT: " + (clean && sameBoard
                        ? "CLEAN - TE removed its session-scoped row and left the real board intact."
                        : "NOT CLEAN - a synthetic row or a board change survived the session. Any "
                                + "Scavenger read of getOffers() outside a session would see it."))),
                false);
        return clean && sameBoard ? 1 : 0;
    }

    /**
     * R1 — <b>ordered, multiplicity-sensitive</b> board comparison.
     *
     * <p>The first version did {@code before.removeAll(after)} and reported the remainder. That is
     * asymmetric and detects only one of four ways a board can change: a <b>removed</b> offer. An
     * <b>added</b> offer, a <b>reordered</b> pair, and a <b>duplicated</b> offer all left the
     * remainder empty and were reported as "no drift" — so a parity result could have been produced
     * against a board that had genuinely changed between the direct quote and the session. Pure and
     * unit-tested, since a comparison that cannot see a difference is the failure mode here.
     */
    static List<String> diffBoards(List<String> before, List<String> after) {
        List<String> out = new ArrayList<>();
        if (before.size() != after.size()) {
            out.add("board size: snapshot=" + before.size() + " session=" + after.size());
        }
        for (int index = 0; index < Math.max(before.size(), after.size()); index++) {
            String was = index < before.size() ? before.get(index) : "<absent>";
            String now = index < after.size() ? after.get(index) : "<absent>";
            if (!was.equals(now)) {
                out.add("board[" + index + "]: snapshot=" + was + " session=" + now);
            }
        }
        return out;
    }

    /**
     * A value copy, because the captured offer must not keep tracking the live one.
     *
     * <p>TE replaces index 0 wholesale on each reprice rather than mutating in place, so the
     * captured reference would survive repricing — but {@code uses} and {@code specialPriceDiff} are
     * mutated in place when a trade actually completes. Capturing the object would then let a later
     * action rewrite evidence that was supposed to be a snapshot.
     */
    private static MerchantOffer copyOf(MerchantOffer offer) {
        MerchantOffer copy = new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(),
                offer.getResult().copy(), offer.getUses(), offer.getMaxUses(), offer.getXp(),
                offer.getPriceMultiplier(), offer.getDemand());
        copy.setSpecialPriceDiff(offer.getSpecialPriceDiff());
        return copy;
    }

    /**
     * R1 — the armed observer that removes the impossible instruction.
     *
     * <h2>The defect</h2>
     *
     * The first design told the user to run a chat command <i>while the merchant GUI was open</i>.
     * They cannot: the GUI owns the keyboard, and closing it fires {@code setTradingPlayer(null)},
     * which is precisely when TE removes the row we were trying to read. The instruction was not
     * merely awkward, it was unsatisfiable — the observation window and the ability to observe were
     * mutually exclusive.
     *
     * <h2>What this does instead</h2>
     *
     * While armed, each server tick reads the pinned villager's board and captures the <b>first</b>
     * real priced synthetic quote it sees, then disarms. It <b>only observes</b>: no pricing, no
     * transaction, no write of any kind to the board. Every field it records was produced by TE.
     *
     * <p>Bounded three ways (RET-1a): it captures at most once, it disarms itself after
     * {@link #ARM_WINDOW_TICKS}, and its entire state is four static fields that are overwritten,
     * never accumulated.
     *
     * <h2>Guards on the capture</h2>
     *
     * <ul>
     *   <li>{@code getTradingPlayer() != null} — a real session must be live, so this cannot capture
     *       a row left behind by something else.</li>
     *   <li>not a placeholder — the placeholder is a UI affordance and carries no price.</li>
     *   <li>{@code costA} item equals the snapshotted input — otherwise it is a quote for a
     *       different stack and comparing it to our direct quote would be meaningless.</li>
     * </ul>
     */
    private static final int ARM_WINDOW_TICKS = 20 * 180;
    private static boolean parityArmed;
    private static int parityArmRemaining;
    private static MerchantOffer parityCaptured;
    private static List<String> parityCapturedBoard;
    private static boolean paritySessionSeen;

    /** Registered once from the mod initializer. */
    public static void installObserver() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK
                .register(Te3ProbeCommand::observe);
    }

    private static void observe(net.minecraft.server.MinecraftServer server) {
        tickMutation(server);
        if (!parityArmed || paritySubject == null) {
            return;
        }
        if (--parityArmRemaining <= 0) {
            parityArmed = false;
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!(level.getEntity(paritySubject)
                    instanceof net.minecraft.world.entity.npc.Villager villager)) {
                continue;
            }
            MerchantOffers board = villager.getOffers();
            if (board.isEmpty()) {
                return;
            }
            MerchantOffer first = board.get(0);
            if (!games.brennan.tradeeverything.trade.SyntheticOfferFactory.isSynthetic(first)) {
                return;
            }
            // Tracked from arming onward. This is the ONLY session evidence this probe owns; see
            // paritySnapshot for why the pre-arm history cannot be reconstructed from the board.
            paritySessionSeen = true;
            if (games.brennan.tradeeverything.trade.SyntheticOfferFactory.isPlaceholder(first)
                    || villager.getTradingPlayer() == null
                    || !first.getItemCostA().item().value().equals(parityInput.getItem())) {
                return;
            }
            parityCaptured = copyOf(first);
            parityCapturedBoard = fingerprint(board);
            parityArmed = false;
            return;
        }
    }

    // ------------------------------------------------------------- step 7B market mutation

    /**
     * Step 7B — change the market, not Scavenger.
     *
     * <h2>The lever</h2>
     *
     * {@code TradeEverythingApi.setItemOverride} is upstream's own public API for an item's value in
     * sixteenths, read by {@code ItemValuation.overrideValue} on every valuation. Raising oak_log
     * from its derived value of 1 changes what a log is worth, so the next quote costs a different
     * number of logs — <b>real pricing state, changed from outside</b>.
     *
     * <p>What this deliberately is not: telling Scavenger to reject anything, forcing a replan, or
     * faking a Q2 mismatch inside our own code. A fixture that did those would prove that a fixture
     * can make a test pass.
     *
     * <h2>Why it fires from the tick loop</h2>
     *
     * The mutation has to land <b>between</b> selection and revalidation — while the mob walks. Doing
     * it from the observer hook would put an action into the path that is supposed to only watch, and
     * the whole step-7A claim rests on that path being inert. So the observer keeps a count and this
     * reads it.
     */
    private static final int MUTATED_LOG_VALUE = 2;
    private static boolean mutationArmed;
    private static boolean mutationApplied;

    private static int applyMutation(CommandSourceStack source) {
        try {
            games.brennan.tradeeverything.api.TradeEverythingApi.setItemOverride(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("oak_log"),
                    MUTATED_LOG_VALUE);
        } catch (NoClassDefFoundError missing) {
            source.sendFailure(Component.literal(
                    "[TE3] Trade Everything is not installed - nothing to mutate"));
            return 0;
        }
        mutationApplied = true;
        mutationArmed = false;
        TradeRuntimeObserver.note("MUTATION APPLIED  oak_log value -> " + MUTATED_LOG_VALUE
                + " sixteenths (upstream setItemOverride)");
        source.sendSuccess(() -> Component.literal(
                "[TE3] market mutated: oak_log value -> " + MUTATED_LOG_VALUE
                        + ". The next TE quote costs a different number of logs."), false);
        return 1;
    }

    /** Fires once, as soon as the mob has actually planned a Trade Everything route. */
    private static void tickMutation(net.minecraft.server.MinecraftServer server) {
        if (!mutationArmed || mutationApplied
                || TradeRuntimeObserver.tradeEverythingSelections() == 0) {
            return;
        }
        applyMutation(server.createCommandSourceStack().withSuppressedOutput());
    }

    private static int parityArm(CommandSourceStack source) {
        if (paritySubject == null) {
            source.sendFailure(Component.literal("[TE3] no snapshot - run `parity snapshot` first"));
            return 0;
        }
        parityArmed = true;
        parityArmRemaining = ARM_WINDOW_TICKS;
        parityCaptured = null;
        parityCapturedBoard = null;
        source.sendSuccess(() -> Component.literal(String.join(String.valueOf((char) 10),
                "[TE3] observer armed for " + (ARM_WINDOW_TICKS / 20) + "s",
                "  open the pinned villager and place the snapshotted stack in the trade slot.",
                "  The capture happens on a server tick while the GUI is open - you do NOT need to",
                "  type anything with the merchant screen up, which was not possible anyway.",
                "  Then close the merchant and run: /spmscavenger debug te3 parity live")), false);
        return 1;
    }

    /**
     * Field-exact offer comparison — the <b>pure</b> seam, so its strength is unit-testable.
     *
     * <p>"Both say emerald" is not parity. Count, component predicate, lifetime and the price
     * modifiers all decide whether a plan built on the direct quote survives contact with the offer
     * the player is actually shown. {@code getItemCostA} is compared because {@code ItemCost}
     * carries the {@code DataComponentPredicate}; {@code getCostA} is compared as well because that
     * is the stack the demand and special-price modifiers actually land on.
     */
    static List<String> diffOffers(MerchantOffer direct, MerchantOffer live) {
        List<String> out = new ArrayList<>();
        if (!sameCost(direct.getItemCostA(), live.getItemCostA())) {
            out.add("costA: direct=" + direct.getItemCostA() + " live=" + live.getItemCostA());
        }
        if (direct.getItemCostB().isPresent() != live.getItemCostB().isPresent()
                || (direct.getItemCostB().isPresent()
                        && !sameCost(direct.getItemCostB().get(), live.getItemCostB().get()))) {
            out.add("costB: direct=" + direct.getItemCostB() + " live=" + live.getItemCostB());
        }
        if (!ItemStack.matches(direct.getCostA(), live.getCostA())) {
            out.add("effective costA: direct=" + describe(direct.getCostA())
                    + " live=" + describe(live.getCostA()));
        }
        if (!ItemStack.matches(direct.getResult(), live.getResult())) {
            out.add("result: direct=" + describe(direct.getResult())
                    + " live=" + describe(live.getResult()));
        }
        if (direct.getMaxUses() != live.getMaxUses()) {
            out.add("maxUses: direct=" + direct.getMaxUses() + " live=" + live.getMaxUses());
        }
        if (direct.getUses() != live.getUses()) {
            out.add("uses: direct=" + direct.getUses() + " live=" + live.getUses());
        }
        if (direct.getXp() != live.getXp()) {
            out.add("xp: direct=" + direct.getXp() + " live=" + live.getXp());
        }
        if (Float.compare(direct.getPriceMultiplier(), live.getPriceMultiplier()) != 0) {
            out.add("priceMultiplier: direct=" + direct.getPriceMultiplier()
                    + " live=" + live.getPriceMultiplier());
        }
        if (direct.getDemand() != live.getDemand()) {
            out.add("demand: direct=" + direct.getDemand() + " live=" + live.getDemand());
        }
        if (direct.getSpecialPriceDiff() != live.getSpecialPriceDiff()) {
            out.add("specialPriceDiff: direct=" + direct.getSpecialPriceDiff()
                    + " live=" + live.getSpecialPriceDiff());
        }
        if (direct.shouldRewardExp() != live.shouldRewardExp()) {
            out.add("rewardExp: direct=" + direct.shouldRewardExp()
                    + " live=" + live.shouldRewardExp());
        }
        return out;
    }

    /**
     * {@code ItemCost} is a record, and its generated {@code equals} is <b>useless here</b>: one of
     * its components is a cached {@code ItemStack}, and {@code ItemStack} inherits identity
     * equality. Two structurally identical costs therefore compare unequal, which made the very
     * first parity run report {@code DIVERGENT} on a pair built by the same expression.
     *
     * <p>It failed in the safe direction — a false divergence, not a false parity — but a
     * comparator that always says "different" answers no question at all. Compared field-wise
     * instead: holder, count, and the component predicate.
     */
    private static boolean sameCost(net.minecraft.world.item.trading.ItemCost direct,
            net.minecraft.world.item.trading.ItemCost live) {
        return direct.item().equals(live.item())
                && direct.count() == live.count()
                && direct.components().equals(live.components());
    }

    /** The non-synthetic board, as stable text. Synthetic rows are excluded by definition. */
    static List<String> fingerprint(MerchantOffers board) {
        List<String> out = new ArrayList<>();
        for (MerchantOffer offer : board) {
            if (!games.brennan.tradeeverything.trade.SyntheticOfferFactory.isSynthetic(offer)) {
                out.add(describeOffer(offer));
            }
        }
        return out;
    }

    static String describeOffer(MerchantOffer offer) {
        return describe(offer.getCostA())
                + (offer.getCostB().isEmpty() ? "" : " + " + describe(offer.getCostB()))
                + " -> " + describe(offer.getResult())
                + "  [uses " + offer.getUses() + "/" + offer.getMaxUses()
                + ", xp " + offer.getXp()
                + ", mult " + offer.getPriceMultiplier()
                + ", demand " + offer.getDemand()
                + ", special " + offer.getSpecialPriceDiff() + "]";
    }

    private static net.minecraft.world.entity.npc.Villager nearestFixtureVillager(
            CommandSourceStack source) {
        return source.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.npc.Villager.class,
                        new AABB(net.minecraft.core.BlockPos.containing(source.getPosition()))
                                .inflate(RADIUS),
                        v -> v.getTags().contains("te3"))
                .stream().findFirst().orElse(null);
    }

    private static net.minecraft.world.entity.npc.Villager subjectVillager(
            CommandSourceStack source) {
        if (paritySubject == null) {
            return null;
        }
        return source.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.npc.Villager.class,
                        new AABB(net.minecraft.core.BlockPos.containing(source.getPosition()))
                                .inflate(RADIUS),
                        v -> v.getUUID().equals(paritySubject))
                .stream().findFirst().orElse(null);
    }

    // --------------------------------------------------------------- R12 fixture conditioning

    /** Bounded: 0.4^40 and 0.6^40 are both below 1e-8, so exhaustion is a real fault, not bad luck. */
    private static final int MAX_NATURAL_ROLLS = 40;

    /**
     * R12 — condition the te3 merchants into the exact board the source census proved reachable.
     *
     * <h2>Re-roll, never author</h2>
     *
     * Both boards this witness needs are ordinary vanilla draws that simply are not certain:
     *
     * <ul>
     *   <li><b>Novice armorer, all-sell.</b> The level-1 pool is {@code EmeraldForItems(COAL,15)}
     *       plus four {@code ItemsForEmeralds} iron-armour listings, and {@code updateTrades} draws
     *       2. Both drawn from the four sell listings — probability {@code C(4,2)/C(5,2) = 0.6} —
     *       leaves no non-emerald cost on the board, so {@code DefaultBuyItemSelector} falls through
     *       to its {@code EMERALD} return. That fallback is the <i>only</i> way an authorized
     *       log/plank/stick can be paid in emeralds: escalation is structurally impossible, because
     *       {@code payoutFor} compares <b>one</b> item's value (count-independent — 1 x 0.75) against
     *       {@code unit x cap}, whose smallest possible value is 1.</li>
     *   <li><b>Level-3 toolsmith with the iron pickaxe.</b> That pool is
     *       {@code EmeraldForItems(FLINT,30)}, three {@code EnchantedItemForEmeralds}
     *       (axe/shovel/pickaxe) and {@code ItemsForEmeralds(DIAMOND_HOE)} — 2 drawn, so the pickaxe
     *       appears {@code 1 - C(4,2)/C(5,2) = 0.4} of the time. This is the exact draw that failed
     *       VR-T2's first setup.</li>
     * </ul>
     *
     * <p>So the fixture <b>discards vanilla boards until vanilla produces the one it needs</b>, and
     * never writes an {@code Offers} tag. Authoring the toolsmith's listing would have made "read
     * its exact live price" vacuous — the price would be the one this file chose. Every offer the
     * scan then measures is generated by {@code Villager#updateTrades}, at whatever price and
     * enchantment vanilla rolled.
     *
     * <p>The seller predicate is TE's own {@link games.brennan.tradeeverything.trade.ItemValuation
     * #selectBuyItem}, not a re-derivation of it. GVC-6: mirror the consuming mod's predicate, and a
     * re-derived copy would drift from the thing being measured.
     */
    private static int fixture(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<net.minecraft.world.entity.npc.Villager> present = level.getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                new AABB(net.minecraft.core.BlockPos.containing(source.getPosition()))
                        .inflate(RADIUS),
                v -> v.getTags().contains("te3"));
        if (present.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[TE3] no te3-tagged villagers - run the scenario function first"));
            return 0;
        }
        List<String> report = new ArrayList<>();
        try {
            // Same exposure as `index`: TE is modCompileOnly, so its absence is a missing class at
            // the first call, not a load failure. Reported as an install problem rather than a stack
            // trace, because that is what it always is.
            games.brennan.tradeeverything.trade.ItemValuation.class.getName();
        } catch (NoClassDefFoundError missing) {
            source.sendFailure(Component.literal(
                    "[TE3] Trade Everything is not on the runtime classpath - install "
                            + "tradeeverything-fabric-0.3.0.jar in this instance"));
            return 0;
        }
        for (net.minecraft.world.entity.npc.Villager seed : present) {
            String profession = id(seed);
            java.util.function.Predicate<net.minecraft.world.entity.npc.Villager> wanted =
                    switch (profession) {
                        case "armorer" -> Te3ProbeCommand::sellsOnlyForEmeralds;
                        case "toolsmith" -> Te3ProbeCommand::listsIronPickaxe;
                        default -> null;
                    };
            if (wanted == null) {
                report.add("  " + profession + " - no condition, left as rolled");
                continue;
            }
            int[] rolls = new int[1];
            net.minecraft.world.entity.npc.Villager settled = rerollNaturally(level, seed, wanted, rolls);
            if (settled == null) {
                source.sendFailure(Component.literal("[TE3] " + profession + " did not roll the "
                        + "required board in " + MAX_NATURAL_ROLLS + " vanilla draws. At p>=0.4 "
                        + "that is ~1e-8 - suspect the pool, not the dice."));
                return 0;
            }
            report.add("  " + profession + " settled after " + rolls[0] + " vanilla draw(s): "
                    + describeBoard(settled));
        }
        source.sendSuccess(() -> Component.literal("[TE3] fixture conditioned (no Offers authored)\n"
                + String.join("\n", report)), false);
        return 1;
    }

    /** True when TE's own selector finds no non-emerald cost, so the payout falls back to emerald. */
    private static boolean sellsOnlyForEmeralds(net.minecraft.world.entity.npc.Villager villager) {
        return !villager.getOffers().isEmpty()
                && games.brennan.tradeeverything.trade.ItemValuation
                        .selectBuyItem(villager, villager.getOffers()) == Items.EMERALD;
    }

    /**
     * The purchasing power a step-7A mob can actually reach.
     *
     * <p>Five log stacks less the craft reserve is 319 disposable logs; at the census-derived
     * 22 logs/emerald that is {@code floor(319/22) = 14} emeralds, and the fixture seeds none. A
     * vanilla iron pickaxe rolls at 8..22, so a roll above this is a market the mob cannot afford —
     * the route would be correct and simply never complete.
     *
     * <p>Narrowing which vanilla roll is accepted, not authoring one: the price is still whatever
     * vanilla generated, and the accepted board is an unmodified draw.
     */
    private static final int MAX_AFFORDABLE_PICKAXE_PRICE = 14;

    private static boolean listsIronPickaxe(net.minecraft.world.entity.npc.Villager villager) {
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.getResult().is(Items.IRON_PICKAXE)
                    && offer.getCostA().getCount() <= MAX_AFFORDABLE_PICKAXE_PRICE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Discard and re-summon until vanilla rolls an acceptable board. {@code AbstractVillager} builds
     * offers lazily and exposes no reset, so a fresh entity is the only way to draw again without
     * writing the field ourselves.
     */
    private static net.minecraft.world.entity.npc.Villager rerollNaturally(
            ServerLevel level, net.minecraft.world.entity.npc.Villager seed,
            java.util.function.Predicate<net.minecraft.world.entity.npc.Villager> wanted,
            int[] rollsOut) {
        net.minecraft.world.entity.npc.Villager current = seed;
        for (int roll = 1; roll <= MAX_NATURAL_ROLLS; roll++) {
            if (wanted.test(current)) {
                rollsOut[0] = roll;
                return current;
            }
            net.minecraft.world.entity.npc.Villager next =
                    net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (next == null) {
                return null;
            }
            next.moveTo(current.getX(), current.getY(), current.getZ(),
                    current.getYRot(), current.getXRot());
            next.setVillagerData(current.getVillagerData());
            next.setPersistenceRequired();
            next.setNoAi(true);
            next.setCustomName(current.getCustomName());
            for (String tag : current.getTags()) {
                next.addTag(tag);
            }
            current.discard();
            level.addFreshEntity(next);
            current = next;
        }
        return null;
    }

    private static String describeBoard(net.minecraft.world.entity.npc.Villager villager) {
        List<String> parts = new ArrayList<>();
        for (MerchantOffer offer : villager.getOffers()) {
            parts.add(describe(offer.getCostA()) + " -> " + describe(offer.getResult()));
        }
        return String.join(" | ", parts);
    }

    // ------------------------------------------------------------------ the probe

    enum Bucket { A_DIRECT, B_FUNDING, C_IRRELEVANT, D_ILLEGAL, E_REPRESENTATION_MISS }

    /**
     * One merchant's board as plain data — the minimum seam needed to test the <b>caller</b>.
     *
     * <p>Every revision of this defect was caller-scope: first a single offer, then one merchant's
     * board, while {@code TradeFundingPlanner} was correct throughout. A test that exercised the
     * planner would have proved the component that was never broken. Temporary and package-private;
     * no permanent market abstraction is created for a probe that gets deleted.
     */
    record MarketBoard(String label, List<OfferSnapshot> offers) {
    }

    private static int scan(CommandSourceStack source, String expectDemand) {
        if (!indexed) {
            source.sendFailure(Component.literal(
                    "[TE3] refused - run `index` first. OfferQuoter.quote never calls "
                            + "ensureIndexed, so an unindexed scan would price against TE's "
                            + "fallback economy and report the wrong market (P0-1)."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Mob mob = nearestScavenger(source);
        if (mob == null) {
            source.sendFailure(Component.literal("[TE3] no PlayerMob in range"));
            return 0;
        }
        Container backpack = PlayerMobs.backpack(mob);
        ScavengerConfig cfg = ScavengerConfig.get();
        if (backpack == null) {
            source.sendFailure(Component.literal("[TE3] mob has no backpack"));
            return 0;
        }

        Optional<WorkDemandPolicy.MaterialDemand> demand = WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg)
                .map(WorkDemandPolicy.WorkDemand::payload);
        Optional<WorkDemandPolicy.MaterialDemand> projected = demand.flatMap(d ->
                TradePurchaseProjection.activeSpecFor(d, backpack, mob.getMainHandItem(),
                                mob.getOffhandItem(), cfg)
                        .flatMap(spec -> TradePurchaseProjection.ontoOutput(d, spec)));

        // Fixture entities only. Scanning every nearby AbstractVillager would admit the modpack's
        // own villagers and any passing WanderingTrader - manufacturing A/B reachability from the
        // environment, including the wandering-trader path we agreed must not prove ordinary
        // Villager compatibility.
        // Villager.class, not AbstractVillager: P0-3 asks about ORDINARY villagers, and a
        // WanderingTrader must not be able to prove ordinary-villager reachability. Enforced by
        // type rather than by fixture convention, so an externally tagged trader still cannot slip
        // in. `quote` accepts AbstractVillager, so Villager passes fine.
        List<net.minecraft.world.entity.npc.Villager> merchants = level.getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                new AABB(mob.blockPosition()).inflate(RADIUS),
                v -> v.getTags().contains("te3"));
        if (expectDemand != null) {
            String actual = demand.map(d -> d.materialKey().toString()).orElse("<none>");
            if (!actual.equals(expectDemand)) {
                source.sendFailure(Component.literal("[TE3] scenario INVALID - expected demand "
                        + expectDemand + " but WorkDemandPolicy selected " + actual
                        + ". SURVIVAL outranks PROGRESSION, so surplus logs with torches below "
                        + "target select CHARCOAL over IRON_INGOT. Fix the scenario, not the probe."));
                return 0;
            }
        }
        // Deduplicated: 384 logs occupy 6 slots and every one of them quotes identically against
        // the same merchant, so the raw list would print the single witness six times. The bucket
        // tally below is untouched - only the presentation collapses.
        java.util.LinkedHashMap<String, Integer> lines = new java.util.LinkedHashMap<>();
        int[] tally = new int[Bucket.values().length];
        long quoteNanos = 0L;
        int quotes = 0;
        int returned = 0;
        int candidateStacks = 0;

        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack input = backpack.getItem(slot);
            if (input.isEmpty()) {
                continue;
            }
            candidateStacks++;
            int disposableUnits = disposable(input, backpack, cfg);
            for (net.minecraft.world.entity.npc.Villager merchant : merchants) {
                long t0 = System.nanoTime();
                Optional<MerchantOffer> quoted =
                        OfferQuoter.quote(merchant, input, merchant.getOffers());
                quoteNanos += System.nanoTime() - t0;
                quotes++;
                if (quoted.isEmpty()) {
                    continue;
                }
                returned++;
                MerchantOffer offer = quoted.get();
                // Disposition is judged against the EXACT quote, not against "some units are
                // spare". One disposable log does not authorize a quote costing eight, and the
                // production funding path already requires the count to be covered.
                boolean authorized = disposableUnits >= offer.getCostA().getCount()
                        && ItemStack.isSameItemSameComponents(offer.getCostA(), input);
                String[] evidence = new String[1];
                Bucket bucket = classify(offer, authorized, demand, projected, backpack, cfg,
                        boardsOf(merchants), evidence,
                        m -> SellReserveModel.reservedUnits(m, backpack, cfg));
                tally[bucket.ordinal()]++;
                if (bucket != Bucket.C_IRRELEVANT) {
                    lines.merge("  " + bucket + "  " + describe(offer.getCostA())
                            + " -> " + describe(offer.getResult())
                            + "   SELL @" + id(merchant)
                            + (evidence[0] == null ? ""
                                    : (char) 10 + "        funds: " + evidence[0]),
                            1, Integer::sum);
                }
            }
        }

        StringBuilder out = new StringBuilder("[TE3] P0-3 reachability\n");
        out.append("  demand      = ").append(demand.map(d -> d.materialKey() + " x"
                + d.derivedDeficit()).orElse("<none>")).append('\n');
        out.append("  projection  = ").append(projected.map(d -> d.materialKey().toString())
                .orElse("<none>")).append('\n');
        out.append("  merchants   = ").append(merchants.size())
                .append("   quotes = ").append(quotes).append('\n');
        out.append("  ensureIndexed cold=").append(ms(coldIndexNanos))
                .append(" repeat=").append(ms(warmIndexNanos))
                .append("   quote avg=")
                .append(quotes == 0 ? "n/a" : ms(quoteNanos / quotes)).append('\n');
        for (Bucket b : Bucket.values()) {
            out.append("  ").append(b).append(" = ").append(tally[b.ordinal()]).append('\n');
        }
        if (lines.isEmpty()) {
            out.append("  (no A/B/D/E results)\n");
        } else {
            lines.forEach((line, seen) ->
                    out.append(line).append(seen > 1 ? "   [x" + seen + " input stacks]" : "")
                            .append((char) 10));
        }
        out.append("  backpack slots=").append(backpack.getContainerSize())
                .append("  non-empty=").append(candidateStacks)
                .append("  merchants=").append(merchants.size())
                .append("  quote attempts=").append(quotes)
                .append("  quotes returned=").append(returned).append((char) 10);
        // Reported, never classified on. B_FUNDING answers "can this route be assembled"; these two
        // numbers answer "could the assembled route physically start", which is P0-2's question and
        // not this probe's. Printing them keeps the two from being read as one result.
        out.append("  capacity: free slots=").append(freeSlots(backpack))
                .append("  mergeable emerald stack=").append(hasEmeraldMergeRoom(backpack))
                .append((char) 10);
        if (freeSlots(backpack) == 0 && !hasEmeraldMergeRoom(backpack)) {
            out.append("  CAPACITY WARNING: no free slot and no emerald stack to merge into. A "
                            + "B_FUNDING result here is NOT physically executable - the first SELL "
                            + "would fail NO_ROOM, and classification cannot represent that (P0-2).")
                    .append((char) 10);
        }

        // R10: quotes == 0 means NO economic classification happened, so every bucket is 0 for the
        // trivial reason that nothing was measured. Reporting that as "no useful intersection"
        // would be an architectural conclusion drawn from an empty sample - the exact shape of the
        // level-2 Toolsmith failure, where a probe answered a question it had never asked.
        if (quotes == 0) {
            String why = candidateStacks == 0
                    ? "INVALID FIXTURE - EMPTY/UNSEEDED BACKPACK (no candidate input stacks; the "
                            + "PlayerMob's InventoryCarrier backpack is not the entity slots that "
                            + "`/item replace entity ... inventory.N` addresses)"
                    : merchants.size() == 0
                            ? "INVALID FIXTURE - NO FIXTURE MERCHANTS (no te3-tagged Villager in range)"
                            : "INVALID - candidate stacks and merchants exist but no quote was attempted";
            out.append("  VERDICT: ").append(why).append((char) 10)
                    .append("  No A/B/E architectural conclusion may be drawn from this run.")
                    .append((char) 10);
            source.sendSuccess(() -> Component.literal(out.toString()), false);
            return 0;
        }

        int ab = tally[Bucket.A_DIRECT.ordinal()] + tally[Bucket.B_FUNDING.ordinal()];
        int e = tally[Bucket.E_REPRESENTATION_MISS.ordinal()];
        out.append("  VERDICT: A+B=").append(ab).append("  E=").append(e).append("  -> ");
        if (ab > 0) {
            out.append("REACHABLE - a current V2-TE intersection exists.");
        } else if (e > 0) {
            // Not the same answer as "dead machinery": the market CAN serve the consumer and the
            // demand simply cannot express it. Compatibility code would not fix that.
            out.append("REPRESENTATION BLOCKED - repair demand representation BEFORE V2-TE.");
        } else {
            out.append("NO USEFUL INTERSECTION - V2-TE would presently be dead machinery.");
        }
        out.append("  (D is informational.)").append((char) 10);
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    /**
     * A–E. The order matters: disposition is checked <b>before</b> value, exactly as `W-5` requires,
     * so a lucrative quote on a protected input is `D` and never `A`.
     */
    static Bucket classify(
            MerchantOffer offer, boolean authorized,
            Optional<WorkDemandPolicy.MaterialDemand> demand,
            Optional<WorkDemandPolicy.MaterialDemand> projected,
            Container backpack, ScavengerConfig cfg,
            List<MarketBoard> market, String[] evidence,
            java.util.function.Function<ItemStack, OptionalInt> reservedUnits) {
        if (!authorized) {
            return Bucket.D_ILLEGAL;
        }
        var payout = BuiltInRegistries.ITEM.getKey(offer.getResult().getItem());
        if (demand.map(d -> d.materialKey().equals(payout)).orElse(false)
                || projected.map(d -> d.materialKey().equals(payout)).orElse(false)) {
            return Bucket.A_DIRECT;
        }
        // E: the payout would actually serve the consumer, but the demand names a different item.
        // The explicit case: the torch chain demands CHARCOAL while vanilla villagers trade COAL,
        // and the two are interchangeable as torch fuel. If this bucket is non-empty the fix is a
        // demand-representation question, NOT compatibility code.
        if (demand.map(d -> functionallyEquivalent(d.materialKey(), payout)).orElse(false)) {
            return Bucket.E_REPRESENTATION_MISS;
        }
        if (offer.getResult().is(Items.EMERALD)) {
            // B only if a concrete BUY currently exists for those emeralds to fund.
            // Cross-villager funding, pairwise. V2-E R7 supports a SELL on one merchant funding a
            // BUY on another, and VR-T2 proved it physically: Fletcher 32 sticks -> 1 emerald x4,
            // then Toolsmith 11 emerald -> enchanted iron pickaxe. Searching only the SELLING
            // merchant's own board for that BUY would report exactly that route as C.
            //
            // Pairwise rather than a flattened market: OfferSnapshot.index() is BUYER-LOCAL, and a
            // fake global board would reintroduce the flattened-index defect R8 removed. Each pair
            // is one exact BUY candidate plus this one exact TE SELL candidate.
            final int syntheticIndex = 9_999;
            OfferSnapshot synthetic = OfferSnapshot.of(syntheticIndex, offer);

            for (MarketBoard buyer : market) {
                for (OfferSnapshot buyCandidate : buyer.offers()) {
                    boolean matched = demand.map(d -> {
                        TradeFundingPlanner.FundingTarget target =
                                TradeFundingPlanner.chooseFundingTarget(
                                        projected.orElse(d), List.of(buyCandidate, synthetic),
                                        backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                                        reservedUnits);
                        // R11: actionable(), not merely "a target came back". Runtime #2 produced
                        // 22 oak_log -> 1 emerald against a 13-emerald BUY with 48 logs held:
                        // floor(48/22) = 2 uses = 2 emerald, so the purchase can never complete.
                        // The production contract already encodes this - an unfunded target is
                        // actionable only when sellLeg.fullyFunds(deficit) - so it is reused rather
                        // than re-derived here. B means REACHABLE, not merely quotable.
                        return target != null
                                && target.actionable()
                                && target.buyOffer().rankOrdinal() == buyCandidate.rankOrdinal()
                                && target.sellLeg() != null
                                && target.sellLeg().offer().rankOrdinal() == syntheticIndex;
                    }).orElse(false);
                    if (matched) {
                        evidence[0] = describe(buyCandidate.costA()) + " -> "
                                + describe(buyCandidate.result()) + "   BUY @" + buyer.label();
                        return Bucket.B_FUNDING;
                    }
                }
            }
            return Bucket.C_IRRELEVANT;
        }
        return Bucket.C_IRRELEVANT;
    }

    /** Buyer-LOCAL indexes preserved; never flattened across merchants. */
    private static List<MarketBoard> boardsOf(
            List<net.minecraft.world.entity.npc.Villager> merchants) {
        List<MarketBoard> boards = new ArrayList<>();
        for (net.minecraft.world.entity.npc.Villager merchant : merchants) {
            List<OfferSnapshot> offers = new ArrayList<>();
            List<MerchantOffer> live = merchant.getOffers();
            for (int i = 0; i < live.size(); i++) {
                offers.add(OfferSnapshot.of(i, live.get(i)));
            }
            boards.add(new MarketBoard(id(merchant), offers));
        }
        return boards;
    }

    /** Interchangeable for the consumer that raised the demand. Deliberately tiny and explicit. */
    private static boolean functionallyEquivalent(
            net.minecraft.resources.ResourceLocation demanded,
            net.minecraft.resources.ResourceLocation payout) {
        return demanded.equals(BuiltInRegistries.ITEM.getKey(Items.CHARCOAL))
                && payout.equals(BuiltInRegistries.ITEM.getKey(Items.COAL));
    }

    private static int disposable(ItemStack input, Container backpack, ScavengerConfig cfg) {
        OptionalInt reserved = SellReserveModel.reservedUnits(input, backpack, cfg);
        if (reserved.isEmpty()) {
            return 0;
        }
        return Math.max(0, ScavengerCrafting.count(backpack, input.getItem()) - reserved.getAsInt());
    }

    private static String describe(ItemStack stack) {
        return stack.getCount() + "x "
                + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static String id(AbstractVillager merchant) {
        return merchant instanceof net.minecraft.world.entity.npc.Villager villager
                ? BuiltInRegistries.VILLAGER_PROFESSION
                        .getKey(villager.getVillagerData().getProfession()).getPath()
                : "wandering_trader";
    }

    private static Mob nearestScavenger(CommandSourceStack source) {
        return source.getLevel().getEntitiesOfClass(Mob.class,
                        new AABB(net.minecraft.core.BlockPos.containing(source.getPosition()))
                                .inflate(RADIUS),
                        m -> m.getTags().contains("te3_mob")
                                && PlayerMobs.isPlayerMob(m) && PlayerMobs.backpack(m) != null)
                .stream().findFirst().orElse(null);
    }
}
