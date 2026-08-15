package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gate RET-1e — one permanent-removal rule, enforced rather than remembered.
 *
 * <p>The same owner-lifetime defect has been found three times in three stores. This test exists so a
 * store added later — V2's {@code KnownVillager}, trade sessions, per-villager relationships — cannot
 * repeat it silently: a new per-mob {@code SavedData} that is not registered in
 * {@link PerMobSavedData#forgetAll} fails the build.
 */
class PerMobRemovalContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/noobk/spmscavenger");

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

    /**
     * Every {@code *SavedData} holding state attributable to a mob.
     *
     * <p>Detecting {@code Map<UUID, …>} was not enough: {@code FurnaceJobSavedData} keys tickets by
     * {@code BlockPos} and stores the claimant mob <em>inside</em> the value, so the store that most
     * needed the rule was the one the detector missed. Any mention of {@code UUID} is the honest
     * signal — a persisted store that talks about mob identity at all owes an owner-removal path.
     */
    private static List<Path> perMobStores() throws IOException {
        List<Path> found = new ArrayList<>();
        try (var walk = Files.walk(MAIN)) {
            for (Path p : walk.filter(f -> f.getFileName().toString().endsWith("SavedData.java"))
                    .toList()) {
                if (p.getFileName().toString().equals("PerMobSavedData.java")) {
                    continue; // the rule itself, not a store
                }
                if (code(Files.readString(p)).contains("UUID")) {
                    found.add(p);
                }
            }
        }
        return found;
    }

    /** Body of one method, so a window cannot bleed into the next one. */
    private static String methodBody(String source, String name) {
        int start = source.indexOf(name);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf(System.lineSeparator() + "    }", start);
        if (end < 0) {
            end = source.indexOf("\n    }", start);
        }
        return end < 0 ? source.substring(start) : source.substring(start, end);
    }

    @Test
    void mustHappen_everyPerMobStoreIsSweptOnPermanentRemoval() throws IOException {
        List<Path> stores = perMobStores();
        assertTrue(stores.size() >= 3,
                "expected at least village, mining and furnace stores; found " + stores);

        String rule = code(Files.readString(MAIN.resolve("PerMobSavedData.java")));
        for (Path store : stores) {
            String name = store.getFileName().toString().replace(".java", "");
            assertTrue(code(Files.readString(store)).contains("forgetEverywhere"),
                    name + " keys state by mob UUID and must expose forgetEverywhere (Gate RET-1e)");
            assertTrue(rule.contains(name + "\n") || rule.contains(name + "."),
                    name + " must be registered in PerMobSavedData.forgetAll, or a dead mob's state "
                            + "survives in the save forever");
        }
    }

    /** The sweep must never create the storage it is cleaning. */
    @Test
    void mustNotHappen_theSweepMaterialisesStorage() throws IOException {
        for (Path store : perMobStores()) {
            String body = code(Files.readString(store));
            if (!body.contains("peekIn(ServerLevel")) {
                continue;
            }
            // Method-bounded, not a fixed character window: the first version read 400 characters
            // from `peekIn` and ran straight into the neighbouring allocating accessor, so it failed
            // on correct code.
            // Anchor on the DECLARATION: a method reference (Store::peekIn) can appear earlier in
            // the file, and matching that instead reads the wrong method entirely.
            String peekBody = methodBody(body, "peekIn(ServerLevel");
            assertTrue(peekBody.contains("getDataStorage().get("),
                    store.getFileName() + " peekIn must use the non-creating get(...)");
            assertFalse(peekBody.contains("computeIfAbsent"),
                    store.getFileName() + " sweeping with computeIfAbsent creates save files for "
                            + "dimensions that never held state");
        }
    }

    /** The lifecycle handler calls the single rule, not one store at a time. */
    @Test
    void mustHappen_theLifecycleHandlerUsesTheSingleRule() throws IOException {
        String bootstrap = code(Files.readString(MAIN.resolve("SpmScavenger.java")));

        int sweeps = bootstrap.split(java.util.regex.Pattern.quote("PerMobSavedData.forgetAll("), -1)
                .length - 1;
        assertEquals(2, sweeps,
                "both permanent-removal sites (AFTER_DEATH, shouldDestroy unload) sweep every store");

        assertFalse(bootstrap.contains("VillageMemorySavedData.forgetEverywhere("),
                "the handler must not call one store directly - that is how the other two were missed");
    }

    /** Behavioural check of the sweep core, independent of a live server. */
    @Test
    void mustHappen_sweepReleasesEveryStoreHoldingTheOwner() {
        UUID mob = UUID.randomUUID();
        List<List<UUID>> stores = List.of(
                new ArrayList<>(List.of(mob)),
                new ArrayList<>(List.of(UUID.randomUUID())),
                new ArrayList<>(List.of(mob)));

        int released = PerMobSavedData.sweepAll(stores, mob, List::remove);

        assertEquals(2, released, "only the stores that actually held the owner count");
        assertTrue(stores.get(0).isEmpty());
        assertEquals(1, stores.get(1).size(), "another mob's state is untouched");
        assertTrue(stores.get(2).isEmpty());
    }

    @Test
    void mustHappen_sweepIsSilentWithNothingToRelease() {
        assertEquals(0, PerMobSavedData.sweepAll(List.<List<UUID>>of(), UUID.randomUUID(),
                List::remove));
        assertEquals(0, PerMobSavedData.forgetAll(null, UUID.randomUUID()));
        assertEquals(0, PerMobSavedData.forgetAll(null, null));
    }
}
