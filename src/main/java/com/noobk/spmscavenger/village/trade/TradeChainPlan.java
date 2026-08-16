package com.noobk.spmscavenger.village.trade;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * V2-D — a transient economic intention: buy a target, selling only as much as that purchase needs.
 *
 * <h2>What it deliberately does not carry</h2>
 *
 * No villager, no offer index, no path, no anchor. Those are <b>attempt evidence</b>, valid for one
 * attempt, and putting them here would turn a momentary observation into durable identity — after
 * which the plan would keep pointing at a villager that has walked away, or an offer index that now
 * means something else. The plan holds the two things that genuinely persist across steps:
 * {@link #consumerKey()} (who wants this) and {@link #desiredOutput()} (what they want).
 *
 * <h2>{@code targetHeldQuantity} is an inventory threshold, not a deficit (R6)</h2>
 *
 * R5 opened the chain with {@code demand.derivedDeficit()}, and {@link TradeChainPolicy} terminates
 * on {@code desiredOutputHeld >= targetHeldQuantity}. Those are different coordinate systems, and
 * mixing them broke the most ordinary case there is — <b>already owning some of what you need</b>:
 *
 * <pre>
 * iron pickaxe needs 3, mob holds 1  -&gt; derivedDeficit 2 -&gt; plan quantity 2
 * buy one ingot                      -&gt; held 2           -&gt; 2 &gt;= 2
 *                                                        -&gt; TARGET_OBTAINED_ELSEWHERE
 * </pre>
 *
 * The chain stops one ingot short, and a chain reopened afterwards (deficit 1, held 2) terminates
 * immediately — so that consumer can never be finished by trade again. A deficit shrinks as the goal
 * is approached; a threshold does not, which is precisely why the stopping condition needs the
 * latter. Callers pass {@code heldAtCreation + deficit}.
 *
 * <h2>Why it is not persisted</h2>
 *
 * Transient by construction. A save/reload simply loses it, and the current external demand rebuilds
 * it if it still exists — which is the correct behaviour, because a chain resurrected from disk would
 * be an intention with no live consumer behind it. This also means <b>Gate RET-1e does not apply</b>:
 * there is no store, so there is nothing to sweep on permanent removal.
 */
public record TradeChainPlan(
        ResourceLocation consumerKey,
        ResourceLocation desiredOutput,
        int targetHeldQuantity,
        long createdAtTick,
        long expiresAtTick,
        Step step) {

    /** Default lifetime. Long enough to walk a village, short enough not to outlive its reason. */
    public static final long DEFAULT_LIFETIME_TICKS = 6_000L;

    public enum Step {
        /** Selling disposable material to close the emerald deficit for the BUY below. */
        SELL_TO_FUND,
        /** Enough emeralds are held; the purchase itself is what remains. */
        BUY_TARGET
    }

    public TradeChainPlan {
        Objects.requireNonNull(consumerKey, "consumerKey");
        Objects.requireNonNull(desiredOutput, "desiredOutput");
        Objects.requireNonNull(step, "step");
        if (targetHeldQuantity <= 0) {
            throw new IllegalArgumentException(
                    "targetHeldQuantity must be positive: " + targetHeldQuantity);
        }
        if (expiresAtTick <= createdAtTick) {
            throw new IllegalArgumentException("a chain must expire after it is created");
        }
    }

    /**
     * Open a chain for a consumer that already exists.
     *
     * <p>There is no no-consumer constructor on purpose: a chain with no owner would be an appetite,
     * and the whole point of this slice is that appetite comes from outside (req 1).
     */
    public static TradeChainPlan forConsumer(
            ResourceLocation consumerKey, ResourceLocation desiredOutput, int targetHeldQuantity,
            long nowTick) {
        return new TradeChainPlan(consumerKey, desiredOutput, targetHeldQuantity,
                nowTick, nowTick + DEFAULT_LIFETIME_TICKS, Step.SELL_TO_FUND);
    }

    /**
     * Open a chain from a live demand, converting deficit to threshold <b>here</b>.
     *
     * <p>R6 moved this addition out of the goal deliberately. It lived at a call site no JVM test can
     * reach — {@code advanceChain} needs a {@code ServerLevel} — so passing the deficit instead of the
     * threshold was invisible to the entire suite, and two negative controls that reverted it stayed
     * green. Arithmetic that decides a termination condition does not belong somewhere untestable.
     *
     * @param heldNow units of {@code desiredOutput} the mob already has
     * @param deficit how many more the consumer wants
     */
    public static TradeChainPlan forDemand(
            ResourceLocation consumerKey, ResourceLocation desiredOutput,
            int heldNow, int deficit, long nowTick) {
        return forConsumer(consumerKey, desiredOutput, Math.max(0, heldNow) + deficit, nowTick);
    }

    /**
     * V2-G-R1 — is this the <b>same chain episode</b> as {@code other}?
     *
     * <p>Object identity cannot answer this: {@link #at(Step)} returns a new record on every step
     * transition while remaining the same chain. The stable triple is consumer, desired output and
     * <b>creation tick</b> — {@code at} preserves {@code createdAtTick}, and only
     * {@link #forDemand} mints a new one, which is exactly the boundary a fresh relationship episode
     * is allowed to cross.
     */
    public boolean sameChainAs(TradeChainPlan other) {
        // V2-H0-R2: desiredOutput is deliberately NOT part of identity. `retargetedTo` switches
        // between the two representations of one appetite while preserving the clock, so including
        // the target here made a retarget look like a new chain - and V2-G's ledger would have paid
        // a second familiarity episode for one economic episode, contradicting D-VR-075 directly.
        return other != null
                && createdAtTick == other.createdAtTick
                && consumerKey.equals(other.consumerKey);
    }

    public boolean expired(long nowTick) {
        return nowTick >= expiresAtTick;
    }

    /**
     * V2-H0-R1 — the same chain, buying the other representation of the same appetite.
     *
     * <p>{@code createdAtTick} and {@code expiresAtTick} are <b>preserved</b>. Minting a fresh plan
     * when the market flips between the source material and the recipe output would reset the hard
     * lifetime every time a direct seller wandered in or out of range — the R7 "villager strolls away
     * and the clock restarts" defect returning through a new door. `D-VR-075` calls these two
     * expressions of one consumer appetite, so switching between them is not a new economic episode.
     *
     * <p>The step resets to {@code SELL_TO_FUND}: a different quote has a different price, so any
     * previous "funded" conclusion belonged to the old target.
     */
    public TradeChainPlan retargetedTo(ResourceLocation newOutput, int newTargetHeldQuantity) {
        if (desiredOutput.equals(newOutput) && targetHeldQuantity == newTargetHeldQuantity) {
            return this;
        }
        return new TradeChainPlan(consumerKey, newOutput, newTargetHeldQuantity,
                createdAtTick, expiresAtTick, Step.SELL_TO_FUND);
    }

    /** Step changes; identity does not. */
    public TradeChainPlan at(Step next) {
        return next == step
                ? this
                : new TradeChainPlan(consumerKey, desiredOutput, targetHeldQuantity,
                        createdAtTick, expiresAtTick, next);
    }
}
