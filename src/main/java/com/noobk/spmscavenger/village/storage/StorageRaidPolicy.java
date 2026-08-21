package com.noobk.spmscavenger.village.storage;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * Ally loot enforcement only — explicit grant-or-deny hot path (D-VR-081).
 *
 * <p>Must not import {@link StorageGuardCompatibility} or call {@link StorageOwnershipPolicy}.
 */
public final class StorageRaidPolicy {

    private StorageRaidPolicy() {
    }

    public static boolean mayLoot(Mob mob, ServerLevel level, BlockPos targetPos) {
        if (mob == null || level == null || targetPos == null) {
            return false;
        }
        if (!PlayerMobs.isPlayerMob(mob)) {
            return true;
        }
        return mayLoot(
                PlayerMobVillagePolicySavedData.profileOf(level.getServer(), mob.getUUID()),
                mob.getUUID(),
                level,
                targetPos);
    }

    /** Package-visible for unit tests without a live mob instance. */
    static boolean mayLoot(
            VillageScenarioProfile profile,
            UUID mobId,
            ServerLevel level,
            BlockPos targetPos) {
        if (profile != VillageScenarioProfile.VILLAGE_ALLY) {
            return true;
        }
        Optional<ResolvedContainer> resolved = StorageContainerResolver.resolveLoaded(level, targetPos);
        if (resolved.isEmpty()) {
            return false;
        }
        StoragePermissionSavedData grants = StoragePermissionSavedData.peek(level.getServer());
        if (grants == null) {
            return false;
        }
        return grants.hasExplicitPermission(resolved.get().canonicalGlobal(), mobId);
    }
}
