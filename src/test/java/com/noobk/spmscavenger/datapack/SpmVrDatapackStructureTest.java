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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Task-59 pre-launch — structural validation for {@code spm_vr} VR-T3 fixtures (no Minecraft boot).
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

    private static final Set<String> LIB_FUNCTIONS = Set.of("reset", "setup_village_stub", "spawn_ally");

    private static final Pattern BOUNDED_REPAIR = Pattern.compile(
            "bounded repair", Pattern.CASE_INSENSITIVE);

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
    void scenarioFunctionsRejectBoundedRepairWording() throws IOException {
        for (String id : PRESET_IDS) {
            String body = Files.readString(SCENARIO_ROOT.resolve(id + ".mcfunction"),
                    StandardCharsets.UTF_8);
            assertFalse(BOUNDED_REPAIR.matcher(body).find(),
                    () -> id + " must not resurrect bounded-repair semantics");
        }
    }

    @Test
    void storageGrantedPermitUsesProductionOwnCommand() throws IOException {
        String body = Files.readString(
                SCENARIO_ROOT.resolve("storage_granted_permit.mcfunction"), StandardCharsets.UTF_8);
        assertTrue(body.contains("spmscavenger village storage own"),
                "storage_granted_permit must use explicit own grant seam");
    }

    @Test
    void mandatoryOwnershipWitnessDoesNotInjectAuthority() throws IOException {
        String body = Files.readString(
                SCENARIO_ROOT.resolve("mandatory_ownership_witness.mcfunction"), StandardCharsets.UTF_8);
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
}
