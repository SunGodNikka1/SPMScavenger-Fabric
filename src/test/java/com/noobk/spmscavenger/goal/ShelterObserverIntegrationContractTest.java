package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterObserverIntegrationContractTest {

    @Test
    void repairReusesTheExistingSchedulerObserverAndKeepsStopNonDestructive() throws Exception {
        String observer = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExplorationActivityGoal.java"));
        String shelter = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/SeekShelterGoal.java"));
        String initializer = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/SpmScavenger.java"));
        String classifier = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mining/MoveHolderClassifier.java"));

        assertTrue(observer.contains("shelterGoal.observeScheduler(observation)"));
        assertTrue(observer.contains("ActivityObservationService.observe("));
        assertFalse(shelter.contains("getAvailableGoals()"),
                "SeekShelter must not add a competing scheduler scan");

        int stop = shelter.indexOf("public void stop()");
        int tick = shelter.indexOf("public void tick()", stop);
        String stopBody = shelter.substring(stop, tick);
        assertTrue(stopBody.contains("commitment.suspend()"));
        assertFalse(stopBody.contains("cancelCommitment"));
        assertFalse(stopBody.contains("release()"));
        assertFalse(stopBody.contains("commitment = null"));
        assertTrue(shelter.contains("PlayerMobs.stayAnchorState(mob)"
                + " == PlayerMobs.StayAnchorState.ABSENT"));
        assertTrue(shelter.contains("onEntityUnload(UUID mobId)"));
        assertTrue(shelter.contains("onDeath(UUID mobId)"));
        assertTrue(initializer.contains("cancelShelterCommitment(mob);"));
        assertTrue(initializer.contains("shelterGoal.cancelForOwnerRemoval();"));
        assertTrue(classifier.contains("shelterGoal.isRestingAtShelter()"));
    }
}
