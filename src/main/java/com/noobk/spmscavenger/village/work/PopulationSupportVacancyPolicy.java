package com.noobk.spmscavenger.village.work;

/**
 * D-VR-083-A1 — settlement-wide population-support <em>candidate</em> predicate (not permission).
 *
 * <p>Task-57 must still revalidate breeder-local vacant HOME reachability before food commit.
 */
public final class PopulationSupportVacancyPolicy {

    private PopulationSupportVacancyPolicy() {}

    /**
     * @return {@code true} only when facts are readable and vacancy + adult count satisfy the lock
     */
    public static boolean isPopulationSupportCandidate(VillageWorkFacts facts) {
        if (facts == null || !facts.isReadable()) {
            return false;
        }
        return facts.adultVillagerCount() >= 2 && facts.currentFreeHomeCapacity() > 0;
    }
}
