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
                        .then(Commands.literal("scan").executes(c -> scan(c.getSource())))
                        .then(Commands.literal("reset").executes(c -> {
                            indexed = false;
                            coldIndexNanos = warmIndexNanos = -1L;
                            c.getSource().sendSuccess(() -> Component.literal(
                                    "[TE3] probe state reset (TE's own index is not cleared)"), false);
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

    private enum Bucket { A_DIRECT, B_FUNDING, C_IRRELEVANT, D_ILLEGAL, E_REPRESENTATION_MISS }

    private static int scan(CommandSourceStack source) {
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

        List<AbstractVillager> merchants = level.getEntitiesOfClass(AbstractVillager.class,
                new AABB(mob.blockPosition()).inflate(RADIUS));
        List<String> lines = new ArrayList<>();
        int[] tally = new int[Bucket.values().length];
        long quoteNanos = 0L;
        int quotes = 0;

        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack input = backpack.getItem(slot);
            if (input.isEmpty()) {
                continue;
            }
            boolean authorized = disposable(input, backpack, cfg) > 0;
            for (AbstractVillager merchant : merchants) {
                long t0 = System.nanoTime();
                Optional<MerchantOffer> quoted =
                        OfferQuoter.quote(merchant, input, merchant.getOffers());
                quoteNanos += System.nanoTime() - t0;
                quotes++;
                if (quoted.isEmpty()) {
                    continue;
                }
                MerchantOffer offer = quoted.get();
                Bucket bucket = classify(offer, authorized, demand, projected, backpack, cfg);
                tally[bucket.ordinal()]++;
                if (bucket != Bucket.C_IRRELEVANT) {
                    lines.add("  " + bucket + "  " + describe(offer.getCostA())
                            + " -> " + describe(offer.getResult())
                            + "   @" + id(merchant));
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
        out.append("  VERDICT: A+B = ")
                .append(tally[Bucket.A_DIRECT.ordinal()] + tally[Bucket.B_FUNDING.ordinal()])
                .append(tally[Bucket.A_DIRECT.ordinal()] + tally[Bucket.B_FUNDING.ordinal()] == 0
                        ? "  -> NO REACHABLE INTERSECTION. Do not implement V2-TE." : "")
                .append('\n');
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    /**
     * A–E. The order matters: disposition is checked <b>before</b> value, exactly as `W-5` requires,
     * so a lucrative quote on a protected input is `D` and never `A`.
     */
    private static Bucket classify(
            MerchantOffer offer, boolean authorized,
            Optional<WorkDemandPolicy.MaterialDemand> demand,
            Optional<WorkDemandPolicy.MaterialDemand> projected,
            Container backpack, ScavengerConfig cfg) {
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
            boolean fundable = demand
                    .map(d -> TradeFundingPlanner.chooseFundingTarget(
                            projected.orElse(d), List.of(OfferSnapshot.of(0, offer)), backpack,
                            ItemStack.EMPTY, ItemStack.EMPTY,
                            m -> SellReserveModel.reservedUnits(m, backpack, cfg)) != null)
                    .orElse(false);
            return fundable ? Bucket.B_FUNDING : Bucket.C_IRRELEVANT;
        }
        return Bucket.C_IRRELEVANT;
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
                        m -> PlayerMobs.isPlayerMob(m) && PlayerMobs.backpack(m) != null)
                .stream().findFirst().orElse(null);
    }
}
