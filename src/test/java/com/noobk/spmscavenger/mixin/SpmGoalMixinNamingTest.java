package com.noobk.spmscavenger.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Gate: a {@code @Pseudo} mixin into another mod's {@code Goal} subclass must name vanilla methods by
 * their <b>intermediary</b> name as well as their readable one.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * Social Player Mobs ships remapped to intermediary. Its goals override vanilla {@code Goal} methods,
 * so those overrides are named {@code method_6264} etc. in the distributed jar - {@code javap} on
 * {@code playermob-0.96.0} confirms the host Goal overrides remain intermediary-named in the
 * distributed Fabric artifact.
 *
 * <p>Our mixins are {@code remap = false} (correct - SPM's own class names must not be remapped), so
 * {@code method = "canUse"} was never remapped either, matched nothing, and with {@code require = 0}
 * failed <b>silently</b>. Five mixins were completely inert in production while the build stayed
 * green, the mod loaded, and the log contained no error of any kind.
 *
 * <p>{@code require = 0} is right for optional compat and must stay, so the enforcement belongs here,
 * at build time, instead.
 *
 * <h2>Mapping provenance</h2>
 *
 * Read from the mapping file this build actually uses, not from memory:
 * {@code fabric-loom/1.21.1/loom.mappings.1_21_1.layered+hash.2198-v2/mappings-base.tiny}, class
 * {@code net/minecraft/class_1352} -> {@code net.minecraft.world.entity.ai.goal.Goal}. Two of the
 * five are easy to get backwards by guessing: {@code tick} is {@code method_6268} and {@code stop}
 * is {@code method_6270}, not the reverse.
 */
class SpmGoalMixinNamingTest {

    private static final Map<String, String> VANILLA_GOAL_METHODS = new LinkedHashMap<>();

    static {
        VANILLA_GOAL_METHODS.put("canUse", "method_6264");
        VANILLA_GOAL_METHODS.put("canContinueToUse", "method_6266");
        VANILLA_GOAL_METHODS.put("start", "method_6269");
        VANILLA_GOAL_METHODS.put("tick", "method_6268");
        VANILLA_GOAL_METHODS.put("stop", "method_6270");
    }

    private static final Pattern METHOD_ATTRIBUTE =
            Pattern.compile("method *= *([{][^}]*[}]|\"[^\"]+\")");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    @Test
    void mustHappen_everySpmGoalMixinAlsoNamesTheIntermediaryMethod() throws IOException {
        List<String> failures = new ArrayList<>();
        int inspected = 0;

        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/noobk/spmscavenger/mixin"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("games.brennan.playermob") || !source.contains("@Pseudo")) {
                    continue;
                }
                inspected++;

                Matcher attribute = METHOD_ATTRIBUTE.matcher(source);
                while (attribute.find()) {
                    List<String> names = new ArrayList<>();
                    Matcher quoted = QUOTED.matcher(attribute.group(1));
                    while (quoted.find()) {
                        names.add(quoted.group(1));
                    }
                    for (String name : names) {
                        String intermediary = VANILLA_GOAL_METHODS.get(name);
                        if (intermediary != null && !names.contains(intermediary)) {
                            failures.add(file.getFileName() + ": method = " + attribute.group(1)
                                    + " names the readable \"" + name + "\" but not \""
                                    + intermediary + "\" - SPM ships intermediary, so this injector "
                                    + "matches nothing at runtime and require = 0 hides it");
                        }
                    }
                }
            }
        }

        assertTrue(inspected >= 5,
                "expected to inspect the known SPM goal mixins; found only " + inspected
                        + " - if they were renamed, update this gate rather than deleting it");
        if (!failures.isEmpty()) {
            fail("SPM goal mixins that would silently no-op in production:\n  "
                    + String.join("\n  ", failures));
        }
    }

    /**
     * The inverse error: pairing a method SPM does <b>not</b> inherit from {@code Goal}. SPM's own
     * methods ({@code describe}, {@code renderObjectiveReadout}) keep their readable names in the
     * shipped jar, so an intermediary alias there would be noise at best and wrong at worst.
     */
    @Test
    void mustNotHappen_intermediaryAliasOnAnSpmOwnedMethod() throws IOException {
        String readout = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/mixin/ObjectiveReadoutMixin.java"));
        assertTrue(readout.contains("method = \"describe\""),
                "ObjectiveReadout.describe is SPM's own method, confirmed readable in the shipped "
                        + "jar, and must stay a plain readable target");
    }
}
