package com.noobk.spmscavenger.mining;

/**
 * MI-7C — whether natural descent search has genuinely exhausted local opportunities.
 */
public enum NaturalDescentStatus {
  /** Valid descent expedition still has unexplored opportunities within budget. */
  SEARCHING,
  /** Legitimate reachable natural descent exists right now. */
  AVAILABLE,
  /** Opportunity exists but combat, path, hazard, or interruption blocks use. */
  TEMPORARILY_BLOCKED,
  /**
   * May start {@link MiningProjectMode#CONTROLLED_DESCENT} (MI-7E) — all exhaustion gates satisfied.
   */
  EXHAUSTED
}
