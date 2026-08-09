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
    public static BlockPos findUsable(Level level, BlockPos origin, UUID mob, ScavengerConfig cfg) {
        if (!cfg.smeltEnabled) {
            return null;
        }
        int r = (int) cfg.furnaceSearchRadius;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        FurnaceJobSavedData data = level instanceof ServerLevel server
                ? FurnaceJobSavedData.get(server)
                : null;

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

    public static boolean tryClaimWalk(BlockPos pos, UUID mob, long gameTime) {
        BlockPos key = pos.immutable();
        WalkClaim existing = WALK_CLAIMS.get(key);
        if (existing != null
                && !existing.mob().equals(mob)
                && gameTime <= existing.expiresAtTick()) {
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
        WalkClaim claim = WALK_CLAIMS.get(pos.immutable());
        if (claim == null) {
            return true;
        }
        return claim.mob().equals(mob) || gameTime > claim.expiresAtTick();
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
