package com.noobk.spmscavenger.validation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;

/** Immutable witness description of offer terms; never used for production authorization. */
public record V4OfferFingerprint(
        String costAItem,
        int costACount,
        String costAComponents,
        String costBItem,
        int costBCount,
        String costBComponents,
        String resultItem,
        int resultCount,
        String resultComponents,
        int uses,
        int maxUses) {

    public static V4OfferFingerprint of(MerchantOffer offer) {
        if (offer == null) {
            return empty();
        }
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.assemble();
        return new V4OfferFingerprint(
                itemId(costA), costA.getCount(), components(costA),
                itemId(costB), costB.getCount(), components(costB),
                itemId(result), result.getCount(), components(result),
                offer.getUses(), offer.getMaxUses());
    }

    public static V4OfferFingerprint of(OfferSnapshot offer) {
        if (offer == null) {
            return empty();
        }
        return new V4OfferFingerprint(
                itemId(offer.costA()), offer.costA().getCount(), components(offer.costA()),
                itemId(offer.costB()), offer.costB().getCount(), components(offer.costB()),
                itemId(offer.result()), offer.result().getCount(), components(offer.result()),
                offer.uses(), offer.maxUses());
    }

    static V4OfferFingerprint simple(
            String costItem, int costCount, String resultItem, int resultCount) {
        return new V4OfferFingerprint(
                costItem, costCount, "{}", "minecraft:air", 0, "{}",
                resultItem, resultCount, "{}", 0, 12);
    }

    static V4OfferFingerprint empty() {
        return simple("minecraft:air", 0, "minecraft:air", 0);
    }

    public String compact() {
        String second = costBCount > 0 ? " + " + costBCount + " " + costBItem : "";
        return costACount + " " + costAItem + second + " -> "
                + resultCount + " " + resultItem + " uses=" + uses + "/" + maxUses;
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? "minecraft:air"
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String components(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "{}" : stack.getComponentsPatch().toString();
    }
}
