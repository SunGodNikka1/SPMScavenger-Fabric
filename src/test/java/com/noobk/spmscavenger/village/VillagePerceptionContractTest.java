package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * V1 structural contract.
 *
 * <p>The perception boundary cannot be proved by a unit test: it needs a {@code ServerLevel} with a
 * populated {@code PoiManager} and a mix of loaded and unloaded chunks, which is a runtime scenario
 * (VR-T1), not a JUnit one. What <em>can</em> be enforced statically is the shape that makes the
 * boundary hard to bypass — one POI touch point, filtered before anything downstream sees it — plus
 * the scope of V1 itself.
 *
 * <p>This is the same technique that caught the inert-mixin and mixin-package defects: assert the
 * property the reviewer would otherwise have to remember to look for.
 */
class VillagePerceptionContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/noobk/spmscavenger");

    private static String source(Path relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    /** Strip comments and javadoc so accurate documentation cannot fail a scope guard. */
    private static String code(String java) {
        StringBuilder out = new StringBuilder(java.length());
        boolean inBlock = false;
        for (String line : java.split("\n", -1)) {
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

    private static List<Path> villageSources() throws IOException {
        try (var stream = Files.list(MAIN.resolve("village"))) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Exactly one class may touch {@code PoiManager}. Storage availability is not perception, and
     * that rule is only enforceable if there is a single place to enforce it.
     */
    @Test
    void mustHappen_onlyVillagePerceptionTouchesPoiManager() throws IOException {
        for (Path file : villageSources()) {
            String body = code(Files.readString(file));
            boolean touchesPoi = body.contains("getPoiManager") || body.contains("PoiRecord");
            if (file.getFileName().toString().equals("VillagePerception.java")) {
                assertTrue(touchesPoi, "VillagePerception is the POI touch point");
            } else {
                assertFalse(touchesPoi,
                        file.getFileName() + " must not reach POI storage; go through VillagePerception");
            }
        }
    }

    /**
     * The raw query result must never leave the class. If {@code getInRange(...)} were returned or
     * stored, every caller would inherit the obligation to filter, and one that forgot would give the
     * mob clairvoyance with no visible defect.
     */
    @Test
    void mustHappen_theRawQueryIsFilteredBeforeItEscapes() throws IOException {
        String body = code(source(Path.of("village/VillagePerception.java")));

        int query = body.indexOf("getInRange");
        assertTrue(query > 0, "the query exists");

        int filter = body.indexOf("withinPerception", query);
        int returned = body.indexOf("return new Observation", query);
        assertTrue(filter > 0 && filter < returned,
                "every record passes withinPerception before an Observation is produced");

        assertFalse(body.contains("public static List<PoiRecord>")
                        || body.contains("public static java.util.stream.Stream"),
                "the unfiltered record set must not be exposed");
    }

    /**
     * The boundary check itself must not cause a chunk load — otherwise asking whether the mob may
     * know about a POI would make it knowable, which is the same self-answering-probe defect as
     * D-GAO-057.
     */
    @Test
    void mustNotHappen_theBoundaryCheckCanLoadAChunk() throws IOException {
        String body = code(source(Path.of("village/VillagePerception.java")));
        assertTrue(body.contains("level.hasChunk("), "hasChunk resolves against loaded chunks only");
        for (String loading : List.of("getChunk(", "getChunkAt(", "forceLoad", "setChunkForced",
                "addRegionTicket", "ensureLoaded")) {
            assertFalse(body.contains(loading), "chunk-loading call in the perception path: " + loading);
        }
    }

    /** The query radius must stay identical to vanilla's, or the admitted set — and anchor — differ. */
    @Test
    void mustHappen_queryRadiusMatchesVanilla() {
        assertEquals(64, VillagePerception.VILLAGE_QUERY_RADIUS,
                "Raids#createOrExtendRaid queries PoiManager at radius 64");
    }

    /**
     * V1 scope guard. Each of these belongs to a later, explicitly gated phase, and every one would
     * turn an ontology change into a behaviour change.
     */
    @Test
    void mustNotHappen_v1ReachesIntoLaterPhases() throws IOException {
        for (Path file : villageSources()) {
            String body = code(Files.readString(file));
            String name = file.getFileName().toString();
            for (String forbidden : List.of(
                    "MerchantOffer",      // V2 trade
                    "MerchantMenu",       // V2 trade
                    "BellBlock",          // V1 bell goal was explicitly excluded
                    "attemptToRing",
                    "getRaidAt",          // V5 raid interrupt
                    "addHeroOfTheVillage",// V6 hero credit
                    "GossipContainer",    // V3/V4 reputation
                    "getPlayerReputation",
                    "RaidContainersGoal", // V3 ally chest gate
                    "SeekShelterGoal")) { // day/night arbitration
                assertFalse(body.contains(forbidden),
                        name + " reaches into a later phase: " + forbidden);
            }
        }
    }

    /**
     * V1 must not add a goal. The user's slice stops at persistent identity; a goal would make the
     * mob act on it, and nothing has yet decided what acting means.
     */
    @Test
    void mustNotHappen_v1AddsAnExecutor() throws IOException {
        for (Path file : villageSources()) {
            String body = code(Files.readString(file));
            assertFalse(body.contains("extends Goal") || body.contains("registerGoals"),
                    file.getFileName() + " must not be an executor in V1");
        }
    }

    /** Gate RET-1a: the eviction call site must exist in production, not only in tests. */
    @Test
    void mustHappen_villageMemoryHasProductionEvictionCallers() throws IOException {
        String bootstrap = code(source(Path.of("SpmScavenger.java")));
        assertTrue(bootstrap.contains("VillageMemorySavedData"),
                "the bootstrap must reference the memory it is responsible for evicting");

        // The two call sites are formatted differently (one fits on a line, one does not), so count
        // the call itself rather than a line shape a formatter is free to change.
        int forgetCalls = bootstrap.split("\\.forget\\(mob\\.getUUID\\(\\)\\)", -1).length - 1;
        assertEquals(2, forgetCalls,
                "expected exactly the ENTITY_UNLOAD and AFTER_DEATH eviction call sites");
    }
}
