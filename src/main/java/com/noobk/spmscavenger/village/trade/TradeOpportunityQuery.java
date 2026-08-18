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

    public static TradeOpportunityQuery of(Collection<ItemStack> authorized) {
        return new TradeOpportunityQuery(authorized == null ? List.of() : List.copyOf(authorized));
    }

    /** A source with nothing to quote still answers; it simply has no authorized inputs. */
    public static TradeOpportunityQuery none() {
        return new TradeOpportunityQuery(List.of());
    }

    public boolean isEmpty() {
        return authorizedSellInputs.isEmpty();
    }
}
