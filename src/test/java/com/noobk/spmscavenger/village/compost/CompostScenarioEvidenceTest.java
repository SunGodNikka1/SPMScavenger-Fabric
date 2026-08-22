package com.noobk.spmscavenger.village.compost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.ObservationQuality;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import com.noobk.spmscavenger.village.VillageWorkAdmission;
import com.noobk.spmscavenger.village.work.ComposterWorkFacts;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkTuning;
import com.noobk.spmscavenger.village.work.WorkFactsCompleteness;
import com.noobk.spmscavenger.village.work.WorkFactsFreshness;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CLOSE-58-3 — locked static scenario evidence for Task-58 rows. */
class CompostScenarioEvidenceTest {

    private static final BlockPos ANCHOR_A = new BlockPos(0, 64, 0);
    private static final BlockPos ANCHOR_B = new BlockPos(48, 64, 0);
    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-00000000c058");
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    @AfterEach
    void clearMandatoryState() {
        MandatoryOwnershipRegistry.shutdownServerState();
    }

    /** T58-2 — no readable facts → no executor-local scan. */
    @Test
    void t58_2_selectorUsesCachedPeekOnlyNotObservationKernel() throws IOException {
        String selector = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostTargetSelector.java"));
        String goal = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/CompostGoal.java"));
        assertTrue(selector.contains("ComposterWorkFactsService.peek"));
        assertFalse(selector.contains("ComposterWorkObservationKernel"));
        assertFalse(selector.contains("getPoiManager"));
        assertFalse(goal.contains("ComposterWorkObservationKernel"));
    }

    @Test
    void t58_2_noReadableFactsYieldsEmptySelectionWithoutBlockScan() {
        assertFalse(CompostTargetSelector.select(null, null, 0L).isPresent());
    }

    /** T58-4 — stale facts during episode → zero debit via preflight fail-closed. */
    @Test
    void t58_4_staleFactsFailCurrentEvidenceCheck() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_A, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity identity = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        ComposterWorkFacts freshAtSelect = sampleFacts(identity, 50L);
        long staleTick = 50L + VillageWorkTuning.FRESHNESS_WINDOW_TICKS + 1;
        assertFalse(CompostAdmission.currentComposterEvidence(
                memory, identity, Optional.of(freshAtSelect), staleTick));
    }

    @Test
    void t58_4_negativeControl_planFactsWouldStillLookReadable() {
        SettlementIdentity identity = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        ComposterWorkFacts planFacts = sampleFacts(identity, 50L);
        assertTrue(planFacts.isReadable());
    }

    /** T58-5 — mandatory claim during episode → zero debit (permits gate). */
    @Test
    void t58_5_liveMandatoryClaimDeniesCompostAdmissionPath() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, "iron:raw_iron", 0, 100L);
        VillageWorkAdmission.Result result = VillageWorkAdmission.evaluate(
                VillageScenarioProfile.VILLAGE_ALLY,
                ActivityObservationService.summarize(List.of()),
                false,
                MandatoryOwnershipRegistry.liveClaim(MOB, 100L),
                100L);
        assertFalse(result.permitted());
    }

    @Test
    void t58_5_commitPreflightRoutesThroughPermitsAndLiveClaim() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostAdmission.java"));
        assertTrue(body.contains("commitPreflight"));
        assertTrue(body.contains("!permits(mob, selector, excludeFromObservation)"));
        assertTrue(body.contains("MandatoryOwnershipRegistry.liveClaim"));
    }

    /** T58-7 — invalid/full/unloaded target → zero debit. */
    @Test
    void t58_7_fullComposterFailsMechanicalInputGate() {
        BlockState full = Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 7);
        assertFalse(CompostMechanicalEligibility.canAcceptInput(full));
    }

    @Test
    void t58_7_nullLevelCommitAbortsWithoutDebit() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        var result = CompostTransaction.commit(null, null, backpack, 0, ANCHOR_A);
        assertEquals(CompostTransaction.CommitOutcome.ABORT, result.outcome());
        assertEquals(3, backpack.getItem(0).getCount());
    }

    @Test
    void t58_7_commitPreflightRequiresLoadedChunk() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostAdmission.java"));
        assertTrue(body.contains("!level.isLoaded(composterPos)"));
    }

    /** T58-8 — successful commit → exactly one insertion attempt. */
    @Test
    void t58_8_goalIssuesSingleCommitCallPerActivation() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/CompostGoal.java"));
        assertEquals(1, body.split("CompostTransaction.commit", -1).length - 1);
        assertFalse(body.contains("while ("));
    }

    @Test
    void t58_8_transactionMirrorsSingleShrinkOnlyAfterInsertItem() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostTransaction.java"));
        assertTrue(body.contains("ComposterBlock.insertItem"));
        assertTrue(body.contains("slotStack.shrink(1)"));
    }

    /** T58-9 — unchanged level after eligible insert → terminal committed attempt, no reroll. */
    @Test
    void t58_9_unchangedLevelMapsToCommittedNoLevelChangeOutcome() {
        var unchanged = new CompostTransaction.CommitResult(
                CompostTransaction.CommitOutcome.COMMITTED, 3, 3);
        assertEquals(CompostTerminalOutcome.COMMITTED_NO_LEVEL_CHANGE,
                CompostTerminalOutcome.fromCommitResult(unchanged));
    }

    @Test
    void t58_9_goalDoesNotRetryAfterTerminalOutcome() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/CompostGoal.java"));
        assertTrue(body.contains("CompostTerminalOutcome.fromCommitResult"));
        assertFalse(body.contains("while ("));
    }

    /** T58-11 — mobGriefing false → zero debit. */
    @Test
    void t58_11_mobGriefingFalseDeniesAdmission() {
        assertFalse(CompostAdmission.mobGriefingPermits(null));
    }

    @Test
    void t58_11_goalChecksMobGriefingBeforeSelection() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/CompostGoal.java"));
        assertTrue(body.contains("CompostAdmission.mobGriefingPermits(level)"));
        assertTrue(body.contains("GameRules.RULE_MOBGRIEFING")
                || body.contains("mobGriefingPermits"));
    }

    /** T58-15 — superseded settlement identity → zero debit. */
    @Test
    void t58_15_supersededAnchorFailsSettlementMemoryGate() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_B, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity retired = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        assertFalse(CompostAdmission.settlementStillRemembered(memory, retired));
    }

    @Test
    void t58_15_commitPreflightPeeksCurrentFactsNotPlanFacts() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostAdmission.java"));
        assertTrue(body.contains("ComposterWorkFactsService.peek(level, plan.settlement())"));
        assertFalse(body.contains("plan.facts()"));
    }

    private static ComposterWorkFacts sampleFacts(SettlementIdentity identity, long observedTick) {
        return new ComposterWorkFacts(
                identity,
                List.of(new BlockPos(1, 64, 0)),
                observedTick,
                WorkFactsCompleteness.COMPLETE,
                WorkFactsFreshness.FRESH);
    }
}
