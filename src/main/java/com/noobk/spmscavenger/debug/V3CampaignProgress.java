package com.noobk.spmscavenger.debug;

import java.util.Set;
import java.util.UUID;

/** Pure row-clock state; it classifies observation completeness, never product correctness. */
final class V3CampaignProgress {

    enum Disposition {
        OBSERVING,
        OBSERVATION_COMPLETE,
        INCOMPLETE
    }

    record Probe(
            int replantedTargetMask,
            int matureTargetMask,
            int subjectSeedCount,
            boolean combatTarget,
            Set<UUID> committedHarvestActors) {

        Probe {
            committedHarvestActors = Set.copyOf(committedHarvestActors);
        }

        Probe(int replantedTargetMask, int subjectSeedCount, boolean combatTarget) {
            this(replantedTargetMask, 0, subjectSeedCount, combatTarget, Set.of());
        }
    }

    record Decision(Disposition disposition, boolean fireDeclaredTrigger, String reason) {
    }

    private final V3CampaignScenario scenario;
    private final long openingTick;
    private final int initialSeedCount;
    private boolean triggerFired;
    private boolean combatSeen;
    private int observedReplantMask;
    private int lastReplantedMask;
    private int matureSinceLastHarvestMask;
    private int temporallyHarvestedMask;
    private int temporalHarvestTransitions;
    private boolean repeatedTemporalHarvest;
    private boolean contentionObserved;
    private boolean contendedCommitObserved;
    private Set<UUID> contendersAtCommit = Set.of();
    private long terminalObservedAt = -1L;

    private V3CampaignProgress(
            V3CampaignScenario scenario, long openingTick, Probe initial) {
        this.scenario = scenario;
        this.openingTick = openingTick;
        this.initialSeedCount = initial.subjectSeedCount();
        this.lastReplantedMask = initial.replantedTargetMask();
        this.matureSinceLastHarvestMask = initial.matureTargetMask();
    }

    static V3CampaignProgress open(
            V3CampaignScenario scenario, long openingTick, int initialSeedCount) {
        return new V3CampaignProgress(
                scenario, openingTick, new Probe(0, initialSeedCount, false));
    }

    static V3CampaignProgress open(
            V3CampaignScenario scenario, long openingTick, Probe initial) {
        return new V3CampaignProgress(scenario, openingTick, initial);
    }

    Decision observe(long now, Probe probe) {
        long elapsed = Math.max(0L, now - openingTick);
        boolean fireTrigger = scenario.triggerDelayTicks() > 0
                && !triggerFired
                && elapsed >= scenario.triggerDelayTicks();

        switch (scenario.completionKind()) {
            case FIXED -> {
                if (elapsed >= scenario.fixedWindowTicks()) {
                    return new Decision(Disposition.OBSERVATION_COMPLETE, fireTrigger,
                            "fixed minimum observation window complete");
                }
            }
            case REPLANT -> {
                observedReplantMask |= probe.replantedTargetMask();
                if (terminalObservedAt < 0L
                        && Integer.bitCount(observedReplantMask) >= scenario.requiredReplants()) {
                    terminalObservedAt = now;
                }
            }
            case CONTENDED_REPLANT -> observeContendedReplant(now, probe);
            case TEMPORAL_REPLANT_CYCLE -> observeTemporalCycle(now, probe);
            case COMPOST_DEBIT -> {
                if (terminalObservedAt < 0L
                        && probe.subjectSeedCount() < initialSeedCount) {
                    terminalObservedAt = now;
                }
            }
            case COMBAT_RELEASE -> {
                combatSeen |= probe.combatTarget();
                if (terminalObservedAt < 0L && combatSeen && !probe.combatTarget()) {
                    terminalObservedAt = now;
                }
            }
        }

        lastReplantedMask = probe.replantedTargetMask();

        if (terminalObservedAt >= 0L
                && now - terminalObservedAt >= scenario.stabilizationTicks()) {
            return new Decision(Disposition.OBSERVATION_COMPLETE, fireTrigger,
                    "observed terminal transition plus stabilization window");
        }
        if (elapsed >= scenario.maxWindowTicks()) {
            return new Decision(Disposition.INCOMPLETE, fireTrigger,
                    "required runtime transition not observed before bounded timeout");
        }
        return new Decision(Disposition.OBSERVING, fireTrigger,
                terminalObservedAt < 0L
                        ? "waiting for required runtime transition"
                        : "terminal observed; stabilization window active");
    }

    private void observeContendedReplant(long now, Probe probe) {
        if (!contentionObserved && probe.committedHarvestActors().size() >= 2) {
            contentionObserved = true;
            contendersAtCommit = probe.committedHarvestActors();
        }
        int newlyReplanted = probe.replantedTargetMask() & ~lastReplantedMask;
        if (contentionObserved && newlyReplanted != 0) {
            contendedCommitObserved = true;
        }
        if (terminalObservedAt < 0L
                && contendedCommitObserved
                && contendersAtCommit.stream()
                        .anyMatch(actor -> !probe.committedHarvestActors().contains(actor))) {
            terminalObservedAt = now;
        }
    }

    private void observeTemporalCycle(long now, Probe probe) {
        matureSinceLastHarvestMask |= probe.matureTargetMask();
        int newlyReplanted = probe.replantedTargetMask() & ~lastReplantedMask;
        int validTransitions = newlyReplanted & matureSinceLastHarvestMask;
        while (validTransitions != 0) {
            int bit = Integer.lowestOneBit(validTransitions);
            if ((temporallyHarvestedMask & bit) != 0) {
                repeatedTemporalHarvest = true;
            }
            temporallyHarvestedMask |= bit;
            matureSinceLastHarvestMask &= ~bit;
            temporalHarvestTransitions++;
            validTransitions &= ~bit;
        }
        if (terminalObservedAt < 0L
                && repeatedTemporalHarvest
                && temporalHarvestTransitions >= scenario.requiredReplants()) {
            terminalObservedAt = now;
        }
    }

    void markTriggerFired() {
        triggerFired = true;
    }

    long terminalObservedAt() {
        return terminalObservedAt;
    }
}
