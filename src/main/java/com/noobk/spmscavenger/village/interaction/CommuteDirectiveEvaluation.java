package com.noobk.spmscavenger.village.interaction;

import java.util.Objects;
import java.util.Optional;

/** Director result that keeps semantic validity separate from current movement admission. */
public record CommuteDirectiveEvaluation(
        State state, Optional<CommuteDirective> directive, String cause) {

    public enum State {
        ACTIVE,
        INTERRUPTED,
        CLOSED
    }

    public CommuteDirectiveEvaluation {
        state = Objects.requireNonNull(state, "state");
        directive = Objects.requireNonNull(directive, "directive");
        cause = Objects.requireNonNull(cause, "cause");
        if ((state == State.ACTIVE) != directive.isPresent()) {
            throw new IllegalArgumentException("only ACTIVE carries a movement directive");
        }
    }

    static CommuteDirectiveEvaluation active(CommuteDirective directive) {
        return new CommuteDirectiveEvaluation(State.ACTIVE, Optional.of(directive), "ACTIVE");
    }

    static CommuteDirectiveEvaluation interrupted(String cause) {
        return new CommuteDirectiveEvaluation(State.INTERRUPTED, Optional.empty(), cause);
    }

    static CommuteDirectiveEvaluation closed(String cause) {
        return new CommuteDirectiveEvaluation(State.CLOSED, Optional.empty(), cause);
    }
}
