package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * <b>TEMPORARY V2-H PROOF SUPPORT — remove after VR-T2 is captured.</b>
 *
 * <p>Two separate things, deliberately not mixed:
 *
 * <pre>
 * NARRATIVE   human-readable transitions; may infer from inventory
 * ORACLE      the verdict; compares only exact captured MerchantOffer uses and result components
 * </pre>
 *
 * <p>Inventory inference cannot tell the fixture's Fletcher from any other villager, cannot see a
 * trade made against a different offer, and would count a pickup as a sale. It makes a readable
 * story and an unsound proof, so PASS never consults it.
 *
 * <h2>Failures latch</h2>
 *
 * Once a must-not condition is observed the verdict can never return to PASS. Without that, a run
 * that traded five times and then settled into a correct-looking end state would report PASS on its
 * final sample.
 *
 * <h2>Observe only</h2>
 *
 * {@code peekStatus} throughout — the consumer entry points clear and delete evidence, so polling
 * them would let arming the observer alter the arbitration under test. Nothing here is called from
 * production; the trade path is byte-identical whether or not this exists.
 */
public final class Vrt2Trace {

    private static final int MAX_ENTRIES = 160;

    private static final List<String> ENTRIES = new ArrayList<>();
    /** Latched: once a must-not condition fires it is never removed. */
    private static final Set<String> FAILURES = new LinkedHashSet<>();

    private static Vrt2Oracle oracle;
    private static long nextSampleTick;

    private static String lastRouteStatus;
    private static boolean infeasibleWitnessed;
    private static boolean consumerClosed;
    private static int lastEmeralds = Integer.MIN_VALUE;
    private static int lastSticks = Integer.MIN_VALUE;
    private static int narrativeSells;
    private static int lastFletcherUses;
    private static int lastToolsmithUses;
    private static boolean toolMatched;
    private static int episodesNow;

    private Vrt2Trace() {
    }

    public static void arm(Vrt2Oracle captured) {
        ENTRIES.clear();
        FAILURES.clear();
        oracle = captured;
        nextSampleTick = 0L;
        lastRouteStatus = captured.t0RouteStatus();
        infeasibleWitnessed = false;
        consumerClosed = false;
        lastEmeralds = lastSticks = Integer.MIN_VALUE;
        narrativeSells = 0;
        lastFletcherUses = captured.fletcherBaselineUses();
        lastToolsmithUses = captured.toolsmithBaselineUses();
        toolMatched = false;
        episodesNow = captured.episodeBaseline();
        record("T0 armed");
        if (!Vrt2Oracle.REQUIRED_CONSUMER.equals(captured.t0Consumer())) {
            fail("T0 consumer was " + captured.t0Consumer() + ", not "
                    + Vrt2Oracle.REQUIRED_CONSUMER + " - VR-T2 proves that specific consumer, and "
                    + "closure measured against an arbitrary one proves nothing");
        }
        if (!"UNKNOWN".equals(captured.t0RouteStatus())) {
            fail("T0 route was " + captured.t0RouteStatus() + ", not UNKNOWN - the run cannot "
                    + "prove UNKNOWN -> INFEASIBLE if it did not start UNKNOWN");
        }
    }

    public static void disarm() {
        oracle = null;
        ENTRIES.clear();
        FAILURES.clear();
    }

    public static boolean armed() {
        return oracle != null;
    }

    /** One-tick observation while armed. Pure. */
    public static void sample(ServerLevel level) {
        if (oracle == null || level.getGameTime() < nextSampleTick) {
            return;
        }
        nextSampleTick = level.getGameTime() + 1L;

        if (!(level.getEntity(oracle.mobId()) instanceof Mob mob)) {
            return;
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return;
        }
        final ScavengerConfig cfg = ScavengerConfig.get();
        Optional<WorkDemandPolicy.MaterialDemand> demand = WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg)
                .map(WorkDemandPolicy.WorkDemand::payload);

        observeRoute(level, mob, backpack, cfg, demand);
        observeMerchants(level);
        observeNarrative(backpack);
        observeToolAndClosure(backpack, demand);
        observeEpisodes(level, mob);
    }

    // ------------------------------------------------------------------ observations

    private static void observeRoute(
            ServerLevel level, Mob mob, Container backpack, ScavengerConfig cfg,
            Optional<WorkDemandPolicy.MaterialDemand> demand) {
        String route = demand.map(d -> ExistingRouteFeasibility.peekStatus(level, mob.getUUID(), d,
                backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg).name()).orElse("NONE");
        if (route.equals(lastRouteStatus)) {
            return;
        }
        if ("INFEASIBLE".equals(route) && "UNKNOWN".equals(lastRouteStatus)) {
            infeasibleWitnessed = true;
            record("gather scan completed empty - UNKNOWN -> INFEASIBLE, TRADE admissible");
        } else {
            record("route " + lastRouteStatus + " -> " + route);
        }
        lastRouteStatus = route;
    }

    /** The proof oracle: the exact captured offers, by index, on the exact captured merchants. */
    private static void observeMerchants(ServerLevel level) {
        offerOf(level, oracle.fletcherId(), oracle.fletcherOfferIndex()).ifPresent(offer -> {
            if (!Vrt2Oracle.sameQuote(offer, oracle.fletcherCost(), oracle.fletcherResult())) {
                fail("Fletcher offer#" + oracle.fletcherOfferIndex() + " is no longer the captured "
                        + "quote - its uses can no longer be attributed to the fixture's sale");
                return;
            }
            if (offer.getUses() == lastFletcherUses) {
                return;
            }
            int delta = offer.getUses() - oracle.fletcherBaselineUses();
            record("Fletcher offer uses " + lastFletcherUses + " -> " + offer.getUses()
                    + "  (delta " + delta + ")");
            if (!infeasibleWitnessed) {
                fail("Fletcher traded before UNKNOWN -> INFEASIBLE - trade must not precede the "
                        + "evidence that authorises it");
            }
            if (delta > Vrt2Oracle.EXPECTED_SELLS) {
                fail("Fletcher uses delta " + delta + " exceeds the bounded "
                        + Vrt2Oracle.EXPECTED_SELLS);
            }
            lastFletcherUses = offer.getUses();
        });
        offerOf(level, oracle.toolsmithId(), oracle.toolsmithOfferIndex()).ifPresent(offer -> {
            if (!Vrt2Oracle.sameQuote(offer, offer.getCostA(), oracle.toolsmithResult())
                    || offer.getCostA().getCount() != oracle.price()) {
                fail("Toolsmith offer#" + oracle.toolsmithOfferIndex() + " is no longer the captured "
                        + "quote (price or result changed) - its uses cannot be attributed to the "
                        + "purchase this fixture planned");
                return;
            }
            if (offer.getUses() == lastToolsmithUses) {
                return;
            }
            int delta = offer.getUses() - oracle.toolsmithBaselineUses();
            record("Toolsmith offer uses " + lastToolsmithUses + " -> " + offer.getUses()
                    + "  (delta " + delta + ")");
            if (!infeasibleWitnessed) {
                fail("Toolsmith traded before UNKNOWN -> INFEASIBLE");
            }
            if (delta > Vrt2Oracle.EXPECTED_BUYS) {
                fail("Toolsmith uses delta " + delta + " exceeds the bounded "
                        + Vrt2Oracle.EXPECTED_BUYS);
            }
            lastToolsmithUses = offer.getUses();
        });
    }

    /** Narrative only - never consulted by the verdict. */
    private static void observeNarrative(Container backpack) {
        int emeralds = ScavengerCrafting.count(backpack, Items.EMERALD);
        int sticks = ScavengerCrafting.count(backpack, Items.STICK);
        if (lastSticks != Integer.MIN_VALUE && sticks < lastSticks && emeralds > lastEmeralds) {
            narrativeSells++;
            int shortfall = oracle.price() - emeralds;
            record("  narrative: sale #" + narrativeSells + " sticks " + lastSticks + " -> " + sticks
                    + ", emeralds " + lastEmeralds + " -> " + emeralds
                    + (shortfall <= 0 ? "  [deficit closed]" : "  [" + shortfall + " short]"));
        }
        lastEmeralds = emeralds;
        lastSticks = sticks;
    }

    private static void observeToolAndClosure(
            Container backpack, Optional<WorkDemandPolicy.MaterialDemand> demand) {
        if (!toolMatched) {
            for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
                if (oracle.matchesQuotedTool(backpack.getItem(slot))) {
                    toolMatched = true;
                    record("acquired tool matches the exact quoted Toolsmith result "
                            + "(item and components)");
                    break;
                }
            }
        }
        // The actual claim: THIS consumer stopped being selected. `demand.isEmpty()` would also be
        // true if the mob simply had no work at all, and `activeIronToolRecipe` falls through
        // pickaxe -> axe, so a purchase can change the consumer rather than end it.
        boolean stillOurs = demand.isPresent()
                && demand.get().consumerKey().equals(oracle.t0Consumer());
        if (!stillOurs && !consumerClosed && toolMatched) {
            consumerClosed = true;
            record("consumer CLOSED - " + oracle.t0Consumer() + " is no longer selected");
        }
    }

    private static void observeEpisodes(ServerLevel level, Mob mob) {
        int episodes = episodeCount(level, mob.getUUID(), oracle.settlementAnchor());
        if (episodes == episodesNow) {
            return;
        }
        int delta = episodes - oracle.episodeBaseline();
        record("relationship episode emitted - delta " + delta);
        if (delta > Vrt2Oracle.EXPECTED_EPISODES) {
            fail("relationship episode delta " + delta + " - four sales and one purchase must "
                    + "still teach exactly " + Vrt2Oracle.EXPECTED_EPISODES);
        }
        episodesNow = episodes;
    }

    // ------------------------------------------------------------------ verdict

    private static List<String> unmetRequirements() {
        List<String> unmet = new ArrayList<>();
        if (!infeasibleWitnessed) {
            unmet.add("UNKNOWN -> INFEASIBLE not witnessed");
        }
        int sells = lastFletcherUses - oracle.fletcherBaselineUses();
        if (sells != Vrt2Oracle.EXPECTED_SELLS) {
            unmet.add("Fletcher uses delta " + sells + " != " + Vrt2Oracle.EXPECTED_SELLS);
        }
        int buys = lastToolsmithUses - oracle.toolsmithBaselineUses();
        if (buys != Vrt2Oracle.EXPECTED_BUYS) {
            unmet.add("Toolsmith uses delta " + buys + " != " + Vrt2Oracle.EXPECTED_BUYS);
        }
        if (!toolMatched) {
            unmet.add("acquired tool never matched the exact quoted result");
        }
        if (!consumerClosed) {
            unmet.add(oracle.t0Consumer() + " never stopped being selected");
        }
        int episodes = episodesNow - oracle.episodeBaseline();
        if (episodes != Vrt2Oracle.EXPECTED_EPISODES) {
            unmet.add("episode delta " + episodes + " != " + Vrt2Oracle.EXPECTED_EPISODES);
        }
        return unmet;
    }

    static String readout() {
        if (oracle == null) {
            return "[VR-T2] trace - not armed. Run `/spmscavenger debug vrt2 setup` first.";
        }
        List<String> unmet = unmetRequirements();
        StringBuilder out = new StringBuilder("[VR-T2] trace (TEMPORARY V2-H PROOF SUPPORT)\n");
        out.append(oracle.describe()).append("  --- transitions ---\n  ")
                .append(String.join("\n  ", ENTRIES)).append("\n  --- verdict ---\n");
        if (!FAILURES.isEmpty()) {
            out.append("  FAIL (latched):\n    ").append(String.join("\n    ", FAILURES))
                    .append('\n');
        } else if (!unmet.isEmpty()) {
            out.append("  INCOMPLETE:\n    ").append(String.join("\n    ", unmet)).append('\n');
        } else {
            out.append("  PASS - every VR-T2 requirement met against the captured oracle\n");
        }
        return out.toString();
    }

    static String chainAndEpisodeReadout(ServerLevel level, Mob mob) {
        if (oracle == null) {
            return "  armed         = false\n";
        }
        return "  fletcher uses = " + lastFletcherUses + " (T0 " + oracle.fletcherBaselineUses()
                + ", expect +" + Vrt2Oracle.EXPECTED_SELLS + ")\n"
                + "  toolsmith uses= " + lastToolsmithUses + " (T0 " + oracle.toolsmithBaselineUses()
                + ", expect +" + Vrt2Oracle.EXPECTED_BUYS + ")\n"
                + "  episodes      = " + episodesNow + " (T0 " + oracle.episodeBaseline() + ")\n"
                + "  latched fails = " + FAILURES.size() + "\n";
    }

    // ------------------------------------------------------------------ helpers

    /** Direct UUID resolution - no world-scale entity query. */
    private static Optional<MerchantOffer> offerOf(
            ServerLevel level, UUID villagerId, int offerIndex) {
        if (!(level.getEntity(villagerId) instanceof Villager villager)) {
            return Optional.empty();
        }
        var offers = villager.getOffers();
        return offerIndex >= 0 && offerIndex < offers.size()
                ? Optional.of(offers.get(offerIndex))
                : Optional.empty();
    }

    private static int episodeCount(ServerLevel level, UUID mobId, BlockPos anchor) {
        if (anchor == null) {
            return 0;
        }
        return VillageMemorySavedData.get(level).peek(mobId)
                .flatMap(memory -> memory.relationshipAt(anchor))
                .map(SettlementRelationship::tradeEpisodeCount)
                .orElse(0);
    }

    private static void record(String entry) {
        if (ENTRIES.size() < MAX_ENTRIES) {
            ENTRIES.add(entry);
        }
    }

    /** Latched: a must-not condition, permanent for the run. */
    private static void fail(String reason) {
        if (FAILURES.add(reason)) {
            record("FAIL(latched) " + reason);
        }
    }
}
