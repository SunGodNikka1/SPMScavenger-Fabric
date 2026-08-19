package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherIntentPolicyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void torchShortageProducesOneIntentWithLogsAndCoal() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.craftTools = false;
        SimpleContainer pack = new SimpleContainer(8);

        GatherIntentPolicy.GatherIntent intent =
                GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, 64);

        assertTrue(intent.wants(GatherIntentPolicy.Resource.LOGS));
        assertTrue(intent.wants(GatherIntentPolicy.Resource.COAL));
        assertTrue(intent.shouldGather());
    }

    @Test
    void ironConsumerProducesRawIronIntent() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.IRON;
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.STONE_PICKAXE));

        GatherIntentPolicy.GatherIntent intent =
                GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, 64);

        assertTrue(intent.wants(GatherIntentPolicy.Resource.RAW_IRON));
    }

    @Test
    void diamondIntentIsPlausibilityGatedByAltitude() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.DIAMOND;
        cfg.maxAxeTier = ToolTier.DIAMOND;
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.IRON_PICKAXE));

        assertFalse(GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, 64)
                .wants(GatherIntentPolicy.Resource.DIAMOND));
        assertTrue(GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, 0)
                .wants(GatherIntentPolicy.Resource.DIAMOND));
    }

    @Test
    void craftReadySuppressesAnotherGatherTrip() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.NONE;
        cfg.torchStockTarget = 0;
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        pack.setItem(1, new ItemStack(Items.STICK, 2));
        pack.setItem(2, new ItemStack(Items.IRON_INGOT, 3));

        GatherIntentPolicy.GatherIntent intent =
                GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, 64);

        // V2-DEF-003: this used to assert hasDemand() == true, which was only true because
        // `wantsPickUpgrade -> LOGS` made logs mandatory even with every ingredient already held.
        // The test was encoding the defect. With the consumer frontier there is nothing to acquire,
        // which is a stronger form of the property this test exists for.
        assertFalse(intent.hasDemand(),
                "3 iron and 2 sticks IS the iron pickaxe - nothing remains to be acquired");
        assertFalse(intent.shouldGather());
    }

    /**
     * The suppression property itself, with a demand that is genuinely present.
     *
     * <p>The case above no longer exercises it — no demand means no trip regardless — so the
     * ready-craft suppression is proved separately rather than left implied.
     */
    @Test
    void craftReadyStillSuppressesAGatherTripWhenSomethingIsActuallyWanted() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.NONE;
        cfg.maxAxeTier = ToolTier.NONE;
        cfg.torchStockTarget = 8;
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.COAL, 4));
        pack.setItem(1, new ItemStack(Items.STICK, 4));

        GatherIntentPolicy.GatherIntent intent =
                GatherIntentPolicy.evaluate(pack, ItemStack.EMPTY, cfg, 64);

        assertTrue(intent.hasDemand(), "torches are below target, so the torch chain wants input");
        assertFalse(intent.shouldGather(),
                "but MAKE_TORCHES is ready, and crafting what we hold comes before another trip");
    }

    @Test
    void lootedDiamondPickInOffHandSuppressesRedundantPickProgression() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.DIAMOND;
        cfg.maxAxeTier = ToolTier.NONE;
        cfg.torchStockTarget = 0;
        SimpleContainer pack = new SimpleContainer(8);

        GatherIntentPolicy.GatherIntent intent = GatherIntentPolicy.evaluate(
                pack,
                ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND_PICKAXE),
                cfg,
                0);

        assertFalse(intent.wants(GatherIntentPolicy.Resource.RAW_IRON));
        assertFalse(intent.wants(GatherIntentPolicy.Resource.DIAMOND));
        assertFalse(intent.shouldGather());
    }
}
