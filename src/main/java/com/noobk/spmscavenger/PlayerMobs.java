package com.noobk.spmscavenger;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.InventoryCarrier;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * Identifies Social Player Mobs at runtime <b>without compiling against it</b>.
 *
 * <h2>Why reflection rather than a dependency</h2>
 *
 * Social Player Mobs is <b>PolyForm Shield 1.0.0</b> — source-available with a noncompete clause.
 * Its own wiki documents a {@code modCompileOnly} path for dependent mods, which would be legitimate,
 * but resolving the class by name buys two things that matter more here: this addon ships no part of
 * their code, and it loads harmlessly when SPM is absent instead of hard-failing.
 *
 * <p><b>The cost is silent failure.</b> If they rename the entity, this addon quietly does nothing.
 * That debt is paid back in {@link SpmScavenger}, which logs which of found / absent / changed it is
 * in at startup — without that line a user cannot tell "broken" from "not installed".
 *
 * <h2>The backpack is reached through vanilla, not through SPM</h2>
 *
 * {@code PlayerMobEntity implements InventoryCarrier} — and {@link InventoryCarrier} is
 * <b>vanilla</b> ({@code net.minecraft.world.entity.npc.InventoryCarrier}, the interface villagers
 * use). So the 8-slot backpack every goal in this mod reads and writes is reachable through a
 * vanilla interface with no SPM types involved at all. That single fact is why this addon needs no
 * accessors into their code and no compile dependency.
 */
public final class PlayerMobs {

    private static final String PLAYER_MOB_CLASS = "games.brennan.playermob.entity.PlayerMobEntity";

    /** Resolution state, so startup can report precisely which situation the user is in. */
    public enum State { FOUND, ABSENT, HIERARCHY_CHANGED }

    /** Result of reading SPM's persistent stay-near order through its public accessor. */
    public enum StayAnchorState { ABSENT, PRESENT, UNAVAILABLE }

    /** Read-only provenance for SPM's transient explicit attack order. */
    public enum AttackOrderState { ABSENT, PRESENT, UNAVAILABLE }

    /** GAO-7 read-only snapshot of SPM's two persisted disposition dimensions. */
    public record Disposition(int fightFlight, int friendliness) {}

    /**
     * Fallback only. Pinned to {@code FeelingLedger.DEFAULT} as observed in
     * {@code playermob-fabric-0.96.0+1.21.1.jar}; used solely when the live field cannot be read.
     *
     * <p>Gate SPM-0: a copied constant is a hardcode with a delayed fuse — if SPM moves its neutral
     * point, a copy stays silently wrong and nothing fails loudly. So this value is never used while
     * the real one is readable.
     */
    private static final float PINNED_NEUTRAL_FEELING = 5.0f;

    private static final String FEELING_LEDGER_CLASS = "games.brennan.playermob.entity.FeelingLedger";

    private static Float neutralFeeling;

    /**
     * Neutral on SPM's own 0-10 feeling scale — the value two mobs that have never interacted sit
     * at, so "above neutral" means a real positive history.
     *
     * <p>Read from {@code FeelingLedger.DEFAULT} at runtime rather than copied, so this tracks SPM
     * rather than a snapshot of it. Falls back to {@link #PINNED_NEUTRAL_FEELING} with a warning if
     * that field ever moves.
     */
    public static synchronized float neutralFeeling() {
        if (neutralFeeling != null) {
            return neutralFeeling;
        }
        float value = PINNED_NEUTRAL_FEELING;
        if (available()) {
            try {
                value = Class.forName(FEELING_LEDGER_CLASS).getField("DEFAULT").getFloat(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                SpmScavenger.LOGGER.warn(
                        "[spmscavenger] could not read {}.DEFAULT; falling back to the pinned "
                                + "neutral feeling {}. This mod likely needs an update.",
                        FEELING_LEDGER_CLASS, PINNED_NEUTRAL_FEELING, e);
            }
        }
        neutralFeeling = value;
        return value;
    }

    private static Class<? extends PathfinderMob> cached;
    private static State state;
    private static boolean resolved;
    private static Method feelingToward;
    private static boolean feelingResolved;
    private static boolean warnedFeeling;
    private static Method getStayAnchor;
    private static boolean stayAnchorResolved;
    private static boolean warnedStayAnchor;
    private static Field attackOrder;
    private static boolean attackOrderResolved;
    private static boolean warnedAttackOrder;
    private static Method fightFlight;
    private static Method friendliness;
    private static boolean dispositionResolved;
    private static boolean warnedDisposition;

    private PlayerMobs() {
    }

    @SuppressWarnings("unchecked")
    public static synchronized Class<? extends PathfinderMob> playerMobClass() {
        if (resolved) {
            return cached;
        }
        resolved = true;
        try {
            Class<?> c = Class.forName(PLAYER_MOB_CLASS);
            if (PathfinderMob.class.isAssignableFrom(c)) {
                cached = (Class<? extends PathfinderMob>) c;
                state = State.FOUND;
            } else {
                // Refuse rather than risk a ClassCastException deep inside a goal.
                state = State.HIERARCHY_CHANGED;
            }
        } catch (ClassNotFoundException e) {
            state = State.ABSENT; // expected on runs without SPM, not an error
        }
        return cached;
    }

    public static State state() {
        playerMobClass();
        return state;
    }

    public static boolean available() {
        return playerMobClass() != null;
    }

    /** True when {@code mob} is a PlayerMob. */
    public static boolean isPlayerMob(Mob mob) {
        Class<? extends PathfinderMob> type = playerMobClass();
        return type != null && type.isInstance(mob);
    }

    /**
     * Read SPM's persisted public disposition traits without becoming a second personality owner.
     * Returns {@code null} when the optional host or either accessor is unavailable; GAO-7 then
     * uses neutral host anchors and deterministic addon latent traits.
     */
    public static Disposition disposition(Mob mob) {
        if (!isPlayerMob(mob)) {
            return null;
        }
        resolveDispositionMethods();
        if (fightFlight == null || friendliness == null) {
            return null;
        }
        try {
            Object fight = fightFlight.invoke(mob);
            Object friendly = friendliness.invoke(mob);
            if (fight instanceof Number fightNumber && friendly instanceof Number friendlyNumber) {
                return new Disposition(fightNumber.intValue(), friendlyNumber.intValue());
            }
            warnDispositionUnavailable(new IllegalStateException(
                    "SPM disposition accessors returned non-numeric values"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fightFlight = null;
            friendliness = null;
            dispositionResolved = true;
            warnDispositionUnavailable(e);
        }
        return null;
    }

    private static synchronized void resolveDispositionMethods() {
        if (dispositionResolved) {
            return;
        }
        dispositionResolved = true;
        Class<? extends PathfinderMob> type = playerMobClass();
        if (type == null) {
            return;
        }
        try {
            fightFlight = type.getMethod("fightFlight");
            friendliness = type.getMethod("friendliness");
        } catch (NoSuchMethodException e) {
            fightFlight = null;
            friendliness = null;
            warnDispositionUnavailable(e);
        }
    }

    private static void warnDispositionUnavailable(Throwable cause) {
        if (warnedDisposition) {
            return;
        }
        warnedDisposition = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] PlayerMob disposition accessors are unavailable; GAO-7 uses "
                        + "neutral host anchors. This mod likely needs an update.",
                cause);
    }

    /**
     * How {@code self} feels about {@code other} on SPM's 0-10 scale, or {@code null} when the
     * reading is unavailable.
     *
     * <h2>Why reflection, and why read-only</h2>
     *
     * {@code PlayerMobEntity.feelingToward(LivingEntity)} is <b>public</b> — this reads the same
     * value SPM's own {@code findFollowTarget()} gates on, through the same entry point, so a
     * companion decision here cannot disagree with SPM's idea of who is friends with whom.
     *
     * <p>The ledger behind it ({@code FeelingLedger feelings}) is a <b>private final field with no
     * public mutator</b>, and this mod deliberately does not reach for it. Reading another mod's
     * relationship state to decide our own behaviour is ordinary integration; writing to it would
     * make this addon a silent author of SPM's social economy.
     *
     * <p>Fails closed. If the method is missing — SPM renamed or removed it — this returns
     * {@code null} once, warns once, and every caller treats that as "not a companion" rather than
     * guessing. An unreadable relationship must not become an assumed friendship.
     */
    public static Float feelingToward(Mob self, LivingEntity other) {
        if (!isPlayerMob(self)) {
            return null;
        }
        Method method = feelingMethod();
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(self, other);
            return value instanceof Float feeling ? feeling : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            feelingToward = null;
            feelingResolved = true;
            warnFeelingUnavailable(e);
            return null;
        }
    }

    private static synchronized Method feelingMethod() {
        if (feelingResolved) {
            return feelingToward;
        }
        feelingResolved = true;
        Class<? extends PathfinderMob> type = playerMobClass();
        if (type == null) {
            return null;
        }
        try {
            feelingToward = type.getMethod("feelingToward", LivingEntity.class);
        } catch (NoSuchMethodException e) {
            warnFeelingUnavailable(e);
        }
        return feelingToward;
    }

    private static void warnFeelingUnavailable(Throwable cause) {
        if (warnedFeeling) {
            return;
        }
        warnedFeeling = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] PlayerMob.feelingToward is unavailable; travelling companions "
                        + "disabled. This mod likely needs an update.", cause);
    }

    /**
     * Whether SPM currently tethers this mob to a position or entity.
     *
     * <p>The public {@code PlayerMobEntity#getStayAnchor()} is the semantic source of truth used by
     * SPM's own {@code StayNearGoal}. Returning {@link StayAnchorState#UNAVAILABLE} separately from
     * {@code ABSENT} is deliberate: callers must not let a renamed API silently bypass a persistent
     * player order. The integration warns once and can disable only the behavior that needs this
     * state.</p>
     */
    public static StayAnchorState stayAnchorState(Mob mob) {
        if (!isPlayerMob(mob)) {
            return StayAnchorState.UNAVAILABLE;
        }
        Method method = stayAnchorMethod();
        if (method == null) {
            return StayAnchorState.UNAVAILABLE;
        }
        try {
            return method.invoke(mob) == null ? StayAnchorState.ABSENT : StayAnchorState.PRESENT;
        } catch (ReflectiveOperationException | RuntimeException e) {
            getStayAnchor = null;
            stayAnchorResolved = true;
            warnStayAnchorUnavailable(e);
            return StayAnchorState.UNAVAILABLE;
        }
    }

    private static synchronized Method stayAnchorMethod() {
        if (stayAnchorResolved) {
            return getStayAnchor;
        }
        stayAnchorResolved = true;
        Class<? extends PathfinderMob> type = playerMobClass();
        if (type == null) {
            return null;
        }
        try {
            getStayAnchor = type.getMethod("getStayAnchor");
        } catch (NoSuchMethodException e) {
            warnStayAnchorUnavailable(e);
        }
        return getStayAnchor;
    }

    private static void warnStayAnchorUnavailable(Throwable cause) {
        if (warnedStayAnchor) {
            return;
        }
        warnedStayAnchor = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] PlayerMob.getStayAnchor is unavailable; exploration disabled "
                        + "so it cannot conflict with persistent stay-near orders. This mod likely "
                        + "needs an update.", cause);
    }

    /**
     * Whether the current target is pinned by SPM's explicit player-issued attack order. The
     * source field remains transient and private in v0.96.0, so this optional compatibility read fails
     * closed: an unknown target never gains permission to abandon nighttime shelter.
     */
    public static AttackOrderState attackOrderState(Mob mob) {
        if (!isPlayerMob(mob)) {
            return AttackOrderState.UNAVAILABLE;
        }
        Field field = attackOrderField();
        if (field == null) {
            return AttackOrderState.UNAVAILABLE;
        }
        try {
            return field.get(mob) == null ? AttackOrderState.ABSENT : AttackOrderState.PRESENT;
        } catch (IllegalAccessException | RuntimeException e) {
            attackOrder = null;
            attackOrderResolved = true;
            warnAttackOrderUnavailable(e);
            return AttackOrderState.UNAVAILABLE;
        }
    }

    private static synchronized Field attackOrderField() {
        if (attackOrderResolved) {
            return attackOrder;
        }
        attackOrderResolved = true;
        Class<? extends PathfinderMob> type = playerMobClass();
        if (type == null) {
            return null;
        }
        try {
            attackOrder = type.getDeclaredField("attackOrder");
            attackOrder.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            attackOrder = null;
            warnAttackOrderUnavailable(e);
        }
        return attackOrder;
    }

    private static void warnAttackOrderUnavailable(Throwable cause) {
        if (warnedAttackOrder) {
            return;
        }
        warnedAttackOrder = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] PlayerMob attack-order provenance is unavailable; unknown targets "
                        + "cannot override arrived night shelter. This mod likely needs an update.",
                cause);
    }

    /**
     * The mob's backpack, via the <b>vanilla</b> {@link InventoryCarrier} interface, or {@code null}
     * when this mob does not carry one.
     */
    public static Container backpack(Mob mob) {
        return mob instanceof InventoryCarrier carrier ? carrier.getInventory() : null;
    }
}
