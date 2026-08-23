package com.noobk.spmscavenger.debug;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W2-P2/W2-L1 negative controls and authority-boundary structural contract. */
class TeCurrencyWitnessFixtureTest {

    @Test
    void preArmExactPreparationMayRollback() {
        assertTrue(TeCurrencyWitnessFixture.mayRollbackInventory(
                TeCurrencyWitnessFixture.Phase.PREPARED, true, true));
    }

    @Test
    void postArmNeverDeletesInventoryByTypeAllowlist() {
        assertFalse(TeCurrencyWitnessFixture.mayRollbackInventory(
                TeCurrencyWitnessFixture.Phase.ARMED, true, true));
        assertFalse(TeCurrencyWitnessFixture.mayRollbackInventory(
                TeCurrencyWitnessFixture.Phase.ARMED, true, false));
    }

    @Test
    void failedIdentityOrProvenancePreservesInventory() {
        assertFalse(TeCurrencyWitnessFixture.mayRollbackInventory(
                TeCurrencyWitnessFixture.Phase.PREPARED, false, true));
        assertFalse(TeCurrencyWitnessFixture.mayRollbackInventory(
                TeCurrencyWitnessFixture.Phase.PREPARED, true, false));
    }

    @Test
    void fixtureCannotAcquireProductionAuthority() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/TeCurrencyWitnessFixture.java"));
        for (String forbidden : new String[] {
                "RouteExhaustionEvidence.publish(",
                "RouteExhaustionEvidence.clear(",
                "QuoteBridge", ".quote(",
                "TradeEverythingTradeSource", ".revalidate(",
                "TradeDemandGate", ".authorize(",
                "VillagerTradeAdapter", ".perform"
        }) {
            assertFalse(source.contains(forbidden), () -> "fixture contains forbidden seam: " + forbidden);
        }
        assertTrue(source.contains("TeCurrencyWitnessTracker.arm("),
                "run must delegate to the existing witness preflight/arm");
    }

    @Test
    void validationPrecedesFirstMutationAndFailureRollsBack() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/TeCurrencyWitnessFixture.java"));
        int validation = source.indexOf("List<String> failures = validate(mob, server)");
        int mutation = source.indexOf("backpack.setItem(0, sticks)");
        int rollback = source.indexOf("rollbackKnownPreparation(mob, backpack)", mutation);
        assertTrue(validation >= 0 && mutation > validation,
                "all refusals must be computed before fixture inventory mutation");
        assertTrue(rollback > mutation, "partial preparation failure must roll back before arm");
    }
}
