package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * V1 / D-VR-019 — bounded, vanilla-compatible settlement perception.
 *
 * <h2>Membership: vanilla's own predicate</h2>
 *
 * A settlement is {@code PoiTypeTags.VILLAGE} + {@code IS_OCCUPIED}, exactly as
 * {@code Raids#createOrExtendRaid} defines it. The tag resolves to
 * {@code #acquirable_job_site + home + meeting} — every workstation, beds, and the bell — and being a
 * <b>tag</b>, a datapack or another mod that registers a village-ish POI becomes a village to this
 * mob with no code change (SPM-0 level 3–4). {@code IS_OCCUPIED} means claimed by a villager, so an
 * abandoned bed cluster is not a village here for the same reason it is not one to the raid system.
 *
 * <h2>Perception boundary: storage availability is not perception</h2>
 *
 * {@link PoiManager} extends {@code SectionStorage} and will return POIs for chunks that are not
 * loaded — it reads from persisted section files. Queried naively this class would hand the mob
 * knowledge of villages it has never been near, which is <b>worse</b> than the bed-cluster heuristic
 * it replaces: a block scan could only ever see loaded blocks, so it was incapable of this mistake.
 *
 * <p>So the boundary is a <b>construction invariant</b>, not a check the caller is trusted to
 * remember. This class holds the only reference to {@link PoiManager} in the addon, the raw stream is
 * never returned, and every record passes {@link #withinPerception} before it can influence anything.
 * The same philosophy as ore in Mining Intelligence: the server knows it is there; the mob does not.
 *
 * <p>{@code ServerLevel#hasChunk} resolves against the chunk source's loaded map and <b>does not
 * trigger a load or generation</b>, so asking the question cannot manufacture its own answer
 * (D-GAO-057, same rule as the Opinion admission seam).
 */
public final class VillagePerception {

    /**
     * The radius vanilla uses in {@code Raids#createOrExtendRaid}. Kept identical: a different radius
     * would admit a different POI set, and a different POI set produces a different anchor even with
     * an identical derivation — which is the disagreement D-VR-019 exists to remove.
     */
    public static final int VILLAGE_QUERY_RADIUS = 64;

    private VillagePerception() {
    }

    /** What the mob is allowed to have noticed, and the anchor that follows from it. */
    public record Observation(BlockPos anchor, int admittedPoiCount, int withheldPoiCount) {

        public boolean isSettlement() {
            return admittedPoiCount > 0;
        }

        /**
         * True when the boundary actually excluded something. Useful as a runtime signal that the
         * mob is at the edge of a settlement rather than in it — and as the log line that proves the
         * boundary is doing work rather than being trivially satisfied.
         */
        public boolean partiallyPerceived() {
            return withheldPoiCount > 0;
        }
    }

    /**
     * Observe the settlement around {@code origin}, admitting only POIs the mob could legitimately
     * know about.
     *
     * @param origin normally the mob's own position — perception is from where the mob is, never
     *     from a remembered or predicted place
     */
    public static Observation observe(ServerLevel level, BlockPos origin) {
        List<BlockPos> admitted = new ArrayList<>();
        int withheld = 0;

        // The single PoiManager touch point in the addon. Everything downstream sees `admitted`.
        List<PoiRecord> records = level.getPoiManager()
                .getInRange(
                        holder -> holder.is(PoiTypeTags.VILLAGE),
                        origin,
                        VILLAGE_QUERY_RADIUS,
                        PoiManager.Occupancy.IS_OCCUPIED)
                .toList();

        for (PoiRecord record : records) {
            BlockPos pos = record.getPos();
            if (withinPerception(level, pos)) {
                admitted.add(pos);
            } else {
                withheld++;
            }
        }

        BlockPos anchor = VillageAnchorPolicy.anchorOf(admitted, origin);
        return new Observation(anchor, admitted.size(), withheld);
    }

    /**
     * The invariant: a POI enters perception only from a chunk that is <b>already</b> loaded.
     *
     * <p>Deliberately not {@code level.isLoaded(pos)} — that is a block-level convenience that also
     * consults the height limit and reads slightly differently across versions. {@code hasChunk} on
     * the section coordinates is the direct question, and it is the non-loading one.
     */
    public static boolean withinPerception(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
