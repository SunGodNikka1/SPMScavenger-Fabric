package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Furnace discovery, placement, and session walk claims (D-FSM-002 / D-FSM-007 / FS-2).
 *
 * <p>Persisted ownership lives in {@link FurnaceJobSavedData}; this class only holds short-lived
 * walk claims (bed-claim pattern) so two mobs do not path to the same station.
 */
public final class FurnaceStations {

    /** Walk claim outlives a long approach, then lapses if the mob dies mid-path. */
    public static final long WALK_CLAIM_TICKS = 600L;

    private record WalkClaim(UUID mob, long expiresAtTick) {
    }

    private static final Map<BlockPos, WalkClaim> WALK_CLAIMS = new ConcurrentHashMap<>();

    private FurnaceStations() {
    }

    /**
     * Whether the block is a cooking station <em>at all</em>.
     *
     * <p>FS-R1: this is a shape test, not a capability test, and treating it as one is what put an
     * oak log into a blast furnace. Every caller that is about to commit an <b>input</b> must also
     * ask {@link FurnaceCapability#canCook}; the two questions are not the same and the wider one
     * cannot stand in for the narrower.
     */
    public static boolean isFurnaceState(BlockState state) {
        return state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER);
    }

    /**
     * Pure usability gate used by find + unit tests.
     *
     * @param scavengerOwned furnace was placed/recorded by scavengers
     * @param empty          all inventory slots empty
     * @param hasOpenTicket  an unfinished ticket exists at this pos
     * @param ticketOwner    ticket claimant, or null
     * @param self           mob considering the station
     * @param communalOptIn  {@link ScavengerConfig#useCommunalFurnaces}
     */
    public static boolean mayUse(
            boolean scavengerOwned,
            boolean empty,
            boolean hasOpenTicket,
            UUID ticketOwner,
            UUID self,
            boolean communalOptIn) {
        if (hasOpenTicket) {
            return ticketOwner != null && ticketOwner.equals(self);
        }
        if (scavengerOwned) {
            return empty;
        }
        return communalOptIn && empty;
    }

    public static boolean isContainerEmpty(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Nearest usable furnace in range, honouring ownership + communal opt-in + walk claims.
     */
    /**
     * @deprecated FS-R1 — capability-blind. Kept only for callers with no planned input; a caller
     *     that is about to insert an input must use the {@code plannedInput} overload or it can
     *     select a station that will never consume it.
     */
    @Deprecated
    public static BlockPos findUsable(Level level, BlockPos origin, UUID mob, ScavengerConfig cfg) {
        return findUsable(level, origin, mob, cfg, ItemStack.EMPTY);
    }

    /**
     * @param plannedInput the item the job will insert, or empty to skip the capability check
     */
    public static BlockPos findUsable(
            Level level, BlockPos origin, UUID mob, ScavengerConfig cfg, ItemStack plannedInput) {
        if (!cfg.smeltEnabled) {
            return null;
        }
        int r = (int) cfg.furnaceSearchRadius;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        FurnaceJobSavedData data = level instanceof ServerLevel server
                ? FurnaceJobSavedData.get(server)
                : null;

        // RET-1e: the search cube is loaded by construction, so stale ownership markers inside it
        // can be validated for free. Markers outside every search cube are held by the cap.
        if (data != null) {
            data.pruneOwnedNear(origin, r,
                    pos -> isFurnaceState(level.getBlockState(pos)));
        }

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!isFurnaceState(level.getBlockState(pos))) {
                        continue;
                    }
                    if (!isWalkClaimable(pos, mob, level.getGameTime())) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
                        continue;
                    }
                    // FS-R1: `instanceof AbstractFurnaceBlockEntity` is the common supertype of
                    // furnace, blast furnace and smoker, so it passes for exactly the machines that
                    // cannot run this job. Ask the station itself.
                    if (!plannedInput.isEmpty()
                            && !FurnaceCapability.canCook(furnace, level, plannedInput)) {
                        continue;
                    }
                    boolean empty = isContainerEmpty(furnace);
                    boolean owned = data != null && data.isScavengerOwned(pos);
                    java.util.Optional<FurnaceJobSavedData.FurnaceJobTicket> ticket =
                            data != null ? data.ticketAt(pos) : java.util.Optional.empty();
                    boolean hasTicket = ticket.isPresent();
                    UUID ticketOwner = ticket.map(t -> t.claimantMob()).orElse(null);
                    if (!mayUse(owned, empty, hasTicket, ticketOwner, mob, cfg.useCommunalFurnaces)) {
                        continue;
                    }
                    double dist = pos.distSqr(origin);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    /**
     * PERF-1 — revalidates a cached furnace candidate without scanning the world cube.
     */
    /** @deprecated FS-R1 — capability-blind; see {@link #findUsable(Level, BlockPos, UUID, ScavengerConfig, ItemStack)}. */
    @Deprecated
    public static boolean isUsableAt(Level level, BlockPos pos, UUID mob, ScavengerConfig cfg) {
        return isUsableAt(level, pos, mob, cfg, ItemStack.EMPTY);
    }

    public static boolean isUsableAt(
            Level level, BlockPos pos, UUID mob, ScavengerConfig cfg, ItemStack plannedInput) {
        if (!cfg.smeltEnabled || pos == null) {
            return false;
        }
        if (!isFurnaceState(level.getBlockState(pos))) {
            return false;
        }
        if (!isWalkClaimable(pos, mob, level.getGameTime())) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            return false;
        }
        // FS-R1: a cached candidate must still be able to cook the job. A furnace can be replaced by
        // a blast furnace between selection and arrival.
        if (!plannedInput.isEmpty() && !FurnaceCapability.canCook(furnace, level, plannedInput)) {
            return false;
        }
        FurnaceJobSavedData data = level instanceof ServerLevel server
                ? FurnaceJobSavedData.get(server)
                : null;
        boolean empty = isContainerEmpty(furnace);
        boolean owned = data != null && data.isScavengerOwned(pos);
        java.util.Optional<FurnaceJobSavedData.FurnaceJobTicket> ticket =
                data != null ? data.ticketAt(pos) : java.util.Optional.empty();
        boolean hasTicket = ticket.isPresent();
        UUID ticketOwner = ticket.map(t -> t.claimantMob()).orElse(null);
        return mayUse(owned, empty, hasTicket, ticketOwner, mob, cfg.useCommunalFurnaces);
    }

    public static boolean tryClaimWalk(BlockPos pos, UUID mob, long gameTime) {
        BlockPos key = pos.immutable();
        WalkClaim existing = WALK_CLAIMS.get(key);
        if (existing != null && gameTime > existing.expiresAtTick()) {
            // Gate RET-1d - logical expiry was not physical expiry. An expired claim counted as
            // free but stayed in the map, so every furnace position ever walked to was retained for
            // the life of the server. Remove conditionally so a concurrent re-claim is not lost.
            WALK_CLAIMS.remove(key, existing);
            existing = null;
        }
        if (existing != null && !existing.mob().equals(mob)) {
            return false;
        }
        WALK_CLAIMS.put(key, new WalkClaim(mob, gameTime + WALK_CLAIM_TICKS));
        return true;
    }

    public static void releaseWalk(BlockPos pos, UUID mob) {
        if (pos == null) {
            return;
        }
        WalkClaim claim = WALK_CLAIMS.get(pos.immutable());
        if (claim != null && claim.mob().equals(mob)) {
            WALK_CLAIMS.remove(pos.immutable());
        }
    }

    static boolean isWalkClaimable(BlockPos pos, UUID mob, long gameTime) {
        BlockPos key = pos.immutable();
        WalkClaim claim = WALK_CLAIMS.get(key);
        if (claim == null) {
            return true;
        }
        if (gameTime > claim.expiresAtTick()) {
            WALK_CLAIMS.remove(key, claim);
            return true;
        }
        return claim.mob().equals(mob);
    }

    /** Gate RET-1d - release every walk claim when the server stops. */
    public static void shutdownServerState() {
        WALK_CLAIMS.clear();
    }

    static int walkClaimCount() {
        return WALK_CLAIMS.size();
    }

    /** Test-only: clear session walk claims between cases. */
    static void clearWalkClaimsForTest() {
        WALK_CLAIMS.clear();
    }

    /**
     * Places a furnace from the backpack beside the mob and records scavenger ownership.
     * Does not craft the furnace item — caller must ensure one is held (FS-3 wires craft).
     */
    public static BlockPos tryPlaceFromBackpack(ServerLevel level, Mob mob, Container backpack, ScavengerConfig cfg) {
        if (!cfg.smeltEnabled || !cfg.placeFurnaces) {
            return null;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return null;
        }
        if (ScavengerCrafting.count(backpack, Items.FURNACE) < 1) {
            return null;
        }
        BlockPos spot = freeSpotBeside(level, mob.blockPosition());
        if (spot == null) {
            return null;
        }
        if (!takeFurnace(backpack)) {
            return null;
        }
        level.setBlock(spot, Blocks.FURNACE.defaultBlockState(), Block.UPDATE_ALL);
        FurnaceJobSavedData.get(level).recordPlaced(spot);
        return spot.immutable();
    }

    private static boolean takeFurnace(Container backpack) {
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.is(Items.FURNACE)) {
                backpack.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }

    private static BlockPos freeSpotBeside(Level level, BlockPos origin) {
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos pos = origin.relative(dir);
            BlockState state = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());
            if (state.canBeReplaced() && below.isSolidRender(level, pos.below())) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Face-API emptiness / slot probing helpers for FS-3. Exposed early so FS-2 tests can prove
     * we never assume hardcoded 0/1/2 (D-FSM-009).
     */
    public static int[] slotsForFace(WorldlyContainer container, Direction face) {
        return container.getSlotsForFace(face);
    }
}
