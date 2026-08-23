package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.compat.tradeeverything.TradeEverythingCompat;
import com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Temporary W2 fixture that establishes facts for the production V2-TE witness.
 *
 * <p>The fixture never quotes, authorizes, selects, revalidates, executes, or publishes route
 * evidence. It may place four component-exact witness sticks, equip one stone pickaxe, and spawn
 * one tagged vanilla Toolsmith offer. Gather and Trade must still earn all production authority.
 *
 * <p><b>RET-1:</b> exactly one session, keyed by target UUID and container identity. The session
 * retains no level, mob, or villager reference. Stop/reset, unload/death, and server stop release
 * it; the exact tagged villager UUID is removed when the server is still available.
 */
public final class TeCurrencyWitnessFixture {

    public static final String FIXTURE_TAG = "spmscavenger:v2te_w2_fixture";
    private static final int MERCHANT_RADIUS = 16;
    private static Session session;

    private TeCurrencyWitnessFixture() {
    }

    public enum Phase { PREPARED, ARMED }

    public record Result(boolean success, List<String> lines) {}

    /** Prepare only. Production remains unarmed until the existing tracker accepts its preflight. */
    public static synchronized Result prepare(Mob mob, MinecraftServer server) {
        List<String> out = new ArrayList<>();
        out.add("=== V2-TE W2 Fixture Preparation ===");
        List<String> failures = validate(mob, server);
        if (!failures.isEmpty()) {
            failures.forEach(reason -> out.add("Refused: " + reason));
            out.add("Result: REFUSED BEFORE MUTATION");
            return new Result(false, List.copyOf(out));
        }

        ServerLevel level = (ServerLevel) mob.level();
        Container backpack = PlayerMobs.backpack(mob);
        Villager villager = null;
        try {
            ItemStack sticks = witnessSticks(server);
            ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
            backpack.setItem(0, sticks);
            mob.setItemSlot(EquipmentSlot.MAINHAND, pick);

            BlockPos merchantPos = findMerchantPosition(level, mob.blockPosition());
            villager = createMerchant(level, merchantPos);
            if (!level.addFreshEntity(villager)) {
                throw new IllegalStateException("server rejected fixture villager spawn");
            }
            session = new Session(mob.getUUID(), mob.getName().getString(), backpack,
                    sticks, pick, villager.getUUID(), level.dimension().location().toString(),
                    Phase.PREPARED);
            out.add("Target: " + mob.getName().getString() + " [" + mob.getUUID() + "]");
            out.add("Witness inventory: 4 Unbreaking VIII sticks; stone pickaxe equipped");
            out.add("Fixture merchant: " + villager.getUUID()
                    + " (10 emerald -> 1 iron pickaxe)");
            out.add("Production authority: NOT ARMED");
            out.add("Result: PREPARED");
            return new Result(true, List.copyOf(out));
        } catch (RuntimeException failure) {
            if (villager != null && villager.isAlive() && villager.getTags().contains(FIXTURE_TAG)) {
                villager.discard();
            }
            rollbackKnownPreparation(mob, backpack);
            session = null;
            out.add("Preparation failed: " + failure.getMessage());
            out.add("Result: ROLLED BACK BEFORE ARM");
            return new Result(false, List.copyOf(out));
        }
    }

    /** Atomic command composition: prepare, then invoke the existing witness preflight/arm. */
    public static synchronized Result run(Mob mob, MinecraftServer server, long tick) {
        Result prepared = prepare(mob, server);
        if (!prepared.success()) return prepared;
        Container backpack = PlayerMobs.backpack(mob);
        TeCurrencyWitnessTracker.ArmResult armed =
                TeCurrencyWitnessTracker.arm(mob, backpack, tick);
        List<String> out = new ArrayList<>(prepared.lines());
        out.addAll(armed.lines());
        if (!armed.armed()) {
            cleanup(server, mob, "tracker preflight refused", false);
            out.add("Fixture preparation rolled back because production was never armed.");
            return new Result(false, List.copyOf(out));
        }
        markArmed(mob.getUUID(), backpack);
        out.add("W2 run: ARMED; normal production now owns all decisions.");
        return new Result(true, List.copyOf(out));
    }

    /** Called when an operator uses the older prepare-then-start form. */
    public static synchronized void markArmed(UUID mobId, Container backpack) {
        if (matches(mobId, backpack)) session.phase = Phase.ARMED;
    }

    /** Roll back an exact prepared fixture when arming fails; post-arm inventory is preserved. */
    public static synchronized List<String> cleanup(
            MinecraftServer server, Mob resolvedMob, String reason, boolean reset) {
        if (session == null) return List.of("No V2-TE W2 fixture session exists.");
        Session current = session;
        List<String> out = new ArrayList<>();
        out.add("=== V2-TE W2 Fixture Cleanup ===");
        out.add("Reason: " + reason);
        removeOwnedVillager(server, current, out);

        boolean exactTarget = resolvedMob != null
                && Objects.equals(resolvedMob.getUUID(), current.mobId)
                && PlayerMobs.backpack(resolvedMob) == current.backpack;
        boolean exactPrepared = exactTarget && exactPreparedInventory(resolvedMob, current);
        if (mayRollbackInventory(current.phase, exactTarget, exactPrepared)) {
            rollbackKnownPreparation(resolvedMob, current.backpack);
            out.add("Target inventory: exact pre-arm fixture preparation rolled back.");
        } else {
            out.add("Target inventory: PRESERVED (post-arm or exact fixture provenance not provable).");
            if (exactTarget) out.add("Recovery snapshot: " + describeInventory(current.backpack));
            out.add("Recovery: inspect the PlayerMob backpack/hands and remove only items you can "
                    + "independently attribute to this fixture.");
        }
        session = null;
        out.add(reset ? "Fixture reset; all live references released."
                : "Fixture stopped; all live references released.");
        return List.copyOf(out);
    }

    public static synchronized List<String> statusLines() {
        if (session == null) return List.of("No V2-TE W2 fixture session exists.");
        return List.of(
                "=== V2-TE W2 Fixture ===",
                "Target: " + session.targetName + " [" + session.mobId + "]",
                "Phase: " + session.phase,
                "Fixture villager: " + session.villagerId + " in " + session.dimension,
                "Inventory: " + describeInventory(session.backpack));
    }

    public static synchronized void abortForMob(Mob mob, MinecraftServer server, String reason) {
        if (mob != null && session != null && Objects.equals(session.mobId, mob.getUUID())) {
            cleanup(server, mob, reason, false);
        }
    }

    public static synchronized void shutdownServerState(MinecraftServer server) {
        if (session == null) return;
        List<String> ignored = new ArrayList<>();
        removeOwnedVillager(server, session, ignored);
        // Server shutdown is not a safe moment to rewrite an absent/partially unloading target.
        session = null;
    }

    static boolean mayRollbackInventory(Phase phase, boolean exactTarget, boolean exactPrepared) {
        return phase == Phase.PREPARED && exactTarget && exactPrepared;
    }

    private static List<String> validate(Mob mob, MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        if (session != null) failures.add("another fixture session exists");
        if (TeCurrencyWitnessTracker.hasActiveSession()) failures.add("another witness is active");
        if (mob == null || !PlayerMobs.isPlayerMob(mob)) failures.add("target is not a Social PlayerMob");
        if (server == null) failures.add("server is unavailable");
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return failures;
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) failures.add("PlayerMob backpack is unavailable");
        else if (!containerEmpty(backpack)) failures.add("target backpack is not empty");
        if (!mob.getMainHandItem().isEmpty() || !mob.getOffhandItem().isEmpty()) {
            failures.add("target hands are not empty");
        }
        if (!mob.isAlive()) failures.add("target is not alive");
        if (mob.getTarget() != null) failures.add("target is in combat");
        if (!"0.8.0".equals(TradeEverythingCompat.installedVersion())) {
            failures.add("Trade Everything 0.8.0 is not installed");
        }
        if (!TradeEverythingCompat.currencyCapabilityActive()) failures.add("TE currency capability is inactive");
        if (!TradeEverythingCompat.quoteBridgeHealthy()) failures.add("TE quote bridge is unhealthy");
        if (RouteExhaustionEvidence.tracks(mob.getUUID())) failures.add("target already has route-exhaustion evidence");
        if (!level.getEntitiesOfClass(Villager.class,
                new AABB(mob.blockPosition()).inflate(MERCHANT_RADIUS), Entity::isAlive).isEmpty()) {
            failures.add("another live Villager is within the production discovery radius");
        }
        int radius = Math.max(1, (int) ScavengerConfig.get().gatherSearchRadius);
        boolean incomplete = false;
        boolean ore = false;
        BlockPos origin = mob.blockPosition();
        scan: for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos)
                            || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        incomplete = true;
                        continue;
                    }
                    if (level.getBlockState(pos).is(BlockTags.IRON_ORES)) {
                        ore = true;
                        break scan;
                    }
                }
            }
        }
        if (incomplete) failures.add("Gather search volume is not fully loaded");
        if (ore) failures.add("iron ore exists inside the Gather search volume");
        if (findMerchantPosition(level, origin) == null) failures.add("no safe fixture merchant position nearby");
        return failures;
    }

    private static Villager createMerchant(ServerLevel level, BlockPos pos) {
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.TOOLSMITH).setLevel(2));
        villager.setNoAi(true);
        villager.setCustomName(Component.literal("V2-TE W2 Fixture Toolsmith"));
        villager.addTag(FIXTURE_TAG);
        villager.getOffers().clear();
        villager.getOffers().add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 10), Optional.empty(),
                new ItemStack(Items.IRON_PICKAXE), 0, 12, 0, 0f));
        return villager;
    }

    private static ItemStack witnessSticks(MinecraftServer server) {
        ItemStack stack = new ItemStack(Items.STICK, 4);
        stack.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), 8);
        return stack;
    }

    private static BlockPos findMerchantPosition(ServerLevel level, BlockPos origin) {
        int[][] offsets = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}};
        for (int[] offset : offsets) {
            BlockPos feet = origin.offset(offset[0], 0, offset[1]);
            if (level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)
                    && level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.below()).isFaceSturdy(
                            level, feet.below(), Direction.UP)) return feet;
        }
        return null;
    }

    private static void removeOwnedVillager(MinecraftServer server, Session s, List<String> out) {
        if (server == null) {
            out.add("Fixture villager: not resolved; server unavailable.");
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(s.villagerId);
            if (entity == null) continue;
            if (entity instanceof Villager && entity.getTags().contains(FIXTURE_TAG)) {
                entity.discard();
                out.add("Fixture villager: removed by exact UUID + ownership tag.");
            } else {
                out.add("Fixture villager: PRESERVED; UUID/tag ownership proof failed.");
            }
            return;
        }
        out.add("Fixture villager: already absent.");
    }

    private static boolean matches(UUID mobId, Container backpack) {
        return session != null && Objects.equals(session.mobId, mobId) && session.backpack == backpack;
    }

    /** Read-only command lookup; cleanup still rechecks the same UUID/container pair. */
    public static synchronized boolean matchesForCleanup(UUID mobId, Container backpack) {
        return matches(mobId, backpack);
    }

    private static boolean containerEmpty(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) return false;
        return true;
    }

    private static boolean exactPreparedInventory(Mob mob, Session s) {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null || !mob.getOffhandItem().isEmpty()
                || mob.getMainHandItem() != s.fixturePick) return false;
        int occupied = 0;
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.isEmpty()) continue;
            occupied++;
            if (stack != s.fixtureSticks
                    || !TeCurrencyWitnessTracker.isWitnessStickForFixture(stack)
                    || stack.getCount() != 4) return false;
        }
        return occupied == 1;
    }

    private static void rollbackKnownPreparation(Mob mob, Container backpack) {
        if (backpack != null) backpack.clearContent();
        if (mob != null) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private static String describeInventory(Container container) {
        if (container == null) return "<unavailable>";
        List<String> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) stacks.add("slot " + i + "=" + stack.getCount() + "x "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return stacks.isEmpty() ? "empty" : String.join(", ", stacks);
    }

    private static final class Session {
        private final UUID mobId;
        private final String targetName;
        private final Container backpack;
        private final ItemStack fixtureSticks;
        private final ItemStack fixturePick;
        private final UUID villagerId;
        private final String dimension;
        private Phase phase;

        private Session(UUID mobId, String targetName, Container backpack,
                        ItemStack fixtureSticks, ItemStack fixturePick, UUID villagerId,
                        String dimension, Phase phase) {
            this.mobId = mobId;
            this.targetName = targetName;
            this.backpack = backpack;
            this.fixtureSticks = fixtureSticks;
            this.fixturePick = fixturePick;
            this.villagerId = villagerId;
            this.dimension = dimension;
            this.phase = phase;
        }
    }
}
