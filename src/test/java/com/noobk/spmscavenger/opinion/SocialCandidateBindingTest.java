package com.noobk.spmscavenger.opinion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

        // PRODUCTION UNITS. sociability is a PersonalityModel trait in [0, 1]; subjectPreference is
        // an EntityOpinionMemory channel in [-100, +100]. The earlier version of this test passed
        // 80f for BOTH, which is why it never noticed that production divides the trait by 100.
        float neutral = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 0f).total();
        float sociable = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0.8f, 0f).total();
        float liked = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 80f).total();
        float disliked = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0.8f, -80f).total();

        assertTrue(sociable > neutral, "a sociable mob prefers company");
        assertTrue(liked > neutral, "a liked neighbour is worth greeting");
        assertTrue(disliked < sociable,
                "disliking this particular entity must pull the score down even for a sociable mob");
    }

    /**
     * The unit bug, pinned by magnitude rather than by direction.
     *
     * <p>The old test only asserted {@code sociable > neutral}, which held at 0.32 just as well as
     * at 32 — the defect was invisible to a comparison. A mob a player sees labelled <b>Friendly</b>
     * must contribute something that can actually compete with the other terms, so the assertion is
     * on the size of the contribution.
     */
    @Test
    void mustHappen_aMaximallyFriendlyMobContributesTheFullSociabilityWeight() {
        ActivityOpinionMemory memory = new OpinionMemory().memoryOf(ActivityKind.SOCIALIZING);

        float neutral = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 0f).subjectFit();
        float maximal = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 1.0f, 0f).subjectFit();

        assertEquals(ActivityUtilityWeights.SOCIAL_SOCIABILITY_FIT, maximal - neutral, 0.001f,
                "sociability 1.0 must be worth the full weight; channel() made it weight/100");
        assertTrue(maximal - neutral > 12f,
                "and it must outweigh a MEDIUM settlement bias, or the village matters more than "
                        + "the mob's defining personality trait");
    }

    /** The trait scale is linear across its whole range, not just at the ends. */
    @Test
    void mustHappen_sociabilityScalesLinearlyOverTheTraitRange() {
        ActivityOpinionMemory memory = new OpinionMemory().memoryOf(ActivityKind.SOCIALIZING);
        float base = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 0f).subjectFit();

        float w = ActivityUtilityWeights.SOCIAL_SOCIABILITY_FIT;
        for (float[] pair : new float[][] {{0.25f, 0.25f * w}, {0.5f, 0.5f * w}, {0.75f, 0.75f * w}}) {
            float actual = ActivityUtilityScorer
                    .scoreSocial(new AffectiveState(), memory, pair[0], 0f).subjectFit() - base;
            assertEquals(pair[1], actual, 0.001f, "sociability " + pair[0]);
        }
    }

    /**
     * The trap in the repair. {@code trait01} floors at 0, so applying it to the preference channel
     * would erase dislike entirely — a mob would treat an entity it hates as merely neutral — and
     * saturate every preference above +100... which is why the two inputs keep different normalisers.
     */
    @Test
    void mustNotHappen_theTraitNormaliserIsAppliedToTheOpinionChannel() {
        ActivityOpinionMemory memory = new OpinionMemory().memoryOf(ActivityKind.SOCIALIZING);

        float neutral = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 0f).subjectFit();
        float disliked = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, -80f).subjectFit();
        assertTrue(disliked < neutral,
                "a disliked entity must score BELOW neutral; trait01 would floor it to neutral");

        float half = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 50f).subjectFit() - neutral;
        float full = ActivityUtilityScorer
                .scoreSocial(new AffectiveState(), memory, 0f, 100f).subjectFit() - neutral;
        assertEquals(full / 2f, half, 0.001f,
                "+50 is half of +100 on a -100..+100 channel; trait01 would saturate both");
    }

    /** The subject term is its own named component, so explanations cannot misattribute it. */
    @Test
    void mustHappen_subjectFitIsItsOwnComponent() {
        ActivityUtilityBreakdown social = ActivityUtilityScorer.scoreSocial(
                new AffectiveState(),
                new OpinionMemory().memoryOf(ActivityKind.SOCIALIZING),
                0.8f, 80f);
        assertEquals(DiscretionaryActivity.SOCIAL, social.activity());
        assertTrue(social.subjectFit() > 0f, "sociability and entity opinion land in subjectFit");
        assertEquals(0f, social.noveltyFit(),
                "novelty is EXPLORE's term; reusing its slot would make the readout lie");
    }

    @Test
    void repair44c_socialWinnerIssuesTheExactScoredSubjectThroughTheRealDirectorPath() {
        DiscretionaryDirectorState director = new DiscretionaryDirectorState();
        SocialIntent bob = subject(BOB);

        director.tick(socialTick(1_000L, bob, 100f, 100f));

        DiscretionaryIntent issued = director.pendingIntent().orElseThrow();
        assertEquals(DiscretionaryActivity.SOCIAL, issued.activity());
        assertSame(bob, issued.socialSubject(),
                "issuePending must carry the exact SocialIntent that participated in scoring");
        assertEquals(new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, BOB),
                issued.candidateKey());
        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        assertEquals(issued.candidateKey(), decision.selectedCandidateKey(),
                "the causal decision must name Bob too, not merely the SOCIAL enum");
        assertEquals(issued.candidateKey(), decision.candidates().stream()
                .filter(candidate -> candidate.activity() == DiscretionaryActivity.SOCIAL)
                .findFirst()
                .orElseThrow()
                .candidateKey());
    }

    @Test
    void repair44c_aliceDecisionCannotSilentlyRetainPendingBob() {
        DiscretionaryDirectorState director = new DiscretionaryDirectorState();
        SocialIntent bob = subject(BOB);
        SocialIntent alice = subject(ALICE);

        director.tick(socialTick(1_000L, bob, 100f, 100f));
        UUID bobIntentId = director.pendingIntent().orElseThrow().intentId();
        director.tick(socialTick(1_010L, alice, 100f, 100f));

        DiscretionaryIntent issued = director.pendingIntent().orElseThrow();
        assertNotEquals(bobIntentId, issued.intentId(),
                "same activity kind must not alias two different social candidates");
        assertSame(alice, issued.socialSubject());
        assertTrue(issued.boundTo(ALICE));
        assertFalse(issued.boundTo(BOB));
        assertEquals(issued.candidateKey(),
                director.trace().snapshot().getLast().selectedCandidateKey());
    }

    @Test
    void repair44c_activityOnlySocialAdmissionFailsClosed() {
        DiscretionaryDirectorState director = new DiscretionaryDirectorState();
        SocialIntent bob = subject(BOB);
        director.tick(socialTick(1_000L, bob, 100f, 100f));

        assertFalse(director.mayStartExecutor(DiscretionaryActivity.SOCIAL),
                "activity-only admission cannot prove which subject may execute");
        director.adopt(DiscretionaryActivity.SOCIAL, 1_001L);
        assertTrue(director.runningIntent().isEmpty());
        assertTrue(director.pendingIntent().isPresent());

        DiscretionaryCandidateKey bobKey =
                new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, BOB);
        assertTrue(director.mayStartExecutor(bobKey));
        director.adopt(bobKey, 1_001L);
        assertTrue(director.runningIntent().orElseThrow().boundTo(BOB));
    }

    @Test
    void repair44c_candidateKeyTreatsExploreAndRestAsSingletons() {
        assertEquals(
                DiscretionaryCandidateKey.singleton(DiscretionaryActivity.EXPLORE),
                DiscretionaryIntent.pending(
                                1L, DiscretionaryActivity.EXPLORE, 1f, 0f, 1L)
                        .candidateKey());
        assertEquals(
                DiscretionaryCandidateKey.singleton(DiscretionaryActivity.REST),
                DiscretionaryIntent.pending(
                                2L, DiscretionaryActivity.REST, 1f, 0f, 1L)
                        .candidateKey());
        assertThrows(IllegalArgumentException.class,
                () -> DiscretionaryCandidateKey.singleton(DiscretionaryActivity.SOCIAL));
    }

    @Test
    void repair44c_availableSocialSubjectDoesNotAttachWhenExploreWins() {
        DiscretionaryDirectorState director = new DiscretionaryDirectorState();
        SocialIntent bob = subject(BOB);
        AffectiveState affect = new AffectiveState();
        affect.seedChannels(0f, 100f, 0f, 0f, 100f);
        OpinionMemory opinions = new OpinionMemory();
        opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION).seedForTest(100f, 0f, 0);

        director.tick(new DirectorTickInput(
                1_000L, true, false, false,
                com.noobk.spmscavenger.activity.ActivityObservationService.summarize(
                        java.util.EnumSet.of(
                                com.noobk.spmscavenger.activity.ActivityClass.IDLE_CANDIDATE)),
                new DiscretionaryScoringInput(
                        affect, opinions, new DiscretionaryAvailability(true, true, true),
                        true, true, Optional.of(bob), 0f, -100f),
                ActivityAdmissions.of(
                        ActivityAdmission.ready(true), ActivityAdmission.ready(true),
                        ActivityAdmission.ready(true)),
                ActivityContinuations.none()));

        DiscretionaryIntent issued = director.pendingIntent().orElseThrow();
        assertEquals(DiscretionaryActivity.EXPLORE, issued.activity());
        assertNull(issued.socialSubject(), "a losing SOCIAL subject belongs to no EXPLORE intent");
    }

    @Test
    void repair44c_bobToAliceSwitchCarriesAliceThroughYieldArbitration() {
        DiscretionaryDirectorState director = new DiscretionaryDirectorState();
        SocialIntent bob = subject(BOB);
        SocialIntent alice = subject(ALICE);
        director.tick(socialTick(1_000L, bob, 40f, 20f));
        DiscretionaryCandidateKey bobKey =
                new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, BOB);
        director.adopt(bobKey, 1_001L);
        director.markRunning(bobKey, 1_001L);

        long afterCommitment = 1_001L + DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 1L;
        director.tick(socialTick(afterCommitment, alice, 100f, 100f));

        YieldRequest request = director.yieldRequest().orElseThrow();
        assertEquals(
                new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, ALICE),
                request.challengerKey(),
                "a same-activity subject switch is still a distinct yield transaction");
        assertTrue(director.runningIntent().orElseThrow().boundTo(BOB));
        assertTrue(director.pendingIntent().orElseThrow().boundTo(ALICE));
    }

    @Test
    void repair44c_aliceCannotBorrowBobsContinuationWhenAdoptionIsBlocked() {
        DiscretionaryDirectorState director = new DiscretionaryDirectorState();
        SocialIntent bob = subject(BOB);
        SocialIntent alice = subject(ALICE);
        DiscretionaryCandidateKey bobKey =
                new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, BOB);

        director.tick(socialTick(1_000L, bob, 100f, 100f));
        director.adopt(bobKey, 1_001L);
        director.markRunning(bobKey, 1_001L);

        AffectiveState affect = new AffectiveState();
        affect.seedChannels(0f, 100f, 0f, 0f, 0f);
        director.tick(new DirectorTickInput(
                1_010L,
                true,
                false,
                false,
                com.noobk.spmscavenger.activity.ActivityObservationService.summarize(java.util.EnumSet.of(
                        com.noobk.spmscavenger.activity.ActivityClass.IDLE_CANDIDATE)),
                new DiscretionaryScoringInput(
                        affect,
                        new OpinionMemory(),
                        new DiscretionaryAvailability(true, true, true),
                        true,
                        true,
                        Optional.of(alice),
                        100f,
                        100f),
                ActivityAdmissions.of(
                        ActivityAdmission.ready(true),
                        ActivityAdmission.ready(true),
                        ActivityAdmission.blocked(
                                true, ActivityAdoptionBlocker.SCAN_COOLDOWN,
                                "blocked-for-alice")),
                ActivityContinuations.of(
                        ActivityContinuation.notRunning(),
                        ActivityContinuation.notRunning(),
                        ActivityContinuation.valid())));

        assertTrue(director.runningIntent().orElseThrow().boundTo(BOB));
        assertTrue(director.pendingIntent().isEmpty(),
                "Alice is not Bob's incumbent and cannot inherit Bob's continuation exception");
        OpinionDecisionTrace.Candidate social = director.trace().snapshot().getLast()
                .candidates().stream()
                .filter(candidate -> candidate.activity() == DiscretionaryActivity.SOCIAL)
                .findFirst()
                .orElseThrow();
        assertEquals(OpinionDecisionTrace.CandidateState.SUPPRESSED, social.state());
        assertEquals(OpinionDecisionTrace.SuppressionReason.ADOPTION_NOT_READY,
                social.suppressionReason());
        assertFalse(social.execution().retainedByContinuation());
    }

    private static DirectorTickInput socialTick(
            long tick, SocialIntent subject, float sociability, float preference) {
        AffectiveState affect = new AffectiveState();
        affect.seedChannels(0f, 100f, 0f, 0f, 0f);
        return new DirectorTickInput(
                tick,
                true,
                false,
                false,
                com.noobk.spmscavenger.activity.ActivityObservationService.summarize(
                        java.util.EnumSet.of(
                                com.noobk.spmscavenger.activity.ActivityClass.IDLE_CANDIDATE)),
                new DiscretionaryScoringInput(
                        affect,
                        new OpinionMemory(),
                        new DiscretionaryAvailability(true, true, true),
                        true,
                        true,
                        Optional.of(subject),
                        sociability,
                        preference),
                ActivityAdmissions.of(
                        ActivityAdmission.ready(true),
                        ActivityAdmission.ready(true),
                        ActivityAdmission.ready(true)),
                ActivityContinuations.none());
    }
}
