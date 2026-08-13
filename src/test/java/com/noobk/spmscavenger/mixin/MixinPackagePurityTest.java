package com.noobk.spmscavenger.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Gate: the mixin package may contain <b>only</b> classes registered in {@code spmscavenger.mixins.json}.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * Mixin owns every class under a declared mixin package and refuses to load any of them as an
 * ordinary class:
 *
 * <pre>
 * IllegalClassLoadError: com.noobk.spmscavenger.mixin.OptionalGoalMobResolver is in a defined
 * mixin package com.noobk.spmscavenger.mixin.* owned by spmscavenger.mixins.json and cannot be
 * referenced directly
 * </pre>
 *
 * <p>A plain helper there compiles, packages and class-loads fine. It throws the first time an
 * injected handler <b>calls</b> it - so the blast radius is a server crash mid-tick
 * ({@code Ticking entity}), not a build error.
 *
 * <p>It hid for as long as it did because the SPM goal mixins were themselves silently inert
 * (see {@link SpmGoalMixinNamingTest}). Fixing the naming made the handlers run for the first time,
 * and this crashed the integrated server on the first greet. Two independent silent failures, the
 * second masked by the first - which is the reason a green build and a clean log prove neither.
 */
class MixinPackagePurityTest {

    private static final Path MIXIN_PACKAGE =
            Path.of("src/main/java/com/noobk/spmscavenger/mixin");
    private static final Path CONFIG = Path.of("src/main/resources/spmscavenger.mixins.json");

    @Test
    void mustNotHappen_anUnregisteredClassInsideTheMixinPackage() throws IOException {
        String config = Files.readString(CONFIG);
        List<String> offenders = new ArrayList<>();
        int seen = 0;

        try (Stream<Path> files = Files.list(MIXIN_PACKAGE)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                seen++;
                if (!config.contains("\"" + name + "\"")) {
                    offenders.add(name);
                }
            }
        }

        assertTrue(seen > 0, "mixin package not found - update this gate rather than deleting it");
        if (!offenders.isEmpty()) {
            fail("classes inside the mixin package that Mixin will refuse to load when an injected "
                    + "handler calls them (move them to com.noobk.spmscavenger.compat): " + offenders);
        }
    }

    /** The helper that caused the crash must stay outside, and stay reachable. */
    @Test
    void mustHappen_theSharedGoalResolverLivesOutsideTheMixinPackage() {
        assertTrue(Files.exists(
                        Path.of("src/main/java/com/noobk/spmscavenger/compat/OptionalGoalMobResolver.java")),
                "OptionalGoalMobResolver belongs in compat, not mixin");
        assertTrue(Files.notExists(MIXIN_PACKAGE.resolve("OptionalGoalMobResolver.java")),
                "and must not be reintroduced into the mixin package");
    }
}
