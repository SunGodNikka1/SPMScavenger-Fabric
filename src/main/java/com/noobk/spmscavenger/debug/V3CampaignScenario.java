package com.noobk.spmscavenger.debug;

import java.util.Arrays;
import java.util.Optional;

/** Locked Task-59 preset allowlist and observation clocks. */
enum V3CampaignScenario {
    CROP_MANAGED_SINGLE("crop_managed_single", "VR-T3a", true,
            CompletionKind.REPLANT, 0, 200, 0, 2400, 1),
    CROP_INTERRUPT_COMBAT("crop_interrupt_combat", "VR-T3b", true,
            CompletionKind.COMBAT_RELEASE, 120, 600, 0, 2400, 0),
    CROP_REPLANT_FAILURE("crop_replant_failure", "VR-T3c", true,
            CompletionKind.FIXED, 0, 0, 800, 800, 0),
    COMPOST_SEED_SURPLUS("compost_seed_surplus", "VR-T3d", true,
            CompletionKind.COMPOST_DEBIT, 0, 400, 0, 2400, 0),
    POPULATION_FOOD_DEFICIT("population_food_deficit", "VR-T3e", true,
            CompletionKind.FIXED, 0, 0, 1200, 1200, 0),
    STORAGE_PUBLIC_DENY("storage_public_deny", "VR-T3g", true,
            CompletionKind.FIXED, 0, 0, 800, 800, 0),
    STORAGE_UNKNOWN_DENY("storage_unknown_deny", "VR-T3h", false,
            CompletionKind.FIXED, 0, 0, 800, 800, 0),
    STORAGE_GRANTED_PERMIT("storage_granted_permit", "VR-T3i", true,
            CompletionKind.FIXED, 0, 0, 800, 800, 0),
    MANDATORY_BLOCKS_VILLAGE_WORK("mandatory_blocks_village_work", "VR-T3j", true,
            CompletionKind.FIXED, 0, 0, 1000, 1000, 0),
    CROP_MULTI_MOB("crop_multi_mob", "VR-T3k", true,
            CompletionKind.REPLANT, 0, 200, 0, 2400, 1),
    CROP_HUNGRY_VETO("crop_hungry_veto", "VR-T3l", true,
            CompletionKind.FIXED, 0, 0, 800, 800, 0),
    CROP_MULTI_CYCLE("crop_multi_cycle", "VR-T3m", true,
            CompletionKind.MULTI_REPLANT, 0, 400, 0, 4000, 2),
    MANDATORY_OWNERSHIP_WITNESS("mandatory_ownership_witness", "D-VR-084", true,
            CompletionKind.FIXED, 0, 0, 1000, 1000, 0);

    enum CompletionKind {
        FIXED,
        REPLANT,
        COMBAT_RELEASE,
        COMPOST_DEBIT,
        MULTI_REPLANT
    }

    private final String id;
    private final String rowId;
    private final boolean requiresGate0;
    private final CompletionKind completionKind;
    private final int triggerDelayTicks;
    private final int stabilizationTicks;
    private final int fixedWindowTicks;
    private final int maxWindowTicks;
    private final int requiredReplants;

    V3CampaignScenario(
            String id,
            String rowId,
            boolean requiresGate0,
            CompletionKind completionKind,
            int triggerDelayTicks,
            int stabilizationTicks,
            int fixedWindowTicks,
            int maxWindowTicks,
            int requiredReplants) {
        this.id = id;
        this.rowId = rowId;
        this.requiresGate0 = requiresGate0;
        this.completionKind = completionKind;
        this.triggerDelayTicks = triggerDelayTicks;
        this.stabilizationTicks = stabilizationTicks;
        this.fixedWindowTicks = fixedWindowTicks;
        this.maxWindowTicks = maxWindowTicks;
        this.requiredReplants = requiredReplants;
    }

    static Optional<V3CampaignScenario> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    String id() {
        return id;
    }

    String rowId() {
        return rowId;
    }

    boolean requiresGate0() {
        return requiresGate0;
    }

    CompletionKind completionKind() {
        return completionKind;
    }

    int triggerDelayTicks() {
        return triggerDelayTicks;
    }

    int stabilizationTicks() {
        return stabilizationTicks;
    }

    int fixedWindowTicks() {
        return fixedWindowTicks;
    }

    int maxWindowTicks() {
        return maxWindowTicks;
    }

    int requiredReplants() {
        return requiredReplants;
    }
}
