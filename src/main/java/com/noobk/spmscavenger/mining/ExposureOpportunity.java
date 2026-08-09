package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * D-MIW-TS2 — a physical excavation boundary offered to a downstream consumer.
 *
 * <h2>Why this is not a scan result</h2>
 *
 * Tunnel Search never asks "is there diamond nearby". After cutting a corridor cell it knows only
 * <em>"I just opened these cells"</em>, which is legitimate physical evidence rather than a read
 * through solid stone. Everything downstream reasons from that boundary, so the anti-clairvoyance
 * contract is preserved by construction instead of by discipline.
 *
 * <h2>Why it cannot live inside a Goal</h2>
 *
 * The whole point is that {@code TunnelSearchGoal} stops and {@code GatherResourcesGoal} reads it.
 * A field on either executor is unreadable by the other, so it lives in
 * {@link MiningProjectSavedData} beside the project it belongs to.
 *
 * @param mode owning project mode — a cave-handoff or descent session must never offer one
 * @param sessionOrigin owning project origin, half of the session identity
 * @param sessionStartedAt owning project start tick, the other half
 * @param openedCells cells this step physically excavated; the only place a probe may look
 * @param offeredAt tick the exposure was recorded
 * @param lastActivityAt tick of the most recent cooperative acquisition, for vein-session freshness
 * @param phase where in the handoff this opportunity is
 */
public record ExposureOpportunity(
        MiningProjectMode mode,
        BlockPos sessionOrigin,
        long sessionStartedAt,
        List<BlockPos> openedCells,
        long offeredAt,
        long lastActivityAt,
        Phase phase) {

    public enum Phase {
        /** Recorded, probe not yet executed. */
        OFFERED,
        /**
         * A probe found a legitimate target and the consumer is working.
         *
         * <p>Held past the first break so the consumer's own {@code lastHarvest} vein-follow can
         * finish. Without this the tunnel reacquires between every ore in a vein, which technically
         * works and looks like a mob having a seizure.
         */
        ACQUIRING
    }

    public ExposureOpportunity {
        sessionOrigin = sessionOrigin.immutable();
        openedCells = List.copyOf(openedCells);
    }

    public static ExposureOpportunity offer(
            MiningProject project, List<BlockPos> openedCells, long now) {
        return new ExposureOpportunity(
                project.mode(), project.origin(), project.startedGameTime(),
                openedCells, now, now, Phase.OFFERED);
    }

    /** The probe ran. Whether it found anything is a separate question. */
    public ExposureOpportunity acquiring(long now) {
        return new ExposureOpportunity(
                mode, sessionOrigin, sessionStartedAt, openedCells, offeredAt, now, Phase.ACQUIRING);
    }

    /** The consumer took something; the vein-session idle clock restarts. */
    public ExposureOpportunity withActivity(long now) {
        return new ExposureOpportunity(
                mode, sessionOrigin, sessionStartedAt, openedCells, offeredAt, now, phase);
    }

    /**
     * Session identity, so a stale opportunity from one tunnel can never be consumed by the next.
     * Mirrors {@link MiningProject#matchesSession} rather than inventing a second notion of "same
     * session".
     */
    public boolean belongsTo(MiningProject project) {
        return project != null
                && project.mode() == mode
                && project.origin().equals(sessionOrigin)
                && project.startedGameTime() == sessionStartedAt;
    }

    public boolean contains(BlockPos cell) {
        return openedCells.contains(cell.immutable());
    }
}
