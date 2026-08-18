package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * V2-DEF-001 — <b>a PlayerMob transaction must not erase pending human-player reputation
 * attribution that it did not earn.</b>
 *
 * <p>The three rows of the gate in {@code docs/porting/KNOWN_DEFECTS.md}, plus the two ways this
 * repair would plausibly be written wrong: reading {@code before} <i>after</i> the notify, and
 * restoring unconditionally.
 *
 * <p>A mutable box stands in for {@code Villager.lastTradedPlayer}. The field itself needs a Mixin
 * and therefore a running game; the <i>decision</i> needs neither, and the decision is the part that
 * can be wrong. Runtime gossip confirmation remains the AV-1 proof class for the wiring.
 */
class TradeAttributionPolicyTest {

    /** Stands in for the villager's field; records every write so needless ones are visible. */
    private static final class Field {
        private final AtomicReference<String> value = new AtomicReference<>();
        private final List<String> writes = new ArrayList<>();

        Field(String initial) {
            value.set(initial);
        }

        String read() {
            return value.get();
        }

        void write(String next) {
            writes.add(next);
            value.set(next);
        }
    }

    /** Vanilla's behaviour in our session-less path: rewardTradeXp assigns getTradingPlayer(). */
    private static Runnable vanillaNullsIt(Field field) {
        return () -> field.value.set(null);
    }

    /**
     * Gate row 1 — the defect itself. Player trades, mob trades, villager has not levelled yet.
     */
    @Test
    void mustHappen_aPendingHumanAttributionSurvivesAMobTrade() {
        Field field = new Field("Steve");

        TradeAttributionPolicy.notifyPreserving(field::read, field::write, vanillaNullsIt(field));

        assertEquals("Steve", field.read(),
                "the human earned this TRADE gossip; the mob's trade must not delete it");
        assertEquals(List.of("Steve"), field.writes, "restored exactly once");
    }

    /**
     * Gate row 2 — the mob must never be credited, and the common case must not write at all.
     */
    @Test
    void mustNotHappen_theMobIsCreditedWhenNothingWasPending() {
        Field field = new Field(null);

        TradeAttributionPolicy.notifyPreserving(field::read, field::write, vanillaNullsIt(field));

        assertNull(field.read(), "a PlayerMob is not a player and earns no TRADE reputation");
        assertEquals(List.of(), field.writes,
                "and a villager no human has traded must not be touched at all");
    }

    /**
     * Gate row 3 — a value written during the call is newer than the one we saved.
     *
     * <p>The plausible wrong implementation restores unconditionally and would put {@code Steve}
     * back over {@code Alex}, handing the gossip to the wrong player.
     */
    @Test
    void mustNotHappen_aSavedValueIsRestoredOverANewerOne() {
        Field field = new Field("Steve");

        TradeAttributionPolicy.notifyPreserving(field::read, field::write,
                () -> field.value.set("Alex"));

        assertEquals("Alex", field.read(), "the newer attribution wins");
        assertEquals(List.of(), field.writes, "and no write is needed to leave it alone");
    }

    /** Preservation must be idempotent: a second mob trade changes nothing. */
    @Test
    void mustHappen_preservationIsIdempotentAcrossSuccessiveMobTrades() {
        Field field = new Field("Steve");

        TradeAttributionPolicy.notifyPreserving(field::read, field::write, vanillaNullsIt(field));
        TradeAttributionPolicy.notifyPreserving(field::read, field::write, vanillaNullsIt(field));

        assertEquals("Steve", field.read(), "two mob trades erase no more than one did");
        assertEquals(List.of("Steve", "Steve"), field.writes);
    }

    /**
     * Ordering control. {@code before} must be sampled <b>before</b> the notify.
     *
     * <p>An implementation that reads it afterwards sees {@code null} and restores nothing, which is
     * the original defect wearing the repair's name. Here the notify both observes and clears the
     * value, so a late read cannot produce {@code Steve} by luck.
     */
    @Test
    void mustNotHappen_theSavedValueIsSampledAfterTheNotify() {
        Field field = new Field("Steve");
        List<String> observedDuring = new ArrayList<>();

        TradeAttributionPolicy.notifyPreserving(field::read, field::write, () -> {
            observedDuring.add(field.read());
            field.value.set(null);
        });

        assertEquals(List.of("Steve"), observedDuring, "the value was still present at notify time");
        assertEquals("Steve", field.read(),
                "so it must have been sampled before, not after, the notify");
    }

    /** The two primitives, stated directly, so callers can rely on them independently. */
    @Test
    void mustHappen_thePrimitivesAgreeWithTheSequence() {
        assertEquals("Steve", TradeAttributionPolicy.preserved("Steve", null));
        assertEquals("Alex", TradeAttributionPolicy.preserved("Steve", "Alex"));
        assertNull(TradeAttributionPolicy.preserved(null, null));

        org.junit.jupiter.api.Assertions.assertTrue(
                TradeAttributionPolicy.needsRestore("Steve", null));
        org.junit.jupiter.api.Assertions.assertFalse(
                TradeAttributionPolicy.needsRestore(null, null));
        org.junit.jupiter.api.Assertions.assertFalse(
                TradeAttributionPolicy.needsRestore("Steve", "Alex"));
    }
}
