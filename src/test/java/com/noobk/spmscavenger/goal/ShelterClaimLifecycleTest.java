package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterClaimLifecycleTest {

    private static final BlockPos BED_A = new BlockPos(3, 64, 4);
    private static final BlockPos BED_B = new BlockPos(7, 64, 8);
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000061");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000062");

    @AfterEach
    void clearClaims() {
        SeekShelterGoal.shutdownServerState();
    }

    @Test
    void unloadReleasesOnlyTheOwningMobsClaims() {
        SeekShelterGoal.claimForTest(BED_A, A, 600L);
        SeekShelterGoal.claimForTest(BED_B, B, 600L);

        SeekShelterGoal.onEntityUnload(A);

        assertFalse(SeekShelterGoal.ownsClaimForTest(BED_A, A));
        assertTrue(SeekShelterGoal.ownsClaimForTest(BED_B, B));
        assertEquals(1, SeekShelterGoal.bedClaimCount());
    }

    @Test
    void deathReleasesClaim() {
        SeekShelterGoal.claimForTest(BED_A, A, 600L);
        SeekShelterGoal.onDeath(A);
        assertEquals(0, SeekShelterGoal.bedClaimCount());
    }

    @Test
    void bothBedHalvesResolveToOneClaimKey() {
        BlockPos foot = new BlockPos(10, 64, 10);
        BlockPos head = foot.relative(Direction.EAST);

        assertEquals(head, SeekShelterGoal.canonicalBedPos(foot, Direction.EAST, BedPart.FOOT));
        assertEquals(head, SeekShelterGoal.canonicalBedPos(head, Direction.EAST, BedPart.HEAD));
    }
}
