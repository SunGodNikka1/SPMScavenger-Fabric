package com.noobk.spmscavenger.village.work;

/**
 * Whether a settlement-work observation covered its required loaded footprint.
 */
public enum WorkFactsCompleteness {
    /** Loaded footprint and caps satisfied — safe for population-support candidate evaluation. */
    COMPLETE,
    /** Partial coverage, cap exceeded, or aborted — fail closed for affirmative work. */
    INCOMPLETE
}
