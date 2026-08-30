package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.goal.SeekShelterGoal;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.village.KnownVillager;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.TradeOutputCapability;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.intent.VillageIntent;
import com.noobk.spmscavenger.village.intent.VillageIntentRegistry;
import com.noobk.spmscavenger.village.interaction.VillageInteractionDirector;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Validation-only V4-G controller. Fixture methods mutate declared setup only; every product
 * decision, path, transaction, sleep and home write remains in production.
 */
public final class V4RuntimeCampaignController {

    private static final String LOG_PREFIX = "[spmscavenger/v4-campaign]";
    private static final String INTERRUPTER_TAG = "spm_v4.interrupter";
    private static final int INITIAL_PRICE = 8;
    private static final int CHANGED_PRICE = 10;
    private static final int DEPARTURE_OFFSET = 180;
    private static final long BOOTSTRAP_LIMIT = 2_400L;
    private static final long STARTUP_STABILITY_LIMIT = 20L;
    private static final long PHASE_A_LIMIT = 2_400L;
    private static final long PHASE_B_LIMIT = 2_400L;
    private static final int MAX_EVENTS = 96;

    private static Session active;
    private static CampaignReport lastReport;

    enum State {
        PREPARING,
        WAITING_STARTUP_STABILITY,
        WAITING_SETTLEMENT_AND_INITIAL_BOARD,
        PHASE_A_WAITING_INTENT,
        PHASE_A_COMMUTING,
        PHASE_A_WAITING_LIVE_TRADE,
        PHASE_B_PREPARING,
        PHASE_B_WAITING_REAL_SLEEP,
        PASS,
        FAIL,
        INCOMPLETE,
        FIXTURE_FAILURE,
        ABORTED
    }

    private V4RuntimeCampaignController() {
    }

    public static synchronized int run(CommandSourceStack source) {
        if (active != null) {
            source.sendFailure(Component.literal("A V4-G campaign is already active."));
            return 0;
        }
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("V4-G must start in the Overworld."));
            return 0;
        }
        lastReport = null;
        V4RuntimeWitnessTracker.reset();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        Session preparing = new Session(level.dimension(), origin, level.getGameTime());
        active = preparing;
        try {
            ScavengerConfig config = ScavengerConfig.get();
            preparing.configSummary = configSummary(config);
            validateFixtureConfig(config);
            executeFixtureFunction(source.getServer(), fixtureSource(source.getServer(), origin),
                    "scenario/v4_g");
            preparing.geometryFunctionExecuted = true;
            V4FixtureEntityFactory.VerifiedFixture fixture =
                    V4FixtureEntityFactory.createAndVerify(
                            level, origin, preparing.fixtureCreationDiagnostics);
            Mob subject = fixture.subject();
            Villager trader = fixture.trader();
            Container backpack = PlayerMobs.backpack(subject);
            if (backpack == null) {
                throw new IllegalStateException("fixture PlayerMob backpack unavailable");
            }
            preparing.subjectId = subject.getUUID();
            preparing.traderId = trader.getUUID();
            preparing.helperId = fixture.helper().getUUID();
            preparing.fixtureCreationTick = level.getGameTime();
            preparing.subjectTickCountAtCreation = subject.tickCount;
            preparing.backpackIdentity = backpack;
            prepareSubjectInventory(subject, backpack, false);
            configureOffer(trader, INITIAL_PRICE);
            preparing.initialOffer = V4OfferFingerprint.of(trader.getOffers().getFirst());
            preparing.changedOffer = fingerprintForPrice(CHANGED_PRICE);
            forceCorridorChunks(level, preparing);
            preparing.state = State.WAITING_STARTUP_STABILITY;
            preparing.startupStabilityDeadline =
                    level.getGameTime() + STARTUP_STABILITY_LIMIT;
            record(preparing, level.getGameTime(), "START",
                    "origin=" + origin.toShortString() + " subject=" + subject.getUUID()
                            + " trader=" + trader.getUUID()
                            + " helper=" + fixture.helper().getUUID()
                            + " fixtureAttachmentGate=PASS");
            source.sendSuccess(() -> Component.literal(
                    "V4-G preparation started. Use /spmscavenger debug v4 status or report."), false);
            return 1;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            SpmScavenger.LOGGER.error("{} event=STARTUP_FAILURE", LOG_PREFIX, failure);
            finish(source.getServer(), preparing, State.FIXTURE_FAILURE,
                    level.getGameTime(), "startup exception=" + concise(failure));
            sendLines(source, lastReport.lines());
            return 0;
        }
    }

    public static synchronized int status(CommandSourceStack source) {
        if (active == null) {
            if (lastReport == null) {
                source.sendFailure(Component.literal("No V4-G campaign or report is available."));
                return 0;
            }
            sendLines(source, lastReport.summaryLines());
            return 1;
        }
        sendLines(source, activeLines(active));
        return 1;
    }

    public static synchronized int report(CommandSourceStack source) {
        if (active != null) {
            sendLines(source, CampaignReport.from(active, V4RuntimeWitnessTracker.snapshot(),
                    V4RuntimeWitnessTracker.events()).lines());
            return 1;
        }
        if (lastReport == null) {
            source.sendFailure(Component.literal("No V4-G campaign report is available."));
            return 0;
        }
        sendLines(source, lastReport.lines());
        return 1;
    }

    public static synchronized int stop(CommandSourceStack source) {
        if (active == null) {
            source.sendFailure(Component.literal("No active V4-G campaign to stop."));
            return 0;
        }
        finish(source.getServer(), active, State.ABORTED,
                source.getLevel().getGameTime(), "operator stopped campaign");
        source.sendSuccess(() -> Component.literal(
                "V4-G stopped; use reset to remove exact tagged fixture entities."), false);
        return 1;
    }

    public static synchronized int reset(CommandSourceStack source) {
        BlockPos origin = active != null ? active.origin
                : lastReport != null ? lastReport.origin : BlockPos.containing(source.getPosition());
        if (active != null) {
            finish(source.getServer(), active, State.ABORTED,
                    source.getLevel().getGameTime(), "operator reset campaign");
        }
        try {
            executeFixtureFunction(source.getServer(), fixtureSource(source.getServer(), origin),
                    "cleanup");
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            source.sendFailure(Component.literal("V4-G cleanup failed: " + concise(failure)));
            return 0;
        } finally {
            V4RuntimeWitnessTracker.reset();
            active = null;
        }
        lastReport = null;
        source.sendSuccess(() -> Component.literal(
                "V4-G state reset; exact tagged entities removed, placed blocks preserved."), false);
        return 1;
    }

    public static synchronized void onServerTick(MinecraftServer server) {
        Session session = active;
        if (session == null) {
            return;
        }
        try {
            tick(server, session);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            SpmScavenger.LOGGER.error("{} event=TICK_FAILURE state={}",
                    LOG_PREFIX, session.state, failure);
            finish(server, session, State.FIXTURE_FAILURE, currentTick(server, session),
                    "controller exception=" + concise(failure));
        }
    }

    public static synchronized void onSubjectUnavailable(
            MinecraftServer server, UUID mobId, String reason, long tick) {
        if (active != null && active.subjectId != null && active.subjectId.equals(mobId)) {
            boolean beforeStability = !active.startupStabilityPassed;
            if (beforeStability) {
                active.preBehaviorFailureClass = "CREATION_INITIAL_LIFECYCLE";
            }
            finish(server, active,
                    beforeStability ? State.FIXTURE_FAILURE : State.INCOMPLETE,
                    tick, (beforeStability
                            ? "creation/initial-lifecycle failure: "
                            : "subject unavailable after startup stability: ") + reason);
        }
    }

    public static synchronized void onSubjectDeath(
            MinecraftServer server, Mob subject, DamageSource damageSource, long tick) {
        if (active == null || active.subjectId == null
                || !active.subjectId.equals(subject.getUUID())) {
            return;
        }
        ServerLevel level = server.getLevel(active.dimension);
        if (level == null) {
            active.preBehaviorFailureClass = active.startupStabilityPassed
                    ? "POST_STABILITY_UNAVAILABLE" : "CREATION_INITIAL_LIFECYCLE";
            finish(server, active,
                    active.startupStabilityPassed ? State.INCOMPLETE : State.FIXTURE_FAILURE,
                    tick, "subject death callback dimension unavailable");
            return;
        }
        active.deathDiagnostics = V4SubjectDeathDiagnostics.capture(
                level, subject, damageSource, active.fixtureCreationTick, tick);
        record(active, tick, "SUBJECT_DEATH",
                "damageType=" + active.deathDiagnostics.damageType()
                        + " messageId=" + active.deathDiagnostics.damageMessageId()
                        + " ticksSinceFixtureCreation="
                        + active.deathDiagnostics.ticksSinceFixtureCreation());
        boolean beforeStability = !active.startupStabilityPassed;
        active.preBehaviorFailureClass = beforeStability
                ? "CREATION_INITIAL_LIFECYCLE" : "POST_STABILITY_SUBJECT_DEATH";
        finish(server, active,
                beforeStability ? State.FIXTURE_FAILURE : State.INCOMPLETE,
                tick, beforeStability
                        ? "creation/initial-lifecycle failure: subject died before startup stability"
                        : "subject died after startup stability");
    }

    public static synchronized void shutdownServerState(MinecraftServer server) {
        if (active != null) {
            finish(server, active, State.ABORTED, currentTick(server, active), "server stopped");
        }
        V4RuntimeWitnessTracker.reset();
    }

    private static void tick(MinecraftServer server, Session session) {
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) {
            finish(server, session, State.INCOMPLETE, session.startTick, "dimension unavailable");
            return;
        }
        long now = level.getGameTime();
        if (session.state == State.WAITING_STARTUP_STABILITY) {
            tickStartupStability(server, level, session, now);
            return;
        }
        Entity entity = level.getEntity(session.subjectId);
        Entity merchant = level.getEntity(session.traderId);
        if (!(entity instanceof Mob subject) || !PlayerMobs.isPlayerMob(subject)
                || !(merchant instanceof Villager trader)) {
            finish(server, session, State.INCOMPLETE, now, "fixture subject or trader unavailable");
            return;
        }
        Container backpack = PlayerMobs.backpack(subject);
        if (backpack == null || backpack != session.backpackIdentity) {
            finish(server, session, State.FIXTURE_FAILURE, now,
                    "subject backpack identity changed/unavailable");
            return;
        }

        observeLiveDemand(level, subject, backpack, now);
        if (hasHome(level, subject.getUUID()) && session.state.ordinal() < State.PHASE_B_PREPARING.ordinal()) {
            finish(server, session, State.FAIL, now,
                    "homeBeforeTrade became present; V1.5 return would contaminate causality");
            return;
        }

        switch (session.state) {
            case WAITING_SETTLEMENT_AND_INITIAL_BOARD ->
                    tickBootstrap(server, level, subject, trader, backpack, session, now);
            case PHASE_A_WAITING_INTENT, PHASE_A_COMMUTING, PHASE_A_WAITING_LIVE_TRADE ->
                    tickPhaseA(server, level, subject, trader, backpack, session, now);
            case PHASE_B_PREPARING -> preparePhaseB(server, level, subject, session, now);
            case PHASE_B_WAITING_REAL_SLEEP -> tickPhaseB(server, level, subject, session, now);
            default -> { }
        }
    }

    private static void tickStartupStability(
            MinecraftServer server, ServerLevel level, Session session, long now) {
        Entity subjectEntity = level.getEntity(session.subjectId);
        Entity traderEntity = level.getEntity(session.traderId);
        Entity helperEntity = level.getEntity(session.helperId);
        boolean subjectReady = subjectEntity instanceof Mob subject
                && PlayerMobs.isPlayerMob(subject) && subject.isAlive() && !subject.isRemoved();
        boolean traderReady = traderEntity instanceof Villager trader
                && trader.isAlive() && !trader.isRemoved();
        boolean helperReady = helperEntity instanceof Villager helper
                && helper.isAlive() && !helper.isRemoved();
        int subjectTickCount = subjectEntity == null
                ? session.subjectTickCountAtCreation : subjectEntity.tickCount;
        V4StartupStabilityGate.Assessment assessment = V4StartupStabilityGate.evaluate(
                session.fixtureCreationTick, now, session.startupStabilityDeadline,
                session.subjectTickCountAtCreation, subjectTickCount,
                subjectReady, traderReady, helperReady);
        switch (assessment.verdict()) {
            case WAITING -> session.reason = assessment.reason();
            case FIXTURE_FAILURE -> {
                session.preBehaviorFailureClass = "CREATION_INITIAL_LIFECYCLE";
                finish(server, session, State.FIXTURE_FAILURE, now,
                        "creation/initial-lifecycle failure: " + assessment.reason());
            }
            case PASS -> {
                Mob subject = (Mob) subjectEntity;
                Villager trader = (Villager) traderEntity;
                Container backpack = PlayerMobs.backpack(subject);
                if (backpack == null || backpack != session.backpackIdentity) {
                    session.preBehaviorFailureClass = "CREATION_INITIAL_LIFECYCLE";
                    finish(server, session, State.FIXTURE_FAILURE, now,
                            "creation/initial-lifecycle failure: backpack identity changed");
                    return;
                }
                session.startupStabilityPassed = true;
                session.startupStabilityPassTick = now;
                session.state = State.WAITING_SETTLEMENT_AND_INITIAL_BOARD;
                session.deadline = now + BOOTSTRAP_LIMIT;
                session.reason = "startup stability passed; waiting settlement and initial board";
                V4RuntimeWitnessTracker.arm(
                        subject.getUUID(), trader.getUUID(), backpack,
                        session.initialOffer, now);
                record(session, now, "STARTUP_STABILITY_PASS",
                        "fixtureCreationTick=" + session.fixtureCreationTick
                                + " subjectTickCount=" + subject.tickCount
                                + " traderAlive=true helperAlive=true");
            }
        }
    }

    private static void tickBootstrap(
            MinecraftServer server, ServerLevel level, Mob subject, Villager trader,
            Container backpack, Session session, long now) {
        if (!session.dayAdvanced && now - session.startTick >= 120L) {
            level.setDayTime(dayBase(level) + 1_000L);
            session.dayAdvanced = true;
            record(session, now, "DAY_ADVANCED", "dayTime=" + level.getDayTime());
        }
        Optional<MobVillageMemory> memory = memory(level, subject.getUUID());
        if (memory.isPresent() && memory.get().villages().size() == 1) {
            session.settlementAnchor = memory.get().villages().getFirst().anchor();
            double distance = horizontalDistance(session.settlementAnchor, trader.blockPosition());
            session.anchorTraderDistance = distance;
            if (distance > VillagerTradeAdapterDistance.localRadius()) {
                finish(server, session, State.FIXTURE_FAILURE, now,
                        "anchor-to-trader distance " + format(distance)
                                + " exceeds V2 local discovery radius 16");
                return;
            }
        }
        boolean capability = memory.filter(existing -> session.settlementAnchor != null
                        && existing.knownTradersAt(session.settlementAnchor).stream()
                        .flatMap(known -> known.activeCapabilities(now).stream())
                        .anyMatch(TradeOutputCapability.of(new ItemStack(Items.IRON_PICKAXE))::equals))
                .isPresent();
        V4RuntimeWitnessTracker.Snapshot witness = V4RuntimeWitnessTracker.snapshot();
        if (session.settlementAnchor != null && capability && witness.initialBoardObserved()
                && witness.routeStatus()
                        == ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE) {
            openPhaseA(level, subject, trader, backpack, session, now);
            return;
        }
        if (now >= session.deadline) {
            finish(server, session, State.INCOMPLETE, now,
                    "bootstrap timeout settlement=" + (session.settlementAnchor != null)
                            + " capability=" + capability
                            + " initialBoard=" + witness.initialBoardObserved()
                            + " route=" + witness.routeStatus());
        }
    }

    private static void openPhaseA(
            ServerLevel level, Mob subject, Villager trader, Container backpack,
            Session session, long now) {
        session.homeBeforeTrade = !hasHome(level, subject.getUUID());
        if (!session.homeBeforeTrade) {
            throw new IllegalStateException("Phase A cannot open with HOME");
        }
        configureOffer(trader, CHANGED_PRICE);
        V4OfferFingerprint changed = V4OfferFingerprint.of(trader.getOffers().getFirst());
        if (!changed.equals(session.changedOffer) || changed.equals(session.initialOffer)) {
            throw new IllegalStateException("changed live offer fingerprint mismatch");
        }
        V4RuntimeWitnessTracker.markChangedOffer(changed, now);
        prepareSubjectInventory(subject, backpack, true);
        BlockPos departure = session.origin.offset(DEPARTURE_OFFSET, 0, 0);
        subject.teleportTo(departure.getX() + 0.5, departure.getY(), departure.getZ() + 0.5);
        session.departure = departure;
        session.phaseAOpenTick = now;
        session.deadline = now + PHASE_A_LIMIT;
        session.state = State.PHASE_A_WAITING_INTENT;
        V4RuntimeWitnessTracker.openPhaseA(now);
        record(session, now, "PHASE_A_OPEN",
                "homeBeforeTrade=" + session.homeBeforeTrade
                        + " rememberedSettlement=" + session.settlementAnchor.toShortString()
                        + " anchorTraderDistance=" + format(session.anchorTraderDistance)
                        + " initial=" + session.initialOffer.compact()
                        + " changed=" + changed.compact()
                        + " departure=" + departure.toShortString());
    }

    private static void tickPhaseA(
            MinecraftServer server, ServerLevel level, Mob subject, Villager trader,
            Container backpack, Session session, long now) {
        V4RuntimeWitnessTracker.Snapshot witness = V4RuntimeWitnessTracker.snapshot();
        if (witness.cachedInitialOfferExecuted()) {
            finish(server, session, State.FAIL, now, "cached initial offer terms executed");
            return;
        }
        if (witness.routeFailurePublications() > 0) {
            finish(server, session, State.FAIL, now,
                    "interruption/commute published route-failure evidence");
            return;
        }
        if (witness.intentIdentity() != null && session.state == State.PHASE_A_WAITING_INTENT) {
            session.state = State.PHASE_A_COMMUTING;
            record(session, now, "INTENT_OPEN", witness.intentIdentity());
        }
        if (witness.commuteSeeded() && !session.interrupterAttempted
                && session.departure != null
                && horizontalDistance(session.departure, subject.blockPosition()) >= 8.0) {
            session.interrupterAttempted = true;
            spawnInterrupter(level, subject, session, now);
        }
        manageInterruption(level, subject, session, witness, now);
        if (witness.arrivalObserved()) {
            V4RuntimeWitnessTracker.stampArrival(now);
            session.state = State.PHASE_A_WAITING_LIVE_TRADE;
        }
        if (witness.changedOfferExecuted()) {
            int pickaxes = countAll(subject, backpack, Items.IRON_PICKAXE);
            int emeralds = countAll(subject, backpack, Items.EMERALD);
            if (!witness.changedBoardRediscovered() || !witness.intentReleasedAtArrival()
                    || pickaxes < 1 || emeralds != 0) {
                finish(server, session, State.FAIL, now,
                        "trade committed without complete changed-board/arrival/inventory evidence"
                                + " changedBoard=" + witness.changedBoardRediscovered()
                                + " released=" + witness.intentReleasedAtArrival()
                                + " pickaxes=" + pickaxes + " emeralds=" + emeralds);
                return;
            }
            session.phaseAPassTick = now;
            record(session, now, "PHASE_A_PASS",
                    "executed=" + witness.executedOffer().compact()
                            + " pickaxes=" + pickaxes + " emeralds=" + emeralds);
            session.state = State.PHASE_B_PREPARING;
            return;
        }
        if (now >= session.deadline) {
            finish(server, session, State.INCOMPLETE, now,
                    "Phase A timeout state=" + session.state
                            + " route=" + witness.routeStatus()
                            + " intent=" + witness.intentIdentity()
                            + " commute=" + witness.commuteSeeded()
                            + " arrival=" + witness.arrivalObserved()
                            + " changedRediscovery=" + witness.changedBoardRediscovered());
        }
    }

    private static void preparePhaseB(
            MinecraftServer server, ServerLevel level, Mob subject, Session session, long now) {
        VillageMemorySavedData data = VillageMemorySavedData.peekInDimension(level);
        Optional<MobVillageMemory> memory = data == null
                ? Optional.empty() : data.peek(subject.getUUID());
        if (memory.isEmpty() || session.settlementAnchor == null
                || memory.get().homeAnchor().isPresent()) {
            finish(server, session, State.FIXTURE_FAILURE, now,
                    "Phase B requires remembered settlement and absent HOME");
            return;
        }
        SettlementRelationship relationship = memory.get().relationshipAt(session.settlementAnchor)
                .orElseGet(SettlementRelationship::empty);
        int delta = Math.max(0, 600 - relationship.familiarityScore());
        relationship.bumpFamiliarity(delta, now);
        memory.get().putRelationship(session.settlementAnchor, relationship);
        data.setDirty();
        int associations = associatedFixtureBeds(session, memory.get());
        session.phaseBAssociationCount = associations;
        session.homeBeforeSleep = memory.get().homeAnchor().isEmpty();
        if (associations < 1 || !session.homeBeforeSleep
                || relationship.familiarityScore() < 600) {
            finish(server, session, State.FIXTURE_FAILURE, now,
                    "Phase B preflight familiarity=" + relationship.familiarityScore()
                            + " associatedFixtureBeds=" + associations
                            + " homeBeforeSleep=" + session.homeBeforeSleep);
            return;
        }
        level.setDayTime(dayBase(level) + 13_000L);
        session.phaseBOpenTick = now;
        session.deadline = now + PHASE_B_LIMIT;
        session.state = State.PHASE_B_WAITING_REAL_SLEEP;
        record(session, now, "PHASE_B_OPEN",
                "homeBeforeSleep=true familiarity=" + relationship.familiarityScore()
                        + " associatedSettlementCount=1 fixtureBeds=" + associations);
    }

    private static void tickPhaseB(
            MinecraftServer server, ServerLevel level, Mob subject, Session session, long now) {
        if (seekShelterRunning(subject)) {
            V4RuntimeWitnessTracker.observeSeekShelterRunning(subject.getUUID(), now);
        }
        Optional<MobVillageMemory> memory = memory(level, subject.getUUID());
        BlockPos home = memory.flatMap(MobVillageMemory::homeAnchor).orElse(null);
        V4RuntimeWitnessTracker.Snapshot witness = V4RuntimeWitnessTracker.snapshot();
        if (home != null && !home.equals(session.settlementAnchor)) {
            finish(server, session, State.FAIL, now,
                    "HOME designated to unexpected anchor " + home.toShortString());
            return;
        }
        if (subject.isSleeping() && home != null && home.equals(session.settlementAnchor)
                && witness.seekShelterObserved() && witness.homePromotionObserved()) {
            session.phaseBPassTick = now;
            record(session, now, "PHASE_B_PASS",
                    "sleeping=true homeAfterSleep=" + home.toShortString()
                            + " bed=" + witness.sleepBed());
            finish(server, session, State.PASS, now,
                    "Phase A REQUIRED_TRADE + changed live offer and Phase B first HOME passed");
            return;
        }
        if (now >= session.deadline) {
            finish(server, session, State.INCOMPLETE, now,
                    "Phase B timeout seekShelter=" + witness.seekShelterObserved()
                            + " sleeping=" + subject.isSleeping()
                            + " home=" + (home == null ? "absent" : home.toShortString())
                            + " promotionEvent=" + witness.homePromotionObserved());
        }
    }

    private static void observeLiveDemand(
            ServerLevel level, Mob subject, Container backpack, long now) {
        Optional<WorkDemandPolicy.MaterialDemand> demand = WorkDemandPolicy.select(
                        backpack, subject.getMainHandItem(), subject.getOffhandItem(),
                        ScavengerConfig.get())
                .map(WorkDemandPolicy.WorkDemand::payload);
        if (demand.isEmpty()) {
            return;
        }
        ExistingRouteFeasibility.ExistingRouteStatus status = ExistingRouteFeasibility.status(
                level, subject.getUUID(), demand.get(), backpack,
                subject.getMainHandItem(), subject.getOffhandItem(), ScavengerConfig.get());
        V4RuntimeWitnessTracker.observeDemand(demand.get().identity(), status, now);
    }

    private static void spawnInterrupter(
            ServerLevel level, Mob subject, Session session, long now) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            record(session, now, "INTERRUPTION_INCOMPLETE", "zombie creation unavailable");
            return;
        }
        zombie.moveTo(subject.getX() + 3.0, subject.getY(), subject.getZ(), 180.0F, 0.0F);
        zombie.addTag(INTERRUPTER_TAG);
        zombie.addTag("spm_v4.fixture");
        zombie.setPersistenceRequired();
        zombie.setTarget(subject);
        if (!level.addFreshEntity(zombie)) {
            record(session, now, "INTERRUPTION_INCOMPLETE", "zombie add failed");
            return;
        }
        session.interrupterId = zombie.getUUID();
        session.interrupterSpawnTick = now;
        record(session, now, "INTERRUPTER_SPAWNED", "uuid=" + zombie.getUUID());
    }

    private static void manageInterruption(
            ServerLevel level, Mob subject, Session session,
            V4RuntimeWitnessTracker.Snapshot witness, long now) {
        Optional<VillageIntent> intent = VillageIntentRegistry.current(subject.getUUID());
        if (session.interrupterId != null && subject.getTarget() != null && intent.isPresent()
                && !session.interruptionObserved) {
            session.interruptionObserved = true;
            session.interruptionTick = now;
            V4RuntimeWitnessTracker.observeInterruption(subject.getUUID(), intent.get(), now);
            record(session, now, "INTERRUPTION_OBSERVED",
                    "target=" + subject.getTarget().getUUID());
        }
        if (session.interrupterId != null
                && (session.interruptionObserved && now - session.interruptionTick >= 40L
                        || now - session.interrupterSpawnTick >= 200L)) {
            Entity interrupter = level.getEntity(session.interrupterId);
            if (interrupter != null && interrupter.getTags().contains(INTERRUPTER_TAG)) {
                interrupter.discard();
            }
            session.interrupterId = null;
            if (!session.interruptionObserved) {
                record(session, now, "INTERRUPTION_INCOMPLETE",
                        "no natural subject target acquired within 200 ticks");
            }
        }
        if (session.interruptionObserved && !session.resumeObserved
                && subject.getTarget() == null && intent.isPresent()) {
            V4RuntimeWitnessTracker.observeResume(subject.getUUID(), intent.get(), now);
            session.resumeObserved = V4RuntimeWitnessTracker.snapshot().sameBindingResumed();
            if (session.resumeObserved) {
                record(session, now, "RESUME_OBSERVED", "sameBinding=true");
            }
        }
    }

    private static void prepareSubjectInventory(Mob subject, Container backpack, boolean funded) {
        backpack.clearContent();
        backpack.setItem(0, new ItemStack(Items.STICK, 2));
        backpack.setItem(1, new ItemStack(
                Items.TORCH, Math.max(8, ScavengerConfig.get().torchStockTarget)));
        backpack.setItem(2, new ItemStack(Items.DIAMOND_AXE));
        if (funded) {
            backpack.setItem(3, new ItemStack(Items.EMERALD, CHANGED_PRICE));
        }
        subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        subject.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static void configureOffer(Villager trader, int emeraldCost) {
        trader.setVillagerData(trader.getVillagerData()
                .setProfession(VillagerProfession.TOOLSMITH).setLevel(2));
        MerchantOffers board = trader.getOffers();
        board.clear();
        board.add(offer(emeraldCost));
    }

    private static MerchantOffer offer(int emeraldCost) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeraldCost), Optional.empty(),
                new ItemStack(Items.IRON_PICKAXE), 12, 0, 0.05F);
    }

    private static V4OfferFingerprint fingerprintForPrice(int emeraldCost) {
        return V4OfferFingerprint.of(offer(emeraldCost));
    }

    private static void validateFixtureConfig(ScavengerConfig config) {
        if (!config.enabled || !config.gatherResources || !config.craftTools
                || !config.seekShelter || !config.sleepInBeds) {
            throw new IllegalStateException(
                    "fixture requires enabled/gatherResources/craftTools/seekShelter/sleepInBeds");
        }
        if (config.maxPickTier.compareTo(ToolTier.IRON) < 0) {
            throw new IllegalStateException("maxPickTier must be IRON or DIAMOND");
        }
        if (config.gatherSearchRadius <= 0.0 || config.gatherSearchRadius > 20.0) {
            throw new IllegalStateException(
                    "gatherSearchRadius must be >0 and <=20 for the cleared scan volume");
        }
        if (config.shelterSearchRadius < 6.0) {
            throw new IllegalStateException("shelterSearchRadius must be >=6 for fixture beds");
        }
        if (config.torchStockTarget > 64) {
            throw new IllegalStateException("torchStockTarget must be <=64 for fixture inventory");
        }
    }

    private static String configSummary(ScavengerConfig config) {
        return "enabled=" + config.enabled
                + " gatherResources=" + config.gatherResources
                + " gatherSearchRadius=" + config.gatherSearchRadius
                + " craftTools=" + config.craftTools
                + " maxPickTier=" + config.maxPickTier
                + " torchStockTarget=" + config.torchStockTarget
                + " seekShelter=" + config.seekShelter
                + " sleepInBeds=" + config.sleepInBeds
                + " shelterSearchRadius=" + config.shelterSearchRadius;
    }

    private static Optional<MobVillageMemory> memory(ServerLevel level, UUID mobId) {
        VillageMemorySavedData data = VillageMemorySavedData.peekInDimension(level);
        return data == null ? Optional.empty() : data.peek(mobId);
    }

    private static boolean hasHome(ServerLevel level, UUID mobId) {
        return memory(level, mobId).flatMap(MobVillageMemory::homeAnchor).isPresent();
    }

    private static int associatedFixtureBeds(Session session, MobVillageMemory memory) {
        return (int) memory.villages().stream()
                .filter(village -> fixtureBeds(session).stream()
                        .anyMatch(bed -> SettlementBoundsPolicy.within(bed, village.anchor())))
                .count();
    }

    private static List<BlockPos> fixtureBeds(Session session) {
        return List.of(session.origin.offset(-4, 0, 1),
                session.origin.offset(-7, 0, 1), session.origin.offset(-10, 0, 1));
    }

    private static boolean seekShelterRunning(Mob subject) {
        return ((MobGoalSelectorAccessor) subject).spmscavenger$getGoalSelector()
                .getAvailableGoals().stream()
                .anyMatch(WrappedGoal::isRunning)
                && ((MobGoalSelectorAccessor) subject).spmscavenger$getGoalSelector()
                .getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .map(WrappedGoal::getGoal)
                .anyMatch(SeekShelterGoal.class::isInstance);
    }

    private static int countAll(Mob subject, Container backpack, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack stack = backpack.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        if (subject.getMainHandItem().is(item)) count += subject.getMainHandItem().getCount();
        if (subject.getOffhandItem().is(item)) count += subject.getOffhandItem().getCount();
        return count;
    }

    private static void forceCorridorChunks(ServerLevel level, Session session) {
        int minX = (session.origin.getX() - 32) >> 4;
        int maxX = (session.origin.getX() + DEPARTURE_OFFSET + 32) >> 4;
        int minZ = (session.origin.getZ() - 32) >> 4;
        int maxZ = (session.origin.getZ() + 32) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.setChunkForced(x, z, true)) {
                    session.forcedChunks.add(new ChunkPos(x, z));
                }
            }
        }
    }

    private static int releaseChunks(MinecraftServer server, Session session) {
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) return 0;
        int released = 0;
        for (ChunkPos chunk : session.forcedChunks) {
            if (level.setChunkForced(chunk.x, chunk.z, false)) released++;
        }
        session.forcedChunks.clear();
        return released;
    }

    private static void executeFixtureFunction(
            MinecraftServer server, CommandSourceStack source, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("spm_v4", path);
        CommandFunction<CommandSourceStack> function = server.getFunctions().get(id)
                .orElseThrow(() -> new IllegalStateException("fixture function not loaded: " + id));
        server.getFunctions().execute(function, source);
    }

    private static CommandSourceStack fixtureSource(MinecraftServer server, BlockPos origin) {
        return server.createCommandSourceStack().withLevel(server.overworld())
                .withPosition(Vec3.atLowerCornerOf(origin)).withSuppressedOutput();
    }

    private static void finish(
            MinecraftServer server, Session session, State state, long tick, String reason) {
        session.state = state;
        session.reason = reason;
        session.terminalTick = tick;
        record(session, tick, "FINAL", "state=" + state + " reason=" + reason);
        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        List<String> witnessEvents = V4RuntimeWitnessTracker.events();
        session.forcedChunksReleased += releaseChunks(server, session);
        lastReport = CampaignReport.from(session, snapshot, witnessEvents);
        active = null;
        V4RuntimeWitnessTracker.reset();
    }

    private static void record(Session session, long tick, String event, String detail) {
        if (session.events.size() >= MAX_EVENTS) return;
        String line = "tick=" + tick + " event=" + event + " " + detail;
        session.events.add(line);
        SpmScavenger.LOGGER.info("{} {}", LOG_PREFIX, line);
    }

    private static List<String> activeLines(Session session) {
        V4RuntimeWitnessTracker.Snapshot witness = V4RuntimeWitnessTracker.snapshot();
        List<String> lines = new ArrayList<>(List.of(
                "=== V4-G Runtime Campaign Status ===",
                "state=" + session.state + " reason=" + session.reason,
                "subject=" + session.subjectId + " trader=" + session.traderId
                        + " helper=" + session.helperId,
                "config=" + session.configSummary,
                "homeBeforeTrade=" + (session.homeBeforeTrade ? "ABSENT" : "PRESENT"),
                "rememberedSettlement=" + printable(session.settlementAnchor),
                "anchorTraderDistance=" + format(session.anchorTraderDistance),
                "initialOfferFingerprint=" + printable(witness.initialOffer()),
                "changedLiveOfferFingerprint=" + printable(witness.changedOffer()),
                "liveDemandIdentity=" + witness.demandIdentity(),
                "ExistingRouteStatus=" + witness.routeStatus(),
                "VillageIntent=" + witness.intentIdentity(),
                "COMMUTE source=" + witness.commuteSource(),
                "next=" + next(session)));
        lines.addAll(session.fixtureCreationDiagnostics.lines());
        return List.copyOf(lines);
    }

    private static String next(Session session) {
        return switch (session.state) {
            case WAITING_SETTLEMENT_AND_INITIAL_BOARD ->
                    "natural settlement + Gather exhaustion + V2 initial board observation";
            case WAITING_STARTUP_STABILITY ->
                    "first normal subject/server tick with all required entities alive";
            case PHASE_A_WAITING_INTENT -> "production REQUIRED_TRADE intent/directive";
            case PHASE_A_COMMUTING -> "physical return, optional interruption/resume, anchor arrival";
            case PHASE_A_WAITING_LIVE_TRADE -> "V2 changed-board rediscovery and live transaction";
            case PHASE_B_PREPARING -> "fixture familiarity/night preparation";
            case PHASE_B_WAITING_REAL_SLEEP -> "SeekShelterGoal real sleep and first HOME";
            default -> "report/reset";
        };
    }

    private static void sendLines(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private static long currentTick(MinecraftServer server, Session session) {
        ServerLevel level = server.getLevel(session.dimension);
        return level == null ? session.startTick : level.getGameTime();
    }

    private static long dayBase(ServerLevel level) {
        return Math.floorDiv(level.getDayTime(), 24_000L) * 24_000L;
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private static String format(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "UNAVAILABLE";
    }

    private static String printable(Object value) {
        return value == null ? "UNAVAILABLE" : value.toString();
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static final class VillagerTradeAdapterDistance {
        static double localRadius() { return 16.0D; }
    }

    private static final class Session {
        final ResourceKey<Level> dimension;
        final BlockPos origin;
        final long startTick;
        final List<String> events = new ArrayList<>();
        final Set<ChunkPos> forcedChunks = new LinkedHashSet<>();
        State state = State.PREPARING;
        String reason = "preparing";
        String configSummary = "UNREAD";
        UUID subjectId;
        UUID traderId;
        UUID helperId;
        Object backpackIdentity;
        BlockPos settlementAnchor;
        BlockPos departure;
        V4OfferFingerprint initialOffer;
        V4OfferFingerprint changedOffer;
        long deadline;
        long phaseAOpenTick = -1L;
        long phaseAPassTick = -1L;
        long phaseBOpenTick = -1L;
        long phaseBPassTick = -1L;
        long terminalTick = -1L;
        long fixtureCreationTick = -1L;
        long startupStabilityDeadline = -1L;
        long startupStabilityPassTick = -1L;
        int subjectTickCountAtCreation;
        boolean startupStabilityPassed;
        String preBehaviorFailureClass = "NONE";
        V4SubjectDeathDiagnostics deathDiagnostics;
        boolean dayAdvanced;
        boolean homeBeforeTrade;
        boolean homeBeforeSleep;
        double anchorTraderDistance = Double.NaN;
        int phaseBAssociationCount;
        UUID interrupterId;
        boolean interrupterAttempted;
        long interrupterSpawnTick;
        boolean interruptionObserved;
        long interruptionTick;
        boolean resumeObserved;
        int forcedChunksReleased;
        boolean geometryFunctionExecuted;
        final V4FixtureEntityFactory.Diagnostics fixtureCreationDiagnostics =
                new V4FixtureEntityFactory.Diagnostics();

        Session(ResourceKey<Level> dimension, BlockPos origin, long startTick) {
            this.dimension = dimension;
            this.origin = origin.immutable();
            this.startTick = startTick;
        }
    }

    private record CampaignReport(
            State state,
            String reason,
            UUID subjectId,
            UUID traderId,
            UUID helperId,
            String dimension,
            BlockPos origin,
            BlockPos settlementAnchor,
            double anchorTraderDistance,
            boolean homeBeforeTrade,
            boolean homeBeforeSleep,
            int phaseBAssociationCount,
            long startTick,
            long phaseAOpenTick,
            long phaseAPassTick,
            long phaseBOpenTick,
            long phaseBPassTick,
            long terminalTick,
            long fixtureCreationTick,
            long startupStabilityPassTick,
            boolean startupStabilityPassed,
            String preBehaviorFailureClass,
            List<String> deathDiagnostics,
            int forcedChunksReleased,
            boolean geometryFunctionExecuted,
            String fixtureFailureStage,
            String fixtureFailureDetail,
            List<String> fixtureCreationDiagnostics,
            String configSummary,
            V4RuntimeWitnessTracker.Snapshot witness,
            List<String> controllerEvents,
            List<String> witnessEvents) {

        static CampaignReport from(
                Session session, V4RuntimeWitnessTracker.Snapshot witness,
                List<String> witnessEvents) {
            return new CampaignReport(session.state, session.reason, session.subjectId,
                    session.traderId, session.helperId,
                    session.dimension.location().toString(), session.origin,
                    session.settlementAnchor, session.anchorTraderDistance,
                    session.homeBeforeTrade, session.homeBeforeSleep,
                    session.phaseBAssociationCount, session.startTick, session.phaseAOpenTick,
                    session.phaseAPassTick, session.phaseBOpenTick, session.phaseBPassTick,
                    session.terminalTick, session.fixtureCreationTick,
                    session.startupStabilityPassTick, session.startupStabilityPassed,
                    session.preBehaviorFailureClass,
                    session.deathDiagnostics == null
                            ? List.of("deathDiagnostics=UNAVAILABLE")
                            : session.deathDiagnostics.lines(),
                    session.forcedChunksReleased,
                    session.geometryFunctionExecuted,
                    session.fixtureCreationDiagnostics.failureStage,
                    session.fixtureCreationDiagnostics.failureDetail,
                    session.fixtureCreationDiagnostics.lines(), session.configSummary, witness,
                    List.copyOf(session.events), List.copyOf(witnessEvents));
        }

        List<String> summaryLines() {
            return List.of(
                    "=== V4-G Runtime Campaign Report ===",
                    "state=" + state + " reason=" + reason,
                    "PhaseA=" + (phaseAPassTick >= 0 ? "PASS" : "NOT_PASS")
                            + " PhaseB=" + (phaseBPassTick >= 0 ? "PASS" : "NOT_PASS"),
                    "fixtureEntityPreflight="
                            + ("NONE".equals(fixtureFailureStage) ? "PASS" : "FAIL")
                            + " stage=" + fixtureFailureStage
                            + " detail=" + fixtureFailureDetail,
                    "startupStability=" + (startupStabilityPassed ? "PASS" : "NOT_PASS")
                            + " preBehaviorFailureClass=" + preBehaviorFailureClass,
                    "VERDICT=" + state);
        }

        List<String> lines() {
            List<String> lines = new ArrayList<>(summaryLines());
            lines.add("environment=minecraft:1.21.1 spm:0.96.0 dimension=" + dimension);
            lines.add("config=" + configSummary);
            lines.add("subject=" + subjectId + " trader=" + traderId + " helper=" + helperId
                    + " origin=" + origin.toShortString());
            lines.add("geometryFunctionExecuted=" + geometryFunctionExecuted);
            lines.add("-- fixture entity creation preflight --");
            lines.addAll(fixtureCreationDiagnostics);
            lines.add("rememberedSettlement=" + printable(settlementAnchor)
                    + " anchorTraderDistance=" + format(anchorTraderDistance));
            lines.add("ticks start=" + startTick + " phaseAOpen=" + phaseAOpenTick
                    + " phaseAPass=" + phaseAPassTick + " phaseBOpen=" + phaseBOpenTick
                    + " phaseBPass=" + phaseBPassTick + " terminal=" + terminalTick);
            lines.add("fixtureCreationTick=" + fixtureCreationTick
                    + " startupStabilityPassTick=" + startupStabilityPassTick);
            lines.add("-- subject death diagnostics --");
            lines.addAll(deathDiagnostics);
            lines.add("homeBeforeTrade=" + (homeBeforeTrade ? "ABSENT" : "PRESENT")
                    + " homeBeforeSleep=" + (homeBeforeSleep ? "ABSENT" : "PRESENT")
                    + " phaseBAssociatedSettlementCount="
                    + (phaseBAssociationCount > 0 ? 1 : 0));
            lines.add("initialOfferFingerprint=" + printable(witness.initialOffer()));
            lines.add("changedLiveOfferFingerprint=" + printable(witness.changedOffer()));
            lines.add("executedOfferFingerprint=" + printable(witness.executedOffer()));
            lines.add("liveDemandIdentity=" + witness.demandIdentity()
                    + " ExistingRouteStatus=" + witness.routeStatus());
            lines.add("selectedDestination=" + printable(settlementAnchor));
            lines.add("VillageIntent=" + witness.intentIdentity()
                    + " CommuteDirectiveAdmission=" + witness.commuteSeeded()
                    + " COMMUTE source=" + witness.commuteSource());
            lines.add("interrupted=" + witness.interrupted()
                    + " navigationDiscarded=" + witness.navigationDiscarded()
                    + " resumed=" + witness.resumed()
                    + " sameBinding=" + witness.sameBindingResumed()
                    + " routeFailurePublications=" + witness.routeFailurePublications());
            lines.add("arrival=" + witness.arrivalObserved()
                    + " intentRelease=" + witness.intentReleasedAtArrival()
                    + " changedBoardRediscovery=" + witness.changedBoardRediscovered());
            lines.add("changedOfferExecuted=" + witness.changedOfferExecuted()
                    + " cachedInitialOfferExecuted=" + witness.cachedInitialOfferExecuted());
            lines.add("SeekShelterGoalRunning=" + witness.seekShelterObserved()
                    + " sleeping=" + witness.sleepingObserved()
                    + " homePromotion=" + witness.homePromotionObserved()
                    + " bedPos=" + witness.sleepBed());
            lines.add("forcedChunksReleased=" + forcedChunksReleased);
            lines.add("-- controller transitions --");
            lines.addAll(controllerEvents);
            lines.add("-- passive witness transitions --");
            lines.addAll(witnessEvents);
            return List.copyOf(lines);
        }
    }
}
