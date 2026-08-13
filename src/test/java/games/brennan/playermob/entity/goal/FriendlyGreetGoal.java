package games.brennan.playermob.entity.goal;

import net.minecraft.world.entity.ai.goal.Goal;

/** Test-only name double for binding-dependent semantic classification. */
public final class FriendlyGreetGoal extends Goal {
    @Override
    public boolean canUse() {
        return false;
    }
}
