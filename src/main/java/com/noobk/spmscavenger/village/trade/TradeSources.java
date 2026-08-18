package com.noobk.spmscavenger.village.trade;

import java.util.List;

/**
 * D-VR-077 step 5 — the registry that turns carried provenance back into a resolver.
 *
 * <h2>Why a lookup rather than storing the source object</h2>
 *
 * Attempt evidence carries a {@link TradeSourceKey}, not a source instance, because it outlives a
 * planning pass and is compared, logged and reasoned about. Resolving the key here keeps sources
 * stateless singletons and keeps the evidence a value.
 *
 * <h2>The switch is deliberately exhaustive</h2>
 *
 * {@link #of} switches over the enum with no default. Adding {@code TRADE_EVERYTHING} in step 6 will
 * therefore <b>fail to compile</b> until its source is registered — which is the point. A default
 * branch would silently route a Trade Everything offer to the vanilla board resolver, where its
 * {@code Requote} ref has no address and revalidation would return empty. That failure looks exactly
 * like "the offer went away", so it would be diagnosed as a market race rather than a missing
 * registration.
 */
public final class TradeSources {

    private TradeSources() {
    }

    /** Every source a planning round may draw candidates from, in stable order. */
    public static List<TradeOpportunitySource> all() {
        return List.of(VanillaTradeSource.INSTANCE);
    }

    public static TradeOpportunitySource of(TradeSourceKey key) {
        return switch (key) {
            case VANILLA -> VanillaTradeSource.INSTANCE;
        };
    }
}
