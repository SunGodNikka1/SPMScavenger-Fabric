package com.noobk.spmscavenger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Gate RET-1e — <b>the one permanent-removal rule</b> for per-mob persisted state.
 *
 * <h2>Why this exists as a single place</h2>
 *
 * The same defect has now been found three times in this mod, in three different stores, by three
 * separate reviews:
 *
 * <ul>
 *   <li>village memory — evicted on generic unload, then on a staleness clock, then in one dimension
 *       only;</li>
 *   <li>{@code MiningProjectSavedData} — five per-mob maps with task-scoped {@code clear*} methods
 *       and no owner-lifecycle path at all;</li>
 *   <li>{@code FurnaceJobSavedData} — tickets keyed by claimant mob, released only by task
 *       transitions.</li>
 * </ul>
 *
 * Every one of them was individually reasonable and collectively the same bug. A store added later —
 * V2's {@code KnownVillager}, trade sessions, per-villager relationships — will be written by someone
 * who has not read this history, so the rule is expressed once, as code, with a structural test that
 * fails when a new per-mob store is not registered here.
 *
 * <h2>The contract</h2>
 *
 * <pre>
 * ordinary unload / dimension change  -> preserve  (semantic memory outlives a chunk boundary)
 * RemovalReason.shouldDestroy()       -> forgetAll (KILLED, DISCARDED)
 * extent                              -> every dimension, via a NON-CREATING accessor
 * </pre>
 *
 * The non-creating accessor matters: sweeping with {@code computeIfAbsent} would materialise save
 * files for dimensions that never held any state, which is cleanup that creates the thing it cleans.
 */
public final class PerMobSavedData {

    private PerMobSavedData() {
    }

    /**
     * Release every per-mob store in the mod for an owner that is permanently gone.
     *
     * <p>Callers must have established permanent removal first — {@code AFTER_DEATH}, or
     * {@code ENTITY_UNLOAD} with {@code RemovalReason.shouldDestroy()}.
     *
     * @return total number of (store, dimension) pairs that actually held something
     */
    public static int forgetAll(MinecraftServer server, UUID mobId) {
        if (server == null || mobId == null) {
            return 0;
        }
        int released = 0;
        released += com.noobk.spmscavenger.village.VillageMemorySavedData
                .forgetEverywhere(server, mobId);
        released += com.noobk.spmscavenger.mining.MiningProjectSavedData
                .forgetEverywhere(server, mobId);
        released += FurnaceJobSavedData.forgetEverywhere(server, mobId);
        released += com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData
                .forgetEverywhere(server, mobId);
        released += com.noobk.spmscavenger.village.storage.StoragePermissionSavedData
                .forgetEverywhere(server, mobId);
        return released;
    }

    /**
     * Sweep one store across every loaded dimension.
     *
     * @param peek must be a <b>non-creating</b> accessor returning {@code null} when the dimension
     *     holds no such data
     * @param forget returns whether anything was released
     */
    public static <T extends SavedData> int sweep(
            MinecraftServer server,
            UUID mobId,
            Function<ServerLevel, T> peek,
            BiPredicate<T, UUID> forget) {
        if (server == null || mobId == null) {
            return 0;
        }
        int released = 0;
        for (ServerLevel level : server.getAllLevels()) {
            T data = peek.apply(level);
            if (data != null && forget.test(data, mobId)) {
                released++;
            }
        }
        return released;
    }

    /** Testable core: sweep an explicit list of stores, free of server plumbing. */
    public static <T> int sweepAll(List<T> stores, UUID mobId, BiPredicate<T, UUID> forget) {
        int released = 0;
        for (T store : stores) {
            if (store != null && forget.test(store, mobId)) {
                released++;
            }
        }
        return released;
    }
}
