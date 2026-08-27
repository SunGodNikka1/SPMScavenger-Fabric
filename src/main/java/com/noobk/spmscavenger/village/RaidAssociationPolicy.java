package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * V1-R1 — <b>vanilla-compatible</b> raid association: "would vanilla's raid at this centre be the raid
 * happening at my village?"
 *
 * <h2>Separated from identity on purpose</h2>
 *
 * This is the half of the old {@code sameSettlement} that was genuinely load-bearing for D-VR-019 and
 * D-VR-010: it must match {@code ServerLevel#getRaidAt}, which calls
 * {@code raids.getNearbyRaid(pos, 9216)} — 96 blocks squared — and {@code Raids#getOrCreateRaid},
 * which reuses any raid found within that radius rather than creating a second one.
 *
 * <p>{@link VillageIdentityPolicy} owns the different, smaller question of what the mob considers one
 * place. Keeping them apart is what lets a raid legitimately cover the independently designated
 * home and another nearby remembered settlement without those two becoming one identity.
 *
 * <p>Consequently {@link #associatedVillages} returns <b>every</b> match rather than the nearest one.
 * A single vanilla raid covering two remembered villages is a normal, representable situation now; the
 * consumer (D-VR-010, V5) decides what to do about it — and the natural rule, once it exists, is that
 * the highest-tier associated village wins, which is a decision the old collapsed model could not even
 * pose.
 */
public final class RaidAssociationPolicy {

    /**
     * {@code ServerLevel#getRaidAt} passes {@code 9216} to {@code getNearbyRaid} (bytecode offset 5,
     * {@code sipush 9216}). Must not drift from vanilla: this is the compatibility half.
     */
    public static final int RAID_ASSOCIATION_RADIUS_SQR = 9216;

    private RaidAssociationPolicy() {
    }

    /** Whether a raid centred at {@code raidCentre} is the raid at the settlement anchored here. */
    public static boolean associated(BlockPos villageAnchor, BlockPos raidCentre) {
        return villageAnchor != null && raidCentre != null
                && villageAnchor.distSqr(raidCentre) <= RAID_ASSOCIATION_RADIUS_SQR;
    }

    /**
     * Every remembered village a raid at {@code raidCentre} covers.
     *
     * <p>Returning a list rather than an {@code Optional} is the point of the split: one raid over two
     * remembered settlements is representable instead of being resolved by accident at merge time.
     */
    public static List<KnownVillage> associatedVillages(
            Collection<KnownVillage> remembered, BlockPos raidCentre) {
        List<KnownVillage> matches = new ArrayList<>();
        if (remembered == null || raidCentre == null) {
            return matches;
        }
        for (KnownVillage village : remembered) {
            if (associated(village.anchor(), raidCentre)) {
                matches.add(village);
            }
        }
        return matches;
    }
}
