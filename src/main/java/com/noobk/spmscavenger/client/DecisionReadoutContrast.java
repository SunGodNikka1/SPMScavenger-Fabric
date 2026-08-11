package com.noobk.spmscavenger.client;

import net.minecraft.client.renderer.LightTexture;

/**
 * Client-only contrast policy for Social Player Mobs' Creative decision readout.
 *
 * <p>The host renderer submits nominally white text using the entity's world light. In caves that
 * makes the label dark even though its ARGB colour is white. Scavenger keeps the host's visibility,
 * distance, focus, layout and text ownership intact; this policy changes only the draw arguments.
 */
public final class DecisionReadoutContrast {

    public static final int HOST_SECONDARY_TEXT = 0xFFBFBFBF;
    public static final int LIGHT_SECONDARY_TEXT = 0xFFE6E6E6;

    private DecisionReadoutContrast() {
    }

    /** Full-bright affects only the glyph draw; it does not make the entity or world glow. */
    public static int packedLight() {
        return LightTexture.FULL_BRIGHT;
    }

    public static int textColor(int original, boolean seeThrough) {
        // The translucent pass and its user-controlled backdrop remain entirely host-owned.
        if (seeThrough) return original;
        return original == HOST_SECONDARY_TEXT ? LIGHT_SECONDARY_TEXT : original;
    }

    /** Never override SPM/Minecraft's user-controlled background plate. */
    public static int backgroundColor(int original, boolean seeThrough) {
        return original;
    }
}
