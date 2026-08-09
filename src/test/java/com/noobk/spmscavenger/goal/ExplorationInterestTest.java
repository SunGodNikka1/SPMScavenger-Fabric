package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.goal.ExplorationInterest.Signal;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic that decides where mobs choose to go. No Minecraft types involved. */
final class ExplorationInterestTest {

    @Test
    void presenceIsScoredAndQuantityIsNotRepresentable() {
        // A base with 180 chests scores exactly what one chest scores: the API takes a set of
        // categories, so quantity cannot leak into the number at all.
        assertEquals(Signal.STORAGE.weight, ExplorationInterest.chunkScore(EnumSet.of(Signal.STORAGE)));
        assertEquals(Signal.STORAGE.weight + Signal.TRANSPORT.weight + Signal.SMELTING.weight,
                ExplorationInterest.chunkScore(
                        EnumSet.of(Signal.STORAGE, Signal.TRANSPORT, Signal.SMELTING)));
    }

    @Test
    void anEmptyChunkIsWorthNothingAndIsNeverAPenalty() {
        assertEquals(0, ExplorationInterest.chunkScore(EnumSet.noneOf(Signal.class)));
        assertEquals(0, ExplorationInterest.routeScore(0));
        // Unknown chunks report 0 too; wilderness must never score below empty.
        assertTrue(ExplorationInterest.routeScore(0) >= 0);
        assertEquals(0, ExplorationInterest.routeScore(-25));
    }

    @Test
    void everyChunkIsCappedSoOneWarehouseCannotDominate() {
        int everything = ExplorationInterest.chunkScore(EnumSet.allOf(Signal.class));
        assertEquals(ExplorationInterest.CHUNK_CAP, everything);
        assertTrue(everything <= ExplorationInterest.CHUNK_CAP);
    }

    @Test
    void onlyTheStrongestSignalSaturatesAChunk() {
        assertTrue(ExplorationInterest.saturates(Signal.SPAWNER));
        for (Signal signal : Signal.values()) {
            if (signal != Signal.SPAWNER) {
                assertFalse(ExplorationInterest.saturates(signal), signal + " must not short-circuit");
            }
        }
    }

    @Test
    void interestCannotOverrideTheRecentDestinationPenalty() {
        // The route score subtracts 100 per repeated destination region. Interest must stay under
        // that or a mob would walk back to the same rewarding chunk expedition after expedition,
        // which is the quantity problem wearing a different hat.
        assertTrue(ExplorationInterest.ROUTE_CAP < 100,
                "interest must not defeat the anti-repetition penalty");
        assertEquals(ExplorationInterest.ROUTE_CAP, ExplorationInterest.routeScore(10_000));
    }

    @Test
    void interestOutweighsTheWeakerNoveltyTermsSoItCanActuallyBreakTies() {
        // Repeated heading is -35 and a merely-visited region is -20; a real find should win.
        assertTrue(ExplorationInterest.CHUNK_CAP > 35);
        assertTrue(ExplorationInterest.ROUTE_CAP > 35 + 20);
    }

    @Test
    void signalWeightsKeepTheirIntendedOrdering() {
        assertTrue(Signal.SPAWNER.weight > Signal.VAULT.weight);
        assertTrue(Signal.VAULT.weight > Signal.STORAGE.weight);
        assertTrue(Signal.STORAGE.weight > Signal.REST.weight);
        assertTrue(Signal.REST.weight >= Signal.BREWING.weight);
        assertTrue(Signal.BREWING.weight > Signal.SMELTING.weight);
        assertTrue(Signal.SMELTING.weight > Signal.TRANSPORT.weight);
        assertTrue(Signal.TRANSPORT.weight > Signal.OTHER.weight);
    }
}
