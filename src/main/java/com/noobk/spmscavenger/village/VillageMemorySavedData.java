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
 *   <tr><th>Bound</th><td>{@link #MAX_TRACKED_MOBS} (LRU by newest sighting) and
 *       {@link #MEMORY_TTL_TICKS}; each entry internally bounded by
 *       {@link MobVillageMemory#MAX_KNOWN_VILLAGES}</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #forget} on <b>death only</b>, plus {@link #prune} at load</td></tr>
 *   <tr><th>Death</th><td>deleted — permanent removal</td></tr>
 *   <tr><th>Unload</th><td><b>preserved</b> — see below</td></tr>
 *   <tr><th>Server stop</th><td>flushed with the level's data storage</td></tr>
 * </table>
 *
 * <h2>V1-R1 — unload must not delete semantic memory</h2>
 *
 * The first version evicted from {@code ServerEntityEvents.ENTITY_UNLOAD}. Fabric defines that event
 * as <b>any</b> entity leaving a server world — a chunk unloading, the player walking away — not as
 * death. So a PlayerMob could remember a village across a save/load and still have the record erased
 * simply by wandering out of range, before the memory ever had a chance to matter.
 *
 * <p>The mistake was copying the shape of the neighbouring unload calls without checking their
 * semantics. Those release <b>runtime</b> state — the admission-seam pulse, the parked experience
 * context — which genuinely should die on unload. This is persisted {@code SavedData}. The rule:
 * <b>generic unload parks or releases runtime state; only permanent removal deletes semantic
 * memory.</b>
 *
 * <p>Gate RET-1 still has to hold without that call site, and death alone does not cover a mob
 * removed without a death event (discarded, {@code /kill} on an unloaded entity, a mod removing it).
 * Two load-time bounds close it: a staleness TTL and a total cap. Both are honest about the residual
 * — such an entry survives until the next world load, bounded by {@link #MAX_TRACKED_MOBS}.
 *
 * <p>Reads use {@link #peek}, which never creates an entry. A mob that has never seen a village must
 * not acquire a memory object merely because something asked whether it had one — the same
 * non-allocating-query rule the Opinion seam follows.
 */
public final class VillageMemorySavedData extends SavedData {

    public static final String DATA_NAME = "spmscavenger_village_memory";

    /**
     * 30 in-game days since the mob last saw any village. Long enough that memory outlives the kind
     * of absence unload causes — which is the whole point of the V1-R1 repair — and short enough that
     * a mob which vanished without a death event does not persist forever.
     */
    public static final long MEMORY_TTL_TICKS = 30L * 24_000L;

    /** Hard ceiling per dimension, applied after the TTL, evicting the stalest first. */
    public static final int MAX_TRACKED_MOBS = 256;

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
        // V1-R1: the full quality, not just the admitted count. withheldPoiCount is how much of the
        // settlement the boundary refused, and it is the only signal that distinguishes "small
        // village seen whole" from "big village glimpsed from the edge".
        KnownVillage village = memoryOf(mob).remember(
                observation.anchor(),
                tick,
                new ObservationQuality(observation.admittedPoiCount(), observation.withheldPoiCount()));
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

    /**
     * RET-1a — the production eviction call site, for <b>permanent removal only</b>.
     *
     * <p>Deliberately not wired to {@code ENTITY_UNLOAD}: see the class note. A structural test
     * asserts the unload handler does not reach this class.
     */
    public void forget(UUID mob) {
        if (mob != null && byMob.remove(mob) != null) {
            setDirty();
        }
    }

    public int trackedMobCount() {
        return byMob.size();
    }

    /**
     * RET-1a — the bound that survives losing the unload call site.
     *
     * <p>Runs at load rather than per tick: both limits are about entries whose owning mob is gone,
     * and a gone mob does not produce ticks. Returns the number evicted so a caller can log it.
     */
    public int prune(long now) {
        int before = byMob.size();
        byMob.entrySet().removeIf(entry -> {
            long lastSeen = entry.getValue().lastTouchedTick();
            return entry.getValue().size() == 0 || now - lastSeen > MEMORY_TTL_TICKS;
        });
        while (byMob.size() > MAX_TRACKED_MOBS) {
            UUID stalest = null;
            long stalestTick = Long.MAX_VALUE;
            for (Map.Entry<UUID, MobVillageMemory> entry : byMob.entrySet()) {
                long touched = entry.getValue().lastTouchedTick();
                if (touched < stalestTick) {
                    stalestTick = touched;
                    stalest = entry.getKey();
                }
            }
            if (stalest == null) {
                break;
            }
            byMob.remove(stalest);
        }
        int evicted = before - byMob.size();
        if (evicted > 0) {
            setDirty();
        }
        return evicted;
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
