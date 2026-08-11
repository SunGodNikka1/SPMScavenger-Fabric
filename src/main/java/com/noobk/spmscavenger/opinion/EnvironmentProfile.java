package com.noobk.spmscavenger.opinion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** GAO-9 — immutable, bounded, multi-label context captured at an existing route/event seam. */
public record EnvironmentProfile(Set<EnvironmentKind> kinds) {

    private static final EnvironmentProfile EMPTY =
            new EnvironmentProfile(Collections.emptySet());

    public EnvironmentProfile {
        Objects.requireNonNull(kinds, "kinds");
        kinds = kinds.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(kinds));
    }

    public static EnvironmentProfile empty() {
        return EMPTY;
    }

    public static EnvironmentProfile of(EnvironmentKind... kinds) {
        if (kinds.length == 0) {
            return empty();
        }
        EnumSet<EnvironmentKind> values = EnumSet.noneOf(EnvironmentKind.class);
        Collections.addAll(values, kinds);
        return new EnvironmentProfile(values);
    }

    public boolean contains(EnvironmentKind kind) {
        return kinds.contains(kind);
    }

    public boolean isEmpty() {
        return kinds.isEmpty();
    }

    public int size() {
        return kinds.size();
    }
}
