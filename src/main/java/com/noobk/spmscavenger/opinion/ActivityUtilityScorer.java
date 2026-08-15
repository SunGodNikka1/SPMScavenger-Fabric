package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;

/**
 * GAO-3 — per-candidate utility math. Mood influences score, not legality; preference and
 * repetition remain separate channels.
 */
public final class ActivityUtilityScorer {

    private ActivityUtilityScorer() {
    }

    public static ActivityUtilityBreakdown scoreExplore(
            AffectiveState affect, ActivityOpinionMemory memory) {
        float preference = UtilityNormalizer.channel(memory.preference()) * ActivityUtilityWeights.PREFERENCE;
        float repetition =
                -UtilityNormalizer.repetitionPressure(memory.repetition()) * ActivityUtilityWeights.REPETITION;
        float recentReward =
                UtilityNormalizer.channel(memory.recentReward()) * ActivityUtilityWeights.RECENT_REWARD;
        float failurePressure =
                -UtilityNormalizer.failurePressure(memory.recentFailures()) * ActivityUtilityWeights.FAILURE;
        float boredomFit =
                UtilityNormalizer.channel(affect.boredom()) * ActivityUtilityWeights.EXPLORE_BOREDOM_FIT;
        float stressFit =
                -UtilityNormalizer.channel(affect.stress()) * ActivityUtilityWeights.EXPLORE_STRESS_FIT;
        float noveltyFit =
                UtilityNormalizer.channel(affect.novelty()) * ActivityUtilityWeights.EXPLORE_NOVELTY_FIT;
        float cost = -ActivityUtilityWeights.EXPLORE_COST;

        return ActivityUtilityBreakdown.explore(
                ActivityUtilityWeights.BASE_USEFULNESS_EXPLORE,
                preference,
                boredomFit,
                stressFit,
                noveltyFit,
                recentReward,
                repetition,
                failurePressure,
                cost);
    }

    public static ActivityUtilityBreakdown scoreRest(
            AffectiveState affect, ActivityOpinionMemory memory) {
        float preference = UtilityNormalizer.channel(memory.preference()) * ActivityUtilityWeights.PREFERENCE;
        float repetition =
                -UtilityNormalizer.repetitionPressure(memory.repetition()) * ActivityUtilityWeights.REPETITION;
        float recentReward =
                UtilityNormalizer.channel(memory.recentReward()) * ActivityUtilityWeights.RECENT_REWARD;
        float failurePressure =
                -UtilityNormalizer.failurePressure(memory.recentFailures()) * ActivityUtilityWeights.FAILURE;
        float stressFit =
                UtilityNormalizer.channel(affect.stress()) * ActivityUtilityWeights.REST_STRESS_FIT;
        float boredomFit =
                -UtilityNormalizer.channel(affect.boredom()) * ActivityUtilityWeights.REST_BOREDOM_FIT;
        float cost = -ActivityUtilityWeights.REST_COST;

        return ActivityUtilityBreakdown.rest(
                ActivityUtilityWeights.BASE_USEFULNESS_REST,
                preference,
                boredomFit,
                stressFit,
                recentReward,
                repetition,
                failurePressure,
                cost);
    }

    /**
     * GAO-10 — SOCIAL utility for one specific subject.
     *
     * <p>{@code subjectPreference} is this mob's opinion of <em>this</em> entity, so two mobs looking
     * at the same neighbour can legitimately disagree, and the same mob can want company in general
     * while wanting nothing to do with one particular neighbour.
     *
     * <p>This function is only ever reached once a live opportunity has been validated, so it never
     * has to represent "there is nobody" as a low score — absence removes the candidate entirely
     * rather than making it merely unattractive.
     */
    public static ActivityUtilityBreakdown scoreSocial(
            AffectiveState affect,
            ActivityOpinionMemory memory,
            float sociability,
            float subjectPreference) {
        return scoreSocial(affect, memory, sociability, subjectPreference, 0f);
    }

    public static ActivityUtilityBreakdown scoreSocial(
            AffectiveState affect,
            ActivityOpinionMemory memory,
            float sociability,
            float subjectPreference,
            float settlementSocialBias) {
        float preference =
                UtilityNormalizer.channel(memory.preference()) * ActivityUtilityWeights.PREFERENCE;
        float repetition = -UtilityNormalizer.repetitionPressure(memory.repetition())
                * ActivityUtilityWeights.REPETITION;
        float recentReward = UtilityNormalizer.channel(memory.recentReward())
                * ActivityUtilityWeights.RECENT_REWARD;
        float failurePressure = -UtilityNormalizer.failurePressure(memory.recentFailures())
                * ActivityUtilityWeights.FAILURE;
        float boredomFit =
                UtilityNormalizer.channel(affect.boredom()) * ActivityUtilityWeights.SOCIAL_BOREDOM_FIT;
        float stressFit =
                -UtilityNormalizer.channel(affect.stress()) * ActivityUtilityWeights.SOCIAL_STRESS_FIT;
        // Three inputs, and the first two are in different units that must not be normalised alike.
        //   sociability        PersonalityModel trait, clamped [0, 1]      -> trait01
        //   subjectPreference  EntityOpinionMemory channel, [-100, +100]   -> channel
        // channel() on the trait under-scaled a maximally Friendly mob by 100x (0.32, not 32), so a
        // MEDIUM village bias of +12 outweighed the mob's defining personality trait by ~40x.
        // trait01() on the preference would be just as wrong the other way: it floors at 0, so a
        // *disliked* entity would read neutral instead of negative, and +50 would saturate to full.
        float sociabilityFit =
                UtilityNormalizer.trait01(sociability) * ActivityUtilityWeights.SOCIAL_SOCIABILITY_FIT;
        float preferenceFit = UtilityNormalizer.channel(subjectPreference)
                * ActivityUtilityWeights.SOCIAL_SUBJECT_PREFERENCE;
        float subjectFit = sociabilityFit + preferenceFit + settlementSocialBias;
        float cost = -ActivityUtilityWeights.SOCIAL_COST;

        return ActivityUtilityBreakdown.social(
                ActivityUtilityWeights.BASE_USEFULNESS_SOCIAL,
                preference,
                boredomFit,
                stressFit,
                subjectFit,
                recentReward,
                repetition,
                failurePressure,
                cost);
    }

    public static ActivityUtilityBreakdown score(
            DiscretionaryActivity activity, DiscretionaryScoringInput input) {
        ActivityKind kind = activity.opinionKind();
        ActivityOpinionMemory memory = input.opinionMemory().memoryOf(kind);
        return switch (activity) {
            case EXPLORE -> scoreExplore(input.affectiveState(), memory);
            case REST -> scoreRest(input.affectiveState(), memory);
            case SOCIAL -> scoreSocial(
                    input.affectiveState(),
                    memory,
                    input.sociability(),
                    input.subjectPreference(),
                    input.settlementSocialBias());
        };
    }
}
