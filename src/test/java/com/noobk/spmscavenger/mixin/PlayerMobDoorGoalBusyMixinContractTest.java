package com.noobk.spmscavenger.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMobDoorGoalBusyMixinContractTest {

    @Test
    void optionalGuardRepairsOneBoundedDoorPassageWithoutOperatingDoors() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/PlayerMobDoorGoalBusyMixin.java"));
        String config = Files.readString(Path.of("src/main/resources/spmscavenger.mixins.json"));

        assertTrue(source.contains("@Pseudo"));
        assertTrue(source.contains("games.brennan.playermob.entity.goal.PlayerMobDoorGoal"));
        for (String name : new String[] {"canUse", "method_6264", "start", "method_6269",
                "tick", "method_6268", "stop", "method_6270"}) {
            assertTrue(source.contains("\"" + name + "\""),
                    "missing injector target " + name);
        }
        assertTrue(source.contains("require = 0"));
        assertTrue(source.contains("getMethod(\"isOperatingDoor\")"));
        assertTrue(source.contains("getMethod(\"isRecovering\")"));
        assertTrue(source.contains("cir.setReturnValue(false)"));
        assertTrue(source.contains("DoorPassagePolicy.admitOpenEpisode"));
        assertTrue(source.contains("DoorPassagePolicy.EncounterKey"));
        assertTrue(source.contains("DoorPassagePolicy.approachSide"));
        assertTrue(source.contains("DoorPassagePolicy.separated"));
        assertTrue(source.contains("DoorPassagePolicy.nextGeneration"));
        assertTrue(source.contains("spmscavenger$pauseCrossingClockDuringOperation"));
        assertTrue(source.contains("DoorPassagePolicy.closeAfterEpisode"));
        assertFalse(source.contains("import net.minecraft.world.level.pathfinder.Path"));
        assertFalse(source.contains("getNavigation().getPath()"));
        assertTrue(source.contains("stock SPM behavior retained"));
        assertFalse(source.contains("DoorObstruction"));
        assertFalse(source.contains("setOpen("));
        assertTrue(config.contains("PlayerMobDoorGoalBusyMixin"));
    }
}
