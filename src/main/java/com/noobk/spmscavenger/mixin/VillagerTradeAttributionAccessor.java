package com.noobk.spmscavenger.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * V2-DEF-001 — read and restore the villager's <b>pending</b> trade attribution.
 *
 * <h2>The defect</h2>
 *
 * {@code VillagerTradeAdapter} trades with no merchant session, so {@code getTradingPlayer()} is
 * {@code null} when it calls {@code notifyTrade}. Vanilla {@code Villager#rewardTradeXp} then does
 * {@code this.lastTradedPlayer = this.getTradingPlayer()} — writing {@code null} over whatever was
 * there. That field is consumed exactly once, at level-up in {@code customServerAiStep}, to award
 * {@code ReputationEventType.TRADE} to the human who earned it. So a PlayerMob trading a villager
 * that a player has traded, before that villager levels, silently deletes the player's gossip.
 *
 * <h2>Why an accessor and not a redirect</h2>
 *
 * A {@code @Redirect} on the field write would fix it for every caller in the game, including other
 * mods' no-session trades. That is a larger behavioural claim than the evidence supports, and Gate
 * SPM-0 says the more compatible option wins: this mod repairs <b>its own</b> transaction and leaves
 * everyone else's semantics untouched. The accessor is read/write because the repair is
 * save-then-restore around our own {@code notifyTrade}, not a suppression of vanilla's assignment.
 *
 * <h2>Not a way to earn gossip</h2>
 *
 * The setter exists only to put back a value that was already there. A PlayerMob is not a player and
 * must never be written into this field; the policy that decides what goes back is
 * {@code TradeAttributionPolicy}, and it can only ever return the pre-existing value or the newer
 * one — never a mob.
 *
 * <p>{@code lastTradedPlayer} is {@code private} on {@code Villager} in 1.21.1 with no accessor
 * (`CONFIRMED` from the mapped jar), which is why this seam exists at all.
 */
@Mixin(Villager.class)
public interface VillagerTradeAttributionAccessor {

    @Accessor("lastTradedPlayer")
    Player spmscavenger$getLastTradedPlayer();

    @Accessor("lastTradedPlayer")
    void spmscavenger$setLastTradedPlayer(Player player);
}
