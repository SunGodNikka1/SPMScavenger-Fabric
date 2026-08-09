package com.noobk.spmscavenger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-TTU-010 / TT-1aC — Phase 1 craft-target caps. */
class ScavengerConfigTierTest {

    /** TT-2d(min): IRON is now craftable; DIAMOND remains clamped to IRON. */
    @Test
    void u10a_supportedCapsSurviveUnchanged() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.DIAMOND;

        // Phase 3: DIAMOND is craftable now, so a supported cap must survive untouched.
        assertFalse(cfg.normalizeCraftTargets(), "supported caps need no normalisation");
        assertEquals(ToolTier.IRON, cfg.maxPickTier);
        assertEquals(ToolTier.DIAMOND, cfg.maxAxeTier);
    }

    /** Fresh config targets diamond progression when tool crafting is enabled. */
    @Test
    void freshDefaultsTargetDiamond() {
        ScavengerConfig cfg = new ScavengerConfig();
        assertEquals(ToolTier.DIAMOND, cfg.maxPickTier);
        assertEquals(ToolTier.DIAMOND, cfg.maxAxeTier);
    }

    /** Null caps still fail closed to the conservative default, not the field default. */
    @Test
    void u10b_nullCapsFailClosedToTheDefault() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = null;
        cfg.maxAxeTier = null;

        // Deliberate Phase 3 change: null used to resolve to the highest craftable tier, which
        // after Phase 3 would grant DIAMOND from an unparseable config. Corrupt input fails closed.
        assertTrue(cfg.normalizeCraftTargets());
        assertEquals(ScavengerConfig.DEFAULT_CRAFT_TIER, cfg.maxPickTier);
        assertEquals(ScavengerConfig.DEFAULT_CRAFT_TIER, cfg.maxAxeTier);
    }

    @Test
    void craftableCapsRemainUnchanged() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.WOOD;
        cfg.maxAxeTier = ToolTier.NONE;

        assertFalse(cfg.normalizeCraftTargets());
        assertEquals(ToolTier.WOOD, cfg.maxPickTier);
        assertEquals(ToolTier.NONE, cfg.maxAxeTier);
    }

    /**
     * Phase 3 (D-TTU-023) expands the craftable caps to DIAMOND. Updated deliberately rather than
     * deleted: this test's job is to make any change to the reachable tier set a conscious one, and
     * it did exactly that when diamond was added.
     */
    @Test
    void uiSelectionsCoverEveryCraftableTier() {
        assertEquals(5, ScavengerConfig.CRAFTABLE_TIER_CAPS.size());
        assertTrue(ScavengerConfig.CRAFTABLE_TIER_CAPS.contains(ToolTier.NONE));
        assertTrue(ScavengerConfig.CRAFTABLE_TIER_CAPS.contains(ToolTier.WOOD));
        assertTrue(ScavengerConfig.CRAFTABLE_TIER_CAPS.contains(ToolTier.STONE));
        assertTrue(ScavengerConfig.CRAFTABLE_TIER_CAPS.contains(ToolTier.IRON));
        assertTrue(ScavengerConfig.CRAFTABLE_TIER_CAPS.contains(ToolTier.DIAMOND),
                "Phase 3: diamond must be selectable or its consumer can never activate");
    }

    /** Every craftable cap must survive load-time sanitisation, or the UI offers a lie. */
    @Test
    void everyOfferedCapSurvivesSanitisation() {
        for (ToolTier tier : ScavengerConfig.CRAFTABLE_TIER_CAPS) {
            ScavengerConfig cfg = new ScavengerConfig();
            cfg.maxPickTier = tier;
            cfg.maxAxeTier = tier;
            cfg.normalizeCraftTargets();
            assertEquals(tier, cfg.maxPickTier, "offered cap " + tier + " was clamped away");
            assertEquals(tier, cfg.maxAxeTier, "offered cap " + tier + " was clamped away");
        }
    }
}
