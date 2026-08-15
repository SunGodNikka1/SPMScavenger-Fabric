package com.noobk.spmscavenger.village;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 *   <tr><th>Bound</th><td>{@link #MAX_TRACKED_MOBS} safety valve; each entry internally bounded
 *       by {@link MobVillageMemory#MAX_KNOWN_VILLAGES}</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #forgetEverywhere} (<b>all dimensions</b>), on permanent
 *       removal: {@code AFTER_DEATH}, and {@code ENTITY_UNLOAD} when
 *       {@code RemovalReason.shouldDestroy()}</td></tr>
 *   <tr><th>Death / discard</th><td>deleted — the mob is gone</td></tr>
 *   <tr><th>Chunk unload, dimension change</th><td><b>preserved</b> — see below</td></tr>
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
 * <h2>V1-R2 — memory age is not an owner-liveness signal</h2>
 *
 * The first repair replaced the unload call site with a staleness TTL: prune, at load, any entry whose
 * newest sighting was over 30 in-game days old. That was <b>the same mistake in a new place</b>. Time
 * since a mob last saw a village measures <i>memory freshness</i>; it says nothing about whether the
 * mob still exists. An alive PlayerMob that spends thirty days mining and then crosses a server
 * restart would lose every settlement it knew — including its {@code HOME_VILLAGE}.
 *
 * <p>The correct signal is the owner's own lifecycle, and vanilla publishes it.
 * {@code Entity.RemovalReason.shouldDestroy()} is {@code true} for {@code KILLED} and
 * {@code DISCARDED}, {@code false} for {@code UNLOADED_TO_CHUNK}, {@code UNLOADED_WITH_PLAYER} and
 * {@code CHANGED_DIMENSION} — precisely "this entity is permanently gone". {@code Entity#setRemoved}
 * assigns {@code removalReason} <em>before</em> invoking {@code levelCallback.onRemove} (pinned jar,
 * offsets 9 and 45), and Fabric's {@code ENTITY_UNLOAD} fires downstream of that, so the reason is
 * populated when the handler reads it.
 *
 * <p><b>If village forgetting is ever wanted it is a cognition feature</b> — a memory-decay policy
 * with its own design, tests and player-visible behaviour — not a side effect of garbage collection.
 * Deleting a mob's home because it was busy elsewhere is not memory management.
 *
 * <p>{@link #MAX_TRACKED_MOBS} survives only as a safety valve for the residual case: a mob removed
 * without any lifecycle event reaching us. It should never fire in normal play, so it warns when it
 * does, and its victim ordering is an acknowledged last-resort heuristic, not a correctness
 * mechanism.
 *
 * <p>Reads use {@link #peek}, which never creates an entry. A mob that has never seen a village must
 * not acquire a memory object merely because something asked whether it had one — the same
 * non-allocating-query rule the Opinion seam follows.
 */
public final class VillageMemorySavedData extends SavedData {

    public static final String DATA_NAME = "spmscavenger_village_memory";

    /**
     * Safety valve only, reached solely when mobs vanish without any lifecycle event reaching us.
     *
     * <p>Deliberately generous: 256 memory-holding PlayerMobs in one dimension is already unusual,
     * the cost of an over-large cap is a few kilobytes, and the cost of an over-small one is deleting
     * a live mob's home.
     */
    public static final int MAX_TRACKED_MOBS = 256;

    private final Map<UUID, MobVillageMemory> byMob = new HashMap<>();

    public VillageMemorySavedData() {
    }

    private static Factory<VillageMemorySavedData> factory() {
        return new Factory<>(VillageMemorySavedData::new, VillageMemorySavedData::load, DataFixTypes.LEVEL);
    }

    public static VillageMemorySavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(factory(), DATA_NAME);
    }

    /**
     * Existing memory for this level, or {@code null} — <b>never created</b>.
     *
     * <p>{@code DimensionDataStorage#get} returns the cached instance, else reads from disk only if
     * the file exists, else returns and caches {@code null} (verified in the pinned jar, offsets
     * 0–58). So sweeping every dimension for a dead mob cannot materialise village-memory files for
     * the Nether and End of a world that never had one.
     */
    private static VillageMemorySavedData peekIn(ServerLevel level) {
        return level.getDataStorage().get(factory(), DATA_NAME);
    }

    /** Non-allocating read. */
    public Optional<MobVillageMemory> peek(UUID mob) {
        return Optional.ofNullable(byMob.get(mob));
    }

    /**
     * VR-T1 debug read — non-creating at both the saved-data and mob layers.
     *
     * <p>Uses {@link #peekIn} so a mob that has never perceived a village does not materialise an
     * empty memory file. Does not call {@link VillagePerception#observe} or refresh memory.
     */
    public static Optional<MobVillageMemory> peekMobMemory(ServerLevel level, UUID mob) {
        VillageMemorySavedData data = peekIn(level);
        return data == null ? Optional.empty() : data.peek(mob);
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
        KnownVillage village = memoryOf(mob).remember(
                observation.anchor(),
                tick,
                ObservationQuality.of(observation.coverage(), observation.admittedPoiCount()));
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
     * <p>Callers must first establish that the owner is gone for good: {@code AFTER_DEATH}, or
     * {@code ENTITY_UNLOAD} with {@code RemovalReason.shouldDestroy()}. A plain unload is not
     * permanent removal, and a structural test asserts the unload handler checks the reason.
     */
    public boolean forget(UUID mob) {
        if (mob != null && byMob.remove(mob) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * V1-R3 — permanent removal must clear <b>every</b> dimension, not the one the mob died in.
     *
     * <h2>The leak this closes</h2>
     *
     * Memory is per-dimension, but a mob is not. Villages are overwhelmingly an Overworld feature and
     * PlayerMobs die in the Nether and End, so the ordinary sequence was:
     *
     * <pre>
     * Overworld : perceives villages          -> entry written to the Overworld store
     * -> Nether : CHANGED_DIMENSION           -> shouldDestroy() false, memory correctly preserved
     * -> Nether : KILLED                      -> forget() ran against the NETHER store
     *                                            (which never had an entry)
     * Overworld : entry survives forever, owner permanently gone
     * </pre>
     *
     * The mob keeps its UUID across the transition — {@code Entity#restoreFrom} copies the full NBT
     * and removes only {@code "Dimension"}, and {@code saveWithoutId} writes {@code "UUID"} — which is
     * exactly why preserving the memory on {@code CHANGED_DIMENSION} is right, and exactly why the
     * eventual deletion has to be global.
     *
     * <p>This was not a rare edge case but the <b>common</b> path, and it would have made the
     * {@link #MAX_TRACKED_MOBS} warning fire for an ordinary cause — destroying the signal value of a
     * warning whose whole purpose is to mean "something abnormal happened".
     *
     * @return how many dimensions actually held memory for this mob
     */
    public static int forgetEverywhere(MinecraftServer server, UUID mob) {
        if (server == null || mob == null) {
            return 0;
        }
        List<VillageMemorySavedData> stores = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            VillageMemorySavedData existing = peekIn(level);
            if (existing != null) {
                stores.add(existing);
            }
        }
        return forgetIn(stores, mob);
    }

    /** Testable core of {@link #forgetEverywhere}, free of server plumbing. */
    static int forgetIn(Iterable<VillageMemorySavedData> stores, UUID mob) {
        int cleared = 0;
        for (VillageMemorySavedData store : stores) {
            if (store != null && store.forget(mob)) {
                cleared++;
            }
        }
        return cleared;
    }

    public int trackedMobCount() {
        return byMob.size();
    }

    /**
     * RET-1a — the safety valve, and nothing more.
     *
     * <p>Drops entries holding no villages (no semantic content to lose) and, if the map has somehow
     * exceeded {@link #MAX_TRACKED_MOBS}, sheds least-recently-active entries until it fits.
     *
     * <p><b>The cap's victim ordering is a known-imperfect heuristic.</b> Least recently active is not
     * the same as gone — that is the entire lesson of V1-R2. It is acceptable only because reaching
     * the cap already means something abnormal has happened, and bounded loss beats unbounded growth.
     * Hence the warning rather than silent proceeding.
     *
     * @return the number of entries evicted
     */
    public int prune() {
        int before = byMob.size();
        byMob.entrySet().removeIf(entry -> entry.getValue().size() == 0);

        if (byMob.size() > MAX_TRACKED_MOBS) {
            com.noobk.spmscavenger.SpmScavenger.LOGGER.warn(
                    "Village memory holds {} mobs, above the {} safety cap. Entries are released only"
                            + " on permanent removal, so this means mobs are vanishing without a"
                            + " lifecycle event reaching us. Shedding least-recently-active entries.",
                    byMob.size(), MAX_TRACKED_MOBS);
        }
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
