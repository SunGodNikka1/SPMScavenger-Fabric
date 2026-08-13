package com.noobk.spmscavenger.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorOperationShelterHoldMixinContractTest {

    @Test
    void optionalGuardSuppressesOnlySchedulerWrapperDuringExactArrivedHold() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/DoorOperationShelterHoldMixin.java"));
        String config = Files.readString(Path.of("src/main/resources/spmscavenger.mixins.json"));

        assertTrue(source.contains("@Pseudo"));
        assertTrue(source.contains("games.brennan.playermob.entity.goal.DoorOperationGoal"));
        // Assert the naming CONTRACT, not one literal spelling: SPM ships intermediary, so a
        // readable-only target silently matches nothing. See SpmGoalMixinNamingTest.
        for (String name : new String[] {"canUse", "method_6264",
                "canContinueToUse", "method_6266"}) {
            assertTrue(source.contains("\"" + name + "\""),
                    "missing injector target " + name);
        }
        assertTrue(source.contains("require = 0"));
        assertTrue(source.contains("ShelterNightAuthority.isSettled(mob.getUUID())"));
        assertTrue(config.contains("DoorOperationShelterHoldMixin"));

        assertFalse(source.contains("beginDoorOperation"),
                "The addon must not duplicate SPM's physical door operation");
        assertFalse(source.contains("setOpen("),
                "The addon must not directly mutate the host door state");
        assertFalse(source.contains("getNavigation().stop"),
                "The compatibility guard must not acquire movement authority itself");
    }
}
