package com.noobk.spmscavenger.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Immutable validation-side capture of the exact AFTER_DEATH callback state. */
record V4SubjectDeathDiagnostics(
        long deathTick,
        long ticksSinceFixtureCreation,
        String damageSourceIdentity,
        String damageType,
        String damageMessageId,
        String localizedDeathMessage,
        UUID directEntityUuid,
        String directEntityType,
        UUID causingEntityUuid,
        String causingEntityType,
        float health,
        float maxHealth,
        String position,
        BlockPos blockPosition,
        String blockAtFeet,
        String blockAtHead,
        String blockBelow,
        boolean onFire,
        float fallDistance,
        String difficulty,
        boolean alive,
        boolean deadOrDying,
        boolean removed,
        String removalReason) {

    static V4SubjectDeathDiagnostics capture(
            ServerLevel level, Mob subject, DamageSource source,
            long fixtureCreationTick, long deathTick) {
        BlockPos blockPos = subject.blockPosition();
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        String type = source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("UNKEYED:" + source.type());
        return new V4SubjectDeathDiagnostics(
                deathTick,
                Math.max(0L, deathTick - fixtureCreationTick),
                source.toString(),
                type,
                source.getMsgId(),
                source.getLocalizedDeathMessage(subject).getString(),
                direct == null ? null : direct.getUUID(),
                entityType(direct),
                causing == null ? null : causing.getUUID(),
                entityType(causing),
                subject.getHealth(),
                subject.getMaxHealth(),
                subject.position().toString(),
                blockPos.immutable(),
                level.getBlockState(blockPos).toString(),
                level.getBlockState(blockPos.above()).toString(),
                level.getBlockState(blockPos.below()).toString(),
                subject.isOnFire(),
                subject.fallDistance,
                level.getDifficulty().getKey(),
                subject.isAlive(),
                subject.isDeadOrDying(),
                subject.isRemoved(),
                subject.getRemovalReason() == null
                        ? "NOT_REMOVED" : subject.getRemovalReason().name());
    }

    List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("deathTick=" + deathTick
                + " ticksSinceFixtureCreation=" + ticksSinceFixtureCreation);
        lines.add("damageSource identity=" + damageSourceIdentity
                + " type=" + damageType + " messageId=" + damageMessageId);
        lines.add("localizedDeathMessage=" + localizedDeathMessage);
        lines.add("directEntity uuid=" + printable(directEntityUuid)
                + " type=" + directEntityType);
        lines.add("causingEntity uuid=" + printable(causingEntityUuid)
                + " type=" + causingEntityType);
        lines.add("subject health=" + health + "/" + maxHealth
                + " position=" + position + " blockPosition=" + blockPosition.toShortString());
        lines.add("blocks feet=" + blockAtFeet + " head=" + blockAtHead
                + " below=" + blockBelow);
        lines.add("onFire=" + onFire + " fallDistance=" + fallDistance
                + " difficulty=" + difficulty);
        lines.add("entityState alive=" + alive + " deadOrDying=" + deadOrDying
                + " removed=" + removed + " removalReason=" + removalReason);
        return List.copyOf(lines);
    }

    private static String entityType(Entity entity) {
        return entity == null
                ? "NONE" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static String printable(Object value) {
        return value == null ? "NONE" : value.toString();
    }
}
