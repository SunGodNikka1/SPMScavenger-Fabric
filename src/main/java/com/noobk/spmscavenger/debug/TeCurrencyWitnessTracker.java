package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.compat.tradeeverything.TradeEverythingCompat;
import com.noobk.spmscavenger.village.trade.MerchantCurrencyPolicies;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.SellFundingLeg;
import com.noobk.spmscavenger.village.trade.TradeFundingPlanner;
import com.noobk.spmscavenger.village.trade.TradeSourceKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary, single-mob runtime witness for V2-TE emerald-block currency parity.
 *
 * <p><b>Observation only.</b> This class owns no goal, policy, offer, scan, inventory write, or
 * transaction. Its sole live reference is the armed mob's {@link Container}; the UUID plus Java
 * container identity are the admission key for every hook. One Q2 {@link MerchantOffer} reference
 * is retained only until the corresponding SELL commits, then reduced to a boolean identity result.
 *
 * <p><b>RET-1 bound:</b> one static session, never a map. Stop, reset, unload, death, server stop,
 * PASS, FAIL, and observer self-disable all release the container and Q2 references.
 */
public final class TeCurrencyWitnessTracker {

    public static final String LOG_PREFIX = "[spmscavenger/v2te-witness]";
    public static final ResourceLocation IRON_PICKAXE_CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");

    private static final Logger LOGGER = LoggerFactory.getLogger("spmscavenger/v2te-witness");
    private static Session session;

    private TeCurrencyWitnessTracker() {
    }

    public enum State {
        ARMED,
        IRON_PICKAXE_DEMAND_SEEN,
        TE_Q1_QUOTE_SEEN,
        TE_FUNDING_PLAN_SELECTED,
        TE_Q2_REQUOTE_CONFIRMED,
        SELL_COMMITTED,
        EMERALD_BLOCK_PAYOUT_CONFIRMED,
        IRON_PICKAXE_PURCHASE_SELECTED,
        PAYMENT_STAGE_ENTERED,
        BLOCK_NORMALIZATION_STAGED,
        PURCHASE_COMMITTED,
        PASS,
        INCOMPLETE,
        FAIL
    }

    public record InventorySnapshot(
            int witnessSticks, int emeralds, int emeraldBlocks, int ironPickaxes) {

        static InventorySnapshot of(Container container) {
            if (container == null) {
                return new InventorySnapshot(0, 0, 0, 0);
            }
            int sticks = 0;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (isWitnessStick(stack)) {
                    sticks += stack.getCount();
                }
            }
            return new InventorySnapshot(
                    sticks,
                    container.countItem(Items.EMERALD),
                    container.countItem(Items.EMERALD_BLOCK),
                    container.countItem(Items.IRON_PICKAXE));
        }

        static InventorySnapshot of(ItemStack[] stacks) {
            if (stacks == null) {
                return new InventorySnapshot(0, 0, 0, 0);
            }
            int sticks = 0;
            int emeralds = 0;
            int blocks = 0;
            int picks = 0;
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (isWitnessStick(stack)) sticks += stack.getCount();
                if (stack.is(Items.EMERALD)) emeralds += stack.getCount();
                if (stack.is(Items.EMERALD_BLOCK)) blocks += stack.getCount();
                if (stack.is(Items.IRON_PICKAXE)) picks += stack.getCount();
            }
            return new InventorySnapshot(sticks, emeralds, blocks, picks);
        }

        int liquidityUnits() {
            return emeralds + emeraldBlocks * 9;
        }
    }

    public record Preflight(
            UUID mobId,
            String targetName,
            boolean playerMob,
            String installedTeVersion,
            boolean currencyCapabilityActive,
            boolean quoteBridgeHealthy,
            ResourceLocation consumerKey,
            ToolTier pickTier,
            ResourceLocation desiredOutput,
            InventorySnapshot backpack,
            boolean witnessStickInMainHand,
            boolean witnessStickInOffhand) {
    }

    public record ArmResult(boolean armed, List<String> lines) {
    }

    /** Build the preflight exclusively from current production state. */
    public static Preflight preflight(Mob mob, Container backpack) {
        boolean playerMob = mob != null && PlayerMobs.isPlayerMob(mob);
        ItemStack main = mob == null ? ItemStack.EMPTY : mob.getMainHandItem();
        ItemStack off = mob == null ? ItemStack.EMPTY : mob.getOffhandItem();
        WorkDemandPolicy.MaterialDemand demand = backpack == null ? null
                : WorkDemandPolicy.select(backpack, main, off, ScavengerConfig.get())
                        .map(WorkDemandPolicy.WorkDemand::payload)
                        .orElse(null);
        ScavengerCrafting.ConsumerRecipeSpec recipe = backpack == null ? null
                : ScavengerCrafting.activeIronToolRecipe(
                                backpack, main, off, ScavengerConfig.get())
                        .orElse(null);
        return new Preflight(
                mob == null ? null : mob.getUUID(),
                mob == null ? "<missing>" : mob.getName().getString(),
                playerMob,
                TradeEverythingCompat.installedVersion(),
                TradeEverythingCompat.currencyCapabilityActive(),
                TradeEverythingCompat.quoteBridgeHealthy(),
                demand == null ? null : demand.consumerKey(),
                backpack == null ? ToolTier.NONE
                        : ToolTierPolicy.tierOfPick(backpack, main, off),
                recipe == null ? null : BuiltInRegistries.ITEM.getKey(recipe.output()),
                InventorySnapshot.of(backpack),
                isWitnessStick(main),
                isWitnessStick(off));
    }

    /** Arm one exact UUID/container pair, or return an evidence-rich refusal. */
    public static synchronized ArmResult arm(Mob mob, Container backpack, long tick) {
        return arm(preflight(mob, backpack), backpack, tick);
    }

    static synchronized ArmResult arm(Preflight preflight, Container backpack, long tick) {
        List<String> lines = preflightLines(preflight);
        if (session != null && session.active()) {
            lines.add("Result: REFUSED — another witness is already armed for " + session.targetName);
            return new ArmResult(false, List.copyOf(lines));
        }
        List<String> failures = validate(preflight, backpack);
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                lines.add("Preflight failure: " + failure);
            }
            lines.add("Result: REFUSED");
            return new ArmResult(false, List.copyOf(lines));
        }
        session = new Session(preflight, backpack);
        lines.add("Result: ARMED");
        transition(session, State.ARMED, tick,
                "target=" + preflight.mobId() + " container=" + identity(backpack));
        return new ArmResult(true, List.copyOf(lines));
    }

    /** Test hook uses the same preflight validation and state machine; it grants no production power. */
    static synchronized ArmResult armForTest(
            Preflight preflight, Container backpack, long tick) {
        return arm(preflight, backpack, tick);
    }

    public static void observeDemand(
            UUID mobId, Container backpack, WorkDemandPolicy.MaterialDemand demand, long tick) {
        observe("demand", () -> {
            Session s = matching(mobId, backpack);
            if (s == null || demand == null || !IRON_PICKAXE_CONSUMER.equals(demand.consumerKey())) {
                return;
            }
            s.consumerActive = true;
            advance(s, State.IRON_PICKAXE_DEMAND_SEEN, tick,
                    "consumer=" + demand.consumerKey() + " material=" + demand.materialKey()
                            + " deficit=" + demand.derivedDeficit());
        });
    }

    /** Refresh independently observed compatibility facts without granting either capability. */
    public static void observeCompatibilityHealth(
            UUID mobId,
            Container backpack,
            String installedVersion,
            boolean currencyActive,
            boolean quoteHealthy,
            long tick) {
        observe("compatibility-health", () -> {
            Session s = matching(mobId, backpack);
            if (s == null) return;
            if (!Objects.equals(s.teVersion, installedVersion)) {
                fail(s, tick, "installed Trade Everything version changed during witness");
            } else if (!currencyActive) {
                fail(s, tick, "TE currency capability became inactive");
            } else if (!quoteHealthy) {
                s.bridgeFailureObserved = true;
                fail(s, tick, "TE quote bridge became unhealthy");
            }
        });
    }

    public static void observeQ1(
            UUID mobId,
            Container backpack,
            TradeSourceKey source,
            OfferSnapshot quote,
            long tick) {
        observe("q1", () -> {
            Session s = matching(mobId, backpack);
            if (s == null || source != TradeSourceKey.TRADE_EVERYTHING || quote == null
                    || !isWitnessStick(quote.costA()) || !quote.result().is(Items.EMERALD_BLOCK)) {
                return;
            }
            s.q1 = quote;
            s.fundingUnits = MerchantCurrencyPolicies.current().fundingUnits(quote.result());
            advance(s, State.TE_Q1_QUOTE_SEEN, tick,
                    "q1=" + describe(quote.costA()) + " -> " + describe(quote.result())
                            + " fundingUnits=" + s.fundingUnits
                            + " components=" + componentFingerprint(quote.costA()));
        });
    }

    public static void observeFundingPlan(
            UUID mobId,
            Container backpack,
            TradeSourceKey selectedSource,
            TradeFundingPlanner.FundingTarget funding,
            SellFundingLeg sellLeg,
            long tick) {
        observe("funding-plan", () -> {
            Session s = matching(mobId, backpack);
            if (s == null || selectedSource != TradeSourceKey.TRADE_EVERYTHING
                    || funding == null || funding.deficit() == null || sellLeg == null
                    || !isWitnessStick(sellLeg.offer().costA())
                    || !sellLeg.offer().result().is(Items.EMERALD_BLOCK)) {
                return;
            }
            s.originalDeficit = funding.deficit().emeraldsNeeded();
            s.fundingUnits = sellLeg.emeraldsPerUse();
            s.authorizedSaleUses = Math.min(
                    sellLeg.affordableUses(),
                    (s.originalDeficit + s.fundingUnits - 1) / s.fundingUnits);
            advance(s, State.TE_FUNDING_PLAN_SELECTED, tick,
                    "deficit=" + s.originalDeficit + " fundingUnits=" + s.fundingUnits
                            + " authorizedUses=" + s.authorizedSaleUses);
        });
    }

    public static void observeQ2(
            UUID mobId,
            Container backpack,
            TradeSourceKey source,
            OfferSnapshot planned,
            MerchantOffer q2,
            long tick) {
        observe("q2", () -> {
            Session s = matching(mobId, backpack);
            if (s == null || source != TradeSourceKey.TRADE_EVERYTHING || planned == null
                    || q2 == null || !planned.semanticallyMatches(q2)) {
                if (s != null && source == TradeSourceKey.TRADE_EVERYTHING) {
                    fail(s, tick, "Q1/Q2 semantic correspondence failed");
                }
                return;
            }
            s.q1q2Correspondence = true;
            s.q2Offer = q2;
            advance(s, State.TE_Q2_REQUOTE_CONFIRMED, tick,
                    "semanticCorrespondence=true q2Identity=" + identity(q2));
        });
    }

    /** Called at the generic executor boundary; proves the TE Q2 object arrived by identity. */
    public static void observeExecutionOffer(Container backpack, MerchantOffer live, long tick) {
        observe("execution-offer", () -> {
            Session s = matching(backpack);
            if (s == null || s.q2Offer == null) {
                return;
            }
            if (live != s.q2Offer) {
                fail(s, tick, "executor received a different object than the recorded TE Q2 offer");
                return;
            }
            s.q2IdentityPreserved = true;
            log(s, tick, "TE_Q2_EXECUTION_IDENTITY", "sameReference=true identity=" + identity(live));
        });
    }

    public static void observePurchaseSelected(
            UUID mobId,
            Container backpack,
            OfferSnapshot purchase,
            long tick) {
        observe("purchase-selected", () -> {
            Session s = matching(mobId, backpack);
            if (s == null || purchase == null || !purchase.result().is(Items.IRON_PICKAXE)) {
                return;
            }
            s.purchaseSnapshot = purchase;
            s.purchaseCostUnits = MerchantCurrencyPolicies.current().paymentUnits(purchase.costA())
                    + MerchantCurrencyPolicies.current().paymentUnits(purchase.costB());
            s.blockLiquidityAdmitted = MerchantCurrencyPolicies.current().liquidity(backpack)
                    >= s.purchaseCostUnits && backpack.countItem(Items.EMERALD_BLOCK) > 0;
            advance(s, State.IRON_PICKAXE_PURCHASE_SELECTED, tick,
                    "costUnits=" + s.purchaseCostUnits
                            + " liquidity=" + MerchantCurrencyPolicies.current().liquidity(backpack));
        });
    }

    /** Snapshot the real inventory immediately after the sole transaction owner stages it. */
    public static void observePaymentStageEntered(
            Container backpack, MerchantOffer live, ItemStack[] staged, long tick) {
        observe("payment-stage", () -> {
            Session s = matching(backpack);
            if (s == null || live == null || !live.assemble().is(Items.IRON_PICKAXE)) {
                return;
            }
            s.realBeforePayment = InventorySnapshot.of(backpack);
            InventorySnapshot stagedBefore = InventorySnapshot.of(staged);
            if (!s.realBeforePayment.equals(stagedBefore)) {
                fail(s, tick, "initial staged copy differs from real inventory");
                return;
            }
            advance(s, State.PAYMENT_STAGE_ENTERED, tick,
                    "realBefore=" + compact(s.realBeforePayment));
        });
    }

    /** Observe production normalization; never performs or repeats its arithmetic. */
    public static void observeBlockNormalization(
            Container backpack, MerchantOffer live, ItemStack[] staged, long tick) {
        observe("normalization", () -> {
            Session s = matching(backpack);
            if (s == null || live == null || !live.assemble().is(Items.IRON_PICKAXE)
                    || s.realBeforePayment == null) {
                return;
            }
            s.stagedAfterNormalization = InventorySnapshot.of(staged);
            s.realDuringStaging = InventorySnapshot.of(backpack);
            s.realUnchangedDuringStaging = s.realBeforePayment.equals(s.realDuringStaging);

            int blocksBroken = s.realBeforePayment.emeraldBlocks()
                    - s.stagedAfterNormalization.emeraldBlocks();
            int uncovered = Math.max(0,
                    s.purchaseCostUnits - s.realBeforePayment.emeralds());
            s.minimumBlockConversion = blocksBroken >= 0
                    && blocksBroken * 9 >= uncovered
                    && (blocksBroken == 0 || (blocksBroken - 1) * 9 < uncovered);
            if (!s.realUnchangedDuringStaging) {
                fail(s, tick, "real inventory changed while denomination conversion was staged");
                return;
            }
            if (!s.minimumBlockConversion) {
                fail(s, tick, "staged conversion did not break the minimum number of blocks");
                return;
            }
            advance(s, State.BLOCK_NORMALIZATION_STAGED, tick,
                    "staged=" + compact(s.stagedAfterNormalization)
                            + " realDuring=" + compact(s.realDuringStaging)
                            + " blocksBroken=" + blocksBroken);
        });
    }

    /** Observe the one real commit. Sale and purchase paths are classified from the live result. */
    public static void observeCommit(Container backpack, MerchantOffer live, long tick) {
        observe("commit", () -> {
            Session s = matching(backpack);
            if (s == null || live == null) {
                return;
            }
            if (live == s.q2Offer) {
                s.actualSaleCount++;
                s.afterSale = InventorySnapshot.of(backpack);
                if (!s.q2IdentityPreserved) {
                    fail(s, tick, "TE sale committed before exact Q2 executor identity was proven");
                    return;
                }
                advance(s, State.SELL_COMMITTED, tick,
                        "saleCount=" + s.actualSaleCount + " inventory=" + compact(s.afterSale));
                boolean physicalPayout = s.afterSale.emeraldBlocks() > 0
                        && s.afterSale.emeralds() == 0
                        && s.afterSale.witnessSticks() == s.start.witnessSticks() - 1;
                if (!physicalPayout) {
                    fail(s, tick, "TE sale did not leave the expected physical block payout");
                    return;
                }
                s.physicalBlockPayout = true;
                // The live reference has served its only purpose. Retain the identity verdict, not
                // an upstream object, for the rest of the report.
                s.q2Offer = null;
                advance(s, State.EMERALD_BLOCK_PAYOUT_CONFIRMED, tick,
                        "blocks=" + s.afterSale.emeraldBlocks()
                                + " emeralds=" + s.afterSale.emeralds());
                return;
            }
            if (live.assemble().is(Items.IRON_PICKAXE)) {
                s.actualPurchaseCount++;
                s.finalInventory = InventorySnapshot.of(backpack);
                int expectedLiquidity = s.realBeforePayment == null ? -1
                        : s.realBeforePayment.liquidityUnits() - s.purchaseCostUnits;
                s.exactPaymentDebit = expectedLiquidity >= 0
                        && s.finalInventory.liquidityUnits() == expectedLiquidity;
                s.correctLooseChange = s.stagedAfterNormalization != null
                        && s.finalInventory.emeraldBlocks()
                                == s.stagedAfterNormalization.emeraldBlocks()
                        && s.finalInventory.emeralds()
                                == s.stagedAfterNormalization.emeralds() - s.purchaseCostUnits;
                s.pickaxeAcquired = s.finalInventory.ironPickaxes()
                        > s.start.ironPickaxes();
                if (!s.exactPaymentDebit || !s.correctLooseChange || !s.pickaxeAcquired) {
                    fail(s, tick, "purchase commit violated payment, change, or output invariant");
                    return;
                }
                advance(s, State.PURCHASE_COMMITTED, tick,
                        "purchaseCount=" + s.actualPurchaseCount
                                + " final=" + compact(s.finalInventory));
            }
        });
    }

    /** Count the production notifier invocation without invoking or wrapping it. */
    public static void observeNotifyAttempt(Container backpack, MerchantOffer live, long tick) {
        observe("notify", () -> {
            Session s = matching(backpack);
            if (s == null || live == null) {
                return;
            }
            if (live.assemble().is(Items.EMERALD_BLOCK)) s.sellNotifyAttempts++;
            if (live.assemble().is(Items.IRON_PICKAXE)) s.purchaseNotifyAttempts++;
            if (s.sellNotifyAttempts > 1 || s.purchaseNotifyAttempts > 1) {
                fail(s, tick, "duplicate notify/trade attempt observed");
            }
        });
    }

    /** Called only after the production notifier returns normally. */
    public static void observeNotifyCompleted(Container backpack, MerchantOffer live, long tick) {
        observe("notify-complete", () -> {
            Session s = matching(backpack);
            if (s == null || live == null || !live.assemble().is(Items.IRON_PICKAXE)
                    || s.state != State.PURCHASE_COMMITTED) {
                return;
            }
            if (s.actualSaleCount != 1 || s.actualPurchaseCount != 1
                    || s.sellNotifyAttempts != 1 || s.purchaseNotifyAttempts != 1) {
                fail(s, tick, "trade/notify counts were not exactly one SELL and one BUY");
                return;
            }
            if (!s.blockLiquidityAdmitted || !s.physicalBlockPayout
                    || !s.q1q2Correspondence || !s.q2IdentityPreserved
                    || !s.minimumBlockConversion || !s.realUnchangedDuringStaging) {
                fail(s, tick, "one or more recorded compatibility invariants did not pass");
                return;
            }
            transition(s, State.PASS, tick, "verdict=PASS");
            releaseLiveReferences(s);
        });
    }

    /** Active lifecycle abort for unload or death. Retains only immutable report evidence. */
    public static synchronized void abortForMob(UUID mobId, String reason, long tick) {
        if (session == null || !Objects.equals(session.mobId, mobId) || !session.active()) {
            return;
        }
        incomplete(session, tick, reason);
    }

    public static synchronized List<String> stop(String reason, long tick) {
        if (session == null) {
            return List.of("No V2-TE witness session exists.");
        }
        if (session.active()) {
            incomplete(session, tick, reason);
        }
        return statusLines(session, false);
    }

    public static synchronized List<String> reset() {
        if (session != null) {
            releaseLiveReferences(session);
        }
        session = null;
        return List.of("V2-TE witness reset; no live references retained.");
    }

    public static synchronized void shutdownServerState(long tick) {
        if (session != null && session.active()) {
            incomplete(session, tick, "server stopped");
        }
        // A stopped server has nowhere to issue `report`; release the report object too so a later
        // integrated-server session in the same JVM cannot inherit it.
        session = null;
    }

    public static synchronized List<String> statusLines() {
        return session == null
                ? List.of("No V2-TE witness session exists.")
                : statusLines(session, false);
    }

    public static synchronized List<String> reportLines() {
        return session == null
                ? List.of("No V2-TE witness session exists.")
                : statusLines(session, true);
    }

    static synchronized State stateForTests() {
        return session == null ? null : session.state;
    }

    static synchronized boolean retainsLiveReferencesForTests() {
        return session != null && (session.backpack != null || session.q2Offer != null);
    }

    private static List<String> validate(Preflight p, Container backpack) {
        List<String> failures = new ArrayList<>();
        if (p == null || p.mobId() == null) failures.add("target entity is missing");
        if (p != null && !p.playerMob()) failures.add("target is not a Social PlayerMob");
        if (backpack == null) failures.add("PlayerMob InventoryCarrier backpack is unavailable");
        if (p == null) return failures;
        if (!"0.8.0".equals(p.installedTeVersion())) failures.add("Trade Everything 0.8.0 is not installed");
        if (!p.currencyCapabilityActive()) failures.add("TE currency capability is not ACTIVE");
        if (!p.quoteBridgeHealthy()) failures.add("TE quote bridge is not HEALTHY");
        if (!IRON_PICKAXE_CONSUMER.equals(p.consumerKey())) failures.add("iron-pickaxe consumer is not active");
        if (p.pickTier() != ToolTier.STONE) failures.add("current pickaxe tier is not STONE");
        if (!BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE).equals(p.desiredOutput())) {
            failures.add("desired output is not minecraft:iron_pickaxe");
        }
        if (p.backpack().witnessSticks() != 4) failures.add("backpack must contain exactly 4 enchanted witness sticks");
        if (p.backpack().emeralds() != 0) failures.add("backpack must start with 0 loose emeralds");
        if (p.backpack().emeraldBlocks() != 0) failures.add("backpack must start with 0 emerald blocks");
        if (p.witnessStickInMainHand()) failures.add("witness stick is in the main hand");
        if (p.witnessStickInOffhand()) failures.add("witness stick is in the offhand");
        return failures;
    }

    private static List<String> preflightLines(Preflight p) {
        List<String> out = new ArrayList<>();
        out.add("=== V2-TE Witness Preflight ===");
        if (p == null) {
            out.add("Target: <missing>");
            return out;
        }
        out.add("Target: " + p.targetName() + " [" + p.mobId() + "]");
        out.add("PlayerMob: " + yesNo(p.playerMob()));
        out.add("Trade Everything installed version: " + nullable(p.installedTeVersion()));
        out.add("Currency capability: " + (p.currencyCapabilityActive() ? "ACTIVE" : "INACTIVE"));
        out.add("Quote bridge: " + (p.quoteBridgeHealthy() ? "HEALTHY" : "UNHEALTHY"));
        out.add("Active consumer: " + nullable(p.consumerKey()));
        out.add("Current pickaxe tier: " + p.pickTier());
        out.add("Desired output: " + nullable(p.desiredOutput()));
        out.add("Enchanted witness sticks: " + p.backpack().witnessSticks());
        out.add("Loose emeralds: " + p.backpack().emeralds());
        out.add("Emerald blocks: " + p.backpack().emeraldBlocks());
        out.add("Witness stick in main hand: " + yesNo(p.witnessStickInMainHand()));
        out.add("Witness stick in offhand: " + yesNo(p.witnessStickInOffhand()));
        return out;
    }

    private static List<String> statusLines(Session s, boolean report) {
        List<String> out = new ArrayList<>();
        out.add(report ? "=== V2-TE 0.8.0 Runtime Witness ===" : "=== V2-TE Currency Witness ===");
        out.add("Target: " + s.targetName + " [" + s.mobId + "]");
        out.add("State: " + s.state);
        out.add("TE version: " + nullable(s.teVersion));
        out.add("Currency capability: " + pass(s.currencyCapability));
        out.add("Quote bridge linkage: " + pass(s.quoteHealthy));
        out.add("Iron-pickaxe demand observed: " + pass(s.consumerActive));
        out.add("TE Q1 block quote observed: " + pass(s.q1 != null));
        if (s.q1 != null) {
            out.add("Q1: " + describe(s.q1.costA()) + " -> " + describe(s.q1.result()));
            out.add("Q1 input components: " + componentFingerprint(s.q1.costA()));
        }
        out.add("Funding deficit / output units: " + s.originalDeficit + " / " + s.fundingUnits);
        out.add("Authorized / actual sale uses: " + s.authorizedSaleUses + " / " + s.actualSaleCount);
        out.add("Q1/Q2 semantic correspondence: " + pass(s.q1q2Correspondence));
        out.add("Exact Q2 object passed to executor: " + pass(s.q2IdentityPreserved));
        out.add("Physical emerald-block payout: " + pass(s.physicalBlockPayout));
        out.add("Toolsmith iron-pickaxe offer selected: " + pass(s.purchaseSnapshot != null));
        out.add("Block liquidity admitted: " + pass(s.blockLiquidityAdmitted));
        out.add("Real before payment: " + compact(s.realBeforePayment));
        out.add("Staged after normalization: " + compact(s.stagedAfterNormalization));
        out.add("Real during staging: " + compact(s.realDuringStaging));
        out.add("Final after commit: " + compact(s.finalInventory));
        out.add("Minimum block conversion: " + pass(s.minimumBlockConversion));
        out.add("Real inventory unchanged during staging: " + pass(s.realUnchangedDuringStaging));
        out.add("Exact payment debit: " + pass(s.exactPaymentDebit));
        out.add("Correct loose change: " + pass(s.correctLooseChange));
        out.add("Iron pickaxe acquired: " + pass(s.pickaxeAcquired));
        out.add("Unexpected extra stick sale: " + yesNo(s.actualSaleCount > 1));
        out.add("Premature block conversion: " + yesNo(s.realBeforePayment != null
                && s.afterSale != null && s.realBeforePayment.emeraldBlocks() != s.afterSale.emeraldBlocks()));
        out.add("Duplicate notify/trade: " + yesNo(s.sellNotifyAttempts > 1
                || s.purchaseNotifyAttempts > 1 || s.actualSaleCount > 1 || s.actualPurchaseCount > 1));
        out.add("Bridge failure: " + yesNo(s.bridgeFailureObserved));
        out.add("Inventory loss: " + yesNo(s.state == State.FAIL
                && s.reason != null && (s.reason.contains("inventory")
                        || s.reason.contains("payment") || s.reason.contains("payout"))));
        if (s.state == State.FAIL) {
            out.add("VERDICT: FAIL");
            out.add("FIRST VIOLATED INVARIANT: " + s.reason);
        } else if (s.state == State.PASS) {
            out.add("VERDICT: PASS");
        } else {
            out.add("VERDICT: INCOMPLETE");
            out.add("REASON: " + nullable(s.reason == null ? "witness has not reached PASS" : s.reason));
            out.add("Next expected: " + nextExpected(s.state));
        }
        return List.copyOf(out);
    }

    private static String nextExpected(State state) {
        if (state == null) return "arm witness";
        return switch (state) {
            case ARMED -> "iron-pickaxe demand";
            case IRON_PICKAXE_DEMAND_SEEN -> "TE Q1 emerald-block quote";
            case TE_Q1_QUOTE_SEEN -> "TE funding plan selection";
            case TE_FUNDING_PLAN_SELECTED -> "TE Q2 re-quote";
            case TE_Q2_REQUOTE_CONFIRMED -> "exact Q2 executor entry";
            case SELL_COMMITTED -> "physical emerald-block payout";
            case EMERALD_BLOCK_PAYOUT_CONFIRMED -> "ordinary iron-pickaxe purchase";
            case IRON_PICKAXE_PURCHASE_SELECTED -> "payment staging";
            case PAYMENT_STAGE_ENTERED -> "staged block normalization";
            case BLOCK_NORMALIZATION_STAGED -> "purchase commit";
            case PURCHASE_COMMITTED -> "successful notify completion";
            case PASS -> "none";
            case INCOMPLETE, FAIL -> "reset and begin a new witness";
        };
    }

    private static boolean isWitnessStick(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.STICK)) {
            return false;
        }
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments != null && !enchantments.isEmpty();
    }

    private static String componentFingerprint(ItemStack stack) {
        return stack == null ? "<null>" : stack.getComponentsPatch().toString();
    }

    private static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static String compact(InventorySnapshot snapshot) {
        if (snapshot == null) return "<not observed>";
        return "sticks=" + snapshot.witnessSticks() + " blocks=" + snapshot.emeraldBlocks()
                + " emeralds=" + snapshot.emeralds() + " iron_pickaxes=" + snapshot.ironPickaxes();
    }

    private static String pass(boolean value) {
        return value ? "PASS" : "NOT YET";
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String nullable(Object value) {
        return value == null ? "<none>" : value.toString();
    }

    private static String identity(Object value) {
        return value == null ? "<released>" : Integer.toHexString(System.identityHashCode(value));
    }

    private static synchronized Session matching(UUID mobId, Container backpack) {
        return session != null && session.active() && Objects.equals(session.mobId, mobId)
                && session.backpack == backpack ? session : null;
    }

    private static synchronized Session matching(Container backpack) {
        return session != null && session.active() && session.backpack == backpack ? session : null;
    }

    private static void advance(Session s, State next, long tick, String evidence) {
        if (s.state == State.FAIL || s.state == State.INCOMPLETE || s.state == State.PASS) return;
        if (next.ordinal() > s.state.ordinal()) transition(s, next, tick, evidence);
    }

    private static void transition(Session s, State next, long tick, String evidence) {
        s.state = next;
        log(s, tick, next.name(), evidence);
    }

    private static void fail(Session s, long tick, String reason) {
        if (s.state == State.FAIL || s.state == State.PASS) return;
        s.reason = reason;
        transition(s, State.FAIL, tick, "reason=" + reason);
        releaseLiveReferences(s);
    }

    private static void incomplete(Session s, long tick, String reason) {
        s.reason = reason;
        transition(s, State.INCOMPLETE, tick, "reason=" + reason);
        releaseLiveReferences(s);
    }

    private static void releaseLiveReferences(Session s) {
        s.backpack = null;
        s.q2Offer = null;
    }

    private static void log(Session s, long tick, String event, String evidence) {
        LOGGER.info("{} mob={} tick={} event={} {}", LOG_PREFIX, s.mobId,
                tick < 0 ? "?" : Long.toString(tick), event, evidence);
    }

    /** Debug failure must disable only the observer, never alter the production transaction. */
    private static void observe(String hook, Runnable observation) {
        try {
            observation.run();
        } catch (Throwable failure) {
            synchronized (TeCurrencyWitnessTracker.class) {
                if (session != null && session.active()) {
                    session.reason = "observer failure at " + hook + ": " + failure;
                    session.state = State.FAIL;
                    releaseLiveReferences(session);
                }
            }
            LOGGER.error("{} observer failure at {}; witness disabled without changing production",
                    LOG_PREFIX, hook, failure);
        }
    }

    private static final class Session {
        private final UUID mobId;
        private final String targetName;
        private final String teVersion;
        private final boolean currencyCapability;
        private final boolean quoteHealthy;
        private final InventorySnapshot start;
        private Container backpack;
        private State state = State.ARMED;
        private String reason;
        private boolean consumerActive;
        private OfferSnapshot q1;
        private MerchantOffer q2Offer;
        private boolean q1q2Correspondence;
        private boolean q2IdentityPreserved;
        private int originalDeficit;
        private int fundingUnits;
        private int authorizedSaleUses;
        private int actualSaleCount;
        private int actualPurchaseCount;
        private int sellNotifyAttempts;
        private int purchaseNotifyAttempts;
        private boolean physicalBlockPayout;
        private OfferSnapshot purchaseSnapshot;
        private int purchaseCostUnits;
        private boolean blockLiquidityAdmitted;
        private InventorySnapshot afterSale;
        private InventorySnapshot realBeforePayment;
        private InventorySnapshot stagedAfterNormalization;
        private InventorySnapshot realDuringStaging;
        private InventorySnapshot finalInventory;
        private boolean minimumBlockConversion;
        private boolean realUnchangedDuringStaging;
        private boolean exactPaymentDebit;
        private boolean correctLooseChange;
        private boolean pickaxeAcquired;
        private boolean bridgeFailureObserved;

        private Session(Preflight p, Container backpack) {
            this.mobId = p.mobId();
            this.targetName = p.targetName();
            this.teVersion = p.installedTeVersion();
            this.currencyCapability = p.currencyCapabilityActive();
            this.quoteHealthy = p.quoteBridgeHealthy();
            this.start = p.backpack();
            this.backpack = backpack;
        }

        private boolean active() {
            return state != State.PASS && state != State.FAIL && state != State.INCOMPLETE;
        }
    }
}
