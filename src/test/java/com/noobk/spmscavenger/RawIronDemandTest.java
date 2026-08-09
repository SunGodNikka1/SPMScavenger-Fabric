package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TT-2c — iron ore is mined because a consumer wants it, never because ore exists.
 *
 * <p>Deliberately does <b>not</b> assert the D-TTU-012 live capability gate (golden pick refused,
 * iron pick accepted). That gate resolves through {@code ItemStack.isCorrectToolForDrops}, which
 * reads the {@code #minecraft:incorrect_for_*_tool} <b>block tags</b>, and
 * {@link Bootstrap#bootStrap()} binds tags <b>empty</b> without a datapack — the same constraint
 * {@code ScavengerCraftingTest} documents for {@code ItemTags}. A unit test here would assert
 * against empty tags and could pass for the wrong reason. That gate is runtime-verifiable only and
 * is recorded as such in the RFC.
 */
class RawIronDemandTest {

    private static final int BACKPACK_SIZE = 8;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ScavengerConfig ironCapableConfig() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.craftTools = true;
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.IRON;
        return cfg;
    }

    /** A stone pick with the iron tier reachable is exactly the "upgrade wanted" frontier. */
    private static SimpleContainer stonePickPack() {
        SimpleContainer pack = new SimpleContainer(BACKPACK_SIZE);
        pack.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        return pack;
    }

    @Test
    void mustHappen_ironWantedAndNoneCarriedProducesADeficit() {
        int deficit = WorkDemandPolicy.rawIronDeficit(
                stonePickPack(), ItemStack.EMPTY, ironCapableConfig());
        assertTrue(deficit > 0, "a wanted iron pick with no ingots and no raw iron must pull mining");
    }

    @Test
    void mustHappen_rawIronAlreadyCarriedCountsAgainstTheDeficit() {
        SimpleContainer pack = stonePickPack();
        ScavengerConfig cfg = ironCapableConfig();
        int empty = WorkDemandPolicy.rawIronDeficit(pack, ItemStack.EMPTY, cfg);

        pack.setItem(1, new ItemStack(Items.RAW_IRON, 1));
        int withOne = WorkDemandPolicy.rawIronDeficit(pack, ItemStack.EMPTY, cfg);
        assertEquals(empty - 1, withOne, "carried raw iron must reduce what still needs mining");
    }

    @Test
    void mustNotHappen_miningContinuesOnceEnoughRawIronIsCarried() {
        SimpleContainer pack = stonePickPack();
        ScavengerConfig cfg = ironCapableConfig();
        pack.setItem(1, new ItemStack(Items.RAW_IRON, 64));
        assertEquals(0, WorkDemandPolicy.rawIronDeficit(pack, ItemStack.EMPTY, cfg),
                "a mob already holding enough raw iron must stop wanting ore");
    }

    @Test
    void mustNotHappen_oreWantedWhileTheIronTierIsUnreachable() {
        ScavengerConfig stoneCapped = ironCapableConfig();
        stoneCapped.maxPickTier = ToolTier.STONE;
        stoneCapped.maxAxeTier = ToolTier.STONE;
        assertEquals(0, WorkDemandPolicy.rawIronDeficit(
                        stonePickPack(), ItemStack.EMPTY, stoneCapped),
                "with iron unreachable there is no consumer, so ore stays in the ground");
    }

    @Test
    void mustNotHappen_oreWantedWithToolCraftingDisabled() {
        ScavengerConfig off = ironCapableConfig();
        off.craftTools = false;
        assertEquals(0, WorkDemandPolicy.rawIronDeficit(stonePickPack(), ItemStack.EMPTY, off),
                "craftTools=false removes the consumer entirely");
    }

    @Test
    void mustNotHappen_demandLatchesAfterTheConsumerIsSatisfied() {
        // De-latch (D-FSM-010): looting an iron pick removes the consumer, so the pull must stop
        // on the very next evaluation rather than persisting from the earlier decision.
        SimpleContainer pack = stonePickPack();
        ScavengerConfig cfg = ironCapableConfig();
        assertTrue(WorkDemandPolicy.rawIronDeficit(pack, ItemStack.EMPTY, cfg) > 0);

        pack.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        assertEquals(0, WorkDemandPolicy.rawIronDeficit(pack, ItemStack.EMPTY, cfg),
                "a looted iron pick must end iron ore demand immediately");
    }
}
