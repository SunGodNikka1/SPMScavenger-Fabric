package com.noobk.spmscavenger.client;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ToolTier;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu settings. Client-only — nothing on the common path references this class, so a dedicated
 * server runs without Cloth Config or Mod Menu installed.
 */
public final class ScavengerConfigScreen {

    private ScavengerConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ScavengerConfig cfg = ScavengerConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Social Player Mobs: Scavenger"))
                .setSavingRunnable(cfg::save);

        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        // Cycle selector (not Cloth dropdown): DropdownBoxEntry expand/collapse is focus-coupled and
        // can stick open until the screen is recreated. Selector still limits values to Phase-1 caps.
        ToolTier[] craftableCaps = ScavengerConfig.CRAFTABLE_TIER_CAPS.toArray(ToolTier[]::new);

        general.addEntry(eb.startBooleanToggle(Component.literal("Enable scavenging"), cfg.enabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Off leaves PlayerMobs exactly as Social Player Mobs"),
                        Component.literal("ships them."))
                .setSaveConsumer(v -> cfg.enabled = v)
                .build());

        // ---- Opinion ----
        general.addEntry(eb.startTextDescription(Component.literal("Opinion")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(
                        Component.literal("Adaptive opinion and mood"), cfg.opinionEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Off preserves legacy explore/rest/campfire"),
                        Component.literal("behaviour — the same as before Opinion shipped."),
                        Component.literal(" "),
                        Component.literal("On lets mobs learn activity preferences, track")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("short-term mood, and choose discretionary")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("explore vs rest from experience.")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal(" "),
                        Component.literal("Mandatory work, combat, cave handoffs, and")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("commands still take priority.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.opinionEnabled = v)
                .build());

        // ---- Torches ----
        general.addEntry(eb.startTextDescription(Component.literal("Torches")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Place torches when dark"), cfg.placeTorches)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Torches come out of the mob's backpack —"),
                        Component.literal("they are not conjured from air."))
                .setSaveConsumer(v -> cfg.placeTorches = v)
                .build());

        general.addEntry(eb.startIntSlider(Component.literal("Light level to light up"), cfg.torchLightLevel, 0, 15)
                .setDefaultValue(7)
                .setTooltip(Component.literal("Places a torch where block light is below this."),
                        Component.literal("7 is the level below which hostiles spawn, so the")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("mob stops as soon as the spot is safe.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.torchLightLevel = v)
                .build());

        general.addEntry(eb.startIntField(Component.literal("Ticks between torches"), cfg.torchCooldownTicks)
                .setDefaultValue(200)
                .setMin(20).setMax(6000)
                .setTooltip(Component.literal("Stops one mob carpeting a room. 200 = 10 seconds."))
                .setSaveConsumer(v -> cfg.torchCooldownTicks = v)
                .build());

        // ---- Shelter ----
        general.addEntry(eb.startTextDescription(Component.literal("Shelter")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Take shelter at night"), cfg.seekShelter)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Ranks nearby spots: a bed beats an enclosed room,"),
                        Component.literal("which beats a lit spot, which beats a bare overhang."),
                        Component.literal("Never builds or digs; if nothing scores well enough")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("the mob simply carries on as normal.").withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.seekShelter = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Sleep in beds"), cfg.sleepInBeds)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Claim an unoccupied bed and lie down in it."),
                        Component.literal("A bed outranks every other kind of shelter."),
                        Component.literal(" "),
                        Component.literal("Sleeping mobs do NOT skip the night — only real")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("players count for that. Beds are released at dawn.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.sleepInBeds = v)
                .build());

        // ---- Gathering ----
        general.addEntry(eb.startTextDescription(Component.literal("Gathering")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Gather wood and coal"), cfg.gatherResources)
                .setDefaultValue(true)
                .setTooltip(Component.literal("The only feature that BREAKS blocks."),
                        Component.literal("Also requires the mobGriefing game rule.")
                                .withStyle(ChatFormatting.YELLOW),
                        Component.literal("Keeps logs/coal/charcoal for crafting; other drops")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("fall for Social Player Mobs to pick up.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.gatherResources = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Craft tools at tables"), cfg.craftTools)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Craft pickaxes and axes at a crafting table."),
                        Component.literal("Off: torches only, and coal only if a pickaxe is looted."))
                .setSaveConsumer(v -> cfg.craftTools = v)
                .build());

        general.addEntry(eb.startSelector(
                        Component.literal("Max pick tier"),
                        craftableCaps,
                        ScavengerConfig.sanitizeCraftTarget(cfg.maxPickTier))
                .setDefaultValue(ToolTier.DIAMOND)
                .setNameProvider(tier -> Component.literal(tier.name()))
                .setTooltip(Component.literal("Highest pick tier to craft toward."),
                        Component.literal("Caps: NONE, WOOD, STONE, IRON, DIAMOND."),
                        Component.literal("Only applies when tool crafting is enabled."),
                        Component.literal("Click to cycle.").withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.maxPickTier = ScavengerConfig.sanitizeCraftTarget(v))
                .build());

        general.addEntry(eb.startSelector(
                        Component.literal("Max axe tier"),
                        craftableCaps,
                        ScavengerConfig.sanitizeCraftTarget(cfg.maxAxeTier))
                .setDefaultValue(ToolTier.DIAMOND)
                .setNameProvider(tier -> Component.literal(tier.name()))
                .setTooltip(Component.literal("Highest axe tier to craft toward."),
                        Component.literal("Caps: NONE, WOOD, STONE, IRON, DIAMOND."),
                        Component.literal("Only applies when tool crafting is enabled."),
                        Component.literal("Click to cycle.").withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.maxAxeTier = ScavengerConfig.sanitizeCraftTarget(v))
                .build());

        general.addEntry(eb.startIntSlider(Component.literal("Cobble stock target"), cfg.cobbleStockTarget, 0, 64)
                .setDefaultValue(6)
                .setTooltip(Component.literal("Cobble to gather while upgrading to stone tools."),
                        Component.literal("Six covers one stone pick and one stone axe."))
                .setSaveConsumer(v -> cfg.cobbleStockTarget = v)
                .build());

        general.addEntry(eb.startDoubleField(Component.literal("Crafting table search radius"), cfg.craftingTableSearchRadius)
                .setDefaultValue(24.0)
                .setMin(4.0).setMax(64.0)
                .setTooltip(Component.literal("Walk to the nearest existing table within this range"),
                        Component.literal("before crafting or placing a new one."))
                .setSaveConsumer(v -> cfg.craftingTableSearchRadius = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Place new crafting tables"), cfg.placeCraftingTables)
                .setDefaultValue(true)
                .setTooltip(Component.literal("When off, tool crafting only works at tables"),
                        Component.literal("already in the world — no new benches placed."))
                .setSaveConsumer(v -> cfg.placeCraftingTables = v)
                .build());

        general.addEntry(eb.startTextDescription(Component.literal("Furnaces")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Smelt at furnaces"), cfg.smeltEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Charcoal and iron smelting at a real furnace."),
                        Component.literal("Off disables find/place and the smelt goal."))
                .setSaveConsumer(v -> cfg.smeltEnabled = v)
                .build());

        general.addEntry(eb.startDoubleField(Component.literal("Furnace search radius"), cfg.furnaceSearchRadius)
                .setDefaultValue(24.0)
                .setMin(4.0).setMax(64.0)
                .setTooltip(Component.literal("Walk to the nearest usable furnace within this range"),
                        Component.literal("before crafting or placing a new one."))
                .setSaveConsumer(v -> cfg.furnaceSearchRadius = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Place new furnaces"), cfg.placeFurnaces)
                .setDefaultValue(true)
                .setTooltip(Component.literal("When off, smelting only uses furnaces already"),
                        Component.literal("owned or (if enabled) empty communal ones."),
                        Component.literal("Places a block, so it also needs mobGriefing.")
                                .withStyle(ChatFormatting.YELLOW))
                .setSaveConsumer(v -> cfg.placeFurnaces = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                        Component.literal("Use empty communal furnaces"), cfg.useCommunalFurnaces)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Allow claiming an empty furnace the scavengers"),
                        Component.literal("did not place (village/player utility)."),
                        Component.literal("Never merges into a furnace that already has items.")
                                .withStyle(ChatFormatting.YELLOW),
                        Component.literal("Leave off unless you want shared stations.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.useCommunalFurnaces = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Protect player builds"), cfg.protectPlayerBuilds)
                .setDefaultValue(true)
                .setTooltip(Component.literal("A log is only chopped when it is rooted on soil,"),
                        Component.literal("3+ tall, crowned with leaves, not in a horizontal run,"),
                        Component.literal("and has nothing man-made within 3 blocks."),
                        Component.literal(" "),
                        Component.literal("A tree growing against your wall is skipped too —")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("the check errs towards refusing.")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal(" "),
                        Component.literal("Off: ANY log in range can be broken. Leave this on.")
                                .withStyle(ChatFormatting.RED))
                .setSaveConsumer(v -> cfg.protectPlayerBuilds = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                        Component.literal("Clear blocking tree leaves"), cfg.clearLeafObstructions)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Only after navigation makes no progress for 1 second."),
                        Component.literal("Removes one directly blocking leaf, then re-paths;"),
                        Component.literal("maximum 3 leaves per tree approach."),
                        Component.literal("Build protection and mobGriefing still apply."),
                        Component.literal("Disable to guarantee this addon never removes leaves.")
                                .withStyle(ChatFormatting.YELLOW))
                .setSaveConsumer(v -> cfg.clearLeafObstructions = v)
                .build());

        general.addEntry(eb.startIntField(Component.literal("Stop at this many torches"), cfg.torchStockTarget)
                .setDefaultValue(8)
                .setMin(1).setMax(64)
                .setTooltip(Component.literal("Gathering and crafting stop once the backpack holds"),
                        Component.literal("this many. Without a stop condition a scavenger"),
                        Component.literal("would strip a forest."))
                .setSaveConsumer(v -> cfg.torchStockTarget = v)
                .build());

        // ---- Antics ----
        general.addEntry(eb.startTextDescription(Component.literal("Antics")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Copy your crouch"), cfg.mimicry)
                .setDefaultValue(true)
                .setTooltip(Component.literal("A mob that can see you crouches when you do."),
                        Component.literal("Cosmetic; changes nothing in the world.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.mimicry = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Hop while chasing"), cfg.bunnyHop)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Bunny-hops while closing on a target."),
                        Component.literal("Every hop is collision-checked, so mobs run")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("normally under leaves and in corridors.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.bunnyHop = v)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Campfires"), cfg.campfire)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Once stocked with torches and tools, craft a"),
                        Component.literal("campfire, place it, and idle around it."),
                        Component.literal(" "),
                        Component.literal("Places a block, so it also needs mobGriefing.")
                                .withStyle(ChatFormatting.YELLOW))
                .setSaveConsumer(v -> cfg.campfire = v)
                .build());

        // ---- Exploration ----
        general.addEntry(eb.startTextDescription(Component.literal("Exploration")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Purposeful exploration"), cfg.exploring)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Replaces the vanilla idle stroll with tracked local"),
                        Component.literal("wandering plus occasional longer expeditions."),
                        Component.literal(" "),
                        Component.literal("Never scans for resources and never forces chunks.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.exploring = v)
                .build());

        general.addEntry(eb.startDoubleField(Component.literal("Local wander speed"), cfg.localWanderSpeed)
                .setDefaultValue(0.8)
                .setMin(0.5).setMax(1.2)
                .setSaveConsumer(v -> cfg.localWanderSpeed = v)
                .build());

        general.addEntry(eb.startDoubleField(Component.literal("Exploration speed"), cfg.exploreSpeed)
                .setDefaultValue(0.95)
                .setMin(0.5).setMax(1.3)
                .setSaveConsumer(v -> cfg.exploreSpeed = v)
                .build());

        general.addEntry(eb.startDoubleField(Component.literal("Fighting chase speed"), cfg.combatChaseSpeed)
                .setDefaultValue(1.35)
                .setMin(1.0).setMax(1.5)
                .setTooltip(Component.literal("SPM Fighting approach speed (melee and ranged"),
                        Component.literal("closing). Does not change in-range strafe or"),
                        Component.literal("shot cadence. Applies on mob load.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.combatChaseSpeed = v)
                .build());

        general.addEntry(eb.startDoubleField(
                        Component.literal("Minimum stage distance"), cfg.exploreMinStageDistance)
                .setDefaultValue(24.0)
                .setMin(16.0).setMax(64.0)
                .setTooltip(Component.literal("Horizontal intent distance per stage. Long stages are"),
                        Component.literal("walked as shorter hops, because vanilla pathfinding"),
                        Component.literal("cannot reach past a mob's follow range.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.exploreMinStageDistance = v)
                .build());

        general.addEntry(eb.startDoubleField(
                        Component.literal("Maximum stage distance"), cfg.exploreMaxStageDistance)
                .setDefaultValue(48.0)
                .setMin(16.0).setMax(80.0)
                .setSaveConsumer(v -> cfg.exploreMaxStageDistance = v)
                .build());

        general.addEntry(eb.startIntField(
                        Component.literal("Local trips before exploring"), cfg.exploreLocalTripsThreshold)
                .setDefaultValue(2)
                .setMin(1).setMax(10)
                .setTooltip(Component.literal("Only naturally completed, meaningful walks count."),
                        Component.literal("An interrupted stroll never counts.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.exploreLocalTripsThreshold = v)
                .build());

        general.addEntry(eb.startIntField(Component.literal("Idle ticks before exploring"), cfg.exploreIdleTicks)
                .setDefaultValue(600)
                .setMin(100).setMax(6000)
                .setTooltip(Component.literal("Independent fallback if local strolls cannot finish."),
                        Component.literal("600 ticks = 30 seconds.").withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.exploreIdleTicks = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                        Component.literal("Travel with companions"), cfg.exploreCompanions)
                .setDefaultValue(true)
                .setTooltip(Component.literal("A mob setting out invites nearby PlayerMobs it feels"),
                        Component.literal("positively about to walk the same way."),
                        Component.literal(" "),
                        Component.literal("In practice that means the ones it has greeted.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.exploreCompanions = v)
                .build());

        general.addEntry(eb.startDoubleField(
                        Component.literal("Companion search radius"), cfg.exploreCompanionRadius)
                .setDefaultValue(10.0)
                .setMin(4.0).setMax(24.0)
                .setSaveConsumer(v -> cfg.exploreCompanionRadius = v)
                .build());

        general.addEntry(eb.startIntField(
                        Component.literal("Companions per expedition"), cfg.exploreCompanionMax)
                .setDefaultValue(2)
                .setMin(0).setMax(4)
                .setTooltip(Component.literal("0 leaves every expedition solitary."))
                .setSaveConsumer(v -> cfg.exploreCompanionMax = v)
                .build());

        // ---- Environmental escape ----
        general.addEntry(eb.startTextDescription(Component.literal("Environmental escape")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startBooleanToggle(
                        Component.literal("Escape powder snow and walls"), cfg.environmentalEscape)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Recovers a mob sinking in powder snow or stuck"),
                        Component.literal("inside a solid block. Movement is always tried first."),
                        Component.literal(" "),
                        Component.literal("Fire is left to Social Player Mobs' own bucket goal.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.environmentalEscape = v)
                .build());

        general.addEntry(eb.startIntField(
                        Component.literal("Movement grace ticks"), cfg.environmentalEscapeGraceTicks)
                .setDefaultValue(8)
                .setMin(0).setMax(200)
                .setTooltip(Component.literal("How long to try walking out before breaking the block."),
                        Component.literal("True suffocation always uses zero grace, because")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("navigation cannot solve it.").withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.environmentalEscapeGraceTicks = v)
                .build());

        general.addEntry(eb.startIntField(
                        Component.literal("Escape search radius"), cfg.environmentalEscapeSearchRadius)
                .setDefaultValue(6)
                .setMin(1).setMax(8)
                .setSaveConsumer(v -> cfg.environmentalEscapeSearchRadius = v)
                .build());

        general.addEntry(eb.startDoubleField(
                        Component.literal("Escape speed"), cfg.environmentalEscapeSpeed)
                .setDefaultValue(1.1)
                .setMin(0.5).setMax(1.5)
                .setSaveConsumer(v -> cfg.environmentalEscapeSpeed = v)
                .build());

        general.addEntry(eb.startBooleanToggle(
                        Component.literal("Break the trapping block"), cfg.environmentalEscapeBreakBlocks)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Last resort, and only the block actually intersecting"),
                        Component.literal("the mob. Mined with its real tools, at vanilla speed."),
                        Component.literal(" "),
                        Component.literal("Breaks blocks, so it also needs mobGriefing.")
                                .withStyle(ChatFormatting.YELLOW),
                        Component.literal("Datapacks can extend or deny it through the")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("environmental_escape_breakable / _never_break tags.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.environmentalEscapeBreakBlocks = v)
                .build());

        general.addEntry(eb.startDoubleField(
                        Component.literal("Hardest block it may break"), cfg.environmentalEscapeMaxHardness)
                .setDefaultValue(3.0)
                .setMin(0.0).setMax(50.0)
                .setTooltip(Component.literal("3.0 covers snow, dirt, stone and deepslate."),
                        Component.literal("Obsidian and reinforced blocks stay out of reach.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.environmentalEscapeMaxHardness = v)
                .build());

        general.addEntry(eb.startIntField(
                        Component.literal("Blocks per incident"), cfg.environmentalEscapeMaxBlocks)
                .setDefaultValue(3)
                .setMin(0).setMax(16)
                .setSaveConsumer(v -> cfg.environmentalEscapeMaxBlocks = v)
                .build());

        general.addEntry(eb.startIntField(
                        Component.literal("Ticks between breaks"), cfg.environmentalEscapeBreakIntervalTicks)
                .setDefaultValue(20)
                .setMin(1).setMax(200)
                .setSaveConsumer(v -> cfg.environmentalEscapeBreakIntervalTicks = v)
                .build());

        // ---- Wealth ----
        general.addEntry(eb.startTextDescription(Component.literal("Wealth")
                .withStyle(ChatFormatting.GOLD)).build());

        general.addEntry(eb.startDoubleField(Component.literal("Greed"), cfg.greed)
                .setDefaultValue(0.0)
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal("How much a mob values holding more than its"),
                        Component.literal("recipes require."),
                        Component.literal(" "),
                        Component.literal("0 keeps mobs to exact crafting needs — the")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("behaviour before wealth existed.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.greed = v)
                .build());

        general.addEntry(eb.startDoubleField(Component.literal("Wealth level"), cfg.wealthLevel)
                .setDefaultValue(0.0)
                .setMin(0.0).setMax(4.0)
                .setTooltip(Component.literal("Global scaler for stockpiling desire."),
                        Component.literal("0 disables wealth entirely, whatever greed says.")
                                .withStyle(ChatFormatting.GRAY))
                .setSaveConsumer(v -> cfg.wealthLevel = v)
                .build());

        return builder.build();
    }
}
