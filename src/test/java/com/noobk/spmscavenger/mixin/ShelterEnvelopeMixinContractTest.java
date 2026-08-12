package com.noobk.spmscavenger.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShelterEnvelopeMixinContractTest {

    @Test
    void optionalHostHooksDelegateToOnePolicyAndRemainOptional() throws Exception {
        String greet = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/FriendlyGreetShelterHoldMixin.java"));
        String combat = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/WeaponAttackShelterHoldMixin.java"));
        String mixins = Files.readString(Path.of("src/main/resources/spmscavenger.mixins.json"));

        for (String source : java.util.List.of(greet, combat)) {
            assertTrue(source.contains("@Pseudo"));
            assertTrue(source.contains("require = 0"));
            assertTrue(source.contains("ShelterActivityEnvelope"));
            assertFalse(source.contains("ShelterNightAuthority"));
        }
        assertTrue(mixins.contains("FriendlyGreetShelterHoldMixin"));
        assertTrue(mixins.contains("WeaponAttackShelterHoldMixin"));
        for (String pinnedTravel : java.util.List.of(
                "FriendlyGreetGoal", "FollowLovedOneGoal", "SeekAmmoGoal",
                "RaidContainersGoal", "RaidArmorStandsGoal", "CollectFloorItemsGoal",
                "HarvestCropsGoal", "AdvanceCarriageGoal", "CrossGroupGapGoal")) {
            assertTrue(greet.contains(pinnedTravel), pinnedTravel);
        }
    }
}
