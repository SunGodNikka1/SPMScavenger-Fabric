package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.goal.ExplorationInterest.Signal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the interest signal out of chunks that are <b>already in memory</b>, for the length of one
 * route-planning call.
 *
 * <h2>Invariants — all five exist to keep this off the tick budget</h2>
 *
 * <ol>
 *   <li><b>Never use a chunk accessor that can load or generate.</b> Only
 *       {@link net.minecraft.server.level.ServerChunkCache#getChunkNow}. {@code null} means
 *       <em>unknown</em> and scores zero — it must never be read as "bad candidate", or exploration
 *       would quietly start preferring whichever chunks happen to be resident.</li>
 *   <li><b>Never enumerate an unbounded block-entity collection.</b> At most
 *       {@link ExplorationInterest#SAMPLE_LIMIT} entries, with an early exit as soon as a signal
 *       saturates the chunk cap.</li>
 *   <li><b>Never score from a per-tick path.</b> Not from {@code canUse}, not from {@code tick} —
 *       only from cooldown-gated expedition construction, which runs roughly once per 40 seconds
 *       per mob.</li>
 *   <li><b>Never inspect the same chunk twice in one planning call.</b> The eight candidate routes
 *       overlap heavily; this cache is why that costs nothing. It is discarded with the object.</li>
 *   <li><b>Never let quantity decide attractiveness.</b> Enforced in
 *       {@link ExplorationInterest#chunkScore} by scoring a {@link java.util.Set} of categories.</li>
 * </ol>
 *
 * <p>The cache is per planning call by design. A persistent world index would need invalidation,
 * memory and save/load handling to answer a question that is only asked eight times a minute.
 */
final class ChunkInterest {

    private final ServerLevel level;
    private final Map<Long, Integer> cache = new HashMap<>();

    ChunkInterest(ServerLevel level) {
        this.level = level;
    }

    /** Interest score for the chunk containing this block position. */
    int at(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        Long key = ChunkPos.asLong(chunkX, chunkZ);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        int score = inspect(chunkX, chunkZ);
        cache.put(key, score);
        return score;
    }

    private int inspect(int chunkX, int chunkZ) {
        // Invariant 1. getChunkNow never loads and never generates.
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return 0; // Unknown. Do not load it, and do not hold it against the route.
        }

        EnumSet<Signal> signals = EnumSet.noneOf(Signal.class);
        int sampled = 0;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (sampled++ >= ExplorationInterest.SAMPLE_LIMIT) {
                break; // Invariant 2.
            }
            Signal signal = classify(blockEntity.getType());
            if (signal == null) {
                continue;
            }
            signals.add(signal);
            if (ExplorationInterest.saturates(signal)) {
                break; // Nothing left in this chunk can change the answer.
            }
        }
        return ExplorationInterest.chunkScore(signals);
    }

    /** Evidence to category. Returns {@code null} for block entities that say nothing useful. */
    private static Signal classify(BlockEntityType<?> type) {
        if (type == BlockEntityType.MOB_SPAWNER || type == BlockEntityType.TRIAL_SPAWNER) {
            return Signal.SPAWNER;
        }
        if (type == BlockEntityType.VAULT) {
            return Signal.VAULT;
        }
        if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST
                || type == BlockEntityType.BARREL || type == BlockEntityType.SHULKER_BOX) {
            return Signal.STORAGE;
        }
        if (type == BlockEntityType.BED) {
            return Signal.REST;
        }
        if (type == BlockEntityType.BREWING_STAND) {
            return Signal.BREWING;
        }
        if (type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE
                || type == BlockEntityType.SMOKER) {
            return Signal.SMELTING;
        }
        if (type == BlockEntityType.HOPPER || type == BlockEntityType.DISPENSER) {
            return Signal.TRANSPORT;
        }
        if (type == BlockEntityType.LECTERN || type == BlockEntityType.CAMPFIRE
                || type == BlockEntityType.BEEHIVE) {
            return Signal.OTHER;
        }
        // An ender chest is furniture, not loot, and signs/banners are everywhere. Say nothing.
        return null;
    }
}
