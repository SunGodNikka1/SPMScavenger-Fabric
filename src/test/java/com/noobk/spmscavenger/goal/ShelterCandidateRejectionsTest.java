package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterCandidateRejectionsTest {

    @Test
    void failedCandidatesAreBoundedAndPhysicallyExpire() {
        ShelterCandidateRejections rejections = new ShelterCandidateRejections();
        for (int i = 0; i < ShelterCandidateRejections.MAX_ENTRIES + 4; i++) {
            rejections.reject(new BlockPos(i, 64, 0), 0);
        }

        assertEquals(ShelterCandidateRejections.MAX_ENTRIES, rejections.size(0));
        assertFalse(rejections.contains(new BlockPos(0, 64, 0)));
        assertTrue(rejections.contains(new BlockPos(19, 64, 0)));
        assertEquals(0, rejections.size(ShelterCandidateRejections.REJECTION_TICKS + 1));
    }
}
