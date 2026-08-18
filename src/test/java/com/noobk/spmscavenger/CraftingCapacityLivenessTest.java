package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * <b>Runtime-reproduced liveness defect</b> — step 7A, run #1.
 *
 * <h2>What happened</h2>
 *
 * <pre>
 * seeded    6x64 oak_log | 16 torch | (free)          8 slots, one free
 * tick 1    LOGS_TO_PLANKS commits: 383 logs, 4 planks in the last slot   -> backpack FULL
 * tick 2..N PLANKS_TO_STICKS selected. Four sticks need a NEW slot; there is none.
 *           apply() is atomic, so it consumes nothing and returns false.
 *           nextStep() selects the same step again. And again.
 * </pre>
 *
 * The mob stood at "Crafting Sticks" indefinitely and never reached trade discovery. Observed:
 * {@code selections=0 transactions=0 episodes=0 logs=383}.
 *
 * <h2>Why nothing caught it</h2>
 *
 * There was no exception, no log line, and — because {@code apply} is atomic — no lost items to
 * notice. Every unit test asked "does this craft produce the right result", which it does whenever
 * it runs at all. Nothing asked "can this craft run", and a full backpack is the one state where the
 * answer is no. It took an autonomous mob with nowhere to put four sticks to surface it.
 */
class CraftingCapacityLivenessTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The exact run-#1 state, one tick after the mob's first craft. */
    private static SimpleContainer fullAfterFirstCraft() {
        SimpleContainer backpack = new SimpleContainer(8);
        for (int slot = 0; slot < 6; slot++) {
            backpack.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        }
        backpack.setItem(6, new ItemStack(Items.TORCH, 16));
        backpack.setItem(7, new ItemStack(Items.OAK_PLANKS, 4));
        return backpack;
    }

    @Test
    void mustNotHappen_aCraftIsSelectedThatCannotDeliverItsResult() {
        SimpleContainer backpack = fullAfterFirstCraft();

        assertFalse(ScavengerCrafting.canCommit(backpack, ScavengerCrafting.Step.PLANKS_TO_STICKS),
                "four sticks need a slot the full backpack does not have");
        assertNotEquals(ScavengerCrafting.Step.PLANKS_TO_STICKS,
                ScavengerCrafting.nextStep(backpack, new ScavengerConfig(),
                        new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_AXE)),
                "selecting it again every tick is the loop that stalled the mob");
        // NOTE: item tags are not populated by Bootstrap.bootStrap(), so a plank craft is
        // unexercisable here and this assertion would also hold with the repair reverted. The
        // capacity property is proved below on MAKE_TORCHES, whose ingredients are items.
    }

    /**
     * Room versus no room, on a craft this environment can actually exercise.
     *
     * <h2>A limitation worth stating</h2>
     *
     * Item <b>tags</b> are not populated by {@code Bootstrap.bootStrap()}, so every plank/log craft
     * reports "cannot commit" here regardless of space — which means the run-#1 assertion above,
     * while true, would also be true with the repair reverted. It is kept as documentation of the
     * observed state, not as proof.
     *
     * <p>The proof uses {@code MAKE_TORCHES}, whose ingredients are items rather than tags, so the
     * only thing deciding the outcome is capacity. This is what stops the preflight being a blanket
     * refusal.
     */
    @Test
    void mustHappen_thePreflightDiscriminatesOnCapacityAlone() {
        SimpleContainer roomy = new SimpleContainer(4);
        roomy.setItem(0, new ItemStack(Items.COAL, 8));
        roomy.setItem(1, new ItemStack(Items.STICK, 8));

        assertTrue(ScavengerCrafting.canCommit(roomy, ScavengerCrafting.Step.MAKE_TORCHES),
                "ingredients present and a free slot for the torches");

        SimpleContainer full = new SimpleContainer(2);
        full.setItem(0, new ItemStack(Items.COAL, 8));
        full.setItem(1, new ItemStack(Items.STICK, 8));

        assertFalse(ScavengerCrafting.canCommit(full, ScavengerCrafting.Step.MAKE_TORCHES),
                "same ingredients, nowhere to put four torches - capacity is the only difference");
    }

    /**
     * And the selector honours it: the craft the mob would otherwise repeat for ever is not chosen.
     */
    @Test
    void mustNotHappen_theSelectorReturnsACraftThatCannotCommit() {
        SimpleContainer full = new SimpleContainer(2);
        full.setItem(0, new ItemStack(Items.COAL, 8));
        full.setItem(1, new ItemStack(Items.STICK, 8));

        assertEquals(ScavengerCrafting.Step.NOTHING,
                ScavengerCrafting.nextStep(full, new ScavengerConfig(),
                        ItemStack.EMPTY, ItemStack.EMPTY),
                "MAKE_TORCHES is top priority and its ingredients are held, but it cannot deliver - "
                        + "returning it is precisely the loop that stalled the mob");

        SimpleContainer roomy = new SimpleContainer(4);
        roomy.setItem(0, new ItemStack(Items.COAL, 8));
        roomy.setItem(1, new ItemStack(Items.STICK, 8));

        assertEquals(ScavengerCrafting.Step.MAKE_TORCHES,
                ScavengerCrafting.nextStep(roomy, new ScavengerConfig(),
                        ItemStack.EMPTY, ItemStack.EMPTY),
                "with room, nothing about selection changed");
    }

    /** The preflight and the commit share {@code applyMutating}, so they cannot disagree. */
    @Test
    void mustHappen_thePreflightAgreesWithTheCommit() {
        for (ScavengerCrafting.Step step : new ScavengerCrafting.Step[] {
                ScavengerCrafting.Step.LOGS_TO_PLANKS, ScavengerCrafting.Step.PLANKS_TO_STICKS}) {
            SimpleContainer full = fullAfterFirstCraft();
            SimpleContainer copy = new SimpleContainer(8);
            for (int slot = 0; slot < full.getContainerSize(); slot++) {
                copy.setItem(slot, full.getItem(slot).copy());
            }
            assertEquals(ScavengerCrafting.canCommit(full, step),
                    ScavengerCrafting.apply(copy, step),
                    step + ": a preflight that could disagree with the commit would be a second "
                            + "capacity model");
        }
    }

    /**
     * The reshaped step-7A fixture: the mob's own crafting must not consume the slot the first
     * trade payout needs.
     */
    @Test
    void mustHappen_theStepSevenFixtureLeavesRoomAfterItsOwnCrafting() {
        SimpleContainer fixture = new SimpleContainer(8);
        for (int slot = 0; slot < 5; slot++) {
            fixture.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        }
        fixture.setItem(5, new ItemStack(Items.TORCH, 16));
        fixture.setItem(6, new ItemStack(Items.STICK, 3));

        assertEquals(ScavengerCrafting.Step.NOTHING,
                ScavengerCrafting.nextStep(fixture, new ScavengerConfig(),
                        new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_AXE)),
                "three sticks already satisfy the iron-pickaxe recipe's stick requirement, so no "
                        + "craft chain starts and the free slot survives for the TE payout "
                        + "(tag-confounded in this environment; the runtime fixture is the real "
                        + "check)");
        assertTrue(fixture.getItem(7).isEmpty(), "and that slot is genuinely still free");
    }
}
