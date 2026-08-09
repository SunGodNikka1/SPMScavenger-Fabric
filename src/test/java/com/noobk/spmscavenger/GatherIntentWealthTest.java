package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-4 — wealth wired into gather intent.
 *
 * <p>The binding constraint is <b>exact-consumer parity at defaults</b> (D-MIW-004): with
 * {@code greed = 0} or {@code wealthLevel = 0}, intent must be byte-for-byte what it was before
 * wealth existed. The second constraint is that wealth is <b>additive</b> — it may only ever grow
 * the intent set, never shrink or redirect it (D-MIW-015 separates NEED from WEALTH).
 */
class GatherIntentWealthTest {

    private static final int BACKPACK_SIZE = 8;
    private static final int SURFACE = 64;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ScavengerConfig baseConfig() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.craftTools = true;
        cfg.maxPickTier = ToolTier.STONE;
        cfg.maxAxeTier = ToolTier.STONE;
        return cfg;
    }

    /** A mob with every consumer need already met, so intent is driven only by wealth. */
    private static SimpleContainer satisfiedPack() {
        SimpleContainer pack = new SimpleContainer(BACKPACK_SIZE);
        pack.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        pack.setItem(1, new ItemStack(Items.STONE_AXE));
        pack.setItem(2, new ItemStack(Items.TORCH, 64));
        pack.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
        return pack;
    }

    private static EnumSet<GatherIntentPolicy.Resource> intentOf(
            SimpleContainer pack, ScavengerConfig cfg) {
        return EnumSet.copyOf(
                GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, SURFACE).resources());
    }

    // ---- parity ----

    @Test
    void mustHappen_defaultsAreExactConsumerParity() {
        ScavengerConfig cfg = baseConfig();
        assertEquals(0.0, cfg.greed, "greed must default to 0");
        assertEquals(0.0, cfg.wealthLevel, "wealthLevel must default to 0");
        assertTrue(intentOf(satisfiedPack(), cfg).isEmpty(),
                "a mob with every consumer need met must want nothing at default config");
    }

    @Test
    void mustNotHappen_greedAloneEnablesWealth() {
        ScavengerConfig cfg = baseConfig();
        cfg.greed = 1.0;
        cfg.wealthLevel = 0.0;
        assertTrue(intentOf(satisfiedPack(), cfg).isEmpty(),
                "wealthLevel=0 must disable wealth regardless of greed");
    }

    @Test
    void mustNotHappen_wealthLevelAloneEnablesWealth() {
        ScavengerConfig cfg = baseConfig();
        cfg.greed = 0.0;
        cfg.wealthLevel = 1.0;
        assertTrue(intentOf(satisfiedPack(), cfg).isEmpty(),
                "greed=0 must disable wealth regardless of wealthLevel");
    }

    // ---- additive, never subtractive ----

    @Test
    void mustHappen_wealthAddsDesireBeyondConsumerNeed() {
        ScavengerConfig cfg = baseConfig();
        cfg.greed = 1.0;
        cfg.wealthLevel = 1.0;
        assertFalse(intentOf(satisfiedPack(), cfg).isEmpty(),
                "with wealth enabled a satisfied mob still wants more of something");
    }

    @Test
    void mustNotHappen_wealthRemovesAnythingTheConsumerAskedFor() {
        // An empty pack has real consumer deficits. Enabling wealth must produce a superset.
        SimpleContainer empty = new SimpleContainer(BACKPACK_SIZE);
        ScavengerConfig off = baseConfig();
        ScavengerConfig on = baseConfig();
        on.greed = 1.0;
        on.wealthLevel = 1.0;

        EnumSet<GatherIntentPolicy.Resource> consumerOnly = intentOf(empty, off);
        EnumSet<GatherIntentPolicy.Resource> withWealth = intentOf(empty, on);

        assertFalse(consumerOnly.isEmpty(), "an empty pack must have consumer deficits");
        assertTrue(withWealth.containsAll(consumerOnly),
                "wealth may add to intent but must never remove a consumer-driven resource");
    }

    @Test
    void mustNotHappen_greedOutOfRangeThrowsInsteadOfClamping() {
        // The policy validates its own context; the wiring clamps before constructing one, so an
        // out-of-range config must degrade rather than crash a mob tick.
        ScavengerConfig cfg = baseConfig();
        cfg.greed = 5.0;
        cfg.wealthLevel = 1.0;
        assertFalse(intentOf(satisfiedPack(), cfg).isEmpty(),
                "an out-of-range greed must be clamped by the wiring, not thrown from the policy");
    }
}
