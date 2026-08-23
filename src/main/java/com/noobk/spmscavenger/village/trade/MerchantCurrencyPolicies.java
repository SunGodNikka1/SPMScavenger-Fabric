package com.noobk.spmscavenger.village.trade;

import java.util.Objects;

/** One process-wide currency authority, defaulting to vanilla when no validated provider installs. */
public final class MerchantCurrencyPolicies {

    private static volatile MerchantCurrencyPolicy active = VanillaMerchantCurrency.INSTANCE;

    private MerchantCurrencyPolicies() {
    }

    public static MerchantCurrencyPolicy current() {
        return active;
    }

    /** Called once by a separately validated optional compatibility capability during mod init. */
    public static void installOptionalProvider(MerchantCurrencyPolicy policy) {
        active = Objects.requireNonNull(policy, "policy");
    }

}
