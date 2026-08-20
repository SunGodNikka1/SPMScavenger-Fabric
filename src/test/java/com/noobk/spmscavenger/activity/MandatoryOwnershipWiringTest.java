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
