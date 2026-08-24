package com.noobk.spmscavenger.datapack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Task-59 pre-launch — structural and fixture-shape validation for {@code spm_vr} (no Minecraft boot).
 */
class SpmVrDatapackStructureTest {

    private static final Path DATAPACK_ROOT = Path.of("test-datapacks/phase-village-raid");
    private static final Path SCENARIO_ROOT = DATAPACK_ROOT.resolve("data/spm_vr/function/scenario");
    private static final Path LIB_ROOT = DATAPACK_ROOT.resolve("data/spm_vr/function/_lib");

    /** Twelve applicable VR-T3 letter rows (a–e, g–m) plus D-VR-084 witness — thirteen preset ids. */
    private static final List<String> PRESET_IDS = List.of(
            "crop_managed_single",
            "crop_interrupt_combat",
            "crop_replant_failure",
            "compost_seed_surplus",
            "population_food_deficit",
            "storage_public_deny",
            "storage_unknown_deny",
            "storage_granted_permit",
            "mandatory_blocks_village_work",
            "crop_multi_mob",
            "crop_hungry_veto",
            "crop_multi_cycle",
            "mandatory_ownership_witness");

    private static final Set<String> LIB_FUNCTIONS = Set.of(
            "reset",
            "setup_village_stub",
            "spawn_ally",
            "claim_village_beds",
            "stage_interrupt_zombie");

    private static final Pattern BOUNDED_REPAIR = Pattern.compile(
            "bounded repair", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATURE_CROP = Pattern.compile(
            "\\[age=7\\]", Pattern.CASE_INSENSITIVE);

    @Test
    void packMcmetaExistsWithExpectedFormat() throws IOException {
        Path meta = DATAPACK_ROOT.resolve("pack.mcmeta");
        assertTrue(Files.isRegularFile(meta), () -> "missing " + meta);
        JsonObject pack = JsonParser.parseString(Files.readString(meta)).getAsJsonObject()
                .getAsJsonObject("pack");
        assertEquals(48, pack.get("pack_format").getAsInt());
    }

    @Test
    void everyManifestPresetHasScenarioFunction() {
        for (String id : PRESET_IDS) {
            Path fn = SCENARIO_ROOT.resolve(id + ".mcfunction");
            assertTrue(Files.isRegularFile(fn),
                    () -> "missing scenario function for preset " + id + ": " + fn);
        }
    }

    @Test
    void sharedLibFunctionsExist() {
        for (String lib : LIB_FUNCTIONS) {
            Path fn = LIB_ROOT.resolve(lib + ".mcfunction");
            assertTrue(Files.isRegularFile(fn), () -> "missing _lib/" + lib + ".mcfunction");
        }
    }

    @Test
    void loadTagReferencesSpmVrLoad() throws IOException {
        Path loadTag = DATAPACK_ROOT.resolve("data/minecraft/tags/function/load.json");
        assertTrue(Files.isRegularFile(loadTag));
        String json = Files.readString(loadTag);
        assertTrue(json.contains("spm_vr:load"), () -> "load tag must reference spm_vr:load");
    }

    @Test
    void operatorEntrypointsAndRunbookExist() throws IOException {
        Path help = DATAPACK_ROOT.resolve("data/spm_vr/function/help.mcfunction");
        Path cleanup = DATAPACK_ROOT.resolve("data/spm_vr/function/cleanup.mcfunction");
        Path readme = DATAPACK_ROOT.resolve("README.md");
        assertTrue(Files.isRegularFile(help), "missing spm_vr:help");
        assertTrue(Files.isRegularFile(cleanup), "missing spm_vr:cleanup");
        assertTrue(Files.isRegularFile(readme), "missing standalone operator README");

        String helpBody = Files.readString(help, StandardCharsets.UTF_8);
        for (String id : PRESET_IDS) {
            assertTrue(helpBody.contains("spm_vr:scenario/" + id),
                    () -> "help must list preset " + id);
        }
        String cleanupBody = Files.readString(cleanup, StandardCharsets.UTF_8);
        assertTrue(cleanupBody.contains("function spm_vr:_lib/reset"));
        assertTrue(cleanupBody.toLowerCase().contains("blocks are preserved"),
                "cleanup must disclose that world blocks are not provenance-safe to erase");

        String readmeBody = Files.readString(readme, StandardCharsets.UTF_8);
        assertTrue(readmeBody.contains("/spmscavenger debug v3 inspect"));
        assertTrue(readmeBody.contains("VR-T3-RUNTIME-EVIDENCE.md"));
        assertTrue(readmeBody.contains("does not force"));
    }

    @Test
    void scenarioFunctionsRejectBoundedRepairWording() throws IOException {
        for (String id : PRESET_IDS) {
            String body = Files.readString(SCENARIO_ROOT.resolve(id + ".mcfunction"),
                    StandardCharsets.UTF_8);
            assertFalse(BOUNDED_REPAIR.matcher(body).find(),
                    () -> id + " must not resurrect bounded-repair semantics");
        }
    }

    @Test
    void settlementBootstrapDoesNotFakeHomeOwnership() throws IOException {
        String setup = Files.readString(LIB_ROOT.resolve("setup_village_stub.mcfunction"),
                StandardCharsets.UTF_8);
        String claim = Files.readString(LIB_ROOT.resolve("claim_village_beds.mcfunction"),
                StandardCharsets.UTF_8);

        assertTrue(setup.contains("minecraft:bell"), "settlement needs meeting bell");
        assertTrue(countOccurrences(setup, "minecraft:villager") >= 2,
                "need >=2 adult villagers for population facts");
        assertTrue(countOccurrences(setup, "_bed[part=head") >= 3,
                "need >=3 HOME beds (2 for vanilla claim + spare vacancy)");
        assertTrue(setup.contains("claim_village_beds"),
                "proximity nudge must be scheduled while villagers acquire beds naturally");
        assertTrue(setup.contains("time set 18000"),
                "night setup helps vanilla bed-acquisition AI");
        assertFalse(setup.contains("NoAI:1b"),
                "NoAI villagers cannot execute vanilla PoiManager.take() bed claim");
        assertFalse(claim.contains("minecraft:home"),
                "must not fake HOME POI via Brain memory injection");
        assertFalse(claim.toLowerCase().contains("sleepingx"),
                "must not fake POI tickets via SleepingX/Y/Z injection");
        assertFalse(claim.contains("data merge entity"),
                "claim helper must not inject villager ownership NBT");
    }

    @Test
    void populationFoodDeficitTargetsVillagerRecipient() throws IOException {
        String body = readScenario("population_food_deficit");
        assertTrue(body.contains("minecraft:villager"),
                "VR-T3e recipient must be a villager");
        assertTrue(body.contains("spm_vr.food_recipient"),
                "mark villager food-recipient for observation");
        assertTrue(body.contains("minecraft:bread"),
                "subject must carry disposable breeding food");
        assertFalse(body.contains("spm_vr.recipient"),
                "must not use a second PlayerMob as recipient");
        assertFalse(body.contains("tag=spm_vr.recipient"),
                "must not tag a PlayerMob as population-food recipient");
    }

    @Test
    void cropInterruptCombatStagesInterruptionAfterPathingWindow() throws IOException {
        String body = readScenario("crop_interrupt_combat");
        assertFalse(body.contains("schedule function spm_vr:_lib/stage_interrupt_zombie"),
                "campaign controller owns the trigger relative to the valid opening tick");
        assertTrue(body.contains("campaign controller"),
                "scenario must disclose controller-owned trigger timing");
        assertFalse(body.matches("(?s).*summon minecraft:zombie.*"),
                "zombie must not spawn immediately in scenario body");
        String stage = Files.readString(LIB_ROOT.resolve("stage_interrupt_zombie.mcfunction"),
                StandardCharsets.UTF_8);
        assertTrue(stage.contains("summon minecraft:zombie"),
                "staged helper must spawn the interrupt hostile");
        assertTrue(stage.contains("execute at @e[type=playermob:player_mob,tag=spm_vr.subject"),
                "scheduled/command-invoked helper must anchor to the fixture subject");
    }

    @Test
    void scheduledBedNudgeAnchorsToFixtureSubject() throws IOException {
        String claim = Files.readString(LIB_ROOT.resolve("claim_village_beds.mcfunction"),
                StandardCharsets.UTF_8);
        assertTrue(countOccurrences(claim,
                "execute at @e[type=playermob:player_mob,tag=spm_vr.subject") >= 2,
                "scheduled helper must not inherit server-spawn execution position");
    }

    @Test
    void cropMultiMobContendsForSingleMatureCrop() throws IOException {
        String body = readScenario("crop_multi_mob");
        assertEquals(1, countMatches(body, MATURE_CROP),
                "VR-T3k must expose exactly one mature crop for contention");
        assertTrue(countOccurrences(body, "playermob:player_mob") >= 2,
                "VR-T3k needs two PlayerMobs");
    }

    @Test
    void cropHungryVetoEstablishesWantsFoodAndAdmissionDenialShape() throws IOException {
        String body = readScenario("crop_hungry_veto");
        assertTrue(body.contains("carrots[age=7]"),
                "host HarvestCropsGoal targets edible crops — not managed wheat alone");
        assertTrue(body.contains("minecraft:air"),
                "empty backpack establishes wantsFood()");
        assertTrue(body.contains("oak_log"),
                "gather demand establishes VillageWorkAdmission denial via mandatory claim");
        assertFalse(body.toLowerCase().contains("minecraft:hunger"),
                "Hunger effect does not drive SPM wantsFood()");
        assertTrue(body.contains("spawn_ally"),
                "must use production ally spawn with VILLAGE_ALLY profile seam");
        assertTrue(Files.readString(LIB_ROOT.resolve("spawn_ally.mcfunction"), StandardCharsets.UTF_8)
                        .contains("village_ally"),
                "ally spawn must assign VILLAGE_ALLY profile");
    }

    @Test
    void storageGrantedPermitUsesProductionOwnCommand() throws IOException {
        String body = readScenario("storage_granted_permit");
        assertTrue(body.contains("spmscavenger village storage own"),
                "storage_granted_permit must use explicit own grant seam");
    }

    @Test
    void mandatoryOwnershipWitnessDoesNotInjectAuthority() throws IOException {
        String body = readScenario("mandatory_ownership_witness");
        assertFalse(body.toLowerCase().contains("mandatoryownership"),
                "witness must not reference injected MandatoryOwnership API");
        assertFalse(body.contains("spmscavenger debug"),
                "witness must not use debug authority injection");
    }

    @Test
    void presetIdCountMatchesClosureRule() {
        assertEquals(13, PRESET_IDS.size(),
                "12 applicable VR-T3 letter rows + D-VR-084 witness");
    }

    @Test
    void noExtraScenarioFilesOutsideManifest() throws IOException {
        if (!Files.isDirectory(SCENARIO_ROOT)) {
            return;
        }
        Set<String> onDisk = Files.list(SCENARIO_ROOT)
                .filter(p -> p.toString().endsWith(".mcfunction"))
                .map(p -> p.getFileName().toString().replace(".mcfunction", ""))
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(PRESET_IDS), onDisk,
                () -> "scenario folder must match manifest preset ids exactly; extra=" +
                        onDisk.stream().filter(id -> !PRESET_IDS.contains(id)).toList());
    }

    private static String readScenario(String id) throws IOException {
        return Files.readString(SCENARIO_ROOT.resolve(id + ".mcfunction"), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static int countMatches(String haystack, Pattern pattern) {
        Matcher matcher = pattern.matcher(haystack);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
