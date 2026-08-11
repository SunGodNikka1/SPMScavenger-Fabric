package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.UUID;

/** GAO-7 — deterministic host-anchored personality construction (Option A). */
public final class PersonalityFactory {

    private static final float HOST_ANCHOR_WEIGHT = 0.75f;

    private PersonalityFactory() {
    }

    public static PersonalityModel fromMob(Mob mob) {
        Objects.requireNonNull(mob, "mob");
        PlayerMobs.Disposition disposition = PlayerMobs.disposition(mob);
        if (disposition == null) {
            return fromIdentity(mob.getUUID(), null, null);
        }
        return fromIdentity(mob.getUUID(), disposition.fightFlight(), disposition.friendliness());
    }

    /**
     * Pure construction seam. Missing host values use neutral anchors, not assumed aggression or
     * friendliness. UUID latent values still provide stable individuality.
     */
    public static PersonalityModel fromIdentity(
            UUID mobId, Integer fightFlight, Integer friendliness) {
        Objects.requireNonNull(mobId, "mobId");
        float curiosity = latent(mobId, 0x243F6A8885A308D3L);
        float persistence = latent(mobId, 0x13198A2E03707344L);
        float materialism = latent(mobId, 0xA4093822299F31D0L);
        float socialResidual = latent(mobId, 0x082EFA98EC4E6C89L);
        float riskResidual = latent(mobId, 0x452821E638D01377L);
        float adventureResidual = latent(mobId, 0xBE5466CF34E90C6CL);

        float sociability = anchored(normalizeHost(friendliness), socialResidual);
        float riskTolerance = anchored(normalizeHost(fightFlight), riskResidual);
        float adventurousness = 0.40f * curiosity
                + 0.35f * riskTolerance
                + 0.25f * adventureResidual;
        return new PersonalityModel(
                curiosity,
                sociability,
                riskTolerance,
                persistence,
                materialism,
                adventurousness);
    }

    private static float normalizeHost(Integer value) {
        if (value == null) {
            return 0.5f;
        }
        return Math.max(0, Math.min(10, value)) / 10.0f;
    }

    private static float anchored(float host, float residual) {
        return HOST_ANCHOR_WEIGHT * host + (1.0f - HOST_ANCHOR_WEIGHT) * residual;
    }

    private static float latent(UUID id, long salt) {
        long mixed = mix64(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23) ^ salt);
        return (float) ((mixed >>> 40) * 0x1.0p-24);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
