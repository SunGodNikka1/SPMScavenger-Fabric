package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * D-VR-077 step 4 — the source contract and its data types.
 *
 * <p>No source implementations exist yet, so nothing here proves behaviour. What it proves is that
 * the <b>identity</b> and <b>permission</b> types are safe to build on — because both are already
 * load-bearing for execution the moment step 5 arrives, and both have a failure mode that compiles
 * silently.
 */
class TradeOpportunityContractTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack damaged(int damage) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(damage);
        return stack;
    }

    // ------------------------------------------------------------------ Requote identity

    /**
     * The reason this cannot be a naive record.
     *
     * <p>{@code ItemStack} inherits identity equality, so the generated {@code equals} would call
     * two independently copied but component-identical stacks different. {@code SellFundingLeg}
     * compares {@code attempted.ref().equals(offer.ref())} at the execution boundary, so that would
     * surface as a mob refusing its own authorized trade — the step-2 regression by another road.
     */
    @Test
    void mustHappen_independentlyCopiedKeysCompareEqual() {
        OfferRef.Requote fromOneStack = new OfferRef.Requote(new ItemStack(Items.OAK_LOG, 64));
        OfferRef.Requote fromAnother = new OfferRef.Requote(new ItemStack(Items.OAK_LOG, 64));

        assertEquals(fromOneStack, fromAnother, "same item, same components, different objects");
        assertEquals(fromOneStack.hashCode(), fromAnother.hashCode(),
                "and the hash must agree, or a map keyed by this ref loses entries silently");
    }

    /** Held count is inventory's business, never identity's. */
    @Test
    void mustNotHappen_theHeldCountReachesRequoteIdentity() {
        OfferRef.Requote full = new OfferRef.Requote(new ItemStack(Items.OAK_LOG, 64));
        OfferRef.Requote partial = new OfferRef.Requote(new ItemStack(Items.OAK_LOG, 42));

        assertEquals(full, partial, "64x and 42x oak_log are the same requote key");
        assertEquals(full.hashCode(), partial.hashCode());
        assertEquals(1, full.inputKey().getCount(), "canonicalized on the way in");
    }

    /** Components decide the price, so they decide identity. */
    @Test
    void mustNotHappen_differentComponentsCompareEqual() {
        assertNotEquals(new OfferRef.Requote(damaged(0)), new OfferRef.Requote(damaged(37)),
                "a damaged pickaxe is not the same quotable input as a pristine one");
        assertNotEquals(new OfferRef.Requote(new ItemStack(Items.OAK_LOG)),
                new OfferRef.Requote(new ItemStack(Items.SPRUCE_LOG)));
    }

    /** Copied in and copied out: neither end can reach the stored identity. */
    @Test
    void mustNotHappen_mutationAltersAStoredRequoteIdentity() {
        ItemStack caller = new ItemStack(Items.IRON_PICKAXE);
        OfferRef.Requote ref = new OfferRef.Requote(caller);

        caller.setDamageValue(99);
        assertEquals(new OfferRef.Requote(new ItemStack(Items.IRON_PICKAXE)), ref,
                "mutating the stack that was passed in must not move the identity");

        ref.inputKey().setDamageValue(99);
        assertEquals(new OfferRef.Requote(new ItemStack(Items.IRON_PICKAXE)), ref,
                "and neither may mutating the stack the accessor handed back");
    }

    /** An empty input identifies no offer, so it fails closed like a negative board index. */
    @Test
    void mustNotHappen_anEmptyRequoteKeyIsConstructible() {
        assertThrows(IllegalArgumentException.class, () -> new OfferRef.Requote(ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new OfferRef.Requote(null));
    }

    /** The two ref kinds are different identities even when both describe the same trade. */
    @Test
    void mustNotHappen_aBoardRefEqualsARequoteRef() {
        assertNotEquals(OfferRef.board(0), OfferRef.requote(new ItemStack(Items.OAK_LOG)));
    }

    // ------------------------------------------------------------------ the query

    @Test
    void mustHappen_authorizedInputsAreCanonicalizedAndDeduplicated() {
        TradeOpportunityQuery query = TradeOpportunityQuery.of(List.of(
                new ItemStack(Items.OAK_LOG, 64),
                new ItemStack(Items.OAK_LOG, 12),
                new ItemStack(Items.STICK, 5),
                ItemStack.EMPTY));

        assertEquals(2, query.authorizedSellInputs().size(),
                "one entry per stack KIND - the query says what may be quoted, not how much");
        assertTrue(query.authorizedSellInputs().stream().allMatch(s -> s.getCount() == 1),
                "count is canonicalized away; quantity is inventory's, not permission's");
    }

    @Test
    void mustNotHappen_theQueryExposesTheCallersMutableStacks() {
        ItemStack caller = new ItemStack(Items.OAK_LOG, 64);
        TradeOpportunityQuery query = TradeOpportunityQuery.of(List.of(caller));

        caller.setCount(1);
        caller.setDamageValue(5);
        query.authorizedSellInputs().get(0).setCount(64);

        List<ItemStack> after = query.authorizedSellInputs();
        assertEquals(1, after.size());
        assertEquals(1, after.get(0).getCount(), "neither end can mutate what may be quoted");
        assertTrue(ItemStack.isSameItemSameComponents(after.get(0), new ItemStack(Items.OAK_LOG)));
    }

    // ------------------------------------------------------------------ the contract shape

    /**
     * Neither method may take inventory.
     *
     * <p>{@code offers} was tightened first; {@code revalidate} followed for the same reason.
     * Neither proven source needs a backpack to establish market truth, and handing one over creates
     * the ownership temptation the query exists to remove.
     */
    @Test
    void mustNotHappen_aSourceReceivesRawInventory() {
        assertTrue(Arrays.stream(TradeOpportunitySource.class.getMethods())
                        .flatMap(m -> Arrays.stream(m.getParameterTypes()))
                        .noneMatch(Container.class::isAssignableFrom),
                "a market source reports opportunity; it does not decide spend permission");
        assertEquals(Optional.class,
                Arrays.stream(TradeOpportunitySource.class.getMethods())
                        .filter(m -> m.getName().equals("revalidate")).findFirst().orElseThrow()
                        .getReturnType(),
                "revalidate hands back the live object to execute, or nothing");
    }

    /** Step 4 is the contract only; a source implementation arriving here would hide step 5. */
    @Test
    void mustHappen_noSourceImplementationExistsYet() throws java.io.IOException {
        try (var paths = java.nio.file.Files.walk(
                java.nio.file.Path.of("src/main/java/com/noobk/spmscavenger"))) {
            List<String> implementors = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return java.nio.file.Files.readString(path)
                                    .contains("implements TradeOpportunitySource");
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .toList();
            assertEquals(List.of(), implementors,
                    "VanillaTradeSource is step 5, deliberately separate so a vanilla behaviour "
                            + "change cannot hide behind a new interface in the same commit");
        }
    }

    // ------------------------------------------------------------------ semantic fields

    private static MerchantOffer offer(int xp, int demand) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, xp, 0f, demand);
    }

    @Test
    void mustHappen_theLockedSemanticFieldsAreCaptured() {
        OfferSnapshot snapshot = OfferSnapshot.of(0, offer(7, 3));

        assertEquals(7, snapshot.xp());
        assertEquals(3, snapshot.demand());
        assertEquals(0f, snapshot.priceMultiplier());
        assertEquals(0, snapshot.specialPriceDiff());
        assertTrue(snapshot.rewardExp(), "vanilla defaults reward-exp on and nothing disables it");
        assertEquals(12, snapshot.maxUses());
    }

    /**
     * The rule that keeps step 4 free of behaviour change.
     *
     * <p>{@code matchesLive} asks <b>transaction equivalence</b>: is the trade I am about to perform
     * the trade I authorized? {@code demand} and {@code xp} do not change that, and they legitimately
     * move between selection and execution. Reading them here would abort vanilla trades that
     * succeed today — the strict comparison belongs to an independently re-quoting source, which has
     * no shared object identity to fall back on.
     */
    @Test
    void mustNotHappen_semanticDriftStrengthensVanillaMatchesLive() {
        OfferSnapshot planned = OfferSnapshot.of(0, offer(0, 0));
        MerchantOffer drifted = offer(9, 6);

        assertTrue(planned.matchesLive(drifted),
                "same effective cost and result - vanilla must still accept this");
        assertNotEquals(planned.xp(), OfferSnapshot.of(0, drifted).xp(),
                "while the semantic snapshot did record the difference");
        assertNotEquals(planned.demand(), OfferSnapshot.of(0, drifted).demand());
    }

    // ------------------------------------------------------------------ provenance fails closed

    @Test
    void mustNotHappen_carriedFundingOmitsItsSource() {
        assertThrows(NullPointerException.class, () -> new TradeAttemptFunding(
                        net.minecraft.resources.ResourceLocation
                                .fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade"),
                        null, null, null, 13),
                "once step 5 dispatches on buySource, null has exactly one plausible-looking "
                        + "repair - inferring it from the ref - and that is what D-VR-077 rejects");
    }

    @Test
    void mustHappen_theSourceKeyStillHasOnlyVanilla() {
        assertEquals(1, TradeSourceKey.values().length,
                "TRADE_EVERYTHING arrives in step 6, not with the contract");
        assertFalse(Arrays.toString(TradeSourceKey.values()).contains("TRADE_EVERYTHING"));
    }
}
