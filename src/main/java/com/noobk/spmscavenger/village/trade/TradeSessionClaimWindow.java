package com.noobk.spmscavenger.village.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2-E — <b>"while I am deliberately trying to reach Bob to trade, I will not interrupt myself to
 * greet Bob."</b>
 *
 * <h2>Why an interlock is needed at all</h2>
 *
 * {@code FriendlyGreetGoal} is priority <b>1</b> with MOVE+LOOK; {@code TradeWithVillagerGoal} is
 * priority 3. A P3 goal cannot hold anything against a P1 goal — the selector simply stops it. Worse,
 * the collision is <i>caused by the approach</i>: greet's {@code canUse} takes the nearest greetable
 * entity, and walking into interaction range makes the trade target exactly that. The mob would
 * preempt itself by arriving.
 *
 * <p>The only mechanism that can express this is the admission seam, which already intercepts SPM's
 * own chosen target.
 *
 * <h2>Why this is not the 44D-R2 veto</h2>
 *
 * That veto was <i>no SOCIAL intent → suppress greeting</i>, which let Opinion globally erase SPM's
 * native behaviour. This is <i>active trade with Bob + SPM wants to greet Bob → suppress that one
 * collision</i>:
 *
 * <ul>
 *   <li>this mob may still greet Alice;</li>
 *   <li>other mobs may still greet Bob;</li>
 *   <li>players may still interact with Bob;</li>
 *   <li>Bob is greetable again the moment the attempt ends.</li>
 * </ul>
 *
 * A lease over <b>our own greeting</b>, never a reservation of the villager. It confers no authority
 * over Bob and blocks nothing else in the world.
 *
 * <h2>Claim at attempt start, not at FACE</h2>
 *
 * A FACE-only claim would rest correctness on an unproven ordering fact — that the goal publishes its
 * claim before the P1 greet next evaluates the now-close villager. The selector re-evaluates every
 * tick, so that race is real. The claim therefore opens when the concrete attempt begins, before WALK
 * can carry the mob into greet range. The "this suppresses greeting for a whole walk" objection is
 * answered by bounding the <b>attempt</b> ({@link TradeCandidateRound}), not by letting trade greet
 * its own target.
 *
 * <h2>Gate RET-1</h2>
 *
 * Runtime-only, one entry per mob, keyed by the mob's stable UUID. The persistence half of RET-1e
 * does not apply; the <b>completeness</b> half does — a leaked claim silently suppresses greeting for
 * a villager nobody is trading with. Releases: {@code stop()} (unconditional), success, abandon or
 * demotion, demand disappearance, target loss, entity unload/death, server stop, and a hard expiry as
 * backstop.
 */
public final class TradeSessionClaimWindow {

    /**
     * Backstop only. Every ordinary path releases explicitly; this exists so a path nobody thought of
     * cannot suppress greeting forever. Comfortably longer than a bounded approach, far shorter than
     * a session.
     */
    public static final long MAX_CLAIM_TICKS = 1_200L;

    private record Claim(UUID villagerId, long expiresAtTick) {
    }

    private static final Map<UUID, Claim> CLAIMS = new ConcurrentHashMap<>();

    private TradeSessionClaimWindow() {
    }

    /** Open, or re-target, this mob's trade interlock. One claim per mob. */
    public static void claim(UUID mobId, UUID villagerId, long gameTime) {
        if (mobId == null || villagerId == null) {
            return;
        }
        CLAIMS.put(mobId, new Claim(villagerId, gameTime + MAX_CLAIM_TICKS));
    }

    /**
     * Does this mob's live trade claim name exactly this villager?
     *
     * <p>The pairing must match. A claim on Bob says nothing about Alice, which is the entire
     * difference between an interlock and social ownership.
     */
    public static boolean claims(UUID mobId, UUID villagerId, long gameTime) {
        if (mobId == null || villagerId == null) {
            return false;
        }
        Claim claim = CLAIMS.get(mobId);
        if (claim == null) {
            return false;
        }
        if (gameTime >= claim.expiresAtTick()) {
            CLAIMS.remove(mobId, claim);
            return false;
        }
        return claim.villagerId().equals(villagerId);
    }

    /** Unconditional release. Safe to call when no claim exists — {@code stop()} calls it blindly. */
    public static void release(UUID mobId) {
        if (mobId != null) {
            CLAIMS.remove(mobId);
        }
    }

    public static void shutdownServerState() {
        CLAIMS.clear();
    }

    /** Test/diagnostic. */
    public static int trackedClaimCount() {
        return CLAIMS.size();
    }
}
