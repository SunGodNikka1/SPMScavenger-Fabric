package com.noobk.spmscavenger.opinion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.EnumSet;

/** Target-native O(1) classification of a position the caller already had reason to inspect. */
public final class EnvironmentClassifier {

    private EnvironmentClassifier() {
    }

    public static EnvironmentProfile classify(ServerLevel level, BlockPos position) {
        Holder<Biome> biome = level.getBiome(position);
        return fromSignals(
                biome.is(BiomeTags.IS_FOREST),
                biome.is(BiomeTags.IS_OCEAN),
                biome.value().coldEnoughToSnow(position),
                biome.is(BiomeTags.IS_NETHER),
                biome.is(BiomeTags.IS_END));
    }

    static EnvironmentProfile fromSignals(
            boolean forest, boolean ocean, boolean snowy, boolean nether, boolean end) {
        EnumSet<EnvironmentKind> labels = EnumSet.noneOf(EnvironmentKind.class);
        if (forest) labels.add(EnvironmentKind.FOREST);
        if (ocean) labels.add(EnvironmentKind.OCEAN);
        if (snowy) labels.add(EnvironmentKind.SNOWY);
        if (nether) labels.add(EnvironmentKind.NETHER);
        if (end) labels.add(EnvironmentKind.END);
        return labels.isEmpty() ? EnvironmentProfile.empty() : new EnvironmentProfile(labels);
    }
}
