package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

/**
 * V2-H — <b>can the vanilla economy actually supply the demands Scavenger raises?</b>
 *
 * <h2>Why this exists before the fixture</h2>
 *
 * The locked VR-T2 fixture assumed a farmer carrot→emerald SELL would drive the chain, which the User
 * already corrected: production forbids ownerless emerald appetite, so the fixture needs a real
 * bounded purchase consumer. That correction raises a prior question nobody had asked — <b>does any
 * vanilla villager sell what a Scavenger consumer wants?</b>
 *
 * <p>{@code TradeFundingPlanner.chooseFundingTarget} only returns a target when some offer's
 * <i>result</i> satisfies the live {@code MaterialDemand}. If nothing sells that material, the
 * registrar can never choose {@code TRADE}, and every repair from R1–R8 is correct machinery serving
 * an empty market. That is the north star's first invariant: <i>every demand must have a consumer,
 * and every consumer a reachable supply.</i>
 *
 * <p>Read from the real {@link VillagerTrades#TRADES} table rather than from bytecode inspection —
 * an earlier window-heuristic over {@code javap} output contradicted itself on {@code IRON_INGOT},
 * which is exactly why a structural read is the wrong proof class here.
 */
class VanillaTradeSupplyProbeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Set<String> SOLD = new TreeSet<>();
    private static final Set<String> UNSAMPLED = new TreeSet<>();
    private static int sampled;

    /**
     * Every item any vanilla villager will hand over — <b>plus the listings we could not sample</b>.
     *
     * <p>Coverage is reported rather than assumed. The first version of this probe {@code break}ed
     * out of a listing on the first exception and reached 7 of ~128 items, which would have made
     * "iron is not purchasable" a conclusion drawn from a broken instrument. Per AV-1, an unsampled
     * listing is {@code UNKNOWN}, never {@code ABSENT}.
     */
    private static void probe() {
        RandomSource random = RandomSource.create(1234L);

        VillagerTrades.TRADES.forEach((profession, byLevel) -> byLevel.forEach((level, listings) -> {
            for (VillagerTrades.ItemListing listing : listings) {
                boolean any = false;
                for (int attempt = 0; attempt < 60; attempt++) {
                    try {
                        MerchantOffer offer = listing.getOffer(null, random);
                        if (offer != null && !offer.getResult().isEmpty()) {
                            SOLD.add(BuiltInRegistries.ITEM
                                    .getKey(offer.getResult().getItem()).toString());
                            any = true;
                        }
                    } catch (RuntimeException | LinkageError e) {
                        // Keep sampling: some listings randomise into an entity-dependent branch.
                    }
                }
                // `getOffer` needs a live Entity for several listing kinds - including
                // ItemsForEmeralds, which is the ONE that sells items. Reading its constructed
                // `itemStack` field is the authoritative source: it is the exact stack the offer
                // would hand over, already fixed at table-construction time.
                if (!any) {
                    any = readDeclaredResult(listing);
                }
                if (any) {
                    sampled++;
                } else {
                    UNSAMPLED.add(listing.getClass().getSimpleName());
                }
            }
        }));
    }

    /** Any {@code ItemStack} field on the listing is a result it can hand over. */
    private static boolean readDeclaredResult(VillagerTrades.ItemListing listing) {
        boolean found = false;
        for (java.lang.reflect.Field field : listing.getClass().getDeclaredFields()) {
            if (!net.minecraft.world.item.ItemStack.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                net.minecraft.world.item.ItemStack stack =
                        (net.minecraft.world.item.ItemStack) field.get(listing);
                if (stack != null && !stack.isEmpty()) {
                    SOLD.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                            + "  [" + listing.getClass().getSimpleName() + "." + field.getName() + "]");
                    found = true;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Recorded as unsampled below rather than silently treated as empty.
            }
        }
        return found;
    }

    /**
     * The finding, stated as an executable fact.
     *
     * <p>If this test ever fails because iron or charcoal became purchasable, that is good news and
     * the fixture assumption changes — which is precisely why it is pinned rather than written in
     * prose.
     */
    /**
     * <b>Finding: the vanilla economy does not supply either material Scavenger demands.</b>
     *
     * <p>283 of 286 listings sampled. The three that resist sampling —
     * {@code EmeraldsForVillagerTypeItem}, {@code EnchantBookForEmeralds},
     * {@code TreasureMapForEmeralds} — sell biome-specific goods, enchanted books and maps
     * respectively; none is a plausible source of an ingot or a fuel, so the negative holds with
     * those three named rather than hidden.
     *
     * <p>Vanilla sells the finished {@code iron_pickaxe}, {@code iron_axe}, {@code iron_sword} and
     * the iron armour set — but never {@code iron_ingot}, and no {@code charcoal} or {@code coal} at
     * all. {@code chooseFundingTarget} only returns a target when an offer's <b>result</b> satisfies
     * the live {@code MaterialDemand}, so with today's demands {@code TradeDemandRegistrar} cannot
     * reach {@code TRADE} in an uncontaminated vanilla world.
     *
     * <p>Pinned as a test so the constraint is executable: if a future version or the fixture design
     * makes either material purchasable, this fails and the V2-H assumption is revisited
     * deliberately rather than by accident.
     */
    @Test
    void mustHappen_vanillaSuppliesNeitherMaterialScavengerDemands() {
        probe();

        assertFalse(SOLD.isEmpty(), "the probe must actually reach the vanilla trade table");
        assertTrue(SOLD.size() > 150,
                "coverage guard: the first version of this probe reached 7 items and would have "
                        + "made a broken instrument look like a finding. Observed: " + SOLD.size());
        assertEquals(Set.of("EmeraldsForVillagerTypeItem", "EnchantBookForEmeralds",
                        "TreasureMapForEmeralds"), UNSAMPLED,
                "the unsampled set must stay known and named - an unsampled listing is UNKNOWN, "
                        + "never ABSENT");

        assertFalse(sellsExactly("minecraft:iron_ingot"),
                "no vanilla villager sells iron ingots - only finished iron tools and armour");
        assertFalse(sellsExactly("minecraft:charcoal"), "no vanilla villager sells charcoal");
        assertFalse(sellsExactly("minecraft:coal"), "no vanilla villager sells coal");

        // The supply that DOES exist for the iron tool frontier, if the consumer ever wants the
        // finished tool rather than its input.
        assertTrue(sellsExactly("minecraft:iron_pickaxe"), "the toolsmith sells the tool itself");
    }

    private static boolean sellsExactly(String itemId) {
        return SOLD.stream().anyMatch(entry -> entry.equals(itemId) || entry.startsWith(itemId + " "));
    }
}
