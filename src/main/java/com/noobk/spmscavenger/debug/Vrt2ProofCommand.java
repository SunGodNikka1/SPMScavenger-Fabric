package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.SettlementRelationshipService;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence;
import com.noobk.spmscavenger.village.trade.TradePurchaseProjection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * <b>TEMPORARY V2-H PROOF SUPPORT — remove after VR-T2 is captured.</b>
 *
 * <p>{@code /spmscavenger debug vrt2 setup|status|trace|reset}. Server-side only; every branch
 * requires operator permission and does nothing on a client.
 *
 * <h2>The boundary this class must not cross</h2>
 *
 * The whole value of VR-T2 is that the mob <b>earns</b> its trade route. So this command may
 * establish <i>preconditions</i> and <i>observe</i>, and may never supply an answer:
 *
 * <pre>
 * ALLOWED   read the exact fixture villagers' vanilla offers, derive E, seed inventory,
 *           assert exhaustion evidence is absent, report state
 *
 * FORBIDDEN publish RouteExhaustionEvidence      (trade would authorise itself)
 *           open or advance a TradeChainPlan     (the chain must be the mob's)
 *           mutate MerchantOffer uses            (the economy stays vanilla)
 *           force a transaction
 *           close the consumer
 * </pre>
 *
 * <h2>Why the trace has no production hooks</h2>
 *
 * Transitions are <b>derived by polling the same read-only state {@code status} reads</b>, not
 * emitted from instrumented production call sites. Threading a recorder through the executor would
 * make the proof harness a participant in the thing it is proving, and would leave debug scaffolding
 * inside the trade path after VR-T2 is done. Polling costs a little fidelity — a transition that
 * happens and reverts inside one sample window is missed — and buys a production path that is
 * byte-identical whether or not this command exists.
 *
 * <p>Offers are read through vanilla {@code getOffers()} rather than {@code VillagerTradeAdapter},
 * deliberately: an oracle sharing an adapter with the system under test would cancel out a bug in
 * that adapter on both sides.
 */
public final class Vrt2ProofCommand {

    /** Sticks for exactly four authorised Fletcher sales, plus the live craft-chain reserve. */
    private static final int FIXTURE_STICKS = 4 * 32 + 3;
    /** The mob starts exactly this many emeralds short, whatever price the Toolsmith rolled. */
    private static final int FIXTURE_EMERALD_SHORTFALL = 4;
    private static final double FIXTURE_RADIUS = 24.0D;

    private Vrt2ProofCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger")
                .then(Commands.literal("debug")
                        .then(Commands.literal("vrt2")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("setup")
                                        .executes(ctx -> run(ctx.getSource(), Action.SETUP)))
                                .then(Commands.literal("status")
                                        .executes(ctx -> run(ctx.getSource(), Action.STATUS)))
                                .then(Commands.literal("trace")
                                        .executes(ctx -> run(ctx.getSource(), Action.TRACE)))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> run(ctx.getSource(), Action.RESET))))));
    }

    private enum Action { SETUP, STATUS, TRACE, RESET }

    private static int run(CommandSourceStack source, Action action) {
        ServerLevel level = source.getLevel();
        Optional<Mob> mob = nearestScavenger(source);
        if (mob.isEmpty() && action != Action.TRACE) {
            source.sendFailure(Component.literal(
                    "[VR-T2] no PlayerMob within " + (int) FIXTURE_RADIUS + " blocks"));
            return 0;
        }
        return switch (action) {
            case SETUP -> setup(source, level, mob.orElseThrow());
            case STATUS -> status(source, level, mob.orElseThrow());
            case TRACE -> trace(source);
            case RESET -> reset(source, mob.orElse(null));
        };
    }

    // ------------------------------------------------------------------ setup

    /**
     * Establish T0. Reads live vanilla offers, derives {@code E}, seeds {@code E - 4} emeralds.
     *
     * <p>Nothing learned here is handed to the mob: no offer snapshot, no offer index, no villager
     * reference, no price, no funding decision. The AI must rediscover all of it.
     */
    private static int setup(CommandSourceStack source, ServerLevel level, Mob mob) {
        Optional<Villager> smith = uniqueMerchant(level, mob, "toolsmith");
        Optional<Villager> fletch = uniqueMerchant(level, mob, "fletcher");
        if (smith.isEmpty() || fletch.isEmpty()) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED - need exactly one "
                    + "toolsmith and one fletcher in range; an ambiguous fixture cannot say "
                    + "which merchant the mob traded with"));
            return 0;
        }
        Optional<MerchantOffer> toolsmith = offerOf(smith.get(),
                offer -> offer.getResult().is(Items.IRON_PICKAXE));
        Optional<MerchantOffer> fletcher = offerOf(fletch.get(),
                offer -> offer.getCostA().is(Items.STICK) && offer.getResult().is(Items.EMERALD));

        if (toolsmith.isEmpty() || fletcher.isEmpty()) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED — missing "
                    + (toolsmith.isEmpty() ? "Toolsmith iron_pickaxe offer " : "")
                    + (fletcher.isEmpty() ? "Fletcher stick offer" : "")));
            return 0;
        }
        int price = toolsmith.get().getCostA().getCount();
        int fletcherRemaining = fletcher.get().getMaxUses() - fletcher.get().getUses();
        if (fletcherRemaining < FIXTURE_EMERALD_SHORTFALL) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED — Fletcher has "
                    + fletcherRemaining + " uses left, fixture needs "
                    + FIXTURE_EMERALD_SHORTFALL));
            return 0;
        }
        if (price <= FIXTURE_EMERALD_SHORTFALL) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED — price " + price
                    + " <= shortfall " + FIXTURE_EMERALD_SHORTFALL + "; no SELL would be forced"));
            return 0;
        }

        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED — no backpack"));
            return 0;
        }
        backpack.clearContent();
        backpack.setItem(0, new ItemStack(Items.EMERALD, price - FIXTURE_EMERALD_SHORTFALL));
        backpack.setItem(1, new ItemStack(Items.STICK, 64));
        backpack.setItem(2, new ItemStack(Items.STICK, 64));
        backpack.setItem(3, new ItemStack(Items.STICK, FIXTURE_STICKS - 128));
        backpack.setItem(4, new ItemStack(Items.TORCH, 8));
        backpack.setItem(5, new ItemStack(Items.COBBLESTONE, 6));
        mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.STONE_PICKAXE));
        // IRON axe, not stone. `activeIronToolRecipe` is ordered pickaxe-then-axe, so a stone axe
        // means the purchase does not close the consumer - it hands the frontier straight to
        // `iron_axe_upgrade`, and the mob would carry on trading past the five transactions this
        // proof is supposed to bound. Config targets DIAMOND for both by default, so the stone axe
        // stays upgrade-eligible; seeding the iron axe isolates the one consumer VR-T2 is proving
        // without mutating global progression config to make the proof easier.
        mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new ItemStack(Items.IRON_AXE));

        // The route must be earned. Clearing rather than publishing is the whole point.
        RouteExhaustionEvidence.clear(mob.getUUID());

        // Oracle capture. Held privately by the harness: a uses-delta or a component comparison
        // is meaningless without a baseline. None of it is supplied to the mob.
        int fletcherIndex = fletch.get().getOffers().indexOf(fletcher.get());
        int toolsmithIndex = smith.get().getOffers().indexOf(toolsmith.get());
        if (fletcherIndex < 0 || toolsmithIndex < 0) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED - could not resolve a local "
                    + "offer index; refusing rather than carrying corrupted oracle state forward"));
            return 0;
        }
        // The fixture arithmetic (131 sticks, E-4 emeralds, four sales) is built on these exact
        // semantics, so assert them rather than assuming the vanilla table still matches.
        if (fletcher.get().getCostA().getCount() != 32
                || !fletcher.get().getCostB().isEmpty()
                || fletcher.get().getResult().getCount() != 1) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED - Fletcher quote is not the "
                    + "expected 32 sticks -> 1 emerald with no second cost"));
            return 0;
        }
        if (toolsmith.get().getResult().getCount() != 1
                || !toolsmith.get().getCostA().is(Items.EMERALD)
                || !toolsmith.get().getCostB().isEmpty()) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED - Toolsmith quote is not "
                    + "emeralds-only -> exactly one iron_pickaxe; the E-4 fixture assumes the "
                    + "purchase has no second cost"));
            return 0;
        }

        BlockPos anchor = SettlementRelationshipService
                .nearestSettlementAnchorAt(level, mob.getUUID(), mob.blockPosition()).orElse(null);
        if (anchor == null) {
            source.sendFailure(Component.literal("[VR-T2] setup FAILED - no remembered settlement "
                    + "anchor; the V2-G one-episode requirement cannot be proven without one"));
            return 0;
        }
        // The REAL baseline. A hardcoded 0 here would let a pre-existing episode count as the one
        // this run was supposed to earn - a false PASS with no transaction behind it.
        int episodeBaseline = VillageMemorySavedData.get(level).peek(mob.getUUID())
                .flatMap(memory -> memory.relationshipAt(anchor))
                .map(SettlementRelationship::tradeEpisodeCount)
                .orElse(0);

        Optional<WorkDemandPolicy.MaterialDemand> t0Demand = WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), ScavengerConfig.get())
                .map(WorkDemandPolicy.WorkDemand::payload);
        Vrt2Trace.arm(new Vrt2Oracle(
                mob.getUUID(),
                fletch.get().getUUID(), fletcherIndex, fletcher.get().getUses(),
                fletcher.get().getCostA().copy(), fletcher.get().getCostB().copy(),
                fletcher.get().getResult().copy(),
                smith.get().getUUID(), toolsmithIndex, toolsmith.get().getUses(),
                price, toolsmith.get().getCostA().copy(), toolsmith.get().getCostB().copy(),
                toolsmith.get().getResult().copy(),
                anchor,
                episodeBaseline,
                t0Demand.map(WorkDemandPolicy.MaterialDemand::consumerKey).orElse(null),
                t0Demand.map(d -> ExistingRouteFeasibility.peekStatus(level, mob.getUUID(), d,
                                backpack, mob.getMainHandItem(), mob.getOffhandItem(),
                                ScavengerConfig.get()).name())
                        .orElse("NONE")));

        source.sendSuccess(() -> Component.literal(String.join("\n",
                "[VR-T2] T0 established (TEMPORARY V2-H PROOF SUPPORT)",
                "  toolsmith BUY   = " + price + " emerald -> " + describe(toolsmith.get().getResult()),
                "  fletcher SELL   = " + fletcher.get().getCostA().getCount() + "x stick -> "
                        + fletcher.get().getResult().getCount() + " emerald  (uses left "
                        + fletcherRemaining + ")",
                "  seeded emeralds = " + (price - FIXTURE_EMERALD_SHORTFALL)
                        + "   (exactly " + FIXTURE_EMERALD_SHORTFALL + " short)",
                "  seeded sticks   = " + FIXTURE_STICKS + "   (4 sales + 3 reserve)",
                "  exhaustion      = ABSENT (must be earned by a real gather scan)",
                "  nothing above was handed to the mob")), false);
        return 1;
    }

    // ------------------------------------------------------------------ status

    private static int status(CommandSourceStack source, ServerLevel level, Mob mob) {
        Container backpack = PlayerMobs.backpack(mob);
        ScavengerConfig cfg = ScavengerConfig.get();
        Optional<WorkDemandPolicy.MaterialDemand> sourceDemand = backpack == null
                ? Optional.empty()
                : WorkDemandPolicy.select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg)
                        .map(WorkDemandPolicy.WorkDemand::payload);

        StringBuilder out = new StringBuilder("[VR-T2] status\n");
        out.append("  consumer      = ").append(sourceDemand
                .map(d -> d.consumerKey().toString()).orElse("<none>")).append('\n');
        out.append("  source demand = ").append(sourceDemand
                .map(d -> d.materialKey() + " x" + d.derivedDeficit()).orElse("<none>")).append('\n');

        Optional<ScavengerCrafting.ConsumerRecipeSpec> spec = sourceDemand.flatMap(d ->
                TradePurchaseProjection.activeSpecFor(d, backpack, mob.getMainHandItem(),
                        mob.getOffhandItem(), cfg));
        out.append("  projection    = ").append(sourceDemand
                .flatMap(d -> spec.flatMap(s -> TradePurchaseProjection.ontoOutput(d, s)))
                .map(d -> d.materialKey() + " x" + d.derivedDeficit())
                .orElse("<none>")).append('\n');

        // peek*, never the consumer entry points: `status` clears evidence on positive progress
        // and `exhaustedFor` deletes on expiry/mismatch, so polling them would let observing the
        // run change the arbitration being observed.
        sourceDemand.ifPresent(d -> out.append("  route status  = ")
                .append(ExistingRouteFeasibility.peekStatus(level, mob.getUUID(), d, backpack,
                        mob.getMainHandItem(), mob.getOffhandItem(), cfg))
                .append('\n'));
        out.append("  exhaustion    = ").append(sourceDemand
                .map(d -> RouteExhaustionEvidence.peekExhaustedFor(
                        mob.getUUID(), d, level.getGameTime()) ? "PRESENT" : "absent")
                .orElse("absent")).append('\n');

        out.append("  pick tier     = ").append(backpack == null ? "?" : ToolTierPolicy
                .tierOfPick(backpack, mob.getMainHandItem(), mob.getOffhandItem())).append('\n');
        if (backpack != null) {
            out.append("  emeralds      = ").append(ScavengerCrafting.count(backpack, Items.EMERALD))
                    .append("   sticks = ").append(ScavengerCrafting.count(backpack, Items.STICK))
                    .append("   iron_pickaxe = ")
                    .append(ScavengerCrafting.count(backpack, Items.IRON_PICKAXE)).append('\n');
        }
        appendOfferState(out, level, mob);
        out.append(Vrt2Trace.chainAndEpisodeReadout(level, mob));
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static void appendOfferState(StringBuilder out, ServerLevel level, Mob mob) {
        for (Villager villager : java.util.stream.Stream.of(
                        uniqueMerchant(level, mob, "toolsmith").orElse(null),
                        uniqueMerchant(level, mob, "fletcher").orElse(null))
                .filter(java.util.Objects::nonNull).toList()) {
            for (MerchantOffer offer : villager.getOffers()) {
                boolean buy = offer.getResult().is(Items.IRON_PICKAXE);
                boolean sell = offer.getCostA().is(Items.STICK)
                        && offer.getResult().is(Items.EMERALD);
                if (buy || sell) {
                    out.append("  ").append(buy ? "toolsmith" : "fletcher ").append("     = ")
                            .append(offer.getCostA().getCount()).append("x ")
                            .append(BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem())
                                    .getPath())
                            .append(" -> ").append(describe(offer.getResult()))
                            .append("   uses ").append(offer.getUses()).append('/')
                            .append(offer.getMaxUses()).append('\n');
                }
            }
        }
    }

    // ------------------------------------------------------------------ trace / reset

    private static int trace(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(Vrt2Trace.readout()), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, Mob mob) {
        Vrt2Trace.disarm();
        if (mob != null) {
            RouteExhaustionEvidence.clear(mob.getUUID());
            Container backpack = PlayerMobs.backpack(mob);
            if (backpack != null) {
                backpack.clearContent();
            }
        }
        source.sendSuccess(() -> Component.literal(
                "[VR-T2] reset — instrumentation disarmed, fixture inventory cleared"), false);
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static Optional<Mob> nearestScavenger(CommandSourceStack source) {
        List<Mob> mobs = source.getLevel().getEntitiesOfClass(Mob.class,
                new AABB(net.minecraft.core.BlockPos.containing(source.getPosition()))
                        .inflate(FIXTURE_RADIUS),
                candidate -> PlayerMobs.isPlayerMob(candidate)
                        && PlayerMobs.backpack(candidate) != null);
        return mobs.stream().min((a, b) -> Double.compare(
                a.distanceToSqr(source.getPosition()), b.distanceToSqr(source.getPosition())));
    }

    /** Reads the exact fixture villagers through <b>vanilla</b> offers, never through the adapter. */
    private static Optional<Villager> uniqueMerchant(
            ServerLevel level, Mob mob, String professionPath) {
        List<Villager> matches = level.getEntitiesOfClass(Villager.class,
                new AABB(mob.blockPosition()).inflate(FIXTURE_RADIUS),
                villager -> BuiltInRegistries.VILLAGER_PROFESSION
                        .getKey(villager.getVillagerData().getProfession())
                        .getPath().equals(professionPath));
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static Optional<MerchantOffer> offerOf(
            Villager merchant, java.util.function.Predicate<MerchantOffer> match) {
        for (MerchantOffer offer : merchant.getOffers()) {
            if (match.test(offer)) {
                return Optional.of(offer);
            }
        }
        return Optional.empty();
    }

    private static String describe(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return stack.getCount() + "x " + id
                + (stack.isEnchanted() ? " (enchanted)" : "");
    }
}
