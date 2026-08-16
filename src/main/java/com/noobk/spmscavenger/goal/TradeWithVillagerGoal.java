package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.RouteEvidence;
import com.noobk.spmscavenger.village.trade.TradeCandidateRound;
import com.noobk.spmscavenger.village.trade.TradeDemandGate;
import com.noobk.spmscavenger.village.trade.TradeEvaluation;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
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
        return target != null
                && VillagerTradeAdapter.available(target)
                && liveDemand(level).isPresent();
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

    private record Candidate(Villager villager, OfferSnapshot offer) {
    }

    private Optional<WorkDemandPolicy.MaterialDemand> liveDemand(ServerLevel level) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return Optional.empty();
        }
        // Live, never cached: a stale demand would authorize a trade nobody wants.
        return WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), ScavengerConfig.get())
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

        List<OfferSnapshot> offers = new ArrayList<>();
        java.util.Map<Integer, Villager> owners = new java.util.HashMap<>();
        int slot = 0;
        for (Villager villager : nearby) {
            for (OfferSnapshot offer : VillagerTradeAdapter.inspectOffers(villager)) {
                OfferSnapshot keyed = new OfferSnapshot(slot, offer.costA(), offer.costB(),
                        offer.result(), offer.uses(), offer.maxUses());
                offers.add(keyed);
                owners.put(slot, villager);
                slot++;
            }
        }

        boolean affordable = offers.stream()
                .anyMatch(offer -> VillagerTradeAdapter.canAfford(backpack, offer));

        return TradeDemandGate
                .authorize(demand.get(), RouteEvidence.of(false, offers, affordable))
                .flatMap(authorization -> authorization.rankedOffers().stream()
                        .filter(evaluation -> owners.containsKey(evaluation.offerIndex()))
                        .map(evaluation -> resolve(owners, offers, evaluation))
                        .filter(candidate -> VillagerTradeAdapter.canAfford(
                                PlayerMobs.backpack(mob), candidate.offer()))
                        .findFirst());
    }

    private Candidate resolve(
            java.util.Map<Integer, Villager> owners, List<OfferSnapshot> offers,
            TradeEvaluation evaluation) {
        Villager villager = owners.get(evaluation.offerIndex());
        OfferSnapshot flat = offers.get(evaluation.offerIndex());
        // Re-key to the villager's own offer index, because the adapter re-resolves against that
        // villager's live offer list at commit time.
        UUID id = villager.getUUID();
        int actualIndex = 0;
        List<OfferSnapshot> live = VillagerTradeAdapter.inspectOffers(villager);
        for (OfferSnapshot candidate : live) {
            if (candidate.result().getItem() == flat.result().getItem()
                    && candidate.costA().getItem() == flat.costA().getItem()) {
                actualIndex = candidate.index();
                break;
            }
        }
        return new Candidate(villager, new OfferSnapshot(actualIndex, flat.costA(), flat.costB(),
                flat.result(), flat.uses(), flat.maxUses()));
    }
}
