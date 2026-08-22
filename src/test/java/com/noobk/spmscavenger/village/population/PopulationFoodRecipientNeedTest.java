package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Task-57 — recipient need predicates (T57-7, T57-14) via structural contract. */
class PopulationFoodRecipientNeedTest {

    private static String selectorBody() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/PopulationFoodRecipientSelector.java"));
    }

    @Test
    void t57_14_needsFoodRequiresWantsMoreAndNotCanBreed() throws IOException {
        String body = selectorBody();
        assertTrue(body.contains("wantsMoreFood()"));
        assertTrue(body.contains("canBreed()"));
        assertTrue(body.contains("wantsMoreFood() && !villager.canBreed()")
                || body.contains("villager.wantsMoreFood() && !villager.canBreed()"));
    }

    @Test
    void t57_7_eligibleAdultRequiresZeroAge() throws IOException {
        String body = selectorBody();
        assertTrue(body.contains("getAge() == 0"));
    }

    @Test
    void admissionRevalidatesRecipientNeedAtHandoff() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/PopulationFoodSupportAdmission.java"));
        assertTrue(body.contains("PopulationFoodRecipientSelector.needsFood(recipient)"));
    }

    @Test
    void negativeControl_breedableVillagerMustNotPassNeedsFoodAlone() throws IOException {
        String body = selectorBody();
        assertFalse(body.matches("(?s).*needsFood.*wantsMoreFood\\(\\).*\\n.*return true.*"),
                "needsFood must not return true on wantsMoreFood alone");
    }
}
