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
        // AFTER bootstrap. A static initializer here loaded Te3ProbeCommand first, which pulled
        // Minecraft classes in ahead of Bootstrap and left ItemStack permanently un-initializable
        // for every test in the JVM - four unrelated suites went red.
        Te3ProbeCommand.explorationSettingForTesting(new Te3ProbeCommand.ExplorationSetting() {
            @Override
            public boolean get() {
                return EXPLORING[0];
            }

            @Override
            public void set(boolean value) {
                EXPLORING[0] = value;
            }
        });
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
        // Compact since 7B: run #1 printed a full board dump and a Requote toString on every line,
        // which is unreadable in Minecraft chat - a readout that exists but cannot be read.
        assertTrue(TradeRuntimeObserver.events().get(0).startsWith("PLAN #1 TE"),
                "which source produced the candidate is the headline fact and cannot be inferred "
                        + "from outside the goal: " + TradeRuntimeObserver.events().get(0));
        assertTrue(TradeRuntimeObserver.events().get(0).contains("22 oak_log -> 1 emerald"),
                "and the quote itself, short enough to read in chat");
        assertEquals(1, TradeRuntimeObserver.tradeEverythingSelections(),
                "the TE plan counter is what arms the step-7B mutation, so it must be exact");
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

    // ------------------------------------------------------------------ step 7B

    /** Chat-readable: one short line per event, or the readout exists but cannot be read. */
    @Test
    void mustHappen_everyEventFitsOnOneReadableLine() {
        TradeRuntimeObserver.setRecording(true);

        TradeRuntimeObserver.selected("TRADE_EVERYTHING", null, "ignored",
                new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1));
        TradeRuntimeObserver.revalidated("TRADE_EVERYTHING", null, "ignored", Optional.empty());
        TradeRuntimeObserver.note("MUTATION APPLIED  oak_log value -> 2 sixteenths");
        TradeRuntimeObserver.episode();

        for (String line : TradeRuntimeObserver.events()) {
            assertTrue(line.length() <= 90, "too wide for chat (" + line.length() + "): " + line);
            assertFalse(line.contains((char) 10 + ""), "one event, one line: " + line);
        }
        assertTrue(TradeRuntimeObserver.events().get(1).contains("REJECTED"),
                "a refused revalidation must say so in the word a reader is scanning for");
    }

    /**
     * Width alone cannot police this.
     *
     * <p>In a world-free test the villager is {@code null}, so a re-added board dump measures one
     * character and the length assertion above stays green — a negative control that put the dump
     * back broke nothing. What actually made run #1 unreadable was a <b>live</b> board printed on
     * every line, so the per-event hooks are checked for it structurally instead.
     */
    @Test
    void mustNotHappen_perEventLinesEmbedAFullBoardDump() throws IOException {
        String observer = Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/debug/TradeRuntimeObserver.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
        String hooks = observer.substring(observer.indexOf("public static void selected("),
                observer.indexOf("public static void note("));

        assertFalse(hooks.contains("board(villager)"),
                "a live board on every event is what made the run-#1 readout unusable; the "
                        + "board! size flag carries the same claim in six characters");
    }

    /**
     * The counter the step-7B mutation is armed on.
     *
     * <p>It fires from the server tick rather than from a hook, so the observer stays inert — the
     * whole step-7A claim rests on that path being unable to act.
     */
    @Test
    void mustHappen_theTradeEverythingPlanCounterIsExact() {
        TradeRuntimeObserver.setRecording(true);

        TradeRuntimeObserver.selected("VANILLA", null, "r", new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.IRON_INGOT));
        assertEquals(0, TradeRuntimeObserver.tradeEverythingSelections(),
                "a vanilla plan must not arm a Trade Everything mutation");

        TradeRuntimeObserver.selected("TRADE_EVERYTHING", null, "r",
                new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1));
        assertEquals(1, TradeRuntimeObserver.tradeEverythingSelections());
    }

    /** The 7B fixture mutates the market and nothing else. */
    @Test
    void mustNotHappen_theMutationFixtureTouchesScavenger() throws IOException {
        String scenario = fixture("scenario/step7b_mutation.mcfunction");

        assertTrue(scenario.contains("mutate arm"), "the mutation is armed, not hand-timed");
        for (String forbidden : java.util.List.of(
                "emerald", "iron_pickaxe", "te3 p02", "te3 scan", "tp @e", "data modify")) {
            assertFalse(scenario.toLowerCase(java.util.Locale.ROOT).contains(forbidden),
                    "the fixture must not " + forbidden);
        }
    }

    // ------------------------------------------------------- 7B-R0 mutation lifecycle

    /**
     * Arming is a fixture state change, not a market one — the market only moves when the mob has
     * actually planned a Trade Everything route.
     */
    @Test
    void mustHappen_armingSetsUpTheMutationWithoutFiringIt() {
        Te3ProbeCommand.disarmMutation();
        Te3ProbeCommand.armMutation();

        assertTrue(Te3ProbeCommand.mutationArmed());
        assertFalse(Te3ProbeCommand.mutationApplied(), "nothing has been mutated yet");
    }

    @Test
    void mustHappen_resetDisarmsAndClearsTheAppliedMarker() {
        Te3ProbeCommand.armMutation();
        // Drive it to APPLIED first. Without this the assertion below passes trivially, because
        // arming already clears the marker - a negative control that deleted the clear broke
        // nothing at all.
        Te3ProbeCommand.markMutationApplied();
        assertTrue(Te3ProbeCommand.mutationApplied(), "the mutation has fired");

        Te3ProbeCommand.disarmMutation();

        assertFalse(Te3ProbeCommand.mutationArmed(), "a stale arm would fire into the next scenario");
        assertFalse(Te3ProbeCommand.mutationApplied(),
                "and a stale applied marker would stop the next one firing at all");
    }

    /** Two scenarios in one session: the second must begin from an unarmed fixture. */
    @Test
    void mustHappen_asecondFixtureBeginsUnarmed() {
        Te3ProbeCommand.armMutation();
        Te3ProbeCommand.disarmMutation();
        Te3ProbeCommand.armMutation();

        assertTrue(Te3ProbeCommand.mutationArmed());
        assertFalse(Te3ProbeCommand.mutationApplied(),
                "re-arming after a reset starts a clean cycle, not a resumed one");
    }

    /**
     * The half that cannot be executed here.
     *
     * <p>Trade Everything is {@code modCompileOnly}, so the JUnit runtime has no
     * {@code TradeEverythingApi} to call and the removal itself is <b>{@code UNVERIFIED}</b> until a
     * runtime session. What is checkable is the value passed, and that is the part with a plausible
     * wrong answer: {@code setItemOverride(oak_log, 1)} restores a log's ordinary value and looks
     * completely correct, while leaving a runtime override permanently installed that shadows the
     * user's config for the rest of the session. Upstream removes only on {@code <= 0}.
     */
    @Test
    void mustNotHappen_resetPinsAnOverrideInsteadOfRemovingIt() throws IOException {
        String probe = Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/debug/Te3ProbeCommand.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        assertTrue(probe.contains("CLEAR_OVERRIDE = 0"),
                "upstream removes the override only when sixteenths <= 0");
        assertTrue(probe.contains("CLEAR_OVERRIDE);"),
                "and the clear path must pass it rather than a plausible-looking 1");
        assertFalse(probe.contains("MUTATED_ITEM), 1)"),
                "restoring oak_log to 1 would shadow config/derived valuation for the session");
        // Scoped to the reset block. Checking the whole file only proved the call exists
        // SOMEWHERE - `watch off` also clears - so removing it from reset broke nothing.
        String resetBlock = probe.substring(probe.indexOf("Commands.literal(\"reset\")"),
                probe.indexOf("probe state reset"));
        assertTrue(resetBlock.contains("disarmMutation();"), "reset must disarm");
        assertTrue(resetBlock.contains("clearMarketMutation();"),
                "and must take the override back off - disarming alone leaves the next scenario "
                        + "starting from a mutated economy with nothing to indicate it");
    }

    // ------------------------------------------- 7B-R1 exploration hold (fixture only)

    /** Stands in for the config, which lazily loads from disk and is absent in a unit run. */
    private static final boolean[] EXPLORING = {true};

    private static void restoreExploringTo(boolean value) {
        Te3ProbeCommand.releaseExploration();
        EXPLORING[0] = value;
    }

    @Test
    void mustHappen_armingHoldsExplorationUntilTheFirstTradeEverythingPlan() {
        restoreExploringTo(true);
        TradeRuntimeObserver.setRecording(true);
        TradeRuntimeObserver.reset();

        Te3ProbeCommand.armMutation();

        assertTrue(Te3ProbeCommand.explorationHeld(),
                "with no TE plan yet the subject must stay in its own market");
        assertFalse(EXPLORING[0],
                "ordinary discretionary exploration is the only thing held");

        restoreExploringTo(true);
    }

    /** From PLAN #1 the mob is back under ordinary production behaviour, immediately. */
    @Test
    void mustHappen_theFirstTradeEverythingPlanRestoresExploration() {
        restoreExploringTo(true);
        TradeRuntimeObserver.setRecording(true);
        TradeRuntimeObserver.reset();
        Te3ProbeCommand.armMutation();

        TradeRuntimeObserver.selected("TRADE_EVERYTHING", null, "r",
                new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1));
        Te3ProbeCommand.tickFixtureForTesting();

        assertFalse(Te3ProbeCommand.explorationHeld());
        assertTrue(EXPLORING[0],
                "the walk, mutation, rejection and replan all run unheld");

        restoreExploringTo(true);
    }

    /** A vanilla plan is not the condition under test, so the hold stays. */
    @Test
    void mustNotHappen_aVanillaPlanReleasesTheHold() {
        restoreExploringTo(true);
        TradeRuntimeObserver.setRecording(true);
        TradeRuntimeObserver.reset();
        Te3ProbeCommand.armMutation();

        TradeRuntimeObserver.selected("VANILLA", null, "r", new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.IRON_INGOT));
        Te3ProbeCommand.tickFixtureForTesting();

        assertTrue(Te3ProbeCommand.explorationHeld());

        restoreExploringTo(true);
    }

    @Test
    void mustHappen_resetRestoresExplorationBeforeAnyPlan() {
        restoreExploringTo(true);
        Te3ProbeCommand.armMutation();

        Te3ProbeCommand.disarmMutation();

        assertFalse(Te3ProbeCommand.explorationHeld());
        assertTrue(EXPLORING[0], "an aborted run must not leave the mob homebound");
    }

    @Test
    void mustHappen_resetRestoresExplorationAfterAPlan() {
        restoreExploringTo(true);
        TradeRuntimeObserver.setRecording(true);
        TradeRuntimeObserver.reset();
        Te3ProbeCommand.armMutation();
        TradeRuntimeObserver.selected("TRADE_EVERYTHING", null, "r",
                new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1));
        Te3ProbeCommand.tickFixtureForTesting();

        Te3ProbeCommand.disarmMutation();

        assertFalse(Te3ProbeCommand.explorationHeld());
        assertTrue(EXPLORING[0]);
    }

    /**
     * The setting is <b>saved</b>, not assumed.
     *
     * <p>A fixture that restored a hardcoded {@code true} would silently switch exploration on for a
     * user who had deliberately turned it off — changing the game beyond its own scenario, the same
     * failure shape as pinning a value override instead of removing it.
     */
    @Test
    void mustNotHappen_aUserWhoDisabledExplorationHasItTurnedBackOn() {
        restoreExploringTo(false);

        Te3ProbeCommand.armMutation();
        assertFalse(EXPLORING[0], "already off; the hold changes nothing");

        Te3ProbeCommand.disarmMutation();
        assertFalse(EXPLORING[0],
                "and cleanup restores the value that was there, not a convenient default");

        restoreExploringTo(true);
    }

    /** The hold is in memory only; a debug fixture must never rewrite the user's config file. */
    @Test
    void mustNotHappen_theHoldIsPersistedToDisk() throws IOException {
        String probe = Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/debug/Te3ProbeCommand.java"))
                .replaceAll("(?m)//.*$", "");

        assertFalse(probe.contains(".save(") || probe.contains("writeConfig"),
                "the temporary setting must not outlive the session, let alone the fixture");
    }

    // --------------------------------------------- 7B-R1b arming must follow ENTITY_LOAD

    /**
     * Ordering is load-bearing, and getting it wrong is invisible.
     *
     * <p>{@code SpmScavengerInstallPolicy} reads {@code cfg.exploring} at ENTITY_LOAD to decide
     * {@code replacesHostStroll} and {@code installsOverlandExploration}. A later config change does
     * not re-wire an already-loaded entity, so arming before the summon permanently gives the
     * fixture mob a different goal stack — no {@code ExploringGoal}, no
     * {@code TrackedLocalWanderGoal}, host stroll retained — and restoring the flag afterwards
     * cannot put those goals back.
     *
     * <p>The mob would then behave plausibly and wrongly for the whole run, with nothing in the
     * readout to say why.
     */
    @Test
    void mustHappen_theMobIsSummonedBeforeTheMutationIsArmed() throws IOException {
        String scenario = fixture("scenario/step7b_mutation.mcfunction");

        int summon = scenario.indexOf("summon playermob:player_mob");
        int seed = scenario.indexOf("seed autonomous");
        int arm = scenario.indexOf("mutate arm");

        assertTrue(summon > 0 && seed > 0 && arm > 0, "all three setup steps must be present");
        assertTrue(summon < arm,
                "arming before ENTITY_LOAD strips goals that restoring the flag cannot reinstate");
        assertTrue(seed < arm, "and the mob must be fully seeded before the hold begins");
    }

    /** Arming is the last setup command, so nothing runs between it and the mob's first AI tick. */
    @Test
    void mustHappen_armingIsTheFinalSetupCommand() throws IOException {
        String scenario = fixture("scenario/step7b_mutation.mcfunction");
        // From the END of the arm line, so the arm command itself is not re-examined.
        String afterArm = scenario.substring(
                scenario.indexOf((char) 10, scenario.indexOf("mutate arm")) + 1);

        for (String line : afterArm.split(String.valueOf((char) 10))) {
            String trimmed = line.trim();
            assertTrue(trimmed.isEmpty() || trimmed.startsWith("say "),
                    "only chat output may follow the arm: " + trimmed);
        }
    }

    /**
     * What the hold actually suppresses, pinned so the claim cannot drift.
     *
     * <p>R1 documentation said "only ordinary discretionary exploration". That was wrong —
     * {@code exploring} also gates {@code MiningDirector}'s gather path, so the hold does bias route
     * choice while in effect. The partition below is the honest statement, and it is asserted rather
     * than described because a comment cannot go red.
     */
    @Test
    void mustHappen_theHoldLeavesCraftSmeltTradeAndExhaustionUntouched() throws IOException {
        for (String untouched : java.util.List.of(
                "goal/CraftTorchesGoal.java",
                "goal/SmeltAtFurnaceGoal.java",
                "goal/TradeWithVillagerGoal.java",
                "village/trade/RouteExhaustionEvidence.java",
                "village/trade/TradeFundingPlanner.java")) {
            String body = Files.readString(
                            Path.of("src/main/java/com/noobk/spmscavenger").resolve(untouched))
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("(?m)//.*$", "");
            assertFalse(body.contains(".exploring"),
                    untouched + " must not read cfg.exploring, or the hold would change the very "
                            + "behaviour step 7B is measuring");
        }
    }

    /** And the surfaces it does suppress, named — so "only exploration" cannot be claimed again. */
    @Test
    void mustHappen_theSuppressedSurfacesAreAcknowledged() throws IOException {
        String mining = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mining/MiningDirector.java"));
        assertTrue(mining.contains("cfg.exploring"),
                "MiningDirector's gather path IS gated by exploring - the hold suppresses it, and "
                        + "the fixture documentation must keep saying so");

        String probe = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/Te3ProbeCommand.java"));
        assertTrue(probe.contains("MiningDirector gather path"),
                "the honesty note must survive future edits to the hold");
    }

    // ------------------------------------- 003c witness: deterministic gather terrain

    /**
     * The witness needs a world, not just a market.
     *
     * <p>{@code GATHER YIELDING} only fires when gather <b>selects an unrelated target</b> while a
     * handoff is published. Step 7A leaned on ambient terrain for that, and the previous failed run
     * had already chopped the one nearby tree — {@code cleanup} kills entities and restores no
     * blocks. The second run therefore had nothing to select, the event could not occur, and the run
     * looked like a policy failure rather than a missing precondition.
     */
    @Test
    void mustHappen_bothScenariosArmDeterministicGatherTerrain() throws IOException {
        for (String name : java.util.List.of("scenario/step7a_autonomous.mcfunction",
                "scenario/step7b_mutation.mcfunction")) {
            String scenario = fixture(name);
            int summon = scenario.indexOf("summon playermob:player_mob");
            int terrain = scenario.indexOf("te3 terrain");

            assertTrue(terrain > 0, name + " must place the unrelated wealth target itself");
            assertTrue(summon < terrain,
                    name + ": terrain is placed relative to the mob, so the mob must exist first");
        }
    }

    /** And arming still comes last in 7B, after the terrain step. */
    @Test
    void mustHappen_terrainIsArmedBeforeTheMarketMutation() throws IOException {
        String scenario = fixture("scenario/step7b_mutation.mcfunction");

        assertTrue(scenario.indexOf("te3 terrain") < scenario.indexOf("mutate arm"),
                "the hold begins last, once the world is fully built");
    }

    /** Blocks are fixture state: a run that consumes them must not change the next run's world. */
    @Test
    void mustHappen_cleanupRestoresFixtureBlocks() throws IOException {
        String cleanup = fixture("cleanup.mcfunction");

        assertTrue(cleanup.contains("te3 terrain restore"),
                "entities were always cleaned; the witness log was not, which is how the 003c "
                        + "witness silently went missing between runs");
        assertTrue(cleanup.indexOf("te3 terrain restore") < cleanup.indexOf("kill @e[tag=te3]"),
                "restore before the entity sweep, while the world is still addressable");
    }

    /**
     * The iron check must sweep the same volume the gather scan does.
     *
     * <p>A narrower box would pass while the real sweep still found ore — the precondition would be
     * verified against a volume nobody searches, and the run would produce a false negative for the
     * handoff.
     */
    @Test
    void mustHappen_theIronPreconditionUsesTheGatherSweepVolume() throws IOException {
        String probe = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/Te3ProbeCommand.java"));
        String gather = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java"));

        assertTrue(gather.contains("for (int dy = -4; dy <= 4; dy++)"),
                "the gather sweep's vertical band, read from production");
        assertTrue(probe.contains("for (int dy = -4; dy <= 4"),
                "the precondition check must use the same band, or it verifies a volume nobody "
                        + "searches");
        assertTrue(probe.contains("GatherProtection.isExposedToAir"),
                "and the same exposure rule - buried ore is not a candidate for either");
    }

    /** Refusing is the point: a run with iron in radius proves nothing and must not start. */
    @Test
    void mustHappen_thePreconditionRefusesRatherThanWarns() throws IOException {
        String probe = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/Te3ProbeCommand.java"));
        String terrainBody = probe.substring(probe.indexOf("private static int terrain("),
                probe.indexOf("private static void place("));

        // The IRON refusal specifically. A bare "contains sendFailure" matched the unrelated
        // sits-on-a-log guard, so deleting the iron check entirely broke nothing - a control that
        // failed to fail.
        int refusal = terrainBody.indexOf("air-exposed iron ore inside the gather radius");
        int placement = terrainBody.indexOf("place(level, logPos");

        assertTrue(refusal > 0,
                "with iron in radius the mandatory route is servable, no handoff is published, and "
                        + "the witness cannot occur - that is a refusal, not a warning");
        assertTrue(placement > 0 && refusal < placement,
                "and it refuses BEFORE building anything, so a rejected run leaves no litter");
    }
}
