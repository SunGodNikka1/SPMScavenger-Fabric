package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * The human-looking nonsense: mirroring a nearby player's crouch, and bunny-hopping while chasing.
 *
 * <h2>Why this goal holds no flags</h2>
 *
 * {@code setFlags(EnumSet.noneOf(Flag.class))} — deliberately. Every other goal in this mod claims
 * {@code MOVE} and therefore <em>competes</em>; this one must run <b>alongside</b> combat, fleeing,
 * looting and everything else, because its whole job is to decorate behaviour that another goal is
 * driving. A goal with no flags is never blocked and never blocks, so a mob can chase and hop at the
 * same time. It also means this goal must never touch navigation — it only nudges the pose and the
 * jump control.
 *
 * <h2>Jumping is refused in tight spaces</h2>
 *
 * Bunny-hopping under leaves, in a 2-high corridor or in a doorway looks broken rather than funny —
 * the mob head-bumps, loses speed, and can snag on the block above. So every hop is checked against
 * real collision: {@code noCollision} over the box the mob would occupy mid-jump, plus a clear block
 * above its head. In a forest canopy or a tunnel the mob simply runs normally.
 *
 * <h2>What it does not do</h2>
 *
 * No greeting, no gifting, no bowing. Social Player Mobs owns those, and a second greeting system
 * would be exactly the duplication Gate SPM-2 forbids. This adds gestures in contexts SPM has none
 * for: mirroring a player, and moving while pursuing.
 */
public class AnticsGoal extends RandomLookAroundGoal {

    private final Mob mob;

    private int crouchTicks;
    private int hopCooldown;
    private int mimicCooldown;

    /** How long a mirrored crouch is held after the player stops, so it does not strobe. */
    private static final int CROUCH_HOLD_TICKS = 20;
    /** Minimum gap between mirror reactions — stops a twitching player making the mob vibrate. */
    private static final int MIMIC_COOLDOWN_TICKS = 10;
    /** Roughly a player's hop rhythm rather than every possible tick. */
    private static final int HOP_INTERVAL_TICKS = 11;
    private static final double MIMIC_RANGE = 8.0;
    /** Only hop while actually closing distance; hopping on the spot looks like a glitch. */
    private static final double HOP_MIN_DISTANCE_SQR = 9.0;

    public AnticsGoal(Mob mob) {
        super(mob);
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    /**
     * The superclass supplies only SPM's cosmetic-readout classification, never its look behaviour.
     * {@code ObjectiveReadout#isNoise} treats {@link RandomLookAroundGoal} as background, which is
     * what keeps "Antics" out of the objective line beside the mob's real action.
     */
    @Override
    public void start() {
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        return cfg.enabled && (cfg.mimicry || cfg.bunnyHop);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        if (crouchTicks > 0) {
            mob.setShiftKeyDown(false);
        }
        crouchTicks = 0;
    }

    @Override
    public void tick() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (cfg.mimicry) {
            tickMimicry();
        }
        if (cfg.bunnyHop) {
            tickHop();
        }
    }

    // ---- Mimicry ----------------------------------------------------------

    /**
     * Copies a nearby <b>survival/adventure</b> player's crouch. Creative and spectator players are
     * ignored — the same rule SPM uses ({@code TargetCategory#classify}) — so flying overhead to
     * watch gathering does not make mobs crouch-stare at you when you hold shift to descend.
     */
    private void tickMimicry() {
        if (crouchTicks > 0 && --crouchTicks == 0) {
            mob.setShiftKeyDown(false);
        }
        if (mimicCooldown > 0) {
            mimicCooldown--;
            return;
        }
        // SPM's FriendlyGreetGoal owns crouch-bowing between mobs — do not pile mimic on top.
        if (mob.isCrouching() && crouchTicks == 0) {
            return;
        }
        // A mob mid-errand (gather, shelter, craft) should not mirror the player's shift key.
        if (!mob.getNavigation().isDone()) {
            return;
        }
        // A mob busy fighting is not in the mood.
        if (mob.getTarget() != null) {
            return;
        }
        Player player = mob.level().getNearestPlayer(mob, MIMIC_RANGE);
        if (player == null
                || player.isCreative()
                || player.isSpectator()
                || !mob.getSensing().hasLineOfSight(player)) {
            return;
        }
        if (player.isCrouching()) {
            mob.setShiftKeyDown(true);
            if (mayWriteMimicLook(OpinionFeatureGate.isEnabled())) {
                mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
            crouchTicks = CROUCH_HOLD_TICKS;
            mimicCooldown = MIMIC_COOLDOWN_TICKS;
        }
    }

    /** Opinion-on gaze belongs to the scheduled GAO-8A LOOK owner, never this flagless decorator. */
    static boolean mayWriteMimicLook(boolean opinionEnabled) {
        return !opinionEnabled;
    }

    // ---- Bunny-hopping ----------------------------------------------------

    /** Hops while pursuing something, but only where a hop actually fits. */
    private void tickHop() {
        if (hopCooldown > 0) {
            hopCooldown--;
            return;
        }
        var target = mob.getTarget();
        if (target == null || !mob.onGround()) {
            return;
        }
        if (mob.distanceToSqr(target) < HOP_MIN_DISTANCE_SQR) {
            return; // in melee range: stop clowning and fight
        }
        if (!hasHeadroom()) {
            return;
        }
        mob.getJumpControl().jump();
        hopCooldown = HOP_INTERVAL_TICKS;
    }

    /**
     * True when the mob could rise without hitting anything.
     *
     * <p>Checked against real collision rather than a block lookup, so leaves, slabs, trapdoors and
     * fences all behave correctly — leaves in particular are solid enough to head-bump but would pass
     * a naive {@code isAir} test.
     */
    private boolean hasHeadroom() {
        AABB box = mob.getBoundingBox();
        return mob.level().noCollision(mob, box.move(0.0, 0.6, 0.0))
                && mob.level().noCollision(mob, box.move(0.0, 1.0, 0.0));
    }
}
