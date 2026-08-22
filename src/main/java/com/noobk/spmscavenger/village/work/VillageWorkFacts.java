package com.noobk.spmscavenger.village.work;

import java.util.Objects;

/**
 * Immutable snapshot of settlement-work evidence at one observation time (V3-D).
 *
 * <p>Not permission. Population-support candidacy is evaluated by
 * {@link PopulationSupportVacancyPolicy} on task-57 handoff paths.
 */
public final class VillageWorkFacts {

    private final SettlementIdentity identity;
    private final int adultVillagerCount;
    private final int totalUsableHomeCapacity;
    private final int claimedHomeCount;
    private final int currentFreeHomeCapacity;
    private final long observedAtTick;
    private final WorkFactsCompleteness completeness;
    private final WorkFactsFreshness freshness;

    public VillageWorkFacts(
            SettlementIdentity identity,
            int adultVillagerCount,
            int totalUsableHomeCapacity,
            int claimedHomeCount,
            int currentFreeHomeCapacity,
            long observedAtTick,
            WorkFactsCompleteness completeness,
            WorkFactsFreshness freshness) {
        this.identity = Objects.requireNonNull(identity, "identity");
        if (adultVillagerCount < 0
                || totalUsableHomeCapacity < 0
                || claimedHomeCount < 0
                || currentFreeHomeCapacity < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        this.adultVillagerCount = adultVillagerCount;
        this.totalUsableHomeCapacity = totalUsableHomeCapacity;
        this.claimedHomeCount = claimedHomeCount;
        this.currentFreeHomeCapacity = currentFreeHomeCapacity;
        this.observedAtTick = observedAtTick;
        this.completeness = Objects.requireNonNull(completeness, "completeness");
        this.freshness = Objects.requireNonNull(freshness, "freshness");
    }

    public SettlementIdentity identity() {
        return identity;
    }

    public int adultVillagerCount() {
        return adultVillagerCount;
    }

    public int totalUsableHomeCapacity() {
        return totalUsableHomeCapacity;
    }

    public int claimedHomeCount() {
        return claimedHomeCount;
    }

    public int currentFreeHomeCapacity() {
        return currentFreeHomeCapacity;
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

    public VillageWorkFacts withFreshness(WorkFactsFreshness next, long atTick) {
        return new VillageWorkFacts(
                identity,
                adultVillagerCount,
                totalUsableHomeCapacity,
                claimedHomeCount,
                currentFreeHomeCapacity,
                observedAtTick,
                completeness,
                next);
    }
}
