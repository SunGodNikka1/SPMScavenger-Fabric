package com.noobk.spmscavenger;

import com.noobk.spmscavenger.compat.SpmCombatChaseSpeed;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import com.noobk.spmscavenger.network.OpinionInspectNetworking;
import com.noobk.spmscavenger.goal.AnticsGoal;
import com.noobk.spmscavenger.goal.PassiveExpressionGoal;
import com.noobk.spmscavenger.goal.CampfireGoal;
import com.noobk.spmscavenger.goal.ControlledDescentGoal;
import com.noobk.spmscavenger.goal.TunnelSearchGoal;
import com.noobk.spmscavenger.goal.CraftTorchesGoal;
import com.noobk.spmscavenger.goal.GatherResourcesGoal;
import com.noobk.spmscavenger.goal.ExplorationActivityGoal;
import com.noobk.spmscavenger.goal.ExplorationReadiness;
import com.noobk.spmscavenger.goal.ExploringGoal;
import com.noobk.spmscavenger.goal.VillagePerceptionObserver;
import com.noobk.spmscavenger.village.VillagePerceptionScheduler;
import com.noobk.spmscavenger.goal.EnvironmentalEscapeGoal;
import com.noobk.spmscavenger.goal.PlaceTorchGoal;
import com.noobk.spmscavenger.goal.CompostGoal;
import com.noobk.spmscavenger.goal.PopulationFoodSupportGoal;
import com.noobk.spmscavenger.goal.VillageHarvestEpisodeGoal;
import com.noobk.spmscavenger.goal.SeekShelterGoal;
import com.noobk.spmscavenger.goal.SmeltAtFurnaceGoal;
import com.noobk.spmscavenger.goal.TrackedLocalWanderGoal;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.command.VillageProfileCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        OpinionInspectNetworking.registerServer();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        VillageProfileCommands.register(dispatcher));
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

        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    VillagePerceptionScheduler.forServer(server).onServerTick(server);
                    com.noobk.spmscavenger.village.storage.StorageGuardCompatibility.onServerTick();
                    com.noobk.spmscavenger.village.crop.HarvestCropGuardCompatibility.onServerTick();
                });
        // Optional market source. Guarded and reflective: nothing on the common trade path names a
        // Trade Everything class, so common classes load normally when the mod is absent.
        com.noobk.spmscavenger.compat.tradeeverything.TradeEverythingCompat.install();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
                com.noobk.spmscavenger.compat.tradeeverything.TradeEverythingCompat::prewarm);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                OpinionExperienceRegistry.resumeOnLoad(mob);
                install(mob);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                com.noobk.spmscavenger.debug.TeCurrencyWitnessTracker.abortForMob(
                        mob.getUUID(), "PlayerMob unloaded", world.getGameTime());
                cancelShelterCommitment(mob);
                SeekShelterGoal.onEntityUnload(mob.getUUID());
                RestSessionCoordinator.invalidateOnUnload(
                        mob.getUUID(), world.getGameTime());
                OpinionExperienceRegistry.parkOnUnload(mob.getUUID(), world.getGameTime());
                com.noobk.spmscavenger.opinion.SocialAdmissionSeam.release(mob.getUUID());
                com.noobk.spmscavenger.opinion.SocialGreetClaimWindow.release(mob.getUUID());
                com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry.release(mob.getUUID());
                com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow.release(mob.getUUID());
                com.noobk.spmscavenger.village.population.PopulationFoodEpisodeCooldown.release(mob.getUUID());
                com.noobk.spmscavenger.village.compost.CompostEpisodeCooldown.release(mob.getUUID());
                com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence.clear(mob.getUUID());
                // D-VR-084 / RET-1: the pending-claim store is runtime-only; unload (chunk or
                // dimension) releases the claim so no claim outlives its owner's presence. The
                // remembered terminal survives ordinary unload (the mob may return); permanent
                // removal below clears the whole slot.
                com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.release(
                        mob.getUUID(),
                        com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.ReleaseReason.ORDINARY);
                if (world.getServer() != null) {
                    VillagePerceptionScheduler.forServer(world.getServer())
                            .unregisterObserver(mob.getUUID());
                }
                // V1-R2: ENTITY_UNLOAD covers both "the chunk unloaded" and "the entity was
                // destroyed", so the reason - not the event - decides. shouldDestroy() is true only
                // for KILLED and DISCARDED; a chunk unload or dimension change keeps the memory.
                // Entity#setRemoved assigns removalReason before the callback chain this event sits
                // on, so it is populated here.
                net.minecraft.world.entity.Entity.RemovalReason reason = mob.getRemovalReason();
                if (reason != null && reason.shouldDestroy()) {
                    // D-VR-084 / RET-1: the mob is permanently gone — clear the whole slot,
                    // including the anti-self-renewal terminal, so no stale memory lingers.
                    com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.removePermanently(
                            mob.getUUID());
                    // V1-R3: every dimension, not just this one. The mob keeps its UUID across a
                    // dimension change, so memory written in the Overworld outlives a death in the
                    // Nether unless the sweep is global.
                    com.noobk.spmscavenger.PerMobSavedData.forgetAll(
                            world.getServer(), mob.getUUID());
                }
            }
        });
        // Gate RET-1 - release per-world experience state when the server stops. Without this a
        // singleplayer session that opens world A, leaves, and opens world B keeps world A's
        // contexts reachable through a static map for the rest of the JVM's life. Deliberately not
        // clearAll(), which is test-only and also resets sink wiring.
        // V1-R2 / Gate RET-1a - the safety valve only. Memory age is NOT an orphan signal: an alive
        // mob that spends a month away from villages must keep its home. Permanent removal is handled
        // by RemovalReason.shouldDestroy() above; this exists purely for mobs that vanish without any
        // lifecycle event, and it warns when it fires because that should not happen.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            com.noobk.spmscavenger.village.storage.StorageGuardCompatibility.beginServerSession();
            com.noobk.spmscavenger.village.crop.HarvestCropGuardCompatibility.beginServerSession();
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                int evicted = com.noobk.spmscavenger.village.VillageMemorySavedData.get(level)
                        .prune();
                if (evicted > 0) {
                    LOGGER.info("Pruned {} stale village memories in {}", evicted,
                            level.dimension().location());
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> {
                    com.noobk.spmscavenger.debug.TeCurrencyWitnessTracker.shutdownServerState(
                            server.getTickCount());
                    OpinionExperienceRegistry.shutdownServerState();
                    FurnaceStations.shutdownServerState();
                    SeekShelterGoal.shutdownServerState();
                    com.noobk.spmscavenger.opinion.SocialAdmissionSeam.shutdownServerState();
                    com.noobk.spmscavenger.opinion.SocialGreetClaimWindow.shutdownServerState();
                    com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry.shutdownServerState();
                    com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow.shutdownServerState();
                    com.noobk.spmscavenger.village.population.PopulationFoodEpisodeCooldown.shutdownServerState();
                    com.noobk.spmscavenger.village.compost.CompostEpisodeCooldown.shutdownServerState();
                    com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence.shutdownServerState();
                    com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.shutdownServerState();
                    com.noobk.spmscavenger.village.storage.StorageGuardCompatibility.shutdownServerState();
                    com.noobk.spmscavenger.village.crop.HarvestCropGuardCompatibility.shutdownServerState();
                    VillagePerceptionScheduler.shutdown(server);
                    com.noobk.spmscavenger.village.work.VillageWorkFactsService.shutdown(server);
                });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                com.noobk.spmscavenger.debug.TeCurrencyWitnessTracker.abortForMob(
                        mob.getUUID(), "PlayerMob died", mob.level().getGameTime());
                cancelShelterCommitment(mob);
                SeekShelterGoal.onDeath(mob.getUUID());
                OpinionExperienceRegistry.onDeath(mob.getUUID());
                com.noobk.spmscavenger.opinion.SocialAdmissionSeam.release(mob.getUUID());
                com.noobk.spmscavenger.opinion.SocialGreetClaimWindow.release(mob.getUUID());
                com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry.release(mob.getUUID());
                com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow.release(mob.getUUID());
                com.noobk.spmscavenger.village.population.PopulationFoodEpisodeCooldown.release(mob.getUUID());
                com.noobk.spmscavenger.village.compost.CompostEpisodeCooldown.release(mob.getUUID());
                com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence.clear(mob.getUUID());
                com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.release(
                        mob.getUUID(),
                        com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.ReleaseReason.ORDINARY);
                // D-VR-084 / RET-1: death is permanent — the whole slot (claim + terminal) is gone.
                com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry.removePermanently(
                        mob.getUUID());
                if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    VillagePerceptionScheduler.forServer(serverLevel.getServer())
                            .unregisterObserver(mob.getUUID());
                    // V1-R3 / RET-1e: one sweep covering every per-mob SavedData in the mod.
                    com.noobk.spmscavenger.PerMobSavedData.forgetAll(
                            serverLevel.getServer(), mob.getUUID());
                }
            }
        });
    }

    private static void install(Mob mob) {
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector == null) {
            return;
        }
        if (alreadyInstalled(selector)) {
            ensureVillagePerceptionObserver(mob, selector);
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        if (SpmScavengerInstallPolicy.installsLeaseCleanupObserver(cfg)) {
            installLeaseCleanupOnly(mob, selector);
            return;
        }
        if (SpmScavengerInstallPolicy.appliesCombatChaseSpeed(cfg)) {
            SpmCombatChaseSpeed.apply(mob, selector);
        }
        if (mob instanceof PathfinderMob pathfinderMob) {
            // MOVE only: vanilla/SPM FloatGoal may still own JUMP. FireBucketGoal wins because the
            // escape goal immediately yields whenever the mob catches fire.
            selector.addGoal(0, new EnvironmentalEscapeGoal(pathfinderMob));
        }
        installVillagePerceptionObserver(mob, selector);
        SeekShelterGoal shelterGoal = new SeekShelterGoal(mob, 1.0);
        selector.addGoal(2, shelterGoal);
        selector.addGoal(4, new PlaceTorchGoal(mob, 1.0));
        selector.addGoal(4, new VillageHarvestEpisodeGoal(mob, selector, 0.9));
        selector.addGoal(4, new PopulationFoodSupportGoal(mob, selector, 0.9));
        selector.addGoal(5, new CompostGoal(mob, selector, 0.9));
        CampfireGoal campfireGoal = new CampfireGoal(mob, 0.9);
        selector.addGoal(7, campfireGoal);
        selector.addGoal(PassiveExpressionGoal.PRIORITY, new PassiveExpressionGoal(mob));
        // Flagless, so it decorates whatever another goal is already driving.
        selector.addGoal(9, new AnticsGoal(mob));
        selector.addGoal(3, new CraftTorchesGoal(mob, 1.0));
        selector.addGoal(3, new GatherResourcesGoal(mob, 0.9));
        // Level with craft/gather so charcoal/iron jobs are not starved by idle stroll.
        selector.addGoal(3, new SmeltAtFurnaceGoal(mob, 1.0));
        // V2-E: the deliberate-work band. Combat (0-2), shelter (2) and commands preempt it;
        // FriendlyGreetGoal at priority 1 would too, which is what TradeSessionClaimWindow answers.
        selector.addGoal(3, new com.noobk.spmscavenger.goal.TradeWithVillagerGoal(mob, 1.0));
        installExploration(mob, selector, shelterGoal, campfireGoal);
    }

    private static void installLeaseCleanupOnly(Mob mob, GoalSelector selector) {
        if (!(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }
        installVillagePerceptionObserver(mob, selector);
        ExplorationReadiness readiness = new ExplorationReadiness();
        selector.addGoal(9, new ExplorationActivityGoal(
                pathfinderMob, selector, readiness, false, null, null));
    }

    private static void installExploration(
            Mob mob, GoalSelector selector, SeekShelterGoal shelterGoal, @org.jetbrains.annotations.Nullable CampfireGoal campfireGoal) {
        if (!(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        ExplorationReadiness readiness = new ExplorationReadiness();

        if (!SpmScavengerInstallPolicy.installsMiningExecutors(cfg)) {
            selector.addGoal(9, new ExplorationActivityGoal(
                    pathfinderMob, selector, readiness, false, shelterGoal, campfireGoal));
            return;
        }

        if (SpmScavengerInstallPolicy.replacesHostStroll(cfg)) {
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
                        pathfinderMob, selector, readiness, false, shelterGoal, campfireGoal));
                if (!warnedStrollShape) {
                    warnedStrollShape = true;
                    LOGGER.warn("[spmscavenger] PlayerMob idle stroll shape changed; exploration left "
                            + "disabled rather than replacing an unknown goal.");
                }
                return;
            }
            selector.removeGoal(originalStroll);
        }

        selector.addGoal(3, new ControlledDescentGoal(pathfinderMob, 0.9, readiness));
        // Same priority as the other deliberate-excavation executor: mode selection belongs to
        // MiningDirector, and arbitration to the intent matrix, not to Minecraft's priority
        // numbers. Two mining goals racing on priority would reintroduce exactly the scheduler
        // coupling the control plane exists to remove.
        selector.addGoal(3, new TunnelSearchGoal(pathfinderMob, 0.9));
        if (SpmScavengerInstallPolicy.installsOverlandExploration(cfg)) {
            selector.addGoal(8, new ExploringGoal(pathfinderMob, readiness));
            selector.addGoal(9, new TrackedLocalWanderGoal(
                    pathfinderMob, Math.max(0.5, Math.min(1.2, cfg.localWanderSpeed)), readiness));
        }
        // Flagless observer; staggered internally and treats every unknown goal as meaningful work.
        selector.addGoal(9, new ExplorationActivityGoal(
                pathfinderMob, selector, readiness, true, shelterGoal, campfireGoal));
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
                    || goal instanceof PassiveExpressionGoal
                    || goal instanceof ControlledDescentGoal
                    || goal instanceof ExploringGoal
                    || goal instanceof TrackedLocalWanderGoal
                    || goal instanceof ExplorationActivityGoal
                    || goal instanceof VillagePerceptionObserver) {
                return true;
            }
        }
        return false;
    }

    private static void installVillagePerceptionObserver(Mob mob, GoalSelector selector) {
        ensureVillagePerceptionObserver(mob, selector);
    }

    /**
     * Idempotent V1-D install + scheduler registration. Called on every {@code ENTITY_LOAD} even when
     * other Scavenger goals are already present, so {@code unregisterObserver} on unload cannot
     * leave a mob with a surviving goal but no scheduler registration.
     */
    static void ensureVillagePerceptionObserver(Mob mob, GoalSelector selector) {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled) {
            return;
        }
        boolean hasObserver = false;
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VillagePerceptionObserver) {
                hasObserver = true;
                break;
            }
        }
        if (!hasObserver) {
            selector.addGoal(VillagePerceptionObserver.PRIORITY, new VillagePerceptionObserver(mob));
        }
        if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getServer() != null) {
            VillagePerceptionScheduler.forServer(serverLevel.getServer())
                    .registerObserver(mob.getUUID());
        }
    }

    /** Cancel the per-entity commitment before the static claim fallback is swept. */
    private static void cancelShelterCommitment(Mob mob) {
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector == null) {
            return;
        }
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SeekShelterGoal shelterGoal) {
                shelterGoal.cancelForOwnerRemoval();
                return;
            }
        }
    }
}
