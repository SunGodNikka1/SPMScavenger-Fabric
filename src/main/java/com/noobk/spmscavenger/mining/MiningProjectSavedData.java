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

    /**
     * MI-14A — outcomes awaiting a consumer. Kept separately because a finished project is
     * <b>deleted</b>: every handoff reason maps to {@code SUCCESS} and {@code shouldPersist()} keeps
     * only RUNNING / INTERRUPTED / RETRY, so the outcome would vanish in the same call that made it.
     */
    private final Map<UUID, MiningTransition> pendingTransitions = new HashMap<>();

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
                ListTag transitions = tag.getList("transitions", 10);
        for (int index = 0; index < transitions.size(); index++) {
            CompoundTag wrapped = transitions.getCompound(index);
            data.pendingTransitions.put(
                    wrapped.getUUID("mob"), MiningTransition.load(wrapped.getCompound("transition")));
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

        ListTag transitions = new ListTag();
        for (Map.Entry<UUID, MiningTransition> entry : pendingTransitions.entrySet()) {
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("mob", entry.getKey());
            wrapped.put("transition", entry.getValue().save());
            transitions.add(wrapped);
        }
        tag.put("transitions", transitions);
        return tag;
    }

    /** Records an outcome for a later consumer. Overwrites any unconsumed one for that mob. */
    public void recordTransition(UUID mobId, MiningTransition transition) {
        pendingTransitions.put(mobId, transition);
        setDirty();
    }

    /** Reads without consuming — for admission checks that must not clear the claim. */
    public Optional<MiningTransition> pendingTransition(UUID mobId) {
        return Optional.ofNullable(pendingTransitions.get(mobId));
    }

    /** Reads and clears. The consumer that acts on an outcome owns removing it. */
    public Optional<MiningTransition> consumeTransition(UUID mobId) {
        MiningTransition taken = pendingTransitions.remove(mobId);
        if (taken != null) {
            setDirty();
        }
        return Optional.ofNullable(taken);
    }

    public void clearTransition(UUID mobId) {
        if (pendingTransitions.remove(mobId) != null) {
            setDirty();
        }
    }

    /**
     * Completes a project and preserves its outcome atomically, so the transition cannot be lost to
     * the removal that {@code completeProject} performs for terminal lifecycles.
     */
    public Optional<MiningProject> completeProject(
            UUID mobId, MiningProjectEnd end, MiningTransition transition) {
        recordTransition(mobId, transition);
        return completeProject(mobId, end);
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
