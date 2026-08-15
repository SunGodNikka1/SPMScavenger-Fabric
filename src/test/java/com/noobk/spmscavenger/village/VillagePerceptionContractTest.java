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

        int coverage = body.indexOf("PerceptionCoverage.compute", 0);
        assertTrue(coverage > 0 && coverage < query,
                "coverage is computed independently before any PoiManager query");

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
     * V1-D adds a flagless observer in {@code goal}, not in {@code village}. The village package
     * remains perception + memory only.
     */
    @Test
    void mustNotHappen_villagePackageAddsAnExecutor() throws IOException {
        for (Path file : villageSources()) {
            String body = code(Files.readString(file));
            assertFalse(body.contains("extends Goal") || body.contains("registerGoals"),
                    file.getFileName() + " must not be an executor in V1");
        }
    }

    /** V1-D production path: scheduler services {@link VillagePerception#observe} via the service. */
    @Test
    void mustHappen_v1DProductionObservePathExists() throws IOException {
        String service = code(source(Path.of("village/VillagePerceptionService.java")));
        assertTrue(service.contains("VillagePerception.observe"),
                "service must call the sole POI touch point");
        assertTrue(service.contains("VillageMemorySavedData"),
                "service must write memory through saved data");
        String bootstrap = code(source(Path.of("SpmScavenger.java")));
        assertTrue(bootstrap.contains("VillagePerceptionObserver"),
                "observer must be installed on PlayerMob load");
        assertTrue(bootstrap.contains("ServerTickEvents.END_SERVER_TICK"),
                "scheduler must hook server tick end");
        assertTrue(bootstrap.contains("VillagePerceptionScheduler"),
                "scheduler must be wired in bootstrap");
    }

    /** Observer must remain flagless — no MOVE/LOOK authority. */
    @Test
    void mustHappen_v1DObserverIsFlagless() throws IOException {
        String observer = code(source(Path.of("goal/VillagePerceptionObserver.java")));
        assertTrue(observer.contains("EnumSet.noneOf(Goal.Flag.class)"),
                "observer must not claim Goal flags");
        assertTrue(observer.contains("VillagePerceptionEnqueueDebounce"),
                "observer must use explicit enqueue debounce state");
        assertFalse(observer.contains("gameTime - lastEnqueueTick <"),
                "observer must not subtract a sentinel lastEnqueueTick");
        assertFalse(observer.contains("lastEnqueueTick = Long.MIN_VALUE"),
                "observer must not use Long.MIN_VALUE as enqueue sentinel");
        assertFalse(observer.contains("getPoiManager") && observer.contains("PoiRecord"),
                "observer must not touch POI storage");
    }

    /** VR-T1A closed — temporary debug commands and trace plumbing removed after runtime PASS. */
    @Test
    void mustHappen_vrT1aDiagnosticsRemoved() throws IOException {
        assertFalse(Files.exists(MAIN.resolve("command/VillageMemoryDebugCommand.java")),
                "village-memory debug command must be removed after VR-T1A");
        assertFalse(Files.exists(MAIN.resolve("command/VillageProbeDebugCommand.java")),
                "village-probe debug command must be removed after VR-T1A");
        assertFalse(Files.exists(MAIN.resolve("command/VillageDriverDebugCommand.java")),
                "village-driver debug command must be removed after VR-T1A");
        assertFalse(Files.exists(MAIN.resolve("village/VillagePerceptionServiceTrace.java")),
                "service trace plumbing must be removed after VR-T1A");
        assertFalse(Files.exists(MAIN.resolve("village/VillagePerceptionDriverDiagnostics.java")),
                "driver diagnostics must be removed after VR-T1A");
        String bootstrap = code(source(Path.of("SpmScavenger.java")));
        assertFalse(bootstrap.contains("village-memory"),
                "bootstrap must not register village-memory");
        assertFalse(bootstrap.contains("village-probe"),
                "bootstrap must not register village-probe");
        assertFalse(bootstrap.contains("village-driver"),
                "bootstrap must not register village-driver");
        String service = code(source(Path.of("village/VillagePerceptionService.java")));
        assertFalse(service.contains("recordServiceTrace"),
                "observeAndRecord must not retain VR-T1 trace plumbing");
    }

    @Test
    void mustHappen_villagePerceptionReloadPath() throws IOException {
        String bootstrap = code(source(Path.of("SpmScavenger.java")));
        assertTrue(bootstrap.contains("ensureVillagePerceptionObserver"),
                "reload must re-ensure observer + scheduler registration");
        assertTrue(bootstrap.contains("alreadyInstalled(selector)"),
                "alreadyInstalled guard must remain");
        int ensureCalls = bootstrap.split("ensureVillagePerceptionObserver", -1).length - 1;
        assertTrue(ensureCalls >= 2,
                "ensureVillagePerceptionObserver must be called from install paths and reload guard");
    }

    /**
     * Gate RET-1a: eviction must exist in production, not only as an API.
     *
     * <p><b>V1-R1/R2/R3.</b> The first version of this test counted call sites and asserted two —
     * unload and death — thereby enforcing the very defect it guarded. Counting was always the wrong
     * assertion. What matters is which <em>API</em> production uses, because that is what encodes the
     * semantics: the global sweep, never the single-dimension primitive.
     */
    @Test
    void mustHappen_permanentRemovalSweepsEveryDimension() throws IOException {
        String bootstrap = code(source(Path.of("SpmScavenger.java")));

        int sweeps = bootstrap.split(java.util.regex.Pattern.quote("forgetEverywhere("), -1).length - 1;
        assertEquals(2, sweeps,
                "two permanent-removal sites sweep all dimensions: AFTER_DEATH and the "
                        + "shouldDestroy()-gated unload");

        assertFalse(bootstrap.contains(".forget(mob.getUUID())"),
                "production must not use the single-dimension primitive: memory is per-dimension but "
                        + "a mob is not, so a per-level forget leaks every mob that dies away from "
                        + "the dimension it explored");
    }

    /**
     * The V1-R3 leak. A mob keeps its UUID across a dimension change, so Overworld memory outlives a
     * Nether death unless the sweep is global — and since villages are an Overworld feature, that was
     * the common path, not an edge case.
     */
    @Test
    void mustHappen_theSweepIsNonCreating() throws IOException {
        String savedData = code(source(Path.of("village/VillageMemorySavedData.java")));

        int sweep = savedData.indexOf("forgetEverywhere");
        assertTrue(sweep > 0, "the sweep exists");
        String body = savedData.substring(sweep);
        assertTrue(body.contains("peekIn("),
                "the sweep must use the non-creating accessor");
        assertFalse(body.contains("computeIfAbsent"),
                "sweeping all dimensions with computeIfAbsent would materialise village-memory files "
                        + "for the Nether and End of a world that never had one");
    }

    /**
     * The original P0. Fabric's {@code ENTITY_UNLOAD} fires for any entity leaving a server world — a
     * chunk unloading, a player walking away — so the handler must decide on the removal
     * <em>reason</em>, never on the event alone.
     */
    @Test
    void mustNotHappen_unloadDeletesMemoryWithoutCheckingTheReason() throws IOException {
        String bootstrap = code(source(Path.of("SpmScavenger.java")));

        int unload = bootstrap.indexOf("ENTITY_UNLOAD");
        assertTrue(unload > 0, "the unload handler exists");
        int nextHandler = bootstrap.indexOf("ServerLifecycleEvents", unload);
        assertTrue(nextHandler > unload, "found the end of the unload handler");
        String unloadBody = bootstrap.substring(unload, nextHandler);

        if (unloadBody.contains("VillageMemorySavedData")) {
            assertTrue(unloadBody.contains("shouldDestroy()"),
                    "unload may only delete memory when RemovalReason.shouldDestroy() is true");
            assertTrue(unloadBody.indexOf("shouldDestroy()") < unloadBody.indexOf("forgetEverywhere("),
                    "the reason must be checked before the deletion, not after");
        }
    }

    /**
     * V1-R2. Memory age is not an owner-liveness signal: an alive PlayerMob that spends a month
     * mining must not lose its HOME_VILLAGE at the next restart. If village forgetting is ever
     * wanted it is a cognition/memory-decay feature with its own design — not garbage collection.
     */
    @Test
    void mustNotHappen_memoryAgeIsUsedAsAnOrphanCollectionSignal() throws IOException {
        String savedData = code(source(Path.of("village/VillageMemorySavedData.java")));
        assertFalse(savedData.contains("MEMORY_TTL_TICKS"),
                "a staleness TTL over semantic memory deletes live mobs' homes");
        assertFalse(savedData.contains("lastTouchedTick() >") || savedData.contains("> MEMORY_TTL"),
                "no age comparison may gate deletion");

        int prune = savedData.indexOf("public int prune(");
        assertTrue(prune > 0, "the safety valve exists");
        assertFalse(savedData.substring(prune).contains("now"),
                "prune must not take or consult a clock — its only inputs are emptiness and the cap");
    }

    /** The residual bound must still exist, and still have a production caller. */
    @Test
    void mustHappen_theSafetyValveExistsAndIsCalled() throws IOException {
        String savedData = code(source(Path.of("village/VillageMemorySavedData.java")));
        assertTrue(savedData.contains("MAX_TRACKED_MOBS"), "a hard ceiling exists");
        assertTrue(savedData.contains("LOGGER.warn"),
                "reaching the cap is abnormal and must be reported, not silently absorbed");

        String bootstrap = code(source(Path.of("SpmScavenger.java")));
        assertTrue(bootstrap.contains(".prune("),
                "the bound needs a production caller, not just an API (RET-1a)");
    }
}
