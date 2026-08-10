package com.noobk.spmscavenger;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import com.noobk.spmscavenger.goal.AnticsGoal;
import com.noobk.spmscavenger.goal.CampfireGoal;
import com.noobk.spmscavenger.goal.ControlledDescentGoal;
import com.noobk.spmscavenger.goal.TunnelSearchGoal;
import com.noobk.spmscavenger.goal.CraftTorchesGoal;
import com.noobk.spmscavenger.goal.GatherResourcesGoal;
import com.noobk.spmscavenger.goal.ExplorationActivityGoal;
import com.noobk.spmscavenger.goal.ExplorationReadiness;
import com.noobk.spmscavenger.goal.ExploringGoal;
import com.noobk.spmscavenger.goal.EnvironmentalEscapeGoal;
import com.noobk.spmscavenger.goal.PlaceTorchGoal;
import com.noobk.spmscavenger.goal.SeekShelterGoal;
import com.noobk.spmscavenger.goal.SmeltAtFurnaceGoal;
import com.noobk.spmscavenger.goal.TrackedLocalWanderGoal;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds scavenging behaviour to Social Player Mobs.
 *
 * <h2>How the goals get attached</h2>
 *
 * Social Player Mobs' own wiki states it plainly: <i>"{@code registerGoals()} is not an extension
 * point. It's a {@code protected} override with no registry or event hook."</i> So goals are added
 * from outside, on {@link ServerEntityEvents#ENTITY_LOAD}, reaching the selector through one
 * accessor mixin on <b>vanilla</b> {@code Mob}.
 *
 * <p>{@code ENTITY_LOAD} is also the <b>latest</b> available hook, which matters: it fires after
 * every mod has finished wiring that mob, so this addon sees the final goal set rather than racing
 * whatever else is decorating PlayerMobs.
 *
 * <h2>Priorities</h2>
 *
 * Chosen to sit <em>below</em> everything Social Player Mobs considers urgent and alongside its own
 * housekeeping. SPM uses 0 for float/fire, 1 for social and commanded actions, 2 for combat, and 3
 * for eating and looting. Scavenging is not more important than any of those:
 *
 * <ul>
 *   <li><b>2 — shelter.</b> It has to be <b>strictly below 3</b> to work at all.
 *       {@code WrappedGoal#canBeReplacedBy} only lets a goal take flags from a running one with a
 *       <em>strictly lower</em> priority number, and SPM runs {@code RaidContainersGoal},
 *       {@code RaidArmorStandsGoal} and {@code CollectFloorItemsGoal} at 3 — constantly, since
 *       scavenging is its core loop. At 4 (v1.0–1.1) shelter could never interrupt any of them, so
 *       it only ran when nothing else wanted the mob, which at night near loot is almost never.
 *       Sharing 2 with combat is safe: this goal cancels itself the moment a target exists, and
 *       priority-1 fleeing still preempts it.</li>
 *   <li><b>3 — crafting, gathering, and smelting.</b> <b>Level with</b> SPM's own chores
 *       ({@code RaidContainersGoal}, {@code RaidArmorStandsGoal}, {@code CollectFloorItemsGoal},
 *       {@code EatFoodGoal}), not below them. At 5 and 6 (v1.0–1.3) they lost every contest to
 *       looting and effectively never ran — the readout under a mob's name never once said
 *       "Gather resources". Equal priority cannot preempt, so this does not starve looting either;
 *       the two families interleave as each finishes. Smelting shares 3 so charcoal jobs are not
 *       stranded below gather when a plan is ready.</li>
 *   <li><b>4 — torches.</b> Below the chores that feed it, above idle wandering.</li>
 * </ul>
 *
 * <h2>Failure mode</h2>
 *
 * With Social Player Mobs absent this mod attaches nothing and logs that it did nothing. It never
 * touches a mob it did not recognise, so it cannot affect vanilla or other mods' entities.
 */
public class SpmScavenger implements ModInitializer {

    public static final String MOD_ID = "spmscavenger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean warnedStrollShape;

    @Override
    public void onInitialize() {
        ScavengerConfig cfg = ScavengerConfig.get();

        switch (PlayerMobs.state()) {
            case FOUND -> LOGGER.info(
                    "[spmscavenger] Social Player Mobs found — active "
                            + "(torches={}, shelter={}, gather={}, exploring={})",
                    cfg.placeTorches, cfg.seekShelter, cfg.gatherResources, cfg.exploring);
            case ABSENT -> LOGGER.info(
                    "[spmscavenger] Social Player Mobs not present — doing nothing.");
            case HIERARCHY_CHANGED -> LOGGER.warn(
                    "[spmscavenger] Social Player Mobs is installed but its entity is no longer a "
                            + "PathfinderMob; scavenging disabled. This mod likely needs an update.");
        }

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                OpinionExperienceRegistry.resume(mob.getUUID());
                install(mob);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                RestSessionCoordinator.invalidateOnUnload(
                        mob.getUUID(), world.getGameTime());
                OpinionExperienceRegistry.freeze(mob.getUUID());
            }
        });
        // Gate RET-1 - release per-world experience state when the server stops. Without this a
        // singleplayer session that opens world A, leaves, and opens world B keeps world A's
        // contexts reachable through a static map for the rest of the JVM's life. Deliberately not
        // clearAll(), which is test-only and also resets sink wiring.
        ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> {
                    OpinionExperienceRegistry.shutdownServerState();
                    FurnaceStations.shutdownServerState();
                    SeekShelterGoal.shutdownServerState();
                });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                OpinionExperienceRegistry.onDeath(mob.getUUID());
            }
        });
    }

    private static void install(Mob mob) {
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector == null || alreadyInstalled(selector)) {
            return;
        }
        if (mob instanceof PathfinderMob pathfinderMob) {
            // MOVE only: vanilla/SPM FloatGoal may still own JUMP. FireBucketGoal wins because the
            // escape goal immediately yields whenever the mob catches fire.
            selector.addGoal(0, new EnvironmentalEscapeGoal(pathfinderMob));
        }
        selector.addGoal(2, new SeekShelterGoal(mob, 1.0));
        selector.addGoal(4, new PlaceTorchGoal(mob, 1.0));
        selector.addGoal(7, new CampfireGoal(mob, 0.9));
        // Flagless, so it decorates whatever another goal is already driving.
        selector.addGoal(9, new AnticsGoal(mob));
        selector.addGoal(3, new CraftTorchesGoal(mob, 1.0));
        selector.addGoal(3, new GatherResourcesGoal(mob, 0.9));
        // Level with craft/gather so charcoal/iron jobs are not starved by idle stroll.
        selector.addGoal(3, new SmeltAtFurnaceGoal(mob, 1.0));
        installExploration(mob, selector);
    }

    private static void installExploration(Mob mob, GoalSelector selector) {
        if (!(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        ExplorationReadiness readiness = new ExplorationReadiness();

        // GAO-0-B1: persisted mining authority still needs lease settlement when the global switch
        // is already disabled at ENTITY_LOAD time. This flagless cleanup observer owns no executor,
        // replaces no host stroll, and is permanently barred from pressure/handoff/assignment.
        if (!cfg.enabled) {
            selector.addGoal(9, new ExplorationActivityGoal(
                    pathfinderMob, selector, readiness, false));
            return;
        }

        Goal originalStroll = null;
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (goal.getClass() == WaterAvoidingRandomStrollGoal.class) {
                originalStroll = goal;
                break;
            }
        }
        if (originalStroll == null) {
            // Preserve the compatibility fail-closed behavior while keeping persisted lease cleanup
            // alive. An unknown host stroll shape must not accidentally authorize mining work.
            selector.addGoal(9, new ExplorationActivityGoal(
                    pathfinderMob, selector, readiness, false));
            if (!warnedStrollShape) {
                warnedStrollShape = true;
                LOGGER.warn("[spmscavenger] PlayerMob idle stroll shape changed; exploration left "
                        + "disabled rather than replacing an unknown goal.");
            }
            return;
        }

        selector.removeGoal(originalStroll);
        selector.addGoal(3, new ControlledDescentGoal(pathfinderMob, 0.9, readiness));
        // Same priority as the other deliberate-excavation executor: mode selection belongs to
        // MiningDirector, and arbitration to the intent matrix, not to Minecraft's priority
        // numbers. Two mining goals racing on priority would reintroduce exactly the scheduler
        // coupling the control plane exists to remove.
        selector.addGoal(3, new TunnelSearchGoal(pathfinderMob, 0.9));
        selector.addGoal(8, new ExploringGoal(pathfinderMob, readiness));
        selector.addGoal(9, new TrackedLocalWanderGoal(
                pathfinderMob, Math.max(0.5, Math.min(1.2, cfg.localWanderSpeed)), readiness));
        // Flagless observer; staggered internally and treats every unknown goal as meaningful work.
        selector.addGoal(9, new ExplorationActivityGoal(pathfinderMob, selector, readiness));
    }

    /**
     * Entities can be loaded more than once — chunk reload, dimension change — and each pass would
     * otherwise stack another copy of every goal onto the same mob.
     */
    private static boolean alreadyInstalled(GoalSelector selector) {
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof PlaceTorchGoal
                    || goal instanceof EnvironmentalEscapeGoal
                    || goal instanceof SeekShelterGoal
                    || goal instanceof GatherResourcesGoal
                    || goal instanceof CraftTorchesGoal
                    || goal instanceof SmeltAtFurnaceGoal
                    || goal instanceof CampfireGoal
                    || goal instanceof AnticsGoal
                    || goal instanceof ControlledDescentGoal
                    || goal instanceof ExploringGoal
                    || goal instanceof TrackedLocalWanderGoal
                    || goal instanceof ExplorationActivityGoal) {
                return true;
            }
        }
        return false;
    }
}
