package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.goal.PopulationFoodSupportGoal;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import org.junit.jupiter.api.Test;

/** Task-57 — VILLAGE_WORK taxonomy pin. */
class PopulationFoodTaxonomyTest {

    @Test
    void populationFoodSupportMapsToVillageWork() {
        assertEquals(
                ActivityClass.VILLAGE_WORK,
                MoveHolderClassifier.staticActivityClass(PopulationFoodSupportGoal.class));
    }
}
