package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ValidationArtifactBoundaryTest {

    @Test
    void productionSourceHasNoValidationDependencyOrLegacyTask59Wiring() throws IOException {
        Path main = Path.of("src/main");
        try (Stream<Path> paths = Files.walk(main)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                assertTrue(!source.contains("com.noobk.spmscavenger.validation"), path.toString());
                assertTrue(!source.contains("spmscavenger_validation"), path.toString());
                assertTrue(!source.contains("V3RuntimeCampaignController"), path.toString());
                assertTrue(!source.contains("V3RuntimeWitnessCommands"), path.toString());
            }
        }
    }

    @Test
    void syntheticForbiddenProductionEntryBreaksBoundaryPredicate() {
        Set<String> entries = Set.of(
                "fabric.mod.json",
                "com/noobk/spmscavenger/validation/Injected.class");
        assertThrows(AssertionError.class, () -> assertProductionEntries(entries));
    }

    @Test
    void syntheticValidationResourceBreaksBoundaryPredicate() {
        Set<String> entries = Set.of(
                "fabric.mod.json",
                "data/spm_vr/function/scenario/crop_managed_single.mcfunction");
        assertThrows(AssertionError.class, () -> assertProductionEntries(entries));
    }

    @Test
    void syntheticDuplicateValidationClassBreaksBoundaryPredicate() {
        Set<String> production = Set.of("com/noobk/spmscavenger/SpmScavenger.class");
        Set<String> validation = new HashSet<>(Set.of(
                "com/noobk/spmscavenger/validation/SpmScavengerValidation.class",
                "com/noobk/spmscavenger/SpmScavenger.class"));
        assertThrows(AssertionError.class, () -> assertNoDuplicates(production, validation));
    }

    private static void assertProductionEntries(Set<String> entries) {
        boolean forbidden = entries.stream().anyMatch(name ->
                name.startsWith("com/noobk/spmscavenger/validation/")
                        || name.startsWith("data/spm_vr/")
                        || name.contains("V3RuntimeCampaignController")
                        || name.contains("V3Gate0")
                        || name.contains("V3Contamination"));
        if (forbidden) {
            throw new AssertionError("production contains validation entry");
        }
    }

    private static void assertNoDuplicates(Set<String> production, Set<String> validation) {
        Set<String> duplicate = new HashSet<>(validation);
        duplicate.retainAll(production);
        if (!duplicate.isEmpty()) {
            throw new AssertionError("duplicate production class: " + duplicate);
        }
    }
}
