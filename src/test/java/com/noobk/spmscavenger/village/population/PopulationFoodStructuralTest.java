package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Task-57 — structural boundaries for V3-E population food. */
class PopulationFoodStructuralTest {

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
    }

    @Test
    void mustHappen_handoffPreflightPeeksCurrentFactsNotPlanCapture() throws IOException {
        String body = source("village/PopulationFoodSupportAdmission.java");
        assertTrue(body.contains("VillageWorkFactsService.peek(level, plan.settlement())"));
        assertFalse(body.contains("plan.facts()"));
        assertFalse(body.contains("VillageWorkFactsService.schedule"),
                "admission must not schedule observation — selector owns refresh requests");
    }

    @Test
    void mustHappen_villageWorkAdmissionStillDoesNotReadWorkFacts() throws IOException {
        String body = source("village/VillageWorkAdmission.java");
        assertFalse(body.contains("VillageWorkFacts"));
        assertFalse(body.contains("village.work"));
    }

    @Test
    void mustNotHappen_goalMutatesVillageWorkFacts() throws IOException {
        String body = source("goal/PopulationFoodSupportGoal.java");
        assertFalse(body.contains("VillageWorkFactsService"),
                "goal must not write or schedule facts mutation");
        assertFalse(body.contains("setPopulation"));
        assertFalse(body.contains("persist"));
    }

    @Test
    void mustNotHappen_breederHomeProofNeverCallsPoiTake() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/BreederLocalHomeProof.java"));
        assertFalse(body.contains(".take("), "HOME proof must not call PoiManager.take");
        assertTrue(body.contains("getInRange"), "HOME proof must stay read-only");
    }

    @Test
    void mustNotHappen_handoffDoesNotCreditSocialOrFamiliarity() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/PopulationFoodHandoff.java"));
        assertFalse(body.contains("OpinionExperience"));
        assertFalse(body.contains("SocialExecution"));
        assertFalse(body.contains("familiarity"));
    }

    @Test
    void mustNotHappen_goalDoesNotMutateVillagerBrain() throws IOException {
        String body = source("goal/PopulationFoodSupportGoal.java");
        assertFalse(body.contains("getBrain()"));
        assertFalse(body.contains("VillagerBrain"));
    }

    @Test
    void mustNotHappen_goalDoesNotCommandBreeding() throws IOException {
        String body = source("goal/PopulationFoodSupportGoal.java");
        assertFalse(body.contains("breed"));
        assertFalse(body.contains("makeLove"));
    }

    @Test
    void mustHappen_reserveConstantIsTwelveNotZero() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/PopulationFoodTuning.java"));
        assertTrue(body.contains("MIN_SURVIVAL_NUTRITION_RESERVE = 12"));
        assertFalse(body.contains("MIN_SURVIVAL_NUTRITION_RESERVE = 0"));
    }

    @Test
    void mustHappen_recipientSelectorInspectsAtMostNearestCap() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/PopulationFoodRecipientSelector.java"));
        assertTrue(body.contains("VillagerRecipientCandidateSource"));
        assertTrue(body.contains("Math.min(matches.size(), PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES)"));
        assertFalse(body.contains("MAX_RECIPIENT_CANDIDATES + 1"),
                "cap must limit inspection work, not reject settlements larger than K");
    }

    @Test
    void mustHappen_homeProofIsExistentialWithProbeCap() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/BreederLocalHomeProof.java"));
        assertTrue(body.contains("VacantHomeCandidateSource"));
        assertTrue(body.contains("MAX_HOME_PROBES_PER_RECIPIENT"));
        assertFalse(body.contains("withinBudget && found"),
                "reachable HOME proof must not be invalidated by unexamined records");
    }
}
