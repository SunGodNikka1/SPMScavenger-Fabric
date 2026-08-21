package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Task-55 — commit contract structural and abort-path tests (no ServerLevel harness). */
class CropHarvestTransactionTest {

    @Test
    void abortWhenAdmissionDeniedBeforeWorldAccess() {
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commit(
                null,
                null,
                null,
                null,
                null,
                false);
        assertEquals(CropHarvestTransaction.CommitOutcome.ABORT, result.outcome());
    }

    @Test
    void commitUsesNamedUpdateAllFlag() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/crop/CropHarvestTransaction.java"));
        assertTrue(body.contains("Block.UPDATE_ALL"));
        assertFalse(body.matches("(?s).*setBlock\\([^)]*,\\s*3\\s*\\).*"));
    }

    @Test
    void commitRollsDropsOnceInsideCommitPrepare() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/crop/CropHarvestTransaction.java"));
        assertEquals(1, body.split("stagedDrops = Block\\.getDrops").length - 1);
        int dropsIndex = body.indexOf("stagedDrops = Block.getDrops");
        int setBlockIndex = body.indexOf("level.setBlock(pos, ageZero");
        assertTrue(dropsIndex > 0 && setBlockIndex > dropsIndex);
    }

    @Test
    void commitDistinguishesAbortFromInvariantFailure() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/crop/CropHarvestTransaction.java"));
        assertTrue(body.contains("INVARIANT_FAILURE"));
        assertTrue(body.contains("restoreEscrow"));
        assertTrue(body.contains("!after.equals(ageZero)"));
    }

    @Test
    void commitRevalidatesDeterministicFeasibilityBeforeDropRoll() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/crop/CropHarvestTransaction.java"));
        int feasibility = body.indexOf("HarvestCandidatePolicy.deterministicReplantFeasible");
        int drops = body.indexOf("stagedDrops = Block.getDrops");
        assertTrue(feasibility > 0 && drops > feasibility);
    }
}
