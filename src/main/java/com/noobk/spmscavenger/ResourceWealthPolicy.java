package com.noobk.spmscavenger;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Generic resource valuation boundary; MI-3/MI-23 NEED layers + MI-24 wealth curves. */
public final class ResourceWealthPolicy {

    public enum ResourceCategory { LOGS, COAL, COBBLESTONE, IRON, DIAMOND }

    /** Requested quantities, not independently calculated shortfalls. */
    public record ResourceNeedContext(
            ResourceCategory category,
            int currentAmount,
            int immediateDemand,
            int replacementDemand,
            int workingReserve,
            int projectDemand) {
        public ResourceNeedContext {
            Objects.requireNonNull(category, "category");
            if (currentAmount < 0 || immediateDemand < 0 || replacementDemand < 0
                    || workingReserve < 0 || projectDemand < 0) {
                throw new IllegalArgumentException("resource need quantities must be non-negative");
            }
        }
    }

    /**
     * One allocation result. Blocking demand is immediate + replacement + project; reserve is
     * evaluated only with stock left after those layers, so one item can never satisfy two needs.
     */
    public record NeedUtility(
            ResourceCategory category,
            int immediateShortfall,
            int replacementShortfall,
            int projectShortfall,
            int reserveShortfall,
            int surplus) {
        public int blockingShortfall() {
            return immediateShortfall + replacementShortfall + projectShortfall;
        }

        public boolean hasBlockingNeed() {
            return blockingShortfall() > 0;
        }

        public boolean hasReserveNeed() {
            return reserveShortfall > 0;
        }
    }

    /** Gen-1 v1 profile constants (D-MIW-026). */
    public record ResourceWealthProfile(
            float baseValue,
            int comfortableAmount,
            int saturationAmount,
            float hoardability,
            float rarityAppeal,
            float generalUtility) {
        public ResourceWealthProfile {
            if (comfortableAmount < 0 || saturationAmount < comfortableAmount) {
                throw new IllegalArgumentException("invalid comfortable/saturation band");
            }
        }
    }

    public record ResourceWealthContext(
            ResourceCategory category,
            int currentAmount,
            float greed,
            float wealthLevel) {
        public ResourceWealthContext {
            Objects.requireNonNull(category, "category");
            if (currentAmount < 0 || greed < 0.0F || greed > 1.0F || wealthLevel < 0.0F) {
                throw new IllegalArgumentException("invalid wealth context");
            }
        }
    }

    public record WealthUtility(
            ResourceCategory category,
            float wealthFactor,
            float wealthValue,
            float opportunityBonus,
            float acquisitionCost) {
        public float netUtility() {
            return wealthValue + opportunityBonus - acquisitionCost;
        }
    }

    private static final float SATURATION_FLOOR = 0.05F;
    private static final Map<ResourceCategory, ResourceWealthProfile> GEN1_PROFILES =
            new EnumMap<>(ResourceCategory.class);

    static {
        GEN1_PROFILES.put(ResourceCategory.LOGS,
                new ResourceWealthProfile(0.35F, 8, 32, 0.6F, 0.0F, 0.5F));
        GEN1_PROFILES.put(ResourceCategory.COAL,
                new ResourceWealthProfile(0.40F, 16, 64, 0.7F, 0.0F, 0.6F));
        GEN1_PROFILES.put(ResourceCategory.COBBLESTONE,
                new ResourceWealthProfile(0.20F, 12, 48, 0.5F, 0.0F, 0.4F));
        GEN1_PROFILES.put(ResourceCategory.IRON,
                new ResourceWealthProfile(0.55F, 12, 48, 0.75F, 0.0F, 0.9F));
        GEN1_PROFILES.put(ResourceCategory.DIAMOND,
                new ResourceWealthProfile(0.90F, 6, 24, 0.95F, 0.85F, 0.7F));
    }

    private ResourceWealthPolicy() {
    }

    public static ResourceWealthProfile profileFor(ResourceCategory category) {
        return GEN1_PROFILES.get(category);
    }

    public static NeedUtility evaluateNeed(ResourceNeedContext context) {
        int available = context.currentAmount();

        Allocation immediate = allocate(available, context.immediateDemand());
        Allocation replacement = allocate(immediate.remaining(), context.replacementDemand());
        Allocation project = allocate(replacement.remaining(), context.projectDemand());
        Allocation reserve = allocate(project.remaining(), context.workingReserve());

        return new NeedUtility(
                context.category(),
                immediate.shortfall(),
                replacement.shortfall(),
                project.shortfall(),
                reserve.shortfall(),
                reserve.remaining());
    }

    /** Marginal wealth utility for one more unit (MI-24). */
    public static float wealthFactor(int amount, ResourceWealthProfile profile) {
        if (amount < profile.comfortableAmount()) {
            return 1.0F;
        }
        if (amount >= profile.saturationAmount()) {
            return SATURATION_FLOOR;
        }
        float span = profile.saturationAmount() - profile.comfortableAmount();
        float over = amount - profile.comfortableAmount();
        return 1.0F - 0.95F * (over / span);
    }

    public static float wealthValue(ResourceWealthContext context) {
        if (context.greed() == 0.0F || context.wealthLevel() == 0.0F) {
            return 0.0F;
        }
        ResourceWealthProfile profile = profileFor(context.category());
        float factor = wealthFactor(context.currentAmount(), profile);
        float scale = context.greed() * context.wealthLevel();
        return profile.baseValue() * profile.hoardability() * factor * scale
                + profile.rarityAppeal() * scale * factor;
    }

    /** Local acquisition bonus (MI-25). */
    public static float opportunityBonus(
            float wealthValue, float greed, float acquisitionCost) {
        if (wealthValue <= 0.0F) {
            return 0.0F;
        }
        float detourBudget = 8.0F + greed * 12.0F;
        float proximity = Math.max(0.0F, 1.0F - acquisitionCost / detourBudget);
        return wealthValue * proximity;
    }

    public static WealthUtility evaluateWealth(
            ResourceWealthContext context, float acquisitionCost) {
        float wealthValue = wealthValue(context);
        float bonus = opportunityBonus(wealthValue, context.greed(), acquisitionCost);
        return new WealthUtility(
                context.category(),
                wealthFactor(context.currentAmount(), profileFor(context.category())),
                wealthValue,
                bonus,
                acquisitionCost);
    }

    private static Allocation allocate(int available, int demand) {
        int used = Math.min(available, demand);
        return new Allocation(available - used, demand - used);
    }

    private record Allocation(int remaining, int shortfall) {
    }
}
