package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * V1 — one mob's settlement memory.
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>settlement anchor, plus stable villager UUID within a settlement</td></tr>
 *   <tr><th>Bound</th><td>{@link #MAX_KNOWN_VILLAGES}; 16 traders/settlement, 64/mob,
 *       16 capability hints/trader; hints expire after 168,000 ticks</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #remember} (LRU, every call) and
 *       {@link #observeTrader} (trader bounds + physical TTL pruning), plus
 *       {@code VillageMemorySavedData#forget} on permanent removal</td></tr>
 *   <tr><th>Death</th><td>deleted — the mob is permanently gone</td></tr>
 *   <tr><th>Unload</th><td><b>preserved</b>. V1-R1: this is persistent semantic memory, not runtime
 *       state. Deleting it on a generic unload meant a mob could remember villages through NBT and
 *       still have the record erased before it ever mattered.</td></tr>
 *   <tr><th>Server stop</th><td>persisted with the level; not runtime state</td></tr>
 * </table>
 *
 * <p>The key is the anchor rather than a minted id on purpose (RET-1b). A freshly generated
 * {@code UUID} per discovery would look tidy and would be unbounded even for one mob pacing through
 * one village — every pass would mint a new settlement.
 *
 * <p>The bound is not defensive padding. An exploring PlayerMob crosses villages indefinitely, so
 * without a cap this list is a slow leak that no unit test can observe: it compiles, its tests are
 * green in milliseconds, and it shows up as heap an hour in.
 */
public final class MobVillageMemory {

    /**
     * Why 16: the mob only needs enough settlements to choose between (D-VR-009's site score) and to
     * find its way home. Beyond that, the least-recently-seen village is one the mob has not visited
     * in a long time and can rediscover in a single pass — cheap to lose, unbounded to keep.
     */
    public static final int MAX_KNOWN_VILLAGES = 16;
    public static final int MAX_KNOWN_TRADERS_PER_SETTLEMENT = 16;
    public static final int MAX_KNOWN_TRADERS_PER_MOB = 64;

    private final List<KnownVillage> villages = new ArrayList<>();
    private final Map<BlockPos, SettlementRelationship> relationships = new HashMap<>();
    /** D-VR-090 — bounded positive evidence, owned by the same lifecycle as settlement memory. */
    private final Map<UUID, KnownVillager> knownTraders = new HashMap<>();
    /** D-VR-089 — one factual home, independent of every settlement's other facts. */
    private BlockPos homeAnchor;
    /** Load-only signal used by the owning SavedData to schedule canonical schema rewrite. */
    private boolean legacySchemaLoaded;

    public List<KnownVillage> villages() {
        return List.copyOf(villages);
    }

    public List<KnownVillager> knownTraders() {
        return knownTraders.values().stream()
                .sorted(java.util.Comparator.comparing(KnownVillager::villagerId))
                .toList();
    }

    public Optional<KnownVillager> knownTrader(UUID villagerId) {
        return Optional.ofNullable(knownTraders.get(villagerId));
    }

    public List<KnownVillager> knownTradersAt(BlockPos anchor) {
        KnownVillage settlement = at(anchor).orElse(null);
        if (settlement == null) {
            return List.of();
        }
        return knownTraders.values().stream()
                .filter(trader -> trader.settlementAnchor().equals(settlement.anchor()))
                .sorted(java.util.Comparator.comparing(KnownVillager::villagerId))
                .toList();
    }

    /**
     * Records one complete live board against an already remembered settlement.
     *
     * @return whether persistent state changed; unknown settlements fail closed and allocate nothing
     */
    public boolean observeTrader(BlockPos settlementAnchor, UUID villagerId,
            ResourceLocation profession, int level, List<ItemStack> outputs, long tick) {
        KnownVillage settlement = at(settlementAnchor).orElse(null);
        if (settlement == null || villagerId == null) {
            return false;
        }
        boolean changed = pruneExpiredTraderCapabilities(tick);
        KnownVillager trader = knownTraders.get(villagerId);
        if (trader == null) {
            trader = new KnownVillager(villagerId, settlement.anchor(), profession, level, tick);
            knownTraders.put(villagerId, trader);
            changed = true;
        } else if (!trader.settlementAnchor().equals(settlement.anchor())) {
            trader.rekey(settlement.anchor());
            changed = true;
        }
        changed |= trader.observe(profession, level, outputs, tick);
        changed |= evictKnownTraderBounds();
        return changed;
    }

    /** Physical TTL pruning; trader identity deliberately survives with no hints. */
    public boolean pruneExpiredTraderCapabilities(long now) {
        boolean changed = false;
        for (KnownVillager trader : knownTraders.values()) {
            changed |= trader.pruneExpired(now);
        }
        return changed;
    }

    public Optional<SettlementRelationship> relationshipAt(BlockPos anchor) {
        BlockPos key = canonicalRelationshipKey(anchor);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(relationships.get(key));
    }

    public void putRelationship(BlockPos anchor, SettlementRelationship relationship) {
        BlockPos key = canonicalRelationshipKey(anchor);
        if (key != null && relationship != null) {
            relationships.put(key.immutable(), relationship);
        }
    }

    /**
     * D-VR-049 — move relationship row when village anchor supersedes.
     */
    public void rekeyRelationship(BlockPos oldAnchor, BlockPos newAnchor) {
        if (oldAnchor == null || newAnchor == null || oldAnchor.equals(newAnchor)) {
            return;
        }
        SettlementRelationship existing = relationships.remove(oldAnchor.immutable());
        if (existing == null) {
            return;
        }
        BlockPos newKey = canonicalRelationshipKey(newAnchor);
        if (newKey == null) {
            relationships.put(oldAnchor.immutable(), existing);
            return;
        }
        SettlementRelationship merged = relationships.get(newKey);
        if (merged != null) {
            relationships.put(newKey.immutable(), SettlementRelationship.clampMerged(merged, existing));
        } else {
            relationships.put(newKey.immutable(), existing);
        }
    }

    private BlockPos canonicalRelationshipKey(BlockPos anchor) {
        if (anchor == null) {
            return null;
        }
        return at(anchor).map(KnownVillage::anchor).orElse(anchor.immutable());
    }

    private void mergeRelationshipOnIdentity(BlockPos anchor, BlockPos mergedAnchor) {
        if (anchor == null || mergedAnchor == null) {
            return;
        }
        BlockPos fromKey = anchor.immutable();
        BlockPos toKey = canonicalRelationshipKey(mergedAnchor);
        if (toKey == null || fromKey.equals(toKey)) {
            return;
        }
        SettlementRelationship absorbed = relationships.remove(fromKey);
        if (absorbed == null) {
            return;
        }
        SettlementRelationship target = relationships.get(toKey);
        if (target != null) {
            relationships.put(toKey, SettlementRelationship.clampMerged(target, absorbed));
        } else {
            relationships.put(toKey, absorbed);
        }
    }

    public int size() {
        return villages.size();
    }

    public Optional<KnownVillage> home() {
        if (homeAnchor == null) {
            return Optional.empty();
        }
        return villages.stream().filter(village -> village.anchor().equals(homeAnchor)).findFirst();
    }

    public Optional<BlockPos> homeAnchor() {
        return Optional.ofNullable(homeAnchor);
    }

    public Optional<KnownVillage> at(BlockPos anchor) {
        return villages.stream()
                .filter(v -> VillageIdentityPolicy.sameSettlement(v.anchor(), anchor))
                .findFirst();
    }

    /**
     * Record an observation, merging into an existing settlement when the anchors agree.
     *
     * @return the settlement the observation belongs to
     */
    public KnownVillage remember(BlockPos anchor, long tick, ObservationQuality quality) {
        KnownVillage existing = at(anchor).orElse(null);
        if (existing != null) {
            BlockPos oldAnchor = existing.anchor();
            KnownVillage updated = existing.withObservation(anchor, tick, quality);
            if (updated != existing) {
                villages.set(villages.indexOf(existing), updated);
                rekeyRelationship(oldAnchor, updated.anchor());
                rekeyKnownTraders(oldAnchor, updated.anchor());
                if (oldAnchor.equals(homeAnchor)) {
                    homeAnchor = updated.anchor();
                }
            } else {
                mergeRelationshipOnIdentity(anchor, existing.anchor());
            }
            evictBeyondBound();
            return updated;
        }
        KnownVillage discovered = KnownVillage.discovered(anchor, tick, quality);
        villages.add(discovered);
        mergeRelationshipOnIdentity(anchor, discovered.anchor());
        evictBeyondBound();
        return discovered;
    }

    /**
     * Designate this mob's home settlement. The single canonical anchor belongs to this memory, not
     * to a mutually exclusive role stored on the settlement (D-VR-089).
     *
     * @return {@code false} when the anchor names no remembered settlement
     */
    public boolean designateHome(BlockPos anchor) {
        KnownVillage target = at(anchor).orElse(null);
        if (target == null) {
            return false;
        }
        homeAnchor = target.anchor();
        return true;
    }

    /**
     * RET-1a — the production eviction call site. Runs on every {@link #remember}, not on a timer, so
     * the bound cannot be defeated by a mob that stops being ticked at the wrong moment.
     *
     * <p>The home village is never evicted. It is the one entry whose loss is not recoverable by
     * walking past again — rediscovery restores the settlement but not its designation.
     */
    private void evictBeyondBound() {
        while (villages.size() > MAX_KNOWN_VILLAGES) {
            KnownVillage stalest = null;
            for (KnownVillage village : villages) {
                if (village.anchor().equals(homeAnchor)) {
                    continue;
                }
                if (stalest == null || village.lastSeenTick() < stalest.lastSeenTick()) {
                    stalest = village;
                }
            }
            if (stalest == null) {
                // Exactly one entry can be home, so this is unreachable unless the in-memory
                // invariant is broken. Breaking beats spinning forever.
                return;
            }
            relationships.remove(stalest.anchor());
            removeKnownTradersAt(stalest.anchor());
            villages.remove(stalest);
        }
        pruneOrphanRelationships();
    }

    /**
     * D-VR-049 — drop relationship rows whose settlement no longer exists. Required after load when
     * {@link #evictBeyondBound()} ran before relationships were deserialized.
     */
    private void pruneOrphanRelationships() {
        relationships.keySet().removeIf(anchor -> villages.stream()
                .noneMatch(village -> VillageIdentityPolicy.sameSettlement(village.anchor(), anchor)));
    }

    private void rekeyKnownTraders(BlockPos oldAnchor, BlockPos newAnchor) {
        if (oldAnchor == null || newAnchor == null || oldAnchor.equals(newAnchor)) {
            return;
        }
        for (KnownVillager trader : knownTraders.values()) {
            if (trader.settlementAnchor().equals(oldAnchor)) {
                trader.rekey(newAnchor);
            }
        }
    }

    private void removeKnownTradersAt(BlockPos anchor) {
        knownTraders.values().removeIf(trader -> trader.settlementAnchor().equals(anchor));
    }

    /** Enforces local and global bounds independently, oldest observation then UUID. */
    private boolean evictKnownTraderBounds() {
        boolean changed = false;
        for (KnownVillage village : villages) {
            while (knownTradersAt(village.anchor()).size() > MAX_KNOWN_TRADERS_PER_SETTLEMENT) {
                KnownVillager victim = knownTradersAt(village.anchor()).stream()
                        .min(KNOWN_TRADER_EVICTION_ORDER).orElse(null);
                if (victim == null) {
                    break;
                }
                knownTraders.remove(victim.villagerId());
                changed = true;
            }
        }
        while (knownTraders.size() > MAX_KNOWN_TRADERS_PER_MOB) {
            KnownVillager victim = knownTraders.values().stream()
                    .min(KNOWN_TRADER_EVICTION_ORDER).orElse(null);
            if (victim == null) {
                break;
            }
            knownTraders.remove(victim.villagerId());
            changed = true;
        }
        return changed;
    }

    private static final java.util.Comparator<KnownVillager> KNOWN_TRADER_EVICTION_ORDER =
            java.util.Comparator.comparingLong(KnownVillager::lastSeenTick)
                    .thenComparing(KnownVillager::villagerId);

    /**
     * Newest sighting across all remembered settlements.
     *
     * <p><b>Not a liveness signal.</b> V1-R2: this was briefly used as an orphan-collection TTL, which
     * would have deleted an alive mob's home for the crime of mining for a month. It measures memory
     * freshness only, and its sole remaining consumer is the last-resort victim ordering of the
     * {@code MAX_TRACKED_MOBS} safety valve.
     */
    public long lastTouchedTick() {
        long newest = Long.MIN_VALUE;
        for (KnownVillage village : villages) {
            newest = Math.max(newest, village.lastSeenTick());
        }
        for (KnownVillager trader : knownTraders.values()) {
            newest = Math.max(newest, trader.lastSeenTick());
        }
        return newest == Long.MIN_VALUE ? 0L : newest;
    }

    public CompoundTag save() {
        return knownTraders.isEmpty()
                ? save(null)
                : save(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (KnownVillage village : villages) {
            list.add(village.save());
        }
        tag.put("villages", list);
        if (homeAnchor != null) {
            tag.put("homeAnchor", NbtUtils.writeBlockPos(homeAnchor));
        }
        ListTag relationshipList = new ListTag();
        for (Map.Entry<BlockPos, SettlementRelationship> entry : relationships.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.put("anchor", NbtUtils.writeBlockPos(entry.getKey()));
            row.put("relationship", entry.getValue().save());
            relationshipList.add(row);
        }
        tag.put("relationships", relationshipList);
        ListTag traderList = new ListTag();
        if (registries != null) {
            for (KnownVillager trader : knownTraders()) {
                traderList.add(trader.save(registries));
            }
        }
        tag.put("knownTraders", traderList);
        return tag;
    }

    public static MobVillageMemory load(CompoundTag tag) {
        return tag == null || tag.getList("knownTraders", Tag.TAG_COMPOUND).isEmpty()
                ? load(tag, null)
                : load(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    public static MobVillageMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        MobVillageMemory memory = new MobVillageMemory();
        if (tag == null) {
            return memory;
        }
        ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
        boolean explicitHomePresent = tag.contains("homeAnchor");
        BlockPos explicitHome = explicitHomePresent
                ? NbtUtils.readBlockPos(tag, "homeAnchor").orElse(null)
                : null;
        BlockPos firstLegacyHome = null;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (row.contains("tier")) {
                memory.legacySchemaLoaded = true;
            }
            KnownVillage village = KnownVillage.load(row);
            if (village == null) {
                continue;
            }
            if (!explicitHomePresent && firstLegacyHome == null
                    && "HOME_VILLAGE".equals(row.getString("tier"))) {
                firstLegacyHome = village.anchor();
            }
            memory.villages.add(village);
        }
        BlockPos requestedHome = explicitHomePresent ? explicitHome : firstLegacyHome;
        if (requestedHome != null) {
            memory.homeAnchor = memory.at(requestedHome)
                    .map(KnownVillage::anchor)
                    .orElse(null);
        }
        ListTag relationshipList = tag.getList("relationships", Tag.TAG_COMPOUND);
        for (int i = 0; i < relationshipList.size(); i++) {
            CompoundTag row = relationshipList.getCompound(i);
            BlockPos anchor = NbtUtils.readBlockPos(row, "anchor").orElse(null);
            if (anchor == null) {
                continue;
            }
            memory.relationships.put(
                    anchor,
                    SettlementRelationship.load(row.getCompound("relationship")));
        }
        ListTag traderList = tag.getList("knownTraders", Tag.TAG_COMPOUND);
        for (int i = 0; registries != null && i < traderList.size(); i++) {
            KnownVillager trader = KnownVillager.load(traderList.getCompound(i), registries);
            if (trader == null) {
                continue;
            }
            KnownVillage settlement = memory.at(trader.settlementAnchor()).orElse(null);
            if (settlement == null) {
                continue;
            }
            trader.rekey(settlement.anchor());
            KnownVillager previous = memory.knownTraders.get(trader.villagerId());
            if (previous == null || KNOWN_TRADER_EVICTION_ORDER.compare(previous, trader) < 0) {
                memory.knownTraders.put(trader.villagerId(), trader);
            }
        }
        memory.evictBeyondBound();
        memory.evictKnownTraderBounds();
        return memory;
    }

    boolean migratedLegacySchema() {
        return legacySchemaLoaded;
    }
}
