package com.noobk.spmscavenger.compat.tradeeverything;

import com.noobk.spmscavenger.village.trade.MerchantCurrencyPolicies;
import com.noobk.spmscavenger.village.trade.TradeSources;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * D-VR-077 step 6 — the single entry point that decides whether Trade Everything participates.
 *
 * <h2>Two independent checks</h2>
 *
 * <pre>
 * mod present?         FabricLoader.isModLoaded("tradeeverything")
 * pinned API intact?   ReflectiveTradeEverythingBridge.tryResolve()
 * </pre>
 *
 * Both must pass. They are genuinely different questions: a future release could be installed and
 * still have moved the methods this port pinned, and treating "installed" as "compatible" is how a
 * compat layer starts throwing from inside a planning tick.
 *
 * <p>The mod id is <b>{@code tradeeverything}</b>, read from the pinned artifact's
 * {@code fabric.mod.json}. It is <i>not</i> the Modrinth slug {@code trade-everything}, which is
 * what the Gradle coordinate uses — assuming they matched would have disabled compatibility
 * permanently and silently.
 *
 * <h2>Absence is not an error</h2>
 *
 * No mod, no source, no log noise beyond one informational line; vanilla trading is untouched.
 * Installed-but-incompatible is a warning, because that one is worth telling somebody about.
 */
public final class TradeEverythingCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("spmscavenger/te-compat");

    /** From the pinned artifact's fabric.mod.json, not from the Modrinth slug. */
    public static final String MOD_ID = "tradeeverything";

    private static QuoteBridge bridge = ReflectiveTradeEverythingBridge.unavailable();
    private static volatile String installedVersion;
    private static volatile boolean currencyCapabilityActive;

    private TradeEverythingCompat() {
    }

    /**
     * Called once from the mod initializer.
     *
     * <p>This class is the first thing that touches a Trade Everything name, and it does so only
     * through {@code Class.forName} inside the bridge — so nothing on the common trade path resolves
     * an upstream class, whether or not the mod is installed.
     */
    public static void install() {
        var container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty()) {
            LOGGER.info("[spmscavenger] Trade Everything not installed - vanilla trade offers only");
            return;
        }
        String version = container.get().getMetadata().getVersion().getFriendlyString();
        installedVersion = version;
        if (TradeEverythingCurrencyProvider.supportsVersion(version)) {
            MerchantCurrencyPolicies.installOptionalProvider(new TradeEverythingCurrencyProvider());
            currencyCapabilityActive = true;
            LOGGER.info("[spmscavenger] Trade Everything {} emerald-block currency enabled", version);
        } else {
            LOGGER.warn("[spmscavenger] Trade Everything {} currency behavior is not source-validated; "
                    + "emerald blocks remain ordinary items", version);
        }

        // Quote capability is deliberately independent of currency capability. A later failure in
        // RecipeValues/OfferQuoter disables synthetic opportunities only; it does not revoke a
        // source-validated denomination contract supplied by the installed mod version.
        bridge = ReflectiveTradeEverythingBridge.tryResolve();
        if (!bridge.available()) {
            return;
        }
        TradeSources.registerTradeEverything(new TradeEverythingTradeSource(bridge));
        LOGGER.info("[spmscavenger] Trade Everything registered as an optional trade source");
    }

    /** Temporary witness readout: installed metadata version, independent of either capability. */
    public static String installedVersion() {
        return installedVersion;
    }

    /** Temporary witness readout: whether the separately version-gated denomination installed. */
    public static boolean currencyCapabilityActive() {
        return currencyCapabilityActive;
    }

    /** Temporary witness readout: current reflective quote health, which may fail closed later. */
    public static boolean quoteBridgeHealthy() {
        return bridge.available();
    }

    /**
     * Prewarm upstream's recipe index at a known lifecycle boundary.
     *
     * <p>Cold {@code ensureIndexed} measured at 11.739 ms against 0.004 ms warm, so paying it during
     * server start is worth it. It is a prewarm, not a guarantee: {@code TradeEverythingApi.reload()}
     * is public and replaces the config object upstream memoizes on, so a defensive call before every
     * quote can still occasionally be the cold one. That is accepted rather than engineered around —
     * an asynchronous indexing subsystem for a rare external event would be more machinery than the
     * problem.
     */
    public static void prewarm(MinecraftServer server) {
        if (bridge.available()) {
            bridge.ensureIndexed(server);
        }
    }
}
