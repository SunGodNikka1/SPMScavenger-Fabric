package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * <b>V2-DEF-003b</b> — optional wealth must not mask a mandatory route's factual conclusion.
 *
 * <h2>The stall</h2>
 *
 * With {@code greed = 0.1} and {@code wealthLevel = 0.1}, {@code ResourceWealthPolicy.LOGS}
 * saturates at 32 but keeps {@code wealthFactor = 0.05}, so a saturated log still carries positive
 * utility. {@code GatherIntent.hasDemand()} correctly refuses to start a scan for that alone — but
 * once a mandatory RAW_IRON demand started one, {@code intent.wants(resource, cost)} admitted the
 * log as a candidate:
 *
 * <pre>
 * RAW_IRON mandatory, none in radius
 * saturated LOG wealth candidate in radius
 *   -&gt; findTarget returns the LOG
 *   -&gt; the scan is not NO_CANDIDATES_IN_RADIUS
 *   -&gt; RAW_IRON exhaustion is never published
 *   -&gt; ExistingRouteFeasibility stays UNKNOWN and trade can never displace
 * </pre>
 *
 * <h2>The invariant</h2>
 *
 * Optional opportunity may affect <b>target selection</b>. It may not prevent a mandatory consumer
 * route from reaching its <b>own</b> conclusion. Wealth keeps noticing and acquiring logs; it simply
 * stops being able to answer a question that was asked about iron.
 *
 * <p>The repair is observational: the single bounded sweep now retains which resource families it
 * turned up, instead of collapsing to {@code target != null}. No rescans, and <b>gather remains the
 * only publisher</b> — nothing here lets trade infer or publish exhaustion for itself.
 */
class WealthMaskingMandatoryRouteTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ScavengerConfig ironWithWealth() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.NONE;
        cfg.torchStockTarget = 0;
        cfg.greed = 0.1;
        cfg.wealthLevel = 0.1;
        return cfg;
    }

    /** Saturated logs plus the runtime greed/wealth values, exactly as observed. */
    private static SimpleContainer saturatedLogsNoIron() {
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        pack.setItem(1, new ItemStack(Items.STICK, 3));
        return pack;
    }

    // ------------------------------------------------------------------ family classification

    /**
     * Families are read once, independent of intent.
     *
     * <p>{@code isPassOneCandidate} collapses mandatory need and optional wealth into one boolean;
     * this is the fact the sweep has to keep so it can answer per resource afterwards.
     */
    @Test
    void mustHappen_everyGatherableBlockNamesItsOwnFamily() {
        assertEquals(Optional.of(GatherIntentPolicy.Resource.RAW_IRON),
                GatherCandidatePolicy.familyOf(Blocks.IRON_ORE.defaultBlockState()));
        assertEquals(Optional.of(GatherIntentPolicy.Resource.RAW_IRON),
                GatherCandidatePolicy.familyOf(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()));
        // LOGS is a BLOCK TAG, and Bootstrap.bootStrap() populates no tags - familyOf(OAK_LOG)
        // reads empty here for reasons that have nothing to do with this repair. Asserting it would
        // be asserting the harness. The ore and stone families are identity-based and do work.
        assertEquals(Optional.of(GatherIntentPolicy.Resource.COAL),
                GatherCandidatePolicy.familyOf(Blocks.COAL_ORE.defaultBlockState()));
        assertEquals(Optional.of(GatherIntentPolicy.Resource.DIAMOND),
                GatherCandidatePolicy.familyOf(Blocks.DIAMOND_ORE.defaultBlockState()));
        assertEquals(Optional.of(GatherIntentPolicy.Resource.COBBLESTONE),
                GatherCandidatePolicy.familyOf(Blocks.STONE.defaultBlockState()));
        assertEquals(Optional.empty(),
                GatherCandidatePolicy.familyOf(Blocks.DIRT.defaultBlockState()),
                "a block nobody gathers belongs to no family and must not be attributed to one");
    }

    /**
     * Iron ore is iron ore whoever wanted it.
     *
     * <p>Classifying by intent would have reproduced the defect one layer down — the saturated log
     * would have been filed as "whatever the scan was for". {@code familyOf} takes no intent
     * parameter, which is the structural form of that guarantee.
     */
    @Test
    void mustNotHappen_familyClassificationDependsOnWhoWantedIt() {
        assertEquals(1, java.util.Arrays.stream(GatherCandidatePolicy.class.getMethods())
                        .filter(method -> method.getName().equals("familyOf"))
                        .peek(method -> assertEquals(1, method.getParameterCount(),
                                "familyOf must take only the block state - an intent parameter is "
                                        + "how the collapse would come back"))
                        .count());
        assertEquals(GatherCandidatePolicy.familyOf(Blocks.IRON_ORE.defaultBlockState()),
                GatherCandidatePolicy.familyOf(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()),
                "the sweep records what the block IS, not why it was admitted");
    }

    // ------------------------------------------------------------------ wealth still works

    /**
     * Wealth activation is untouched by this repair — and cannot be isolated in a unit fixture.
     *
     * <p>"Saturated logs alone must not start a scan" is not constructible here: wealth contexts are
     * built for <b>every</b> resource, and coal, iron and cobblestone are unsaturated at zero held,
     * so {@code hasDemand()} is true whatever the logs do. Saturating all of them would be testing a
     * fixture rather than the rule.
     *
     * <p>What is checkable is that the repair did not reach into activation at all: it changed which
     * families a completed sweep <i>remembers</i>, not who may start one.
     */
    @Test
    void mustNotHappen_theRepairChangesWealthActivation() throws java.io.IOException {
        String intent = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/GatherIntentPolicy.java"));

        assertTrue(intent.contains("readyCraftStep == ScavengerCrafting.Step.NOTHING"),
                "shouldGather still gates on a ready craft step");
        assertFalse(intent.contains("lastScanFamilies"),
                "the family record belongs to the sweep, not to activation");
    }

    /** But unsaturated wealth still does — the repair must not quietly disable it. */
    @Test
    void mustHappen_unsaturatedWealthStillDrivesAGatherTrip() {
        ScavengerConfig cfg = ironWithWealth();
        cfg.maxPickTier = ToolTier.NONE;
        SimpleContainer empty = new SimpleContainer(8);

        assertTrue(GatherIntentPolicy
                        .evaluate(empty, ItemStack.EMPTY, ItemStack.EMPTY, cfg, 64)
                        .hasDemand(),
                "nothing held, greed on - ordinary wealth appetite is untouched");
    }

    /** And wealth may still admit the log as a candidate; selection is not what was wrong. */
    @Test
    void mustHappen_wealthMayStillAdmitAnOptionalCandidate() {
        ScavengerConfig cfg = ironWithWealth();
        GatherIntentPolicy.GatherIntent intent = GatherIntentPolicy.evaluate(
                saturatedLogsNoIron(), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.IRON_AXE), cfg, 64);

        assertTrue(intent.requiredResources().contains(GatherIntentPolicy.Resource.RAW_IRON),
                "iron is the mandatory need");
        assertFalse(intent.requiredResources().contains(GatherIntentPolicy.Resource.LOGS),
                "logs are wealth here, not need - V2-DEF-003 established that");
        assertTrue(intent.wants(GatherIntentPolicy.Resource.LOGS, 0.0F),
                "and wealth may still WANT them; forbidding that would be disabling wealth, which "
                        + "is explicitly not the repair");
    }

    // ------------------------------------------------------------------ the publish guard

    /**
     * The guard, read from the production source.
     *
     * <p>The end-to-end claim needs a live world — a bounded sweep over real blocks with a real
     * mob — so what is pinned here is that the publish decision is scoped to the demand's own
     * precursor rather than to the scan's overall verdict, and that the family record is taken at
     * pass one so a protection-rejected candidate still counts as present.
     */
    /**
     * Blocker 1 — the publish must be <b>reached</b> when another resource won selection.
     *
     * <p>{@code canUse} only called {@code publishRouteExhaustion} when {@code selected == null}.
     * The defect scenario is exactly the one where something WAS selected — a wealth log — while
     * the mandatory route found nothing, so the resource-specific logic was never reached in the
     * case it exists for. A structural test for "the file contains the scoped check" was true
     * throughout and proved nothing about that.
     */
    @Test
    void mustHappen_exhaustionIsEvaluatedAfterEverySweepNotOnlyEmptyOnes() throws java.io.IOException {
        String gather = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        int scan = gather.indexOf("GatherTarget selected = findTarget(cfg);");
        int publish = gather.indexOf("publishRouteExhaustion(cfg, now);", scan);
        int nullBranch = gather.indexOf("if (selected == null) {", scan);

        assertTrue(scan > 0 && publish > 0 && nullBranch > 0);
        assertTrue(publish < nullBranch,
                "the publish must run before - and therefore regardless of - the null-target "
                        + "branch, or a wealth log winning selection silences the mandatory route");
    }

    @Test
    void mustHappen_thePublishDecisionIsScopedToItsOwnResource() throws java.io.IOException {
        String gather = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java"));

        assertTrue(gather.contains("lastScanFamilies.contains(precursor.get())"),
                "the question is asked about RAW_IRON, not about whether the scan found anything");
        assertFalse(gather.contains(
                        "lastScanFailure != GatherCandidatePolicy.ScanFailureReason"
                                + ".NO_CANDIDATES_IN_RADIUS"),
                "the whole-scan verdict is exactly what let a wealth log answer for iron");
        assertTrue(gather.contains("sweep.offer(pos, dist, GatherCandidatePolicy.familyOf(state))"),
                "observed during the single sweep - no rescan per resource");
        assertFalse(gather.contains("lastScanFamilies::add"),
                "recording must not sit beside the buffer prune again; GatherScanSweep.offer owns "
                        + "the ordering now, and GatherScanSweepTest proves the behaviour");
    }

    /** Trade must not have acquired the ability to conclude this for itself. */
    @Test
    void mustNotHappen_tradePublishesRouteExhaustion() throws java.io.IOException {
        String trade = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"));

        assertFalse(trade.contains("RouteExhaustionEvidence.publish"),
                "gather is the existing-work owner and the only party allowed to publish its own "
                        + "search result");
    }
}
