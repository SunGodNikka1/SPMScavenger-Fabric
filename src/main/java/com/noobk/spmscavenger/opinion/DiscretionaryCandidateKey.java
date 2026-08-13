package com.noobk.spmscavenger.opinion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 44C-R — identity of one discretionary choice, distinct from its broad activity kind.
 *
 * <p>EXPLORE and REST are singleton candidates. SOCIAL is about one immutable subject, so
 * SOCIAL(Bob) and SOCIAL(Alice) are different candidates even though both use the same executor
 * family. This key is comparison/arbitration data only; the complete {@link SocialIntent} remains
 * bound to {@link DiscretionaryIntent}.
 */
public record DiscretionaryCandidateKey(DiscretionaryActivity activity, UUID subjectId) {

    public DiscretionaryCandidateKey {
        Objects.requireNonNull(activity, "activity");
        if ((activity == DiscretionaryActivity.SOCIAL) != (subjectId != null)) {
            throw new IllegalArgumentException(
                    "SOCIAL requires a subject key and other activities must not carry one");
        }
    }

    public static DiscretionaryCandidateKey of(
            DiscretionaryActivity activity, SocialIntent socialSubject) {
        return new DiscretionaryCandidateKey(
                activity, socialSubject == null ? null : socialSubject.targetId());
    }

    public static DiscretionaryCandidateKey singleton(DiscretionaryActivity activity) {
        return of(activity, null);
    }

    public Optional<UUID> subject() {
        return Optional.ofNullable(subjectId);
    }
}
