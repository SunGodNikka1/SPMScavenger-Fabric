package com.noobk.spmscavenger.village.interaction;

import com.noobk.spmscavenger.village.intent.VillageIntent;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * V4-E movement admission for the existing COMMUTE executor.
 *
 * <p>The exact intent instance is the binding. Two intents with equal values are still different
 * commitments and may not inherit one another's expedition or path state.
 */
public record CommuteDirective(BlockPos destination, Purpose purpose, Binding binding) {

    public enum Purpose {
        REQUIRED_TRADE
    }

    public CommuteDirective {
        destination = Objects.requireNonNull(destination, "destination").immutable();
        purpose = Objects.requireNonNull(purpose, "purpose");
        binding = Objects.requireNonNull(binding, "binding");
        if (!binding.intent().destination().anchor().equals(destination)) {
            throw new IllegalArgumentException("directive destination must match bound intent");
        }
    }

    public static CommuteDirective requiredTrade(VillageIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.kind() != VillageIntent.Kind.REQUIRED_TRADE) {
            throw new IllegalArgumentException("required-trade directive needs required-trade intent");
        }
        return new CommuteDirective(
                intent.destination().anchor(), Purpose.REQUIRED_TRADE, new Binding(intent));
    }

    /** Identity-bound rather than value-bound; see the class contract. */
    public record Binding(VillageIntent intent) {
        public Binding {
            Objects.requireNonNull(intent, "intent");
        }

        public boolean matchesExact(VillageIntent candidate) {
            return intent == candidate;
        }

        public boolean matchesExact(Binding other) {
            return other != null && intent == other.intent;
        }
    }
}
