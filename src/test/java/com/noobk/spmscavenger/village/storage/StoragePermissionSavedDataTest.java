package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-54 — permission registry, RET-1, command asymmetry helpers. */
class StoragePermissionSavedDataTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000056");
    private static final GlobalPos KEY = GlobalPos.of(
            net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld")),
            new net.minecraft.core.BlockPos(8, 64, 8));

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** C2 — revoke-key removes stale row without world truth. */
    @Test
    void c2_revokeKeyRemovesStaleRow() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, MOB);
        assertTrue(data.revokeKey(KEY));
        assertFalse(data.hasExplicitPermission(KEY, MOB));
        assertEquals(0, data.grantCount());
    }

    /** RET-1 — forgetEverywhere clears owner and share rows. */
    @Test
    void forgetEverywhereClearsOwnerAndShare() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        UUID other = UUID.randomUUID();
        data.grantOwner(KEY, MOB);
        data.addShare(KEY, other);
        assertTrue(data.forget(MOB));
        assertEquals(0, data.grantCount());
    }

    /** Round-trip persistence preserves canonical GlobalPos key. */
    @Test
    void saveLoadRoundTripPreservesGrant() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, MOB);
        CompoundTag saved = data.save(new CompoundTag(), registries);
        StoragePermissionSavedData loaded = StoragePermissionSavedData.load(saved, registries);
        assertTrue(loaded.hasExplicitPermission(KEY, MOB));
    }

    /** listForMob returns canonical keys suitable for revoke-key. */
    @Test
    void listForMobReturnsCanonicalKeys() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, MOB);
        assertEquals(1, data.listForMob(MOB).size());
        assertEquals(KEY, data.listForMob(MOB).getFirst());
    }
}
