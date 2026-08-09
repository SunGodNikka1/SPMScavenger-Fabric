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

    /** MI-14C1 - execution lifecycle per mob, kept beside the project it authorizes. */
    private final Map<UUID, MiningExecutionLease> leases = new HashMap<>();

    /** MI-14C2-R1 — authority that survives transition consumption until completion or expiry. */
    private final Map<UUID, MiningExecutionCommitment> commitments = new HashMap<>();

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
        ListTag leaseList = tag.getList("leases", Tag.TAG_COMPOUND);
        for (int index = 0; index < leaseList.size(); index++) {
            CompoundTag wrapped = leaseList.getCompound(index);
            if (!wrapped.hasUUID("mob")) {
                continue;
            }
            data.leases.put(
                    wrapped.getUUID("mob"),
                    MiningExecutionLease.load(wrapped.getCompound("lease")));
        }
        ListTag commitmentList = tag.getList("commitments", Tag.TAG_COMPOUND);
        for (int index = 0; index < commitmentList.size(); index++) {
            CompoundTag wrapped = commitmentList.getCompound(index);
            if (!wrapped.hasUUID("mob")) {
                continue;
            }
            data.commitments.put(
                    wrapped.getUUID("mob"),
                    MiningExecutionCommitment.load(wrapped.getCompound("commitment")));
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

        ListTag leaseList = new ListTag();
        for (Map.Entry<UUID, MiningExecutionLease> entry : leases.entrySet()) {
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("mob", entry.getKey());
            wrapped.put("lease", entry.getValue().save());
            leaseList.add(wrapped);
        }
        tag.put("leases", leaseList);

        ListTag commitmentList = new ListTag();
        for (Map.Entry<UUID, MiningExecutionCommitment> entry : commitments.entrySet()) {
            CompoundTag wrapped = new CompoundTag();
            wrapped.putUUID("mob", entry.getKey());
            wrapped.put("commitment", entry.getValue().save());
            commitmentList.add(wrapped);
        }
        tag.put("commitments", commitmentList);
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

    /** MI-14C1 - the lease authorizing this mob's assignment, if one was issued. */
    public Optional<MiningExecutionLease> leaseOf(UUID mobId) {
        return Optional.ofNullable(leases.get(mobId));
    }

    public void putLease(UUID mobId, MiningExecutionLease lease) {
        leases.put(mobId, lease);
        setDirty();
    }

    public void clearLease(UUID mobId) {
        if (leases.remove(mobId) != null) {
            setDirty();
        }
    }

    /** MI-14C2-R1 — active execution commitment, if any. */
    public Optional<MiningExecutionCommitment> commitmentOf(UUID mobId) {
        return Optional.ofNullable(commitments.get(mobId));
    }

    public void putCommitment(UUID mobId, MiningExecutionCommitment commitment) {
        commitments.put(mobId, commitment);
        setDirty();
    }

    public void clearCommitment(UUID mobId) {
        if (commitments.remove(mobId) != null) {
            setDirty();
        }
    }

    /**
     * Atomically consumes a {@code CAVE_FOUND} transition and installs the continuation commitment.
     *
     * @return false when the expected handoff is no longer pending
     */
    public boolean claimCaveContinuation(UUID mobId, MiningTransition expected, long now) {
        Optional<MiningTransition> pending = pendingTransition(mobId);
        if (pending.isEmpty() || !pending.get().equals(expected)) {
            return false;
        }
        consumeTransition(mobId);
        putCommitment(mobId, MiningExecutionCommitment.caveContinuation(expected, now));
        return true;
    }

    /** Drops expired commitments so intent derivation does not resurrect stale authority. */
    public void pruneExpiredCommitments(UUID mobId, long now) {
        MiningExecutionCommitment commitment = commitments.get(mobId);
        if (commitment != null && !commitment.isActive(now)) {
            clearCommitment(mobId);
        }
    }

    public boolean hasActiveCaveContinuation(UUID mobId, long now) {
        pruneExpiredCommitments(mobId, now);
        return commitmentOf(mobId)
                .filter(commitment -> commitment.kind() == ExecutionCommitmentKind.CAVE_CONTINUATION)
                .filter(commitment -> commitment.isActive(now))
                .isPresent();
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
