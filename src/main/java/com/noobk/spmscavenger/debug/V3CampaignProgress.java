package com.noobk.spmscavenger.debug;

/** Pure row-clock state; it classifies observation completeness, never product correctness. */
final class V3CampaignProgress {

    enum Disposition {
        OBSERVING,
        OBSERVATION_COMPLETE,
        INCOMPLETE
    }

    record Probe(int replantedTargetMask, int subjectSeedCount, boolean combatTarget) {
    }

    record Decision(Disposition disposition, boolean fireDeclaredTrigger, String reason) {
    }

    private final V3CampaignScenario scenario;
    private final long openingTick;
    private final int initialSeedCount;
    private boolean triggerFired;
    private boolean combatSeen;
    private int observedReplantMask;
    private long terminalObservedAt = -1L;

    private V3CampaignProgress(
            V3CampaignScenario scenario, long openingTick, int initialSeedCount) {
        this.scenario = scenario;
        this.openingTick = openingTick;
        this.initialSeedCount = initialSeedCount;
    }

    static V3CampaignProgress open(
            V3CampaignScenario scenario, long openingTick, int initialSeedCount) {
        return new V3CampaignProgress(scenario, openingTick, initialSeedCount);
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
            case REPLANT, MULTI_REPLANT -> {
                observedReplantMask |= probe.replantedTargetMask();
                if (terminalObservedAt < 0L
                        && Integer.bitCount(observedReplantMask) >= scenario.requiredReplants()) {
                    terminalObservedAt = now;
                }
            }
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

    void markTriggerFired() {
        triggerFired = true;
    }

    long terminalObservedAt() {
        return terminalObservedAt;
    }
}
