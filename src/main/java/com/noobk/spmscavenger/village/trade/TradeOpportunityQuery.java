package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * D-VR-077 step 4 — <b>what a market source is permitted to quote</b>, and nothing more.
 *
 * <h2>Opportunity truth must not become spend permission</h2>
 *
 * The rejected shape was {@code offers(Villager, Container backpack)}. Handing a market source the
 * whole backpack asks it to work out what may be sold — and disposition is V2 policy, decided by
 * {@code SellReserveModel} against reserves this mod owns. A source that derived permission from
 * inventory would be answering a question it has no standing to answer:
 *
 * <pre>
 * SellReserveModel        "you MAY consider selling this kind of stack"
 * TradeOpportunityQuery   [oak_log x1, stick x1, ...]
 * TradeOpportunitySource  "if you did, the market quote is this"
 * </pre>
 *
 * The source must never derive the first statement from the third.
 *
 * <h2>Count is canonicalized to 1</h2>
 *
 * This says which stack <b>kinds</b> may be quoted, not how much permission exists. Leaving the held
 * count in would encode quantity into a permission object, and quantity belongs to live inventory,
 * disposable units, affordable uses and the transaction-time debit. It also matches how quoting
 * actually works — Trade Everything's valuation is a function of item and components, not count.
 *
 * <h2>Bounded and defensive</h2>
 *
 * Entries are copied in and copied out, de-duplicated by item-and-components, and the list is
 * immutable. A caller mutating the stacks it passed cannot change what a source is allowed to quote,
 * and a source cannot mutate the caller's inventory through the query it was handed.
 */
public record TradeOpportunityQuery(List<ItemStack> authorizedSellInputs) {

    /**
     * The most distinct stack kinds a caller may authorize in one query.
     *
     * <h2>Where the number comes from</h2>
     *
     * The caller contract is "the {@code SellReserveModel}-modelled kinds the mob is currently
     * holding", and a mob can hold at most one distinct kind per backpack slot. That producer bound
     * is <b>Social Player Mobs'</b> — the backpack is its {@code InventoryCarrier} inventory, eight
     * slots today — and copying another mod's constant into our source is a hardcode with a delayed
     * fuse. So this sits one doubling above it: SPM may grow its inventory without this failing
     * closed on the first user who updates.
     *
     * <h2>What it is not</h2>
     *
     * <b>Not a performance budget.</b> Quoting cost scales with kinds <i>times villagers</i>, and
     * the villager side is the caller's to bound by only inspecting merchants it already selected.
     * A cap here cannot make that safe and should not pretend to.
     *
     * <p>What it does do is make "bounded" <b>structural</b> rather than a promise in a comment.
     * Exceeding it means the caller broke its own contract — most plausibly by handing over raw
     * inventory, which is the failure this whole type exists to prevent — so it fails closed rather
     * than quietly quoting whatever arrived.
     */
    public static final int MAX_AUTHORIZED_INPUTS = 16;

    /**
     * Canonicalizes, de-duplicates, and refuses an over-large set.
     *
     * <p>{@code null} and empty entries are <b>ignored</b>, not rejected: a caller filtering its
     * backpack legitimately produces gaps, and this is the one rule both entry points follow.
     * Duplicates are collapsed <i>before</i> the capacity check, so repeating a kind cannot consume
     * capacity — the limit is on distinct kinds, which is what a source actually quotes.
     */
    public TradeOpportunityQuery {
        List<ItemStack> canonical = new ArrayList<>();
        if (authorizedSellInputs != null) {
            for (ItemStack input : authorizedSellInputs) {
                if (input == null || input.isEmpty()) {
                    continue;
                }
                ItemStack key = input.copyWithCount(1);
                boolean seen = false;
                for (ItemStack existing : canonical) {
                    if (ItemStack.isSameItemSameComponents(existing, key)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    if (canonical.size() == MAX_AUTHORIZED_INPUTS) {
                        // Refuse, never truncate. Silently dropping the tail would make which kinds
                        // a source may quote depend on the caller's iteration order - a permission
                        // decided by accident.
                        throw new IllegalArgumentException(
                                "at most " + MAX_AUTHORIZED_INPUTS + " distinct authorized sell "
                                        + "inputs; a larger set means the caller passed something "
                                        + "wider than its own disposition policy");
                    }
                    canonical.add(key);
                }
            }
        }
        authorizedSellInputs = List.copyOf(canonical);
    }

    /** Copies, so a source cannot reach the caller's stacks through the query. */
    @Override
    public List<ItemStack> authorizedSellInputs() {
        List<ItemStack> copies = new ArrayList<>(authorizedSellInputs.size());
        for (ItemStack input : authorizedSellInputs) {
            copies.add(input.copy());
        }
        return List.copyOf(copies);
    }

    /**
     * Same rules as the constructor.
     *
     * <p>This previously wrapped the argument in {@code List.copyOf}, which throws on a {@code null}
     * element <b>before</b> the constructor's tolerance can apply — so the two entry points
     * disagreed about the same input, and only one of them was documented. {@code ArrayList} accepts
     * nulls and lets the single canonicalization rule run.
     */
    public static TradeOpportunityQuery of(Collection<ItemStack> authorized) {
        return new TradeOpportunityQuery(
                authorized == null ? List.of() : new ArrayList<>(authorized));
    }

    /** A source with nothing to quote still answers; it simply has no authorized inputs. */
    public static TradeOpportunityQuery none() {
        return new TradeOpportunityQuery(List.of());
    }

    public boolean isEmpty() {
        return authorizedSellInputs.isEmpty();
    }
}
