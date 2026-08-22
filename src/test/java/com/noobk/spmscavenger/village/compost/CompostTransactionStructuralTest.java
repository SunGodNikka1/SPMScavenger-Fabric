package com.noobk.spmscavenger.village.compost;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Task-58 — COMMIT debit ownership (T58-16, D-VR-087-TX1). */
class CompostTransactionStructuralTest {

    @Test
    void t58_16_singleDebitOwnerUsesInsertItemThenMirrorShrink() throws java.io.IOException {
        String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostTransaction.java"));
        assertTrue(body.contains("ComposterBlock.insertItem"));
        assertTrue(body.contains("slotStack.shrink(1)"));
        assertFalse(body.contains("remove("), "pre-debit via ContainerMerge.remove is forbidden");
        assertFalse(body.contains("consume(1)"), "pre-debit via stack.consume is forbidden");
    }

    @Test
    void t58_14_noExtractProducePath() throws java.io.IOException {
        String goal = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/CompostGoal.java"));
        String transaction = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostTransaction.java"));
        assertFalse(goal.contains("extractProduce"));
        assertFalse(transaction.contains("extractProduce"));
    }
}
