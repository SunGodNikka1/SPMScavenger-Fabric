package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.intent.VillageIntent;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Passive one-session V4-G evidence ledger. Every method only compares/copies evidence supplied by
 * production observation mixins or the validation controller; it owns no decision or mutation API.
 */
public final class V4RuntimeWitnessTracker {

    private static final int MAX_EVENTS = 96;
    private static Session active;

    private V4RuntimeWitnessTracker() {
    }

    static synchronized void arm(
            UUID mobId, UUID traderId, Object backpackIdentity,
            V4OfferFingerprint initialOffer, long tick) {
        active = new Session(mobId, traderId, backpackIdentity, initialOffer, tick);
        event(tick, "ARMED", "initial=" + initialOffer.compact());
    }

    public static synchronized void observeBoard(
            UUID mobId, UUID traderId, V4OfferFingerprint offer, long tick) {
        Session session = matching(mobId, traderId);
        if (session == null || offer == null) {
            return;
        }
        if (!session.phaseAOpen && session.initialOffer.equals(offer)) {
            session.initialBoardObserved = true;
            event(tick, "INITIAL_BOARD_OBSERVED", offer.compact());
        }
        if (session.phaseAOpen && offer.equals(session.changedOffer)) {
            session.changedBoardRediscovered = true;
            event(tick, "CHANGED_BOARD_REDISCOVERED", offer.compact());
        }
    }

    static synchronized void markChangedOffer(V4OfferFingerprint changed, long tick) {
        if (active == null) {
            return;
        }
        active.changedOffer = Objects.requireNonNull(changed);
        event(tick, "LIVE_OFFER_CHANGED", changed.compact());
    }

    static synchronized void openPhaseA(long tick) {
        if (active == null) {
            return;
        }
        active.phaseAOpen = true;
        active.phaseAOpenTick = tick;
        event(tick, "PHASE_A_OPEN", "home=false");
    }

    static synchronized void observeDemand(
            WorkDemandPolicy.MaterialDemandIdentity identity,
            ExistingRouteFeasibility.ExistingRouteStatus routeStatus,
            long tick) {
        if (active == null) {
            return;
        }
        active.demandIdentity = identity;
        active.routeStatus = routeStatus;
        if (!Objects.equals(active.lastLoggedDemandIdentity, identity)
                || active.lastLoggedRouteStatus != routeStatus) {
            active.lastLoggedDemandIdentity = identity;
            active.lastLoggedRouteStatus = routeStatus;
            event(tick, "LIVE_DEMAND", identity + " route=" + routeStatus);
        }
    }

    public static synchronized void observeDirective(UUID mobId, VillageIntent intent, long tick) {
        if (!matchingMob(mobId) || intent == null) {
            return;
        }
        if (!active.phaseAOpen) {
            if (active.bootstrapRequiredTradeIntents.add(intent)) {
                active.bootstrapLocalRequiredTradeCount++;
                event(tick, "BOOTSTRAP_LOCAL_REQUIRED_TRADE", identity(intent));
            }
            return;
        }
        if (active.intent == null) {
            active.intent = intent;
            active.intentIdentity = identity(intent);
            event(tick, "REQUIRED_TRADE_DIRECTIVE", active.intentIdentity);
        } else if (active.intent == intent) {
            event(tick, "DIRECTIVE_REVALIDATED", active.intentIdentity);
        }
    }

    public static synchronized void observeCommuteSeed(
            UUID mobId, VillageIntent intent, boolean seeded, long tick) {
        if (!matchingMob(mobId) || intent == null || !seeded) {
            return;
        }
        if (!active.phaseAOpen) {
            if (active.bootstrapCommuteSeeds.add(intent)) {
                active.bootstrapLocalCommuteSeedCount++;
                event(tick, "BOOTSTRAP_LOCAL_COMMUTE_SEED", identity(intent));
            }
            return;
        }
        active.commuteSeeded = true;
        active.commuteSource = "REQUIRED_TRADE";
        active.intent = active.intent == null ? intent : active.intent;
        active.intentIdentity = identity(active.intent);
        event(tick, "COMMUTE_SEEDED", "source=REQUIRED_TRADE binding=" + identity(intent));
    }

    public static synchronized void observeInterruption(UUID mobId, VillageIntent intent, long tick) {
        if (!matchingExact(mobId, intent)) {
            return;
        }
        active.interrupted = true;
        active.interruptedBinding = intent;
        event(tick, "COMMUTE_INTERRUPTED", "binding=" + identity(intent));
    }

    public static synchronized void observeResume(UUID mobId, VillageIntent intent, long tick) {
        if (!matchingExact(mobId, intent) || !active.interrupted) {
            return;
        }
        active.resumed = true;
        active.sameBindingResumed = active.interruptedBinding == intent;
        event(tick, "COMMUTE_RESUMED", "sameBinding=" + active.sameBindingResumed);
    }

    public static synchronized void observeArrival(UUID mobId, VillageIntent intent, boolean released, long tick) {
        if (!matchingMob(mobId) || intent == null) {
            return;
        }
        if (!active.phaseAOpen) {
            if (active.bootstrapArrivals.add(intent)) {
                active.bootstrapLocalArrivalCount++;
                event(tick, "BOOTSTRAP_LOCAL_ARRIVAL",
                        "binding=" + identity(intent) + " released=" + released);
            }
            active.bootstrapLocalIntentReleased |= released;
            return;
        }
        if (!matchingExact(mobId, intent)) {
            return;
        }
        active.arrivalObserved = true;
        active.intentReleasedAtArrival = released;
        if (tick >= 0L) {
            active.arrivalStamped = true;
            event(tick, "SETTLEMENT_ARRIVAL", "intentReleased=" + released);
        }
    }

    static synchronized void observeBootstrapIntentClosed(
            UUID mobId, VillageIntent intent, long tick) {
        if (!matchingMob(mobId) || active.phaseAOpen || intent == null) {
            return;
        }
        active.bootstrapLocalIntentReleased = true;
        event(tick, "BOOTSTRAP_LOCAL_INTENT_RELEASED_OR_CLOSED", identity(intent));
    }

    static synchronized void stampArrival(long tick) {
        if (active == null || !active.arrivalObserved || active.arrivalStamped) {
            return;
        }
        active.arrivalStamped = true;
        event(tick, "SETTLEMENT_ARRIVAL_OBSERVED", "intentReleased=" + active.intentReleasedAtArrival);
    }

    public static synchronized void observeNavigationStop(UUID mobId, long tick) {
        if (!matchingMob(mobId) || !active.interrupted || active.navigationDiscarded) {
            return;
        }
        active.navigationDiscarded = true;
        event(tick, "COMMUTE_NAVIGATION_DISCARDED", "intentRetained=true");
    }

    public static synchronized void observeRouteFailure(UUID mobId, VillageIntent intent, boolean published, long tick) {
        if (!matchingMob(mobId) || intent == null || !published) {
            return;
        }
        active.routeFailurePublications++;
        event(tick, "ROUTE_FAILURE_PUBLISHED", identity(intent));
    }

    public static synchronized void observeTrade(
            Object backpackIdentity, UUID traderId, V4OfferFingerprint offer,
            boolean traded, long tick) {
        if (active == null || active.backpackIdentity != backpackIdentity
                || !active.traderId.equals(traderId) || offer == null || !traded) {
            return;
        }
        if (!active.phaseAOpen) {
            if (offer.equals(active.initialOffer)) {
                active.initialWarmupOfferExecuted = true;
                event(tick, "INITIAL_WARMUP_TRADE", offer.compact());
            } else {
                event(tick, "PRE_PHASE_A_OTHER_TRADE", offer.compact());
            }
            return;
        }
        active.executedOffer = offer;
        active.changedOfferExecuted = offer.equals(active.changedOffer);
        active.cachedInitialOfferExecuted = offer.equals(active.initialOffer)
                && !offer.equals(active.changedOffer);
        event(tick, "TRADE_COMMITTED", offer.compact());
    }

    static synchronized void observeSeekShelterRunning(UUID mobId, long tick) {
        if (matchingMob(mobId) && !active.seekShelterObserved) {
            active.seekShelterObserved = true;
            event(tick, "SEEK_SHELTER_RUNNING", "true");
        }
    }

    public static synchronized void observeHomePromotion(
            UUID mobId, BlockPosEvidence bed, boolean sleeping, boolean promoted, long tick) {
        if (!matchingMob(mobId)) {
            return;
        }
        active.sleepingObserved |= sleeping;
        active.homePromotionObserved |= promoted;
        active.sleepBed = bed;
        event(tick, "SLEEP_HOME_EVENT",
                "bed=" + bed + " sleeping=" + sleeping + " promoted=" + promoted);
    }

    static synchronized Snapshot snapshot() {
        if (active == null) {
            return Snapshot.empty();
        }
        return active.snapshot();
    }

    static synchronized List<String> events() {
        return active == null ? List.of() : List.copyOf(active.events);
    }

    static synchronized void reset() {
        active = null;
    }

    private static Session matching(UUID mobId, UUID traderId) {
        return active != null && active.mobId.equals(mobId) && active.traderId.equals(traderId)
                ? active : null;
    }

    private static boolean matchingMob(UUID mobId) {
        return active != null && active.mobId.equals(mobId);
    }

    private static boolean matchingExact(UUID mobId, VillageIntent intent) {
        return matchingMob(mobId) && active.intent == intent;
    }

    private static String identity(VillageIntent intent) {
        return intent.kind() + "@" + intent.openedAtTick() + "/" + intent.destination()
                + "/" + intent.requiredTradeDemand().orElse(null)
                + "#" + Integer.toHexString(System.identityHashCode(intent));
    }

    private static void event(long tick, String event, String detail) {
        if (active == null || active.events.size() >= MAX_EVENTS) {
            return;
        }
        active.events.add("tick=" + tick + " event=" + event + " " + detail);
    }

    /** Avoid retaining Minecraft positions in unit-only snapshots while preserving exact evidence. */
    public record BlockPosEvidence(int x, int y, int z) {
        @Override public String toString() { return x + "," + y + "," + z; }
    }

    record Snapshot(
            boolean armed,
            boolean phaseAOpen,
            long phaseAOpenTick,
            boolean initialBoardObserved,
            boolean initialWarmupOfferExecuted,
            int bootstrapLocalRequiredTradeCount,
            int bootstrapLocalCommuteSeedCount,
            int bootstrapLocalArrivalCount,
            boolean bootstrapLocalIntentReleased,
            boolean changedBoardRediscovered,
            V4OfferFingerprint initialOffer,
            V4OfferFingerprint changedOffer,
            V4OfferFingerprint executedOffer,
            WorkDemandPolicy.MaterialDemandIdentity demandIdentity,
            ExistingRouteFeasibility.ExistingRouteStatus routeStatus,
            String intentIdentity,
            boolean commuteSeeded,
            String commuteSource,
            boolean interrupted,
            boolean navigationDiscarded,
            boolean resumed,
            boolean sameBindingResumed,
            boolean arrivalObserved,
            boolean intentReleasedAtArrival,
            int routeFailurePublications,
            boolean changedOfferExecuted,
            boolean cachedInitialOfferExecuted,
            boolean seekShelterObserved,
            boolean sleepingObserved,
            boolean homePromotionObserved,
            BlockPosEvidence sleepBed,
            int eventCount) {

        static Snapshot empty() {
            return new Snapshot(false, false, -1L, false, false, 0, 0, 0, false,
                    false, null, null, null,
                    null, ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN, null,
                    false, "NONE", false, false, false, false, false, false, 0,
                    false, false, false, false, false, null, 0);
        }
    }

    private static final class Session {
        final UUID mobId;
        final UUID traderId;
        final Object backpackIdentity;
        final V4OfferFingerprint initialOffer;
        final List<String> events = new ArrayList<>();
        V4OfferFingerprint changedOffer;
        V4OfferFingerprint executedOffer;
        boolean phaseAOpen;
        long phaseAOpenTick = -1L;
        boolean initialBoardObserved;
        boolean initialWarmupOfferExecuted;
        final java.util.Set<VillageIntent> bootstrapRequiredTradeIntents =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final java.util.Set<VillageIntent> bootstrapCommuteSeeds =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final java.util.Set<VillageIntent> bootstrapArrivals =
                Collections.newSetFromMap(new IdentityHashMap<>());
        int bootstrapLocalRequiredTradeCount;
        int bootstrapLocalCommuteSeedCount;
        int bootstrapLocalArrivalCount;
        boolean bootstrapLocalIntentReleased;
        boolean changedBoardRediscovered;
        WorkDemandPolicy.MaterialDemandIdentity demandIdentity;
        ExistingRouteFeasibility.ExistingRouteStatus routeStatus =
                ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN;
        WorkDemandPolicy.MaterialDemandIdentity lastLoggedDemandIdentity;
        ExistingRouteFeasibility.ExistingRouteStatus lastLoggedRouteStatus =
                ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN;
        VillageIntent intent;
        String intentIdentity;
        boolean commuteSeeded;
        String commuteSource = "NONE";
        boolean interrupted;
        boolean navigationDiscarded;
        boolean arrivalStamped;
        VillageIntent interruptedBinding;
        boolean resumed;
        boolean sameBindingResumed;
        boolean arrivalObserved;
        boolean intentReleasedAtArrival;
        int routeFailurePublications;
        boolean changedOfferExecuted;
        boolean cachedInitialOfferExecuted;
        boolean seekShelterObserved;
        boolean sleepingObserved;
        boolean homePromotionObserved;
        BlockPosEvidence sleepBed;

        Session(UUID mobId, UUID traderId, Object backpackIdentity,
                V4OfferFingerprint initialOffer, long tick) {
            this.mobId = Objects.requireNonNull(mobId);
            this.traderId = Objects.requireNonNull(traderId);
            this.backpackIdentity = Objects.requireNonNull(backpackIdentity);
            this.initialOffer = Objects.requireNonNull(initialOffer);
        }

        Snapshot snapshot() {
            return new Snapshot(true, phaseAOpen, phaseAOpenTick, initialBoardObserved,
                    initialWarmupOfferExecuted, bootstrapLocalRequiredTradeCount,
                    bootstrapLocalCommuteSeedCount, bootstrapLocalArrivalCount,
                    bootstrapLocalIntentReleased, changedBoardRediscovered,
                    initialOffer, changedOffer, executedOffer,
                    demandIdentity, routeStatus, intentIdentity, commuteSeeded, commuteSource,
                    interrupted, navigationDiscarded, resumed, sameBindingResumed, arrivalObserved,
                    intentReleasedAtArrival, routeFailurePublications, changedOfferExecuted,
                    cachedInitialOfferExecuted, seekShelterObserved, sleepingObserved,
                    homePromotionObserved, sleepBed, events.size());
        }
    }
}
