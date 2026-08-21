package com.noobk.spmscavenger.village;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * D-VR-080 — one server-global village-work policy store per mob.
 *
 * <p>Hosted on the Overworld's {@link DimensionDataStorage} only. {@link VillageScenarioProfile#NEUTRAL}
 * is represented by <b>absence</b> — tracked entries are mobs with an explicit non-default policy.
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>mob {@code UUID}</td></tr>
 *   <tr><th>Bound</th><td>one tiny entry per explicitly assigned mob — no silent cap eviction</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #forgetEverywhere} via {@code PerMobSavedData.forgetAll}
 *       on permanent removal only</td></tr>
 *   <tr><th>Chunk unload / dimension change</th><td><b>preserve</b></td></tr>
 *   <tr><th>Server stop</th><td>persist ally rows only</td></tr>
 * </table>
 */
public final class PlayerMobVillagePolicySavedData extends SavedData {

    public static final String DATA_NAME = "spmscavenger_village_policy";

    private final Map<UUID, VillageScenarioProfile> assignments = new HashMap<>();

    public PlayerMobVillagePolicySavedData() {
    }

    private static Factory<PlayerMobVillagePolicySavedData> factory() {
        return new Factory<>(PlayerMobVillagePolicySavedData::new,
                PlayerMobVillagePolicySavedData::load, DataFixTypes.LEVEL);
    }

    /** Allocating accessor — writes only. */
    public static PlayerMobVillagePolicySavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    /** Non-creating accessor — never materializes the save file. */
    public static PlayerMobVillagePolicySavedData peek(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage().get(factory(), DATA_NAME);
    }

    /**
     * Read-only profile lookup — {@link VillageScenarioProfile#NEUTRAL} when absent; never
     * materializes storage.
     */
    public static VillageScenarioProfile profileOf(MinecraftServer server, UUID mobId) {
        if (mobId == null) {
            return VillageScenarioProfile.NEUTRAL;
        }
        PlayerMobVillagePolicySavedData data = peek(server);
        if (data == null) {
            return VillageScenarioProfile.NEUTRAL;
        }
        return data.readProfile(mobId);
    }

    public static void setProfile(MinecraftServer server, UUID mobId, VillageScenarioProfile profile) {
        if (server == null || mobId == null || profile == null) {
            return;
        }
        if (profile == VillageScenarioProfile.NEUTRAL) {
            forget(server, mobId);
            return;
        }
        if (profile == VillageScenarioProfile.VILLAGE_ALLY) {
            get(server).assignAlly(mobId);
        }
    }

    /**
     * RET-1a — non-creating removal. Returns whether an assignment existed.
     */
    public static boolean forget(MinecraftServer server, UUID mobId) {
        if (server == null || mobId == null) {
            return false;
        }
        PlayerMobVillagePolicySavedData data = peek(server);
        if (data == null) {
            return false;
        }
        return data.removeAssignment(mobId);
    }

    /** RET-1e contract surface — single canonical store, not a per-dimension sweep. */
    public static int forgetEverywhere(MinecraftServer server, UUID mobId) {
        return forget(server, mobId) ? 1 : 0;
    }

    VillageScenarioProfile readProfile(UUID mobId) {
        VillageScenarioProfile stored = assignments.get(mobId);
        return stored == null ? VillageScenarioProfile.NEUTRAL : stored;
    }

    void assignAlly(UUID mobId) {
        assignments.put(mobId, VillageScenarioProfile.VILLAGE_ALLY);
        setDirty();
    }

    boolean removeAssignment(UUID mobId) {
        if (assignments.remove(mobId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    int trackedCount() {
        return assignments.size();
    }

    public static PlayerMobVillagePolicySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerMobVillagePolicySavedData data = new PlayerMobVillagePolicySavedData();
        ListTag list = tag.getList("assignments", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("mob") || !entry.contains("profile", Tag.TAG_STRING)) {
                continue;
            }
            VillageScenarioProfile profile = VillageScenarioProfile.fromSerialized(entry.getString("profile"));
            if (profile == VillageScenarioProfile.VILLAGE_ALLY) {
                data.assignments.put(entry.getUUID("mob"), VillageScenarioProfile.VILLAGE_ALLY);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, VillageScenarioProfile> entry : assignments.entrySet()) {
            if (entry.getValue() != VillageScenarioProfile.VILLAGE_ALLY) {
                continue;
            }
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("mob", entry.getKey());
            wrapped.putString("profile", VillageScenarioProfile.VILLAGE_ALLY.serialized());
            list.add(wrapped);
        }
        if (!list.isEmpty()) {
            tag.put("assignments", list);
        }
        return tag;
    }
}
