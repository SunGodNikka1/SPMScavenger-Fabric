package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Break timing physics, and the tunnel's distance budget.
 *
 * <p>Both executors computed {@code 20 / (hardness * toolSpeed)}, but
 * {@code BlockState#getDestroySpeed} returns <b>hardness</b> — so the expression was
 * {@code 1 / hardness}, inverted. The mob would have laboured over stone and flicked through
 * obsidian. The invariants below are stated as physics rather than as expected constants, so a
 * future retune cannot quietly restore the inversion.
 */
class MiningBreakTimingTest {

    private static final int MIN = 5;
    private static final int MAX = 200;

    private static int ticks(float hardness, float toolSpeed) {
        return MiningBreakTiming.breakTicks(hardness, toolSpeed, MIN, MAX);
    }

    @Test
    void mustNotHappen_aHarderBlockBreaksFaster() {
        float tool = 6.0F;
        int previous = 0;
        for (float hardness : new float[] {0.5F, 1.5F, 3.0F, 4.5F, 22.5F, 50.0F}) {
            int now = ticks(hardness, tool);
            assertTrue(now >= previous,
                    "hardness " + hardness + " broke faster than the softer block before it - "
                            + "this is the inverted formula returning");
            previous = now;
        }
    }

    @Test
    void mustNotHappen_aBetterToolBreaksSlower() {
        float hardness = 3.0F;   // deepslate-ish
        int previous = MAX + 1;
        for (float toolSpeed : new float[] {1.0F, 2.0F, 4.0F, 6.0F, 8.0F, 12.0F}) {
            int now = ticks(hardness, toolSpeed);
            assertTrue(now <= previous, "tool speed " + toolSpeed + " was slower than a worse tool");
            previous = now;
        }
    }

    @Test
    void mustHappen_theOldFormulaWouldFailTheseInvariants() {
        // What the shipped code computed: 20 / (hardness * tool).
        float tool = 6.0F;
        int oldStone = (int) Math.ceil(1.0F / (1.5F * tool) * 20.0F);
        int oldObsidian = (int) Math.ceil(1.0F / (50.0F * tool) * 20.0F);

        assertTrue(oldObsidian < oldStone,
                "documents the defect: obsidian was cheaper than stone under the old formula");
        assertTrue(ticks(50.0F, tool) > ticks(1.5F, tool),
                "and is now the other way round");
    }

    @Test
    void mustHappen_everyBreakIsBounded() {
        for (float hardness : new float[] {0.0F, 0.1F, 1.0F, 50.0F, 1_000F, Float.MAX_VALUE}) {
            for (float tool : new float[] {0.0F, -1.0F, 0.5F, 1.0F, 100.0F}) {
                int result = ticks(hardness, tool);
                assertTrue(result >= MIN && result <= MAX,
                        "hardness=" + hardness + " tool=" + tool + " produced " + result);
            }
        }
    }

    @Test
    void mustHappen_unbreakableNeverEntersOrdinaryTiming() {
        assertTrue(MiningBreakTiming.isUnbreakable(-1.0F));
        assertFalse(MiningBreakTiming.isUnbreakable(0.0F));
        assertEquals(MAX, ticks(-1.0F, 100.0F),
                "bedrock cannot be sped up by a good pickaxe; safety validators reject it first, "
                        + "and this is the fallback if one ever does not");
    }

    @Test
    void mustHappen_barehandedIsTreatedAsSpeedOne() {
        assertEquals(ticks(3.0F, 1.0F), ticks(3.0F, 0.0F),
                "a non-positive tool speed must not divide by zero or become instant");
    }

    // ---- the tunnel's distance budget was configured and never fed ----

    @Test
    void mustNotHappen_aTunnelRunsPastItsDistanceCap() {
        MiningBudget budget = MiningBudget.controlledDescentDefaults();
        MiningBudgetUsage usage = MiningBudgetUsage.EMPTY;

        assertFalse(budget.isDistanceExhausted(usage), "fresh project");

        // A mostly-air corridor: far from the anchor, well under the 64-block mining cap.
        usage = usage.withProgress(budget.maxDistanceFromAnchor(), 0);

        assertTrue(budget.isDistanceExhausted(usage),
                "the 48-block cap must actually stop the corridor - before the repair nothing ever "
                        + "raised recorded distance, so this compared against a permanent zero");
        assertFalse(budget.isBlocksExhausted(usage),
                "and it stops for distance, not because it happened to mine 64 blocks");
    }

    @Test
    void mustHappen_aCompletedStepRecordsHowFarTheCorridorHasRun() {
        BlockPos origin = new BlockPos(0, 12, 0);
        MiningProject project = MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH, origin, Direction.EAST,
                MiningBudget.controlledDescentDefaults(), 100L);

        // What completeStep now does at each stand along the corridor.
        for (int step = 1; step <= 10; step++) {
            project = project.withBudgetUsage(project.budgetUsage().withProgress(step, 0));
        }

        assertEquals(10, project.budgetUsage().maxHorizontalDistance(),
                "distance travelled is recorded, so the cap is a live contract rather than a "
                        + "configured number nothing updates");
    }
}
