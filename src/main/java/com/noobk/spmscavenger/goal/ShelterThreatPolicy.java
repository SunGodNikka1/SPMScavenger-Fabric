package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/** Pure bounded provenance policy: a target is not automatically an emergency. */
final class ShelterThreatPolicy {

    private static final int RECENT_HURT_TICKS = 100;
    private static final double NEARBY_THREAT_SQR = 12.0 * 12.0;

    enum Threat {
        NONE,
        PLAYER_ORDERED_COMBAT,
        SELF_DEFENCE,
        NEARBY_HOSTILE,
        UNKNOWN_OR_PROACTIVE
    }

    private ShelterThreatPolicy() {
    }

    record Evidence(
            boolean playerOrderedCombat,
            boolean recentlyHurt,
            boolean hasTarget,
            boolean targetIsVanillaEnemy,
            boolean targetIsActiveThreat,
            boolean targetIsNearby,
            boolean targetVisible) {
    }

    static Threat classify(Mob mob) {
        LivingEntity attacker = mob.getLastHurtByMob();
        boolean recentAttack = attacker != null
                && attacker.isAlive()
                && mob.tickCount - mob.getLastHurtByMobTimestamp() <= RECENT_HURT_TICKS;
        LivingEntity target = mob.getTarget();
        return classify(new Evidence(
                PlayerMobs.attackOrderState(mob) == PlayerMobs.AttackOrderState.PRESENT,
                mob.hurtTime > 0 || recentAttack,
                target != null,
                target instanceof Enemy,
                target instanceof Mob threat && threat.getTarget() != null,
                target != null && target.isAlive()
                        && mob.distanceToSqr(target) <= NEARBY_THREAT_SQR,
                target != null && mob.hasLineOfSight(target)));
    }

    static Threat classify(Evidence evidence) {
        if (evidence.playerOrderedCombat()) {
            return Threat.PLAYER_ORDERED_COMBAT;
        }
        if (evidence.recentlyHurt()) {
            return Threat.SELF_DEFENCE;
        }
        if (!evidence.hasTarget()) {
            return Threat.NONE;
        }
        if (evidence.targetIsVanillaEnemy()
                && evidence.targetIsActiveThreat()
                && evidence.targetIsNearby()
                && evidence.targetVisible()) {
            return Threat.NEARBY_HOSTILE;
        }
        // Includes SPM HuntForFood's passive Animal target and targets whose origin is not proven.
        return Threat.UNKNOWN_OR_PROACTIVE;
    }

    static boolean overridesShelter(Mob mob) {
        return overridesShelter(classify(mob));
    }

    static boolean overridesShelter(Threat threat) {
        return switch (threat) {
            case PLAYER_ORDERED_COMBAT, SELF_DEFENCE, NEARBY_HOSTILE -> true;
            case NONE, UNKNOWN_OR_PROACTIVE -> false;
        };
    }
}
