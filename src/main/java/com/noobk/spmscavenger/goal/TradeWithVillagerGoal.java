package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.RouteEvidence;
import com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence;
import com.noobk.spmscavenger.village.trade.SellAuthorization;
import com.noobk.spmscavenger.village.trade.SellReserveModel;
import com.noobk.spmscavenger.village.trade.TradeFundingPlanner;
import com.noobk.spmscavenger.village.trade.TradeCandidateRound;
import com.noobk.spmscavenger.village.trade.TradeChainPlan;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy;
import com.noobk.spmscavenger.village.trade.TradeDemandGate;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
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
    /**
     * R5 — V2-D's chain, owned by production.
     *
     * <p>The decision the User posed was Option A (V2-D remains the policy owner) versus Option B
     * (R4's live re-derivation supersedes it). <b>Option A</b>, because the two were not equivalent:
     * R4 independently reproduced external-consumer identity, actual-inventory advancement, deficit
     * re-derivation and stop-when-funded, but it had <b>no hard lifetime at all</b>. A chain that can
     * never complete would have retried until the demand changed. That invariant only exists in
     * {@link TradeChainPlan}, so the abstraction earns its place rather than being duplicated.
     *
     * <p>Deliberately <b>not</b> cleared in {@link #stop()}: a lifetime that resets whenever the goal
     * yields to combat is not a lifetime. It is cleared on termination and on consumer change, which
     * are the events that actually end a chain. RET-1: one nullable field per goal instance, bounded
     * by the mob, with no store to sweep.
     */
    private TradeChainPlan chain;

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
            case TRADED -> continueChain(level);
            case MERCHANT_BUSY, MERCHANT_UNAVAILABLE, OFFER_GONE, OFFER_CHANGED, OUT_OF_STOCK ->
                    reselect(level);
            // CANNOT_AFFORD / NO_ROOM are facts about us, not this villager; another candidate would
            // fail identically, so the round ends rather than churning through the whole village.
            default -> endRound(level);
        }
    }

    /**
     * R4 — a completed trade advances the chain; it does not end it.
     *
     * <h2>Why R3 stopped after one trade</h2>
     *
     * {@code TRADED} cleared the target and returned. With no target {@code canContinueToUse()} is
     * false, so the goal stopped — and a funding SELL, whose entire purpose is to make the following
     * BUY affordable, ended the round immediately before the BUY it paid for. The SELL and BUY halves
     * both existed and could never run in sequence.
     *
     * <h2>Actual inventory is the state transition</h2>
     *
     * Nothing is decremented from a remembered plan. The emeralds are now in the backpack, so
     * re-deriving from scratch — demand, BUY quote, deficit, authorization — produces the next step
     * for free, and produces the <b>right</b> one if the world changed during the trade. A remembered
     * "two sells remaining" counter would be a second source of truth about a quantity the container
     * already states, and Task 50 is what that costs.
     *
     * <h2>The seller is not a failed candidate</h2>
     *
     * {@code reselect()} demotes the villager and consumes its budget, which is correct for a
     * villager that <i>refused</i> us. Demoting the farmer who just bought our wheat would make the
     * one merchant proven to trade with us unreachable for the rest of the round. So the same
     * candidate stays selectable with a fresh approach budget, and only round-level bounds
     * ({@code exhausted}, the cooldown, and the demand itself going away) end the round.
     */
    private void continueChain(ServerLevel level) {
        // Released immediately: the interlock covers an attempt, never a relationship.
        TradeSessionClaimWindow.release(mob.getUUID());
        target = null;
        plannedOffer = null;

        Optional<Candidate> next = authorizedCandidate(level);
        if (next.isPresent()) {
            beginAttempt(level, next.get());
        } else {
            // No further step: either the demand is satisfied, or nothing on offer serves it now.
            endRound(level);
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
        Optional<WorkDemandPolicy.MaterialDemand> demand = WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), ScavengerConfig.get())
                .map(WorkDemandPolicy.WorkDemand::payload);

        // R5, and the reason this method has an effect at all: every path that asks "is there a
        // consumer" funnels through here, so the exhaustion episode's lifetime is bound to the
        // consumer in exactly one place. Scattering the same call across canUse / canContinueToUse /
        // attemptTransaction / authorizedCandidate is how one of them ends up forgotten.
        //
        // Note this keys off the CONSUMER, never off the goal being interrupted: combat preempting
        // the mob leaves the demand standing, so a legitimate completed search survives it.
        RouteExhaustionEvidence.retainOnly(
                mob.getUUID(), demand.orElse(null), level.getGameTime());
        return demand;
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

        // V2-D bridge: choose the BUY quote first, derive its shortfall, then authorize a disposable
        // material to fund it. Without a live deficit every SELL is refused by design, which is why
        // V2-E was previously a direct-BUY subset of the locked architecture.
        TradeFundingPlanner.FundingTarget funding = fundingTarget(demand.get(), offers, backpack);
        TradeEvaluationPolicy.EmeraldDeficit deficit =
                funding == null ? null : funding.deficit();
        // R4: permission reaches the decision. Without this the registrar evaluated every funding
        // SELL against a null authorization and refused it - the SELL half of V2-D existed and was
        // unreachable from production.
        SellAuthorization authorization =
                deficit == null ? null : fundingAuthorization(deficit, offers, backpack);

        // R5: V2-D decides whether this chain may continue and which leg is next. Its verdict is
        // taken before ranking, because a terminated chain must not produce a candidate at all.
        TradeChainPolicy.ChainOutcome outcome =
                advanceChain(level, demand.get(), backpack, funding, authorization, offers);
        if (outcome == null || !outcome.active()) {
            return Optional.empty();
        }
        TradeChainPlan.Step step = outcome.plan().step();
        if (step == TradeChainPlan.Step.SELL_TO_FUND && outcome.sellBlocked()) {
            // Not enough authorized material to close the deficit. A state to report, not a reason
            // to attempt a purchase we cannot pay for.
            return Optional.empty();
        }

        return TradeDemandGate
                .authorize(demand.get(),
                        new RouteEvidence(
                                existingFeasible, offers, affordable, deficit, authorization))
                .flatMap(decision -> decision.rankedOffers().stream()
                        .map(evaluation -> owners.get(evaluation.offerIndex()))
                        .filter(java.util.Objects::nonNull)
                        // The chain's step, not the ranking, decides which leg is legal now. Without
                        // this a BUY the mob cannot yet afford and a SELL that funds it are ranked
                        // against each other in one list, and the order is V2-B's opinion rather
                        // than the chain's sequence.
                        .filter(candidate -> isFundingSell(candidate.offer())
                                == (step == TradeChainPlan.Step.SELL_TO_FUND))
                        .filter(candidate -> VillagerTradeAdapter.canAfford(
                                backpack, candidate.offer()))
                        .findFirst());
    }

    private static boolean isFundingSell(OfferSnapshot offer) {
        return offer.result().is(net.minecraft.world.item.Items.EMERALD);
    }

    /**
     * R5 — open, advance or terminate the V2-D chain that owns this purchase.
     *
     * <h2>Why V2-D owns it rather than the executor</h2>
     *
     * R4's {@code continueChain} re-derived everything from actual inventory, which independently
     * reproduced most of V2-D's locked invariants — external consumer identity, inventory-driven
     * advancement, stop-when-funded. It reproduced <b>all but one</b>: there was no hard lifetime, so
     * a chain that could never complete would retry until the demand happened to change. Rather than
     * keep two encodings of the same state machine and add expiry to the second one, the executor
     * supplies facts and {@link TradeChainPolicy} decides.
     *
     * <h2>Facts, never fetched inside the policy</h2>
     *
     * Everything below is read here and passed in, which is what keeps the policy pure and testable.
     *
     * <p>{@code desiredQuantity} is the deficit at the moment the chain opened — the amount this
     * chain exists to acquire. Holding that many later means it was obtained elsewhere (mined,
     * looted, gifted) and the chain stops, which is exactly V2-D's {@code TARGET_OBTAINED_ELSEWHERE}.
     */
    private TradeChainPolicy.ChainOutcome advanceChain(
            ServerLevel level, WorkDemandPolicy.MaterialDemand demand, Container backpack,
            TradeFundingPlanner.FundingTarget funding, SellAuthorization authorization,
            List<OfferSnapshot> offers) {
        if (funding == null) {
            // Nothing on offer serves the demand, so there is no purchase for a chain to fund.
            chain = null;
            return null;
        }
        long now = level.getGameTime();
        if (chain == null
                || !chain.consumerKey().equals(demand.consumerKey())
                || !chain.desiredOutput().equals(demand.materialKey())) {
            chain = TradeChainPlan.forConsumer(
                    demand.consumerKey(), demand.materialKey(), demand.derivedDeficit(), now);
        }

        TradeChainPolicy.ChainOutcome outcome = TradeChainPolicy.evaluate(
                chain,
                new TradeChainPolicy.ChainFacts(
                        true,
                        ScavengerCrafting.count(
                                backpack, BuiltInRegistries.ITEM.get(demand.materialKey())),
                        ScavengerCrafting.count(backpack, net.minecraft.world.item.Items.EMERALD),
                        funding.buyOffer().costA().getCount(),
                        emeraldsPerSellUse(authorization, offers),
                        authorization == null ? 0 : authorization.disposableUnits()),
                now);

        // Terminated chains are dropped rather than left standing: an expired or ownerless plan that
        // survives is the stale-ownership shape this slice keeps having to remove.
        chain = outcome.active() ? outcome.plan() : null;
        return outcome;
    }

    /** Emeralds the authorized funding SELL yields per use, or {@code 0} when none is authorized. */
    private static int emeraldsPerSellUse(
            SellAuthorization authorization, List<OfferSnapshot> offers) {
        if (authorization == null || authorization.isEmpty()) {
            return 0;
        }
        for (OfferSnapshot offer : offers) {
            if (isFundingSell(offer) && offer.isTradeable() && !offer.outOfStock()
                    && authorization.permits(offer.costA())) {
                return offer.result().getCount();
            }
        }
        return 0;
    }

    /** Trade may displace working progression only on positively proven infeasibility. */
    private boolean existingRouteInfeasible(
            ServerLevel level, WorkDemandPolicy.MaterialDemand demand) {
        return ExistingRouteFeasibility.tradeMayDisplace(
                level, mob.getUUID(), demand, PlayerMobs.backpack(mob),
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
    /**
     * R3: the deficit is derived from the BUY quote this iteration will actually serve.
     *
     * <p>R2 took the cheapest emerald cost found anywhere, independently of the offer ranking would
     * choose — Task 50's "right arithmetic against the wrong offer", one layer earlier. The mob would
     * have sold exactly enough for a purchase it was not going to make.
     */
    private TradeFundingPlanner.FundingTarget fundingTarget(
            WorkDemandPolicy.MaterialDemand demand, List<OfferSnapshot> offers, Container backpack) {
        return TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack);
    }

    /**
     * R3: which disposable material may fund that shortfall, delegated to the permission layer.
     *
     * <p>R4: reserves come from {@link SellReserveModel}, which reads the existing craft chain — so
     * a log the torch chain has claimed is not spare merely because a villager will pay for it, and
     * a material nobody has modelled is refused rather than assumed free. R3 passed
     * {@code material -> 0} here, which made the permission layer unanimously permissive.
     */
    private SellAuthorization fundingAuthorization(
            TradeEvaluationPolicy.EmeraldDeficit deficit, List<OfferSnapshot> offers,
            Container backpack) {
        ScavengerConfig cfg = ScavengerConfig.get();
        return TradeFundingPlanner.authorizeFunding(
                deficit, offers, backpack, mob.getMainHandItem(), mob.getOffhandItem(),
                material -> SellReserveModel.reservedUnits(material, backpack, cfg));
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
