package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V1 — one mob's settlement memory.
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>settlement anchor, merged at {@link VillageIdentityPolicy#SAME_SETTLEMENT_RADIUS_SQR}</td></tr>
 *   <tr><th>Bound</th><td>{@link #MAX_KNOWN_VILLAGES}, LRU by {@code lastSeenTick}</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #remember} (LRU, every call) and
 *       {@code VillageMemorySavedData#forget} on <b>death only</b></td></tr>
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

    private final List<KnownVillage> villages = new ArrayList<>();
    private final Map<BlockPos, SettlementRelationship> relationships = new HashMap<>();

    public List<KnownVillage> villages() {
        return List.copyOf(villages);
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
        return villages.stream().filter(KnownVillage::isHome).findFirst();
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
     * Designate this mob's home settlement. Exactly one at a time — a second home is not a richer
     * model, it is an ambiguous answer to "is this my home", and D-VR-010 asks that question to
     * decide whether to abandon what it is doing.
     *
     * @return {@code false} when the anchor names no remembered settlement
     */
    public boolean designateHome(BlockPos anchor) {
        KnownVillage target = at(anchor).orElse(null);
        if (target == null) {
            return false;
        }
        for (KnownVillage village : villages) {
            if (village.isHome()) {
                village.setTier(SettlementTier.PASSING_THROUGH);
            }
        }
        target.setTier(SettlementTier.HOME_VILLAGE);
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
                if (village.isHome()) {
                    continue;
                }
                if (stalest == null || village.lastSeenTick() < stalest.lastSeenTick()) {
                    stalest = village;
                }
            }
            if (stalest == null) {
                // Every entry is home — impossible while designateHome keeps exactly one, but a
                // corrupt save could produce it. Breaking beats spinning forever.
                return;
            }
            relationships.remove(stalest.anchor());
            villages.remove(stalest);
        }
    }

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
        return newest == Long.MIN_VALUE ? 0L : newest;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (KnownVillage village : villages) {
            list.add(village.save());
        }
        tag.put("villages", list);
        ListTag relationshipList = new ListTag();
        for (Map.Entry<BlockPos, SettlementRelationship> entry : relationships.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.put("anchor", NbtUtils.writeBlockPos(entry.getKey()));
            row.put("relationship", entry.getValue().save());
            relationshipList.add(row);
        }
        tag.put("relationships", relationshipList);
        return tag;
    }

    public static MobVillageMemory load(CompoundTag tag) {
        MobVillageMemory memory = new MobVillageMemory();
        if (tag == null) {
            return memory;
        }
        ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
        boolean homeSeen = false;
        for (int i = 0; i < list.size(); i++) {
            KnownVillage village = KnownVillage.load(list.getCompound(i));
            if (village == null) {
                continue;
            }
            if (village.isHome()) {
                if (homeSeen) {
                    // A save with two homes is corrupt; demote the later one rather than load an
                    // ambiguous answer to "is this my home".
                    village.setTier(SettlementTier.PASSING_THROUGH);
                } else {
                    homeSeen = true;
                }
            }
            memory.villages.add(village);
        }
        memory.evictBeyondBound();
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
        return memory;
    }
}
