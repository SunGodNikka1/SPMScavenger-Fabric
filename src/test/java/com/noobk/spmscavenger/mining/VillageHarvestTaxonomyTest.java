package com.noobk.spmscavenger.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.goal.VillageHarvestEpisodeGoal;
import org.junit.jupiter.api.Test;

class VillageHarvestTaxonomyTest {

    @Test
    void villageHarvestEpisodeMapsToVillageWork() {
        assertEquals(
                ActivityClass.VILLAGE_WORK,
                MoveHolderClassifier.staticActivityClass(VillageHarvestEpisodeGoal.class));
    }
}
