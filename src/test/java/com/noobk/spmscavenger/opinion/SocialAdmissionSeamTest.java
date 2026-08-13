package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 44A — adapter seam proof, static half.
 *
 * <p>Proves the descriptor the {@code @Redirect} names actually exists in the pinned artifact, and
 * that the readiness pulse is bounded, non-allocating and released with the world. The half that
 * cannot be proven here — that the mixin <em>applies</em> alongside the shelter {@code HEAD} hook at
 * runtime — is explicitly out of reach of a unit test and needs an approved launch.
 */
class SocialAdmissionSeamTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("seam".getBytes());

    @AfterEach
    void reset() {
        SocialAdmissionSeam.shutdownServerState();
    }

    /**
     * The redirect target is a string: invisible to the compiler, and Loom leaves an unresolvable
     * name verbatim rather than failing. If SPM renames or restructures this call, the mixin
     * silently stops applying — so the descriptor is verified against the packaged jar here.
     */
    @Test
    void mustHappen_theRedirectTargetExistsInThePinnedArtifact() throws Exception {
        java.nio.file.Path jar = java.nio.file.Path.of(
                "run/.fabric/processedMods/playermob-0.86.0-64b5720b4b825f21.jar");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.exists(jar), "pinned SPM artifact not present in this checkout");

        byte[] entity;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            entity = zip.getInputStream(
                            zip.getEntry("games/brennan/playermob/entity/PlayerMobEntity.class"))
                    .readAllBytes();
        }
        String constants = new String(entity, java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(constants.contains("nearestWhereReaction"),
                "the method the redirect names must exist");
        assertTrue(constants.contains(
                        "(Lgames/brennan/playermob/entity/Reaction;D)"
                                + "Lnet/minecraft/world/entity/LivingEntity;"),
                "with the exact descriptor the @At target declares - a matching name and a changed "
                        + "signature is the silent-failure case");
    }

    // ---- pulse semantics ----

    @Test
    void mustHappen_aWitnessedAttemptIsFreshOnlyBriefly() {
        SocialAdmissionSeam.AdmissionObservation window =
                new SocialAdmissionSeam.AdmissionObservation(
                        100L, 10.0, java.util.UUID.randomUUID());

        assertTrue(window.isFresh(100L));
        assertTrue(window.isFresh(100L + SocialAdmissionSeam.PULSE_LIFETIME_TICKS));
        assertFalse(window.isFresh(100L + SocialAdmissionSeam.PULSE_LIFETIME_TICKS + 1),
                "a stale window must not authorize a much later adoption");
        assertFalse(window.isFresh(99L), "and time does not run backwards");
    }

    @Test
    void mustHappen_thePulseBridgesTheObserverCadence() {
        assertTrue(SocialAdmissionSeam.PULSE_LIFETIME_TICKS >= 20,
                "the director observes every 10 ticks; a pulse shorter than a couple of passes "
                        + "would make readiness unobservable in practice");
    }

    @Test
    void mustNotHappen_readingAWindowCreatesOne() {
        assertTrue(SocialAdmissionSeam.observation(MOB, 0L).isEmpty());
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount(),
                "observation must not allocate - RET-1a and D-GAO-057 agree on this");
        assertFalse(SocialAdmissionSeam.seamObserved(MOB));
    }

    @Test
    void mustHappen_staleWindowsAreEvictedOnRead() {
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount());
        // Nothing recorded, so nothing to evict; the contract is that expiry deletes rather than
        // merely reporting stale - the RET-1a "expired is a predicate, never a deletion" trap.
        assertTrue(SocialAdmissionSeam.observation(MOB, 10_000L).isEmpty());
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount());
    }

    @Test
    void mustHappen_theSeamIsReleasedWithTheWorld() {
        SocialAdmissionSeam.shutdownServerState();
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount());
    }

    /** 44D preserves exact parity when Opinion is disabled and gates the host answer when enabled. */
    @Test
    void mustHappen_theSeamKeepsTheOptionalFailClosedGate() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/"
                        + "FriendlyGreetAdmissionSeamMixin.java"));

        assertTrue(source.contains("if (!OpinionFeatureGate.isEnabled())"));
        assertTrue(source.contains("return original;"),
                "Opinion-off must preserve SPM's own target exactly");
        assertTrue(source.contains("SocialExecutionBindingRegistry")
                        && source.contains(".admit("),
                "Opinion-on admission must bind the current host answer, not a cached pulse");
        assertTrue(source.contains("require = 0"),
                "a missing call site must be survivable, never fatal");
    }

    @Test
    void mustHappen_aResolutionFailureFailsClosed() {
        assertEquals(null, SocialAdmissionSeam.invokeOriginal(null, null, 10.0),
                "no host object, no answer - null is what canUse() sees when nothing is eligible, "
                        + "so the safe direction is 'no greet', never a fabricated target");
    }

    // ---- entity-lifetime bound (Gate RET-1a) ----

    /**
     * Eviction on read alone bounded the map by "mobs still being queried". An unloaded or dead mob
     * is never queried again, so its final window stayed resident for the rest of the session -
     * "expired is a predicate, never a deletion" arriving by a different door.
     */
    @Test
    void mustHappen_unloadAndDeathReleaseTheWindowImmediately() {
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount());

        SocialAdmissionSeam.release(MOB);
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount(), "releasing an absent mob is safe");

        SocialAdmissionSeam.release(null);
        assertEquals(0, SocialAdmissionSeam.trackedWindowCount(), "and a null id is not a crash");
    }

    @Test
    void mustHappen_bothEntityLifecycleHooksReleaseTheSeam() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/SpmScavenger.java"));
        String stripped = source.lines()
                .filter(line -> !line.strip().startsWith("//"))
                .reduce("", (a, b) -> a + b + System.lineSeparator());

        int releases = stripped.split(java.util.regex.Pattern.quote(
                "SocialAdmissionSeam.release("), -1).length - 1;
        assertEquals(2, releases,
                "ENTITY_UNLOAD and AFTER_DEATH must both release - one alone leaves the other path "
                        + "accumulating for the whole session, found " + releases);
        assertTrue(stripped.contains("SocialAdmissionSeam.shutdownServerState()"),
                "and the world boundary still clears the rest");
    }

    /** The failure Task 44A exists to detect: descriptor accepted, handler rejected at load. */
    @Test
    void mustHappen_theRedirectHandlerCoercesInaccessibleHostTypes() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/"
                        + "FriendlyGreetAdmissionSeamMixin.java"));

        assertTrue(source.contains("@Coerce Object playerMob"),
                "an INVOKE redirect handler must match the redirected signature with the owner "
                        + "prepended; the addon cannot name PlayerMobEntity, so the parameter must "
                        + "be coerced or the injector rejects the handler at load while the build "
                        + "stays green");
        assertTrue(source.contains("@Coerce Object reaction"),
                "same for the Reaction enum");
    }
}
