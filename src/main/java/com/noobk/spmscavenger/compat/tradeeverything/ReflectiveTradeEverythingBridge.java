package com.noobk.spmscavenger.compat.tradeeverything;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * D-VR-077 step 6 — the only class in this mod that names a Trade Everything type, and it names them
 * as <b>strings</b>.
 *
 * <h2>Pinned shapes</h2>
 *
 * Verified against Trade Everything v0.3.0, commit {@code fe305e6}:
 *
 * <pre>
 * games.brennan.tradeeverything.trade.RecipeValues#ensureIndexed(MinecraftServer)          -&gt; void
 * games.brennan.tradeeverything.trade.OfferQuoter#quote(AbstractVillager, ItemStack,
 *                                                      MerchantOffers)                     -&gt; Optional
 * </pre>
 *
 * Both are validated at handshake time, including the return types. A signature drift in a future
 * upstream release therefore disables compatibility at load rather than throwing from inside a
 * planning tick.
 *
 * <h2>Degrade, never break</h2>
 *
 * Missing class, missing method, wrong return type, {@code LinkageError} — all mean
 * {@link #available()} is false, no source is registered, and vanilla trading is untouched. A quote
 * that throws at runtime is logged once and treated as "no opportunity", because a market source
 * failing is not a reason to take the mob's trading away.
 *
 * <p>What it must never do is fail <i>silently</i>: found, absent and broken are all logged once at
 * startup, so "Trade Everything is installed but Scavenger ignores it" is diagnosable from the log
 * rather than from bisecting behaviour.
 */
public final class ReflectiveTradeEverythingBridge implements QuoteBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("spmscavenger/te-bridge");

    private static final String RECIPE_VALUES = "games.brennan.tradeeverything.trade.RecipeValues";
    private static final String OFFER_QUOTER = "games.brennan.tradeeverything.trade.OfferQuoter";

    private final Method ensureIndexed;
    private final Method quote;
    private boolean quoteFailureLogged;

    private ReflectiveTradeEverythingBridge(Method ensureIndexed, Method quote) {
        this.ensureIndexed = ensureIndexed;
        this.quote = quote;
    }

    /** An unusable bridge, so callers never have to hold a null. */
    static QuoteBridge unavailable() {
        return new ReflectiveTradeEverythingBridge(null, null);
    }

    /**
     * Resolve and validate every pinned shape, or return an unavailable bridge.
     *
     * <p>Called only after the runtime mod-present check, so an absent mod does not produce a scary
     * log line — but the shapes are still verified independently, because "the mod is installed" and
     * "the API this port pinned is still there" are different facts.
     */
    static QuoteBridge tryResolve() {
        try {
            Class<?> recipeValues = Class.forName(RECIPE_VALUES);
            Class<?> offerQuoter = Class.forName(OFFER_QUOTER);

            Method indexed = recipeValues.getMethod("ensureIndexed", MinecraftServer.class);
            Method quoted = offerQuoter.getMethod(
                    "quote", AbstractVillager.class, ItemStack.class, MerchantOffers.class);

            if (indexed.getReturnType() != void.class) {
                return refuse("RecipeValues.ensureIndexed returns "
                        + indexed.getReturnType().getName() + ", expected void");
            }
            if (quoted.getReturnType() != Optional.class) {
                return refuse("OfferQuoter.quote returns "
                        + quoted.getReturnType().getName() + ", expected Optional");
            }
            LOGGER.info("[spmscavenger] Trade Everything bridge ready (pinned v0.3.0 shapes found)");
            return new ReflectiveTradeEverythingBridge(indexed, quoted);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError | RuntimeException e) {
            return refuse("pinned Trade Everything API not found: " + e);
        }
    }

    private static QuoteBridge refuse(String why) {
        LOGGER.warn("[spmscavenger] Trade Everything is installed but its pinned API did not "
                + "validate - trading continues with vanilla offers only. {}", why);
        return unavailable();
    }

    @Override
    public boolean available() {
        return ensureIndexed != null && quote != null;
    }

    @Override
    public void ensureIndexed(MinecraftServer server) {
        if (!available() || server == null) {
            return;
        }
        try {
            ensureIndexed.invoke(null, server);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logQuoteFailureOnce("ensureIndexed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<MerchantOffer> quote(
            Villager villager, ItemStack input, MerchantOffers offers) {
        if (!available() || villager == null || input == null || input.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object result = quote.invoke(null, villager, input, offers);
            // The object upstream produced, handed straight on. Rebuilding it here would strip the
            // synthetic marker its own afterTrade hook keys on.
            return result instanceof Optional<?> optional
                    ? (Optional<MerchantOffer>) optional
                    : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logQuoteFailureOnce("quote", e);
            return Optional.empty();
        }
    }

    /** Once. A per-tick failure must not turn a compatibility problem into a log-flood problem. */
    private void logQuoteFailureOnce(String what, Throwable cause) {
        if (!quoteFailureLogged) {
            quoteFailureLogged = true;
            LOGGER.warn("[spmscavenger] Trade Everything {} failed; treating it as 'no opportunity' "
                    + "from now on. Vanilla trading is unaffected.", what, cause);
        }
    }
}
