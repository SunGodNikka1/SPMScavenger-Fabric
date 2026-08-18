package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import com.noobk.spmscavenger.village.SettlementRelationshipService;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.RouteEvidence;
import com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence;
import com.noobk.spmscavenger.village.trade.SellFundingLeg;
import com.noobk.spmscavenger.village.trade.SellReserveModel;
import com.noobk.spmscavenger.village.trade.TradeFundingPlanner;
import com.noobk.spmscavenger.village.trade.TradeAttemptFunding;
import com.noobk.spmscavenger.village.trade.TradePurchaseProjection;
import com.noobk.spmscavenger.village.trade.TradeCandidateRound;
import com.noobk.spmscavenger.village.trade.TradeChainPlan;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy;
import com.noobk.spmscavenger.village.trade.TradeEpisodeLedger;
import com.noobk.spmscavenger.village.trade.TradeDemandGate;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
import net.minecraft.core.BlockPos;
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

    /** R7 — the purchase this attempt funds; null unless a funding SELL is in progress. */
    private TradeAttemptFunding attemptFunding;
    /**
     * D-VR-077 step 3 — the source that owns the offer being attempted, held for the whole walk.
     *
     * <p>Provenance added at selection and dropped when the candidate becomes attempt state would be
     * worse than never adding it: step 4 would infer the source at the execution boundary, which is
     * the inference the decision forbids.
     */
    private com.noobk.spmscavenger.village.trade.TradeSourceKey attemptSource;

    /**
     * V2-G — the settlement this visit's trade episode belongs to, or {@code null} when this round
     * has not yet transacted.
     *
     * <p>This field <b>is</b> the once-per-visit rule (`D-VR-063`). Set at the first successful
     * transaction and cleared when the episode is emitted, so a ten-use chain teaches one village
     * relationship rather than ten, and a round that only walked and failed teaches nothing.
     *
     * <p>Captured at first success rather than at round start on purpose: that is the moment a trade
     * episode demonstrably exists <i>and</i> the mob is provably standing at the villager. A round
     * that opens in one settlement and succeeds in another would otherwise credit the wrong village.
     */
    private BlockPos tradeEpisodeAnchor;

    /**
     * V2-G-R2 — the chain the pending episode <b>belongs to</b>, captured with the anchor.
     *
     * <p>Emission is not simultaneous with the transaction: {@code continueChain} records the anchor,
     * then replans, and replanning can terminate this chain and mint the next one before teardown
     * emits anything. Crediting against the <i>current</i> chain therefore credited whichever chain
     * happened to be live at teardown — the wrong one.
     */
    private TradeChainPlan tradeEpisodeChain;

    /**
     * V2-G-R1 — one relationship episode per {@link TradeChainPlan}, across preemption.
     *
     * <p>The anchor alone bounds credit within one uninterrupted visit, but the chain deliberately
     * survives {@code stop()}, so a combat interruption after a successful SELL would let the same
     * chain earn a second episode when it resumed. Never reset at teardown — only when a genuinely
     * new chain is opened.
     */
    private final TradeEpisodeLedger episodeLedger = new TradeEpisodeLedger();

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
        return authorizedCandidate(level, null).isPresent();
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
            authorizedCandidate(level, null)
                    .ifPresent(attempt -> beginAttempt(level, attempt));
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
        // Unconditional and first. A claim outliving its goal suppresses greeting for a villager
        // nobody is trading with, so nothing - including a throwing episode emit below - may come
        // between stop() and this release.
        TradeSessionClaimWindow.release(mob.getUUID());
        if (mob.level() instanceof ServerLevel level) {
            // V2-G, also here and not only in endRound: a visit interrupted by combat after a
            // successful trade still happened, and the mob should remember the village it traded in.
            // Idempotent by construction - emitTradeEpisode clears the anchor - so the ordinary
            // endRound-then-stop teardown credits once, not twice.
            emitTradeEpisode(level);
        }
        round.clear();
        target = null;
        plannedOffer = null;
        attemptFunding = null;
        attemptSource = null;
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
        // R6 - the boundary that mattered most, and the one R5 left open. Selection authorized this
        // SELL against the inventory of several hundred ticks ago; affordability is the only thing
        // rechecked since. Those are not the same question:
        //
        //   selection   64 sticks, 3 reserved, 61 disposable, 32-stick sale -> authorized
        //   walk        crafting consumes 30 sticks
        //   execution   34 sticks, 3 reserved, 31 disposable
        //               canAfford(32) is TRUE, and the sale spends into the reserve
        //
        // Planning permission does not authorize execution. Re-derived here against inventory as it
        // now stands, and bound to the exact quote being attempted.
        if (isFundingSell(plannedOffer) && !stillAuthorized(backpack)) {
            reselect(level);
            return;
        }
        // Physical legality first, exactly as performTrade ordered it: a dead target ENDS the round,
        // it does not demote a candidate, and collapsing that into the empty-revalidation branch
        // below would quietly turn an endRound into a reselect.
        if (target == null || !target.isAlive()) {
            endRound(level);
            return;
        }
        // D-VR-077 step 5: the source that owns this offer resolves it; the adapter never reads the
        // ref. Empty covers exactly what OFFER_GONE, OFFER_CHANGED and OUT_OF_STOCK covered, and all
        // three already routed to reselect.
        // Fail closed on an unresolvable source: an offer whose source is not installed is not a
        // vanilla offer, and handing it to the board resolver would report "gone" and be diagnosed
        // as a market race rather than a missing registration.
        java.util.Optional<net.minecraft.world.item.trading.MerchantOffer> resolved =
                com.noobk.spmscavenger.village.trade.TradeSources.of(attemptSource)
                        .flatMap(source -> source.revalidate(target, plannedOffer));
        if (resolved.isEmpty()) {
            reselect(level);
            return;
        }
        VillagerTradeAdapter.TradeResult result =
                VillagerTradeAdapter.performResolvedTrade(backpack, target, resolved.get());

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
        // V2-G: the visit has now produced at least one real transaction. First success only - the
        // anchor is not re-resolved on later uses in the same chain, which is what keeps the episode
        // count per visit rather than per click.
        if (tradeEpisodeAnchor == null) {
            tradeEpisodeAnchor = SettlementRelationshipService
                    .nearestSettlementAnchorAt(level, mob.getUUID(), mob.blockPosition())
                    .orElse(null);
            // Captured together and never separately: the anchor says WHERE the episode happened,
            // this says WHICH chain earned it. Reading the chain later reads a different answer.
            tradeEpisodeChain = chain;
        }
        // Captured before the attempt is torn down: the purchase this sale just funded is the one
        // thing the re-centred discovery below cannot rediscover on its own.
        Villager carriedBuyer = attemptFunding == null ? null : attemptFunding.buyer();
        // Released immediately: the interlock covers an attempt, never a relationship.
        TradeSessionClaimWindow.release(mob.getUUID());
        // R6: begin() is idempotent for the candidate already in progress, so re-selecting this same
        // villager returned early and it inherited the approach ticks and path failures the previous
        // attempt had spent. The merchant that just proved it trades with us was the one arriving on
        // a part-spent budget. Cleared without demoting - success is not a temporary illegality.
        round.completeCurrentSuccessfully();
        target = null;
        plannedOffer = null;
        attemptSource = null;

        Optional<AuthorizedAttempt> next = authorizedCandidate(level, carriedBuyer);
        if (next.isPresent()) {
            beginAttempt(level, next.get());
        } else {
            // No further step: either the demand is satisfied, or nothing on offer serves it now.
            endRound(level);
        }
    }

    /**
     * Is the funding SELL about to be executed still permitted, right now?
     *
     * <p>Everything is recomputed from current inventory — reserves, disposable units, the deficit,
     * the leg — and then the <b>attempted quote</b> must be the one that came back. A different
     * authorized quote appearing is not permission to execute this one; that is the same
     * wrong-offer substitution {@link SellFundingLeg} exists to prevent, arriving at the last
     * possible moment.
     */
    private boolean stillAuthorized(Container backpack) {
        TradeAttemptFunding context = attemptFunding;
        if (context == null) {
            // A funding SELL with no recorded purchase behind it has no justification at all.
            return false;
        }
        // R8: the PURCHASE must still exist, not merely the buyer. `available` proves the entity is
        // usable and nothing more - a player emptying that trade, a demand reprice, or the offer
        // simply moving would all leave it true while the reason for this sale had evaporated.
        //
        // Inspecting this one villager is not the sweep the round forbids: that rule is about
        // touching offer lists for villagers never selected, and this one is carried precisely
        // because the executor selected it.
        // Physical legality stays with us; the source answers only whether the purchase still
        // stands. revalidateOffer used to do both, which is precisely the mixture per-source
        // resolution had to separate.
        if (!VillagerTradeAdapter.available(context.buyer())) {
            return false;
        }
        if (com.noobk.spmscavenger.village.trade.TradeSources.of(context.buySource())
                .flatMap(source -> source.revalidate(context.buyer(), context.buyQuote()))
                .isEmpty()) {
            return false;
        }
        // And its non-emerald payment must still be in the backpack. `owedToPurchase` protects that
        // material only when it is the same one being sold; a diamond consumed elsewhere during the
        // walk is invisible to a stick sale.
        // The recorded quote, not a re-snapshot of the live one: matchesLive compares effective
        // cost A/B and result exactly, so a purchase that revalidated has identical costs by
        // construction.
        if (!VillagerTradeAdapter.canAffordNonEmerald(backpack, context.buyQuote())) {
            return false;
        }
        int deficit = context.emeraldsRequired()
                - ScavengerCrafting.count(backpack, net.minecraft.world.item.Items.EMERALD);
        if (deficit <= 0) {
            // Emeralds arrived from somewhere else during the walk. Nothing left to fund.
            return false;
        }

        // Re-derived from inventory as it now stands, against THIS villager's live offers only.
        ScavengerConfig cfg = ScavengerConfig.get();
        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new TradeEvaluationPolicy.EmeraldDeficit(context.consumerKey(), deficit),
                com.noobk.spmscavenger.village.trade.TradeSources.of(attemptSource)
                        .map(source -> source.offers(target, authorizedSellQuery(backpack)))
                        .orElse(java.util.List.of()),
                context.buyQuote(),
                backpack,
                mob.getMainHandItem(),
                mob.getOffhandItem(),
                material -> SellReserveModel.reservedUnits(material, backpack, cfg));

        // A different authorized quote is not permission to execute this one.
        return leg != null && leg.usable() && leg.covers(plannedOffer);
    }

    /**
     * R7 — what the current attempt is funding, carried from selection to execution.
     *
     * <h2>Why the boundary needed a context at all</h2>
     *
     * R6 re-derived the whole funding decision at execution from {@code inspectOffers(target)} — but
     * {@code target} is the villager being <b>sold to</b>. {@code chooseFundingTarget} refuses to
     * produce anything without a BUY quote serving the demand, so the ordinary physical arrangement
     * broke completely:
     *
     * <pre>
     * toolsmith A   emeralds -&gt; iron      the purchase
     * fletcher  B   sticks   -&gt; emeralds  the funding
     *
     * selection    both inspected, leg valid
     * walk to B
     * execution    inspect B alone -&gt; no iron quote -&gt; funding null -&gt; SELL always refused
     * </pre>
     *
     * The safety check made cross-villager funding impossible, and the fixture missed it by putting
     * both quotes in one list.
     *
     * <p>So the purchase is carried instead of rediscovered. This is <b>attempt evidence</b>, so
     * holding villager and offer identity is correct here — and it is exactly why none of it may
     * enter {@link TradeChainPlan}, which outlives the attempt.
     */
    /** A candidate to attempt, plus the purchase it funds (null for a direct BUY). */
    private record AuthorizedAttempt(Candidate candidate, TradeAttemptFunding funding) {
    }

    // ------------------------------------------------------------------ candidates

    private void beginAttempt(ServerLevel level, AuthorizedAttempt attempt) {
        Candidate candidate = attempt.candidate();
        target = candidate.villager();
        plannedOffer = candidate.offer();
        attemptSource = candidate.source();
        // Recorded only for a funding SELL: a direct BUY funds nothing and has no purchase behind it.
        attemptFunding = attempt.funding();
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
        attemptSource = null;
        mob.getNavigation().stop();

        Optional<AuthorizedAttempt> next = authorizedCandidate(level, null);
        if (next.isPresent()) {
            beginAttempt(level, next.get());
        } else {
            endRound(level);
        }
    }

    private void endRound(ServerLevel level) {
        // R2: release first here as well. stop() already did, but endRound is the other teardown
        // path and a throwing credit would leak the greet interlock just as readily from it.
        TradeSessionClaimWindow.release(mob.getUUID());
        emitTradeEpisode(level);
        round.endRound(level.getGameTime());
        target = null;
        plannedOffer = null;
        attemptSource = null;
        mob.getNavigation().stop();
    }

    /**
     * V2-G — emit at most one settlement relationship episode for this visit.
     *
     * <p>Idempotent: clearing the anchor is what makes the two teardown paths safe to both call it.
     * Emits nothing when the round never transacted, and nothing when the mob was outside any
     * remembered settlement's bounds — a trade in the wilderness is a real trade but not a village
     * relationship, and there is no anchor to credit.
     */
    private void emitTradeEpisode(ServerLevel level) {
        BlockPos anchor = tradeEpisodeAnchor;
        // R2: both pending fields cleared before anything else, so no path can re-emit them.
        TradeChainPlan earnedBy = tradeEpisodeChain;
        tradeEpisodeAnchor = null;
        tradeEpisodeChain = null;
        if (anchor == null) {
            return;
        }
        // R1: the anchor bounds credit within one visit; the ledger bounds it across the chain's
        // lifetime, which outlives this teardown by design.
        //
        // R2: against the chain that EARNED it, never the live field. Replanning between the
        // transaction and teardown can have moved `chain` on to an entirely different consumer,
        // which would both re-credit the old chain and mark the new one spent without it trading.
        if (!episodeLedger.consumeCreditFor(earnedBy)) {
            return;
        }
        SettlementRelationshipService.onTradeEpisode(
                level, mob.getUUID(), anchor, level.getGameTime());
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
            /**
             * D-VR-077 step 3 — which market source owns this offer's revalidation. Carried, never
             * derived: {@code offer.ref()} says how a source re-resolves the offer, not which source
             * that is. Always {@code VANILLA} today, and stored anyway so step 4 has nothing to
             * infer.
             */
            com.noobk.spmscavenger.village.trade.TradeSourceKey source,
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
        // R6: the chain gets the same treatment, through V2-D. R5 bound only the evidence to the
        // live consumer, so a chain could outlive its owner - stop() preserves it deliberately, and
        // advanceChain always reported consumerStillWants = true because it only runs once a demand
        // has been found. CONSUMER_GONE was unreachable in production.
        terminateChainIfOwnerless(level, demand.orElse(null));
        return demand;
    }

    /**
     * D-VR-077 step 5 — which stack kinds a market source may be asked to quote.
     *
     * <h2>Modelled is not disposable</h2>
     *
     * {@code SellReserveModel} answers whether this mod <i>knows</i> what claims a material; an
     * empty answer is ignorance and must refuse. But knowing the reserve is not permission to spend:
     * {@code SellExpendabilityPolicy} subtracts that reserve and applies the held-item veto, and only
     * a positive surplus makes a kind eligible to ask the market about.
     *
     * <p>Building the query from {@code modelled()} alone would authorize quoting a material whose
     * every unit is spoken for — permission by category rather than by quantity.
     *
     * <h2>Eligible to ask is not permission to spend</h2>
     *
     * This says only "the market may be asked about this kind". The exact quantity, for the exact
     * quote, for the exact external consumer, is still decided later by
     * {@code TradeFundingPlanner.authorizeFunding} and carried in {@code SellFundingLeg}. The two
     * must not be merged: one is a question, the other is a commitment.
     */
    private com.noobk.spmscavenger.village.trade.TradeOpportunityQuery authorizedSellQuery(
            Container backpack) {
        return authorizedSellQuery(backpack, mob.getMainHandItem(), mob.getOffhandItem(),
                ScavengerConfig.get());
    }

    /** Static and hand-explicit so the disposability rule is provable without a mob. */
    static com.noobk.spmscavenger.village.trade.TradeOpportunityQuery authorizedSellQuery(
            Container backpack, net.minecraft.world.item.ItemStack mainHand,
            net.minecraft.world.item.ItemStack offHand, ScavengerConfig cfg) {
        List<net.minecraft.world.item.ItemStack> authorized = new ArrayList<>();
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack stack = backpack.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            java.util.OptionalInt reserved = SellReserveModel.reservedUnits(stack, backpack, cfg);
            if (reserved.isEmpty()) {
                // Unmodelled. Ignorance does not authorize spending, and it does not authorize
                // asking either - a quote we may never act on is a quote not worth paying for.
                continue;
            }
            // Category-level, matching how the reserve is expressed: the craft chain wants "three
            // logs", not three of one component variant.
            int held = ScavengerCrafting.count(backpack, stack.getItem());
            int disposable = com.noobk.spmscavenger.village.trade.SellExpendabilityPolicy
                    .disposableUnits(stack, held, reserved.getAsInt(), mainHand, offHand);
            // Step 5.5 note: no exactness clamp belongs here, and one was briefly added before a
            // negative control showed it could never fire. Entries come from held slots, so every
            // kind in the query is held by construction. Where category-versus-exact actually
            // matters is the FUNDING LEG - `TradeFundingPlanner.legFor` clamps affordable uses by
            // TradeInventoryFacts.countExact - because that is the first point at which a specific
            // QUANTITY of a specific variant is committed to.
            if (disposable > 0) {
                authorized.add(stack);
            }
        }
        // Canonicalizes counts away and de-duplicates by item-and-components.
        return com.noobk.spmscavenger.village.trade.TradeOpportunityQuery.of(authorized);
    }

    /**
     * Bounded discovery. Offers are inspected only for villagers already selected as candidates —
     * never in a passive sweep, because {@code getOffers()} lazily populates a villager's trades and
     * a broad scan would initialise them across a whole village.
     */
    private Optional<AuthorizedAttempt> authorizedCandidate(
            ServerLevel level, Villager carriedBuyer) {
        Optional<WorkDemandPolicy.MaterialDemand> demand = liveDemand(level);
        if (demand.isEmpty()) {
            return Optional.empty();
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return Optional.empty();
        }

        List<Villager> nearby = new ArrayList<>(level.getEntitiesOfClass(
                Villager.class,
                new AABB(mob.blockPosition()).inflate(CANDIDATE_RADIUS),
                villager -> VillagerTradeAdapter.available(villager)
                        && round.available(villager.getUUID())));
        // R8: discovery re-centres on the mob, and the mob has just walked to the seller. With buyer
        // and seller each 15 blocks from the start point on opposite sides, the buyer is 30 blocks
        // away by the time the sale completes and vanishes from a 16-block scan - despite never
        // moving, still being alive, and still offering the exact purchase this chain exists for.
        //
        // So the buyer from the leg just completed is re-admitted as an already-selected candidate.
        // It is a hint, never authority: every fact about it is revalidated below, and if the quote
        // has gone it simply drops out and ordinary discovery replans.
        if (carriedBuyer != null
                && VillagerTradeAdapter.available(carriedBuyer)
                && round.available(carriedBuyer.getUUID())
                && nearby.stream().noneMatch(v -> v.getUUID().equals(carriedBuyer.getUUID()))) {
            nearby.add(carriedBuyer);
        }
        if (nearby.isEmpty()) {
            return Optional.empty();
        }

        // Ranking needs one flat ordinal space across villagers; execution needs the villager's
        // own board ref. Since D-VR-077 a snapshot carries both as SEPARATE fields, so neither has
        // to be re-derived later - matching an offer back by item identity is ambiguous when a
        // villager sells the same item
        // pair at two different counts, and the identity was ours to keep in the first place.
        List<OfferSnapshot> offers = new ArrayList<>();
        java.util.Map<Integer, Candidate> owners = new java.util.HashMap<>();
        int slot = 0;
        com.noobk.spmscavenger.village.trade.TradeOpportunityQuery query =
                authorizedSellQuery(backpack);
        for (Villager villager : nearby) {
            // One source today, so the candidate set and its order are unchanged. Iterating the
            // registry rather than calling the vanilla source directly is what makes step 6 a
            // registry entry instead of another edit here.
            for (com.noobk.spmscavenger.village.trade.TradeOpportunitySource source
                    : com.noobk.spmscavenger.village.trade.TradeSources.all()) {
            for (OfferSnapshot offer : source.offers(villager, query)) {
                // D-VR-077: one snapshot now carries both coordinates - its own board ref
                // for execution, and this round's flat ordinal for ranking. The old code built a
                // SECOND snapshot whose index field held the slot, and kept the real one in
                // `owners`, because a single int could not hold both.
                OfferSnapshot ranked = offer.withRankOrdinal(slot);
                offers.add(ranked);
                owners.put(slot, new Candidate(villager, source.key(), ranked,
                        demand.get().consumerKey(), demand.get().materialKey()));
                slot++;
            }
            }
        }

        boolean affordable = offers.stream()
                .anyMatch(offer -> VillagerTradeAdapter.canAfford(backpack, offer));

        // P0/R2: produced, tri-state, and trade proceeds only on positively proven infeasibility.
        boolean existingFeasible = !existingRouteInfeasible(level, demand.get());

        // V2-D bridge: choose the BUY quote first, derive its shortfall, then authorize a disposable
        // material to fund it. Without a live deficit every SELL is refused by design, which is why
        // V2-E was previously a direct-BUY subset of the locked architecture.
        // V2-H0 / D-VR-075. The source demand is the ingredient the EXISTING route needs; the
        // market may only sell the finished output. Direct material is tried first and always wins,
        // so a datapack that ever sells iron ingots keeps the original path. Only when no fundable
        // direct purchase exists does the same consumer restate its appetite in market units.
        //
        // `purchaseDemand` drives evaluation, the chain and execution. `demand.get()` - the source -
        // continues to drive ExistingRouteFeasibility above, because its exhaustion evidence
        // describes the raw-iron gather route and means nothing about crafting a pickaxe.
        WorkDemandPolicy.MaterialDemand purchaseDemand = demand.get();
        TradeFundingPlanner.FundingTarget funding = fundingTarget(purchaseDemand, offers, backpack);
        // R1: direct material outranks the projection only when it can ACT. A non-null target with a
        // deficit and no legal SELL leg is a purchase that can never complete, and letting its mere
        // existence suppress the projection made a reachable finished-tool purchase unreachable.
        if (funding == null || !funding.actionable()) {
            Optional<WorkDemandPolicy.MaterialDemand> projected = activeSpec(backpack)
                    .flatMap(spec -> TradePurchaseProjection.ontoOutput(demand.get(), spec));
            if (projected.isPresent()) {
                TradeFundingPlanner.FundingTarget viaOutput =
                        fundingTarget(projected.get(), offers, backpack);
                // Fall back to the direct target when the projection is no better, so a blocked
                // direct route still reports sellBlocked rather than vanishing.
                if (viaOutput != null && viaOutput.actionable()) {
                    purchaseDemand = projected.get();
                    funding = viaOutput;
                }
            }
        }
        TradeEvaluationPolicy.EmeraldDeficit deficit =
                funding == null ? null : funding.deficit();
        // R6: one exact SELL quote, carried as identity. R5 derived the chain's per-use yield from
        // the first authorized SELL in the list while the executor attempted whichever SELL the
        // ranking preferred - Task 50's "right arithmetic against the wrong offer", one layer in.
        SellFundingLeg sellLeg = funding == null ? null : funding.sellLeg();

        // V2-D decides whether this chain may continue and which leg is next. Its verdict is taken
        // before ranking, because a terminated chain must not produce a candidate at all.
        TradeChainPolicy.ChainOutcome outcome =
                advanceChain(level, demand.get(), purchaseDemand, backpack, funding);
        if (outcome == null || !outcome.active()) {
            return Optional.empty();
        }
        TradeChainPlan.Step step = outcome.plan().step();
        if (step == TradeChainPlan.Step.SELL_TO_FUND
                && (outcome.sellBlocked() || sellLeg == null || !sellLeg.usable())) {
            // Not enough authorized material to close the deficit. A state to report, not a reason
            // to attempt a purchase we cannot pay for.
            return Optional.empty();
        }

        // The exact quote this iteration planned - not "the best offer that happens to be the right
        // direction". Those diverge whenever the planner's fundability filter rejects something the
        // registrar's ranking accepts, and then the mob would execute a quote whose economics nobody
        // computed.
        OfferSnapshot planned = step == TradeChainPlan.Step.SELL_TO_FUND
                ? sellLeg.offer()
                : funding.buyOffer();

        // Carried, never rediscovered at execution: the BUY and the SELL routinely belong to
        // different villagers, and re-deriving the purchase while standing at the seller finds no
        // purchase at all.
        final WorkDemandPolicy.MaterialDemand selected = purchaseDemand;
        Candidate buyCandidate = owners.get(funding.buyOffer().rankOrdinal());
        TradeAttemptFunding attemptContext =
                step == TradeChainPlan.Step.SELL_TO_FUND && buyCandidate != null
                        ? new TradeAttemptFunding(
                                selected.consumerKey(),
                                buyCandidate.villager(),
                                // D-VR-077 step 3: the BUY's own source, carried independently of
                                // the seller's. Both are VANILLA today; the first real divergence is
                                // already known - a Trade Everything synthetic SELL funding a
                                // vanilla Toolsmith BUY.
                                buyCandidate.source(),
                                // Since step 2 one snapshot carries both coordinates, so this is the
                                // buyer's own board ref AND its round ordinal. The old code had to
                                // reach past a flattened stand-in to find the real address; asking
                                // buyer A for global ordinal 7 when its board index is 2 would have
                                // revalidated a different trade, or none at all.
                                buyCandidate.offer(),
                                funding.emeraldsRequired())
                        : null;

        return TradeDemandGate
                .authorize(selected,
                        new RouteEvidence(existingFeasible, offers, affordable, deficit,
                                sellLeg == null ? null : sellLeg.authorization()))
                // V2-C still owns the route decision; it simply no longer chooses the quote.
                .filter(decision -> decision.rankedOffers().stream()
                        .anyMatch(evaluation -> evaluation.tieBreakOrdinal() == planned.rankOrdinal()))
                .map(decision -> owners.get(planned.rankOrdinal()))
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> VillagerTradeAdapter.canAfford(backpack, candidate.offer()))
                .map(candidate -> new AuthorizedAttempt(candidate, attemptContext));
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
            ServerLevel level,
            WorkDemandPolicy.MaterialDemand sourceDemand,
            WorkDemandPolicy.MaterialDemand demand,
            Container backpack,
            TradeFundingPlanner.FundingTarget funding) {
        long now = level.getGameTime();
        int held = ScavengerCrafting.count(
                backpack, BuiltInRegistries.ITEM.get(demand.materialKey()));

        if (funding == null) {
            // R2: count what the CHAIN is buying, not whichever representation the market happened
            // to select this iteration. A surviving pickaxe chain evaluated against a held iron
            // INGOT read 1 >= 1 and terminated as TARGET_OBTAINED_ELSEWHERE while the mob owned no
            // pickaxe at all - the V2-D R6 coordinate-system defect, in the units dimension.
            int heldForChain = chain == null ? held : ScavengerCrafting.count(
                    backpack, BuiltInRegistries.ITEM.get(chain.desiredOutput()));
            // R7: no usable quote right now is NOT a reason to destroy the plan. Nulling it here let
            // a villager strolling out of range reset the hard lifetime, and the next quote start a
            // fresh 6000 ticks - the same defect the review rejected for combat interruption, keyed
            // on market visibility instead. The consumer still wants the material; only the evidence
            // is missing. V2-D still gets to expire it.
            if (chain != null) {
                TradeChainPolicy.ChainOutcome idle =
                        TradeChainPolicy.withoutMarketEvidence(chain, heldForChain, now);
                chain = idle.active() ? idle.plan() : null;
            }
            return null;
        }

        ScavengerCrafting.ConsumerRecipeSpec spec = TradePurchaseProjection
                .activeSpecFor(sourceDemand, backpack, mob.getMainHandItem(),
                        mob.getOffhandItem(), ScavengerConfig.get())
                .orElse(null);
        // R2: judged against the SOURCE demand, and in both directions. Using the selected purchase
        // demand made direct -> projected fail: `ontoOutput(iron_pickaxe, ...)` has no projection
        // back to the ingot, so the executor reminted and the market flip restarted the clock after
        // all. Both the old and the new target must be representations of the same source appetite.
        if (chain != null
                && chain.consumerKey().equals(demand.consumerKey())
                && !chain.desiredOutput().equals(demand.materialKey())
                && TradePurchaseProjection.isPurchaseTargetFor(
                        sourceDemand, spec, chain.desiredOutput())
                && TradePurchaseProjection.isPurchaseTargetFor(
                        sourceDemand, spec, demand.materialKey())) {
            // Same appetite, other representation. Preserve the clock: minting a fresh plan whenever
            // a direct seller wanders in or out of range would restart the hard lifetime on every
            // market flip - R7's reset defect through a new door.
            chain = chain.retargetedTo(demand.materialKey(), held + demand.derivedDeficit());
        } else if (chain == null
                || !chain.consumerKey().equals(demand.consumerKey())
                || !chain.desiredOutput().equals(demand.materialKey())) {
            // R6: an absolute inventory threshold, never the deficit. Passing the deficit made
            // "hold 1 of the 3 ingots you need" terminate the chain after a single purchase, and any
            // chain reopened afterwards terminated instantly - that consumer could never be finished
            // by trade again.
            chain = TradeChainPlan.forDemand(
                    demand.consumerKey(), demand.materialKey(),
                    held, demand.derivedDeficit(), now);
        }

        TradeChainPolicy.ChainOutcome outcome = TradeChainPolicy.evaluate(
                chain,
                TradeChainPolicy.factsFrom(funding, held,
                        ScavengerCrafting.count(backpack, net.minecraft.world.item.Items.EMERALD)),
                now);

        // Terminated chains are dropped rather than left standing: an expired or ownerless plan that
        // survives is the stale-ownership shape this slice keeps having to remove.
        chain = outcome.active() ? outcome.plan() : null;
        return outcome;
    }

    /**
     * R6 — the consumer is gone or has changed, so its chain is over.
     *
     * <p>R5 bound the exhaustion <i>evidence</i> to the live consumer and left the chain resident:
     * {@code stop()} deliberately preserves it, and {@code advanceChain} only ever runs after a
     * demand has been found, so it always passed {@code consumerStillWants = true}. {@code
     * CONSUMER_GONE} was unreachable in production and a chain could outlive its owner, then resume
     * when the same consumer reappeared.
     *
     * <p>Routed through {@link TradeChainPolicy} rather than nulling the field directly — Option A
     * means V2-D owns chain termination, and a second lifecycle rule beside it is what Option A was
     * chosen to avoid.
     */
    private void terminateChainIfOwnerless(
            ServerLevel level, WorkDemandPolicy.MaterialDemand liveDemand) {
        if (chain == null) {
            return;
        }
        // R1: a projected chain buys the recipe OUTPUT while the live demand still names the
        // INGREDIENT, so a material-equality test declared every projected chain ownerless on the
        // next continuation tick - and discovery then rebuilt one, which made the architecture
        // violation look like working behaviour.
        Container backpack = PlayerMobs.backpack(mob);
        if (TradePurchaseProjection.stillOwns(chain, liveDemand,
                backpack == null ? null : activeSpec(backpack).orElse(null))) {
            return;
        }
        TradeChainPolicy.ChainOutcome outcome = TradeChainPolicy.evaluate(
                chain,
                new TradeChainPolicy.ChainFacts(false, 0, 0, 0, 0, 0),
                level.getGameTime());
        if (outcome.terminated()) {
            chain = null;
        }
    }

    /** The live consumer recipe behind the current demand, if any. */
    private Optional<ScavengerCrafting.ConsumerRecipeSpec> activeSpec(Container backpack) {
        return liveDemandPayload(backpack).flatMap(source -> TradePurchaseProjection.activeSpecFor(
                source, backpack, mob.getMainHandItem(), mob.getOffhandItem(),
                ScavengerConfig.get()));
    }

    /** The raw selected demand, without the episode-lifecycle side effects of {@link #liveDemand}. */
    private Optional<WorkDemandPolicy.MaterialDemand> liveDemandPayload(Container backpack) {
        if (backpack == null) {
            return Optional.empty();
        }
        return WorkDemandPolicy
                .select(backpack, mob.getMainHandItem(), mob.getOffhandItem(), ScavengerConfig.get())
                .map(WorkDemandPolicy.WorkDemand::payload);
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
     * The deficit is derived from the BUY quote this iteration will actually serve, and the SELL leg
     * that would close it is chosen in the same pass.
     *
     * <p>R2 took the cheapest emerald cost found anywhere, independently of the offer the ranking
     * would choose — Task 50's "right arithmetic against the wrong offer". Reserves come from
     * {@link SellReserveModel}, which reads the existing craft chain, so a log the torch chain has
     * claimed is not spare merely because a villager will pay for it, and a material nobody has
     * modelled is refused rather than assumed free.
     */
    private TradeFundingPlanner.FundingTarget fundingTarget(
            WorkDemandPolicy.MaterialDemand demand, List<OfferSnapshot> offers, Container backpack) {
        ScavengerConfig cfg = ScavengerConfig.get();
        return TradeFundingPlanner.chooseFundingTarget(
                demand, offers, backpack, mob.getMainHandItem(), mob.getOffhandItem(),
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
