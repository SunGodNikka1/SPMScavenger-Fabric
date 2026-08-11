package com.noobk.spmscavenger.client;

import net.minecraft.client.renderer.LightTexture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionReadoutContrastTest {

    @Test
    void darkWorldLightCannotDimDecisionText() {
        assertEquals(LightTexture.FULL_BRIGHT, DecisionReadoutContrast.packedLight());
    }

    @Test
    void primaryStaysWhiteAndSecondaryBecomesLighter() {
        assertEquals(0xFFFFFFFF, DecisionReadoutContrast.textColor(0xFFFFFFFF, false));
        assertEquals(
                DecisionReadoutContrast.LIGHT_SECONDARY_TEXT,
                DecisionReadoutContrast.textColor(
                        DecisionReadoutContrast.HOST_SECONDARY_TEXT, false));
    }

    @Test
    void backdropHasAtLeastHalfOpacityButNeverGetsWeaker() {
        assertEquals(0x80000000, DecisionReadoutContrast.backgroundColor(0x40000000, true));
        assertEquals(0xC0000000, DecisionReadoutContrast.backgroundColor(0xC0000000, true));
        assertEquals(0, DecisionReadoutContrast.backgroundColor(0, false));
    }

    @Test
    void seeThroughPassIsReadableAndUnrelatedSolidColorsRemainOwnedByHost() {
        assertEquals(
                DecisionReadoutContrast.SEE_THROUGH_TEXT,
                DecisionReadoutContrast.textColor(0x20FFFFFF, true));
        assertEquals(0xFF55AA55, DecisionReadoutContrast.textColor(0xFF55AA55, false));
    }
}
