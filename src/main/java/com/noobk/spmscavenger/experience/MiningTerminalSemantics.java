package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.mining.MiningExecutionLease;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import com.noobk.spmscavenger.progression.TaskLifecycle;

/**
 * D-GAO-024 — the single classification of a mining terminal, for every consumer.
 *
 * <h2>The fork this removes</h2>
 *
 * {@code MiningProjectEnd} was classified twice, independently:
 *
 * <pre>
 *                MiningProjectEnd
 *          ┌────────────┴─────────────┐
 *   shared outcome policy      PlaceOpinionService
 *   TOOL_FAILURE                TOOL_FAILURE
 *        ↓                           ↓
 *   PROTECTED_INTERRUPT              −6f
 *   ENVIRONMENT_BLOCKED
 *        ↓                           ↓
 *   "do not learn dislike"      "learn dislike"
 * </pre>
 *
 * D-GAO-023 — <em>feasibility/safety/authority outcomes do not automatically imply dislike</em> —
 * was therefore honoured by activity learning and contradicted by place learning two files away.
 * Consumers now receive the semantics rather than re-derive them.
 *
 * <h2>Evidence before learning</h2>
 *
 * A control-plane transition is not experiential evidence. A project revoked before the executor
 * ever adopted it produced nothing observable, so its terminal reason alone must not teach activity,
 * place, entity or environment preference.
 *
 * <p>{@code everStarted == false} is a <b>minimum no-learning gate</b>, not a claim that every
 * independently observed world event is meaningless; and {@code everStarted == true} is
 * <b>necessary but not sufficient</b> — once execution has begun, the shared outcome and cause still
 * decide whether the result is learnable at all.
 *
 * <p>Episode bookkeeping is deliberately <b>not</b> gated by this: a terminal must still be able to
 * close and release its episode without creating subjective preference evidence, or the retention
 * repair (RET-1b) regresses.
 *
 * @param end the exact terminal reason, always preserved for trace and debugging
 * @param outcome shared outcome class
 * @param cause shared cause
 * @param everStarted whether the executor ever adopted this project
 * @param physicalProgressObserved whether the executor recorded observable progress
 */
public record MiningTerminalSemantics(
        MiningProjectEnd end,
        OutcomeClass outcome,
        ExperienceCause cause,
        boolean everStarted,
        boolean physicalProgressObserved) {

    /**
     * Captures execution evidence <b>before</b> lifecycle cleanup.
     *
     * <p>The trap this exists to avoid: {@code everStarted} lives on the lease, and
     * {@code completeProject} clears the lease. A learning layer that queries the lease afterwards
     * reads an absent record and silently treats every terminal as never-started. The owner must
     * hand over the evidence, not leave the consumer to reconstruct deleted history.
     */
    public static MiningTerminalSemantics of(MiningProjectEnd end, MiningExecutionLease lease) {
        boolean started = lease != null && lease.everStarted();
        boolean progressed = lease != null
                && lease.lastExecutionProgressAt() != MiningExecutionLease.NO_PROGRESS_RECORDED;
        return new MiningTerminalSemantics(end, outcomeFor(end), causeFor(end), started, progressed);
    }

    /**
     * Whether this terminal may produce subjective preference learning of any kind.
     *
     * <p>Both halves are required. Without execution there is no evidence; with execution the shared
     * outcome policy still governs, so a player-ordered stop or a protected interrupt teaches
     * nothing even though the mob really was digging.
     */
    public boolean mayLearnPreference() {
        return mayLearnPreference(0);
    }

    /**
     * @param executionFailureCount repetitions of this activity's execution failure, because the
     *     shared policy routes {@code EXECUTION_FAILURE} through
     *     {@link ExperienceOutcomePolicy#mayEmitFailureLearning} with a threshold rather than
     *     through the preference channel. Consulting only the preference channel would be a fork in
     *     the opposite direction - stricter than the shared policy, and equally wrong.
     */
    public boolean mayLearnPreference(int executionFailureCount) {
        if (!everStarted) {
            return false;
        }
        return ExperienceOutcomePolicy.mayEmitPreferenceLearning(outcome)
                || ExperienceOutcomePolicy.mayEmitFailureLearning(outcome, executionFailureCount);
    }

    /** Bookkeeping and trace are always permitted; only learning is gated. */
    public boolean isControlPlaneOnly() {
        return !everStarted;
    }

    public static OutcomeClass outcomeFor(MiningProjectEnd end) {
        return switch (end.lifecycle()) {
            case SUCCESS -> OutcomeClass.VOLUNTARY_SUCCESS;
            case RETRY, BLOCKED -> OutcomeClass.EXECUTION_FAILURE;
            case INTERRUPTED -> OutcomeClass.PROTECTED_INTERRUPT;
            default -> OutcomeClass.VOLUNTARY_ABANDON;
        };
    }

    public static ExperienceCause causeFor(MiningProjectEnd end) {
        return switch (end) {
            case CAVE_FOUND -> ExperienceCause.MINING_CAVE_FOUND;
            case HANDOFF_TUNNEL_SEARCH -> ExperienceCause.MINING_TUNNEL_HANDOFF;
            case NO_PROGRESS -> ExperienceCause.MINING_NO_PROGRESS;
            case SEARCH_BUDGET_EXHAUSTED -> ExperienceCause.MINING_BUDGET_EXHAUSTED;
            case HAZARD -> ExperienceCause.MINING_HAZARD;
            case COMBAT -> ExperienceCause.MINING_COMBAT;
            case PLAYER_ORDER -> ExperienceCause.MINING_PLAYER_ORDER;
            case LEASE_EXPIRED -> ExperienceCause.MINING_LEASE_EXPIRED;
            case TOOL_FAILURE -> ExperienceCause.ENVIRONMENT_BLOCKED;
            default -> ExperienceCause.UNSPECIFIED;
        };
    }

    /** Stress is an affect input, not preference learning, and still requires real execution. */
    public float stress() {
        if (!everStarted) {
            return 0.0f;
        }
        return end == MiningProjectEnd.NO_PROGRESS || end == MiningProjectEnd.HAZARD ? 0.25f : 0.0f;
    }

    public boolean isSuccess() {
        return end.lifecycle() == TaskLifecycle.SUCCESS;
    }
}
