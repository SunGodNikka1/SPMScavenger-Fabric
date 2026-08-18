# Known defects — open

Defects discovered but deliberately **not** repaired in the commit that found them, each with the
gate that closes it. A defect leaves this file only when its gate has evidence.

---

## V2-DEF-001 — a PlayerMob trade erases pending human-player trade reputation

**Status:** OPEN. **Discovered:** P0-2 source review. **Applies to:** shipped vanilla V2, not only
V2-TE. **Severity:** low impact, high principle.

### What happens

`VillagerTradeAdapter` executes without a merchant session, so `villager.getTradingPlayer()` is
`null` when it calls `notifyTrade`. Vanilla `Villager#rewardTradeXp` then does:

```java
this.lastTradedPlayer = this.getTradingPlayer();   // -> null in our path
```

`lastTradedPlayer` is consumed exactly once, in `Villager#customServerAiStep`, at level-up:

```java
if (this.lastTradedPlayer != null && this.level() instanceof ServerLevel level) {
    level.onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
    this.level().broadcastEntityEvent(this, (byte) 14);
    this.lastTradedPlayer = null;
}
```

So if a human trades with a villager that is about to level up, and a PlayerMob trades with the same
villager before that level-up resolves, the human's pending `TRADE` gossip is silently dropped. The
player loses reputation they earned, with no message and no log line.

### Why it is not bundled with P0-2

It changes **shipped** V2 behaviour. Repairing it inside the detached-execution experiment would mix
a behaviour change into a proof about a call path, and a green P0-2 would no longer be evidence
about only the thing P0-2 was testing.

### Contract

> A PlayerMob transaction must not erase pending human-player reputation attribution that it did not
> earn.

Note the contract is about **preservation**, not about the mob earning gossip. The mob is not a
player and must not receive `TRADE` reputation.

### Repair gate — V2-DEF-001

Not a one-line fix. `lastTradedPlayer` is `private` on `Villager` with no public accessor, so the
repair needs an accessor or Mixin seam, and the seam itself needs a test.

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Player trades, then a PlayerMob trades, then the villager levels up | the player still receives `ReputationEventType.TRADE` | the mob's trade nulls the pending attribution | accessor/Mixin seam test + runtime gossip check |
| PlayerMob trades a villager no player has traded | `lastTradedPlayer` stays `null` | the mob is credited with `TRADE` reputation | seam test |
| Two PlayerMobs trade in sequence | preservation is idempotent | a saved value is restored onto a villager that has since been traded by a player | seam test |

Runtime proof class (AV-1): a gossip read before/after, not a compile.
