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
 * Source-verified against Trade Everything v0.3.0 ({@code fe305e6}) and v0.8.0
 * ({@code a67795d598ceb3afa7adc3c33e98407cbc177b71}):
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
 * {@link #available()} is false at startup, no source is registered, and vanilla trading is
 * untouched.
 *
 * <p>A failure <i>after</i> a successful handshake disables the bridge <b>permanently</b> for the
 * session. The current call returns no opportunity, {@link #available()} becomes false, and every
 * later call is a no-op — so a broken reflective method is invoked exactly once, not once per
 * planning pass. The registered source stays in the registry and goes inert.
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
    /**
     * Runtime health, separate from resolution success.
     *
     * <p>The handshake proves the pinned shapes <i>exist</i>. It cannot prove they will keep
     * working: an upstream change, a mixin conflict, or a state assumption we do not share can make
     * an invocation throw long after startup. The first such failure disables the bridge for good.
     *
     * <p>This was the repair. The previous version suppressed repeated <b>logs</b> and said "no
     * opportunity from now on" while {@code available()} stayed true — so the broken call was
     * re-invoked on every planning pass, and the one warning that would have explained it had
     * already been printed. Quiet is not the same as closed.
     */
    private volatile boolean healthy = true;
    private volatile boolean failureLogged;

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
            LOGGER.info("[spmscavenger] Trade Everything quote bridge ready "
                    + "(source-validated v0.3.0/v0.8.0 shapes found)");
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
        return healthy && ensureIndexed != null && quote != null;
    }

    /**
     * @param server non-null by caller contract — {@code TradeEverythingTradeSource.usable} checks
     *     it, and the prewarm hook receives it from {@code SERVER_STARTED}. Deliberately not
     *     re-guarded here: a guard that cannot fire in production is dead weight that also makes the
     *     invocation untestable.
     */
    @Override
    public void ensureIndexed(MinecraftServer server) {
        if (!available()) {
            return;
        }
        try {
            ensureIndexed.invoke(null, server);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failClosed("ensureIndexed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<MerchantOffer> quote(
            Villager villager, ItemStack input, MerchantOffers offers) {
        // available() first, so a bridge disabled by a previous failure never invokes again - this
        // is what makes a failed ensureIndexed genuinely stop the quotation that follows it.
        if (!available() || input == null || input.isEmpty()) {
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
            failClosed("quote", e);
            return Optional.empty();
        }
    }

    /**
     * Disable permanently, then warn once.
     *
     * <p>Both halves matter and they are different. Disabling stops the reflective call being
     * re-invoked every planning pass — an exception flood on the server thread is not better than a
     * log flood, it is worse, because it is invisible. Logging once keeps the compatibility problem
     * from becoming a log problem. The order is deliberate: health is cleared before anything else
     * can observe it.
     *
     * <p>The registered source stays in the registry and simply goes inert, because
     * {@code TradeEverythingTradeSource.usable} consults {@code available()} on every call. Vanilla
     * is untouched throughout.
     */
    private void failClosed(String what, Throwable cause) {
        healthy = false;
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn("[spmscavenger] Trade Everything {} failed; the compatibility bridge is now "
                    + "DISABLED for this session and will not be retried. Vanilla trading is "
                    + "unaffected.", what, cause);
        }
    }

    /** Test seam: build a bridge over arbitrary static methods, bypassing upstream resolution. */
    static QuoteBridge overMethods(Method ensureIndexed, Method quote) {
        return new ReflectiveTradeEverythingBridge(ensureIndexed, quote);
    }
}
