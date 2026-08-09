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

  /** Bounded trace ring per mob (D-GAO-025). */
  public static final int TRACE_CAPACITY = 24;

  private DiscretionaryDirectorConstants() {}
}
