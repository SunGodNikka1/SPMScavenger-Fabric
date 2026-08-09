package com.noobk.spmscavenger.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import net.minecraft.core.BlockPos;
import java.util.List;
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

    /**
     * D-MIW-TS2 - exposure offered by an active project to a downstream consumer.
     *
     * <p>Runtime-only: an opportunity is worth at most {@code OFFER_LIFETIME_TICKS}, so persisting
     * it across a restart would only ever resurrect something stale. The project it belongs to is
     * persisted, so the tunnel resumes correctly and simply offers the next cell it cuts.
     */
    private final Map<UUID, ExposureOpportunity> exposures = new HashMap<>();

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

    /** D-MIW-TS2 - the exposure this mob's active project is currently offering, if any. */
    public Optional<ExposureOpportunity> exposureOf(UUID mobId) {
        return Optional.ofNullable(exposures.get(mobId));
    }

    /**
     * Records a fresh excavation boundary, replacing any earlier offer.
     *
     * <p>An unprobed offer being overwritten by the next cut is correct: the newer boundary is the
     * one the mob is standing at. An offer that is already {@code ACQUIRING} is left alone, because
     * the consumer is mid-vein and the producer should not be cutting anyway.
     */
    public void offerExposure(UUID mobId, MiningProject project, List<BlockPos> openedCells,
            long now) {
        ExposureOpportunity existing = exposures.get(mobId);
        if (existing != null
                && existing.phase() == ExposureOpportunity.Phase.ACQUIRING
                && existing.belongsTo(project)) {
            return;
        }
        exposures.put(mobId, ExposureOpportunity.offer(project, openedCells, now));
    }

    /**
     * TS2-M1 — takes the single probe this offer grants, <b>removing</b> it atomically.
     *
     * <p>The earlier version returned the offer without removing it, so "one probe per exposure"
     * was a comment rather than a mechanism: the same offer could be probed every tick until it
     * expired. Having the right states is not enough if callers can bypass the transitions.
     *
     * <p>Consumed when the probe <b>executes</b>, not when the consumer is merely scheduled — an
     * admission failure upstream must never destroy an opportunity nothing inspected, which is why
     * this is called at the probe rather than at {@code canUse} entry.
     *
     * <p>Finding nothing therefore needs no cleanup call: the offer is already gone. Finding a
     * legitimate target requires handing the evidence back to
     * {@link #beginCooperativeAcquisition}, which re-admits it only for the same session.
     *
     * @return the boundary to inspect, or empty when no probe is on offer
     */
    public Optional<ExposureOpportunity> takeExposureProbe(UUID mobId, MiningProject project,
            long now) {
        ExposureOpportunity offer = exposures.get(mobId);
        if (!ExposureOpportunityPolicy.offersProbe(offer, project, now)) {
            return Optional.empty();
        }
        exposures.remove(mobId);
        return Optional.of(offer);
    }

    /**
     * Re-admits a taken probe as an active acquisition, so vein-follow can finish.
     *
     * <p>Requires the evidence a successful take produced. Without that argument the transition
     * could be driven from whatever happened to be stored, with no proof a probe ran, that it was
     * still {@code OFFERED}, that it was fresh, or that it belonged to the caller's project.
     *
     * @return whether the session was opened
     */
    public boolean beginCooperativeAcquisition(
            UUID mobId, MiningProject project, ExposureOpportunity taken, long now) {
        if (taken == null || project == null
                || taken.phase() != ExposureOpportunity.Phase.OFFERED
                || !taken.belongsTo(project)
                || now - taken.offeredAt() > ExposureOpportunityPolicy.OFFER_LIFETIME_TICKS) {
            return false;
        }
        exposures.put(mobId, taken.acquiring(now));
        return true;
    }

    /**
     * Restarts the vein idle clock after a cooperative take.
     *
     * <p>Only refreshes a live {@code ACQUIRING} session for this project: an {@code OFFERED} offer
     * must not have its lifetime extended, or the 100-tick freshness bound becomes advisory.
     */
    public boolean noteCooperativeAcquisition(UUID mobId, MiningProject project, long now) {
        ExposureOpportunity active = exposures.get(mobId);
        if (!ExposureOpportunityPolicy.holdsCooperativeSession(active, project, now)) {
            return false;
        }
        exposures.put(mobId, active.withActivity(now));
        return true;
    }

    public void clearExposure(UUID mobId) {
        exposures.remove(mobId);
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
    public boolean claimCaveContinuation(
            UUID mobId, MiningTransition expected, long now, int authorityTicks) {
        Optional<MiningTransition> pending = pendingTransition(mobId);
        if (pending.isEmpty() || !pending.get().equals(expected)) {
            return false;
        }
        consumeTransition(mobId);
        putCommitment(
                mobId, MiningExecutionCommitment.caveContinuation(expected, now, authorityTicks));
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
