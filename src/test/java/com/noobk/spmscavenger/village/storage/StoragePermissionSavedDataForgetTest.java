package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-54 R1-1 — selective forget via reverse index. */
class StoragePermissionSavedDataForgetTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SHARED = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
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

    @Test
    void forgetSharedMobPreservesOwnerGrant() {
        StoragePermissionSavedData data = grantOwnerPlusShare();
        assertTrue(data.forget(SHARED));
        assertTrue(data.hasExplicitPermission(KEY, OWNER));
        assertFalse(data.hasExplicitPermission(KEY, SHARED));
        assertTrue(data.listForMob(SHARED).isEmpty());
        assertEquals(1, data.listForMob(OWNER).size());

        StoragePermissionSavedData reloaded = roundTrip(data);
        assertTrue(reloaded.hasExplicitPermission(KEY, OWNER));
        assertFalse(reloaded.hasExplicitPermission(KEY, SHARED));
    }

    @Test
    void forgetOwnerPreservesSharedMobGrant() {
        StoragePermissionSavedData data = grantOwnerPlusShare();
        assertTrue(data.forget(OWNER));
        assertFalse(data.hasExplicitPermission(KEY, OWNER));
        assertTrue(data.hasExplicitPermission(KEY, SHARED));
        assertTrue(data.listForMob(OWNER).isEmpty());
        assertEquals(1, data.listForMob(SHARED).size());

        StoragePermissionSavedData reloaded = roundTrip(data);
        assertFalse(reloaded.hasExplicitPermission(KEY, OWNER));
        assertTrue(reloaded.hasExplicitPermission(KEY, SHARED));
    }

    @Test
    void shareAfterOwnRejectsOwnerOverlap() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, OWNER);
        assertFalse(data.addShare(KEY, OWNER));
        assertTrue(data.hasExplicitPermission(KEY, OWNER));
        assertTrue(data.listForMob(OWNER).contains(KEY));
    }

    @Test
    void forgetOwnerAfterOwnDoesNotLeaveGhostSharePermission() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, OWNER);
        assertFalse(data.addShare(KEY, OWNER));
        assertTrue(data.forget(OWNER));
        assertFalse(data.hasExplicitPermission(KEY, OWNER));
        assertTrue(data.listForMob(OWNER).isEmpty());
        assertEquals(0, data.grantCount());

        StoragePermissionSavedData reloaded = roundTrip(data);
        assertFalse(reloaded.hasExplicitPermission(KEY, OWNER));
        assertTrue(reloaded.listForMob(OWNER).isEmpty());
    }

    @Test
    void loadNormalizesOverlappingOwnerAndShare() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("pos", (CompoundTag) GlobalPos.CODEC.encodeStart(
                registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                KEY).result().orElse(new CompoundTag()));
        entry.putUUID("owner", OWNER);
        net.minecraft.nbt.ListTag shared = new net.minecraft.nbt.ListTag();
        shared.add(net.minecraft.nbt.NbtUtils.createUUID(OWNER));
        entry.put("shared", shared);
        list.add(entry);
        tag.put("grants", list);

        StoragePermissionSavedData loaded = StoragePermissionSavedData.load(tag, registries);
        assertTrue(loaded.hasExplicitPermission(KEY, OWNER));
        assertEquals(1, loaded.listForMob(OWNER).size());
        assertTrue(loaded.forget(OWNER));
        assertFalse(loaded.hasExplicitPermission(KEY, OWNER));
        assertTrue(loaded.listForMob(OWNER).isEmpty());
    }

    @Test
    void forgetSharedMobOnSharedOnlyRowPrunesGrant() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.addShare(KEY, SHARED);
        assertTrue(data.forget(SHARED));
        assertEquals(0, data.grantCount());
        assertTrue(data.listForMob(SHARED).isEmpty());

        StoragePermissionSavedData reloaded = roundTrip(data);
        assertEquals(0, reloaded.grantCount());
        assertFalse(reloaded.hasExplicitPermission(KEY, SHARED));
    }

    private static StoragePermissionSavedData grantOwnerPlusShare() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, OWNER);
        data.addShare(KEY, SHARED);
        return data;
    }

    private static StoragePermissionSavedData roundTrip(StoragePermissionSavedData data) {
        CompoundTag saved = data.save(new CompoundTag(), registries);
        return StoragePermissionSavedData.load(saved, registries);
    }
}
