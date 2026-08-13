package com.noobk.spmscavenger.opinion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Task 44B — structural guards for the two things most likely to rot silently here.
 */
class SocialTargetResolverContractTest {

    private static final Path RESOLVER =
            Path.of("src/main/java/com/noobk/spmscavenger/opinion/SocialTargetResolver.java");

    private static String source(Path path) throws IOException {
        return Files.readString(path);
    }

    /**
     * Resolution and re-validation must apply the predicate in exactly one place.
     *
     * <p>A second call site is how the two paths drift, and the drift is invisible: a target legal
     * enough to select but not to adopt yields an intent that can never start. This project has
     * already shipped that shape once, when two checks shared a constant but not the boundary.
     */
    @Test
    void mustHappen_exactlyOneApplicationOfTheLegalityPredicate() throws IOException {
        String resolver = source(RESOLVER);
        int applications = resolver.split(java.util.regex.Pattern.quote(
                "SocialTargetLegality.check("), -1).length - 1;
        assertEquals(1, applications,
                "resolve() and validate() must funnel through one judge(); found " + applications
                        + " applications of the predicate");
        assertTrue(resolver.contains("private static SocialTargetValidity judge("),
                "the single application belongs in the shared tail");
    }

    /**
     * The resolver must re-run the host's search rather than trusting the pulse's stale boolean.
     * {@code eligibleTargetFound} never carried an identity, so it can say somebody was greetable
     * but never whom.
     */
    @Test
    void mustNotHappen_theResolverTrustsTheStalePulseFlag() throws IOException {
        String resolver = source(RESOLVER);
        assertFalse(resolver.contains("eligibleTargetFound()"),
                "the pulse flag is evidence about a past moment, not a target");
        assertTrue(resolver.contains("PlayerMobs.nearestGreetTarget("),
                "target identity must come from SPM's own live search");
        assertTrue(resolver.contains("evidence.range()"),
                "the acquisition radius must be read from the host's pulse, never chosen here");
    }

    /**
     * Scope guard for 44B. Each of these belongs to a later, explicitly gated step, and every one of
     * them would quietly convert a data-model change into a behaviour change.
     */
    @Test
    void mustNotHappen_44bReachesIntoExecutionOrScoring() throws IOException {
        String activities = source(Path.of(
                "src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryActivity.java"));
        assertFalse(activities.contains("SOCIAL"),
                "SOCIAL becomes a scored activity in 44C, not 44B");

        String seamMixin = source(Path.of("src/main/java/com/noobk/spmscavenger/mixin/"
                + "FriendlyGreetAdmissionSeamMixin.java"));
        assertTrue(seamMixin.contains("return original;"),
                "44B must not substitute a target into FriendlyGreet; the redirect still returns "
                        + "the host's own answer unchanged");
        assertFalse(code(seamMixin).contains("SocialIntent"),
                "binding the executor to an intent is a later step, gated behind D-GAO-053");

        String resolver = code(source(RESOLVER));
        for (String forbidden : new String[] {"Utility", "mayStartExecutor", "Inspector"}) {
            assertFalse(resolver.contains(forbidden),
                    "44B must not reach into " + forbidden);
        }
    }

    /**
     * Strip comments before asserting absence.
     *
     * <p>Learned the hard way one build ago: the first version of this guard failed because the seam
     * mixin's javadoc <em>states</em> that it creates no {@code SocialIntent}. A structural test that
     * reads prose as if it were code punishes accurate documentation, which is precisely backwards.
     */
    private static String code(String java) {
        StringBuilder kept = new StringBuilder();
        for (String line : java.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*")
                    || trimmed.startsWith("//")) {
                continue;
            }
            kept.append(line).append(System.lineSeparator());
        }
        return kept.toString();
    }
}
