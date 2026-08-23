package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * V2-B — does <b>this</b> offer contribute to <b>this</b> demand, and at what price?
 *
 * <h2>The boundary</h2>
 *
 * This class scores one offer against one demand. It does <b>not</b> decide that trading is the right
 * way to satisfy the demand — comparing trade against gather / smelt / craft, and admitting TRADE as
 * an acquisition route, are V2-C's. Everything here is therefore phrased as a contribution and a
 * price, never a recommendation.
 *
 * <p>Stated as a rule because the erosion is gradual: <b>V2-B must never become a second demand
 * selector.</b> It takes a demand it did not choose, and returns a number someone else compares.
 *
 * <h2>Value grants no permission</h2>
 *
 * Demand *iron ingot*, offer *3 emeralds → diamond sword*: valuable, and rejected. Nothing about an
 * offer's desirability creates authority to pursue it. This is the project's recurring invariant —
 * <i>preference affects choice; preference does not create permission</i> — applied to acquisition,
 * the same shape as burnable-is-not-expendable in the furnace policy.
 *
 * <h2>Selling invents nothing</h2>
 *
 * An offer that pays emeralds is not a discovery that emeralds are desirable. SELL evaluates only
 * against an emerald deficit a **named external consumer** already established toward a BUY step;
 * without one the answer is {@link TradeRejection#NO_CONSUMER_FOR_PAYMENT}. Otherwise the mob would
 * reason "someone will buy my wheat" into an unbounded emerald appetite that no consumer asked for.
 *
 * <p>Pure: no {@code Container}, no entity, no world, no clock. Taking a container is precisely how
 * this class would begin reserving inventory, which is why the parameter does not exist.
 */
public final class TradeEvaluationPolicy {

    /** Emeralds, the only currency vanilla villagers pay in. */
    private static final ResourceLocation EMERALD = BuiltInRegistries.ITEM.getKey(Items.EMERALD);

    /**
     * An emerald shortfall a named consumer already established toward a BUY step.
     *
     * <p>Required for SELL because it is the whole difference between "a consumer needs 27 emeralds
     * for a mending book" and "emeralds seem nice".
     */
    public record EmeraldDeficit(ResourceLocation consumerKey, int emeraldsNeeded) {
        public EmeraldDeficit {
            if (emeraldsNeeded <= 0) {
                throw new IllegalArgumentException("deficit must be positive: " + emeraldsNeeded);
            }
        }
    }

    /** Why an offer contributes nothing. */
    public enum TradeRejection {
        /** The offer's result is not the demanded material. Valuable is not relevant. */
        WRONG_MATERIAL,
        /** Empty cost or result. */
        NOT_TRADEABLE,
        /** {@code uses >= maxUses}. */
        OUT_OF_STOCK,
        /** A SELL offer with no named consumer waiting on emeralds. */
        NO_CONSUMER_FOR_PAYMENT,
        /** Matches, but would contribute zero units. */
        ZERO_CONTRIBUTION,
        /**
         * R5 — a funding SELL whose cost has a second item.
         *
         * <p>{@link SellAuthorization} authorizes exactly one stack and {@code permits} checks
         * exactly {@code costA}, while {@link TradeTransaction} debits {@code costA} <b>and</b>
         * {@code costB}. So {@code 20 sticks + 1 diamond -> 5 emeralds} would pass permission on the
         * sticks and spend the diamond: <b>permission to spend A silently manufacturing permission
         * to spend B</b>. Refused outright rather than solved with a joint reservation allocator,
         * which gen-1 does not need — no vanilla funding trade has a second cost. BUY offers are
         * unaffected and keep V2-A's two-cost support.
         */
        UNSUPPORTED_COMPOUND_COST
    }

    /** Either an evaluation or the reason there is none. Never both, never neither. */
    public record Result(Optional<TradeEvaluation> evaluation, TradeRejection rejection) {

        static Result of(TradeEvaluation evaluation) {
            return new Result(Optional.of(evaluation), null);
        }

        static Result reject(TradeRejection rejection) {
            return new Result(Optional.empty(), rejection);
        }

        public boolean viable() {
            return evaluation.isPresent();
        }
    }

    private TradeEvaluationPolicy() {
    }

    /** BUY only: no emerald deficit supplied, so a SELL offer has no consumer and is rejected. */
    public static Result evaluate(WorkDemandPolicy.MaterialDemand demand, OfferSnapshot offer) {
        return evaluate(demand, offer, null, MerchantCurrencyPolicies.current());
    }

    /**
     * @param emeraldDeficit a shortfall an external consumer already established; {@code null} when
     *     none exists, which makes every SELL offer {@link TradeRejection#NO_CONSUMER_FOR_PAYMENT}
     */
    public static Result evaluate(
            WorkDemandPolicy.MaterialDemand demand,
            OfferSnapshot offer,
            EmeraldDeficit emeraldDeficit) {
        return evaluate(demand, offer, emeraldDeficit, MerchantCurrencyPolicies.current());
    }

    static Result evaluate(
            WorkDemandPolicy.MaterialDemand demand,
            OfferSnapshot offer,
            EmeraldDeficit emeraldDeficit,
            MerchantCurrencyPolicy currency) {
        if (demand == null || offer == null) {
            return Result.reject(TradeRejection.NOT_TRADEABLE);
        }
        if (offer.outOfStock()) {
            return Result.reject(TradeRejection.OUT_OF_STOCK);
        }
        if (!offer.isTradeable()) {
            return Result.reject(TradeRejection.NOT_TRADEABLE);
        }

        ResourceLocation resultKey = keyOf(offer.result());
        if (demand.materialKey().equals(resultKey)) {
            return buy(demand, offer);
        }
        if (currency.recognizesFundingOutput(offer.result())) {
            return sell(demand, offer, emeraldDeficit, currency);
        }
        // Matches nothing this demand asked for. Its market value is not a reason.
        return Result.reject(TradeRejection.WRONG_MATERIAL);
    }

    private static Result buy(WorkDemandPolicy.MaterialDemand demand, OfferSnapshot offer) {
        // Capped at the deficit on purpose: contributing more than was asked for is how a bounded
        // need turns into an unbounded appetite, one "while I am here" at a time.
        int contribution = Math.min(offer.result().getCount(), demand.derivedDeficit());
        if (contribution <= 0) {
            return Result.reject(TradeRejection.ZERO_CONTRIBUTION);
        }
        int payment = offer.costA().getCount() + offer.costB().getCount();
        float unitCost = payment / (float) contribution;

        return Result.of(new TradeEvaluation(
                TradeEvaluation.Direction.BUY,
                demand.consumerKey(),
                demand.materialKey(),
                offer.rankOrdinal(),
                contribution,
                offer.costA(),
                offer.costB(),
                unitCost,
                utility(contribution, demand.derivedDeficit(), unitCost)));
    }

    /**
     * R3 — evaluate a funding SELL against an explicit authorization.
     *
     * <p>The authorization, not the purchase demand, says what may be spent. Passing the external
     * demand here was the R2 bridge defect: an iron demand made every wheat-for-emerald offer
     * {@code WRONG_MATERIAL}, so the only SELL that could fund an iron purchase was selling iron.
     */
    public static Result evaluateSell(
            EmeraldDeficit deficit, SellAuthorization authorization, OfferSnapshot offer) {
        return evaluateSell(deficit, authorization, offer, MerchantCurrencyPolicies.current());
    }

    static Result evaluateSell(
            EmeraldDeficit deficit,
            SellAuthorization authorization,
            OfferSnapshot offer,
            MerchantCurrencyPolicy currency) {
        if (deficit == null) {
            return Result.reject(TradeRejection.NO_CONSUMER_FOR_PAYMENT);
        }
        if (offer == null || !offer.isTradeable()) {
            return Result.reject(TradeRejection.NOT_TRADEABLE);
        }
        if (offer.outOfStock()) {
            return Result.reject(TradeRejection.OUT_OF_STOCK);
        }
        if (!currency.recognizesFundingOutput(offer.result())) {
            return Result.reject(TradeRejection.WRONG_MATERIAL);
        }
        // Permission is the gate, and it is separate from desirability. An unauthorised material is
        // refused however profitable the offer is.
        if (!offer.costB().isEmpty()) {
            return Result.reject(TradeRejection.UNSUPPORTED_COMPOUND_COST);
        }
        if (authorization == null || !authorization.permits(offer.costA())) {
            return Result.reject(TradeRejection.WRONG_MATERIAL);
        }
        return sellAgainst(deficit, offer, currency);
    }

    private static Result sell(
            WorkDemandPolicy.MaterialDemand demand,
            OfferSnapshot offer,
            EmeraldDeficit deficit,
            MerchantCurrencyPolicy currency) {
        if (deficit == null) {
            return Result.reject(TradeRejection.NO_CONSUMER_FOR_PAYMENT);
        }
        // Legacy path: the demand doubles as the authorization, which only makes sense when the mob
        // is selling the very material the demand names. R3's evaluateSell is the general form.
        if (!demand.materialKey().equals(keyOf(offer.costA()))) {
            return Result.reject(TradeRejection.WRONG_MATERIAL);
        }
        return sellAgainst(deficit, offer, currency);
    }

    private static Result sellAgainst(
            EmeraldDeficit deficit, OfferSnapshot offer, MerchantCurrencyPolicy currency) {
        int contribution = Math.min(currency.fundingUnits(offer.result()), deficit.emeraldsNeeded());
        if (contribution <= 0) {
            return Result.reject(TradeRejection.ZERO_CONTRIBUTION);
        }
        int payment = offer.costA().getCount() + offer.costB().getCount();
        float unitCost = payment / (float) contribution;

        return Result.of(new TradeEvaluation(
                TradeEvaluation.Direction.SELL,
                // The consumer is the one that needs the emeralds, not the one that needed the
                // material - otherwise the sell step would be attributed to the wrong appetite.
                deficit.consumerKey(),
                EMERALD,
                offer.rankOrdinal(),
                contribution,
                offer.costA(),
                offer.costB(),
                unitCost,
                utility(contribution, deficit.emeraldsNeeded(), unitCost)));
    }

    /**
     * A comparable number for V2-C, and nothing more.
     *
     * <p>Rises with the fraction of the deficit closed, falls with price per unit. Deliberately
     * simple: an elaborate score here would look like a decision, and the decision is not this
     * class's to make.
     */
    private static float utility(int contribution, int deficit, float unitCost) {
        float coverage = contribution / (float) Math.max(1, deficit);
        return coverage * 100f - unitCost;
    }

    private static ResourceLocation keyOf(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
