package com.noobk.spmscavenger.village.compost;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Task-58 — mechanical eligibility (G0-3). */
class CompostMechanicalEligibilityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void readyComposterRejectsInput() {
        BlockState ready = Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8);
        assertFalse(CompostMechanicalEligibility.canAcceptInput(ready));
    }

    @Test
    void partialComposterAcceptsInput() {
        BlockState partial = Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 3);
        assertTrue(CompostMechanicalEligibility.canAcceptInput(partial));
    }
}
