package com.noobk.spmscavenger.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Task-55 CLOSE-1 — crop continuation stays fail-open when compatibility evidence is missing. */
class HarvestCropsManagedDomainMixinContinuationTest {

    @Test
    void unresolvedContinuationDoesNotForceHostFalse() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/mixin/HarvestCropsManagedDomainMixin.java"));
        int continuation = body.indexOf("spmscavenger$vetoCanContinue");
        int unresolved = body.indexOf("recordTargetResolutionFailed()", continuation);
        int nextMethod = body.indexOf("if (HarvestCropVetoPolicy.shouldVeto", unresolved);
        String unresolvedBranch = body.substring(unresolved, nextMethod);
        assertTrue(unresolvedBranch.contains("recordTargetResolutionFailed()"));
        assertFalse(unresolvedBranch.contains("setReturnValue(false)"));
    }

    @Test
    void positiveManagedDomainStillVetoesContinuation() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/mixin/HarvestCropsManagedDomainMixin.java"));
        int continuation = body.indexOf("spmscavenger$vetoCanContinue");
        String continuationBody = body.substring(continuation);
        assertTrue(continuationBody.contains("HarvestCropVetoPolicy.shouldVeto(mob, level, target)"));
        assertTrue(continuationBody.contains("cir.setReturnValue(false)"));
    }
}
