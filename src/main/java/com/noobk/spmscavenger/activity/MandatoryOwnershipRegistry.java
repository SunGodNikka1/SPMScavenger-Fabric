package com.noobk.spmscavenger.activity;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D-VR-084 / task-52 — the runtime-only, per-mob pending-claim registry.
 *
 * <h2>The anti-self-renewal slot</h2>
 *
 * Each mob holds exactly one slot: the current claim plus the <b>terminal</b>
 * {@code (consumerKey, routeIdentity, generation)} of the claim that last occupied it. A publish
 * is refused when it names the same canonical route at a generation not strictly greater than the
 * remembered one. This is what makes "the demand is still there" unable to mint a successor
 * claim — requirement 3 is structural, not a rule someone must remember.
 *
 * <h2>Generation is producer-side authority</h2>
 *
 * The registry does not mint generations. The owner passes the generation it holds; the owner
 * advances it only at {@link ReleaseReason#EXECUTOR_STARTED} (see the task-52 brief). The
 * registry merely enforces strict monotonicity per canonical route.
 *
 * <h2>Gate RET-1</h2>
 *
 * Runtime-only: keyed by mob UUID, one slot per mob (a new episode replaces, never appends),
 * hard expiry where an expired claim is <b>deleted</b> rather than predicate-false, and
 * production eviction at entity unload, death, and server stop (wired in {@code SpmScavenger}).
 * Never persisted and never registered in {@code PerMobSavedData.forgetAll()} — a persisted claim
 * would resurrect a frozen mob with no live owner able to clear it.
 *
 * <p>Mirrors {@code TradeSessionClaimWindow}, the shipped precedent for a runtime-only per-mob
 * claim registry.
 */
public final class MandatoryOwnershipRegistry {

    /**
     * Bounded pending window. Not an architecture constant: long enough to cover the deliberate
     * work band's scan cadence plus one approach attempt, short enough that an unservable claim
     * stops blocking discretionary work within a few seconds.
     */
    public static final long MAX_CLAIM_TICKS = 400L;

    /** Why a claim left the slot. Only {@link #EXECUTOR_STARTED} mints the next generation. */
    public enum ReleaseReason {
        /** The executor actually began; the running ActivityClass now supplies the blocker. */
        EXECUTOR_STARTED,
        /** A completed sweep proved route exhaustion / ownership handed off. */
        ROUTE_HANDED_OFF,
        /** This attempt cannot serve the route. */
        ABANDONED,
        /** Ordinary end: owner satisfied, cancelled, or the slot is being cleared. */
        ORDINARY
    }

    public enum PublishResult {
        ACCEPTED,
        /** Same canonical route at a generation not greater than the remembered one. */
        REFUSED_SAME_ROUTE_GENERATION
    }

    /** The slot: the current claim (absent after release/expiry) plus the remembered terminal. */
    private record Slot(MandatoryOwnershipClaim claim, Terminal terminal) {
    }

    /**
     * The anti-self-renewal memory: the {@code (consumerKey, routeIdentity, generation)} of the
     * claim that last occupied the slot. It survives the claim's release and expiry — that
     * survival is what refuses a same-identity republish after abandonment or TTL (P6/P7,
     * scenario 5). It is bounded by the entity lifetime: permanent removal and server stop clear
     * the whole slot.
     */
    private record Terminal(ResourceLocation consumerKey, Object routeIdentity, int generation) {
    }

    private static final Map<UUID, Slot> SLOTS = new ConcurrentHashMap<>();

    private MandatoryOwnershipRegistry() {
    }

    /**
     * Attempt to publish a pending claim for this mob.
     *
     * <p>A refused publish is a normal outcome, not an error — the owner simply has nothing new
     * to say. Acceptance replaces the slot; it never appends.
     */
    public static PublishResult publish(
            UUID mobId,
            ResourceLocation consumerKey,
            Object routeIdentity,
            int generation,
            long now) {
        if (mobId == null || consumerKey == null || routeIdentity == null) {
            return PublishResult.REFUSED_SAME_ROUTE_GENERATION;
        }
        Slot slot = SLOTS.get(mobId);
        if (slot != null
                && slot.terminal().consumerKey().equals(consumerKey)
                && slot.terminal().routeIdentity().equals(routeIdentity)
                && generation <= slot.terminal().generation()) {
            return PublishResult.REFUSED_SAME_ROUTE_GENERATION;
        }
        MandatoryOwnershipClaim claim = new MandatoryOwnershipClaim(
                mobId, consumerKey, routeIdentity, generation, now, now + MAX_CLAIM_TICKS);
        SLOTS.put(mobId, new Slot(claim, new Terminal(consumerKey, routeIdentity, generation)));
        return PublishResult.ACCEPTED;
    }

    /**
     * The live claim for this mob, if any. An expired claim is <b>deleted</b> — the claim half of
     * the slot is dropped, not reported as live-but-predicate-false (RET-1a) — while the terminal
     * is retained so the same identity cannot be re-minted by demand existence (scenario 5).
     */
    public static Optional<MandatoryOwnershipClaim> liveClaim(UUID mobId, long now) {
        if (mobId == null) {
            return Optional.empty();
        }
        Slot slot = SLOTS.get(mobId);
        if (slot == null || slot.claim() == null) {
            return Optional.empty();
        }
        if (slot.claim().expired(now)) {
            SLOTS.put(mobId, new Slot(null, slot.terminal()));
            return Optional.empty();
        }
        return Optional.of(slot.claim());
    }

    /**
     * Release the current claim (the claim half is deleted; the terminal is retained so the
     * anti-self-renewal memory survives abandonment, handoff, expiry, and ordinary end). Safe to
     * call when none exists. The reason is semantics for the owner (only
     * {@link ReleaseReason#EXECUTOR_STARTED} advances the owner's generation); the registry
     * itself treats every release as the same deletion.
     */
    public static void release(UUID mobId, ReleaseReason reason) {
        if (mobId != null) {
            Slot slot = SLOTS.get(mobId);
            if (slot != null) {
                SLOTS.put(mobId, new Slot(null, slot.terminal()));
            }
        }
    }

    /**
     * Permanent removal (death / discard / entity destroyed): clear the whole slot including the
     * terminal. The mob no longer exists, so its anti-self-renewal memory must not linger.
     */
    public static void removePermanently(UUID mobId) {
        if (mobId != null) {
            SLOTS.remove(mobId);
        }
    }

    /** Server-stop clear. No claim or terminal survives a restart. */
    public static void shutdownServerState() {
        SLOTS.clear();
    }

    /** Test/diagnostic — slots holding a claim half (live or not yet expiry-read). */
    public static int trackedClaimCount() {
        return (int) SLOTS.values().stream()
                .filter(slot -> slot.claim() != null)
                .count();
    }
}
