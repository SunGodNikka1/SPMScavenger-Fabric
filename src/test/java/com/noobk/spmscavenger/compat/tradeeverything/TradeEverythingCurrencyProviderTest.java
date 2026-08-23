package com.noobk.spmscavenger.compat.tradeeverything;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Independent activation contract for TE denomination behavior. */
class TradeEverythingCurrencyProviderTest {

    @Test
    void mustHappen_sourceValidatedVersionsActivateCurrencyCapability() {
        assertTrue(TradeEverythingCurrencyProvider.supportsVersion("0.3.0"));
        assertTrue(TradeEverythingCurrencyProvider.supportsVersion("0.8.0"));
    }

    @Test
    void mustNotHappen_unknownVersionsInheritCurrencyCompatibility() {
        assertFalse(TradeEverythingCurrencyProvider.supportsVersion(null));
        assertFalse(TradeEverythingCurrencyProvider.supportsVersion("0.7.0"));
        assertFalse(TradeEverythingCurrencyProvider.supportsVersion("0.8.1"));
        assertFalse(TradeEverythingCurrencyProvider.supportsVersion("0.9.0"));
    }

    @Test
    void mustHappen_currencyActivationIsIndependentOfQuoteResolution() throws IOException {
        String compat = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/compat/tradeeverything/TradeEverythingCompat.java"));
        int currencyInstall = compat.indexOf("MerchantCurrencyPolicies.installOptionalProvider");
        int quoteResolve = compat.indexOf("bridge = ReflectiveTradeEverythingBridge.tryResolve()");

        assertTrue(currencyInstall >= 0, "validated currency capability must install");
        assertTrue(quoteResolve > currencyInstall,
                "quote resolution may fail later without revoking denomination authority");

        String provider = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/compat/tradeeverything/TradeEverythingCurrencyProvider.java"));
        assertFalse(provider.contains("QuoteBridge"));
        assertFalse(provider.contains("OfferQuoter"));
        assertFalse(provider.contains("ensureIndexed"));
    }
}
