package com.noobk.spmscavenger.opinion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * GAO-4 — bounded per-mob decision trace (D-GAO-025).
 */
public final class OpinionDecisionTrace {

    public enum Stage {
        SCORE,
        SELECT,
        ABSTAIN,
        INTENT,
        YIELD,
        ADOPT,
        EXECUTOR,
        TERMINAL
    }

    public record Entry(
            long gameTime,
            Stage stage,
            UUID intentId,
            DiscretionaryActivity activity,
            String detail) {}

    private final Deque<Entry> ring = new ArrayDeque<>();

    public void record(long gameTime, Stage stage, UUID intentId, DiscretionaryActivity activity, String detail) {
        if (ring.size() >= DiscretionaryDirectorConstants.TRACE_CAPACITY) {
            ring.removeFirst();
        }
        ring.addLast(new Entry(
                gameTime,
                Objects.requireNonNull(stage, "stage"),
                intentId,
                activity,
                detail == null ? "" : detail));
    }

    public List<Entry> snapshot() {
        return new ArrayList<>(ring);
    }

    public void clear() {
        ring.clear();
    }
}
