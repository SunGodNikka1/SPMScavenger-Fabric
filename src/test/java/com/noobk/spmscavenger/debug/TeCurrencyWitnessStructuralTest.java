package com.noobk.spmscavenger.debug;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the temporary witness to observation-only production seams. */
class TeCurrencyWitnessStructuralTest {

    @Test
    void mustNotHappen_trackerBecomesASecondTradeOrInventoryAuthority() throws IOException {
        String tracker = source("debug/TeCurrencyWitnessTracker.java");

        assertFalse(tracker.contains("ServerTickEvents"), "witness must have no tick loop");
        assertFalse(tracker.contains("extends Goal"), "witness must not enter GoalSelector");
        assertFalse(tracker.contains("TradeSources.register"), "witness must not inject an offer source");
        assertFalse(tracker.contains("TradeDemandGate.authorize"), "witness must not authorize trading");
        assertFalse(tracker.contains("installOptionalProvider("), "witness must not change currency authority");
        assertFalse(tracker.contains(".setItem("), "witness must not mutate the backpack");
        assertFalse(tracker.contains("TradeTransaction.commit("), "witness must not commit inventory");
        assertFalse(tracker.contains("TradeTransaction.debit("), "witness must not debit inventory");
        assertFalse(tracker.contains("TradeTransaction.insert("), "witness must not insert inventory");
        assertFalse(tracker.contains(".normalizeForPayment("), "witness must not normalize currency");
        assertFalse(tracker.contains("ConcurrentHashMap"), "single witness must not become a registry");
    }

    @Test
    void mustHappen_hooksBracketTheExistingTransactionWithoutReplacingIt() throws IOException {
        String adapter = source("village/trade/VillagerTradeAdapter.java");
        int stage = adapter.indexOf("ItemStack[] staged = TradeTransaction.stage(backpack)");
        int entered = adapter.indexOf("observePaymentStageEntered", stage);
        int normalize = adapter.indexOf("currency.normalizeForPayment", entered);
        int normalized = adapter.indexOf("observeBlockNormalization", normalize);
        int debit = adapter.indexOf("TradeTransaction.debit(staged, live.getCostA())", normalized);
        int commit = adapter.indexOf("TradeTransaction.commit(backpack, staged)", debit);
        int observedCommit = adapter.indexOf("observeCommit", commit);
        int notify = adapter.indexOf("notify.accept(live)", observedCommit);

        assertTrue(stage >= 0 && stage < entered);
        assertTrue(entered < normalize && normalize < normalized);
        assertTrue(normalized < debit && debit < commit);
        assertTrue(commit < observedCommit && observedCommit < notify);
    }

    @Test
    void mustHappen_commandAndCleanupReuseExistingProductionTrees() throws IOException {
        String command = source("command/VillageProfileCommands.java");
        String lifecycle = source("SpmScavenger.java");
        String goal = source("goal/TradeWithVillagerGoal.java");

        assertTrue(command.contains("then(TeCurrencyWitnessCommands.debugBranch())"));
        assertTrue(lifecycle.contains("TeCurrencyWitnessTracker.abortForMob"));
        assertTrue(lifecycle.contains("TeCurrencyWitnessTracker.shutdownServerState"));
        assertTrue(goal.contains("observeQ1"));
        assertTrue(goal.contains("observeFundingPlan"));
        assertTrue(goal.contains("observeQ2"));
        assertTrue(goal.contains("observePurchaseSelected"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
    }
}
