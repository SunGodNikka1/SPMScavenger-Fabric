package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterStructuralSatisfactionContractTest {

    @Test
    void structuralEvidenceUsesTwoBlockWallsAndTreatsLeavesAsCoverOnly() throws Exception {
        String source = seekShelterSource();

        assertTrue(source.contains("isStructuralBoundary(level, lower, lowerState)"));
        assertTrue(source.contains("&& isStructuralBoundary(level, upper, upperState)"));
        assertTrue(source.contains("!state.is(BlockTags.LEAVES)"));
        assertTrue(source.contains("state.getBlock() instanceof DoorBlock"));
        assertFalse(source.contains("state.is(BlockTags.LOGS)"),
                "Log walls must not be blacklisted");
    }

    @Test
    void currentInteriorIsSatisfiedBeforeGenericSearchAndBedsNeedProtectedRoutes() throws Exception {
        String source = seekShelterSource();

        assertTrue(source.contains("boolean currentlyInterior"));
        assertTrue(source.contains("if (currentlyInterior && cfg.sleepInBeds)"));
        assertTrue(source.contains("tryAdopt(candidate, pathProbeBudget, true, now)"));
        assertTrue(source.contains("tryAdopt(current, pathProbeBudget, false, now)"));
        assertTrue(source.contains("UPGRADE_SCAN_INTERVAL = 200"));
        assertTrue(source.contains("candidate.tier().ordinal() <= minimumTierExclusive.ordinal()"));
        assertTrue(source.contains("createPath(raw.standPos(), 1)"));
        assertFalse(source.contains("raw.bed() ? 1 : 0"));
    }

    @Test
    void displacedArrivalSuspendsRestAndReturnsToSameCommitment() throws Exception {
        String source = seekShelterSource();
        String commitment = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ShelterCommitment.java"));

        assertTrue(source.contains("commitment.beginReturning(now)"));
        assertTrue(source.contains("suspendShelterRecovery(mob, commitment.commitmentId(), now)"));
        assertTrue(source.contains("mob, commitment.commitmentId(), standPos"));
        assertTrue(commitment.contains("RETURNING"));
        assertTrue(commitment.contains("returnActiveTicks"));
        assertTrue(commitment.contains("returnPathFailures"));
        assertTrue(source.contains("ShelterNightAuthority.release(mob.getUUID())"));
    }

    @Test
    void activeLowerTierTravelCapturesCurrentInteriorAtomically() throws Exception {
        String source = seekShelterSource();

        assertTrue(source.contains("captureCurrentInterior(mob.level().getGameTime())"));
        assertTrue(source.contains("INTERIOR_CAPTURE_INTERVAL = 10"));
        assertTrue(source.contains("shouldCaptureCurrentInterior("));
        assertTrue(source.contains("tryAdopt(current, new ShelterSelectionPolicy.PathProbeBudget()"));
        assertTrue(source.contains("commitment.arrive()"));
        assertTrue(source.contains("ShelterNightAuthority.acquire(mob.getUUID())"));
        assertTrue(source.contains("mob.getNavigation().stop()"));
    }

    @Test
    void replacingAnArrivedFallbackReleasesItsHoldBeforeTheNewApproach() throws Exception {
        String source = seekShelterSource();
        int replacement = source.indexOf("ShelterCommitment replacement");
        int assigned = source.indexOf("commitment = replacement", replacement);
        String transaction = source.substring(replacement, assigned);

        assertTrue(transaction.contains("ShelterNightAuthority.release(mob.getUUID())"));
        assertFalse(transaction.contains("ShelterNightAuthority.acquire"),
                "Replacement selection must not acquire arrival authority before physical arrival");
    }

    private static String seekShelterSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/SeekShelterGoal.java"));
    }
}
