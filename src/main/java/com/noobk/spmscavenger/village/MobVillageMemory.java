package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * V1 — one mob's settlement memory.
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>settlement anchor, merged at {@link VillageAnchorPolicy#SAME_SETTLEMENT_RADIUS_SQR}</td></tr>
 *   <tr><th>Bound</th><td>{@link #MAX_KNOWN_VILLAGES}, LRU by {@code lastSeenTick}</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #remember} (LRU, every call) and
 *       {@code VillageMemorySavedData#forget} on entity unload and death</td></tr>
 *   <tr><th>Death / unload</th><td>memory released with the mob</td></tr>
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

    public List<KnownVillage> villages() {
        return List.copyOf(villages);
    }

    public int size() {
        return villages.size();
    }

    public Optional<KnownVillage> home() {
        return villages.stream().filter(KnownVillage::isHome).findFirst();
    }

    public Optional<KnownVillage> at(BlockPos anchor) {
        return villages.stream()
                .filter(v -> VillageAnchorPolicy.sameSettlement(v.anchor(), anchor))
                .findFirst();
    }

    /**
     * Record an observation, merging into an existing settlement when the anchors agree.
     *
     * @return the settlement the observation belongs to
     */
    public KnownVillage remember(BlockPos anchor, long tick, int poiCount) {
        KnownVillage existing = at(anchor).orElse(null);
        if (existing != null) {
            KnownVillage updated = existing.withStrongerObservation(anchor, tick, poiCount);
            if (updated != existing) {
                villages.set(villages.indexOf(existing), updated);
            }
            evictBeyondBound();
            return updated;
        }
        KnownVillage discovered = KnownVillage.discovered(anchor, tick, poiCount);
        villages.add(discovered);
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
            villages.remove(stalest);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (KnownVillage village : villages) {
            list.add(village.save());
        }
        tag.put("villages", list);
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
        return memory;
    }
}
