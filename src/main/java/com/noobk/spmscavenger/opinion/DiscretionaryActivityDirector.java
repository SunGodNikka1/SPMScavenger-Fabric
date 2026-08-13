package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;

import java.util.UUID;

/**
 * GAO-4 — discretionary activity director entry point (PD-GAO-05 observer cadence).
 */
public final class DiscretionaryActivityDirector {

    private DiscretionaryActivityDirector() {}

    public static void tick(
            UUID mobId,
            long gameTime,
            ActivityObservationService.Observation observation,
            DiscretionaryAvailability availability,
            boolean combatTarget,
            ActivityAdmissions admissions,
            ActivityContinuations continuations) {
        tick(mobId, gameTime, observation, availability, combatTarget, admissions, continuations,
                null);
    }

    /**
     * GAO-10 — the form that can offer SOCIAL.
     *
     * <p>{@code socialOpportunity} is resolved by the caller, which holds the {@code Mob}, on this
     * same decision cadence. It is one map lookup and one id resolution — no world scan and no host
     * relationship query — because the seam already recorded the identity SPM itself chose.
     *
     * <p>An absent opportunity removes SOCIAL from consideration entirely rather than scoring it
     * badly: "there is nobody to greet" is not a weak preference.
     */
    public static void tick(
            UUID mobId,
            long gameTime,
            ActivityObservationService.Observation observation,
            DiscretionaryAvailability availability,
            boolean combatTarget,
            ActivityAdmissions admissions,
            ActivityContinuations continuations,
            SocialIntent socialOpportunity) {
        if (!OpinionFeatureGate.isEnabled()) {
            MobExperienceContext existing = OpinionExperienceRegistry.find(mobId);
            if (existing == null) {
                return;
            }
            existing.discretionaryDirector().tick(new DirectorTickInput(
                    gameTime,
                    false,
                    existing.isFrozen(),
                    combatTarget,
                    observation,
                    new DiscretionaryScoringInput(
                            existing.affectiveState(),
                            existing.opinionMemory(),
                            availability,
                            false,
                            false),
                    admissions,
                    continuations));
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        boolean eligible = DiscretionaryEligibility.isDiscretionaryEligible(observation, combatTarget);

        // Subject-specific inputs are read only when there is a subject. Asking the entity-opinion
        // memory about nobody would allocate nothing useful and invite a null-shaped default to
        // become a silent "neutral opinion about no one".
        float sociability = socialOpportunity == null
                ? 0f
                : context.personalityModel().sociability();
        float subjectPreference = socialOpportunity == null
                ? 0f
                : context.entityOpinionMemory().preference(socialOpportunity.targetId());

        DiscretionaryScoringInput scoringInput = new DiscretionaryScoringInput(
                context.affectiveState(),
                context.opinionMemory(),
                availability,
                eligible,
                true,
                java.util.Optional.ofNullable(socialOpportunity),
                sociability,
                subjectPreference);
        context.discretionaryDirector().tick(new DirectorTickInput(
                gameTime,
                true,
                context.isFrozen(),
                combatTarget,
                observation,
                scoringInput,
                admissions,
                continuations));
    }

    public static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
