package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>TEMPORARY V2-H PROOF SUPPORT — remove after VR-T2 is captured.</b>
 *
 * <p>Records the <i>semantic</i> transitions of one VR-T2 run. Deliberately <b>not</b> a tick log:
 * an entry appears when the story changes, so the readout is the proof narrative rather than a
 * transcript to grep.
 *
 * <h2>Derived, never injected</h2>
 *
 * Every transition is computed from the same read-only state {@code status} reads. Nothing in this
 * class is called from production, so the trade path is byte-identical whether or not the proof
 * harness exists — which matters because the harness is trying to prove that path unaided.
 *
 * <p>The cost is honest and worth stating: <b>sampling can miss a transition that occurs and reverts
 * inside one window</b>, and it attributes a change to the sample that observed it rather than the
 * tick that caused it. For the transitions VR-T2 cares about — all of which persist — that is
 * acceptable. It would not be acceptable for anything transient.
 *
 * <h2>What it must never do</h2>
 *
 * Observe only. No publishing exhaustion, no opening or advancing a chain, no mutating offer uses,
 * no forcing a transaction, no closing the consumer.
 */
public final class Vrt2Trace {

    private static final int MAX_ENTRIES = 128;
    private static final int SAMPLE_INTERVAL_TICKS = 5;

    private static final List<String> ENTRIES = new ArrayList<>();
    private static UUID armedMob;
    private static int observedPrice;
    private static BlockPos anchor;
    private static int episodeBaseline;
    private static long nextSampleTick;

    // Last observed values, so only CHANGES are recorded.
    private static String lastRouteStatus;
    private static String lastPurchaseTarget;
    private static int lastEmeralds = Integer.MIN_VALUE;
    private static int lastSticks = Integer.MIN_VALUE;
    private static int lastIronPickaxes = Integer.MIN_VALUE;
    private static int lastEpisodes = Integer.MIN_VALUE;
    private static boolean consumerClosed;
    private static int sellCount;

    private Vrt2Trace() {
    }

    public static void arm(UUID mobId, int price, BlockPos settlementAnchor, ServerLevel level) {
        ENTRIES.clear();
        armedMob = mobId;
        observedPrice = price;
        anchor = settlementAnchor;
        lastRouteStatus = null;
        lastPurchaseTarget = null;
        lastEmeralds = lastSticks = lastIronPickaxes = Integer.MIN_VALUE;
        consumerClosed = false;
        sellCount = 0;
        nextSampleTick = 0L;
        episodeBaseline = episodeCount(level, mobId, settlementAnchor);
        lastEpisodes = episodeBaseline;
        record("T0 armed — price " + price + ", episode baseline " + episodeBaseline);
    }

    public static void disarm() {
        armedMob = null;
        ENTRIES.clear();
    }

    public static boolean armed() {
        return armedMob != null;
    }

    /** Called from the server tick hook only while armed. Pure observation. */
    public static void sample(ServerLevel level) {
        if (armedMob == null || level.getGameTime() < nextSampleTick) {
            return;
        }
        nextSampleTick = level.getGameTime() + SAMPLE_INTERVAL_TICKS;

        List<Mob> found = level.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(BlockPos.ZERO).inflate(3.0E7D),
                m -> armedMob.equals(m.getUUID()));
        if (found.isEmpty()) {
            return;
        }
        final Mob mob = found.get(0);
        final Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return;
        }
        final ScavengerConfig cfg = ScavengerConfig.get();
        Optional<WorkDemandPolicy.MaterialDemand> demand = WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg)
                .map(WorkDemandPolicy.WorkDemand::payload);

        // Route feasibility - the UNKNOWN -> INFEASIBLE transition VR-T2 exists to witness.
        // peekStatus, never status: `status` clears exhaustion evidence on positive progress, so a
        // 5-tick sampler polling it would let ARMING THE OBSERVER alter the route arbitration under
        // test. The observer must be unable to change the outcome it reports.
        String route = demand.map(d -> ExistingRouteFeasibility.peekStatus(level, mob.getUUID(), d,
                backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg).name()).orElse("NONE");
        if (!route.equals(lastRouteStatus)) {
            if ("INFEASIBLE".equals(route) && "UNKNOWN".equals(lastRouteStatus)) {
                record("gather scan completed empty — route UNKNOWN -> INFEASIBLE, TRADE admissible");
            } else {
                record("route status " + lastRouteStatus + " -> " + route);
            }
            lastRouteStatus = route;
        }

        int emeralds = ScavengerCrafting.count(backpack, Items.EMERALD);
        int sticks = ScavengerCrafting.count(backpack, Items.STICK);
        int picks = ScavengerCrafting.count(backpack, Items.IRON_PICKAXE);

        // A SELL is sticks down and emeralds up in the same window.
        if (lastSticks != Integer.MIN_VALUE && sticks < lastSticks && emeralds > lastEmeralds) {
            sellCount++;
            int shortfall = observedPrice - emeralds;
            record("SELL #" + sellCount + " — sticks " + lastSticks + " -> " + sticks
                    + ", emeralds " + lastEmeralds + " -> " + emeralds
                    + (shortfall <= 0
                            ? "  [deficit closed: SELL_TO_FUND -> BUY_TARGET]"
                            : "  [still " + shortfall + " short]"));
        }
        // A BUY is emeralds down and the tool present.
        if (lastIronPickaxes != Integer.MIN_VALUE && picks > lastIronPickaxes) {
            record("BUY — paid " + (lastEmeralds - emeralds) + " emerald, received iron_pickaxe"
                    + " (expected live price " + observedPrice + ")");
        }
        lastEmeralds = emeralds;
        lastSticks = sticks;
        lastIronPickaxes = picks;

        String target = demand.map(d -> d.materialKey().toString()).orElse("<none>");
        if (!target.equals(lastPurchaseTarget)) {
            record("source demand -> " + target);
            lastPurchaseTarget = target;
        }
        if (demand.isEmpty() && !consumerClosed && picks > 0) {
            consumerClosed = true;
            record("consumer CLOSED — iron_pickaxe_upgrade no longer demanded");
        }

        int episodes = episodeCount(level, mob.getUUID(), anchor);
        if (episodes != lastEpisodes) {
            record("relationship episode emitted — total " + episodes
                    + " (baseline " + episodeBaseline + ")");
            lastEpisodes = episodes;
        }
        if (consumerClosed && ENTRIES.stream().noneMatch(e -> e.contains("VERDICT"))) {
            int earned = lastEpisodes - episodeBaseline;
            record("VERDICT " + (sellCount >= 1 && picks > 0 && earned == 1 ? "PASS" : "FAIL")
                    + " — sells=" + sellCount + " pickaxes=" + picks
                    + " episodes=" + earned + " (expected exactly 1)");
        }
    }

    static String chainAndEpisodeReadout(ServerLevel level, Mob mob) {
        int episodes = episodeCount(level, mob.getUUID(), anchor);
        return "  episodes      = " + episodes + "  (baseline " + episodeBaseline + ")\n"
                + "  sells seen    = " + sellCount + "\n"
                + "  armed         = " + armed() + "\n";
    }

    static String readout() {
        if (armedMob == null) {
            return "[VR-T2] trace — not armed. Run `/spmscavenger debug vrt2 setup` first.";
        }
        return "[VR-T2] trace (TEMPORARY V2-H PROOF SUPPORT)\n  " + String.join("\n  ", ENTRIES);
    }

    private static int episodeCount(ServerLevel level, UUID mobId, BlockPos settlementAnchor) {
        if (settlementAnchor == null) {
            return 0;
        }
        return VillageMemorySavedData.get(level).peek(mobId)
                .flatMap(memory -> memory.relationshipAt(settlementAnchor))
                .map(SettlementRelationship::tradeEpisodeCount)
                .orElse(0);
    }

    private static void record(String entry) {
        if (ENTRIES.size() >= MAX_ENTRIES) {
            return;
        }
        ENTRIES.add(entry);
    }
}
