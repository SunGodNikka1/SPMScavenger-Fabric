package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * D-VR-081 — explicit operator storage permission registry.
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>canonical {@link GlobalPos}</td></tr>
 *   <tr><th>Bound</th><td>one row per logical container; shared set bounded by operator use</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #forgetEverywhere}, {@link #revokeKey}, lifecycle invalidation</td></tr>
 *   <tr><th>Chunk unload</th><td><b>preserve</b></td></tr>
 * </table>
 */
public final class StoragePermissionSavedData extends SavedData implements GrantSnapshot {

    public static final String DATA_NAME = "spmscavenger_storage_permissions";

    private final Map<GlobalPos, GrantRow> grants = new HashMap<>();
    private final Map<UUID, Set<GlobalPos>> reverseIndex = new HashMap<>();

    public StoragePermissionSavedData() {
    }

    private record GrantRow(UUID owner, Set<UUID> shared) {
        GrantRow copy() {
            return new GrantRow(owner, new HashSet<>(shared));
        }
    }

    private static Factory<StoragePermissionSavedData> factory() {
        return new Factory<>(StoragePermissionSavedData::new, StoragePermissionSavedData::load,
                DataFixTypes.LEVEL);
    }

    public static StoragePermissionSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static StoragePermissionSavedData peek(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage().get(factory(), DATA_NAME);
    }

    /** Hot-path explicit grant check — owner or shared mob. */
    public boolean hasExplicitPermission(GlobalPos key, UUID mobId) {
        if (key == null || mobId == null) {
            return false;
        }
        GrantRow row = grants.get(key);
        if (row == null) {
            return false;
        }
        return mobId.equals(row.owner()) || row.shared().contains(mobId);
    }

    @Override
    public boolean hasOwner(GlobalPos key, UUID mobId) {
        if (key == null || mobId == null) {
            return false;
        }
        GrantRow row = grants.get(key);
        return row != null && mobId.equals(row.owner());
    }

    @Override
    public boolean isSharedWith(GlobalPos key, UUID mobId) {
        if (key == null || mobId == null) {
            return false;
        }
        GrantRow row = grants.get(key);
        return row != null && row.shared().contains(mobId);
    }

    public boolean grantOwner(GlobalPos key, UUID owner) {
        if (key == null || owner == null) {
            return false;
        }
        GrantRow existing = grants.get(key);
        Set<UUID> shared = existing == null ? new HashSet<>() : new HashSet<>(existing.shared());
        if (existing != null && owner.equals(existing.owner())) {
            return false;
        }
        if (existing != null && existing.owner() != null) {
            removeFromReverse(existing.owner(), key);
        }
        grants.put(key, new GrantRow(owner, shared));
        addToReverse(owner, key);
        setDirty();
        return true;
    }

    public boolean addShare(GlobalPos key, UUID mobId) {
        if (key == null || mobId == null) {
            return false;
        }
        GrantRow row = grants.get(key);
        if (row == null) {
            row = new GrantRow(null, new HashSet<>());
            grants.put(key, row);
        }
        if (row.shared().contains(mobId)) {
            return false;
        }
        row.shared().add(mobId);
        addToReverse(mobId, key);
        setDirty();
        return true;
    }

    public boolean removeShare(GlobalPos key, UUID mobId) {
        if (key == null || mobId == null) {
            return false;
        }
        GrantRow row = grants.get(key);
        if (row == null || !row.shared().remove(mobId)) {
            return false;
        }
        removeFromReverse(mobId, key);
        pruneEmpty(key);
        setDirty();
        return true;
    }

    public boolean revokeKey(GlobalPos key) {
        if (key == null) {
            return false;
        }
        GrantRow removed = grants.remove(key);
        if (removed == null) {
            return false;
        }
        if (removed.owner() != null) {
            removeFromReverse(removed.owner(), key);
        }
        for (UUID shared : removed.shared()) {
            removeFromReverse(shared, key);
        }
        setDirty();
        return true;
    }

    /** Lifecycle invalidation — delete row at exact canonical key. */
    public boolean invalidateAt(GlobalPos key) {
        return revokeKey(key);
    }

    public List<GlobalPos> listForMob(UUID mobId) {
        if (mobId == null) {
            return List.of();
        }
        Set<GlobalPos> keys = reverseIndex.get(mobId);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return List.copyOf(keys);
    }

    int grantCount() {
        return grants.size();
    }

    public static int forgetEverywhere(MinecraftServer server, UUID mobId) {
        if (server == null || mobId == null) {
            return 0;
        }
        StoragePermissionSavedData data = peek(server);
        if (data == null) {
            return 0;
        }
        return data.forget(mobId) ? 1 : 0;
    }

    boolean forget(UUID mobId) {
        boolean changed = false;
        List<GlobalPos> owned = new ArrayList<>();
        for (Map.Entry<GlobalPos, GrantRow> entry : grants.entrySet()) {
            GrantRow row = entry.getValue();
            if (mobId.equals(row.owner())) {
                owned.add(entry.getKey());
            }
        }
        for (GlobalPos key : owned) {
            changed |= revokeKey(key);
        }
        Set<GlobalPos> indexed = reverseIndex.get(mobId);
        if (indexed != null) {
            for (GlobalPos key : new ArrayList<>(indexed)) {
                GrantRow row = grants.get(key);
                if (row != null && row.shared().remove(mobId)) {
                    changed = true;
                    pruneEmpty(key);
                }
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private void pruneEmpty(GlobalPos key) {
        GrantRow row = grants.get(key);
        if (row == null) {
            return;
        }
        if (row.owner() == null && row.shared().isEmpty()) {
            grants.remove(key);
        }
    }

    private void addToReverse(UUID mobId, GlobalPos key) {
        reverseIndex.computeIfAbsent(mobId, ignored -> new HashSet<>()).add(key);
    }

    private void removeFromReverse(UUID mobId, GlobalPos key) {
        Set<GlobalPos> keys = reverseIndex.get(mobId);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                reverseIndex.remove(mobId);
            }
        }
    }

    public static StoragePermissionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        ListTag list = tag.getList("grants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("pos", Tag.TAG_COMPOUND)) {
                continue;
            }
            GlobalPos key = GlobalPos.CODEC.parse(
                    registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                    entry.getCompound("pos")).result().orElse(null);
            if (key == null) {
                continue;
            }
            UUID owner = entry.hasUUID("owner") ? entry.getUUID("owner") : null;
            Set<UUID> shared = new HashSet<>();
            if (entry.contains("shared", Tag.TAG_LIST)) {
                ListTag sharedList = entry.getList("shared", Tag.TAG_INT_ARRAY);
                for (int j = 0; j < sharedList.size(); j++) {
                    shared.add(NbtUtils.loadUUID(sharedList.get(j)));
                }
            }
            if (owner == null && shared.isEmpty()) {
                continue;
            }
            data.grants.put(key, new GrantRow(owner, shared));
            if (owner != null) {
                data.addToReverse(owner, key);
            }
            for (UUID mob : shared) {
                data.addToReverse(mob, key);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (grants.isEmpty()) {
            return tag;
        }
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, GrantRow> entry : grants.entrySet()) {
            GrantRow row = entry.getValue();
            if (row.owner() == null && row.shared().isEmpty()) {
                continue;
            }
            CompoundTag wrapped = new CompoundTag();
            wrapped.put("pos", (CompoundTag) GlobalPos.CODEC.encodeStart(
                    registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                    entry.getKey()).result().orElse(new CompoundTag()));
            if (row.owner() != null) {
                wrapped.putUUID("owner", row.owner());
            }
            if (!row.shared().isEmpty()) {
                ListTag sharedList = new ListTag();
                for (UUID mob : row.shared()) {
                    sharedList.add(NbtUtils.createUUID(mob));
                }
                wrapped.put("shared", sharedList);
            }
            list.add(wrapped);
        }
        if (!list.isEmpty()) {
            tag.put("grants", list);
        }
        return tag;
    }
}
