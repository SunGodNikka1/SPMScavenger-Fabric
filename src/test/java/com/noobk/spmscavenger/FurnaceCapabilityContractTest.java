package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * FS-R1 — structural contract for station capability.
 *
 * <p>Whether a blast furnace refuses an oak log is a runtime fact needing a live {@code ServerLevel}
 * and {@code RecipeManager}. What can be enforced here is the property whose absence caused the
 * defect: that no code path commits an <b>input</b> to a station on the strength of
 * {@code instanceof AbstractFurnaceBlockEntity} alone.
 */
class FurnaceCapabilityContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/noobk/spmscavenger");

    private static String source(Path relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    /** Strip comments so accurate documentation cannot fail the guard. */
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
     * The defect in one assertion: the insert path must consult capability before the input leaves
     * the backpack. Once inserted, the mistake is no longer cheap to undo.
     */
    @Test
    void mustHappen_theInsertPathChecksCapabilityFirst() throws IOException {
        String goal = code(source(Path.of("goal/SmeltAtFurnaceGoal.java")));

        int capability = goal.indexOf("FurnaceCapability.canCook");
        int insert = goal.indexOf("FurnaceTransfers.tryInsert");
        assertTrue(capability > 0, "the insert path must ask whether this station can cook the input");
        assertTrue(insert > 0, "sanity: the insert exists");
        assertTrue(capability < insert,
                "capability must be checked before insertion, not after");
    }

    /** Station discovery must be able to take the planned input, or selection stays capability-blind. */
    @Test
    void mustHappen_stationSearchCanFilterByPlannedInput() throws IOException {
        String stations = code(source(Path.of("FurnaceStations.java")));
        assertTrue(stations.contains("FurnaceCapability.canCook"),
                "findUsable/isUsableAt must consult capability");
        assertTrue(stations.contains("ItemStack plannedInput"),
                "and must accept the input to consult it about");

        String goal = code(source(Path.of("goal/SmeltAtFurnaceGoal.java")));
        assertTrue(goal.contains("plannedInput()"),
                "the goal must pass its planned input into station search");
    }

    /**
     * The capability question must be asked of the station, not of a class map. An enumeration of
     * vanilla's three furnace classes is wrong for every modded furnace — which would then be either
     * refused or, worse, accepted and left holding the input.
     */
    @Test
    void mustNotHappen_capabilityIsDecidedByAClassMap() throws IOException {
        String capability = code(source(Path.of("FurnaceCapability.java")));
        // "FurnaceBlockEntity" is deliberately absent from this list: it is a substring of the
        // legitimately imported AbstractFurnaceBlockEntity, so testing for it would fail on correct
        // code. Every class map still needs at least one of the names below.
        for (String enumerated : List.of(
                "BlastFurnaceBlockEntity", "SmokerBlockEntity",
                "RecipeType.SMELTING", "RecipeType.BLASTING", "RecipeType.SMOKING")) {
            assertFalse(capability.contains(enumerated),
                    "capability must come from the station's own recipe check, not from " + enumerated);
        }
        assertTrue(capability.contains("spmscavenger$quickCheck"),
                "it reads the station's own RecipeManager.CachedCheck");
    }

    /** Refusing a usable station costs one job; accepting an unusable one strands input and fuel. */
    @Test
    void mustHappen_capabilityFailsClosed() throws IOException {
        String capability = code(source(Path.of("FurnaceCapability.java")));
        assertTrue(capability.contains("instanceof FurnaceRecipeCheckAccessor accessor"),
                "an unavailable accessor must be handled explicitly");
        assertTrue(capability.contains("catch (RuntimeException"),
                "a station that throws must be refused, not propagate into the goal");

        int guard = capability.indexOf("if (!(furnace instanceof FurnaceRecipeCheckAccessor");
        int ret = capability.indexOf("return false;", guard);
        assertTrue(guard > 0 && ret > guard && ret - guard < 400,
                "the accessor-missing branch returns false");
    }

    /** The accessor must be registered, or it silently never applies and everything fails closed. */
    @Test
    void mustHappen_theAccessorIsRegistered() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/spmscavenger.mixins.json"));
        assertTrue(config.contains("FurnaceRecipeCheckAccessor"),
                "an unregistered accessor mixin never applies; capability would refuse every station");
    }
}
