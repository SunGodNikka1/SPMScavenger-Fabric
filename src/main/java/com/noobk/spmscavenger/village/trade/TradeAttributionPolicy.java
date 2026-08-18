package com.noobk.spmscavenger.village.trade;

/**
 * V2-DEF-001 — what a PlayerMob transaction must leave in the villager's pending trade attribution.
 *
 * <h2>The contract</h2>
 *
 * <blockquote>A PlayerMob transaction must not erase pending human-player reputation attribution
 * that it did not earn.</blockquote>
 *
 * <p>Note the shape: <b>preservation</b>, not acquisition. The mob is not a player and must never
 * appear in this field. This class can only return a value that was already present — it has no way
 * to invent one, which is deliberate and is what makes the "mob is never credited" row of the gate
 * true by construction rather than by review.
 *
 * <h2>Why the policy is separate from the accessor</h2>
 *
 * Reading and writing {@code Villager.lastTradedPlayer} needs a Mixin, and a Mixin needs a running
 * game. The <i>decision</i> needs neither, so it lives here as a pure generic function and is tested
 * against the three cases the gate names, including the one that is easy to get wrong: a value that
 * changed during the call must win over the one we saved.
 *
 * <p>Generic rather than typed to {@code Player} for exactly that testability — the rule is about
 * null-versus-present, and nothing about it is player-specific.
 */
public final class TradeAttributionPolicy {

    private TradeAttributionPolicy() {
    }

    /**
     * The value that must stand after our transaction.
     *
     * @param before what the villager held immediately before {@code notifyTrade}
     * @param after what it holds immediately after
     * @return {@code after} whenever it is present — a value written during the call is newer than
     *     ours and must not be clobbered — otherwise {@code before}, restoring an attribution that
     *     our session-less trade nulled out
     */
    public static <T> T preserved(T before, T after) {
        return after != null ? after : before;
    }

    /**
     * Whether a write-back is actually required, so callers can skip touching the entity at all.
     *
     * <p>Kept separate from {@link #preserved} because "the answer is unchanged" and "the answer is
     * null" are different facts, and a caller that writes unconditionally would touch the field on
     * every trade including the overwhelmingly common case where nothing was pending.
     */
    public static <T> boolean needsRestore(T before, T after) {
        return before != null && after == null;
    }

    /**
     * The whole save/notify/restore sequence, over an abstract field.
     *
     * <p>Extracted so the <b>ordering</b> is testable without a game, because that is where this
     * repair would actually go wrong: reading {@code before} after the notify, or restoring
     * unconditionally, both compile and both look right. Binding it to a real villager is then three
     * lines that a reader can check by eye.
     *
     * <p>The setter is invoked only when a restore is genuinely required, so the common case — a
     * villager no human has traded — never touches the field at all.
     */
    public static <T> void notifyPreserving(
            java.util.function.Supplier<T> read, java.util.function.Consumer<T> write,
            Runnable notify) {
        T before = read.get();
        notify.run();
        T after = read.get();
        if (needsRestore(before, after)) {
            write.accept(preserved(before, after));
        }
    }
}
