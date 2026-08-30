package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.Villager;

/**
 * Bounded, validation-only observation ledger for the V4-G bootstrap trade liveness boundary.
 *
 * <p>The API intentionally contains only observations of already-running production calls. It has
 * no reference to a {@code GoalSelector} mutator, route publisher, trade planner, offer accessor or
 * cooldown mutator. One armed subject is retained and reset with the owning V4 campaign.
 */
public final class V4TradeLivenessWitness {

    private static final int MAX_EVENTS = 96;
    private static final ThreadLocal<UUID> TRADE_INVOCATION = new ThreadLocal<>();
    private static final ThreadLocal<UUID> MARKET_DISCOVERY = new ThreadLocal<>();
    private static final ThreadLocal<UUID> GATHER_INVOCATION = new ThreadLocal<>();
    private static Session active;

    public enum Diagnosis {
        TRADE_NOT_SCHEDULED,
        TRADE_EARLY_GATE_REJECTED,
        TRADE_MARKET_DISCOVERY_EMPTY,
        TRADE_ADMITTED_NOT_STARTED,
        TRADE_STARTED_NO_BOARD,
        TRADE_PREEMPTED,
        UNKNOWN
    }

    private V4TradeLivenessWitness() {
    }

    static synchronized void arm(
            UUID mobId, UUID traderId, Object backpackIdentity, long tick) {
        active = new Session(mobId, traderId, backpackIdentity);
        event(tick, "LIVENESS_ARMED", "bounded=true passive=true");
    }

    public static synchronized boolean matchesMob(UUID mobId) {
        return active != null && active.mobId.equals(mobId);
    }

    public static synchronized boolean matchesTrader(UUID traderId) {
        return active != null && active.traderId.equals(traderId);
    }

    public static synchronized void enterTradeCanUse(UUID mobId, Object cooldown, long tick) {
        if (!matchesMob(mobId)) {
            return;
        }
        TRADE_INVOCATION.set(mobId);
        active.cooldownIdentity = cooldown;
        active.tradeCanUseCalls++;
        if (active.firstTradeCanUseTick < 0L) {
            active.firstTradeCanUseTick = tick;
        }
        active.lastTradeCanUseTick = tick;
    }

    public static synchronized void exitTradeCanUse(UUID mobId, boolean result, long tick) {
        if (matchesMob(mobId)) {
            if (result) {
                active.tradeCanUseTrue++;
            } else {
                active.tradeCanUseFalse++;
            }
            event(tick, "TRADE_CAN_USE", "result=" + result);
        }
        TRADE_INVOCATION.remove();
    }

    public static synchronized boolean inTradeInvocation() {
        return active != null && (active.mobId.equals(TRADE_INVOCATION.get())
                || active.mobId.equals(MARKET_DISCOVERY.get()));
    }

    public static synchronized void observeTradeDemand(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, long tick) {
        if (!matchesMob(mobId) || !inTradeInvocation()) {
            return;
        }
        active.tradeGateDemandPresent = demand != null;
        if (demand != null) {
            active.tradeGateDemandIdentity = demand.identity();
        }
        demandTransition(demand == null ? null : demand.identity(), active.routeStatus, tick);
    }

    public static synchronized void observeTradeRouteGate(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, boolean mayDisplace, long tick) {
        if (!matchesMob(mobId) || demand == null) {
            return;
        }
        active.tradeGateDemandPresent = true;
        active.tradeGateDemandIdentity = demand.identity();
        active.tradeGateRouteMayDisplace = mayDisplace;
        active.routeStatus = mayDisplace
                ? ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE
                : ExistingRouteFeasibility.ExistingRouteStatus.FEASIBLE;
        if (mayDisplace && active.routeInfeasibleTick < 0L) {
            active.routeInfeasibleTick = tick;
        }
        demandTransition(demand.identity(), active.routeStatus, tick);
        if (mayDisplace && active.lastGoalState != null
                && "FriendlyGreetGoal".equals(active.lastGoalState.moveHolderClass)) {
            active.friendlyGreetRunningWhenTradeEligible = true;
        }
    }

    public static synchronized void observeBackpackResult(Object backpack, long tick) {
        if (!inTradeInvocation()) {
            return;
        }
        active.tradeGateBackpackPresent = backpack != null;
        if (backpack != null && backpack != active.backpackIdentity) {
            event(tick, "BACKPACK_IDENTITY_MISMATCH", "true");
        }
    }

    public static synchronized void enterAuthorizedCandidate(UUID mobId, long tick) {
        if (!matchesMob(mobId)) {
            return;
        }
        MARKET_DISCOVERY.set(mobId);
        active.authorizedCandidateCalls++;
        event(tick, "AUTHORIZED_CANDIDATE_ENTER", "call=" + active.authorizedCandidateCalls);
    }

    public static synchronized void exitAuthorizedCandidate(
            UUID mobId, boolean present, long tick) {
        if (!matchesMob(mobId)) {
            MARKET_DISCOVERY.remove();
            return;
        }
        if (present) {
            active.authorizedCandidatePresent++;
        } else {
            active.authorizedCandidateEmpty++;
        }
        event(tick, "AUTHORIZED_CANDIDATE_EXIT", "present=" + present);
        MARKET_DISCOVERY.remove();
    }

    public static synchronized void observeVillagerQuery(
            List<? extends Villager> candidates, long tick) {
        if (!inTradeInvocation() || candidates == null) {
            return;
        }
        long observedTick = tick >= 0L ? tick : active.lastTradeCanUseTick;
        active.villagerQueryReached = true;
        active.villagerQueryCandidateCount = candidates.size();
        for (Villager candidate : candidates) {
            if (candidate.getUUID().equals(active.traderId)) {
                active.fixtureTraderIncluded = true;
                active.fixtureTraderAlive = candidate.isAlive();
                active.fixtureTraderAvailable = candidate.isAlive()
                        && !candidate.isSleeping() && candidate.getTradingPlayer() == null;
            }
        }
        event(observedTick, "VILLAGER_QUERY", "count=" + candidates.size()
                + " fixtureIncluded=" + active.fixtureTraderIncluded);
    }

    public static synchronized void observeVanillaBoardRead(UUID traderId, long tick) {
        if (!matchesTrader(traderId) || !inTradeInvocation()) {
            return;
        }
        active.vanillaBoardReadReached = true;
        event(tick, "VANILLA_BOARD_READ", "fixtureTrader=true");
    }

    public static synchronized void observeKnownTraderObservation(
            UUID mobId, UUID traderId, boolean changed, long tick) {
        if (!matchesMob(mobId) || !matchesTrader(traderId)) {
            return;
        }
        active.knownTraderObservationReached = true;
        active.knownTraderObservationChanged |= changed;
        event(tick, "KNOWN_TRADER_OBSERVATION", "changed=" + changed);
    }

    public static synchronized void observeTradeStart(UUID mobId, long tick) {
        if (matchesMob(mobId)) {
            active.tradeStartCalls++;
            event(tick, "TRADE_START", "count=" + active.tradeStartCalls);
        }
    }

    public static synchronized void observeTradeTick(UUID mobId) {
        if (matchesMob(mobId)) {
            active.tradeTickCalls++;
        }
    }

    public static synchronized void observeTradeStop(UUID mobId, long tick) {
        if (matchesMob(mobId)) {
            active.tradeStopCalls++;
            event(tick, "TRADE_STOP", "count=" + active.tradeStopCalls);
        }
    }

    public static synchronized void observeCooldownCheck(
            Object cooldown, boolean activeNow, long retryAtTick, long tick) {
        if (active == null || active.cooldownIdentity != cooldown) {
            return;
        }
        active.marketDiscoveryCooldownActive = activeNow;
        active.tradeGateMarketCooldown = activeNow;
        active.marketDiscoveryCooldownUntil = retryAtTick;
        if (activeNow) {
            event(tick, "MARKET_COOLDOWN_ACTIVE", "until=" + retryAtTick);
        }
    }

    public static synchronized void observeCooldownRecorded(
            Object cooldown, long retryAtTick, long tick) {
        if (active == null || active.cooldownIdentity != cooldown) {
            return;
        }
        active.marketDiscoveryEmptyRecorded = true;
        active.marketDiscoveryCooldownActive = true;
        active.marketDiscoveryCooldownUntil = retryAtTick;
        event(tick, "MARKET_EMPTY_RECORDED", "until=" + retryAtTick);
    }

    public static synchronized void enterGather(UUID mobId) {
        if (matchesMob(mobId)) {
            GATHER_INVOCATION.set(mobId);
        }
    }

    public static synchronized void exitGather(UUID mobId, boolean acquired, long tick) {
        if (matchesMob(mobId) && acquired && active.gatherYieldedToTradeHandoff) {
            active.gatherReacquiredAfterHandoff = true;
            event(tick, "GATHER_REACQUIRED_AFTER_HANDOFF", "true");
        }
        GATHER_INVOCATION.remove();
    }

    public static synchronized void observeGatherMandatoryRoute(boolean present, long tick) {
        if (active != null && active.mobId.equals(GATHER_INVOCATION.get()) && present) {
            active.gatherMandatoryDemandSeen = true;
            eventOnce(tick, "GATHER_MANDATORY_DEMAND", "true");
        }
    }

    public static synchronized void observeRouteEvidencePublish(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, long tick) {
        if (!matchesMob(mobId) || demand == null) {
            return;
        }
        active.gatherRouteExhaustionPublished = true;
        active.gatherRouteExhaustionTick = tick;
        active.routeEvidenceGeneration++;
        active.routeEvidenceIdentity = demand.identity();
        active.routeStatus = ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE;
        active.routeInfeasibleTick = active.routeInfeasibleTick < 0L
                ? tick : active.routeInfeasibleTick;
        demandTransition(demand.identity(), active.routeStatus, tick);
        event(tick, "GATHER_ROUTE_EXHAUSTION", "generation="
                + active.routeEvidenceGeneration + " identity=" + demand.identity());
    }

    public static synchronized void observeRouteEvidenceRead(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, boolean exhausted, long tick) {
        if (!matchesMob(mobId) || demand == null || !exhausted) {
            return;
        }
        active.routeEvidenceReadIdentity = demand.identity();
        active.routeEvidenceReadGeneration = active.routeEvidenceGeneration;
        eventOnce(tick, "ROUTE_EVIDENCE_READ", "generation="
                + active.routeEvidenceReadGeneration + " identity=" + demand.identity());
    }

    public static synchronized void observeGatherYield(boolean yielded, long tick) {
        if (active == null || !active.mobId.equals(GATHER_INVOCATION.get()) || !yielded) {
            return;
        }
        active.gatherYieldWindowOpened = true;
        active.gatherYieldedToTradeHandoff = true;
        event(tick, "GATHER_YIELDED_TO_TRADE", "true");
    }

    public static synchronized void observeGatherStop(UUID mobId, long tick) {
        if (matchesMob(mobId) && active.gatherYieldedToTradeHandoff) {
            active.gatherStoppedAfterHandoff = true;
            event(tick, "GATHER_STOPPED_AFTER_HANDOFF", "true");
        }
    }

    public static synchronized void observeTradeClaim(
            UUID mobId, UUID traderId, boolean opened, long tick) {
        if (!matchesMob(mobId)) {
            return;
        }
        if (opened) {
            active.tradeSessionClaimOpened = true;
            event(tick, "TRADE_SESSION_CLAIM_OPEN", "trader=" + traderId);
        } else {
            active.tradeSessionClaimReleased = true;
            event(tick, "TRADE_SESSION_CLAIM_RELEASE", "true");
        }
    }

    static synchronized void observeGoalSelector(Mob subject, long tick) {
        if (subject == null || !matchesMob(subject.getUUID())) {
            return;
        }
        String move = "NONE";
        int movePriority = Integer.MAX_VALUE;
        String look = "NONE";
        int lookPriority = Integer.MAX_VALUE;
        List<String> requestedRunning = new ArrayList<>();
        for (WrappedGoal wrapped : ((MobGoalSelectorAccessor) subject)
                .spmscavenger$getGoalSelector().getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            Goal goal = wrapped.getGoal();
            String name = goal.getClass().getSimpleName();
            if (isRequestedGoal(name)) {
                requestedRunning.add(name);
            }
            EnumSet<Goal.Flag> flags = goal.getFlags();
            if (flags.contains(Goal.Flag.MOVE) && wrapped.getPriority() < movePriority) {
                move = name;
                movePriority = wrapped.getPriority();
            }
            if (flags.contains(Goal.Flag.LOOK) && wrapped.getPriority() < lookPriority) {
                look = name;
                lookPriority = wrapped.getPriority();
            }
        }
        GoalState next = new GoalState(move, normalizePriority(movePriority), look,
                normalizePriority(lookPriority), List.copyOf(requestedRunning));
        if (!next.equals(active.lastGoalState)) {
            active.lastGoalState = next;
            event(tick, "GOAL_OWNERSHIP", next.toString());
        }
        if (active.routeInfeasibleTick >= 0L && !"NONE".equals(move)
                && !"TradeWithVillagerGoal".equals(move)) {
            active.blockingHolderObservedAfterInfeasible = true;
            active.lastBlockingHolder = move + "@" + normalizePriority(movePriority);
        }
    }

    private static boolean isRequestedGoal(String name) {
        return switch (name) {
            case "GatherResourcesGoal", "TradeWithVillagerGoal", "FriendlyGreetGoal",
                    "RaidContainersGoal", "RaidArmorStandsGoal", "CollectFloorItemsGoal",
                    "EatFoodGoal", "SeekShelterGoal" -> true;
            default -> false;
        };
    }

    private static int normalizePriority(int priority) {
        return priority == Integer.MAX_VALUE ? -1 : priority;
    }

    static synchronized void observeFixtureFacts(
            boolean alive, String profession, int level, double distance, boolean available,
            int emeralds, int pickaxes, long tick) {
        if (active == null) {
            return;
        }
        active.fixtureTraderAlive = alive;
        active.fixtureTraderProfession = profession;
        active.fixtureTraderLevel = level;
        active.fixtureTraderDistance = distance;
        active.fixtureTraderAvailable = available;
        active.subjectEmeraldCount = emeralds;
        active.subjectIronPickaxeCount = pickaxes;
        event(tick, "FIXTURE_FACTS", "alive=" + alive + " profession=" + profession
                + " level=" + level + " distance=" + distance + " available=" + available
                + " emeralds=" + emeralds + " pickaxes=" + pickaxes);
    }

    static synchronized Snapshot snapshot() {
        return active == null ? Snapshot.empty() : active.snapshot();
    }

    static synchronized List<String> events() {
        return active == null ? List.of() : List.copyOf(active.events);
    }

    static synchronized void reset() {
        active = null;
        TRADE_INVOCATION.remove();
        MARKET_DISCOVERY.remove();
        GATHER_INVOCATION.remove();
    }

    static Diagnosis classify(Snapshot snapshot) {
        if (snapshot == null) {
            return Diagnosis.UNKNOWN;
        }
        return classify(new ClassificationEvidence(snapshot.armed,
                snapshot.routeInfeasibleTick >= 0L, snapshot.tradeCanUseCalls,
                snapshot.tradeCanUseTrue, snapshot.authorizedCandidateCalls,
                snapshot.authorizedCandidatePresent,
                snapshot.villagerQueryReached || snapshot.marketDiscoveryEmptyRecorded,
                snapshot.tradeStartCalls, snapshot.vanillaBoardReadReached,
                snapshot.blockingHolderObservedAfterInfeasible));
    }

    static Diagnosis classify(ClassificationEvidence evidence) {
        if (evidence == null || !evidence.armed || !evidence.routeInfeasible) {
            return Diagnosis.UNKNOWN;
        }
        if (evidence.tradeCanUseTrue > 0 && evidence.tradeStartCalls == 0) {
            return Diagnosis.TRADE_ADMITTED_NOT_STARTED;
        }
        if (evidence.tradeStartCalls > 0 && !evidence.vanillaBoardReadReached) {
            return evidence.blockingHolderObservedAfterInfeasible
                    ? Diagnosis.TRADE_PREEMPTED : Diagnosis.TRADE_STARTED_NO_BOARD;
        }
        if (evidence.authorizedCandidateCalls > 0
                && evidence.authorizedCandidatePresent == 0
                && evidence.discoveryOrEmptyRecorded) {
            return Diagnosis.TRADE_MARKET_DISCOVERY_EMPTY;
        }
        if (evidence.tradeCanUseCalls > 0 && evidence.authorizedCandidateCalls == 0) {
            return Diagnosis.TRADE_EARLY_GATE_REJECTED;
        }
        if (evidence.tradeCanUseCalls == 0) {
            return evidence.blockingHolderObservedAfterInfeasible
                    ? Diagnosis.TRADE_PREEMPTED : Diagnosis.TRADE_NOT_SCHEDULED;
        }
        return Diagnosis.UNKNOWN;
    }

    record ClassificationEvidence(
            boolean armed, boolean routeInfeasible,
            int tradeCanUseCalls, int tradeCanUseTrue,
            int authorizedCandidateCalls, int authorizedCandidatePresent,
            boolean discoveryOrEmptyRecorded,
            int tradeStartCalls, boolean vanillaBoardReadReached,
            boolean blockingHolderObservedAfterInfeasible) {
    }

    private static void demandTransition(
            WorkDemandPolicy.MaterialDemandIdentity identity,
            ExistingRouteFeasibility.ExistingRouteStatus status, long tick) {
        if (active == null || Objects.equals(active.lastLoggedDemandIdentity, identity)
                && active.lastLoggedRouteStatus == status) {
            return;
        }
        active.lastLoggedDemandIdentity = identity;
        active.lastLoggedRouteStatus = status;
        event(tick, "LIVE_DEMAND", "identity=" + identity + " route=" + status);
    }

    private static void eventOnce(long tick, String name, String detail) {
        if (active != null && active.events.stream().noneMatch(line -> line.contains("event=" + name))) {
            event(tick, name, detail);
        }
    }

    private static void event(long tick, String name, String detail) {
        if (active == null || active.events.size() >= MAX_EVENTS) {
            return;
        }
        active.events.add("tick=" + tick + " event=" + name + " " + detail);
    }

    record GoalState(
            String moveHolderClass, int moveHolderPriority,
            String lookHolderClass, int lookHolderPriority,
            List<String> requestedRunning) {
    }

    public record Snapshot(
            boolean armed,
            int tradeCanUseCalls, int tradeCanUseTrue, int tradeCanUseFalse,
            long firstTradeCanUseTick, long lastTradeCanUseTick,
            boolean tradeGateDemandPresent,
            WorkDemandPolicy.MaterialDemandIdentity tradeGateDemandIdentity,
            boolean tradeGateRouteMayDisplace, boolean tradeGateBackpackPresent,
            boolean tradeGateMarketCooldown,
            ExistingRouteFeasibility.ExistingRouteStatus routeStatus,
            int authorizedCandidateCalls, int authorizedCandidatePresent,
            int authorizedCandidateEmpty,
            int tradeStartCalls, int tradeTickCalls, int tradeStopCalls,
            boolean villagerQueryReached, int villagerQueryCandidateCount,
            boolean fixtureTraderIncluded, boolean fixtureTraderAvailable,
            double fixtureTraderDistance,
            boolean vanillaBoardReadReached,
            boolean knownTraderObservationReached, boolean knownTraderObservationChanged,
            boolean marketDiscoveryEmptyRecorded, boolean marketDiscoveryCooldownActive,
            long marketDiscoveryCooldownUntil,
            String moveHolderClass, int moveHolderPriority,
            String lookHolderClass, int lookHolderPriority,
            List<String> requestedRunning,
            boolean gatherMandatoryDemandSeen, boolean gatherRouteExhaustionPublished,
            long gatherRouteExhaustionTick, boolean gatherYieldWindowOpened,
            boolean gatherYieldedToTradeHandoff, boolean gatherStoppedAfterHandoff,
            boolean gatherReacquiredAfterHandoff,
            WorkDemandPolicy.MaterialDemandIdentity routeEvidenceIdentity,
            int routeEvidenceGeneration,
            WorkDemandPolicy.MaterialDemandIdentity routeEvidenceReadIdentity,
            int routeEvidenceReadGeneration,
            long routeInfeasibleTick,
            boolean tradeSessionClaimOpened, boolean tradeSessionClaimReleased,
            boolean friendlyGreetRunningWhenTradeEligible,
            boolean blockingHolderObservedAfterInfeasible, String lastBlockingHolder,
            boolean fixtureTraderAlive, String fixtureTraderProfession, int fixtureTraderLevel,
            int subjectEmeraldCount, int subjectIronPickaxeCount,
            Diagnosis diagnosis, int eventCount) {

        static Snapshot empty() {
            return new Snapshot(false, 0, 0, 0, -1, -1, false, null,
                    false, false, false, ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN,
                    0, 0, 0, 0, 0, 0,
                    false, 0, false, false, Double.NaN, false, false, false,
                    false, false, -1, "NONE", -1, "NONE", -1, List.of(),
                    false, false, -1, false, false, false, false,
                    null, 0, null, 0, -1, false, false, false,
                    false, "NONE", false, "UNAVAILABLE", -1, 0, 0,
                    Diagnosis.UNKNOWN, 0);
        }
    }

    private static final class Session {
        final UUID mobId;
        final UUID traderId;
        final Object backpackIdentity;
        final List<String> events = new ArrayList<>();
        Object cooldownIdentity;
        int tradeCanUseCalls;
        int tradeCanUseTrue;
        int tradeCanUseFalse;
        long firstTradeCanUseTick = -1L;
        long lastTradeCanUseTick = -1L;
        boolean tradeGateDemandPresent;
        WorkDemandPolicy.MaterialDemandIdentity tradeGateDemandIdentity;
        boolean tradeGateRouteMayDisplace;
        boolean tradeGateBackpackPresent;
        boolean tradeGateMarketCooldown;
        int authorizedCandidateCalls;
        int authorizedCandidatePresent;
        int authorizedCandidateEmpty;
        int tradeStartCalls;
        int tradeTickCalls;
        int tradeStopCalls;
        boolean villagerQueryReached;
        int villagerQueryCandidateCount;
        boolean fixtureTraderIncluded;
        boolean fixtureTraderAvailable;
        double fixtureTraderDistance = Double.NaN;
        boolean vanillaBoardReadReached;
        boolean knownTraderObservationReached;
        boolean knownTraderObservationChanged;
        boolean marketDiscoveryEmptyRecorded;
        boolean marketDiscoveryCooldownActive;
        long marketDiscoveryCooldownUntil = -1L;
        GoalState lastGoalState;
        boolean gatherMandatoryDemandSeen;
        boolean gatherRouteExhaustionPublished;
        long gatherRouteExhaustionTick = -1L;
        boolean gatherYieldWindowOpened;
        boolean gatherYieldedToTradeHandoff;
        boolean gatherStoppedAfterHandoff;
        boolean gatherReacquiredAfterHandoff;
        WorkDemandPolicy.MaterialDemandIdentity routeEvidenceIdentity;
        int routeEvidenceGeneration;
        WorkDemandPolicy.MaterialDemandIdentity routeEvidenceReadIdentity;
        int routeEvidenceReadGeneration;
        long routeInfeasibleTick = -1L;
        ExistingRouteFeasibility.ExistingRouteStatus routeStatus =
                ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN;
        WorkDemandPolicy.MaterialDemandIdentity lastLoggedDemandIdentity;
        ExistingRouteFeasibility.ExistingRouteStatus lastLoggedRouteStatus =
                ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN;
        boolean tradeSessionClaimOpened;
        boolean tradeSessionClaimReleased;
        boolean friendlyGreetRunningWhenTradeEligible;
        boolean blockingHolderObservedAfterInfeasible;
        String lastBlockingHolder = "NONE";
        boolean fixtureTraderAlive;
        String fixtureTraderProfession = "UNAVAILABLE";
        int fixtureTraderLevel = -1;
        int subjectEmeraldCount;
        int subjectIronPickaxeCount;

        Session(UUID mobId, UUID traderId, Object backpackIdentity) {
            this.mobId = Objects.requireNonNull(mobId);
            this.traderId = Objects.requireNonNull(traderId);
            this.backpackIdentity = Objects.requireNonNull(backpackIdentity);
        }

        Snapshot snapshot() {
            GoalState goal = lastGoalState == null
                    ? new GoalState("NONE", -1, "NONE", -1, List.of()) : lastGoalState;
            Snapshot partial = new Snapshot(true, tradeCanUseCalls, tradeCanUseTrue,
                    tradeCanUseFalse, firstTradeCanUseTick, lastTradeCanUseTick,
                    tradeGateDemandPresent, tradeGateDemandIdentity,
                    tradeGateRouteMayDisplace, tradeGateBackpackPresent,
                    tradeGateMarketCooldown, routeStatus, authorizedCandidateCalls,
                    authorizedCandidatePresent, authorizedCandidateEmpty,
                    tradeStartCalls, tradeTickCalls, tradeStopCalls,
                    villagerQueryReached, villagerQueryCandidateCount,
                    fixtureTraderIncluded, fixtureTraderAvailable, fixtureTraderDistance,
                    vanillaBoardReadReached, knownTraderObservationReached,
                    knownTraderObservationChanged, marketDiscoveryEmptyRecorded,
                    marketDiscoveryCooldownActive, marketDiscoveryCooldownUntil,
                    goal.moveHolderClass, goal.moveHolderPriority,
                    goal.lookHolderClass, goal.lookHolderPriority, goal.requestedRunning,
                    gatherMandatoryDemandSeen, gatherRouteExhaustionPublished,
                    gatherRouteExhaustionTick, gatherYieldWindowOpened,
                    gatherYieldedToTradeHandoff, gatherStoppedAfterHandoff,
                    gatherReacquiredAfterHandoff, routeEvidenceIdentity,
                    routeEvidenceGeneration, routeEvidenceReadIdentity,
                    routeEvidenceReadGeneration, routeInfeasibleTick,
                    tradeSessionClaimOpened, tradeSessionClaimReleased,
                    friendlyGreetRunningWhenTradeEligible,
                    blockingHolderObservedAfterInfeasible, lastBlockingHolder,
                    fixtureTraderAlive, fixtureTraderProfession, fixtureTraderLevel,
                    subjectEmeraldCount, subjectIronPickaxeCount,
                    Diagnosis.UNKNOWN, events.size());
            return new Snapshot(partial.armed, partial.tradeCanUseCalls,
                    partial.tradeCanUseTrue, partial.tradeCanUseFalse,
                    partial.firstTradeCanUseTick, partial.lastTradeCanUseTick,
                    partial.tradeGateDemandPresent, partial.tradeGateDemandIdentity,
                    partial.tradeGateRouteMayDisplace, partial.tradeGateBackpackPresent,
                    partial.tradeGateMarketCooldown, partial.routeStatus,
                    partial.authorizedCandidateCalls,
                    partial.authorizedCandidatePresent, partial.authorizedCandidateEmpty,
                    partial.tradeStartCalls, partial.tradeTickCalls, partial.tradeStopCalls,
                    partial.villagerQueryReached, partial.villagerQueryCandidateCount,
                    partial.fixtureTraderIncluded, partial.fixtureTraderAvailable,
                    partial.fixtureTraderDistance, partial.vanillaBoardReadReached,
                    partial.knownTraderObservationReached, partial.knownTraderObservationChanged,
                    partial.marketDiscoveryEmptyRecorded, partial.marketDiscoveryCooldownActive,
                    partial.marketDiscoveryCooldownUntil, partial.moveHolderClass,
                    partial.moveHolderPriority, partial.lookHolderClass,
                    partial.lookHolderPriority, partial.requestedRunning,
                    partial.gatherMandatoryDemandSeen, partial.gatherRouteExhaustionPublished,
                    partial.gatherRouteExhaustionTick, partial.gatherYieldWindowOpened,
                    partial.gatherYieldedToTradeHandoff, partial.gatherStoppedAfterHandoff,
                    partial.gatherReacquiredAfterHandoff, partial.routeEvidenceIdentity,
                    partial.routeEvidenceGeneration, partial.routeEvidenceReadIdentity,
                    partial.routeEvidenceReadGeneration, partial.routeInfeasibleTick,
                    partial.tradeSessionClaimOpened, partial.tradeSessionClaimReleased,
                    partial.friendlyGreetRunningWhenTradeEligible,
                    partial.blockingHolderObservedAfterInfeasible, partial.lastBlockingHolder,
                    partial.fixtureTraderAlive, partial.fixtureTraderProfession,
                    partial.fixtureTraderLevel, partial.subjectEmeraldCount,
                    partial.subjectIronPickaxeCount, classify(partial), partial.eventCount);
        }
    }
}
