package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dimension-local scavenger furnace ownership and active smelt tickets (D-FSM-007 / FS-2).
 */
public final class FurnaceJobSavedData extends SavedData {

    public static final String DATA_NAME = "spmscavenger_furnace_jobs";

    public enum JobPhase {
        RESERVED,
        INSERTED,
        EXTRACTING,
        CLOSED
    }

    public record FurnaceJobTicket(
            BlockPos furnacePos,
            UUID claimantMob,
            StackFingerprint input,
            StackFingerprint fuel,
            StackFingerprint expectedOutput,
            int reservedOutputSlots,
            long startedGameTime,
            ResourceLocation recipeId,
            JobPhase phase) {

        public FurnaceJobTicket withPhase(JobPhase next) {
            return new FurnaceJobTicket(
                    furnacePos,
                    claimantMob,
                    input,
                    fuel,
                    expectedOutput,
                    reservedOutputSlots,
                    startedGameTime,
                    recipeId,
                    next);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", furnacePos.getX());
            tag.putInt("y", furnacePos.getY());
            tag.putInt("z", furnacePos.getZ());
            tag.putUUID("mob", claimantMob);
            tag.put("input", input.save());
            tag.put("fuel", fuel.save());
            tag.put("output", expectedOutput.save());
            tag.putInt("reservedOut", reservedOutputSlots);
            tag.putLong("started", startedGameTime);
            tag.putString("recipe", recipeId.toString());
            tag.putString("phase", phase.name());
            return tag;
        }

        public static Optional<FurnaceJobTicket> load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("mob")) {
                return Optional.empty();
            }
            Optional<StackFingerprint> input = StackFingerprint.load(tag.getCompound("input"));
            Optional<StackFingerprint> fuel = StackFingerprint.load(tag.getCompound("fuel"));
            Optional<StackFingerprint> output = StackFingerprint.load(tag.getCompound("output"));
            ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
            if (input.isEmpty() || fuel.isEmpty() || output.isEmpty() || recipe == null) {
                return Optional.empty();
            }
            JobPhase phase;
            try {
                phase = JobPhase.valueOf(tag.getString("phase"));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            return Optional.of(new FurnaceJobTicket(
                    pos,
                    tag.getUUID("mob"),
                    input.get(),
                    fuel.get(),
                    output.get(),
                    tag.getInt("reservedOut"),
                    tag.getLong("started"),
                    recipe,
                    phase));
        }
    }

    private final Set<BlockPos> scavengerOwned = new HashSet<>();
    private final Map<BlockPos, FurnaceJobTicket> tickets = new HashMap<>();

    public FurnaceJobSavedData() {
    }

    public static FurnaceJobSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(FurnaceJobSavedData::new, FurnaceJobSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static FurnaceJobSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FurnaceJobSavedData data = new FurnaceJobSavedData();
        ListTag owned = tag.getList("owned", Tag.TAG_COMPOUND);
        for (int i = 0; i < owned.size(); i++) {
            CompoundTag entry = owned.getCompound(i);
            data.scavengerOwned.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        ListTag ticketList = tag.getList("tickets", Tag.TAG_COMPOUND);
        for (int i = 0; i < ticketList.size(); i++) {
            FurnaceJobTicket.load(ticketList.getCompound(i)).ifPresent(t -> {
                if (t.phase() != JobPhase.CLOSED) {
                    data.tickets.put(t.furnacePos().immutable(), t);
                }
            });
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owned = new ListTag();
        for (BlockPos pos : scavengerOwned) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            owned.add(entry);
        }
        tag.put("owned", owned);

        ListTag ticketList = new ListTag();
        for (FurnaceJobTicket ticket : tickets.values()) {
            if (ticket.phase() != JobPhase.CLOSED) {
                ticketList.add(ticket.save());
            }
        }
        tag.put("tickets", ticketList);
        return tag;
    }

    public void recordPlaced(BlockPos pos) {
        scavengerOwned.add(pos.immutable());
        setDirty();
    }

    public boolean isScavengerOwned(BlockPos pos) {
        return scavengerOwned.contains(pos.immutable());
    }

    public Set<BlockPos> ownedStations() {
        return Collections.unmodifiableSet(scavengerOwned);
    }

    public Optional<FurnaceJobTicket> ticketAt(BlockPos pos) {
        return Optional.ofNullable(tickets.get(pos.immutable()));
    }

    public Collection<FurnaceJobTicket> allTickets() {
        return Collections.unmodifiableCollection(tickets.values());
    }

    public void putTicket(FurnaceJobTicket ticket) {
        if (ticket.phase() == JobPhase.CLOSED) {
            tickets.remove(ticket.furnacePos().immutable());
        } else {
            tickets.put(ticket.furnacePos().immutable(), ticket);
        }
        setDirty();
    }

    public void closeTicket(BlockPos pos) {
        FurnaceJobTicket existing = tickets.remove(pos.immutable());
        if (existing != null) {
            setDirty();
        }
    }

    /**
     * After reload: keep tickets whose fingerprints still make sense against {@code matcher}.
     * Fail-closed removes mismatched tickets without inventing stacks.
     */
    public int reclaimOrClose(java.util.function.Predicate<FurnaceJobTicket> stillValid) {
        int closed = 0;
        for (BlockPos pos : new HashSet<>(tickets.keySet())) {
            FurnaceJobTicket ticket = tickets.get(pos);
            if (ticket == null) {
                continue;
            }
            if (!stillValid.test(ticket)) {
                tickets.remove(pos);
                closed++;
                setDirty();
            }
        }
        return closed;
    }

    /** Test helper — empty data without a world. */
    public static FurnaceJobSavedData createEmpty() {
        return new FurnaceJobSavedData();
    }
}
