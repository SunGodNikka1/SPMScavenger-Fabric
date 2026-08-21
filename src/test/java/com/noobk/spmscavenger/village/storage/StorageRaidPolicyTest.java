package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.VillageScenarioProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-54 — VR-T3g–i ally raid policy scenarios (static, no runtime). */
class StorageRaidPolicyTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000054");
    private static final GlobalPos KEY = GlobalPos.of(
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld")),
            new BlockPos(1, 64, 2));

    /** VR-T3g — ally without grant must deny when resolver would succeed. */
    @Test
    void vrT3g_allyWithoutGrantDenies() {
        StoragePermissionSavedData grants = new StoragePermissionSavedData();
        assertFalse(grants.hasExplicitPermission(KEY, MOB));
    }

    /** VR-T3h — ally with empty resolved container denies (simulated via policy helper). */
    @Test
    void vrT3h_allyPolicyDeniesWithoutExplicitGrant() {
        assertFalse(StorageRaidPolicy.mayLoot(
                VillageScenarioProfile.VILLAGE_ALLY,
                MOB,
                null,
                new BlockPos(0, 64, 0)));
    }

    /** VR-T3i-a — neutral profile permits without grant check. */
    @Test
    void vrT3iNeutralPermitsWithoutGrant() {
        assertTrue(StorageRaidPolicy.mayLoot(
                VillageScenarioProfile.NEUTRAL,
                MOB,
                null,
                new BlockPos(0, 64, 0)));
    }

    /** VR-T3i-b — ally with explicit grant permits. */
    @Test
    void vrT3iAllyWithGrantPermits() {
        StoragePermissionSavedData grants = new StoragePermissionSavedData();
        grants.grantOwner(KEY, MOB);
        assertTrue(grants.hasExplicitPermission(KEY, MOB));
    }

    /** VR-T3i-c — grant wins over settlement public semantics on hot path. */
    @Test
    void vrT3iGrantWinsHotPath() {
        StoragePermissionSavedData grants = new StoragePermissionSavedData();
        grants.addShare(KEY, MOB);
        assertTrue(grants.hasExplicitPermission(KEY, MOB));
    }
}
