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
        List<String> lines = new ArrayList<>();
        int[] tally = new int[Bucket.values().length];
        long quoteNanos = 0L;
        int quotes = 0;

        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack input = backpack.getItem(slot);
            if (input.isEmpty()) {
                continue;
            }
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
                    lines.add("  " + bucket + "  " + describe(offer.getCostA())
                            + " -> " + describe(offer.getResult())
                            + "   SELL @" + id(merchant)
                            + (evidence[0] == null ? ""
                                    : (char) 10 + "        funds: " + evidence[0]));
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
        out.append(lines.isEmpty() ? "  (no A/B/D/E results)\n"
                : String.join("\n", lines) + "\n");
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
                        return target != null
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
