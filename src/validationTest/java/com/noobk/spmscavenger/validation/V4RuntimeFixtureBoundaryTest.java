package com.noobk.spmscavenger.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4RuntimeFixtureBoundaryTest {

    @Test
    void v4FixtureLivesOnlyInValidationAndTrackerOwnsNoAuthorityCalls() throws Exception {
        String production = readTree(Path.of("src/main/java"));
        String validation = readTree(Path.of("src/validation/java"));
        assertFalse(production.contains("V4RuntimeCampaignController"));
        assertFalse(production.contains("V4RuntimeWitnessTracker"));
        assertTrue(validation.contains("class V4RuntimeCampaignController"));
        assertTrue(validation.contains("class V4RuntimeWitnessTracker"));

        String tracker = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4RuntimeWitnessTracker.java"));
        for (String forbidden : new String[] {
                "openRequiredTrade(", "openOrResumeRequiredTrade(", "SettlementDestinationRanker.select(",
                "moveTo(", "setTarget(", "performResolvedTrade(", "startSleeping(",
                "designateHome(", "RouteExhaustionEvidence.publish("}) {
            assertFalse(tracker.contains(forbidden), "passive tracker authority leak: " + forbidden);
        }
    }

    @Test
    void commandAndResourceContractIsSingleSessionAndSequential() throws Exception {
        String commands = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4RuntimeWitnessCommands.java"));
        for (String literal : new String[] {"v4", "run", "status", "report", "stop", "reset"}) {
            assertTrue(commands.contains("literal(\"" + literal + "\")"));
        }
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V4RuntimeCampaignController.java"));
        assertTrue(controller.indexOf("PHASE_A") < controller.indexOf("PHASE_B"));
        assertTrue(controller.contains("homeBeforeTrade"));
        assertTrue(controller.contains("FIXTURE_FAILURE"));
        assertTrue(controller.contains("INCOMPLETE"));
        assertTrue(controller.contains("FAIL"));
        assertTrue(controller.contains("PASS"));
        for (String forbidden : new String[] {
                "VillageInteractionDirector.openOrResumeRequiredTrade(",
                "SettlementDestinationRanker.select(", "performResolvedTrade(",
                "FirstHomePromotion.afterSuccessfulSleep(", "designateHome(", "startSleeping("}) {
            assertFalse(controller.contains(forbidden), "fixture authority leak: " + forbidden);
        }
        assertTrue(count(controller, "subject.teleportTo(") == 1,
                "only the declared pre-window departure placement may move the subject");
        assertTrue(controller.indexOf("subject.teleportTo(")
                < controller.indexOf("V4RuntimeWitnessTracker.openPhaseA(now)"));

        Path pack = Path.of("test-datapacks/v4-settlement-integration");
        assertTrue(Files.exists(pack.resolve("pack.mcmeta")));
        assertTrue(Files.exists(pack.resolve("README.md")));
        assertTrue(Files.exists(pack.resolve(
                "data/spm_v4/function/scenario/v4_g.mcfunction")));
        assertTrue(Files.exists(pack.resolve("data/spm_v4/function/cleanup.mcfunction")));
    }

    @Test
    void validationMixinsObserveActualProductionSeamsAndArePackagedOnlyBySidecar() throws Exception {
        String config = Files.readString(Path.of(
                "src/validation/resources/spmscavenger.validation.mixins.json"));
        for (String observer : new String[] {
                "V4KnownTraderObservationMixin", "V4VillageInteractionDirectorMixin",
                "V4ExploringGoalWitnessMixin", "V4VillagerTradeAdapterMixin",
                "V4FirstHomePromotionMixin"}) {
            assertTrue(config.contains(observer));
        }
        String metadata = Files.readString(Path.of("src/validation/resources/fabric.mod.json"));
        assertTrue(metadata.contains("spmscavenger.validation.mixins.json"));
        assertFalse(Files.readString(Path.of("src/main/resources/fabric.mod.json"))
                .contains("spmscavenger.validation.mixins.json"));

        String mixins = readTree(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin"));
        assertTrue(mixins.contains("KnownTraderMarketObservation"));
        assertTrue(mixins.contains("openOrResumeRequiredTrade"));
        assertTrue(mixins.contains("seedRequiredTradeCommuteExpedition"));
        assertTrue(mixins.contains("performResolvedTrade"));
        assertTrue(mixins.contains("afterSuccessfulSleep"));
    }

    @Test
    void exploringGoalStopWitnessNamesReadableAndIntermediaryTargetsAndRemainsRequired()
            throws Exception {
        String mixin = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4ExploringGoalWitnessMixin.java"));
        assertTrue(mixin.contains("method = {\"stop\", \"method_6270\"}"),
                "ExploringGoal overrides Goal.stop, which is method_6270 in the remapped "
                        + "production JAR; both namespaces are required for the validation sidecar");

        String config = Files.readString(Path.of(
                "src/validation/resources/spmscavenger.validation.mixins.json"));
        assertTrue(config.contains("\"required\": true"));
        assertTrue(config.contains("\"defaultRequire\": 1"),
                "the runtime witness must fail closed if neither namespace target attaches");
        assertFalse(mixin.contains("require = 0"),
                "namespace compatibility must not turn the witness into an optional no-op");
    }

    private static int count(String source, String token) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(token, at)) >= 0; at += token.length()) {
            count++;
        }
        return count;
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(path));
            }
        }
        return source.toString();
    }
}
