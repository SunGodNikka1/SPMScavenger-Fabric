package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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

    private static Session active;
    private static CampaignReport lastReport;

    enum State {
        PREPARING,
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
        try {
            executeFixtureFunction(
                    source.getServer(), source.withSuppressedOutput(),
                    "scenario/" + selected.get().id());
        } catch (CommandSyntaxException ex) {
            lastReport = CampaignReport.failedBeforeSubject(
                    selected.get(), level, origin, State.FIXTURE_FAILURE,
                    "preset command failed: " + ex.getRawMessage().getString());
            sendLines(source, lastReport.lines());
            return 0;
        }

        List<Mob> subjects = level.getEntitiesOfClass(
                Mob.class,
                arena(origin),
                mob -> PlayerMobs.isPlayerMob(mob) && mob.getTags().contains(SUBJECT_TAG));
        if (subjects.size() != 1) {
            lastReport = CampaignReport.failedBeforeSubject(
                    selected.get(), level, origin, State.FIXTURE_FAILURE,
                    "expected exactly one tagged subject, found " + subjects.size());
            sendLines(source, lastReport.lines());
            return 0;
        }

        Mob subject = subjects.getFirst();
        active = new Session(
                selected.get(),
                level.dimension(),
                origin,
                subject.getUUID(),
                level.getGameTime(),
                selected.get().requiresGate0() ? State.WAITING_GATE0 : State.WAITING_DAYTIME);
        forceArenaChunks(level, active);
        removePreWindowContaminants(level, active);
        record(active, level.getGameTime(), "START",
                "preset=" + selected.get().id()
                        + " row=" + selected.get().rowId()
                        + " origin=" + origin.toShortString()
                        + " subject=" + subject.getUUID());
        source.sendSuccess(() -> Component.literal(
                "V3 campaign started: " + selected.get().id()
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
                        + " windowOpenTick=" + printableTick(active.openingTick),
                "contaminantsRemovedPreWindow=" + active.contaminantsRemoved,
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
        try {
            CommandSourceStack fixtureSource = source.getServer().createCommandSourceStack()
                    .withLevel(source.getServer().overworld())
                    .withPosition(Vec3.atCenterOf(cleanupOrigin))
                    .withSuppressedOutput();
            executeFixtureFunction(source.getServer(), fixtureSource, "cleanup");
        } catch (CommandSyntaxException ex) {
            source.sendFailure(Component.literal(
                    "V3 controller state released, but fixture cleanup failed: "
                            + ex.getRawMessage().getString()));
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
        Entity entity = level.getEntity(session.subjectId);
        if (!(entity instanceof Mob subject) || !PlayerMobs.isPlayerMob(subject)) {
            finish(server, session, State.INCOMPLETE, level.getGameTime(),
                    "subject unavailable", null, null);
            return;
        }

        V3WitnessSnapshot snapshot = V3WitnessSnapshot.capture(level, subject);
        switch (session.state) {
            case WAITING_GATE0 -> tickGate0(server, level, subject, snapshot, session);
            case WAITING_DAYTIME -> transitionToDay(server, level, session);
            case WAITING_SHELTER_RELEASE -> tickShelterRelease(level, subject, snapshot, session);
            case OBSERVING -> tickObservation(server, level, subject, snapshot, session);
            default -> {
                // Terminal states are moved into lastReport immediately; no other active state ticks.
            }
        }
    }

    public static synchronized void onSubjectUnavailable(
            MinecraftServer server, UUID mobId, String reason, long tick) {
        if (active != null && active.subjectId.equals(mobId)) {
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
        removePreWindowContaminants(level, session);
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
        removePreWindowContaminants(level, session);
        recordTransition(session, snapshot, null);
        if (!level.isDay()) {
            finish(level.getServer(), session, State.EXTERNAL_INTERFERENCE, snapshot.tick(),
                    "world left daytime before row opened", snapshot, null);
            return;
        }
        if (snapshot.rowPrecondition().verdict() == V3RowPrecondition.Verdict.READY) {
            removePreWindowContaminants(level, session);
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
                session.scenario, session.openingTick, evidence.subjectSeedCount());
        session.lastTransitionKey = snapshot.transitionKey() + "|" + evidence.transitionFingerprint();
        session.state = State.OBSERVING;
        session.reason = "row evidence window open";
        record(session, snapshot.tick(), "WINDOW_OPEN",
                "exactOpeningTick=" + snapshot.tick() + " " + snapshot.compactLine());
    }

    private static void tickObservation(
            MinecraftServer server,
            ServerLevel level,
            Mob subject,
            V3WitnessSnapshot snapshot,
            Session session) {
        List<Mob> contaminants = contaminants(level, session.origin, true);
        if (!contaminants.isEmpty()) {
            V3ScenarioEvidence.Capture evidence =
                    V3ScenarioEvidence.capture(level, subject, session.origin, session.scenario);
            finish(server, session, State.EXTERNAL_INTERFERENCE, snapshot.tick(),
                    "unrelated PlayerMob entered arena: "
                            + contaminants.stream().map(Mob::getUUID).toList(), snapshot, evidence);
            return;
        }
        if (!arena(session.origin).contains(subject.position())) {
            V3ScenarioEvidence.Capture evidence =
                    V3ScenarioEvidence.capture(level, subject, session.origin, session.scenario);
            finish(server, session, State.INCOMPLETE, snapshot.tick(),
                    "subject left bounded fixture arena", snapshot, evidence);
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
                        evidence.subjectSeedCount(),
                        snapshot.combatTarget()));
        if (decision.fireDeclaredTrigger()) {
            try {
                CommandSourceStack fixtureSource = server.createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(Vec3.atCenterOf(session.origin))
                        .withSuppressedOutput();
                executeFixtureFunction(
                        server, fixtureSource, "_lib/stage_interrupt_zombie");
                session.progress.markTriggerFired();
                record(session, snapshot.tick(), "DECLARED_TRIGGER",
                        "spm_vr:_lib/stage_interrupt_zombie at open+"
                                + elapsed + "t");
            } catch (CommandSyntaxException ex) {
                finish(server, session, State.FIXTURE_FAILURE, snapshot.tick(),
                        "declared scenario trigger failed: " + ex.getRawMessage().getString(),
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

    private static void removePreWindowContaminants(ServerLevel level, Session session) {
        for (Mob mob : contaminants(level, session.origin, false)) {
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

    private static List<Mob> contaminants(
            ServerLevel level, BlockPos origin, boolean windowOpen) {
        return level.getEntitiesOfClass(Mob.class, arena(origin), mob ->
                PlayerMobs.isPlayerMob(mob)
                        && V3CampaignContaminationPolicy.decide(
                                windowOpen,
                                mob.getTags().contains(FIXTURE_MOB_TAG))
                        != V3CampaignContaminationPolicy.Action.IGNORE);
    }

    private static AABB arena(BlockPos origin) {
        return new AABB(origin).inflate(32.0, 16.0, 32.0);
    }

    private static void executeFixtureFunction(
            MinecraftServer server, CommandSourceStack source, String path)
            throws CommandSyntaxException {
        executeFixtureCommand(server, source, "function spm_vr:" + path);
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

    private static void forceArenaChunks(ServerLevel level, Session session) {
        int minChunkX = (session.origin.getX() - 32) >> 4;
        int maxChunkX = (session.origin.getX() + 32) >> 4;
        int minChunkZ = (session.origin.getZ() - 32) >> 4;
        int maxChunkZ = (session.origin.getZ() + 32) >> 4;
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

    private static String printableTick(long tick) {
        return tick < 0L ? "NOT_OPEN" : Long.toString(tick);
    }

    private static String nextExpected(Session session) {
        return switch (session.state) {
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
        private final UUID subjectId;
        private final long startTick;
        private final List<String> events = new ArrayList<>();
        private final List<ForcedChunk> ownedForcedChunks = new ArrayList<>();
        private int forcedChunksAcquired;
        private int forcedChunksReleased;
        private State state;
        private String reason = "fixture prepared";
        private long gate0Tick = -1L;
        private long dayTransitionTick = -1L;
        private long openingTick = -1L;
        private long terminalTick = -1L;
        private int contaminantsRemoved;
        private boolean midpointRecorded;
        private boolean terminalTransitionRecorded;
        private boolean eventsTruncated;
        private String lastTransitionKey = "";
        private List<String> openingEvidence = List.of();
        private String terminalSnapshot = "UNAVAILABLE";
        private List<String> terminalEvidence = List.of();
        private V3CampaignProgress progress;

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
            long gate0Tick,
            long dayTransitionTick,
            long windowOpenTick,
            long terminalTick,
            int contaminantsRemoved,
            int forcedChunksAcquired,
            int forcedChunksReleased,
            int forcedChunksRemaining,
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
                    session.gate0Tick,
                    session.dayTransitionTick,
                    session.openingTick,
                    session.terminalTick,
                    session.contaminantsRemoved,
                    session.forcedChunksAcquired,
                    session.forcedChunksReleased,
                    session.ownedForcedChunks.size(),
                    session.openingEvidence,
                    session.terminalSnapshot,
                    session.terminalEvidence,
                    session.events,
                    session.eventsTruncated);
        }

        static CampaignReport failedBeforeSubject(
                V3CampaignScenario scenario,
                ServerLevel level,
                BlockPos origin,
                State state,
                String reason) {
            return new CampaignReport(
                    scenario, state, reason, null, level.dimension().location().toString(),
                    origin, level.getGameTime(), -1L, -1L, -1L, level.getGameTime(), 0, 0, 0, 0,
                    List.of(), "UNAVAILABLE", List.of(), List.of(), false);
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
                    + " Gate0Tick=" + printableTick(gate0Tick)
                    + " dayTransitionTick=" + printableTick(dayTransitionTick)
                    + " exactOpeningTick=" + printableTick(windowOpenTick)
                    + " terminalTick=" + printableTick(terminalTick));
            out.add("contaminantsRemovedPreWindow=" + contaminantsRemoved);
            out.add("fixtureForcedChunks acquired=" + forcedChunksAcquired
                    + " released=" + forcedChunksReleased
                    + " remaining=" + forcedChunksRemaining);
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
