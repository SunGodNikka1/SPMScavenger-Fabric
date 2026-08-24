package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.PlayerMobs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Bounded, read-only world/inventory evidence for one Task-59 scenario. */
final class V3ScenarioEvidence {

    private static final int MAX_FIXTURE_MOBS = 16;

    record Capture(
            List<String> lines,
            String transitionFingerprint,
            int replantedTargetMask,
            int subjectSeedCount) {

        Capture {
            lines = List.copyOf(lines);
        }
    }

    private V3ScenarioEvidence() {
    }

    static Capture capture(
            ServerLevel level, Mob subject, BlockPos origin, V3CampaignScenario scenario) {
        List<String> lines = new ArrayList<>();
        List<String> transitionParts = new ArrayList<>();
        int replantedMask = 0;
        int blockIndex = 0;

        for (BlockPos offset : blockOffsets(scenario)) {
            BlockPos pos = origin.offset(offset);
            BlockState state = level.getBlockState(pos);
            String value = "block offset=" + offset.toShortString()
                    + " pos=" + pos.toShortString() + " state=" + state;
            lines.add(value);
            transitionParts.add(value);
            if (state.hasProperty(BlockStateProperties.AGE_7)
                    && state.getValue(BlockStateProperties.AGE_7) == 0) {
                replantedMask |= 1 << blockIndex;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                String inventory = "blockInventory offset=" + offset.toShortString()
                        + " contents=" + inventorySummary(container);
                lines.add(inventory);
                transitionParts.add(inventory);
            }
            blockIndex++;
        }

        Container subjectBackpack = PlayerMobs.backpack(subject);
        int subjectSeeds = subjectBackpack == null ? 0 : subjectBackpack.countItem(Items.WHEAT_SEEDS);
        String subjectInventory = "subjectInventory=" + inventorySummary(subjectBackpack);
        lines.add(subjectInventory);
        transitionParts.add(subjectInventory);

        double coreRadius = V3CampaignSpatialPolicy.SCENARIO_CORE_RADIUS;
        AABB scenarioCore = new AABB(origin).inflate(coreRadius, 16.0, coreRadius);
        List<Mob> fixtureMobs = level.getEntitiesOfClass(Mob.class, scenarioCore,
                mob -> mob.getTags().stream().anyMatch(tag -> tag.startsWith("spm_vr.")));
        fixtureMobs.sort(Comparator.comparing(mob -> mob.getUUID().toString()));
        for (Mob mob : fixtureMobs.stream().limit(MAX_FIXTURE_MOBS).toList()) {
            String role = mob.getTags().stream()
                    .filter(tag -> tag.startsWith("spm_vr."))
                    .sorted()
                    .toList()
                    .toString();
            lines.add("entity=" + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                    + " uuid=" + mob.getUUID()
                    + " role=" + role
                    + " pos=" + mob.blockPosition().toShortString()
                    + " target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getUUID()));
            if (PlayerMobs.isPlayerMob(mob)) {
                String inventory = "playerMobInventory uuid=" + mob.getUUID()
                        + " contents=" + inventorySummary(PlayerMobs.backpack(mob));
                lines.add(inventory);
                transitionParts.add(inventory);
            } else if (mob instanceof Villager villager) {
                String inventory = "villagerInventory uuid=" + mob.getUUID()
                        + " contents=" + inventorySummary(villager.getInventory());
                lines.add(inventory);
                transitionParts.add(inventory);
            }
        }
        if (fixtureMobs.size() > MAX_FIXTURE_MOBS) {
            lines.add("fixtureMobEvidenceTruncated=" + (fixtureMobs.size() - MAX_FIXTURE_MOBS));
        }

        return new Capture(
                lines,
                String.join("|", transitionParts),
                replantedMask,
                subjectSeeds);
    }

    private static List<BlockPos> blockOffsets(V3CampaignScenario scenario) {
        return switch (scenario) {
            case CROP_MULTI_CYCLE -> List.of(
                    new BlockPos(3, 0, 3),
                    new BlockPos(4, 0, 3),
                    new BlockPos(5, 0, 3));
            case COMPOST_SEED_SURPLUS -> List.of(new BlockPos(6, 0, 0));
            case STORAGE_PUBLIC_DENY -> List.of(new BlockPos(8, 0, 0));
            case STORAGE_UNKNOWN_DENY -> List.of(new BlockPos(10, 0, 0));
            case STORAGE_GRANTED_PERMIT -> List.of(new BlockPos(5, 0, 0));
            case POPULATION_FOOD_DEFICIT -> List.of();
            default -> List.of(new BlockPos(4, 0, 4));
        };
    }

    private static String inventorySummary(Container container) {
        if (container == null) {
            return "UNAVAILABLE";
        }
        List<String> stacks = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(slot + "=" + stack.getCount() + "x"
                        + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        return stacks.toString();
    }
}
