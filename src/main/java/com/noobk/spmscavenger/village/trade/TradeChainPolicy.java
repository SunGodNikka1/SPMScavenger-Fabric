package com.noobk.spmscavenger.village.trade;

/**
 * V2-D — what the chain should do <b>right now</b>, recomputed from current facts every time.
 *
 * <h2>Advancement is derived, never remembered</h2>
 *
 * The step follows from <b>emeralds actually held</b>, not from "a sell was attempted". So a failed
 * sell cannot advance the chain (req 11), and a successful one does not need to report anything: the
 * next evaluation simply sees more emeralds. Nothing here records attempts, which is also why SPM
 * eating a sellable food item mid-chain is not a conflict — the next evaluation recalculates against
 * the smaller stock and carries on (req 7).
 *
 * <h2>Selling is bounded by the purchase, not by what is spare</h2>
 *
 * {@code requiredSellUses = ceil(deficit / emeraldsPerSellUse)}, and it is <b>zero</b> the moment the
 * deficit is met. Owning 64 disposable wheat is permission to spend wheat; it is not a reason to sell
 * 64 wheat. <i>Disposable means permitted to spend, not desirable to spend</i> — the same distinction
 * as burnable-is-not-expendable, one layer up.
 */
public final class TradeChainPolicy {

    /** Everything the decision may know, as of now. Supplied by the caller; never fetched here. */
    public record ChainFacts(
            boolean consumerStillWants,
            int desiredOutputHeld,
            int emeraldsHeld,
            int emeraldsRequiredForPurchase,
            int emeraldsPerSellUse,
            int affordableSellUses) {
    }

    /**
     * Build the facts for a live funding target — the one place deficit, units and uses meet.
     *
     * <p>Also moved out of the goal in R6, and for the same reason: {@code affordableSellUses} was
     * being fed {@code disposableUnits} at a call site no test could reach, so raw item units were
     * compared against a use count and a control reverting it stayed green.
     *
     * @param funding the quote this iteration serves; {@code null} yields facts that terminate
     */
    public static ChainFacts factsFrom(
            TradeFundingPlanner.FundingTarget funding, int heldOutput, int heldEmeralds) {
        if (funding == null) {
            return new ChainFacts(false, heldOutput, heldEmeralds, 0, 0, 0);
        }
        SellFundingLeg leg = funding.sellLeg();
        return new ChainFacts(
                true,
                heldOutput,
                heldEmeralds,
                funding.emeraldsRequired(),
                leg == null ? 0 : leg.emeraldsPerUse(),
                // USES, not units. Both sides of the sellBlocked comparison are use counts.
                leg == null ? 0 : leg.affordableUses());
    }

    public enum Termination {
        /** Someone else supplied it — mining, looting, a gift. Stop selling. */
        TARGET_OBTAINED_ELSEWHERE,
        /** The external consumer no longer wants it. The chain has no owner. */
        CONSUMER_GONE,
        /** Hard expiry. */
        EXPIRED
    }

    /**
     * @param plan the chain as it should now stand, or {@code null} when {@code termination} is set
     * @param requiredSellUses successful sells still needed to fund the purchase; {@code 0} once the
     *     deficit is met
     * @param sellBlocked a sell is needed but fewer authorized SELL uses are affordable — a state
     *     to report, not a failure to record
     */
    public record ChainOutcome(
            TradeChainPlan plan,
            int requiredSellUses,
            boolean sellBlocked,
            Termination termination) {

        public boolean terminated() {
            return termination != null;
        }

        public boolean active() {
            return plan != null && termination == null;
        }
    }

    private TradeChainPolicy() {
    }

    public static ChainOutcome evaluate(TradeChainPlan plan, ChainFacts facts, long nowTick) {
        if (plan == null || facts == null) {
            return terminated(Termination.CONSUMER_GONE);
        }
        // The consumer is the chain's only reason to exist, so it is checked before anything else -
        // including before the purchase step. Continuing to BUY for a consumer that is gone would be
        // the chain acquiring an appetite of its own (req 1, req 2).
        if (!facts.consumerStillWants()) {
            return terminated(Termination.CONSUMER_GONE);
        }
        // Obtained elsewhere: mined, looted, gifted. The chain's purpose is satisfied, so it stops -
        // it does not keep selling wheat for emeralds nobody now needs (req 9).
        if (facts.desiredOutputHeld() >= plan.targetHeldQuantity()) {
            return terminated(Termination.TARGET_OBTAINED_ELSEWHERE);
        }
        if (plan.expired(nowTick)) {
            return terminated(Termination.EXPIRED);
        }

        int deficit = Math.max(0, facts.emeraldsRequiredForPurchase() - facts.emeraldsHeld());
        if (deficit <= 0) {
            // Funded. No further selling is possible from here, because requiredSellUses is derived
            // from the deficit and the deficit is gone (req 12, defect probe 1).
            return new ChainOutcome(plan.at(TradeChainPlan.Step.BUY_TARGET), 0, false, null);
        }

        int perSell = Math.max(1, facts.emeraldsPerSellUse());
        int needed = ceilDiv(deficit, perSell);
        // R6: both sides are SELL USES. R5 passed raw disposable item units here and compared them
        // against a use count - 61 sticks read as "61 sells available" when a 32-stick offer allows
        // exactly one. Units on both sides of a comparison is not a detail; it decided sellBlocked.
        boolean blocked = facts.affordableSellUses() < needed;

        return new ChainOutcome(
                plan.at(TradeChainPlan.Step.SELL_TO_FUND),
                needed,
                blocked,
                null);
    }

    /**
     * R7 — evaluate a chain when <b>no market evidence is available right now</b>.
     *
     * <h2>A missing quote is not a missing consumer</h2>
     *
     * R6's executor did {@code if (funding == null) chain = null;} outside this policy, so any moment
     * without a usable quote destroyed the plan — and the next moment with one built a fresh plan
     * with a fresh expiry:
     *
     * <pre>
     * T0     chain opens, expires T6000
     * T3000  the buying villager strolls out of range -&gt; chain = null
     * T3100  it strolls back                          -&gt; new chain, expires T9100
     * </pre>
     *
     * Repeat and the hard lifetime never arrives. That is the same defect the review rejected for
     * combat interruption, triggered by market visibility instead — and the hard lifetime is the one
     * invariant Option A was chosen to preserve.
     *
     * <p>So the plan survives an empty market and keeps its original clock. The consumer still wants
     * the material; only the evidence is momentarily absent. Expiry and target-obtained still apply,
     * because those are facts about the chain rather than about the market.
     */
    public static ChainOutcome withoutMarketEvidence(
            TradeChainPlan plan, int desiredOutputHeld, long nowTick) {
        if (plan == null) {
            return terminated(Termination.CONSUMER_GONE);
        }
        if (desiredOutputHeld >= plan.targetHeldQuantity()) {
            return terminated(Termination.TARGET_OBTAINED_ELSEWHERE);
        }
        if (plan.expired(nowTick)) {
            return terminated(Termination.EXPIRED);
        }
        // Alive, and deliberately with no step advice: there is nothing to act on until a quote
        // reappears, and the caller must not read this as "ready to buy".
        return new ChainOutcome(plan, 0, true, null);
    }

    private static ChainOutcome terminated(Termination termination) {
        return new ChainOutcome(null, 0, false, termination);
    }

    private static int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
