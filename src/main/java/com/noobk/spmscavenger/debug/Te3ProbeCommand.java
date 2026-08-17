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
                        .then(Commands.literal("fixture")
                                .executes(c -> fixture(c.getSource())))
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
            default -> {
                source.sendFailure(Component.literal(
                        "[TE3] unknown scenario - use iron | torch | protected | funding"));
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

    private static boolean listsIronPickaxe(net.minecraft.world.entity.npc.Villager villager) {
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.getResult().is(Items.IRON_PICKAXE)) {
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
                                && target.buyOffer().index() == buyCandidate.index()
                                && target.sellLeg() != null
                                && target.sellLeg().offer().index() == syntheticIndex;
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
