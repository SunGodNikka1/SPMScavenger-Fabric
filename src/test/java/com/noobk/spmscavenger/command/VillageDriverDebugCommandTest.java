package com.noobk.spmscavenger.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.village.VillagePerceptionServiceTrace;
import org.junit.jupiter.api.Test;

class VillageDriverDebugCommandTest {

    @Test
    void formatRecordResultUsesUppercaseLabels() {
        assertEquals("RECORDED", VillageDriverDebugCommand.formatRecordResult(
                VillagePerceptionServiceTrace.RecordResult.RECORDED));
        assertEquals("EMPTY", VillageDriverDebugCommand.formatRecordResult(
                VillagePerceptionServiceTrace.RecordResult.EMPTY));
        assertEquals("SKIPPED", VillageDriverDebugCommand.formatRecordResult(
                VillagePerceptionServiceTrace.RecordResult.SKIPPED));
        assertEquals("NOT_RUN", VillageDriverDebugCommand.formatRecordResult(
                VillagePerceptionServiceTrace.RecordResult.NOT_RUN));
    }

    @Test
    void formatTickNeverWhenUnset() {
        assertEquals("never", VillageDriverDebugCommand.formatTick(Long.MIN_VALUE));
        assertEquals("42", VillageDriverDebugCommand.formatTick(42L));
    }
}
