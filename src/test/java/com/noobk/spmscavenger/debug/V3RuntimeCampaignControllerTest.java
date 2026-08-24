package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V3RuntimeCampaignControllerTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/noobk/spmscavenger/debug/V3RuntimeCampaignController.java");
    private static final Path BOOTSTRAP = Path.of(
            "src/main/java/com/noobk/spmscavenger/SpmScavenger.java");

    @Test
    void controllerUsesOneBoundedSessionAndSharedPassiveSnapshot() throws IOException {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("private static Session active"));
        assertTrue(source.contains("private static CampaignReport lastReport"));
        assertTrue(source.contains("V3WitnessSnapshot.capture("));
        assertTrue(source.contains("MAX_EVENTS"));
        assertFalse(source.contains("Map<UUID"));
        assertFalse(source.contains("ConcurrentHashMap"));
    }

    @Test
    void controllerCannotAcquireProductionAuthorityOrSteerSubject() throws IOException {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("MandatoryOwnershipRegistry.publish("));
        assertFalse(source.contains("RouteExhaustionEvidence.publish("));
        assertFalse(source.contains("VillageWorkAdmission.evaluate("));
        assertFalse(source.contains("canUse()"));
        assertFalse(source.contains("canContinueToUse()"));
        assertFalse(source.contains("getNavigation()"));
        assertFalse(source.contains("setTarget("));
        assertFalse(source.contains("startSleeping("));
        assertFalse(source.contains("Brain"));
        assertFalse(source.contains("minecraft:home"));
        assertFalse(source.contains("setItem("));
    }

    @Test
    void controllerOwnsExplicitLifecycleAndSuppressedFixtureCommands() throws IOException {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("withSuppressedOutput()"));
        assertTrue(source.contains("onSubjectUnavailable("));
        assertTrue(source.contains("shutdownServerState("));
        assertTrue(source.contains("EXTERNAL_INTERFERENCE"));
        assertTrue(source.contains("OBSERVATION_COMPLETE"));
        assertTrue(source.contains("PRODUCT VERDICT: NOT ASSIGNED"));
        assertTrue(source.contains("setChunkForced("));
        assertTrue(source.contains("ownedForcedChunks"));
        assertTrue(source.contains("Level.OVERWORLD"));
        assertTrue(source.contains("withLevel(source.getServer().overworld())"),
                "reset cleanup must remain bound to the recorded Overworld fixture");
        assertTrue(source.contains("active = null;\n        session.forcedChunksReleased"),
                "terminal release must detach active state before chunk-unload callbacks can re-enter");
        assertTrue(source.contains("Session.preparing("));
        assertTrue(source.indexOf("if (session.state == State.PREPARING)")
                        < source.indexOf("level.getEntity(session.subjectId)"),
                "PREPARING must execute before subject-dependent tick logic");
        assertTrue(source.contains("V3CampaignStartupGuard.execute("));
        assertTrue(source.contains("failStartup("));
        assertTrue(source.contains("LOGGER.error("));
        assertTrue(source.contains("startupStage"));
        int preparingStart = source.indexOf("private static void tickPreparing(");
        int preparingEnd = source.indexOf("private static void tickGate0(");
        String preparing = source.substring(preparingStart, preparingEnd);
        assertTrue(preparing.indexOf("startupStage = StartupStage.EXECUTE_SCENARIO")
                        < preparing.indexOf("executeFixtureFunctionNow("),
                "nested datapack dispatch must have an exact failure stage");
        assertTrue(source.contains("server.getFunctions().execute(function, source)"));
        assertFalse(source.contains("executeFixtureFunction("),
                "1.21 function commands must not run through raw Brigadier execute");
        assertTrue(source.contains("releaseForcedChunksSafely(server, session, tick)"));
        assertTrue(source.contains("session.state = State.FIXTURE_FAILURE"));
        assertTrue(source.contains("lastReport.summaryLines()"));
        assertTrue(source.contains("lastReport.lines()"));
        assertFalse(source.contains("forceload remove all"));

        String bootstrap = Files.readString(BOOTSTRAP);
        assertTrue(bootstrap.contains("V3RuntimeCampaignController.onServerTick(server)"));
        assertTrue(count(bootstrap, "V3RuntimeCampaignController.onSubjectUnavailable(") >= 2);
        assertTrue(bootstrap.contains("V3RuntimeCampaignController.shutdownServerState(server)"));
    }

    @Test
    void gate0ThresholdsAreAdjudicatedOnlyAfterNaturalBootstrapWindow() throws IOException {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("WAITING_GATE0_BOOTSTRAP"));
        assertTrue(source.contains("bootstrapStartTick"));
        assertTrue(source.contains("GATE0_TIMEOUT_TICKS = 2400L"),
                "the existing overall Gate-0 timeout must remain unchanged");
        assertTrue(source.contains(
                        "snapshot.tick() - session.startTick >= GATE0_TIMEOUT_TICKS"),
                "bootstrap sequencing must not restart or extend the overall Gate-0 timeout");

        int preparingStart = source.indexOf("private static void tickPreparing(");
        int bootstrapTickStart = source.indexOf("private static void tickGate0Bootstrap(");
        String preparing = source.substring(preparingStart, bootstrapTickStart);
        assertTrue(preparing.indexOf("executeFixtureFunctionNow(")
                        < preparing.indexOf("session.bootstrapStartTick = level.getGameTime()"),
                "bootstrap time must start only after successful scenario execution");
        assertTrue(preparing.contains("? State.WAITING_GATE0_BOOTSTRAP : State.WAITING_DAYTIME"));

        int bootstrapTickEnd = source.indexOf("private static void transitionToDay(");
        String bootstrapTick = source.substring(bootstrapTickStart, bootstrapTickEnd);
        assertTrue(bootstrapTick.contains("V3Gate0BootstrapGate.evaluate("));
        assertTrue(bootstrapTick.indexOf("Verdict.WAITING_BOOTSTRAP")
                        < bootstrapTick.indexOf("tickGate0(server, level, subject, snapshot, session)"),
                "numeric Gate-0 adjudication must follow the bootstrap waiting verdict");
    }

    @Test
    void postOpenCoreExitIsEvidenceRatherThanSubjectLeash() throws IOException {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("if (!arena(session.origin).contains(subject.position()))"));
        assertTrue(source.contains("SUBJECT_LEFT_CORE"));
        assertTrue(source.contains("distanceFromOrigin="));
        assertTrue(source.contains("pendingClaim="));
        assertTrue(source.contains("activeClasses="));
        assertTrue(source.contains("V3CampaignSpatialPolicy.spatiallyUninterpretable("));
        assertTrue(source.contains("observationEnvelope(level, origin)"),
                "unrelated PlayerMobs must be detected across the protected envelope");
        assertFalse(source.contains("getNavigation()"));
        assertFalse(source.contains("teleportTo("));
        assertFalse(source.contains("setDeltaMovement("));
    }

    @Test
    void finalIsolationIsFreshAndOuterPresenceNeedsCausalEvidence() throws IOException {
        String source = Files.readString(SOURCE);
        int shelterStart = source.indexOf("private static void tickShelterRelease(");
        int openStart = source.indexOf("private static void openWindow(");
        String shelter = source.substring(shelterStart, openStart);
        assertTrue(shelter.contains("Mode.FORCED_BOUNDARY"));
        assertTrue(shelter.indexOf("Mode.FORCED_BOUNDARY")
                        < shelter.indexOf("openWindow(level, subject, snapshot, session)"),
                "window opening must follow a fresh unthrottled isolation scan");
        assertTrue(source.contains("V3PostOpenContaminationPolicy.evaluate("));
        assertTrue(source.contains("OUTER_PLAYERMOB_PRESENCE"));
        assertFalse(source.contains("unrelated PlayerMob entered observation envelope:"),
                "outer-envelope presence alone must not remain terminal");
    }

    private static int count(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
