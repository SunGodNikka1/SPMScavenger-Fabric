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
        assertTrue(greet.contains("friendlyGreetActivityClass"),
                "continuation must share exact binding-aware FriendlyGreet semantics");
        assertTrue(greet.contains("method = {\"canUse\", \"method_6264\"}"),
                "admission remains an unbound SOCIAL_REFLEX");
        assertTrue(greet.contains("method = {\"canContinueToUse\", \"method_6266\"}"),
                "continuation has its own binding-aware policy hook");
        assertTrue(combat.contains("FlintAndSteelIgniteGoal"),
                "SPM 0.96's priority-1 combat ritual must share the existing target-provenance "
                        + "guard instead of bypassing shelter on passive hunt targets");
    }
}
