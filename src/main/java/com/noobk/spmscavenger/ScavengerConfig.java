package com.noobk.spmscavenger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code config/spmscavenger.json}, backing the Mod Menu screen.
 *
 * <p>Every behaviour is separately switchable because they carry very different risk. Torches and
 * shelter only ever <em>add</em> to the world; gathering <em>breaks</em> blocks in it, and a server
 * owner may well want the first two and not the third.
 */
public final class ScavengerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ScavengerConfig instance;

    /**
     * Implemented craft-target caps exposed in the UI and accepted after normalization.
     * {@link ToolTier#DIAMOND} remains on the enum for loot ownership only.
     */
    // ---- Wealth (MI-4 / D-MIW-004) -----------------------------------------

    /**
     * How much a mob values holding more of a resource than its recipes require, {@code [0,1]}.
     *
     * <p><b>Default 0.0 is exact-consumer parity</b>: every wealth term evaluates to zero, so gather
     * behaviour is byte-for-byte what it was before wealth existed. Wealth only ever <em>adds</em>
     * desire on top of a consumer deficit; it never replaces or reduces one (D-MIW-015).
     */
    public double greed = 0.0;

    /**
     * Global wealth scaler. {@code 0} disables wealth entirely regardless of {@link #greed}, so
     * either switch alone restores exact-consumer behaviour.
     */
    public double wealthLevel = 0.0;

    /** Conservative fallback for absent or unparseable craft targets. */
    public static final ToolTier DEFAULT_CRAFT_TIER = ToolTier.STONE;

    public static final List<ToolTier> CRAFTABLE_TIER_CAPS = List.of(
            ToolTier.NONE, ToolTier.WOOD, ToolTier.STONE, ToolTier.IRON, ToolTier.DIAMOND);

    /** Master switch. Off leaves PlayerMobs exactly as Social Player Mobs ships them. */
    public boolean enabled = true;

    // ---- Torches ----------------------------------------------------------

    public boolean placeTorches = true;
    /** Place when block light here is below this. 7 is the vanilla hostile-spawn threshold. */
    public int torchLightLevel = 7;
    /** Minimum ticks between placements by one mob. Stops a mob carpeting a room. */
    public int torchCooldownTicks = 200;
    /** How far a mob will walk to place a torch. */
    public double torchSearchRadius = 6.0;

    // ---- Shelter ----------------------------------------------------------

    public boolean seekShelter = true;
    /** How far to look for a sheltered spot at night. */
    public double shelterSearchRadius = 16.0;
    /**
     * Whether a mob may claim an unoccupied bed and actually lie down in it.
     *
     * <p>Its own switch because it is the one shelter behaviour with a visible cost to the player:
     * the mob takes a bed you might want, and it renders lying down through Social Player Mobs'
     * custom player-shaped renderer, which has not been verified to draw the sleeping pose.
     */
    public boolean sleepInBeds = true;

    // ---- Gathering --------------------------------------------------------

    /**
     * Breaks blocks, so it is the one feature that can visibly alter a world. It also respects the
     * {@code mobGriefing} game rule independently of this switch.
     */
    public boolean gatherResources = true;
    public double gatherSearchRadius = 20.0;
    /**
     * When on, a log must look like a tree <em>and</em> stand clear of anything built: rooted on
     * growing ground, at least {@link GatherProtection#MIN_TRUNK_HEIGHT} tall, crowned with leaves
     * near its top, not part of a horizontal run, and with no man-made block within
     * {@link GatherProtection#STRUCTURE_RADIUS}. Coal ore must be exposed to air and equally clear.
     *
     * <p>Off restores the v1.4 behaviour — any log in radius is fair game. <b>Leave this on.</b>
     */
    public boolean protectPlayerBuilds = true;
    /**
     * After a genuine navigation stall, allow the gather goal to remove only a directly blocking
     * leaf near its approved tree (maximum three per approach). Disable for absolute no-leaf-damage
     * policy; {@code mobGriefing} and build protection still apply independently.
     */
    public boolean clearLeafObstructions = true;
    /**
     * Whether mobs craft a workbench and implemented wooden/stone/iron tools.
     *
     * <p>Its own switch because it is the one behaviour that leaves a <em>block</em> standing in the
     * world on purpose rather than as a side effect. Off, the mob still crafts torches, but it can
     * only reach coal if it finds a pickaxe lying around.
     */
    public boolean craftTools = true;
    /** Highest pick tier mobs will craft toward when {@link #craftTools} is on. */
    public ToolTier maxPickTier = ToolTier.STONE;
    /** Highest axe tier mobs will craft toward when {@link #craftTools} is on. */
    public ToolTier maxAxeTier = ToolTier.STONE;
    /**
     * Cobble to stock while stone-tier tools are still wanted. Six is exactly one stone pick plus
     * one stone axe with no hoard buffer.
     */
    public int cobbleStockTarget = 6;

    // ---- Antics and camp ---------------------------------------------------

    /** Mirror a nearby player's crouch. Purely cosmetic; changes no world state. */
    public boolean mimicry = true;
    /**
     * Hop while chasing a target.
     *
     * <p>Every hop is collision-checked first, so mobs run normally under leaves, in corridors and
     * in doorways rather than head-bumping. Cosmetic; changes no world state.
     */
    public boolean bunnyHop = true;
    /**
     * Craft a campfire once already stocked with torches and tools, place it, and idle around it.
     *
     * <p>Places a block, so it also requires the {@code mobGriefing} game rule.
     */
    public boolean campfire = true;
    /**
     * How far to look for an existing {@code minecraft:crafting_table} before crafting or placing a
     * new one. Mobs always walk to the nearest table in range; they only place when none is found.
     */
    public double craftingTableSearchRadius = 24.0;
    /**
     * Whether a mob may craft and place a new table when none is in range. Off means tool crafting
     * only happens at tables already in the world (villages, player bases, tables left by other
     * mobs).
     */
    public boolean placeCraftingTables = true;
    /** Stop gathering wood once the backpack holds this many torches. */
    public int torchStockTarget = 8;

    // ---- Furnace smelting (FS-2) -------------------------------------------

    /** Master switch for charcoal/iron smelting goals and furnace placement. */
    public boolean smeltEnabled = true;
    /** How far to look for a usable furnace before crafting or placing a new one. */
    public double furnaceSearchRadius = 24.0;
    /**
     * Whether a mob may craft and place a new furnace when none usable is in range.
     * Placement still requires {@code mobGriefing}.
     */
    public boolean placeFurnaces = true;
    /**
     * When on, mobs may claim an <em>empty</em> unowned furnace (village/player utility).
     * Default off — only Scavenger-placed or ticket-owned furnaces are used (D-FSM-002).
     */
    public boolean useCommunalFurnaces = false;

    // ---- Exploration ------------------------------------------------------

    /**
     * Replace SPM's slow vanilla idle stroll with tracked local wandering plus generic,
     * forward-biased expeditions. This never scans for resources or forces chunks.
     */
    public boolean exploring = true;
    /** Movement multiplier for the local-wandering fallback. */
    public double localWanderSpeed = 0.8;
    /** Movement multiplier while following an expedition route. */
    public double exploreSpeed = 0.95;
    /** Minimum intended horizontal displacement per expedition stage. */
    public double exploreMinStageDistance = 24.0;
    /** Maximum intended horizontal displacement per expedition stage. */
    public double exploreMaxStageDistance = 48.0;
    /** Successful meaningful local walks before exploration becomes eligible. */
    public int exploreLocalTripsThreshold = 2;
    /** Independent no-work timer; 600 ticks is 30 seconds. */
    public int exploreIdleTicks = 600;
    /**
     * Whether a mob setting out invites nearby PlayerMobs it feels positively about to walk the
     * same way. Off leaves every expedition solitary.
     */
    public boolean exploreCompanions = true;
    /** How far to look for someone to travel with, at the moment an expedition is planned. */
    public double exploreCompanionRadius = 10.0;
    /** Most companions one expedition may recruit, so a departure is company rather than a parade. */
    public int exploreCompanionMax = 2;

    // ---- Environmental escape ---------------------------------------------

    /** Recover from powder snow or a body-intersecting solid block. */
    public boolean environmentalEscape = true;
    /**
     * Ticks of movement-first recovery before block removal is considered, for powder snow.
     * True suffocation (`isInWall()`) uses zero grace, because navigation cannot solve it.
     */
    public int environmentalEscapeGraceTicks = 8;
    /** Local cube radius searched for a safe standing position. Clamped to 1-8. */
    public int environmentalEscapeSearchRadius = 6;
    /** Movement multiplier while escaping. Clamped to 0.5-1.5. */
    public double environmentalEscapeSpeed = 1.1;
    /** Whether the last-resort removal of the actual entrapping block is permitted at all. */
    public boolean environmentalEscapeBreakBlocks = true;
    /** Hardest block the escape may remove. 3.0 covers snow, dirt, stone and deepslate. */
    public double environmentalEscapeMaxHardness = 3.0;
    /** Blocks one escape incident may remove. */
    public int environmentalEscapeMaxBlocks = 3;
    /** Ticks between two removals inside one incident. */
    public int environmentalEscapeBreakIntervalTicks = 20;

    public static synchronized ScavengerConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ScavengerConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ScavengerConfig cfg = GSON.fromJson(reader, ScavengerConfig.class);
                if (cfg != null) {
                    if (cfg.normalizeCraftTargets()) {
                        SpmScavenger.LOGGER.warn(
                                "[spmscavenger] unsupported maxPickTier/maxAxeTier clamped to IRON "
                                        + "(implemented craftable caps are NONE, WOOD, STONE, IRON)");
                        cfg.save();
                    }
                    return cfg;
                }
            } catch (Exception e) {
                // A hand-edited config with a typo must not stop the mod loading.
                SpmScavenger.LOGGER.warn("[spmscavenger] could not read config, using defaults", e);
            }
        }
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.save();
        return cfg;
    }

    /**
     * Clamps unsupported or null craft-target caps to the highest implemented tier, IRON.
     *
     * @return {@code true} if either field changed
     */
    boolean normalizeCraftTargets() {
        boolean changed = false;
        ToolTier pick = sanitizeCraftTarget(maxPickTier);
        if (pick != maxPickTier) {
            maxPickTier = pick;
            changed = true;
        }
        ToolTier axe = sanitizeCraftTarget(maxAxeTier);
        if (axe != maxAxeTier) {
            maxAxeTier = axe;
            changed = true;
        }
        return changed;
    }

    /** Maps unsupported or null craft targets to {@link ToolTier#IRON}. */
    public static ToolTier sanitizeCraftTarget(ToolTier tier) {
        if (tier == null) {
            // Absent or unparseable. Fail *closed* to the conservative default rather than to the
            // highest craftable tier: corrupt config must never silently grant the most aggressive
            // setting (Gate SPM-0).
            return DEFAULT_CRAFT_TIER;
        }
        if (CRAFTABLE_TIER_CAPS.contains(tier)) {
            return tier;
        }
        // A real tier this build cannot craft: clamp down to the highest it can. Derived from the
        // caps rather than naming a tier, so the UI can never offer a cap that load-time
        // sanitisation silently removes (D-TTU-023).
        return CRAFTABLE_TIER_CAPS.stream()
                .max(java.util.Comparator.naturalOrder())
                .orElse(DEFAULT_CRAFT_TIER);
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(path())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            SpmScavenger.LOGGER.warn("[spmscavenger] could not write config", e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spmscavenger.json");
    }
}
