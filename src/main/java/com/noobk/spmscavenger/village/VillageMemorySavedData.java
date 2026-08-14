package com.noobk.spmscavenger.village;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * V1 — per-dimension, per-mob settlement memory (mirrors {@code MiningProjectSavedData}).
 *
 * <p>Dimension-local because a village anchor is a coordinate in one dimension, and because
 * {@code ServerLevel#getRaidAt} — the consumer D-VR-010 will bind — is also per-level.
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>mob {@code UUID} (stable, not minted)</td></tr>
 *   <tr><th>Bound</th><td>live mobs; each entry internally bounded by
 *       {@link MobVillageMemory#MAX_KNOWN_VILLAGES}</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #forget} — called from {@code SpmScavenger}'s
 *       {@code ENTITY_UNLOAD} and {@code AFTER_DEATH} handlers</td></tr>
 *   <tr><th>Server stop</th><td>flushed with the level's data storage</td></tr>
 * </table>
 *
 * <p>Reads use {@link #peek}, which never creates an entry. A mob that has never seen a village must
 * not acquire a memory object merely because something asked whether it had one — the same
 * non-allocating-query rule the Opinion seam follows.
 */
public final class VillageMemorySavedData extends SavedData {

    public static final String DATA_NAME = "spmscavenger_village_memory";

    private final Map<UUID, MobVillageMemory> byMob = new HashMap<>();

    public VillageMemorySavedData() {
    }

    public static VillageMemorySavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(VillageMemorySavedData::new, VillageMemorySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    /** Non-allocating read. */
    public Optional<MobVillageMemory> peek(UUID mob) {
        return Optional.ofNullable(byMob.get(mob));
    }

    /** Allocating: only call when there is something to record. */
    public MobVillageMemory memoryOf(UUID mob) {
        return byMob.computeIfAbsent(mob, ignored -> new MobVillageMemory());
    }

    /**
     * Record an observation against a mob's memory.
     *
     * @return the settlement remembered, or empty when the observation was not a settlement — an
     *     empty observation must not create a memory entry, or every mob standing in open terrain
     *     would acquire one
     */
    public Optional<KnownVillage> record(UUID mob, VillagePerception.Observation observation, long tick) {
        if (observation == null || !observation.isSettlement()) {
            return Optional.empty();
        }
        KnownVillage village =
                memoryOf(mob).remember(observation.anchor(), tick, observation.admittedPoiCount());
        setDirty();
        return Optional.of(village);
    }

    public boolean designateHome(UUID mob, net.minecraft.core.BlockPos anchor) {
        MobVillageMemory memory = byMob.get(mob);
        if (memory == null) {
            return false;
        }
        boolean changed = memory.designateHome(anchor);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** RET-1a — the production eviction call site. */
    public void forget(UUID mob) {
        if (mob != null && byMob.remove(mob) != null) {
            setDirty();
        }
    }

    public int trackedMobCount() {
        return byMob.size();
    }

    public static VillageMemorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageMemorySavedData data = new VillageMemorySavedData();
        ListTag list = tag.getList("mobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("mob")) {
                continue;
            }
            MobVillageMemory memory = MobVillageMemory.load(entry.getCompound("memory"));
            if (memory.size() > 0) {
                data.byMob.put(entry.getUUID("mob"), memory);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, MobVillageMemory> entry : byMob.entrySet()) {
            if (entry.getValue().size() == 0) {
                continue;
            }
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("mob", entry.getKey());
            wrapped.put("memory", entry.getValue().save());
            list.add(wrapped);
        }
        tag.put("mobs", list);
        return tag;
    }
}
