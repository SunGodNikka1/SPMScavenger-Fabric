package com.noobk.spmscavenger.opinion;

/** GAO-9 — weak semantic preference among routes already accepted by every safety gate. */
public final class EnvironmentOpinionRouteRanker {

    public static final int MAX_ROUTE_BIAS = 10;

    private EnvironmentOpinionRouteRanker() {
    }

    public static int routeBias(EnvironmentOpinionMemory memory, EnvironmentProfile profile) {
        if (!OpinionFeatureGate.isEnabled() || profile.isEmpty()) {
            return 0;
        }
        float total = 0f;
        for (EnvironmentKind kind : profile.kinds()) {
            total += memory.preference(kind);
        }
        float mean = total / profile.size();
        return Math.round(UtilityNormalizer.channel(mean) * MAX_ROUTE_BIAS);
    }
}
