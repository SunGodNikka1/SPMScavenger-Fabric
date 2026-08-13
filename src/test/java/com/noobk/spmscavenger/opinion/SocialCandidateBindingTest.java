package com.noobk.spmscavenger.opinion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.noobk.spmscavenger.experience.ActivityKind;

import java.util.Optional;
import java.util.UUID;

/**
 * 44C — SOCIAL is about somebody, and the somebody belongs to the decision that chose it.
 */
class SocialCandidateBindingTest {

    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();

    private static SocialIntent subject(UUID target) {
        return new SocialIntent(target, 1_000L, 990L, 10.0D);
    }

    /**
     * The defect this invariant exists to make impossible.
     *
     * <p>Decision #91 scored SOCIAL <em>with Bob</em>. If the pending intent recorded only
     * "SOCIAL", an executor could later pair it with whoever the newest observation happened to
     * name — the activity matches, so nothing looks wrong, and Alice gets greeted under a decision
     * that was about Bob. The subject must therefore be part of the decision record, not a lookup.
     */
    @Test
    void mustHappen_theWinningSubjectIsBoundToTheDecisionThatChoseIt() {
        SocialIntent bob = subject(BOB);
        DiscretionaryIntent intent =
                DiscretionaryIntent.pending(91L, DiscretionaryActivity.SOCIAL, bob, 0.78f, 0.4f, 1_000L);

        assertSame(bob, intent.socialSubject(), "the exact instance that won, not an equal copy");
        assertTrue(intent.boundTo(BOB));
        assertFalse(intent.boundTo(ALICE),
                "a later observation naming Alice cannot make this Bob-decision about Alice");
    }

    /** SOCIAL without a subject, or a subject on a non-social activity, are both incoherent. */
    @Test
    void mustNotHappen_activityAndSubjectDisagree() {
        assertThrows(IllegalArgumentException.class,
                () -> DiscretionaryIntent.pending(
                        91L, DiscretionaryActivity.SOCIAL, null, 0.5f, 0.1f, 1_000L),
                "SOCIAL with no subject is the exact hole that lets a target be reconstructed later");
        assertThrows(IllegalArgumentException.class,
                () -> DiscretionaryIntent.pending(
                        91L, DiscretionaryActivity.EXPLORE, subject(BOB), 0.5f, 0.1f, 1_000L),
                "only SOCIAL is about somebody");
    }

    @Test
    void mustHappen_nonSocialIntentsCarryNoSubject() {
        for (DiscretionaryActivity activity :
                new DiscretionaryActivity[] {DiscretionaryActivity.EXPLORE, DiscretionaryActivity.REST}) {
            DiscretionaryIntent intent =
                    DiscretionaryIntent.pending(7L, activity, 0.5f, 0.1f, 1_000L);
            assertNull(intent.socialSubject());
            assertFalse(intent.boundTo(BOB));
        }
    }

    /**
     * "There is nobody to greet" is not a weak preference. An absent opportunity must remove SOCIAL
     * from the comparison, so it can neither win nor appear as a considered-and-rejected option.
     */
    @Test
    void mustNotHappen_socialIsScoredWithNoSubject() {
        DiscretionaryScoringInput without = new DiscretionaryScoringInput(
                new AffectiveState(), new OpinionMemory(),
                new DiscretionaryAvailability(true, true, true), true, true);
        assertFalse(without.socialCandidateAvailable());

        Optional<ScoringResult> scored = IdleOpportunityPolicy.score(without);
        assertTrue(scored.isPresent());
        assertTrue(scored.get().ranked().stream()
                        .noneMatch(b -> b.activity() == DiscretionaryActivity.SOCIAL),
                "scoring nobody produces a number that looks like a judgement about a person who "
                        + "does not exist");
    }

    @Test
    void mustHappen_socialIsScoredOnceASubjectExists() {
        DiscretionaryScoringInput with = new DiscretionaryScoringInput(
                new AffectiveState(), new OpinionMemory(),
                new DiscretionaryAvailability(true, true, true), true, true,
                Optional.of(subject(BOB)), 60f, 40f);
        assertTrue(with.socialCandidateAvailable());

        Optional<ScoringResult> scored = IdleOpportunityPolicy.score(with);
        assertTrue(scored.get().ranked().stream()
                        .anyMatch(b -> b.activity() == DiscretionaryActivity.SOCIAL),
                "a validated subject buys SOCIAL a seat at the table");
    }

    /**
     * Sociability and opinion-of-this-entity must both move the score, and independently: a mob can
     * want company in general while wanting nothing to do with one particular neighbour.
     */
    @Test
    void mustHappen_bothSubjectInputsMoveTheScoreIndependently() {
        ActivityOpinionMemory memory = new OpinionMemory()
                .memoryOf(ActivityKind.SOCIALIZING);

        float neutral = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 0f).total();
        float sociable = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 80f, 0f).total();
        float liked = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 80f).total();
        float disliked = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 80f, -80f).total();

        assertTrue(sociable > neutral, "a sociable mob prefers company");
        assertTrue(liked > neutral, "a liked neighbour is worth greeting");
        assertTrue(disliked < sociable,
                "disliking this particular entity must pull the score down even for a sociable mob");
    }

    /** The subject term is its own named component, so explanations cannot misattribute it. */
    @Test
    void mustHappen_subjectFitIsItsOwnComponent() {
        ActivityUtilityBreakdown social = ActivityUtilityScorer.scoreSocial(
                new AffectiveState(),
                new OpinionMemory().memoryOf(ActivityKind.SOCIALIZING),
                80f, 80f);
        assertEquals(DiscretionaryActivity.SOCIAL, social.activity());
        assertTrue(social.subjectFit() > 0f, "sociability and entity opinion land in subjectFit");
        assertEquals(0f, social.noveltyFit(),
                "novelty is EXPLORE's term; reusing its slot would make the readout lie");
    }
}
