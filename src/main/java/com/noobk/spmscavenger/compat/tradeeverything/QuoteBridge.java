package com.noobk.spmscavenger.compat.tradeeverything;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * D-VR-077 step 6 — the entire surface this mod uses from Trade Everything.
 *
 * <h2>Why an interface for two calls</h2>
 *
 * Two reasons, and the second is the one that matters.
 *
 * <p>First, it keeps every upstream class reference inside one reflective implementation. Nothing on
 * the common path names a Trade Everything type, so common classes load normally when the mod is
 * absent — a direct reference would be a {@code NoClassDefFoundError} at init for every user without
 * it, and an {@code isModLoaded} guard cannot help because resolution happens before the guard runs.
 *
 * <p>Second, it makes {@code TradeEverythingTradeSource} testable. The source's real work is
 * Q1/Q2 strictness, {@code Requote} identity and exact-object pass-through, and none of that needs
 * Trade Everything to be on the JUnit classpath — which it is not, since the dependency is
 * {@code modCompileOnly}. A fake bridge proves the source; the reflective bridge gets the step-7
 * runtime witness. Without this seam the source would be untestable and would arrive with the
 * runtime evidence as its first evidence.
 *
 * <h2>Fail closed, never fail silent</h2>
 *
 * {@link #available()} is the handshake result. A missing class, a missing method, a changed return
 * type or a linkage failure all mean unavailable — no source is registered, no opportunity is
 * produced, and vanilla trading continues untouched.
 */
public interface QuoteBridge {

    /** Whether the pinned upstream shapes were all found and validated. */
    boolean available();

    /**
     * {@code RecipeValues.ensureIndexed(MinecraftServer)}.
     *
     * <p>Upstream memoizes on {@code (RecipeManager, config)} identity, so this is cheap when warm
     * and rebuilds when either changes. Called defensively before every quote because
     * {@code TradeEverythingApi.reload()} is public and can replace the config at any time without
     * telling us.
     */
    void ensureIndexed(MinecraftServer server);

    /**
     * {@code OfferQuoter.quote(AbstractVillager, ItemStack, MerchantOffers)}.
     *
     * @return the offer <b>upstream produced</b>. Never rebuild it: synthetic offers are marked with
     *     a mixin-injected instance field, and a constructor call from the same field values
     *     silently drops the marker, after which upstream's own {@code afterTrade} hook stops
     *     recognising the offer.
     */
    Optional<MerchantOffer> quote(Villager villager, ItemStack input, MerchantOffers offers);
}
