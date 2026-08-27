package com.noobk.spmscavenger.village.routing;

import com.noobk.spmscavenger.opinion.DiscretionaryScoringInput;
import com.noobk.spmscavenger.opinion.SettlementOpinionBias;
import com.noobk.spmscavenger.opinion.SettlementOpinionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * D-VR-092/093 — deterministic selector over already-resolved remembered settlement facts.
 *
 * <p>The caller owns non-dimensional objective admission and supplies only admitted facts. This
 * selector enforces dimension compatibility and temporary demotion; neither step grants authority.
 */
public final class SettlementDestinationRanker {

    public record Selection(
            SettlementDestinationFacts facts,
            FactualVillageUtility factualUtility,
            int opinionBias) {
    }

    private static final Comparator<Selection> ORDER = Comparator
            .comparingInt((Selection selection) ->
                    selection.facts().capabilityEvidence().rank())
            .thenComparing(Selection::factualUtility)
            .thenComparing(Comparator.comparingInt(Selection::opinionBias).reversed())
            .thenComparing(selection -> selection.facts().key());

    private SettlementDestinationRanker() {
    }

    public static Optional<Selection> select(
            Collection<SettlementDestinationFacts> candidates,
            ResourceKey<Level> originDimension,
            BlockPos origin,
            DiscretionaryScoringInput opinionInput,
            SettlementOpinionContext opinionContext,
            RouteAttemptEvidence attemptEvidence,
            long now) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(originDimension, "originDimension");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(opinionInput, "opinionInput");
        Objects.requireNonNull(opinionContext, "opinionContext");
        Objects.requireNonNull(attemptEvidence, "attemptEvidence");

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.key().dimension().equals(originDimension))
                .filter(candidate -> !attemptEvidence.temporarilyUnavailable(candidate.key(), now))
                .map(candidate -> new Selection(
                        candidate,
                        FactualVillageUtility.from(origin, candidate),
                        SettlementOpinionBias.request(
                                candidate.village(), opinionInput, opinionContext)))
                .min(ORDER);
    }
}
