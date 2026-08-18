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

    // ------------------------------------------------------------------ bounded, structurally

    /** Distinct wood kinds, so each is a genuinely different quotable input. */
    private static List<ItemStack> distinctKinds(int howMany) {
        List<net.minecraft.world.item.Item> pool = List.of(
                Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS,
                Items.CHERRY_PLANKS, Items.STICK, Items.BAMBOO_PLANKS);
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            stacks.add(new ItemStack(pool.get(i), 64));
        }
        return stacks;
    }

    @Test
    void mustHappen_theMaximumDistinctKindsIsAccepted() {
        TradeOpportunityQuery query = TradeOpportunityQuery.of(
                distinctKinds(TradeOpportunityQuery.MAX_AUTHORIZED_INPUTS));

        assertEquals(TradeOpportunityQuery.MAX_AUTHORIZED_INPUTS,
                query.authorizedSellInputs().size(), "the cap is inclusive");
    }

    /**
     * Refuse, never truncate.
     *
     * <p>Dropping the tail would make which kinds a source may quote depend on the caller's
     * iteration order — a permission decided by accident, which is the failure this type exists to
     * prevent.
     */
    @Test
    void mustNotHappen_anOverLargeSetIsSilentlyTruncated() {
        List<ItemStack> tooMany = distinctKinds(TradeOpportunityQuery.MAX_AUTHORIZED_INPUTS + 1);

        assertThrows(IllegalArgumentException.class, () -> TradeOpportunityQuery.of(tooMany));
        assertThrows(IllegalArgumentException.class, () -> new TradeOpportunityQuery(tooMany));
    }

    /** The limit is on distinct KINDS, so repeating one cannot use up capacity. */
    @Test
    void mustNotHappen_duplicatesConsumeCapacity() {
        List<ItemStack> withDuplicates =
                new java.util.ArrayList<>(distinctKinds(TradeOpportunityQuery.MAX_AUTHORIZED_INPUTS));
        for (int i = 0; i < 40; i++) {
            withDuplicates.add(new ItemStack(Items.OAK_LOG, i + 1));
        }

        assertEquals(TradeOpportunityQuery.MAX_AUTHORIZED_INPUTS,
                TradeOpportunityQuery.of(withDuplicates).authorizedSellInputs().size(),
                "40 more oak_log stacks are still one kind");
    }

    /**
     * Both entry points obey the same rule.
     *
     * <p>{@code of} used to wrap the argument in {@code List.copyOf}, which throws on a {@code null}
     * element <b>before</b> the constructor's documented tolerance could apply. The two doors
     * disagreed about identical input and only one of them said so.
     */
    @Test
    void mustHappen_bothEntryPointsIgnoreNullAndEmptyEntriesIdentically() {
        List<ItemStack> ragged = new java.util.ArrayList<>();
        ragged.add(new ItemStack(Items.OAK_LOG, 64));
        ragged.add(null);
        ragged.add(ItemStack.EMPTY);
        ragged.add(new ItemStack(Items.STICK, 3));

        List<ItemStack> viaConstructor = new TradeOpportunityQuery(ragged).authorizedSellInputs();
        List<ItemStack> viaFactory = TradeOpportunityQuery.of(ragged).authorizedSellInputs();

        assertEquals(2, viaConstructor.size(), "gaps are ignored, not rejected");
        assertEquals(viaConstructor.size(), viaFactory.size(), "and both doors agree");
        for (int i = 0; i < viaConstructor.size(); i++) {
            assertTrue(ItemStack.isSameItemSameComponents(
                            viaConstructor.get(i), viaFactory.get(i)),
                    "same kinds, same order, from either entry point");
        }
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

    /** Step 5 adds exactly one source. Trade Everything is step 6 and must not appear early. */
    @Test
    void mustHappen_onlyTheVanillaSourceIsImplemented() throws java.io.IOException {
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
                    .sorted()
                    .toList();
            assertEquals(List.of("VanillaTradeSource.java"), implementors,
                    "a Trade Everything source arriving with the vanilla parity step would let a "
                            + "vanilla regression hide behind it");
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
