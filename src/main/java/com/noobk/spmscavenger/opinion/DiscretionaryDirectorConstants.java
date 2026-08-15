package com.noobk.spmscavenger.opinion;

/**
 * GAO-4 — tunable director knobs anchored to existing cadences.
 *
 * <p>Rationale: observation runs every 10 ticks ({@code ExplorationActivityGoal}); pending TTL of
 * 200 ticks = 20 observation passes (legacy B-19). Minimum commitment 600 ticks matches
 * {@code ExploringGoal} expedition cooldown horizon. Switch margin is a small utility band to
 * prevent score jitter from flipping winners every observation.
 */
public final class DiscretionaryDirectorConstants {

  /** Top utility must exceed this to issue intent; both negative scores abstain (GAO-4-M7). */
  public static final float ACTIVATION_THRESHOLD = 0f;

  /** Challenger must beat incumbent utility by this margin (GAO-4-M11). */
  public static final float SWITCH_MARGIN = 8f;

  /**
   * Commitment window after adoption — matches {@code ExploringGoal} {@code COOLDOWN_TICKS}
   * (600).
   */
  public static final int MIN_COMMITMENT_TICKS = 600;

  /** Pending intent expires if no executor adopts within this window (20 × 10-tick observations). */
  public static final int PENDING_INTENT_TTL_TICKS = 200;

  /**
   * Must match {@code ExplorationActivityGoal} {@code OBSERVE_INTERVAL}. The discretionary director
   * and {@link SocialTargetResolver} run on that observer's cadence.
   */
  public static final int OPINION_OBSERVE_INTERVAL_TICKS = 10;

  /**
   * After the observer pass that may form a SOCIAL intent, allow this many additional greet
   * {@code canUse} attempts to observe a fresh {@link SocialExecutionBindingRegistry} binding.
   */
  public static final int GREET_CLAIM_CAN_USE_RETRY_TICKS = 3;

  /**
   * Bounded defer between publishing a greet admission pulse and letting an unclaimed native greet
   * proceed as {@code SOCIAL_REFLEX}.
   *
   * <p>Derived from observer phasing: worst-case {@code INTERVAL - 1} ticks until the next pass,
   * plus one tick for that pass to run, plus {@link #GREET_CLAIM_CAN_USE_RETRY_TICKS} for SPM's
   * goal selector to retry {@code admit} after binding. Not an indefinite Opinion veto.
   */
  public static final int GREET_CLAIM_WINDOW_TICKS =
          (OPINION_OBSERVE_INTERVAL_TICKS - 1)
                  + 1
                  + GREET_CLAIM_CAN_USE_RETRY_TICKS;

  /** Bounded whole-decision history per mob (D-GAO-042). */
  public static final int TRACE_DECISION_CAPACITY = 24;

  private DiscretionaryDirectorConstants() {}
}
