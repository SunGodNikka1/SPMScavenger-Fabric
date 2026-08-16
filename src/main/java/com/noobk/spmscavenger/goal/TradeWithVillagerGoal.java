package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.RouteEvidence;
import com.noobk.spmscavenger.village.trade.TradeCandidateRound;
import com.noobk.spmscavenger.village.trade.TradeDemandGate;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * V2-E — walk to a villager and complete one trade for a demand that already exists.
 *
 * <h2>Priority 3, MOVE + LOOK</h2>
 *
 * The deliberate-work band, beside gather / smelt / craft-torches. Combat (0–2), shelter (2) and
 * commands preempt it; {@code FriendlyGreetGoal} at priority <b>1</b> would too, which is why
 * {@link TradeSessionClaimWindow} exists — see its javadoc for why a P3 goal cannot defend itself by
 * priority and must instead suppress the one greet that would preempt it.
 *
 * <h2>Nothing survives the walk</h2>
 *
 * Every fact the decision rested on is recomputed at the attempt boundary: the live demand, this
 * offer's affordability, output capacity, merchant occupancy and availability. Two prior slices
 * handed this one that obligation explicitly — V2-C's {@code paymentAffordable} and V2-D's
 * {@code emeraldsPerSellUse} are both caller-supplied facts that can describe a <i>different offer</i>
 * than the one finally attempted. <b>Planning permission does not authorize execution.</b>
 *
 * <h2>A dead candidate is not a dead route</h2>
 *
 * Asleep, occupied by a player, offer withdrawn, path unreachable — each demotes the candidate inside
 * the current {@link TradeCandidateRound} and the next one is tried. Only an exhausted round yields
 * to the cooldown. See that class for why a "decision cycle" was too short a unit.
 */
public class TradeWithVillagerGoal extends Goal {

    /** Bounded, and deliberately smaller than the crafting-table scan — this one walks to entities. */
    public static final double CANDIDATE_RADIUS = 16.0D;

    /** Interaction range for the transaction itself. */
    private static final double INTERACT_RANGE_SQR = 9.0D;

    private final Mob mob;
    private final double speed;
    private final TradeCandidateRound round = new TradeCandidateRound();

    private Villager target;
    private OfferSnapshot plannedOffer;
    /** The consumer that authorized the attempt in progress. Identity, not a snapshot of demand. */
    private ResourceLocation attemptConsumer;
    private ResourceLocation attemptMaterial;
    private int repathCooldown;

    public TradeWithVillagerGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        if (round.coolingDown(now)) {
            return false;
        }
        return authorizedCandidate(level).isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        // The demand can vanish mid-walk - someone else smelted the iron, the consumer completed.
        // Checked here rather than only at commit so the mob stops walking toward a purchase nobody
        // needs, instead of arriving and then discovering it.
        // Cheap continuation: same consumer, route ownership, physical legality. No villager
        // rescan - exact offer, affordability and capacity stay at the transaction boundary.
        Optional<WorkDemandPolicy.MaterialDemand> demand = liveDemand(level);
        if (demand.isEmpty() || target == null || !VillagerTradeAdapter.available(target)) {
            return false;
        }
        // The SAME consumer, not merely some consumer. Another activity can satisfy the iron
        // frontier mid-walk while the torch chain becomes the selected demand; "a demand exists"
        // would then keep the mob walking to execute an iron offer nobody wants.
        if (!sameAttemptConsumer(demand.get())) {
            return false;
        }
        return existingRouteInfeasible(level, demand.get());
    }

    @Override
    public void start() {
        if (mob.level() instanceof ServerLevel level) {
            authorizedCandidate(level).ifPresent(candidate -> beginAttempt(level, candidate));
        }
    }

    /**
     * Unconditional cleanup boundary.
     *
     * <p>Not merely one of several expected exits: combat, shelter, commands and goal removal all
     * arrive here, and a claim that outlived its goal would suppress greeting for a villager nobody
     * is trading with. Releasing blindly makes leaked ownership impossible rather than unlikely.
     */
    @Override
    public void stop() {
        TradeSessionClaimWindow.release(mob.getUUID());
        round.clear();
        target = null;
        plannedOffer = null;
        attemptConsumer = null;
        attemptMaterial = null;
        repathCooldown = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (mob.distanceToSqr(target) > INTERACT_RANGE_SQR) {
            approach(level);
            return;
        }
        mob.getNavigation().stop();
        attemptTransaction(level);
    }

    // ------------------------------------------------------------------ approach

    private void approach(ServerLevel level) {
        // Consumed every tick, not only on a navigation refusal: an accepted path that stalls
        // against geometry would otherwise never end the attempt.
        if (round.recordApproachTick()) {
            reselect(level);
            return;
        }
        if (repathCooldown-- > 0) {
            return;
        }
        repathCooldown = 10;
        // Re-issued rather than set once: the target is an entity and it strolls.
        if (!mob.getNavigation().moveTo(target, speed) && round.recordPathFailure()) {
            // Budget spent for THIS candidate. "Unreachable" is a fact about one villager.
            reselect(level);
        }
    }

    // ------------------------------------------------------------------ transaction

    private void attemptTransaction(ServerLevel level) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null || plannedOffer == null) {
            reselect(level);
            return;
        }
        // Availability first: asleep or player-occupied is a temporarily illegal candidate, not a
        // failed transaction, and must demote rather than end the route.
        if (!VillagerTradeAdapter.available(target)) {
            reselect(level);
            return;
        }
        // Consumer identity at the execution boundary, freshly derived. Planning permission does not
        // authorize execution, and that applies to *who wanted this* as much as to affordability.
        Optional<WorkDemandPolicy.MaterialDemand> live = liveDemand(level);
        if (live.isEmpty() || !sameAttemptConsumer(live.get())) {
            endRound(level);
            return;
        }
        VillagerTradeAdapter.TradeResult result =
                VillagerTradeAdapter.performTrade(backpack, target, plannedOffer);

        switch (result) {
            case TRADED -> {
                // Released immediately: the interlock covers an attempt, never a relationship.
                TradeSessionClaimWindow.release(mob.getUUID());
                target = null;
                plannedOffer = null;
            }
            case MERCHANT_BUSY, MERCHANT_UNAVAILABLE, OFFER_GONE, OFFER_CHANGED, OUT_OF_STOCK ->
                    reselect(level);
            // CANNOT_AFFORD / NO_ROOM are facts about us, not this villager; another candidate would
            // fail identically, so the round ends rather than churning through the whole village.
            default -> endRound(level);
        }
    }

    // ------------------------------------------------------------------ candidates

    private void beginAttempt(ServerLevel level, Candidate candidate) {
        target = candidate.villager();
        plannedOffer = candidate.offer();
        attemptConsumer = candidate.consumerKey();
        attemptMaterial = candidate.materialKey();
        round.begin(candidate.villager().getUUID());
        // Claim at attempt start, before WALK can carry us into greet range. A FACE-only claim would
        // depend on winning a race against the priority-1 greet re-evaluating every tick.
        TradeSessionClaimWindow.claim(
                mob.getUUID(), candidate.villager().getUUID(), level.getGameTime());
        repathCooldown = 0;
    }

    private void reselect(ServerLevel level) {
        round.demoteCurrent();
        TradeSessionClaimWindow.release(mob.getUUID());
        target = null;
        plannedOffer = null;
        mob.getNavigation().stop();

        Optional<Candidate> next = authorizedCandidate(level);
        if (next.isPresent()) {
            beginAttempt(level, next.get());
        } else {
            endRound(level);
        }
    }

    private void endRound(ServerLevel level) {
        round.endRound(level.getGameTime());
        TradeSessionClaimWindow.release(mob.getUUID());
        target = null;
        plannedOffer = null;
        mob.getNavigation().stop();
    }

    /**
     * A candidate plus the consumer that authorized it.
     *
     * <p>{@code consumerKey} and {@code materialKey} are the stable identity; the deficit is
     * deliberately absent because it legitimately shrinks during the walk. Without them the goal
     * could prove only that <i>a</i> demand exists, and execute an iron offer for a torch-chain
     * demand that replaced it mid-approach.
     */
    private record Candidate(
            Villager villager,
            OfferSnapshot offer,
            ResourceLocation consumerKey,
            ResourceLocation materialKey) {
    }

    private Optional<WorkDemandPolicy.MaterialDemand> liveDemand(ServerLevel level) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return Optional.empty();
        }
        // Live, never cached: a stale demand would authorize a trade nobody wants.
        // Offhand included: tool ownership is checked across backpack + main hand + offhand, and
        // the 3-arg overload substitutes EMPTY. Dropping the offhand here would let V2-E see a
        // weaker owned tier than the rest of progression and manufacture a demand nobody has.
        return WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), ScavengerConfig.get())
                .map(WorkDemandPolicy.WorkDemand::payload);
    }

    /**
     * Bounded discovery. Offers are inspected only for villagers already selected as candidates —
     * never in a passive sweep, because {@code getOffers()} lazily populates a villager's trades and
     * a broad scan would initialise them across a whole village.
     */
    private Optional<Candidate> authorizedCandidate(ServerLevel level) {
        Optional<WorkDemandPolicy.MaterialDemand> demand = liveDemand(level);
        if (demand.isEmpty()) {
            return Optional.empty();
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return Optional.empty();
        }

        List<Villager> nearby = level.getEntitiesOfClass(
                Villager.class,
                new AABB(mob.blockPosition()).inflate(CANDIDATE_RADIUS),
                villager -> VillagerTradeAdapter.available(villager)
                        && round.available(villager.getUUID()));
        if (nearby.isEmpty()) {
            return Optional.empty();
        }

        // Ranking needs one flat index space across villagers; execution needs the villager's own
        // offer index. Both are kept side by side rather than the real one being re-derived later -
        // matching an offer back by item identity is ambiguous when a villager sells the same item
        // pair at two different counts, and the identity was ours to keep in the first place.
        List<OfferSnapshot> offers = new ArrayList<>();
        java.util.Map<Integer, Candidate> owners = new java.util.HashMap<>();
        int slot = 0;
        for (Villager villager : nearby) {
            for (OfferSnapshot offer : VillagerTradeAdapter.inspectOffers(villager)) {
                offers.add(new OfferSnapshot(slot, offer.costA(), offer.costB(),
                        offer.result(), offer.uses(), offer.maxUses()));
                owners.put(slot, new Candidate(villager, offer,      // offer keeps its REAL index
                        demand.get().consumerKey(), demand.get().materialKey()));
                slot++;
            }
        }

        boolean affordable = offers.stream()
                .anyMatch(offer -> VillagerTradeAdapter.canAfford(backpack, offer));

        // P0/R2: produced, tri-state, and trade proceeds only on positively proven infeasibility.
        boolean existingFeasible = !existingRouteInfeasible(level, demand.get());

        // V2-D: without a live emerald deficit every SELL offer is refused by design, so a funding
        // leg could never be entered and V2-E was a direct-BUY subset of the locked architecture.
        TradeEvaluationPolicy.EmeraldDeficit deficit =
                emeraldDeficitFor(demand.get(), offers, backpack);

        return TradeDemandGate
                .authorize(demand.get(),
                        new RouteEvidence(existingFeasible, offers, affordable, deficit))
                .flatMap(authorization -> authorization.rankedOffers().stream()
                        .map(evaluation -> owners.get(evaluation.offerIndex()))
                        .filter(java.util.Objects::nonNull)
                        .filter(candidate -> VillagerTradeAdapter.canAfford(
                                backpack, candidate.offer()))
                        .findFirst());
    }

    /** Trade may displace working progression only on positively proven infeasibility. */
    private boolean existingRouteInfeasible(
            ServerLevel level, WorkDemandPolicy.MaterialDemand demand) {
        return ExistingRouteFeasibility.tradeMayDisplace(
                level, demand, PlayerMobs.backpack(mob),
                mob.getMainHandItem(), mob.getOffhandItem(), ScavengerConfig.get());
    }

    private boolean sameAttemptConsumer(WorkDemandPolicy.MaterialDemand demand) {
        return attemptConsumer != null
                && attemptConsumer.equals(demand.consumerKey())
                && attemptMaterial != null
                && attemptMaterial.equals(demand.materialKey());
    }

    /**
     * V2-D wiring: the emerald shortfall a BUY leg needs, if any.
     *
     * <p>Derived live from the cheapest matching BUY offer and the emeralds actually held, then
     * handed to {@link RouteEvidence} so V2-B may evaluate SELL legs at all. Without it every SELL
     * offer is {@code NO_CONSUMER_FOR_PAYMENT} by design, which is why V2-E was previously a
     * direct-BUY subset of the locked architecture.
     *
     * <p>The deficit carries the <b>external consumer's</b> key, never a manufactured one: the chain
     * exists to fund that purchase and is attributed to it (V2-D req 2/3).
     */
    private TradeEvaluationPolicy.EmeraldDeficit emeraldDeficitFor(
            WorkDemandPolicy.MaterialDemand demand, List<OfferSnapshot> offers, Container backpack) {
        int cheapestBuyCost = Integer.MAX_VALUE;
        for (OfferSnapshot offer : offers) {
            if (!offer.isTradeable()) {
                continue;
            }
            ResourceLocation resultKey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(offer.result().getItem());
            if (!demand.materialKey().equals(resultKey)) {
                continue;
            }
            if (offer.costA().is(net.minecraft.world.item.Items.EMERALD)) {
                cheapestBuyCost = Math.min(cheapestBuyCost, offer.costA().getCount());
            }
        }
        if (cheapestBuyCost == Integer.MAX_VALUE) {
            return null;   // nothing here is bought with emeralds; no funding leg is implied
        }
        int held = com.noobk.spmscavenger.ScavengerCrafting.count(
                backpack, net.minecraft.world.item.Items.EMERALD);
        int shortfall = cheapestBuyCost - held;
        // Bounded by the purchase. No shortfall means no emerald appetite exists at all (V2-D req 2).
        return shortfall > 0
                ? new TradeEvaluationPolicy.EmeraldDeficit(demand.consumerKey(), shortfall)
                : null;
    }

    /**
     * V2-D wiring: how many successful SELL uses this offer must complete, bounded by the deficit.
     *
     * <p>Derived from the <b>live</b> offer being attempted, per Task 50's handoff — never a value
     * carried from planning. Owning 64 disposable wheat is permission to spend wheat, not a reason to
     * sell 64 of it.
     */
    public static int requiredSellUses(
            TradeEvaluationPolicy.EmeraldDeficit deficit, OfferSnapshot sellOffer) {
        if (deficit == null || sellOffer == null || sellOffer.result().isEmpty()) {
            return 0;
        }
        int perUse = Math.max(1, sellOffer.result().getCount());
        return (deficit.emeraldsNeeded() + perUse - 1) / perUse;
    }
}
