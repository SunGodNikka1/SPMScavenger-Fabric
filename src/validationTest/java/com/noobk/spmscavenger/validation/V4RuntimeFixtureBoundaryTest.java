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
        int checkedGeometry = controller.indexOf("V4FixtureGeometryBuilder.createAndVerify(");
        int checkedCreation = controller.indexOf("V4FixtureEntityFactory.createAndVerify(");
        int stability = controller.indexOf("preparing.state = State.WAITING_STARTUP_STABILITY");
        int stabilityMethod = controller.indexOf("private static void tickStartupStability(");
        int bootstrap = controller.indexOf(
                "session.state = State.WAITING_SETTLEMENT_AND_INITIAL_BOARD", stabilityMethod);
        assertTrue(checkedCleanup >= 0 && checkedGeometry > checkedCleanup
                        && checkedCreation > checkedGeometry
                        && stability > checkedCreation && bootstrap > stabilityMethod,
                "verified cleanup/geometry/attachment must lead to startup stability, and only that lifecycle "
                        + "gate may open bootstrap");
        int runStart = controller.indexOf("public static synchronized int run(");
        int statusStart = controller.indexOf("public static synchronized int status(");
        String runMethod = controller.substring(runStart, statusStart);
        assertFalse(runMethod
                        .contains("State.WAITING_SETTLEMENT_AND_INITIAL_BOARD"),
                "run() must not open bootstrap from instantaneous attachment");
        assertFalse(runMethod.contains("\"scenario/v4_g\""),
                "run() must not infer geometry success from an unchecked mcfunction");
        assertTrue(runMethod.contains("fixtureGeometryDiagnostics.ready()"),
                "entity creation must remain behind the explicit geometry gate");
        assertTrue(runMethod.contains("startupCleanupDiagnostics.ready()"),
                "geometry creation must remain behind the synchronous cleanup gate");
        assertFalse(controller.contains("fixture PlayerMob not found"));
        assertFalse(controller.contains("findTagged(level, origin"));

        int initialOffer = runMethod.indexOf("configureOffer(trader, INITIAL_PRICE)");
        int earlyArm = runMethod.indexOf("V4RuntimeWitnessTracker.arm(");
        int stabilityState = runMethod.indexOf("State.WAITING_STARTUP_STABILITY");
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
        assertTrue(run.contains("prepareSubjectInventory(subject, backpack, INITIAL_PRICE)"),
                "warmup must start with the exact live-offer funding amount");
        assertFalse(run.contains("performResolvedTrade("),
                "the validation controller must not execute its warmup transaction");

        int bootstrapStart = controller.indexOf("private static void tickBootstrap(");
        int phaseAStart = controller.indexOf("private static void openPhaseA(");
        String bootstrap = controller.substring(bootstrapStart, phaseAStart);
        for (String evidence : new String[] {
                "initialWarmupOfferExecuted()", "countAll(subject, backpack, Items.IRON_PICKAXE)",
                "bootstrapWarmupDemandResolved", "bootstrapCapabilityPersisted",
                "bootstrap staging allowed REQUIRED_TRADE before departure"}) {
            assertTrue(bootstrap.contains(evidence), "missing warmup boundary: " + evidence);
        }

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
                "bootstrapPrematureRequiredTrade=", "bootstrapPrematureArrival=",
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
        int geometry = controller.indexOf("V4FixtureGeometryBuilder.createAndVerify(");
        int entities = controller.indexOf("V4FixtureEntityFactory.createAndVerify(");
        assertTrue(cleanupGate >= 0 && geometry > cleanupGate && entities > geometry);
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
                "geometryVerified", "geometryFailureStage",
                "geometryFailureCoordinate", "expectedBlock", "actualBlock",
                "verifySpawnGeometry", "isFaceSturdy", "getCollisionShape"}) {
            assertTrue(builder.contains(required), "missing geometry proof boundary: " + required);
        }
        for (String forbidden : new String[] {
                "findSafe", "safe nearby", "spawn retry", "teleportTo(",
                "VillageIntent", "designateHome(", "startSleeping(", "performResolvedTrade("}) {
            assertFalse(builder.contains(forbidden),
                    "geometry fixture exceeded setup authority: " + forbidden);
        }

        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        assertTrue(controller.indexOf("V4FixtureGeometryBuilder.createAndVerify(")
                        < controller.indexOf("V4FixtureEntityFactory.createAndVerify("));
        assertFalse(controller.contains("forceCorridorChunks("),
                "the controller must not retain a second late chunk-forcing path");
        assertFalse(controller.contains("geometryFunctionExecuted"),
                "invocation must not be reported as successful geometry creation");
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
