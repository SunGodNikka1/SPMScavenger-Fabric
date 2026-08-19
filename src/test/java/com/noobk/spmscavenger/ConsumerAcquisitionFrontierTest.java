package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * <b>V2-DEF-003</b> — required-resource truth must mean <i>what the currently reachable consumer
 * route is physically missing</i>, not <i>what might generically be useful while some tool upgrade
 * remains</i>.
 *
 * <h2>The runtime stall</h2>
 *
 * <pre>
 * demand     iron_ingot x3      projection  iron_pickaxe
 * B_FUNDING  10                 free slots  1               plans 0
 * Opinion    MANDATORY_AUTHORITY, discretionaryBlocker = GatherResourcesGoal -> SCAVENGE_WORK
 * </pre>
 *
 * The mob carried 3 sticks for a 2-stick recipe and 320 logs, so LOGS stayed in the required set via
 * {@code wantsPickUpgrade}. {@code GatherResourcesGoal} therefore held mandatory ownership over a
 * resource nothing consumed, and since {@code GatherRoutePrecursor} rightly refuses to read "wanted
 * iron, found log" as iron exhaustion, RAW_IRON exhaustion evidence could never be published and
 * V2-C could never hand the route to trade. The mob stalled with ten funded trades available.
 *
 * <h2>The contradiction it came from</h2>
 *
 * {@code ScavengerCrafting.towardConsumerTool} already asked the right question — planks and logs
 * matter only while the recipe's stick requirement is short. {@code GatherIntentPolicy} asked a
 * broader one. Two readings of the same recipe, disagreeing.
 */
class ConsumerAcquisitionFrontierTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ScavengerConfig ironTarget() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.NONE;
        cfg.torchStockTarget = 0;
        return cfg;
    }

    /** The step-7B fixture, exactly: stone pick, iron axe, 3 sticks, 320 logs, no iron. */
    @Test
    void mustHappen_theIronFrontierAsksForRawIronAndNothingElse() {
        ScavengerConfig cfg = ironTarget();
        SimpleContainer pack = new SimpleContainer(8);
        for (int slot = 0; slot < 5; slot++) {
            pack.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        }
        pack.setItem(5, new ItemStack(Items.STICK, 3));

        GatherIntentPolicy.GatherIntent intent = GatherIntentPolicy.evaluate(
                pack, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_AXE), cfg, 64);

        assertTrue(intent.requiredResources().contains(GatherIntentPolicy.Resource.RAW_IRON),
                "the iron pickaxe is short of iron, and nothing else");
        assertFalse(intent.requiredResources().contains(GatherIntentPolicy.Resource.LOGS),
                "3 sticks already satisfy a 2-stick recipe - 320 more logs serve no consumer");
        assertFalse(intent.requiredResources().contains(GatherIntentPolicy.Resource.COBBLESTONE),
                "the stone prerequisite was passed; a stock target is wealth, not permission");
    }

    /**
     * The rule itself, count-explicit.
     *
     * <p>Plank and log counts come from item <b>tags</b>, which {@code Bootstrap.bootStrap()} does
     * not populate — a container holding 16 oak planks reads as zero here. So the branch that
     * matters most ("crafting owns the precursor, do not gather") is unreachable through a fixture
     * and is proved on the pure helper instead, rather than left to a test that would pass for the
     * wrong reason.
     */
    @Test
    void mustNotHappen_logsAreRequiredWhileTheCraftChainCanStillMakeSticks() {
        assertFalse(ScavengerCrafting.sticksNeedLogs(2, 0, 16, 0),
                "16 planks: PLANKS_TO_STICKS is the route, so logs are not an acquisition");
        assertFalse(ScavengerCrafting.sticksNeedLogs(2, 0, 0, 64),
                "64 logs already held: LOGS_TO_PLANKS is the route, so gathering more is not");
        assertFalse(ScavengerCrafting.sticksNeedLogs(2, 3, 0, 0),
                "3 sticks for a 2-stick recipe: nothing is short at all");
        assertTrue(ScavengerCrafting.sticksNeedLogs(2, 0, 0, 0),
                "nothing to make sticks from - now logs genuinely must be acquired");
        assertTrue(ScavengerCrafting.sticksNeedLogs(2, 1, 1, 0),
                "one plank cannot make a stick craft, so the precursor is still missing");
    }

    /** Sticks short and nothing to make them from: now logs genuinely are the acquisition. */
    @Test
    void mustHappen_logsAreRequiredWhenNothingCanMakeSticks() {
        ScavengerConfig cfg = ironTarget();
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.IRON_INGOT, 3));

        assertTrue(GatherIntentPolicy
                        .evaluate(pack, new ItemStack(Items.STONE_PICKAXE),
                                new ItemStack(Items.IRON_AXE), cfg, 64)
                        .requiredResources().contains(GatherIntentPolicy.Resource.LOGS),
                "no sticks, no planks, no logs - the stick precursor must be acquired");
    }

    /** The stone step is the one that consumes cobble, and only while it is the active step. */
    @Test
    void mustHappen_cobbleIsRequiredWhileTheStoneStepIsActive() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.STONE;
        cfg.maxAxeTier = ToolTier.NONE;
        cfg.torchStockTarget = 0;
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.STICK, 8));

        assertTrue(GatherIntentPolicy
                        .evaluate(pack, new ItemStack(Items.WOODEN_PICKAXE), ItemStack.EMPTY, cfg, 64)
                        .requiredResources().contains(GatherIntentPolicy.Resource.COBBLESTONE),
                "wooden pick, sticks in hand, cobble short - cobble is the missing input");
    }

    /**
     * And once past it, a generic stock target must not carry mandatory authority.
     *
     * <p>This is the NEED/WEALTH boundary. Wanting more cobble than the consumer needs is an
     * appetite; letting it into {@code requiredResources} turns an appetite into permission.
     */
    @Test
    void mustNotHappen_aStockTargetKeepsCobbleMandatoryPastTheStoneStep() {
        ScavengerConfig cfg = ironTarget();
        cfg.cobbleStockTarget = 64;
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.STICK, 8));
        pack.setItem(1, new ItemStack(Items.OAK_LOG, 64));

        assertFalse(GatherIntentPolicy
                        .evaluate(pack, new ItemStack(Items.STONE_PICKAXE),
                                new ItemStack(Items.IRON_AXE), cfg, 64)
                        .requiredResources().contains(GatherIntentPolicy.Resource.COBBLESTONE),
                "already stone, pursuing iron - no current consumer eats cobblestone");
    }

    /**
     * Capacity is not an acquisition problem.
     *
     * <p>Every ingredient is held and the craft is blocked only by a full backpack. Inventing a
     * gather requirement here would send the mob out for more of what it already cannot carry —
     * the step-7A stall in a different costume.
     */
    @Test
    void mustNotHappen_aFullBackpackInventsAGatherRequirement() {
        ScavengerConfig cfg = ironTarget();
        SimpleContainer pack = new SimpleContainer(3);
        pack.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        pack.setItem(1, new ItemStack(Items.STICK, 8));
        pack.setItem(2, new ItemStack(Items.OAK_LOG, 64));

        GatherIntentPolicy.GatherIntent intent = GatherIntentPolicy.evaluate(
                pack, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_AXE), cfg, 64);

        assertFalse(intent.requiredResources().contains(GatherIntentPolicy.Resource.RAW_IRON));
        assertFalse(intent.requiredResources().contains(GatherIntentPolicy.Resource.LOGS));
        assertFalse(intent.requiredResources().contains(GatherIntentPolicy.Resource.COBBLESTONE));
    }

    /**
     * Convergence: an unrelated log nearby must not be able to claim it satisfies the iron route.
     *
     * <p>This is what makes the stall self-perpetuating rather than merely wasteful.
     * {@code GatherRoutePrecursor} correctly refuses "wanted iron, found log" as iron exhaustion —
     * so as long as an irrelevant log stays in the required set, the scan keeps succeeding at
     * something nobody asked for and RAW_IRON exhaustion is never published. The fix is upstream of
     * the precursor: the log should never have been required.
     *
     * <p>No {@code RouteExhaustionEvidence} is manufactured here. Gather remains the only owner
     * permitted to publish its own search result; this asserts only what the frontier asks for.
     */
    @Test
    void mustNotHappen_anIrrelevantLogKeepsTheIronRouteAlive() {
        ScavengerConfig cfg = ironTarget();
        SimpleContainer pack = new SimpleContainer(8);
        pack.setItem(0, new ItemStack(Items.STICK, 3));
        pack.setItem(1, new ItemStack(Items.OAK_LOG, 320 - 256));

        java.util.Set<GatherIntentPolicy.Resource> required = GatherIntentPolicy.evaluate(
                        pack, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_AXE),
                        cfg, 64)
                .requiredResources();

        assertTrue(required.contains(GatherIntentPolicy.Resource.RAW_IRON));
        assertFalse(required.contains(GatherIntentPolicy.Resource.LOGS),
                "with logs out of the required set, a log scan can no longer stand in for the iron "
                        + "search, and the iron search can reach its own conclusion");
    }
}
