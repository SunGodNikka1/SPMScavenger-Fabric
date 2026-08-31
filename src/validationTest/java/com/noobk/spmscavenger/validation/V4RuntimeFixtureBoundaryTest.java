package com.noobk.spmscavenger.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4RuntimeFixtureBoundaryTest {

    @Test
    void v4FixtureLivesOnlyInValidationAndTrackerOwnsNoAuthorityCalls() throws Exception {
        String production = readTree(Path.of("src/main/java"));
        String validation = readTree(Path.of("src/validation/java"));
        assertFalse(production.contains("V4RuntimeCampaignController"));
        assertFalse(production.contains("V4RuntimeWitnessTracker"));
        assertTrue(validation.contains("class V4RuntimeCampaignController"));
        assertTrue(validation.contains("class V4RuntimeWitnessTracker"));

        String tracker = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4RuntimeWitnessTracker.java"));
        for (String forbidden : new String[] {
                "openRequiredTrade(", "openOrResumeRequiredTrade(", "SettlementDestinationRanker.select(",
                "moveTo(", "setTarget(", "performResolvedTrade(", "startSleeping(",
                "designateHome(", "RouteExhaustionEvidence.publish("}) {
            assertFalse(tracker.contains(forbidden), "passive tracker authority leak: " + forbidden);
        }
    }

    @Test
    void commandAndResourceContractIsSingleSessionAndSequential() throws Exception {
        String commands = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4RuntimeWitnessCommands.java"));
        for (String literal : new String[] {"v4", "run", "status", "report", "stop", "reset"}) {
            assertTrue(commands.contains("literal(\"" + literal + "\")"));
        }
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4RuntimeCampaignController.java"));
        assertTrue(controller.indexOf("PHASE_A") < controller.indexOf("PHASE_B"));
        assertTrue(controller.contains("homeBeforeTrade"));
        assertTrue(controller.contains("FIXTURE_FAILURE"));
        assertTrue(controller.contains("INCOMPLETE"));
        assertTrue(controller.contains("FAIL"));
        assertTrue(controller.contains("PASS"));
        for (String forbidden : new String[] {
                "VillageInteractionDirector.openOrResumeRequiredTrade(",
                "SettlementDestinationRanker.select(", "performResolvedTrade(",
                "FirstHomePromotion.afterSuccessfulSleep(", "designateHome(", "startSleeping("}) {
            assertFalse(controller.contains(forbidden), "fixture authority leak: " + forbidden);
        }
        assertTrue(count(controller, "subject.teleportTo(") == 1,
                "only the declared pre-window departure placement may move the subject");
        assertTrue(controller.indexOf("subject.teleportTo(")
                < controller.indexOf("V4RuntimeWitnessTracker.openPhaseA(now)"));

        Path pack = Path.of("test-datapacks/v4-settlement-integration");
        assertTrue(Files.exists(pack.resolve("pack.mcmeta")));
        assertTrue(Files.exists(pack.resolve("README.md")));
        assertTrue(Files.exists(pack.resolve(
                "data/spm_v4/function/scenario/v4_g.mcfunction")));
        assertTrue(Files.exists(pack.resolve("data/spm_v4/function/cleanup.mcfunction")));
    }

    @Test
    void requiredFixtureEntitiesCrossCheckedAttachmentGateBeforeBootstrap() throws Exception {
        Path scenarioPath = Path.of("test-datapacks/v4-settlement-integration/data/spm_v4/"
                + "function/scenario/v4_g.mcfunction");
        String scenario = Files.readString(scenarioPath);
        assertFalse(scenario.contains("summon playermob:player_mob"),
                "an unchecked datapack summon must not stand in for subject creation");
        assertFalse(scenario.contains("summon minecraft:villager"),
                "required trader/helper entities must use the same checked creation boundary");
        assertFalse(scenario.contains("fill "),
                "the documentation-only function must not remain a competing geometry owner");
        assertFalse(scenario.contains("setblock "),
                "mandatory geometry must be owned by the checked validation Java boundary");

        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        int checkedCleanup = controller.indexOf("V4FixtureCleanup.prepareForStartup(");
        int checkedGeometry = controller.indexOf(
                "V4FixtureGeometryBuilder.createAndVerifyStructure(");
        int lightingState = controller.indexOf(
                "preparing.state = State.WAITING_FIXTURE_LIGHTING");
        int checkedCreation = controller.indexOf("V4FixtureEntityFactory.createAndVerify(");
        int stability = controller.indexOf("session.state = State.WAITING_STARTUP_STABILITY");
        int stabilityMethod = controller.indexOf("private static void tickStartupStability(");
        int bootstrap = controller.indexOf(
                "session.state = State.WAITING_SETTLEMENT_AND_INITIAL_BOARD", stabilityMethod);
        assertTrue(checkedCleanup >= 0 && checkedGeometry > checkedCleanup
                        && lightingState > checkedGeometry && checkedCreation > lightingState
                        && stability > checkedCreation && bootstrap > stabilityMethod,
                "cleanup/structure must enter threaded-light wait before entity attachment and stability");
        int runStart = controller.indexOf("public static synchronized int run(");
        int statusStart = controller.indexOf("public static synchronized int status(");
        String runMethod = controller.substring(runStart, statusStart);
        assertFalse(runMethod
                        .contains("State.WAITING_SETTLEMENT_AND_INITIAL_BOARD"),
                "run() must not open bootstrap from instantaneous attachment");
        assertFalse(runMethod.contains("\"scenario/v4_g\""),
                "run() must not infer geometry success from an unchecked mcfunction");
        assertTrue(runMethod.contains("fixtureGeometryDiagnostics.readyForLightingWait()"),
                "lighting wait must remain behind the explicit structure gate");
        assertFalse(runMethod.contains("V4FixtureEntityFactory.createAndVerify("),
                "run() must not create fixture entities before threaded lighting PASS");
        assertTrue(runMethod.contains("startupCleanupDiagnostics.ready()"),
                "geometry creation must remain behind the synchronous cleanup gate");
        assertFalse(controller.contains("fixture PlayerMob not found"));
        assertFalse(controller.contains("findTagged(level, origin"));

        int createStart = controller.indexOf("private static void createFixtureEntitiesAndArm(");
        int stabilityMethodStart = controller.indexOf("private static void tickStartupStability(");
        String createMethod = controller.substring(createStart, stabilityMethodStart);
        int initialOffer = createMethod.indexOf("configureOffer(trader, INITIAL_PRICE)");
        int earlyArm = createMethod.indexOf("V4RuntimeWitnessTracker.arm(");
        int stabilityState = createMethod.indexOf("State.WAITING_STARTUP_STABILITY");
        assertTrue(initialOffer >= 0 && earlyArm > initialOffer && stabilityState > earlyArm,
                "passive witness must arm after fixture inventory/offer setup and before first-tick stability");
        String stabilityBody = controller.substring(stabilityMethod,
                controller.indexOf("private static void tickBootstrap(", stabilityMethod));
        assertFalse(stabilityBody.contains("V4RuntimeWitnessTracker.arm("),
                "startup stability must not reset first-tick witness evidence");

        String factory = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4FixtureEntityFactory.java"));
        for (String required : new String[] {
                "playermob", "player_mob", "BuiltInRegistries.ENTITY_TYPE.getOptional",
                "PlayerMobSummon", "getMethod(\"summon\"", "summon.invoke(null",
                "MobSpawnType.COMMAND", "finalizeSpawn(",
                "addFreshEntity(", "level.getEntity(", "expectedTagsPresent",
                "PlayerMobs.isPlayerMob", "validSpawnGeometry", "traderCreated",
                "helperCreated"}) {
            assertTrue(factory.contains(required), "missing fixture creation proof: " + required);
        }
        assertFalse(factory.contains("playerMobType.get().create(level)"),
                "validation must invoke SPM's canonical public summon helper, not imitate it");
        assertFalse(factory.contains("spawnMob(level, subject"),
                "the generic validation Mob finalizer is for villagers, not the PlayerMob");
        for (String forbidden : new String[] {
                "VillageIntent", "SettlementDestinationRanker", "performResolvedTrade(",
                "startSleeping(", "designateHome(", "moveTo(subject"}) {
            assertFalse(factory.contains(forbidden),
                    "fixture entity creation acquired production authority: " + forbidden);
        }
    }

    @Test
    void phaseAStagingUsesProductionWarmupAndOpensSecondDemandOnlyAfterDeparture()
            throws Exception {
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        String geometry = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4FixtureGeometryBuilder.java"));
        assertTrue(geometry.contains("level.setDayTime(dayBase + 1_000L)"));
        assertFalse(geometry.contains("dayBase + 18_000L"));
        assertFalse(controller.contains("DAY_ADVANCED"),
                "daytime must be a fixture precondition, not a delayed bootstrap workaround");

        int runStart = controller.indexOf("public static synchronized int run(");
        int statusStart = controller.indexOf("public static synchronized int status(");
        String run = controller.substring(runStart, statusStart);
        int fixtureStart = controller.indexOf("private static void createFixtureEntitiesAndArm(");
        int stabilityStart = controller.indexOf("private static void tickStartupStability(");
        String fixtureCreation = controller.substring(fixtureStart, stabilityStart);
        assertTrue(fixtureCreation.contains(
                        "prepareSettlementMemoryInventory(subject, backpack)"),
                "entity creation must begin with a demand-neutral perception inventory");
        int memoryStart = controller.indexOf("private static void tickSettlementMemory(");
        int bootstrapStart = controller.indexOf("private static void tickBootstrap(");
        String memoryWait = controller.substring(memoryStart, bootstrapStart);
        assertTrue(memoryWait.contains(
                        "prepareSubjectInventory(subject, backpack, INITIAL_PRICE)"),
                "warmup funding may appear only after settlement-memory readiness");
        assertFalse(fixtureCreation.contains("performResolvedTrade("),
                "the validation controller must not execute its warmup transaction");

        int phaseAStart = controller.indexOf("private static void openPhaseA(");
        String bootstrap = controller.substring(bootstrapStart, phaseAStart);
        for (String evidence : new String[] {
                "initialWarmupOfferExecuted()", "countAll(subject, backpack, Items.IRON_PICKAXE)",
                "bootstrapWarmupDemandResolved", "bootstrapCapabilityPersisted",
                "BOOTSTRAP_LOCAL_INTENT_RELEASED_OR_CLOSED",
                "bootstrap-local REQUIRED_TRADE remained after warm-up demand resolved"}) {
            assertTrue(bootstrap.contains(evidence), "missing warmup boundary: " + evidence);
        }
        assertFalse(bootstrap.contains(
                        "bootstrap staging allowed REQUIRED_TRADE before departure"),
                "a legal UNKNOWN-backed local intent must not be fixture failure");
        assertFalse(controller.contains("VillageIntentRegistry.release("));
        assertFalse(controller.contains("VillageIntentRegistry.releaseIfCurrent("),
                "bootstrap intent closure must remain production-owned");

        int phaseAEnd = controller.indexOf("private static void tickPhaseA(", phaseAStart);
        String open = controller.substring(phaseAStart, phaseAEnd);
        int noDemandBefore = open.indexOf("second blocking demand existed before departure");
        int teleport = open.indexOf("subject.teleportTo(");
        int departureCheck = open.indexOf("session.departureConfirmed");
        int changeOffer = open.indexOf("configureOffer(trader, CHANGED_PRICE)");
        int markChanged = open.indexOf("V4RuntimeWitnessTracker.markChangedOffer(");
        int openWitness = open.indexOf("V4RuntimeWitnessTracker.openPhaseA(now)");
        int replaceInventory = open.indexOf(
                "prepareSubjectInventory(subject, backpack, CHANGED_PRICE)");
        int verifySecondDemand = open.indexOf("session.phaseASecondDemandOpened");
        assertTrue(noDemandBefore >= 0 && teleport > noDemandBefore
                        && departureCheck > teleport && changeOffer > departureCheck
                        && markChanged > changeOffer && openWitness > markChanged
                        && replaceInventory > openWitness && verifySecondDemand > replaceInventory,
                "Phase A must depart while satisfied, change the live board, open evidence, then create demand");
        assertTrue(open.contains("departure did not leave settlement/trader locality"));
        assertTrue(open.contains("second blocking demand did not open after departure"));

        for (String reportLine : new String[] {
                "bootstrapInitialBoardObserved=", "bootstrapWarmupTradeExecuted=",
                "bootstrapWarmupDemandResolved=", "bootstrapCapabilityPersisted=",
                "bootstrapLocalRequiredTradeCount=", "bootstrapLocalCommuteSeedCount=",
                "bootstrapLocalArrivalCount=", "bootstrapLocalIntentReleased=",
                "departureConfirmed=", "phaseASecondDemandOpened=", "NOT_MEASURED"}) {
            assertTrue(controller.contains(reportLine), "missing report evidence: " + reportLine);
        }
    }

    @Test
    void cleanupIsDirectSynchronousNonDamagingAndPrecedesAllFixtureCreation() throws Exception {
        String cleanup = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4FixtureCleanup.java"));
        for (String required : new String[] {
                "getScheduledEvents()", "getEventsIds()", ".remove(legacyId)",
                "entity.discard()", "cleanupCompletedSynchronously",
                "staleFixtureEntitiesRemaining", "legacyCleanupSchedulePresentAfter",
                "cleanupOwner=VALIDATION_JAVA", "cleanupCommandFunctionInvoked=NO"}) {
            assertTrue(cleanup.contains(required), "missing cleanup proof: " + required);
        }
        for (String forbidden : new String[] {
                ".hurt(", ".damage(", "setHealth(", "heal(", "kill @e[",
                ".schedule(", "getCommands().perform", "getFunctions().execute"}) {
            assertFalse(cleanup.contains(forbidden),
                    "validation teardown must not use damage/delay/commands: " + forbidden);
        }

        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        int cleanupGate = controller.indexOf("V4FixtureCleanup.prepareForStartup(");
        int geometry = controller.indexOf(
                "V4FixtureGeometryBuilder.createAndVerifyStructure(");
        int environment = controller.indexOf(
                "V4FixtureEnvironment.prepareBeforeEntityCreation(");
        int entities = controller.indexOf("V4FixtureEntityFactory.createAndVerify(");
        assertTrue(cleanupGate >= 0 && geometry > cleanupGate
                && environment > geometry && entities > environment);
        assertFalse(controller.contains("executeFixtureFunction("));
        assertFalse(controller.contains("getFunctions().execute("));
        assertFalse(controller.contains("\"spm_v4:cleanup\""));
        assertTrue(count(controller, "discardOwnedFixture(") >= 3,
                "stop/reset/startup rollback must share exact-owned Java cleanup");

        String resource = Files.readString(Path.of(
                "test-datapacks/v4-settlement-integration/data/spm_v4/function/cleanup.mcfunction"));
        assertFalse(resource.contains("kill @e[tag=spm_v4.fixture]"));
        assertFalse(resource.lines().anyMatch(line -> {
            String command = line.stripLeading();
            return !command.isEmpty() && !command.startsWith("#");
        }), "legacy cleanup resource must be documentation-only");

        String production = readTree(Path.of("src/main/java"));
        assertFalse(production.contains("V4FixtureCleanup"));
        assertFalse(production.contains("spm_v4.fixture"));
    }

    @Test
    void cleanupGateRejectsRemainingTimerOrEntityState() {
        V4FixtureCleanup.Diagnostics diagnostics = new V4FixtureCleanup.Diagnostics();
        diagnostics.cleanupAttempted = true;
        diagnostics.cleanupCompletedSynchronously = true;
        diagnostics.cleanupCompletedTick = 42L;
        diagnostics.legacyCleanupScheduleCleared = true;

        diagnostics.legacyCleanupSchedulePresentAfter = true;
        assertFalse(diagnostics.ready(), "a remaining legacy timer must block fixture creation");
        diagnostics.legacyCleanupSchedulePresentAfter = false;
        diagnostics.staleFixtureEntitiesRemaining = 1;
        assertFalse(diagnostics.ready(), "a remaining stale fixture must block fixture creation");
        diagnostics.staleFixtureEntitiesRemaining = 0;
        assertTrue(diagnostics.ready(), "only synchronous empty cleanup may open the gate");
    }

    @Test
    void intentionalTeardownCannotBeClassifiedAsBehavioralSubjectDeath() throws Exception {
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        int unavailable = controller.indexOf("public static synchronized void onSubjectUnavailable(");
        int death = controller.indexOf("public static synchronized void onSubjectDeath(");
        int shutdown = controller.indexOf("public static synchronized void shutdownServerState(");
        String unavailableBody = controller.substring(unavailable, death);
        String deathBody = controller.substring(death, shutdown);
        assertTrue(unavailableBody.indexOf("active.intentionalTeardown")
                < unavailableBody.indexOf("finish(server, active"));
        assertTrue(deathBody.indexOf("active.intentionalTeardown")
                < deathBody.indexOf("V4SubjectDeathDiagnostics.capture("));
        int teardownHelper = controller.indexOf(
                "private static void discardOwnedFixture(");
        String teardownBody = controller.substring(teardownHelper,
                controller.indexOf("private static void discardPartiallyCreatedFixture(",
                        teardownHelper));
        assertTrue(teardownBody.indexOf("session.intentionalTeardown = true")
                < teardownBody.indexOf("V4FixtureCleanup.discardOwned("));
        assertTrue(controller.contains("TEARDOWN_UNLOAD_IGNORED"));
        assertTrue(controller.contains("TEARDOWN_DEATH_CALLBACK_IGNORED"));
    }

    @Test
    void geometryBuilderForcesExactChunksBeforeMutationAndVerifiesBeforeEntityCreation()
            throws Exception {
        String builder = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4FixtureGeometryBuilder.java"));
        int acquire = builder.indexOf("acquireChunks(level, requiredChunks");
        int mutation = builder.indexOf("diagnostics.geometryMutationAttempted = true");
        int verify = builder.indexOf("verifyPostconditions(level, origin, diagnostics)");
        assertTrue(acquire >= 0 && mutation > acquire && verify > mutation,
                "all required chunks must be ready before mutation, then verified afterward");

        for (String required : new String[] {
                "RULE_COMMAND_MODIFICATION_BLOCK_LIMIT", "getForcedChunks()",
                "setChunkForced(", "level.getChunk(chunk.x, chunk.z)",
                "geometryChunksRequired", "geometryChunksReady",
                "geometryMutationAttempted", "geometryMutationSucceeded",
                "geometryStructureVerified", "geometryVerified", "geometryFailureStage",
                "geometryFailureCoordinate", "expectedBlock", "actualBlock",
                "verifySpawnGeometry", "isFaceSturdy", "getCollisionShape",
                "Blocks.LIGHT", "LightBlock.LEVEL", "verifyLightBlocksPresent",
                "verifyPropagatedLighting",
                "fixtureLightingVerified", "minimumRepresentativeBlockLight"}) {
            assertTrue(builder.contains(required), "missing geometry proof boundary: " + required);
        }
        for (String forbidden : new String[] {
                "findSafe", "safe nearby", "spawn retry", "teleportTo(",
                "VillageIntent", "designateHome(", "startSleeping(", "performResolvedTrade(",
                "runLightUpdates", "method_15516", ".join()", ".get()", "Thread.sleep("}) {
            assertFalse(builder.contains(forbidden),
                    "geometry fixture exceeded setup authority: " + forbidden);
        }

        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        assertTrue(controller.indexOf("V4FixtureGeometryBuilder.createAndVerifyStructure(")
                        < controller.indexOf("V4FixtureEntityFactory.createAndVerify("));
        assertFalse(controller.contains("forceCorridorChunks("),
                "the controller must not retain a second late chunk-forcing path");
        assertFalse(controller.contains("geometryFunctionExecuted"),
                "invocation must not be reported as successful geometry creation");
    }

    @Test
    void threadedLightingIsABoundedPreEntityLifecycleRatherThanASynchronousFlush()
            throws Exception {
        String validation = readTree(Path.of("src/validation/java"));
        assertFalse(validation.contains("runLightUpdates("),
                "ThreadedLevelLightEngine must process queued work on its own lifecycle");
        assertFalse(validation.contains("method_15516"),
                "the intermediary alias must not bypass the same threaded-light restriction");
        for (String blocking : new String[] {
                ".join()", "future.get(", "Thread.sleep(", "LockSupport.park", "while (!"}) {
            assertFalse(validation.contains(blocking),
                    "lighting readiness must not block/spin the server thread: " + blocking);
        }

        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        int runStart = controller.indexOf("public static synchronized int run(");
        int statusStart = controller.indexOf("public static synchronized int status(");
        String run = controller.substring(runStart, statusStart);
        assertTrue(run.contains("State.WAITING_FIXTURE_LIGHTING"));
        assertTrue(run.contains("LIGHTING_PROPAGATION_LIMIT = 200L")
                        || controller.contains("LIGHTING_PROPAGATION_LIMIT = 200L"));
        assertFalse(run.contains("V4FixtureEnvironment.prepareBeforeEntityCreation("));
        assertFalse(run.contains("V4FixtureEntityFactory.createAndVerify("));
        assertFalse(run.contains("startupStabilityDeadline"));
        assertFalse(run.contains("BOOTSTRAP_LIMIT"));

        int tickStart = controller.indexOf("private static void tickFixtureLighting(");
        int createStart = controller.indexOf("private static void createFixtureEntitiesAndArm(");
        int stabilityStart = controller.indexOf("private static void tickStartupStability(");
        String lightingTick = controller.substring(tickStart, createStart);
        String postLighting = controller.substring(createStart, stabilityStart);
        assertTrue(lightingTick.indexOf("verifyPropagatedLighting(")
                        < lightingTick.indexOf("case PASS ->"));
        assertTrue(lightingTick.indexOf("case PASS ->")
                        < lightingTick.indexOf("createFixtureEntitiesAndArm("));
        assertTrue(lightingTick.contains("fixture lighting propagation timeout"));
        assertTrue(lightingTick.contains("State.FIXTURE_FAILURE"));
        assertTrue(postLighting.indexOf("V4FixtureEnvironment.prepareBeforeEntityCreation(")
                        < postLighting.indexOf("V4FixtureEntityFactory.createAndVerify("));
        assertEquals(1, count(controller,
                "V4FixtureEnvironment.prepareBeforeEntityCreation("));
        assertEquals(1, count(controller, "V4FixtureEntityFactory.createAndVerify("));
        assertTrue(postLighting.indexOf("session.fixtureCreationTick = now")
                        < postLighting.indexOf("session.startupStabilityDeadline = now"));

        int releaseStart = controller.indexOf("private static int releaseChunks(");
        int finishStart = controller.indexOf("private static void finish(");
        String lightingOnly = controller.substring(tickStart, createStart);
        assertFalse(lightingOnly.contains("setChunkForced("));
        assertFalse(lightingOnly.contains("forcedChunks.clear()"));
        assertTrue(controller.indexOf("session.forcedChunks.clear()", releaseStart) >= 0);
        assertTrue(controller.indexOf("releaseChunks(server, session)", finishStart) >= 0,
                "timeout/stop finish paths must release the chunks retained through lighting wait");

        String environment = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4FixtureEnvironment.java"));
        assertTrue(environment.contains("RULE_DOMOBSPAWNING"));
        assertTrue(controller.contains("V4FixtureEnvironment.restore("),
                "a captured environment lease must still restore on terminal finish");
    }

    @Test
    void geometryPlanCoversVillageCorridorDepartureAndExactPoiPostconditions() {
        BlockPos origin = BlockPos.ZERO;
        var chunks = V4FixtureGeometryBuilder.requiredChunks(origin);
        assertEquals(46, chunks.size(), "exact union of village/corridor/departure chunks changed");
        for (ChunkPos required : new ChunkPos[] {
                new ChunkPos(-2, -2), new ChunkPos(1, 1),
                new ChunkPos(0, -1), new ChunkPos(11, 0),
                new ChunkPos(9, -2), new ChunkPos(12, 1)}) {
            assertTrue(chunks.contains(required), "missing required geometry chunk " + required);
        }

        Map<String, V4FixtureGeometryBuilder.Postcondition> checks =
                V4FixtureGeometryBuilder.representativePostconditions(origin).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                V4FixtureGeometryBuilder.Postcondition::label, value -> value));
        assertEquals(BlockPos.ZERO.offset(2, -1, 0), checks.get("subject-support").pos());
        assertEquals("minecraft:stone", checks.get("subject-support").expected().description());
        assertEquals(BlockPos.ZERO.offset(90, -1, 0), checks.get("corridor-floor-90").pos());
        assertEquals("minecraft:air",
                checks.get("corridor-head-clearance-180").expected().description());
        assertEquals(BlockPos.ZERO.offset(202, -1, 22), checks.get("departure-floor").pos());
        assertEquals("minecraft:bell", checks.get("bell").expected().description());
        assertEquals("minecraft:smithing_table",
                checks.get("workstation").expected().description());
        assertEquals(6, checks.values().stream()
                .filter(check -> check.expected().description().contains("_bed["))
                .count(), "all six bed halves must carry exact final-state expectations");
        assertTrue(checks.values().stream()
                .filter(check -> check.expected().description().contains("_bed["))
                .allMatch(check -> check.expected().description().contains("facing=south")));
        for (int[] range : new int[][] {{-24, 24}, {0, 180}, {158, 202}, {-22, 22}}) {
            var axis = V4FixtureGeometryBuilder.coveredAxis(range[0], range[1]);
            assertEquals(range[0], axis.getFirst());
            assertEquals(range[1], axis.getLast());
            for (int i = 1; i < axis.size(); i++) {
                assertTrue(axis.get(i) - axis.get(i - 1) <= 6,
                        "lighting grid left an uncovered gap");
            }
        }
    }

    @Test
    void arenaBoundarySealsIngressWithoutClosingTheCommuteInterior() {
        var boundary = new java.util.HashSet<>(
                V4FixtureGeometryBuilder.arenaBoundaryOffsets());
        assertFalse(boundary.isEmpty());
        for (int x = 0; x <= 180; x++) {
            assertTrue(V4FixtureGeometryBuilder.arenaInterior(x, 0, 0));
            assertFalse(boundary.contains(new BlockPos(x, 0, 0)),
                    "corridor centerline must remain navigable at x=" + x);
        }
        assertTrue(V4FixtureGeometryBuilder.arenaInterior(-1, 0, 0),
                "fixture trader remains inside the sealed arena");
        assertTrue(V4FixtureGeometryBuilder.arenaInterior(180, 0, 0),
                "departure point remains connected to the corridor");
        assertTrue(V4FixtureGeometryBuilder.arenaInterior(185, 0, 0),
                "controlled interruption spawn area remains available");
        assertTrue(boundary.contains(new BlockPos(-25, 0, 0)));
        assertTrue(boundary.contains(new BlockPos(203, 0, 0)));
        assertFalse(boundary.contains(new BlockPos(90, 4, 0)),
                "the corridor must remain open above its walkable interior");
    }

    @Test
    void arenaIsOpenSkyButEveryGroundLevelHorizontalExitIsSealed() {
        var boundary = new java.util.HashSet<>(
                V4FixtureGeometryBuilder.arenaBoundaryOffsets());
        var horizontal = new net.minecraft.core.Direction[] {
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST};

        for (int x = -24; x <= 202; x++) {
            for (int z = -24; z <= 24; z++) {
                if (!V4FixtureGeometryBuilder.arenaInteriorColumn(x, z)) {
                    continue;
                }
                for (int y = 0; y <= 8; y++) {
                    assertFalse(boundary.contains(new BlockPos(x, y, z)),
                            "validation roof/interior Barrier at " + x + "," + y + "," + z);
                }
                for (net.minecraft.core.Direction direction : horizontal) {
                    int outsideX = x + direction.getStepX();
                    int outsideZ = z + direction.getStepZ();
                    if (V4FixtureGeometryBuilder.arenaInteriorColumn(outsideX, outsideZ)) {
                        continue;
                    }
                    for (int y : new int[] {0, 2, 4}) {
                        assertTrue(boundary.contains(new BlockPos(outsideX, y, outsideZ)),
                                "horizontal perimeter gap at " + outsideX + "," + y + ","
                                        + outsideZ);
                    }
                }
            }
        }

        assertTrue(V4FixtureGeometryBuilder.arenaInteriorColumn(24, 0));
        assertTrue(V4FixtureGeometryBuilder.arenaInteriorColumn(25, 0),
                "village-to-corridor transition must remain open");
        assertTrue(V4FixtureGeometryBuilder.arenaInteriorColumn(157, 0));
        assertTrue(V4FixtureGeometryBuilder.arenaInteriorColumn(158, 0),
                "corridor-to-departure transition must remain open");
        assertTrue(V4FixtureGeometryBuilder.arenaInteriorColumn(185, 0),
                "controlled interrupter remains spawnable inside the departure area");
    }

    @Test
    void isolationCannotReplaceHeightmapSurfaceAboveTravelColumns() throws Exception {
        String exploring = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        assertTrue(exploring.contains("Heightmap.Types.MOTION_BLOCKING_NO_LEAVES"),
                "fixture invariant must remain tied to production landing resolution");

        for (BlockPos barrier : V4FixtureGeometryBuilder.arenaBoundaryOffsets()) {
            assertFalse(V4FixtureGeometryBuilder.arenaInteriorColumn(
                            barrier.getX(), barrier.getZ()),
                    "validation-owned motion-blocking isolation entered an intended travel column: "
                            + barrier.toShortString());
        }
    }

    @Test
    void routeFailureWordingSeparatesTerminalPathFailureFromInterruptionEvidence() {
        assertEquals("required-trade commute terminated with route-failure evidence",
                V4RuntimeCampaignController.routeFailureReason(false));
        assertEquals("interruption produced route-failure evidence",
                V4RuntimeCampaignController.routeFailureReason(true));
    }

    @Test
    void settlementMemoryOrderingAndBoardObservationRemainPassive() throws Exception {
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        assertTrue(controller.contains("WAITING_SETTLEMENT_MEMORY"));
        assertTrue(controller.indexOf("prepareSettlementMemoryInventory(subject, backpack)")
                < controller.indexOf("session.state = State.WAITING_STARTUP_STABILITY"));
        assertTrue(controller.indexOf("session.settlementMemoryReadyTick = now")
                < controller.indexOf("prepareSubjectInventory(subject, backpack, INITIAL_PRICE)"));
        assertTrue(controller.indexOf("session.warmupDemandOpenedTick = now")
                < controller.indexOf("session.deadline = now + BOOTSTRAP_LIMIT",
                        controller.indexOf("session.warmupDemandOpenedTick = now")));
        assertTrue(controller.contains("VillageMemorySavedData.peekInDimension(level)"));
        assertFalse(controller.contains("VillageMemorySavedData.get(level)"));

        String mixin = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4KnownTraderObservationMixin.java"));
        assertFalse(mixin.contains("if (!Boolean.TRUE.equals(cir.getReturnValue()))"));
        assertTrue(mixin.contains("V4RuntimeWitnessTracker.observeBoardInvocation("));
        assertTrue(mixin.contains("V4OfferFingerprint.of(offer),\n"
                + "                        level.getGameTime()"));
        assertFalse(mixin.contains("getOffers()"));
        assertFalse(mixin.contains("getEntitiesOfClass"));
    }

    @Test
    void transactionFingerprintIsInvocationLocalAndCapturedBeforeMutation() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4VillagerTradeAdapterMixin.java"));
        int head = mixin.indexOf("at = @At(\"HEAD\")");
        int capture = mixin.indexOf(
                "preTradeFingerprint.set(V4OfferFingerprint.of(live))", head);
        int returned = mixin.indexOf("at = @At(\"RETURN\")", capture);
        int submit = mixin.indexOf(
                "backpack, villager.getUUID(), preTradeFingerprint.get()", returned);
        assertTrue(head >= 0 && capture > head && returned > capture && submit > returned,
                "the exact invocation must carry its immutable HEAD fingerprint to RETURN");
        assertEquals(2, count(mixin, "@Share(\"preTradeFingerprint\")"));
        for (String forbidden : new String[] {
                "ThreadLocal", "ConcurrentHashMap", "Map<", "getOffers()",
                "getEntitiesOfClass", "V4OfferFingerprint.of(live),\n"
                        + "                cir.getReturnValue()"}) {
            assertFalse(mixin.contains(forbidden),
                    "transaction evidence acquired an unsafe cache/read: " + forbidden);
        }
    }

    @Test
    void unknownSettlementMayOpenLocalBootstrapIntentWithoutBecomingPhaseAEvidence()
            throws Exception {
        String ranker = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/routing/"
                        + "SettlementDestinationRanker.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/intent/"
                        + "VillageIntentPolicy.java"));
        assertTrue(ranker.contains("candidate.facts().capabilityEvidence().rank()")
                        || ranker.contains("selection.facts().capabilityEvidence().rank()"));
        assertFalse(ranker.contains(
                "capabilityEvidence() == CapabilityEvidenceClass.POSITIVE_HINT"),
                "UNKNOWN must remain a rankable investigation candidate");
        assertFalse(policy.contains("CapabilityEvidenceClass.POSITIVE_HINT"),
                "intent opening must not acquire an unstated positive-hint gate");

        String tracker = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeWitnessTracker.java"));
        assertTrue(tracker.contains("BOOTSTRAP_LOCAL_REQUIRED_TRADE"));
        assertTrue(tracker.contains("BOOTSTRAP_LOCAL_COMMUTE_SEED"));
        assertFalse(tracker.contains("PREMATURE_REQUIRED_TRADE_INTENT"));
    }

    @Test
    void environmentIsolationIsOneShotRestorableAndPrecedesEntityCreation()
            throws Exception {
        String environment = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4FixtureEnvironment.java"));
        for (String required : new String[] {
                "RULE_DOMOBSPAWNING", ".set(false, level.getServer())",
                "entity instanceof Enemy", "hostile.discard()",
                "bootstrapForeignHostilesRemaining", "static boolean restore("}) {
            assertTrue(environment.contains(required),
                    "missing environment isolation boundary: " + required);
        }
        for (String forbidden : new String[] {
                ".hurt(", ".damage(", "setHealth(", "heal(", "Difficulty.PEACEFUL",
                "setDifficulty("}) {
            assertFalse(environment.contains(forbidden),
                    "fixture isolation altered gameplay mortality/difficulty: " + forbidden);
        }

        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        int geometry = controller.indexOf(
                "V4FixtureGeometryBuilder.createAndVerifyStructure(");
        int isolate = controller.indexOf("V4FixtureEnvironment.prepareBeforeEntityCreation(");
        int entities = controller.indexOf("V4FixtureEntityFactory.createAndVerify(");
        assertTrue(geometry >= 0 && isolate > geometry && entities > isolate);
        assertEquals(1, count(controller,
                "V4FixtureEnvironment.prepareBeforeEntityCreation("),
                "ambient hostiles must be removed once, not continuously during bootstrap");
        assertTrue(controller.contains("V4FixtureEnvironment.restore("));
        assertTrue(controller.contains("spawnInterrupter("),
                "the later validation-owned interruption remains available");

        V4FixtureEnvironment.Diagnostics diagnostics =
                new V4FixtureEnvironment.Diagnostics();
        diagnostics.doMobSpawningCaptured = true;
        diagnostics.doMobSpawningDisabled = true;
        diagnostics.preflightComplete = true;
        diagnostics.bootstrapForeignHostilesRemaining = 1;
        assertFalse(diagnostics.readyForEntityCreation());
        diagnostics.bootstrapForeignHostilesRemaining = 0;
        assertTrue(diagnostics.readyForEntityCreation());
    }

    @Test
    void everyTerminalCapturesTriStateFixtureFactsBeforeReportConstruction()
            throws Exception {
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        int finish = controller.indexOf("private static void finish(");
        int capture = controller.indexOf("captureTerminalFixtureFacts(server, session, tick)", finish);
        int snapshot = controller.indexOf("V4TradeLivenessWitness.snapshot()", finish);
        assertTrue(capture > finish && snapshot > capture,
                "terminal facts must be sampled before report snapshot construction");
        assertTrue(controller.contains("measurement(tradeLiveness.fixtureTraderAlive())"));
        assertTrue(controller.contains("measurement(tradeLiveness.subjectEmeraldCount())"));
        assertTrue(controller.contains("return value == null ? \"NOT_MEASURED\""));
    }

    @Test
    void attachmentGateRejectsAnyUnverifiedRequiredObject() {
        V4FixtureEntityFactory.Diagnostics diagnostics =
                new V4FixtureEntityFactory.Diagnostics();
        diagnostics.entityTypePresent = true;
        diagnostics.spawnAttempted = true;
        diagnostics.spawnSucceeded = true;
        diagnostics.spawnedUUID = UUID.randomUUID();
        diagnostics.expectedTagsPresent = true;
        diagnostics.levelEntityResolvable = true;
        diagnostics.playerMobsCompatibilityAvailable = true;
        diagnostics.playerMobsIsPlayerMob = true;
        diagnostics.canonicalHostSpawnApiPresent = true;
        diagnostics.canonicalHostSpawnUsed = true;
        diagnostics.canonicalHostSpawnReturned = true;
        diagnostics.subjectGeometryValid = true;
        diagnostics.traderGeometryValid = true;
        diagnostics.helperGeometryValid = true;
        diagnostics.traderCreated = true;
        diagnostics.traderUUID = UUID.randomUUID();

        assertFalse(diagnostics.ready(),
                "bootstrap must remain closed while the required helper is unverified");
        diagnostics.helperCreated = true;
        diagnostics.helperUUID = UUID.randomUUID();
        assertTrue(diagnostics.ready(),
                "the gate opens only after subject, trader and helper are all verified");
    }

    @Test
    void canonicalHostSummonApiIsPinnedAndFixtureGeometryDoesNotOverlapBeds() throws Exception {
        Path hostJar = Path.of(System.getProperty("spm.reference.jar"));
        try (ZipFile zip = new ZipFile(hostJar.toFile())) {
            byte[] bytes = zip.getInputStream(zip.getEntry(
                    "games/brennan/playermob/entity/PlayerMobSummon.class")).readAllBytes();
            String constants = new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(constants.contains("summon"));
            assertTrue(constants.contains("(Lnet/minecraft/class_3218;DDDFLjava/lang/Integer;"
                    + "Ljava/lang/Integer;Ljava/lang/Integer;)"),
                    "pinned SPM 0.96 public canonical summon signature changed");
        }

        String factory = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4FixtureEntityFactory.java"));
        assertTrue(factory.contains("origin.offset(-7, 0, -2)"));
        assertFalse(factory.contains("origin.offset(-7, 0, 1)"),
                "the old helper coordinate was exactly inside the blue bed head block");
        assertTrue(factory.contains("isFaceSturdy"));
        assertTrue(factory.contains("getCollisionShape"));
    }

    @Test
    void deathCallbackPreservesDamageSourceAndNeverMasksMortality() throws Exception {
        String bootstrap = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "SpmScavengerValidation.java"));
        assertTrue(bootstrap.contains("onSubjectDeath("));
        assertTrue(bootstrap.contains("mob, damageSource"));
        assertFalse(bootstrap.contains(
                "V4RuntimeCampaignController.onSubjectUnavailable(\n"
                        + "                        mob.level().getServer(), mob.getUUID(), \"death\""),
                "V4 must not collapse AFTER_DEATH to a string");

        String diagnostics = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4SubjectDeathDiagnostics.java"));
        for (String field : new String[] {
                "damageSourceIdentity", "damageType", "damageMessageId",
                "directEntityUuid", "causingEntityUuid", "health", "maxHealth",
                "blockAtFeet", "blockAtHead", "blockBelow", "onFire", "fallDistance",
                "difficulty", "deadOrDying", "removalReason",
                "ticksSinceFixtureCreation"}) {
            assertTrue(diagnostics.contains(field), "lost death diagnostic: " + field);
        }

        String validation = readTree(Path.of("src/validation/java"));
        for (String forbidden : new String[] {
                "setInvulnerable(true)", "setHealth(", "heal(",
                "ALLOW_DEATH", "cancelDeath", "setNoGravity(true)"}) {
            assertFalse(validation.contains(forbidden),
                    "validation must observe mortality rather than suppress it: " + forbidden);
        }
    }

    @Test
    void validationMixinsObserveActualProductionSeamsAndArePackagedOnlyBySidecar() throws Exception {
        String config = Files.readString(Path.of(
                "src/validation/resources/spmscavenger.validation.mixins.json"));
        for (String observer : new String[] {
                "V4KnownTraderObservationMixin", "V4VillageInteractionDirectorMixin",
                "V4ExploringGoalWitnessMixin", "V4VillagerTradeAdapterMixin",
                "V4FirstHomePromotionMixin"}) {
            assertTrue(config.contains(observer));
        }
        String metadata = Files.readString(Path.of("src/validation/resources/fabric.mod.json"));
        assertTrue(metadata.contains("spmscavenger.validation.mixins.json"));
        assertFalse(Files.readString(Path.of("src/main/resources/fabric.mod.json"))
                .contains("spmscavenger.validation.mixins.json"));

        String mixins = readTree(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin"));
        assertTrue(mixins.contains("KnownTraderMarketObservation"));
        assertTrue(mixins.contains("openOrResumeRequiredTrade"));
        assertTrue(mixins.contains("seedRequiredTradeCommuteExpedition"));
        assertTrue(mixins.contains("performResolvedTrade"));
        assertTrue(mixins.contains("afterSuccessfulSleep"));
    }

    @Test
    void exploringGoalStopWitnessNamesReadableAndIntermediaryTargetsAndRemainsRequired()
            throws Exception {
        String mixin = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4ExploringGoalWitnessMixin.java"));
        assertTrue(mixin.contains("method = {\"stop\", \"method_6270\"}"),
                "ExploringGoal overrides Goal.stop, which is method_6270 in the remapped "
                        + "production JAR; both namespaces are required for the validation sidecar");

        String config = Files.readString(Path.of(
                "src/validation/resources/spmscavenger.validation.mixins.json"));
        assertTrue(config.contains("\"required\": true"));
        assertTrue(config.contains("\"defaultRequire\": 1"),
                "the runtime witness must fail closed if neither namespace target attaches");
        assertFalse(mixin.contains("require = 0"),
                "namespace compatibility must not turn the witness into an optional no-op");
    }

    @Test
    void requiredTradePathWrapperObservesOneExistingCallWithoutChangingIt() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4ExploringGoalWitnessMixin.java"));
        assertTrue(mixin.contains("@WrapOperation("));
        assertTrue(mixin.contains("method = \"planCurrentStage\""));
        assertTrue(mixin.contains("PathNavigation;"));
        assertTrue(mixin.contains("createPath(Lnet/minecraft/core/BlockPos;I)"));
        assertEquals(1, count(mixin, "original.call(navigation, candidate, reach)"),
                "the wrapper must invoke the exact production request once");
        assertFalse(mixin.contains("navigation.createPath("),
                "validation must not issue a second path request");
        assertTrue(mixin.contains("return path;"),
                "the original Path reference must pass back unchanged");
        assertTrue(mixin.contains("mob.getUUID()"),
                "the tracker must filter against the exact armed fixture UUID");
        assertTrue(mixin.contains("mob.onGround()"));
        assertTrue(mixin.contains("mob.isInWater()"));
        assertTrue(mixin.contains("mob.isPassenger()"));
    }

    @Test
    void operatorMilestonesAreGuardedOneTimeTransitions() throws Exception {
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        for (String guard : new String[] {
                "phaseADepartureMessageSent", "commuteMessageSent", "terminalMessageSent"}) {
            assertTrue(controller.contains("if (session." + guard),
                    "missing one-time milestone guard: " + guard);
        }
        assertEquals(1, count(controller, "[V4-G] Phase A started."));
        assertEquals(1, count(controller, "[V4-G] REQUIRED_TRADE commute admitted."));
        assertEquals(1, count(controller, "[V4-G] Campaign finished: "));
        assertTrue(controller.contains("No visual supervision is required."));
        assertTrue(controller.contains("No player interaction required."));
        assertFalse(controller.contains("follow the PlayerMob"));
    }

    private static int count(String source, String token) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(token, at)) >= 0; at += token.length()) {
            count++;
        }
        return count;
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(path));
            }
        }
        return source.toString();
    }
}
