package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * D-VR-090 — positive evidence that a remembered settlement contained a trader producing an output.
 *
 * <p>This is intentionally incapable of authorizing a trade: it stores no cost, price, slot, uses,
 * affordability, live offer, or old policy result. A capability means only "worth investigating".
 */
public final class KnownVillager {

    public static final int MAX_CAPABILITY_HINTS = 16;
    public static final long CAPABILITY_TTL_TICKS = 168_000L;

    public record CapabilityHint(TradeOutputCapability capability, long observedAtTick) {
        public CapabilityHint {
            Objects.requireNonNull(capability, "capability");
        }

        boolean expired(long now) {
            return now - observedAtTick >= CAPABILITY_TTL_TICKS;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = capability.save(registries);
            tag.putLong("observedAt", observedAtTick);
            return tag;
        }

        static CapabilityHint load(CompoundTag tag, HolderLookup.Provider registries) {
            TradeOutputCapability capability = TradeOutputCapability.load(tag, registries);
            return capability == null ? null : new CapabilityHint(capability, tag.getLong("observedAt"));
        }
    }

    private final UUID villagerId;
    private BlockPos settlementAnchor;
    private ResourceLocation lastObservedProfession;
    private int lastObservedLevel;
    private long lastSeenTick;
    private final List<CapabilityHint> capabilityHints = new ArrayList<>();

    KnownVillager(UUID villagerId, BlockPos settlementAnchor, ResourceLocation profession,
            int level, long lastSeenTick) {
        this.villagerId = Objects.requireNonNull(villagerId, "villagerId");
        this.settlementAnchor = Objects.requireNonNull(settlementAnchor, "settlementAnchor").immutable();
        this.lastObservedProfession = profession;
        this.lastObservedLevel = Math.max(0, level);
        this.lastSeenTick = lastSeenTick;
    }

    public UUID villagerId() {
        return villagerId;
    }

    public BlockPos settlementAnchor() {
        return settlementAnchor;
    }

    public ResourceLocation lastObservedProfession() {
        return lastObservedProfession;
    }

    public int lastObservedLevel() {
        return lastObservedLevel;
    }

    public long lastSeenTick() {
        return lastSeenTick;
    }

    public List<CapabilityHint> capabilityHints() {
        return List.copyOf(capabilityHints);
    }

    /** Returns participating evidence and physically removes expired hints. */
    public List<TradeOutputCapability> activeCapabilities(long now) {
        pruneExpired(now);
        return capabilityHints.stream().map(CapabilityHint::capability).toList();
    }

    boolean pruneExpired(long now) {
        return capabilityHints.removeIf(hint -> hint.expired(now));
    }

    void rekey(BlockPos newAnchor) {
        settlementAnchor = Objects.requireNonNull(newAnchor, "newAnchor").immutable();
    }

    /** Complete live-board observation: positive set replacement, never a persistent negative row. */
    boolean observe(ResourceLocation profession, int level, List<ItemStack> outputs, long tick) {
        boolean changed = pruneExpired(tick);
        LinkedHashSet<TradeOutputCapability> observed = new LinkedHashSet<>();
        if (outputs != null) {
            for (ItemStack output : outputs) {
                if (output != null && !output.isEmpty()) {
                    observed.add(TradeOutputCapability.of(output));
                }
            }
        }

        List<CapabilityHint> replacement = new ArrayList<>();
        for (TradeOutputCapability capability : observed) {
            replacement.add(new CapabilityHint(capability, tick));
        }
        replacement.sort(Comparator
                .comparingLong(CapabilityHint::observedAtTick).reversed()
                .thenComparingInt(hint -> hint.capability().hashCode()));
        if (replacement.size() > MAX_CAPABILITY_HINTS) {
            replacement = new ArrayList<>(replacement.subList(0, MAX_CAPABILITY_HINTS));
        }
        if (!capabilityHints.equals(replacement)) {
            capabilityHints.clear();
            capabilityHints.addAll(replacement);
            changed = true;
        }
        if (!Objects.equals(lastObservedProfession, profession)
                || lastObservedLevel != Math.max(0, level)
                || lastSeenTick != tick) {
            lastObservedProfession = profession;
            lastObservedLevel = Math.max(0, level);
            lastSeenTick = tick;
            changed = true;
        }
        return changed;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("villager", villagerId);
        tag.put("settlement", NbtUtils.writeBlockPos(settlementAnchor));
        if (lastObservedProfession != null) {
            tag.putString("profession", lastObservedProfession.toString());
        }
        tag.putInt("level", lastObservedLevel);
        tag.putLong("lastSeen", lastSeenTick);
        ListTag hints = new ListTag();
        for (CapabilityHint hint : capabilityHints) {
            hints.add(hint.save(registries));
        }
        tag.put("capabilities", hints);
        return tag;
    }

    static KnownVillager load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || !tag.hasUUID("villager") || !tag.contains("settlement")) {
            return null;
        }
        BlockPos settlement = NbtUtils.readBlockPos(tag, "settlement").orElse(null);
        if (settlement == null) {
            return null;
        }
        ResourceLocation profession = tag.contains("profession")
                ? ResourceLocation.tryParse(tag.getString("profession")) : null;
        KnownVillager known = new KnownVillager(tag.getUUID("villager"), settlement, profession,
                tag.getInt("level"), tag.getLong("lastSeen"));
        ListTag hints = tag.getList("capabilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < hints.size() && known.capabilityHints.size() < MAX_CAPABILITY_HINTS; i++) {
            CapabilityHint hint = CapabilityHint.load(hints.getCompound(i), registries);
            if (hint != null && known.capabilityHints.stream()
                    .noneMatch(existing -> existing.capability().equals(hint.capability()))) {
                known.capabilityHints.add(hint);
            }
        }
        return known;
    }
}
