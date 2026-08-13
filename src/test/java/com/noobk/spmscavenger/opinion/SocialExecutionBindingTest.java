package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 44D — exact subject admission, lifecycle evidence and fail-closed classification. */
class SocialExecutionBindingTest {

    private static final UUID MOB = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();

    @AfterEach
    void reset() {
        SocialExecutionBindingRegistry.shutdownServerState();
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void mustHappen_liveHostBobBindsTheExactStartableBobIntent() {
        DiscretionaryDirectorState director = pendingSocial(BOB);

        SocialExecutionBindingRegistry.Binding admitted =
                SocialExecutionBindingRegistry.admitExact(MOB, BOB, 1_001L, director)
                        .orElseThrow();
        assertEquals(BOB, admitted.subjectId());
        assertEquals(director.pendingIntent().orElseThrow().intentId(), admitted.intentId());
        assertEquals(SocialExecutionBindingRegistry.Phase.ADMITTED, admitted.phase());

        assertTrue(SocialExecutionBindingRegistry.startedExact(MOB, director, 1_002L));
        assertTrue(director.runningIntent().orElseThrow().boundTo(BOB));
        assertTrue(SocialExecutionBindingRegistry.isRunning(MOB));
    }

    @Test
    void mustNotHappen_liveHostAliceBorrowsBobsIntent() {
        DiscretionaryDirectorState director = pendingSocial(BOB);

        assertTrue(SocialExecutionBindingRegistry
                .admitExact(MOB, ALICE, 1_001L, director)
                .isEmpty());
        assertEquals(0, SocialExecutionBindingRegistry.trackedBindingCount());
        assertTrue(director.pendingIntent().orElseThrow().boundTo(BOB));
    }

    @Test
    void mustHappen_eachAdmissionHasANewGenerationAndRejectedAdmissionCannotLeaveAStaleOne() {
        DiscretionaryDirectorState director = pendingSocial(BOB);
        long first = SocialExecutionBindingRegistry
                .admitExact(MOB, BOB, 1_001L, director).orElseThrow()
                .admissionGeneration();
        SocialExecutionBindingRegistry.rejectAdmission(MOB);
        assertFalse(SocialExecutionBindingRegistry.binding(MOB).isPresent());

        long second = SocialExecutionBindingRegistry
                .admitExact(MOB, BOB, 1_002L, director).orElseThrow()
                .admissionGeneration();
        assertNotEquals(first, second, "two host admissions are not one execution episode");
    }

    @Test
    void mustHappen_dynamicClassificationRequiresTheRunningExactBinding() {
        DiscretionaryDirectorState director = pendingSocial(BOB);
        Goal greet = new games.brennan.playermob.entity.goal.FriendlyGreetGoal();

        assertEquals(ActivityClass.SOCIAL_REFLEX,
                MoveHolderClassifier.activityClass(greet, null, null, MOB, 1_000L));
        SocialExecutionBindingRegistry.admitExact(MOB, BOB, 1_001L, director).orElseThrow();
        assertEquals(ActivityClass.SOCIAL_REFLEX,
                MoveHolderClassifier.activityClass(greet, null, null, MOB, 1_001L),
                "admission is not execution");
        assertTrue(SocialExecutionBindingRegistry.startedExact(MOB, director, 1_002L));
        assertEquals(ActivityClass.DISCRETIONARY_SOCIAL,
                MoveHolderClassifier.activityClass(greet, null, null, MOB, 1_002L));
        SocialExecutionBindingRegistry.release(MOB);
        assertEquals(ActivityClass.SOCIAL_REFLEX,
                MoveHolderClassifier.activityClass(greet, null, null, MOB, 1_003L));
    }

    @Test
    void mustNotHappen_stopAloneBecomesSuccess() {
        DiscretionaryDirectorState director = pendingSocial(BOB);
        SocialExecutionBindingRegistry.admitExact(MOB, BOB, 1_001L, director).orElseThrow();
        SocialExecutionBindingRegistry.Binding admitted =
                SocialExecutionBindingRegistry.binding(MOB).orElseThrow();
        assertEquals(SocialExecutionBindingRegistry.Terminal.NON_COMPLETED,
                SocialExecutionBindingRegistry.terminalOf(admitted, true));

        SocialExecutionBindingRegistry.startedExact(MOB, director, 1_002L);
        SocialExecutionBindingRegistry.Binding running =
                SocialExecutionBindingRegistry.binding(MOB).orElseThrow();
        assertEquals(SocialExecutionBindingRegistry.Terminal.NON_COMPLETED,
                SocialExecutionBindingRegistry.terminalOf(running, true));
    }

    @Test
    void mustHappen_hostDoneEvidenceProducesOneCompletedClassification() {
        DiscretionaryDirectorState director = pendingSocial(BOB);
        SocialExecutionBindingRegistry.admitExact(MOB, BOB, 1_001L, director).orElseThrow();
        SocialExecutionBindingRegistry.startedExact(MOB, director, 1_002L);
        SocialExecutionBindingRegistry.completionObserved(MOB);
        SocialExecutionBindingRegistry.Binding completed =
                SocialExecutionBindingRegistry.binding(MOB).orElseThrow();

        assertEquals(SocialExecutionBindingRegistry.Terminal.COMPLETED,
                SocialExecutionBindingRegistry.terminalOf(completed, true));
        assertEquals(SocialExecutionBindingRegistry.Terminal.NON_COMPLETED,
                SocialExecutionBindingRegistry.terminalOf(completed, false),
                "turning Opinion off cannot leave a success-capable authority behind");
        SocialExecutionBindingRegistry.release(MOB);
        assertEquals(SocialExecutionBindingRegistry.Terminal.UNBOUND,
                SocialExecutionBindingRegistry.terminalOf(
                        SocialExecutionBindingRegistry.binding(MOB).orElse(null), true));
    }

    @Test
    void mustHappen_optionalMixinUsesOnlyHostProducedDoneEvidence() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/FriendlyGreetAdmissionSeamMixin.java"));
        assertTrue(source.contains("method = {\"tickGift\", \"tickFetch\"}"));
        assertTrue(source.contains("DONE:Lgames/brennan/playermob/entity/goal/FriendlyGreetGoal$Phase;"));
        assertTrue(source.contains("opcode = Opcodes.GETSTATIC"));
        assertTrue(source.contains("shift = At.Shift.BEFORE"));
        assertFalse(source.contains("opcode = Opcodes.PUTFIELD"),
                "tickGift also writes FETCH; an all-phase-write hook would manufacture success");
        assertTrue(source.contains("require = 0"));
        assertFalse(source.contains("canContinueToUse()"),
                "terminal classification must not probe host continuation");
        assertFalse(source.contains("reactionToward("),
                "terminal classification must not warm SPM's relationship memo path");
    }

    private static DiscretionaryDirectorState pendingSocial(UUID subjectId) {
        DiscretionaryDirectorState director =
                OpinionExperienceRegistry.contextFor(MOB).discretionaryDirector();
        SocialIntent subject = new SocialIntent(subjectId, 1_000L, 990L, 10.0D);
        AffectiveState affect = new AffectiveState();
        affect.seedChannels(0f, 100f, 0f, 0f, 0f);
        director.tick(new DirectorTickInput(
                1_000L,
                true,
                false,
                false,
                com.noobk.spmscavenger.activity.ActivityObservationService.summarize(
                        java.util.List.of(ActivityClass.IDLE_CANDIDATE)),
                new DiscretionaryScoringInput(
                        affect,
                        new OpinionMemory(),
                        new DiscretionaryAvailability(true, true, true),
                        true,
                        true,
                        Optional.of(subject),
                        100f,
                        100f),
                ActivityAdmissions.of(
                        ActivityAdmission.ready(true),
                        ActivityAdmission.ready(true),
                        ActivityAdmission.ready(true)),
                ActivityContinuations.none()));
        assertTrue(director.pendingIntent().orElseThrow().boundTo(subjectId));
        return director;
    }
}
