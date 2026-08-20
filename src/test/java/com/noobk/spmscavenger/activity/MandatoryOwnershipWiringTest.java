package com.noobk.spmscavenger.activity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * D-VR-084 / task-52 — structural wiring contracts, read from source so a silent revert of any
 * seam fails the build.
 *
 * <p>These are the repository's established "structural ban" tests (see
 * {@code TradeAdapterContractTest}): they pin the exact integration shape the brief requires.
 */
class MandatoryOwnershipWiringTest {

    private static String source(String relative) throws IOException {
        String raw = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
        StringBuilder out = new StringBuilder(raw.length());
        boolean inBlock = false;
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlock = true;
                }
                continue;
            }
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** Requirement 8 — VILLAGE_TRADE joins blocksDiscretionaryChoice (D-VR-082-A1 item 2). */
    @Test
    void villageTradeBlocksDiscretionaryChoice() throws IOException {
        String body = source("opinion/DiscretionaryEligibility.java");
        assertTrue(body.contains("VILLAGE_TRADE"),
                "VILLAGE_TRADE must join blocksDiscretionaryChoice");
    }

    /** InvalidationCause gains MANDATORY_PENDING_CLAIM for inspector attribution. */
    @Test
    void invalidationCauseHasMandatoryPendingClaim() throws IOException {
        String body = source("opinion/InvalidationCause.java");
        assertTrue(body.contains("MANDATORY_PENDING_CLAIM"),
                "InvalidationCause must add MANDATORY_PENDING_CLAIM");
    }

    /**
     * Requirement 9 — DiscretionaryActivityDirector consumes MandatoryOwnership instead of
     * DiscretionaryEligibility directly, and gains no policy of its own.
     */
    @Test
    void directorConsumesMandatoryOwnershipNotEligibilityDirectly() throws IOException {
        String body = source("opinion/DiscretionaryActivityDirector.java");
        assertTrue(body.contains("MandatoryOwnership.evaluate"),
                "director must consume MandatoryOwnership.evaluate");
        assertTrue(!body.contains("DiscretionaryEligibility.isDiscretionaryEligible("),
                "director must not call DiscretionaryEligibility.isDiscretionaryEligible directly");
    }

    /** Gate RET-1 — production eviction call sites wired in SpmScavenger. */
    @Test
    void evictionCallSitesWiredInSpmScavenger() throws IOException {
        String body = source("SpmScavenger.java");
        int unload = count(body, "MandatoryOwnershipRegistry.release");
        assertTrue(unload >= 2,
                "entity unload and after-death must release the registry (found " + unload + ")");
        assertTrue(body.contains("MandatoryOwnershipRegistry.shutdownServerState()"),
                "server stop must clear the registry");
    }

    /**
     * P4 — the Gather publisher derives its demand from the canonical mandatory MaterialDemand
     * via the factored ownedMandatoryRoute seam, not from wantsMore()/gather intent.
     */
    @Test
    void gatherPublisherUsesFactoredCanonicalRoute() throws IOException {
        String body = source("goal/GatherResourcesGoal.java");
        assertTrue(body.contains("ownedMandatoryRoute("),
                "Gather must factor ownedMandatoryRoute(cfg)");
        assertTrue(body.contains("MandatoryOwnershipRegistry.publish"),
                "Gather must publish a pending claim through the shared registry");
        assertTrue(body.contains("MandatoryOwnershipRegistry.release"),
                "Gather must release the pending claim at executor start / abandonment");
    }

    /**
     * P5 — the publish call must sit ABOVE scanClock.claim in canUse so the claim is live the
     * instant responsibility is accepted, closing the scan-cadence escape hatch.
     */
    @Test
    void gatherPublishesBeforeScanClockClaim() throws IOException {
        String body = source("goal/GatherResourcesGoal.java");
        int publish = body.indexOf("MandatoryOwnershipRegistry.publish");
        int scanClock = body.indexOf("scanClock.claim(now)");
        assertTrue(publish >= 0, "publish call must exist");
        assertTrue(scanClock >= 0, "scanClock.claim(now) must exist");
        assertTrue(publish < scanClock,
                "publish must be evaluated before scanClock.claim(now) in canUse (P5)");
    }

    /** The registry must not appear in PerMobSavedData (runtime-only; persistence forbidden). */
    @Test
    void registryIsNeverPersistedInPerMobSavedData() throws IOException {
        String body = source("PerMobSavedData.java");
        assertTrue(!body.contains("MandatoryOwnership"),
                "D-VR-084 forbids persistence; do not register the claim store in forgetAll");
    }

    /**
     * P2 / NC-5 — the producer-side generation is minted ONLY at the EXECUTOR_STARTED release,
     * never per scan or per canUse. Moving the increment into canUse/scan fails this test.
     */
    @Test
    void generationIsMintedOnlyAtExecutorStart() throws IOException {
        String body = source("goal/GatherResourcesGoal.java");
        String canUse = bodyOf(body, "public boolean canUse() {");
        assertTrue(!canUse.contains("mandatoryEpisodeGeneration++"),
                "generation must not advance per canUse/scan (P2)");
        String start = bodyOf(body, "public void start() {");
        assertTrue(start.contains("mandatoryEpisodeGeneration++"),
                "generation advances only at the executor-start release");
        assertTrue(start.contains("ReleaseReason.EXECUTOR_STARTED"),
                "and that release is EXECUTOR_STARTED");
        assertTrue(start.contains("liveClaim(mob.getUUID(), now).isPresent()"),
                "the mint is guarded by a live pending claim (a wealth/cooperative start cannot mint)");
    }

    /**
     * NC-8 / P6/P7 — release reasons other than EXECUTOR_STARTED delete without minting. The
     * producer counter appears exactly once (declaration) plus the single start() increment.
     */
    @Test
    void generationCounterAppearsOnlyOncePlusTheStartIncrement() throws IOException {
        String body = source("goal/GatherResourcesGoal.java");
        int declarations = count(body, "int mandatoryEpisodeGeneration = 0;");
        int increments = count(body, "mandatoryEpisodeGeneration++");
        assertTrue(declarations == 1, "one producer-side counter declaration");
        assertTrue(increments == 1, "one mint site (start()), not one per scan/release path");
    }

    /**
     * Single-authority control — the canonical route predicate (select + scanCovers + of) lives
     * in ownedMandatoryRoute; publishRouteExhaustion consumes it and does not re-check coverage.
     * Splitting the predicate back into two questions fails this test (V2-DEF-003 shape).
     */
    @Test
    void scanCoversLivesInTheFactoredRouteOnly() throws IOException {
        String body = source("goal/GatherResourcesGoal.java");
        String owned = bodyOf(body,
                "private java.util.Optional<OwnedRoute> ownedMandatoryRoute(");
        assertTrue(owned.contains("GatherRoutePrecursor.scanCovers("),
                "scanCovers is part of the ONE factored predicate");
        String exhaustion = bodyOf(body,
                "private java.util.Optional<MandatoryHandoffPolicy.HandoffPublication> "
                        + "publishRouteExhaustion(");
        assertTrue(!exhaustion.contains("GatherRoutePrecursor.scanCovers("),
                "publishRouteExhaustion must consume OwnedRoute, not re-check coverage");
    }

    private static String bodyOf(String body, String methodHeader) {
        int start = body.indexOf(methodHeader);
        if (start < 0) {
            return "";
        }
        int brace = body.indexOf('{', start);
        if (brace < 0) {
            return "";
        }
        int depth = 1;
        for (int i = brace + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return body.substring(start);
    }

    private static int count(String body, String needle) {
        int n = 0;
        int idx = 0;
        while ((idx = body.indexOf(needle, idx)) >= 0) {
            n++;
            idx += needle.length();
        }
        return n;
    }
}
