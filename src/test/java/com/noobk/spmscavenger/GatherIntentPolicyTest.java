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

        assertTrue(intent.hasDemand());
        assertFalse(intent.shouldGather());
    }
}
