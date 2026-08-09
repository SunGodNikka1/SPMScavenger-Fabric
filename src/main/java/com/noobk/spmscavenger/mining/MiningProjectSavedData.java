package com.noobk.spmscavenger.mining;

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
 * Dimension-local active {@link MiningProject} sessions per mob (MI-7A).
 */
public final class MiningProjectSavedData extends SavedData {

    public static final String DATA_NAME = "spmscavenger_mining_projects";

    private final Map<UUID, MiningProject> byMob = new HashMap<>();

    public MiningProjectSavedData() {
    }

    public static MiningProjectSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(MiningProjectSavedData::new, MiningProjectSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static MiningProjectSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MiningProjectSavedData data = new MiningProjectSavedData();
        ListTag list = tag.getList("projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("mob")) {
                continue;
            }
            UUID mob = entry.getUUID("mob");
            MiningProject project = MiningProject.load(entry.getCompound("project"));
            if (project.shouldPersist()) {
                data.byMob.put(mob, project);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, MiningProject> entry : byMob.entrySet()) {
            MiningProject project = entry.getValue();
            if (!project.shouldPersist()) {
                continue;
            }
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("mob", entry.getKey());
            wrapped.put("project", project.save());
            list.add(wrapped);
        }
        tag.put("projects", list);
        return tag;
    }

    public Optional<MiningProject> projectOf(UUID mobId) {
        return Optional.ofNullable(byMob.get(mobId));
    }

    public void putProject(UUID mobId, MiningProject project) {
        if (project.shouldPersist()) {
            byMob.put(mobId, project);
        } else {
            byMob.remove(mobId);
        }
        setDirty();
    }

    public Optional<MiningProject> completeProject(UUID mobId, MiningProjectEnd end) {
        MiningProject existing = byMob.get(mobId);
        if (existing == null) {
            return Optional.empty();
        }
        MiningProject finished = existing.complete(end);
        putProject(mobId, finished);
        if (!finished.shouldPersist()) {
            byMob.remove(mobId);
            setDirty();
        }
        return Optional.of(finished);
    }

    public void clearProject(UUID mobId) {
        if (byMob.remove(mobId) != null) {
            setDirty();
        }
    }

    /** Test helper — empty data without a world. */
    public static MiningProjectSavedData createEmpty() {
        return new MiningProjectSavedData();
    }
}
