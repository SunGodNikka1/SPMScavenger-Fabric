package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Temporary Task-59 fixture/evidence controller. It never participates in GoalSelector or product
 * admission; its only mutations are declared fixture setup, pre-window contamination removal,
 * pre-window daytime transition, declared scenario trigger, and explicit reset cleanup.
 */
public final class V3RuntimeCampaignController {

    private static final String LOG_PREFIX = "[spmscavenger/v3-campaign]";
    private static final String FIXTURE_MOB_TAG = "spm_vr.mob";
    private static final String SUBJECT_TAG = "spm_vr.subject";
    private static final long GATE0_TIMEOUT_TICKS = 2400L;
    private static final long SHELTER_RELEASE_TIMEOUT_TICKS = 200L;
    private static final int MAX_EVENTS = 32;
    private static final int MAX_OUTER_PRESENCE_LOGS = 8;

    private static Session active;
    private static CampaignReport lastReport;

    enum State {
        PREPARING,
        WAITING_GATE0_BOOTSTRAP,
        WAITING_GATE0,
        WAITING_DAYTIME,
        WAITING_SHELTER_RELEASE,
        READY,
        OBSERVING,
        OBSERVATION_COMPLETE,
        INCOMPLETE,
        FIXTURE_INCOMPLETE,
        FIXTURE_FAILURE,
        EXTERNAL_INTERFERENCE,
        ABORTED
    }

    private enum StartupStage {
        ESTABLISH_BOUNDARY,
        EXECUTE_SCENARIO,
        DISCOVER_SUBJECT,
        FORCE_CHUNKS,
        REMOVE_CONTAMINANTS,
        ACTIVATE
    }

    private V3RuntimeCampaignController() {
    }

    public static synchronized int run(CommandSourceStack source, String presetId) {
        Optional<V3CampaignScenario> selected = V3CampaignScenario.byId(presetId);
        if (selected.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Unknown V3 preset '" + presetId + "'. Use command suggestions."));
            return 0;
        }
        if (active != null) {
            source.sendFailure(Component.literal(
                    "A V3 campaign is already active: " + active.scenario.id()));
            return 0;
        }

        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal(
                    "The temporary spm_vr campaign fixture must be started in the Overworld."));
            return 0;
        }
        BlockPos origin = BlockPos.containing(source.getPosition());
        lastReport = null;
        Session preparing = Session.preparing(
                selected.get(), level.dimension(), origin, level.getGameTime());
        active = preparing;
        V3CampaignStartupGuard.Outcome startup = V3CampaignStartupGuard.execute(() ->
            record(preparing, level.getGameTime(), "PREPARING",
                    "preset=" + selected.get().id() + " origin=" + origin.toShortString()),
                outcome -> failStartup(
                source.getServer(), preparing, level.getGameTime(), outcome));
        if (!startup.succeeded()) {
            V3CampaignStartupGuard.Outcome delivery = V3CampaignStartupGuard.execute(
                    () -> sendLines(source, lastReport.lines()));
            if (!delivery.succeeded()) {
                SpmScavenger.LOGGER.error(
                        "{} scenario={} tick={} event=STARTUP_FAILURE_REPORT_DELIVERY failure={}",
                        LOG_PREFIX,
                        preparing.scenario.id(),
                        level.getGameTime(),
                        delivery.failureSummary(),
                        delivery.failure());
            }
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "V3 campaign preparation queued: " + selected.get().id()
                        + ". Use /spmscavenger debug v3 status or report."), false);
        return 1;
    }

    public static synchronized int status(CommandSourceStack source) {
        if (active == null) {
            if (lastReport == null) {
                source.sendFailure(Component.literal("No V3 campaign or report is available."));
                return 0;
            }
            sendLines(source, lastReport.summaryLines());
            return 1;
        }
        List<String> lines = List.of(
                "=== V3 Runtime Campaign Status ===",
                "scenario=" + active.scenario.id() + " row=" + active.scenario.rowId(),
                "state=" + active.state + " reason=" + active.reason,
                "fixtureStartTick=" + active.startTick
                        + " bootstrapStartTick=" + printableTick(active.bootstrapStartTick)
                        + " windowOpenTick=" + printableTick(active.openingTick),
                "contaminantsRemovedPreWindow=" + active.contaminantsRemoved,
                "subjectZone=" + active.subjectZone
                        + " leftCore=" + active.subjectLeftCore
                        + " maxDistanceFromOrigin="
                        + formatDistance(active.maxDistanceFromOrigin)
                        + " pendingClaimObservedAfterOpen="
                        + active.pendingClaimObservedAfterOpen,
                "next=" + nextExpected(active));
        sendLines(source, lines);
        return 1;
    }

    public static synchronized int report(CommandSourceStack source) {
        if (active != null) {
            sendLines(source, activeReport(active).lines());
            return 1;
        }
        if (lastReport == null) {
            source.sendFailure(Component.literal("No V3 campaign report is available."));
            return 0;
        }
        sendLines(source, lastReport.lines());
        return 1;
    }

    public static synchronized int stop(CommandSourceStack source) {
        if (active == null) {
            source.sendFailure(Component.literal("No active V3 campaign to stop."));
            return 0;
        }
        ServerLevel fixtureLevel = source.getServer().getLevel(active.dimension);
        long tick = fixtureLevel == null ? active.startTick : fixtureLevel.getGameTime();
        finish(source.getServer(), active, State.ABORTED, tick,
                "operator stopped campaign", null, null);
        source.sendSuccess(() -> Component.literal(
                "V3 campaign stopped; fixture entities remain until reset/cleanup."), false);
        return 1;
    }

    public static synchronized int reset(CommandSourceStack source) {
        BlockPos cleanupOrigin = active != null
                ? active.origin
                : lastReport != null ? lastReport.origin : BlockPos.containing(source.getPosition());
        if (active != null) {
            ServerLevel fixtureLevel = source.getServer().getLevel(active.dimension);
            long tick = fixtureLevel == null ? active.startTick : fixtureLevel.getGameTime();
            finish(source.getServer(), active, State.ABORTED, tick,
                    "operator reset campaign", null, null);
        }
        CommandSourceStack fixtureSource = source.getServer().createCommandSourceStack()
                .withLevel(source.getServer().overworld())
                .withPosition(Vec3.atCenterOf(cleanupOrigin))
                .withSuppressedOutput();
        V3CampaignStartupGuard.Outcome cleanup = V3CampaignStartupGuard.execute(
                () -> executeFixtureFunctionNow(
                        source.getServer(), fixtureSource, "cleanup"));
        if (!cleanup.succeeded()) {
            SpmScavenger.LOGGER.error(
                    "{} event=FIXTURE_RESET_FAILURE failure={}",
                    LOG_PREFIX, cleanup.failureSummary(), cleanup.failure());
            source.sendFailure(Component.literal(
                    "V3 controller state released, but fixture cleanup failed: "
                            + cleanup.failureSummary()));
            lastReport = null;
            return 0;
        }
        active = null;
        lastReport = null;
        source.sendSuccess(() -> Component.literal(
                "V3 campaign state reset and tagged fixture cleanup invoked."), false);
        return 1;
    }

    public static synchronized void onServerTick(MinecraftServer server) {
        Session session = active;
        if (session == null) {
            return;
        }
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) {
            finish(server, session, State.INCOMPLETE, session.startTick,
                    "fixture dimension unavailable", null, null);
            return;
        }
        if (session.state == State.PREPARING) {
            tickPreparing(server, level, session);
            return;
        }
        Entity entity = level.getEntity(session.subjectId);
        if (!(entity instanceof Mob subject) || !PlayerMobs.isPlayerMob(subject)) {
            finish(server, session, State.INCOMPLETE, level.getGameTime(),
                    "subject unavailable", null, null);
            return;
        }

        V3WitnessSnapshot snapshot = V3WitnessSnapshot.capture(level, subject);
        switch (session.state) {
            case WAITING_GATE0_BOOTSTRAP ->
                    tickGate0Bootstrap(server, level, subject, snapshot, session);
            case WAITING_GATE0 -> tickGate0(server, level, subject, snapshot, session);
            case WAITING_DAYTIME -> transitionToDay(server, level, session);
            case WAITING_SHELTER_RELEASE -> tickShelterRelease(level, subject, snapshot, session);
            case OBSERVING -> tickObservation(server, level, subject, snapshot, session);
            default -> {
                // Terminal states are moved into lastReport immediately; no other active state ticks.
            }
        }
    }

    private static void tickPreparing(
            MinecraftServer server, ServerLevel level, Session session) {
        V3CampaignStartupGuard.execute(() -> {
            session.startupStage = StartupStage.EXECUTE_SCENARIO;
            CommandSourceStack fixtureSource = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(Vec3.atCenterOf(session.origin))
                    .withSuppressedOutput();
            executeFixtureFunctionNow(
                    server, fixtureSource, "scenario/" + session.scenario.id());
            session.bootstrapStartTick = level.getGameTime();
            record(session, session.bootstrapStartTick, "GATE0_BOOTSTRAP_START",
                    "minimumTicks=" + V3Gate0BootstrapGate.MINIMUM_BOOTSTRAP_TICKS);

            session.startupStage = StartupStage.DISCOVER_SUBJECT;
            List<Mob> subjects = level.getEntitiesOfClass(
                    Mob.class,
                    scenarioCore(session.origin),
                    mob -> PlayerMobs.isPlayerMob(mob)
                            && mob.getTags().contains(SUBJECT_TAG));
            if (subjects.size() != 1) {
                throw new IllegalStateException(
                        "expected exactly one tagged subject, found " + subjects.size());
            }
            Mob subject = subjects.getFirst();
            session.subjectId = subject.getUUID();

            session.startupStage = StartupStage.FORCE_CHUNKS;
            forceScenarioCoreChunks(level, session);
            session.startupStage = StartupStage.REMOVE_CONTAMINANTS;
            removePreWindowContaminants(
                    level, session, V3ContaminationScanGate.Mode.PERIODIC);
            session.startupStage = StartupStage.ACTIVATE;
            session.state = session.scenario.requiresGate0()
                    ? State.WAITING_GATE0_BOOTSTRAP : State.WAITING_DAYTIME;
            session.reason = "fixture prepared";
            record(session, level.getGameTime(), "START",
                    "preset=" + session.scenario.id()
                            + " row=" + session.scenario.rowId()
                            + " origin=" + session.origin.toShortString()
                            + " subject=" + subject.getUUID());
        }, outcome -> failStartup(server, session, level.getGameTime(), outcome));
    }

    public static synchronized void onSubjectUnavailable(
            MinecraftServer server, UUID mobId, String reason, long tick) {
        if (active != null && active.subjectId != null && active.subjectId.equals(mobId)) {
            finish(server, active, State.INCOMPLETE, tick,
                    "subject lifecycle ended: " + reason, null, null);
        }
    }

    public static synchronized void shutdownServerState(MinecraftServer server) {
        if (active != null) {
            Session stopping = active;
            active = null;
            SpmScavenger.LOGGER.info("{} scenario={} tick={} event=ABORTED reason=server_stop",
                    LOG_PREFIX, stopping.scenario.id(), stopping.startTick);
            stopping.forcedChunksReleased += releaseForcedChunks(server, stopping);
        }
        lastReport = null;
    }

    private static void tickGate0(
            MinecraftServer server,
            ServerLevel level,
            Mob subject,
            V3WitnessSnapshot snapshot,
            Session session) {
        removePreWindowContaminants(level, session, V3ContaminationScanGate.Mode.PERIODIC);
        recordTransition(session, snapshot, null);
        switch (snapshot.gate0().verdict()) {
            case PASS -> {
                session.gate0Tick = snapshot.tick();
                record(session, snapshot.tick(), "GATE0_PASS", snapshot.compactLine());
                session.state = State.WAITING_DAYTIME;
                session.reason = "Gate0 PASS; advancing declared fixture input to day";
                transitionToDay(level.getServer(), level, session);
            }
            case FIXTURE_FAILURE -> finish(server, session, State.FIXTURE_FAILURE, snapshot.tick(),
                    "Gate0=" + snapshot.gate0().reason(), snapshot, null);
            case INCOMPLETE -> {
                if (snapshot.tick() - session.startTick >= GATE0_TIMEOUT_TICKS) {
                    finish(server, session, State.INCOMPLETE, snapshot.tick(),
                            "Gate0 remained INCOMPLETE for " + GATE0_TIMEOUT_TICKS + " ticks",
                            snapshot, null);
                }
            }
        }
    }

    private static void tickGate0Bootstrap(
            MinecraftServer server,
            ServerLevel level,
            Mob subject,
            V3WitnessSnapshot snapshot,
            Session session) {
        removePreWindowContaminants(level, session, V3ContaminationScanGate.Mode.PERIODIC);
        recordTransition(session, snapshot, null);
        V3Gate0BootstrapGate.Result timing = V3Gate0BootstrapGate.evaluate(
                session.bootstrapStartTick, snapshot.tick(), snapshot.gate0());
        if (timing.verdict() == V3Gate0BootstrapGate.Verdict.WAITING_BOOTSTRAP) {
            session.reason = timing.reason();
            return;
        }
        session.state = State.WAITING_GATE0;
        session.reason = "natural settlement bootstrap complete; adjudicating Gate0";
        record(session, snapshot.tick(), "GATE0_BOOTSTRAP_COMPLETE",
                "elapsedTicks=" + timing.elapsedTicks());
        tickGate0(server, level, subject, snapshot, session);
    }

    private static void transitionToDay(
            MinecraftServer server, ServerLevel level, Session session) {
        session.state = State.WAITING_DAYTIME;
        long now = level.getGameTime();
        long current = level.getDayTime();
        try {
            CommandSourceStack fixtureSource = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(Vec3.atCenterOf(session.origin))
                    .withSuppressedOutput();
            executeFixtureCommand(server, fixtureSource, "weather clear");
            if (!level.isDay()) {
                executeFixtureCommand(server, fixtureSource, "time set day");
                record(session, now, "DAY_TRANSITION",
                        "fixture dayTime " + current + "->" + level.getDayTime()
                                + " weather=clear");
            } else {
                record(session, now, "DAY_READY", "world already daytime; weather=clear");
            }
        } catch (CommandSyntaxException ex) {
            finish(server, session, State.FIXTURE_FAILURE, now,
                    "day/weather fixture transition failed: "
                            + ex.getRawMessage().getString(), null, null);
            return;
        }
        session.dayTransitionTick = now;
        session.state = State.WAITING_SHELTER_RELEASE;
        session.reason = "waiting for genuine SHELTER_HOLD release";
    }

    private static void tickShelterRelease(
            ServerLevel level, Mob subject, V3WitnessSnapshot snapshot, Session session) {
        removePreWindowContaminants(level, session, V3ContaminationScanGate.Mode.PERIODIC);
        recordTransition(session, snapshot, null);
        if (!level.isDay()) {
            finish(level.getServer(), session, State.EXTERNAL_INTERFERENCE, snapshot.tick(),
                    "world left daytime before row opened", snapshot, null);
            return;
        }
        if (snapshot.rowPrecondition().verdict() == V3RowPrecondition.Verdict.READY) {
            V3CampaignStartupGuard.Outcome isolation = V3CampaignStartupGuard.execute(() ->
                    removePreWindowContaminants(
                            level, session, V3ContaminationScanGate.Mode.FORCED_BOUNDARY));
            if (!isolation.succeeded()) {
                SpmScavenger.LOGGER.error(
                        "{} scenario={} tick={} event=FINAL_ISOLATION_FAILURE failure={}",
                        LOG_PREFIX, session.scenario.id(), snapshot.tick(),
                        isolation.failureSummary(), isolation.failure());
                finish(level.getServer(), session, State.FIXTURE_FAILURE, snapshot.tick(),
                        "final pre-window isolation failed: " + isolation.failureSummary(),
                        snapshot, null);
                return;
            }
            record(session, snapshot.tick(), "FINAL_ISOLATION_CHECK",
                    "fresh=true quarantineRadius="
                            + V3CampaignSpatialPolicy.OBSERVATION_ENVELOPE_RADIUS);
            record(session, snapshot.tick(), "ROW_PRECONDITION_READY",
                    "daytime=true shelterHold=false");
            openWindow(level, subject, snapshot, session);
            return;
        }
        if (snapshot.tick() - session.dayTransitionTick >= SHELTER_RELEASE_TIMEOUT_TICKS) {
            finish(level.getServer(), session, State.FIXTURE_INCOMPLETE, snapshot.tick(),
                    "SHELTER_HOLD remained active for " + SHELTER_RELEASE_TIMEOUT_TICKS
                            + " daytime ticks", snapshot, null);
        }
    }

    private static void openWindow(
            ServerLevel level, Mob subject, V3WitnessSnapshot snapshot, Session session) {
        session.state = State.READY;
        session.openingTick = snapshot.tick();
        V3ScenarioEvidence.Capture evidence =
                V3ScenarioEvidence.capture(level, subject, session.origin, session.scenario);
        session.openingEvidence = combinedEvidence(snapshot, evidence);
        session.progress = V3CampaignProgress.open(
                session.scenario,
                session.openingTick,
                new V3CampaignProgress.Probe(
                        evidence.replantedTargetMask(),
                        evidence.matureTargetMask(),
                        evidence.subjectSeedCount(),
                        snapshot.combatTarget(),
                        evidence.committedHarvestActors()));
        session.lastTransitionKey = snapshot.transitionKey() + "|" + evidence.transitionFingerprint();
        session.state = State.OBSERVING;
        session.reason = "row evidence window open";
        record(session, snapshot.tick(), "WINDOW_OPEN",
                "exactOpeningTick=" + snapshot.tick() + " " + snapshot.compactLine());
        observeSubjectLocation(snapshot, subject, session);
    }

    private static void tickObservation(
            MinecraftServer server,
            ServerLevel level,
            Mob subject,
            V3WitnessSnapshot snapshot,
            Session session) {
        V3CampaignSpatialPolicy.Result spatial =
                observeSubjectLocation(snapshot, subject, session);
        List<String> interference = classifyPostOpenContamination(level, subject, session);
        if (!interference.isEmpty()) {
            V3ScenarioEvidence.Capture evidence =
                    V3ScenarioEvidence.capture(level, subject, session.origin, session.scenario);
            finish(server, session, State.EXTERNAL_INTERFERENCE, snapshot.tick(),
                    "causal unrelated PlayerMob interference: " + interference,
                    snapshot, evidence);
            return;
        }
        if (V3CampaignSpatialPolicy.spatiallyUninterpretable(
                session.scenario, spatial.zone())) {
            V3ScenarioEvidence.Capture evidence =
                    V3ScenarioEvidence.capture(level, subject, session.origin, session.scenario);
            finish(server, session, State.INCOMPLETE, snapshot.tick(),
                    "subject crossed escape boundary at distance="
                            + formatDistance(spatial.horizontalDistance()), snapshot, evidence);
            return;
        }

        V3ScenarioEvidence.Capture evidence =
                V3ScenarioEvidence.capture(level, subject, session.origin, session.scenario);
        recordTransition(session, snapshot, evidence);
        long elapsed = snapshot.tick() - session.openingTick;
        if (!session.midpointRecorded
                && elapsed >= Math.max(1, session.scenario.maxWindowTicks() / 2)) {
            session.midpointRecorded = true;
            record(session, snapshot.tick(), "MIDPOINT", snapshot.compactLine());
        }

        V3CampaignProgress.Decision decision = session.progress.observe(
                snapshot.tick(),
                new V3CampaignProgress.Probe(
                        evidence.replantedTargetMask(),
                        evidence.matureTargetMask(),
                        evidence.subjectSeedCount(),
                        snapshot.combatTarget(),
                        evidence.committedHarvestActors()));
        if (decision.fireDeclaredTrigger()) {
            V3CampaignStartupGuard.Outcome trigger = V3CampaignStartupGuard.execute(() -> {
                CommandSourceStack fixtureSource = server.createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(Vec3.atCenterOf(session.origin))
                        .withSuppressedOutput();
                executeFixtureFunctionNow(
                        server, fixtureSource, "_lib/stage_interrupt_zombie");
            });
            if (trigger.succeeded()) {
                session.progress.markTriggerFired();
                record(session, snapshot.tick(), "DECLARED_TRIGGER",
                        "spm_vr:_lib/stage_interrupt_zombie at open+"
                                + elapsed + "t");
            } else {
                SpmScavenger.LOGGER.error(
                        "{} scenario={} tick={} event=DECLARED_TRIGGER_FAILURE failure={}",
                        LOG_PREFIX, session.scenario.id(), snapshot.tick(),
                        trigger.failureSummary(), trigger.failure());
                finish(server, session, State.FIXTURE_FAILURE, snapshot.tick(),
                        "declared scenario trigger failed: " + trigger.failureSummary(),
                        snapshot, evidence);
                return;
            }
        }
        if (session.progress.terminalObservedAt() >= 0L && !session.terminalTransitionRecorded) {
            session.terminalTransitionRecorded = true;
            record(session, session.progress.terminalObservedAt(), "TERMINAL_OBSERVED",
                    decision.reason());
        }
        if (decision.disposition() == V3CampaignProgress.Disposition.OBSERVATION_COMPLETE) {
            finish(server, session, State.OBSERVATION_COMPLETE, snapshot.tick(),
                    decision.reason(), snapshot, evidence);
        } else if (decision.disposition() == V3CampaignProgress.Disposition.INCOMPLETE) {
            finish(server, session, State.INCOMPLETE, snapshot.tick(),
                    decision.reason(), snapshot, evidence);
        }
    }

    private static void recordTransition(
            Session session,
            V3WitnessSnapshot snapshot,
            V3ScenarioEvidence.Capture evidence) {
        String key = snapshot.transitionKey()
                + (evidence == null ? "" : "|" + evidence.transitionFingerprint());
        if (!key.equals(session.lastTransitionKey)) {
            session.lastTransitionKey = key;
            record(session, snapshot.tick(), "SNAPSHOT", snapshot.compactLine());
        }
    }

    private static void removePreWindowContaminants(
            ServerLevel level,
            Session session,
            V3ContaminationScanGate.Mode mode) {
        for (Mob mob : scanContaminants(level, session, mode)) {
            V3CampaignContaminationPolicy.Action action =
                    V3CampaignContaminationPolicy.decide(false, false);
            if (action == V3CampaignContaminationPolicy.Action.REMOVE_PRE_WINDOW) {
                record(session, level.getGameTime(), "CONTAMINANT_REMOVED",
                        "uuid=" + mob.getUUID() + " name=" + mob.getName().getString());
                session.contaminantsRemoved++;
                mob.discard();
            }
        }
    }

    private static List<Mob> scanContaminants(
            ServerLevel level, Session session, V3ContaminationScanGate.Mode mode) {
        long now = level.getGameTime();
        if (!V3ContaminationScanGate.shouldScan(
                now, session.lastContaminationScanTick, mode)) {
            return List.of();
        }
        session.lastContaminationScanTick = now;
        return quarantineCandidates(level, session.origin);
    }

    private static List<Mob> quarantineCandidates(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(Mob.class, observationEnvelope(level, origin), mob ->
                PlayerMobs.isPlayerMob(mob)
                        && V3CampaignContaminationPolicy.decide(
                                false,
                                mob.getTags().contains(FIXTURE_MOB_TAG))
                        != V3CampaignContaminationPolicy.Action.IGNORE);
    }

    private static List<String> classifyPostOpenContamination(
            ServerLevel level, Mob subject, Session session) {
        long now = level.getGameTime();
        if (!V3ContaminationScanGate.shouldScan(
                now, session.lastContaminationScanTick,
                V3ContaminationScanGate.Mode.PERIODIC)) {
            return List.of();
        }
        session.lastContaminationScanTick = now;

        Set<Mob> candidates = new LinkedHashSet<>(level.getEntitiesOfClass(
                Mob.class, observationEnvelope(level, session.origin),
                mob -> isUnrelatedPlayerMob(mob)));
        AABB subjectProximity = subject.getBoundingBox().inflate(
                V3PostOpenContaminationPolicy.SUBJECT_PROXIMITY_RADIUS);
        candidates.addAll(level.getEntitiesOfClass(
                Mob.class, subjectProximity, V3RuntimeCampaignController::isUnrelatedPlayerMob));
        if (subject.getTarget() instanceof Mob target && isUnrelatedPlayerMob(target)) {
            candidates.add(target);
        }

        List<String> terminal = new ArrayList<>();
        for (Mob mob : candidates) {
            boolean targeting = mob.getTarget() == subject || subject.getTarget() == mob;
            double subjectDistance = Math.sqrt(mob.distanceToSqr(subject));
            V3PostOpenContaminationPolicy.Result result =
                    V3PostOpenContaminationPolicy.evaluate(
                            scenarioCore(session.origin).contains(mob.position()),
                            subjectDistance,
                            targeting,
                            false);
            String detail = "uuid=" + mob.getUUID()
                    + " distanceFromOrigin=" + formatDistance(horizontalDistance(
                            session.origin, mob.position()))
                    + " distanceFromSubject=" + formatDistance(subjectDistance)
                    + " reason=" + result.reason();
            if (result.disposition()
                    == V3PostOpenContaminationPolicy.Disposition.EXTERNAL_INTERFERENCE) {
                terminal.add(detail);
            } else if (session.outerPresenceLogged.size() < MAX_OUTER_PRESENCE_LOGS
                    && session.outerPresenceLogged.add(mob.getUUID())) {
                record(session, now, "OUTER_PLAYERMOB_PRESENCE", detail);
            }
        }
        return List.copyOf(terminal);
    }

    private static boolean isUnrelatedPlayerMob(Mob mob) {
        return PlayerMobs.isPlayerMob(mob) && !mob.getTags().contains(FIXTURE_MOB_TAG);
    }

    private static double horizontalDistance(BlockPos origin, Vec3 position) {
        double dx = position.x - (origin.getX() + 0.5);
        double dz = position.z - (origin.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static AABB scenarioCore(BlockPos origin) {
        double radius = V3CampaignSpatialPolicy.SCENARIO_CORE_RADIUS;
        return new AABB(origin).inflate(radius, 16.0, radius);
    }

    private static AABB observationEnvelope(ServerLevel level, BlockPos origin) {
        double radius = V3CampaignSpatialPolicy.OBSERVATION_ENVELOPE_RADIUS;
        return new AABB(
                origin.getX() - radius,
                level.getMinBuildHeight(),
                origin.getZ() - radius,
                origin.getX() + radius,
                level.getMaxBuildHeight(),
                origin.getZ() + radius);
    }

    private static V3CampaignSpatialPolicy.Result observeSubjectLocation(
            V3WitnessSnapshot snapshot, Mob subject, Session session) {
        V3CampaignSpatialPolicy.Result current =
                V3CampaignSpatialPolicy.classify(session.origin, subject.position());
        session.maxDistanceFromOrigin = Math.max(
                session.maxDistanceFromOrigin, current.horizontalDistance());
        session.pendingClaimObservedAfterOpen |= snapshot.pendingClaim().isPresent();
        String context = "distanceFromOrigin=" + formatDistance(current.horizontalDistance())
                + " pendingClaim=" + pendingClaimSummary(snapshot)
                + " activeClasses=" + snapshot.activeClasses();
        if (!session.subjectLeftCore
                && current.zone() != V3CampaignSpatialPolicy.Zone.SCENARIO_CORE) {
            session.subjectLeftCore = true;
            session.firstCoreExitTick = snapshot.tick();
            session.firstCoreExitEvidence = context;
            record(session, snapshot.tick(), "SUBJECT_LEFT_CORE", context);
        }
        if (!session.subjectLeftEnvelope
                && (current.zone() == V3CampaignSpatialPolicy.Zone.ESCAPE_MARGIN
                        || current.zone() == V3CampaignSpatialPolicy.Zone.ESCAPED)) {
            session.subjectLeftEnvelope = true;
            record(session, snapshot.tick(), "SUBJECT_LEFT_OBSERVATION_ENVELOPE", context);
        }
        if (!session.subjectEscaped
                && current.zone() == V3CampaignSpatialPolicy.Zone.ESCAPED) {
            session.subjectEscaped = true;
            record(session, snapshot.tick(), "SUBJECT_ESCAPED", context);
        }
        if (current.zone() != session.subjectZone) {
            record(session, snapshot.tick(), "SUBJECT_ZONE",
                    "from=" + session.subjectZone + " to=" + current.zone() + " " + context);
            session.subjectZone = current.zone();
        }
        return current;
    }

    private static String pendingClaimSummary(V3WitnessSnapshot snapshot) {
        return snapshot.pendingClaim()
                .map(claim -> "YES consumer=" + claim.consumerKey()
                        + " route=" + claim.routeIdentity())
                .orElse("NO");
    }

    private static String formatDistance(double distance) {
        return String.format(Locale.ROOT, "%.2f", distance);
    }

    private static void executeFixtureFunctionNow(
            MinecraftServer server, CommandSourceStack source, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("spm_vr", path);
        CommandFunction<CommandSourceStack> function = server.getFunctions().get(id)
                .orElseThrow(() -> new IllegalStateException(
                        "fixture function is not loaded: " + id));
        server.getFunctions().execute(function, source);
    }

    private static void executeFixtureCommand(
            MinecraftServer server, CommandSourceStack source, String command)
            throws CommandSyntaxException {
        server.getCommands().getDispatcher().execute(command, source);
    }

    private static void finish(
            MinecraftServer server,
            Session session,
            State state,
            long tick,
            String reason,
            V3WitnessSnapshot snapshot,
            V3ScenarioEvidence.Capture evidence) {
        session.state = state;
        session.reason = reason;
        session.terminalTick = tick;
        if (snapshot != null) {
            session.terminalSnapshot = snapshot.compactLine();
        }
        if (evidence != null) {
            session.terminalEvidence = snapshot == null
                    ? evidence.lines()
                    : combinedEvidence(snapshot, evidence);
        }
        record(session, tick, "FINAL", "state=" + state + " reason=" + reason);
        active = null;
        session.forcedChunksReleased += releaseForcedChunks(server, session);
        lastReport = CampaignReport.from(session);
    }

    private static void failStartup(
            MinecraftServer server,
            Session session,
            long tick,
            V3CampaignStartupGuard.Outcome outcome) {
        String reason = "startupStage=" + session.startupStage
                + " exception=" + outcome.failureSummary();
        SpmScavenger.LOGGER.error(
                "{} scenario={} tick={} event=STARTUP_FAILURE stage={} failure={}",
                LOG_PREFIX,
                session.scenario.id(),
                tick,
                session.startupStage,
                outcome.failureSummary(),
                outcome.failure());
        session.state = State.FIXTURE_FAILURE;
        session.reason = reason;
        session.terminalTick = tick;
        record(session, tick, "FINAL", "state=FIXTURE_FAILURE reason=" + reason);
        active = null;
        session.forcedChunksReleased += releaseForcedChunksSafely(server, session, tick);
        lastReport = CampaignReport.from(session);
    }

    private static CampaignReport activeReport(Session session) {
        return CampaignReport.from(session);
    }

    private static void record(Session session, long tick, String event, String detail) {
        String line = "tick=" + tick + " event=" + event + " " + detail;
        if (session.events.size() >= MAX_EVENTS) {
            session.eventsTruncated = true;
            if (event.equals("SNAPSHOT")) {
                return;
            }
            int replace = -1;
            for (int index = 0; index < session.events.size(); index++) {
                if (session.events.get(index).contains(" event=SNAPSHOT ")) {
                    replace = index;
                    break;
                }
            }
            session.events.remove(replace >= 0 ? replace : 0);
        }
        session.events.add(line);
        SpmScavenger.LOGGER.info("{} scenario={} {}",
                LOG_PREFIX, session.scenario.id(), line);
    }

    private static void sendLines(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private static List<String> combinedEvidence(
            V3WitnessSnapshot snapshot, V3ScenarioEvidence.Capture evidence) {
        List<String> lines = new ArrayList<>(snapshot.lines());
        lines.addAll(evidence.lines());
        return List.copyOf(lines);
    }

    private static void forceScenarioCoreChunks(ServerLevel level, Session session) {
        int radius = (int) V3CampaignSpatialPolicy.SCENARIO_CORE_RADIUS;
        int minChunkX = (session.origin.getX() - radius) >> 4;
        int maxChunkX = (session.origin.getX() + radius) >> 4;
        int minChunkZ = (session.origin.getZ() - radius) >> 4;
        int maxChunkZ = (session.origin.getZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.setChunkForced(chunkX, chunkZ, true)) {
                    session.ownedForcedChunks.add(new ForcedChunk(chunkX, chunkZ));
                    session.forcedChunksAcquired++;
                }
            }
        }
    }

    private static int releaseForcedChunks(MinecraftServer server, Session session) {
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) {
            return 0;
        }
        int released = 0;
        for (ForcedChunk chunk : session.ownedForcedChunks) {
            if (level.setChunkForced(chunk.x(), chunk.z(), false)) {
                released++;
            }
        }
        session.ownedForcedChunks.clear();
        return released;
    }

    @SuppressWarnings("removal")
    private static int releaseForcedChunksSafely(
            MinecraftServer server, Session session, long tick) {
        try {
            return releaseForcedChunks(server, session);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (LinkageError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            SpmScavenger.LOGGER.error(
                    "{} scenario={} tick={} event=STARTUP_RESOURCE_RELEASE_FAILURE",
                    LOG_PREFIX, session.scenario.id(), tick, failure);
            return 0;
        }
    }

    private static String printableTick(long tick) {
        return tick < 0L ? "NOT_OPEN" : Long.toString(tick);
    }

    private static String nextExpected(Session session) {
        return switch (session.state) {
            case WAITING_GATE0_BOOTSTRAP -> "natural settlement bootstrap >= "
                    + V3Gate0BootstrapGate.MINIMUM_BOOTSTRAP_TICKS + " ticks";
            case WAITING_GATE0 -> "natural Gate0=PASS";
            case WAITING_DAYTIME -> "declared pre-window day transition";
            case WAITING_SHELTER_RELEASE -> "RowPrecondition=READY";
            case OBSERVING -> "scenario minimum observation clock";
            default -> "report";
        };
    }

    private static final class Session {
        private final V3CampaignScenario scenario;
        private final ResourceKey<Level> dimension;
        private final BlockPos origin;
        private UUID subjectId;
        private final long startTick;
        private final List<String> events = new ArrayList<>();
        private final List<ForcedChunk> ownedForcedChunks = new ArrayList<>();
        private int forcedChunksAcquired;
        private int forcedChunksReleased;
        private State state;
        private String reason = "fixture prepared";
        private long gate0Tick = -1L;
        private long bootstrapStartTick = -1L;
        private long dayTransitionTick = -1L;
        private long openingTick = -1L;
        private long terminalTick = -1L;
        private int contaminantsRemoved;
        private long lastContaminationScanTick = -1L;
        private V3CampaignSpatialPolicy.Zone subjectZone =
                V3CampaignSpatialPolicy.Zone.SCENARIO_CORE;
        private boolean subjectLeftCore;
        private boolean subjectLeftEnvelope;
        private boolean subjectEscaped;
        private boolean pendingClaimObservedAfterOpen;
        private long firstCoreExitTick = -1L;
        private String firstCoreExitEvidence = "NOT_OBSERVED";
        private double maxDistanceFromOrigin;
        private boolean midpointRecorded;
        private boolean terminalTransitionRecorded;
        private boolean eventsTruncated;
        private final Set<UUID> outerPresenceLogged = new LinkedHashSet<>();
        private String lastTransitionKey = "";
        private List<String> openingEvidence = List.of();
        private String terminalSnapshot = "UNAVAILABLE";
        private List<String> terminalEvidence = List.of();
        private V3CampaignProgress progress;
        private StartupStage startupStage = StartupStage.ESTABLISH_BOUNDARY;

        private Session(
                V3CampaignScenario scenario,
                ResourceKey<Level> dimension,
                BlockPos origin,
                UUID subjectId,
                long startTick,
                State state) {
            this.scenario = scenario;
            this.dimension = dimension;
            this.origin = origin.immutable();
            this.subjectId = subjectId;
            this.startTick = startTick;
            this.state = state;
        }

        private static Session preparing(
                V3CampaignScenario scenario,
                ResourceKey<Level> dimension,
                BlockPos origin,
                long startTick) {
            return new Session(
                    scenario, dimension, origin, null, startTick, State.PREPARING);
        }
    }

    private record ForcedChunk(int x, int z) {
    }

    private record CampaignReport(
            V3CampaignScenario scenario,
            State state,
            String reason,
            UUID subjectId,
            String dimension,
            BlockPos origin,
            long fixtureStartTick,
            long bootstrapStartTick,
            long gate0Tick,
            long dayTransitionTick,
            long windowOpenTick,
            long terminalTick,
            int contaminantsRemoved,
            int forcedChunksAcquired,
            int forcedChunksReleased,
            int forcedChunksRemaining,
            boolean subjectLeftCore,
            boolean subjectLeftEnvelope,
            boolean subjectEscaped,
            V3CampaignSpatialPolicy.Zone finalSubjectZone,
            boolean pendingClaimObservedAfterOpen,
            long firstCoreExitTick,
            String firstCoreExitEvidence,
            double maxDistanceFromOrigin,
            List<String> openingEvidence,
            String terminalSnapshot,
            List<String> terminalEvidence,
            List<String> events,
            boolean eventsTruncated) {

        private CampaignReport {
            origin = origin.immutable();
            openingEvidence = List.copyOf(openingEvidence);
            terminalEvidence = List.copyOf(terminalEvidence);
            events = List.copyOf(events);
        }

        static CampaignReport from(Session session) {
            return new CampaignReport(
                    session.scenario,
                    session.state,
                    session.reason,
                    session.subjectId,
                    session.dimension.location().toString(),
                    session.origin,
                    session.startTick,
                    session.bootstrapStartTick,
                    session.gate0Tick,
                    session.dayTransitionTick,
                    session.openingTick,
                    session.terminalTick,
                    session.contaminantsRemoved,
                    session.forcedChunksAcquired,
                    session.forcedChunksReleased,
                    session.ownedForcedChunks.size(),
                    session.subjectLeftCore,
                    session.subjectLeftEnvelope,
                    session.subjectEscaped,
                    session.subjectZone,
                    session.pendingClaimObservedAfterOpen,
                    session.firstCoreExitTick,
                    session.firstCoreExitEvidence,
                    session.maxDistanceFromOrigin,
                    session.openingEvidence,
                    session.terminalSnapshot,
                    session.terminalEvidence,
                    session.events,
                    session.eventsTruncated);
        }

        List<String> summaryLines() {
            return List.of(
                    "=== V3 Runtime Campaign Report ===",
                    "scenario=" + scenario.id() + " row=" + scenario.rowId(),
                    "state=" + state + " reason=" + reason,
                    "window=" + printableTick(windowOpenTick) + "->" + printableTick(terminalTick),
                    "PRODUCT VERDICT: NOT ASSIGNED — review raw evidence against VR-T3 matrix");
        }

        List<String> lines() {
            List<String> out = new ArrayList<>(summaryLines());
            out.add("subject=" + (subjectId == null ? "UNAVAILABLE" : subjectId)
                    + " dimension=" + dimension + " origin=" + origin.toShortString());
            out.add("fixtureStartTick=" + fixtureStartTick
                    + " bootstrapStartTick=" + printableTick(bootstrapStartTick)
                    + " Gate0Tick=" + printableTick(gate0Tick)
                    + " dayTransitionTick=" + printableTick(dayTransitionTick)
                    + " exactOpeningTick=" + printableTick(windowOpenTick)
                    + " terminalTick=" + printableTick(terminalTick));
            out.add("contaminantsRemovedPreWindow=" + contaminantsRemoved);
            out.add("fixtureForcedChunks acquired=" + forcedChunksAcquired
                    + " released=" + forcedChunksReleased
                    + " remaining=" + forcedChunksRemaining);
            out.add("subjectSpatial leftCore=" + subjectLeftCore
                    + " leftObservationEnvelope=" + subjectLeftEnvelope
                    + " escaped=" + subjectEscaped
                    + " finalZone=" + finalSubjectZone
                    + " maxDistanceFromOrigin=" + formatDistance(maxDistanceFromOrigin));
            out.add("pendingClaimObservedAfterOpen=" + pendingClaimObservedAfterOpen
                    + " firstCoreExitTick=" + printableTick(firstCoreExitTick)
                    + " firstCoreExitEvidence=" + firstCoreExitEvidence);
            out.add("-- opening evidence --");
            out.addAll(openingEvidence.isEmpty() ? List.of("UNAVAILABLE") : openingEvidence);
            out.add("-- relevant campaign log lines --");
            out.addAll(events.isEmpty() ? List.of("UNAVAILABLE") : events);
            if (eventsTruncated) {
                out.add("additional transition events truncated after " + MAX_EVENTS);
            }
            out.add("-- terminal evidence --");
            out.add(terminalSnapshot);
            out.addAll(terminalEvidence.isEmpty() ? List.of("UNAVAILABLE") : terminalEvidence);
            return List.copyOf(out);
        }
    }
}
