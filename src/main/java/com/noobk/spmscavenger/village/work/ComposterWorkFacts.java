package com.noobk.spmscavenger.village.work;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of bounded composter-position evidence (V3-F / D-VR-085-A2).
 */
public final class ComposterWorkFacts {

    private final SettlementIdentity identity;
    private final List<BlockPos> composterPositions;
    private final long observedAtTick;
    private final WorkFactsCompleteness completeness;
    private final WorkFactsFreshness freshness;

    public ComposterWorkFacts(
            SettlementIdentity identity,
            List<BlockPos> composterPositions,
            long observedAtTick,
            WorkFactsCompleteness completeness,
            WorkFactsFreshness freshness) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.composterPositions = List.copyOf(Objects.requireNonNull(composterPositions, "composterPositions"));
        this.observedAtTick = observedAtTick;
        this.completeness = Objects.requireNonNull(completeness, "completeness");
        this.freshness = Objects.requireNonNull(freshness, "freshness");
    }

    public SettlementIdentity identity() {
        return identity;
    }

    public List<BlockPos> composterPositions() {
        return composterPositions;
    }

    public long observedAtTick() {
        return observedAtTick;
    }

    public WorkFactsCompleteness completeness() {
        return completeness;
    }

    public WorkFactsFreshness freshness() {
        return freshness;
    }

    public boolean isReadable() {
        return completeness == WorkFactsCompleteness.COMPLETE && freshness == WorkFactsFreshness.FRESH;
    }

    public ComposterWorkFacts withFreshness(WorkFactsFreshness next) {
        return new ComposterWorkFacts(identity, composterPositions, observedAtTick, completeness, next);
    }
}
