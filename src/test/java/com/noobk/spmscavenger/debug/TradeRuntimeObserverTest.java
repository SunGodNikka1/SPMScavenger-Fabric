package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * <b>TEMPORARY STEP-7A SUPPORT.</b>
 *
 * <p>The step-7A claim is that the <i>mob</i> produces the route. That claim is only worth anything
 * if the observer watching it cannot participate, so what is pinned here is the observer's
 * inability to influence: every hook returns {@code void}, every hook is inert unless recording, and
 * the fixture calls none of the APIs that would do the mob's work for it.
 */
class TradeRuntimeObserverTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void stopRecording() {
        TradeRuntimeObserver.setRecording(false);
        TradeRuntimeObserver.reset();
    }

    /**
     * The structural guarantee. A hook that returned anything could be branched on, and the goal
     * would no longer be the only thing deciding.
     */
    @Test
    void mustHappen_everyHookReturnsVoid() {
        for (String hook : java.util.List.of("selected", "revalidated", "transacted", "episode")) {
            for (Method method : TradeRuntimeObserver.class.getDeclaredMethods()) {
                if (method.getName().equals(hook)) {
                    assertEquals(void.class, method.getReturnType(),
                            hook + " must give the goal nothing to branch on");
                }
            }
        }
    }

    @Test
    void mustNotHappen_theObserverRecordsWhenDisabled() {
        TradeRuntimeObserver.setRecording(false);
        TradeRuntimeObserver.reset();

        TradeRuntimeObserver.selected("VANILLA", null, "ref", new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.IRON_INGOT));
        TradeRuntimeObserver.revalidated("VANILLA", null, "ref", Optional.empty());
        TradeRuntimeObserver.transacted("VANILLA", null, "TRADED", null, 0, 0);
        TradeRuntimeObserver.episode();

        assertTrue(TradeRuntimeObserver.events().isEmpty(),
                "an ordinary session pays one volatile read per decision point and nothing else");
        assertFalse(TradeRuntimeObserver.recording());
    }

    @Test
    void mustHappen_recordingCapturesTheSourceThatProducedTheCandidate() {
        TradeRuntimeObserver.setRecording(true);

        TradeRuntimeObserver.selected("TRADE_EVERYTHING", null, "Requote[oak_log]",
                new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1));

        assertEquals(1, TradeRuntimeObserver.events().size());
        assertTrue(TradeRuntimeObserver.events().get(0).contains("TRADE_EVERYTHING"),
                "which source produced the candidate is the headline fact and cannot be inferred "
                        + "from outside the goal");
        assertTrue(TradeRuntimeObserver.events().get(0).contains("Requote[oak_log]"));
    }

    /** RET-1: a debug recorder that grows for a whole session is a shape this repo has shipped. */
    @Test
    void mustNotHappen_theEventLogGrowsWithoutBound() {
        TradeRuntimeObserver.setRecording(true);

        for (int i = 0; i < 5_000; i++) {
            TradeRuntimeObserver.episode();
        }

        assertTrue(TradeRuntimeObserver.events().size() <= 400,
                "bounded: " + TradeRuntimeObserver.events().size());
        assertTrue(TradeRuntimeObserver.summary().contains("dropped="),
                "and it says so rather than quietly losing the tail");
    }

    @Test
    void mustHappen_backpackCountingIsUsedForBeforeAndAfterEvidence() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.EMERALD, 3));
        backpack.setItem(1, new ItemStack(Items.EMERALD, 4));

        assertEquals(7, TradeRuntimeObserver.count(backpack, Items.EMERALD));
        assertEquals(0, TradeRuntimeObserver.count(backpack, Items.IRON_PICKAXE));
    }

    // ------------------------------------------------------------------ the fixture's limits

    /**
     * Commands only.
     *
     * <p>mcfunction comments explain what the fixture deliberately does <b>not</b> do — "no
     * emeralds", "no pickaxe" — so a scan that cannot tell a comment from a command fails on the
     * sentence describing the very restriction it is enforcing. That has now happened three times
     * in this workstream; strip first, scan second.
     */
    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("test-datapacks/te3-probe/data/te3/function").resolve(name))
                .lines()
                .filter(line -> !line.trim().startsWith("#"))
                .reduce("", (a, b) -> a + (char) 10 + b);
    }

    /**
     * The fixture may set the world up. It may not do the mob's job.
     *
     * <p>If any of these appeared, a green step-7A run would prove that the fixture can trade, which
     * nobody doubted.
     */
    @Test
    void mustNotHappen_theFixtureDoesTheMobsWork() throws IOException {
        String scenario = fixture("scenario/step7a_autonomous.mcfunction");

        for (String forbidden : java.util.List.of(
                "emerald", "iron_pickaxe", "te3 p02", "te3 scan", "tp @e", "data modify")) {
            assertFalse(scenario.toLowerCase(java.util.Locale.ROOT).contains(forbidden),
                    "the fixture must not " + forbidden + " - the mob has to earn it");
        }
        assertTrue(scenario.contains("seed autonomous"), "inventory comes from the seed command");
        assertTrue(scenario.contains("te3 fixture"), "and the market from bounded vanilla re-rolls");
    }

    /** The mob must be free to move; every earlier probe fixture froze it, and this one must not. */
    @Test
    void mustNotHappen_theAutonomousMobIsFrozen() throws IOException {
        String scenario = fixture("scenario/step7a_autonomous.mcfunction");

        assertTrue(scenario.contains("summon playermob:player_mob"));
        assertFalse(scenario.contains("NoAI:1b,Tags:[\"te3\",\"te3_mob\"]"),
                "a NoAI mob cannot walk to a merchant, and walking is the thing being tested");
    }

    /** The merchants are ordinary villagers; nothing authors their offers. */
    @Test
    void mustNotHappen_theFixtureAuthorsAMarket() throws IOException {
        String merchants = fixture("_merchants_autonomous.mcfunction");

        assertFalse(merchants.contains("Offers:"),
                "boards come from vanilla generation, narrowed by discarding draws - never written");
        assertTrue(merchants.contains("minecraft:armorer") && merchants.contains("minecraft:toolsmith"));
    }
}
