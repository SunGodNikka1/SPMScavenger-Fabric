package com.noobk.spmscavenger.village.compost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.goal.CompostGoal;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import org.junit.jupiter.api.Test;

/** Task-58 — VILLAGE_WORK taxonomy pin (T58-10 partial). */
class CompostTaxonomyTest {

    @Test
    void compostGoalClassifiesAsVillageWork() {
        assertEquals(
                ActivityClass.VILLAGE_WORK,
                MoveHolderClassifier.staticActivityClass(CompostGoal.class));
    }

    @Test
    void admissionBlocksConcurrentVillageWork() throws java.io.IOException {
        String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostAdmission.java"));
        assertTrue(body.contains("ActivityClass.VILLAGE_WORK"));
        assertTrue(body.contains("concurrentVillageWork"));
    }
}
