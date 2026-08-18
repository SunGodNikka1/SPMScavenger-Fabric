package com.noobk.spmscavenger.village.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * D-VR-077 step 5/6 — the registry that turns carried provenance back into a resolver.
 *
 * <h2>Why a lookup rather than storing the source object</h2>
 *
 * Attempt evidence carries a {@link TradeSourceKey}, not a source instance, because it outlives a
 * planning pass and is compared, logged and reasoned about. Resolving the key here keeps sources
 * stateless and keeps the evidence a value.
 *
 * <h2>The optional source is registered, never referenced</h2>
 *
 * This class is on the eagerly-loaded common path, so it must not name a Trade Everything type — not
 * even to build one. Writing
 *
 * <pre>
 * static final TradeEverythingTradeSource TE = ...;              // or
 * List.of(VanillaTradeSource.INSTANCE, TradeEverythingTradeSource.INSTANCE)
 * </pre>
 *
 * would resolve that class when this one loads, which is a {@code NoClassDefFoundError} at init for
 * every user without the mod. An {@code isModLoaded} guard cannot save it, because resolution
 * happens before the guard runs. So the optional slot is <b>filled from outside</b> by the compat
 * package after its own presence check and API handshake; this class only ever sees the interface.
 *
 * <h2>Fail closed</h2>
 *
 * {@link #of} returns {@link Optional}. An unregistered {@code TRADE_EVERYTHING} resolves to empty,
 * never to vanilla: a re-quoted offer has no address on a villager's board, so handing it to the
 * board resolver would return "gone" and be read as a market race rather than a missing source.
 */
public final class TradeSources {

    private static volatile TradeOpportunitySource tradeEverything;

    private TradeSources() {
    }

    /**
     * Install the optional source. Called once, from the compat package, only after the mod-present
     * check and the pinned-API handshake have both passed.
     */
    public static void registerTradeEverything(TradeOpportunitySource source) {
        if (source != null && source.key() == TradeSourceKey.TRADE_EVERYTHING) {
            tradeEverything = source;
        }
    }

    /** Test seam: forget the optional source, so absence is reproducible in a suite. */
    public static void clearOptionalSources() {
        tradeEverything = null;
    }

    /**
     * Every source a planning round may draw candidates from, in stable order.
     *
     * <p>Vanilla is always first, so installing Trade Everything cannot reorder the vanilla
     * candidates a round was already producing — it appends, and the goal's flat ordinal follows.
     */
    public static List<TradeOpportunitySource> all() {
        List<TradeOpportunitySource> sources = new ArrayList<>(2);
        sources.add(VanillaTradeSource.INSTANCE);
        TradeOpportunitySource optional = tradeEverything;
        if (optional != null) {
            sources.add(optional);
        }
        return List.copyOf(sources);
    }

    public static Optional<TradeOpportunitySource> of(TradeSourceKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return switch (key) {
            case VANILLA -> Optional.of(VanillaTradeSource.INSTANCE);
            case TRADE_EVERYTHING -> Optional.ofNullable(tradeEverything);
        };
    }
}
