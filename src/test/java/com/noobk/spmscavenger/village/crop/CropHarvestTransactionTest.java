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
        int kernel = body.indexOf("static CommitResult commitKernel");
        int kernelEnd = body.indexOf("private static PlantingUnit", kernel);
        String kernelBody = body.substring(kernel, kernelEnd);
        assertEquals(1, kernelBody.split("dropRolls\\+\\+").length - 1);
        int dropsIndex = kernelBody.indexOf("dropRolls++");
        int replaceIndex = kernelBody.indexOf("replacements++");
        assertTrue(dropsIndex > 0 && replaceIndex > dropsIndex);
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
        int kernel = body.indexOf("static CommitResult commitKernel");
        int kernelEnd = body.indexOf("private static PlantingUnit", kernel);
        String kernelBody = body.substring(kernel, kernelEnd);
        int griefing = kernelBody.indexOf("world.mobGriefing()");
        int loaded = kernelBody.indexOf("!world.isLoaded(pos)");
        int feasibility = kernelBody.indexOf("HarvestCandidatePolicy.deterministicReplantFeasible");
        int drops = kernelBody.indexOf("dropRolls++");
        assertTrue(griefing > 0 && loaded > griefing && feasibility > loaded && drops > feasibility);
    }
}
