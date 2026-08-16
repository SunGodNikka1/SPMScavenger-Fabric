package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * V2-H0 / `D-VR-075` — the same consumer, restated in the units the market trades in.
 *
 * <p>The projection's whole risk is that it becomes a <b>fabricated appetite</b>: a desire for a
 * finished tool that no consumer actually raised. Every test here is about that boundary — same
 * consumer key, live recipe only, deficit 1, and the source demand left entirely alone.
 */
class TradePurchaseProjectionTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static WorkDemandPolicy.MaterialDemand ironIngotDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 3,
                ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey());
    }

    /** The projection itself: ingredient demand becomes the finished output, same consumer. */
    @Test
    void mustHappen_theOutputProjectionKeepsTheConsumerAndDropsToOne() {
        Optional<WorkDemandPolicy.MaterialDemand> projected = TradePurchaseProjection
                .ontoOutput(ironIngotDemand(), ScavengerCrafting.IRON_PICKAXE_RECIPE);

        assertTrue(projected.isPresent());
        assertEquals(BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE),
                projected.get().materialKey(), "vanilla sells the tool, never the ingot");
        assertEquals(ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey(),
                projected.get().consumerKey(),
                "same appetite - a different consumer would be a fabricated desire");
        assertEquals(1, projected.get().derivedDeficit(),
                "one finished tool closes the consumer; carrying the ingredient deficit of 3 would "
                        + "ask for three pickaxes");
    }

    /** A recipe belonging to another consumer must never project onto this demand. */
    @Test
    void mustNotHappen_anotherConsumersRecipeProjectsThisDemand() {
        WorkDemandPolicy.MaterialDemand torchChain = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), 2,
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain"));

        assertEquals(Optional.empty(), TradePurchaseProjection
                .ontoOutput(torchChain, ScavengerCrafting.IRON_PICKAXE_RECIPE),
                "the iron recipe cannot speak for the torch chain");
    }

    /** No live recipe, no projection — the appetite exists only while its consumer does. */
    @Test
    void mustNotHappen_projectionOutlivesItsConsumer() {
        assertEquals(Optional.empty(),
                TradePurchaseProjection.ontoOutput(ironIngotDemand(), null));
        assertEquals(Optional.empty(),
                TradePurchaseProjection.ontoOutput(null, ScavengerCrafting.IRON_PICKAXE_RECIPE));
    }

    /** A demand that already names the output projects to nothing, not to itself twice. */
    @Test
    void mustNotHappen_anOutputDemandIsProjectedAgain() {
        WorkDemandPolicy.MaterialDemand alreadyOutput = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(ScavengerCrafting.IRON_PICKAXE_RECIPE.output()), 1,
                ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey());

        assertEquals(Optional.empty(), TradePurchaseProjection
                .ontoOutput(alreadyOutput, ScavengerCrafting.IRON_PICKAXE_RECIPE));
    }

    /** The diamond frontier projects through the identical rule — nothing is iron-specific. */
    @Test
    void mustHappen_theRuleIsNotSpecificToIron() {
        WorkDemandPolicy.MaterialDemand diamondDemand = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND), 3,
                ScavengerCrafting.DIAMOND_PICKAXE_RECIPE.consumerKey());

        assertEquals(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE),
                TradePurchaseProjection
                        .ontoOutput(diamondDemand, ScavengerCrafting.DIAMOND_PICKAXE_RECIPE)
                        .orElseThrow().materialKey());
    }

    // ------------------------------------------------- wiring contract

    private static String goalSource() throws IOException {
        return Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String methodOf(String source, String signature) {
        String body = source.substring(source.indexOf(signature));
        return body.substring(0, body.indexOf((char) 10 + "    }"));
    }

    /**
     * Direct material first, always.
     *
     * <p>The projection is a fallback, not a replacement. If a datapack or another mod ever sells
     * {@code iron_ingot}, that purchase must win and no projection may occur — which is also why
     * nothing in this layer knows what a Toolsmith is.
     */
    @Test
    void mustHappen_theDirectMaterialPurchaseIsTriedFirst() throws IOException {
        String decision = methodOf(goalSource(),
                "private Optional<AuthorizedAttempt> authorizedCandidate(");

        int direct = decision.indexOf("fundingTarget(purchaseDemand, offers, backpack)");
        int fallback = decision.indexOf("TradePurchaseProjection.ontoOutput(");
        assertTrue(direct > 0 && fallback > direct,
                "the source demand is offered to the market before any projection is considered");
        assertTrue(decision.contains("if (funding == null || !funding.actionable())"),
                "R1: precedence belongs to the route that can ACT. A non-null direct target with a "
                        + "deficit and no legal SELL leg can never complete, and letting it suppress "
                        + "the projection made a reachable purchase unreachable");
    }

    /**
     * Existing-route feasibility stays bound to the <b>source</b> demand.
     *
     * <p>{@code ExistingRouteFeasibility} and {@code RouteExhaustionEvidence} describe the raw-iron
     * gather/smelt route. Asking them whether crafting a finished pickaxe is infeasible would be a
     * category error, and would silently reinterpret every exhaustion record already published.
     */
    @Test
    void mustNotHappen_feasibilityIsEvaluatedAgainstTheProjectedDemand() throws IOException {
        String decision = methodOf(goalSource(),
                "private Optional<AuthorizedAttempt> authorizedCandidate(");

        assertTrue(decision.contains("existingRouteInfeasible(level, demand.get())"),
                "feasibility must take the source demand, never the projection");
        assertFalse(decision.contains("existingRouteInfeasible(level, purchaseDemand)"));
        assertFalse(decision.contains("existingRouteInfeasible(level, selected)"));
    }

    /** And the chain, the gate and the attempt context all use the selected purchase demand. */
    @Test
    void mustHappen_theSelectedPurchaseDrivesEvaluationAndExecution() throws IOException {
        String decision = methodOf(goalSource(),
                "private Optional<AuthorizedAttempt> authorizedCandidate(");

        assertTrue(decision.contains("advanceChain(level, purchaseDemand, backpack, funding)"),
                "the chain's desired output is what will actually be bought");
        assertTrue(decision.contains(".authorize(selected,"),
                "V2-C evaluates offers against the demand being purchased");
        assertTrue(decision.contains("selected.consumerKey()"),
                "and the attempt is attributed to the consumer, which never changed");
    }

    /** No villager, profession or Toolsmith special-casing anywhere in the trade package. */
    @Test
    void mustNotHappen_theProjectionHardcodesAProfession() throws IOException {
        String projection = Files.readString(Path.of("src/main/java/com/noobk/spmscavenger/"
                + "village/trade/TradePurchaseProjection.java"));

        for (String forbidden : new String[] {
                "TOOLSMITH", "VillagerProfession", "Items.IRON_PICKAXE", "Items.IRON_INGOT"}) {
            assertFalse(projection.contains(forbidden),
                    "the rule derives from ConsumerRecipeSpec, never from a named item or "
                            + "profession: " + forbidden);
        }
    }

    // ------------------------------------------------- R1: ownership across both representations

    private static TradeChainPlan chainFor(net.minecraft.resources.ResourceLocation output) {
        return TradeChainPlan.forDemand(
                ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey(), output, 0, 1, 100L);
    }

    /**
     * The R1 P0. A projected chain buys the recipe <b>output</b> while the live demand still names
     * the <b>ingredient</b>, so a material-equality ownership test declared it ownerless on the very
     * next continuation tick — and discovery rebuilt one, which made the violation look like working
     * behaviour while the hard lifetime, the relationship credit and chain identity all reset.
     */
    @Test
    void mustHappen_bothPurchaseRepresentationsCountAsOwned() {
        var spec = ScavengerCrafting.IRON_PICKAXE_RECIPE;
        var source = ironIngotDemand();

        assertTrue(TradePurchaseProjection.stillOwns(
                        chainFor(BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE)), source, spec),
                "the projected chain buys the tool - that is the same appetite, not a stray plan");
        assertTrue(TradePurchaseProjection.stillOwns(
                        chainFor(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), source, spec),
                "and the direct chain is still owned too");
    }

    @Test
    void mustNotHappen_aStrayChainCountsAsOwned() {
        var spec = ScavengerCrafting.IRON_PICKAXE_RECIPE;
        var source = ironIngotDemand();

        assertFalse(TradePurchaseProjection.stillOwns(
                        chainFor(BuiltInRegistries.ITEM.getKey(Items.DIAMOND)), source, spec),
                "a chain buying something this consumer never wanted is ownerless");
        assertFalse(TradePurchaseProjection.stillOwns(
                        chainFor(BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE)), source, null),
                "recipe no longer live - the projection is withdrawn in the same tick");
        assertFalse(TradePurchaseProjection.stillOwns(null, source, spec));

        TradeChainPlan otherConsumer = TradeChainPlan.forDemand(
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain"),
                BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE), 0, 1, 100L);
        assertFalse(TradePurchaseProjection.stillOwns(otherConsumer, source, spec),
                "same target, different consumer");
    }

    /** The live-demand funnel must use the two-level test, not raw material equality. */
    @Test
    void mustNotHappen_ownershipUsesRawMaterialEquality() throws IOException {
        String body = methodOf(goalSource(), "private void terminateChainIfOwnerless(");

        assertTrue(body.contains("TradePurchaseProjection.stillOwns("),
                "ownership understands both purchase representations");
        assertFalse(body.contains("chain.desiredOutput().equals(liveDemand.materialKey())"),
                "material equality alone killed every projected chain on the next tick");
    }

    // ------------------------------------------------- R1: representation switch keeps the clock

    /**
     * Switching between the two expressions of one appetite is not a new economic episode.
     *
     * <p>Minting a fresh plan whenever a direct seller wandered into or out of range would restart
     * the 6000-tick lifetime on every market flip — R7's "villager strolls away and the clock
     * resets" defect arriving through a new door.
     */
    @Test
    void mustNotHappen_switchingPurchaseRepresentationRestartsTheLifetime() {
        TradeChainPlan projected = chainFor(BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE));
        TradeChainPlan retargeted = projected.retargetedTo(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 3);

        assertEquals(projected.createdAtTick(), retargeted.createdAtTick());
        assertEquals(projected.expiresAtTick(), retargeted.expiresAtTick(),
                "the clock belongs to the consumer's appetite, not to the quote being served");
        assertEquals(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), retargeted.desiredOutput());
        assertEquals(3, retargeted.targetHeldQuantity(), "and the threshold follows the new units");
        assertEquals(TradeChainPlan.Step.SELL_TO_FUND, retargeted.step(),
                "a different quote has a different price, so any funded conclusion is stale");
    }

    @Test
    void mustHappen_theExecutorRetargetsRatherThanRemints() throws IOException {
        String body = methodOf(goalSource(), "private TradeChainPolicy.ChainOutcome advanceChain(");

        assertTrue(body.contains("chain.retargetedTo("),
                "same consumer, other representation - preserve the lifetime");
        assertTrue(body.contains("TradePurchaseProjection.isPurchaseTargetFor("),
                "and only when the new output really is a target for this consumer");
    }
}
