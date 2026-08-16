# Task 47 brief: V2-A — `VillagerTradeAdapter` + `OfferSnapshot`

**Slice:** V2-A only. V2-B…V2-I and V2-TE are **out of scope** for this task.
**Authorization:** User, 2026-08-15 — "Implement task-47 slice V2-A".
**Not authorized:** Minecraft runtime launch · commit · push.

## Target

| Field | Value |
| --- | --- |
| Port target root | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| Package | `com.noobk.spmscavenger.village.trade` |
| Host | Social Player Mobs `playermob` 0.86.0 (stock — PolyForm Shield, never forked) |
| RFC | `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` → *V2 implementation contract* |

## Source evidence (pinned 1.21.1 merged jar, `CODE_CONFIRMED`)

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-1425f5a1b7/…/*.jar`

| Symbol | Fact | Consequence for this task |
| --- | --- | --- |
| `MerchantOffer#getResult()` | returns the **live `result` field** | **banned** — aliasing corrupts the villager's offer and persists |
| `MerchantOffer#assemble()` | `result.copy()` | the only legal way to obtain the output |
| `MerchantOffer#getCostA()` | `baseCostA.itemStack().copyWithCount(n)` — a copy | safe |
| `MerchantOffer#getBaseCostA()` | returns the **live** `ItemCost.itemStack` | **banned** |
| `MerchantOffer#take(a, b)` | `satisfiedBy` then `a.shrink(costA)`, `b.shrink(costB)` — mutates **only the two stacks passed** (offsets 0–44) | menu-shaped; cannot express multi-slot payment. Use `satisfiedBy` as validation, debit across slots ourselves |
| `AbstractVillager#notifyTrade(MerchantOffer)` | increments uses, awards XP, plays sound; **no `Player` parameter** | callable for a PlayerMob; call **exactly once**, after the inventory commit |
| `Villager#updateTrades` | `addOffersFromItemListings(getOffers(), …)` — **appends** | offer indices are stable across level-up; no re-index handling needed (B-VR-94) |
| `Villager#setTradingPlayer` / `#updateSpecialPrices(Player)` | player-typed | **not called** in gen-1; hero discount stays V6 |
| `PlayerMobs.backpack(mob)` | vanilla `InventoryCarrier` seam | the container this task writes |

## Deliverables

1. **`OfferSnapshot`** — immutable record of one live offer: index, `costA`, `costB`, `result`,
   `uses`, `maxUses`. All stacks stored as **copies**. Exposes `outOfStock()` and
   `matchesLive(MerchantOffer)` for exact revalidation.
2. **`TradeTransaction`** — pure staged-inventory arithmetic over `ItemStack[]`, no entity, no
   `Container` writes until commit: `stage`, `debit` (multi-slot), `insert` (multi-slot, respects
   `getMaxStackSize`), `commit`.
3. **`VillagerTradeAdapter`** — `inspectOffers(Villager)`, `canAfford(Container, OfferSnapshot)`,
   `performTrade(Container, Villager, OfferSnapshot)` returning a typed outcome.

## Required semantics

```text
performTrade:
  1 re-resolve the live offer by index          -> OFFER_GONE if absent
  2 matchesLive(snapshot)                        -> OFFER_CHANGED if not exact
  3 !isOutOfStock()                              -> OUT_OF_STOCK
  4 stage = copy of every backpack slot
  5 debit costA across slots on stage            -> CANNOT_AFFORD
  6 debit costB across slots on stage (if any)   -> CANNOT_AFFORD
  7 preflight insert assemble() into stage       -> NO_ROOM
  8 commit stage to the real container           <- first mutation of anything real
  9 villager.notifyTrade(liveOffer)              <- exactly once, after 8
```

Any failure before step 8 leaves the real backpack and the villager **untouched**.

## Constraints

- Never call `getResult()` or `getBaseCostA()`.
- Never call `setTradingPlayer`, `MerchantMenu`, or any client type.
- No new per-mob persisted state in this slice; if one is ever added it must register in
  `PerMobSavedData.forgetAll` (Gate RET-1e, build-enforced).
- No goal, no scheduling, no demand integration — V2-E/V2-B own those.

## Verification

`.\\gradlew.bat clean build`

**Must happen**
- exact cost debited **across multiple slots** (20 wheat as 16+4);
- output inserted from `assemble()`, merging into a partial stack and respecting max stack size;
- `notifyTrade` called exactly once, after the commit.

**Must not happen**
- any source path reaching `getResult()` / `getBaseCostA()` (structural test);
- partial debit when the result cannot fit;
- any mutation when the live offer changed, vanished, or is out of stock;
- a second `notifyTrade`.

Each must-not gets a negative control run **in isolation**.

## Docs to update

`docs/porting/DECISIONS.md` (V2-A entry) · `.superpowers/sdd/task-47-report.md` ·
`.superpowers/sdd/progress.md` on acceptance.
