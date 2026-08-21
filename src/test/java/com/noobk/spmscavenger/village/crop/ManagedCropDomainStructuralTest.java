package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ManagedCropDomainStructuralTest {

    @Test
    void cropPackageDoesNotReferenceMandatoryOwnershipOrSpmForage() throws Exception {
        Path root = Path.of("src/main/java/com/noobk/spmscavenger/village/crop");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String body = Files.readString(file);
                assertFalse(body.matches("(?m).*import\\s+.*MandatoryOwnership.*"),
                        file + " must not publish MandatoryOwnership");
                assertFalse(body.matches("(?m).*import\\s+.*ForagePolicy.*"),
                        file + " must not compile-import SPM ForagePolicy");
                assertFalse(body.contains("games.brennan.playermob"),
                        file + " must not compile-import SPM types");
            }
        }
    }

    @Test
    void transactionDoesNotCallDestroyBlock() throws Exception {
        String body = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/crop/CropHarvestTransaction.java"));
        assertFalse(body.contains("destroyBlock("));
    }
}
